package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.IdentityHashMap

enum class FieldKind { TEXT, CHECKBOX, RADIO, CHOICE }

/** Where one of a field's boxes sits: a page, and fractions of it as displayed from the top left. */
data class FieldWidget(val sourceIndex: Int, val rect: MarkRect)

/**
 * A fillable field, as the form panel needs it.
 *
 * Values are kept as the strings PDFBox reads and writes: a text field's text,
 * a choice's export value, and for a checkbox or radio group the name of the
 * state that is on, or [OFF].
 */
data class FormField(
    /** The fully qualified name: what [PdfForms.apply] looks the field up by. */
    val name: String,
    /** What to call it on screen: its tooltip if the form has one, else a tidied name. */
    val label: String,
    val kind: FieldKind,
    val value: String,
    /** Export values: a choice's entries, a radio group's buttons, a checkbox's "on" state. */
    val options: List<String> = emptyList(),
    /** What to show for each of [options]; the same list when the form gives no separate labels. */
    val optionLabels: List<String> = options,
    val multiline: Boolean = false,
    val maxLength: Int? = null,
    val readOnly: Boolean = false,
    val widgets: List<FieldWidget> = emptyList(),
) {
    companion object {
        const val OFF = "Off"
    }
}

data class FormInfo(
    val fields: List<FormField>,
    /**
     * The form exists only as XFA, a format only Adobe's own apps fill in. There
     * are no fields here to fill, and the document says so when opened elsewhere.
     */
    val xfaOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = fields.isEmpty()
}

/** Thrown when a value cannot be set at all, even with the characters its font lacks left out. */
class FormValueException(val fieldLabel: String, cause: Throwable) :
    Exception(S.couldNotFill(fieldLabel), cause)

object PdfForms {

    /** Reads the form in [file]. Fields of kinds the app does not fill (signatures, buttons) are left out. */
    suspend fun read(file: File, password: String? = null): FormInfo = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use(::read)
    }

    fun read(document: PDDocument): FormInfo {
        val form = document.documentCatalog.acroForm ?: return FormInfo(emptyList())
        val pageIndex = IdentityHashMap<Any, Int>().apply {
            document.pages.forEachIndexed { index, page -> put(page.cosObject, index) }
        }
        val fields = form.fieldTree.mapNotNull { field ->
            (field as? PDTerminalField)?.let { describe(it, document, pageIndex) }
        }
        val xfaOnly = fields.isEmpty() && form.hasXFA()
        return FormInfo(fields, xfaOnly)
    }

    /**
     * Sets [values] on [document]'s fields, regenerating how each looks so the
     * page shows the new value in any viewer. Names that do not exist, and
     * fields that are read-only, are skipped.
     *
     * Shared by export and preview, so both draw a field the same way.
     */
    fun apply(document: PDDocument, values: Map<String, String>) {
        if (values.isEmpty()) return
        val form = document.documentCatalog.acroForm ?: return
        // XFA would win over the fields in viewers that understand it, showing
        // the old values; with the fields filled, it is only a stale copy.
        if (form.hasXFA()) form.xfa = null
        var approximated = false
        for ((name, value) in values) {
            val field = form.getField(name) as? PDTerminalField ?: continue
            if (field.isReadOnly) continue
            try {
                when (field) {
                    is PDCheckBox -> if (value == FieldFormat.OFF) field.unCheck() else field.check()
                    // "Off" is a valid value for a radio group: nothing chosen.
                    is PDRadioButton, is PDChoice -> field.setValue(value)
                    is PDTextField -> try {
                        field.setValue(value)
                    } catch (e: IllegalArgumentException) {
                        // The form's font cannot draw some of these characters,
                        // e.g. a script it was never meant for. Draw what it can,
                        // keep the real value, and let other viewers redraw it
                        // with fonts of their own.
                        field.setValue(FieldFormat.drawable(value))
                        field.cosObject.setString(COSName.V, value)
                        approximated = true
                    }
                    else -> Unit
                }
            } catch (e: IllegalArgumentException) {
                throw FormValueException(labelOf(field), e)
            } catch (e: UnsupportedOperationException) {
                throw FormValueException(labelOf(field), e)
            }
        }
        // Appearances are generated now; unless one had to be approximated,
        // this stops viewers redrawing them their own way.
        form.needAppearances = approximated
    }

    /**
     * Burns every field's current appearance into its page and removes the form,
     * so the answers can no longer be changed, by anyone, in any app.
     */
    fun flatten(document: PDDocument) {
        document.documentCatalog.acroForm?.flatten()
    }

    private fun describe(
        field: PDTerminalField,
        document: PDDocument,
        pageIndex: IdentityHashMap<Any, Int>,
    ): FormField? {
        val widgets = field.widgets.mapNotNull { widget ->
            val page = widget.page ?: document.pages.firstOrNull { page ->
                page.annotations.any { it.cosObject === widget.cosObject }
            } ?: return@mapNotNull null
            val index = pageIndex[page.cosObject] ?: return@mapNotNull null
            val rect = widget.rectangle ?: return@mapNotNull null
            FieldWidget(index, displayRect(page, rect))
        }
        val base = FormField(
            name = field.fullyQualifiedName ?: return null,
            label = labelOf(field),
            kind = FieldKind.TEXT,
            value = "",
            readOnly = field.isReadOnly,
            widgets = widgets,
        )
        return when (field) {
            is PDTextField -> base.copy(
                value = field.value.orEmpty(),
                multiline = field.isMultiline,
                maxLength = field.maxLen.takeIf { it > 0 },
            )
            is PDCheckBox -> base.copy(
                kind = FieldKind.CHECKBOX,
                value = if (field.isChecked) field.onValue else FieldFormat.OFF,
                options = listOf(field.onValue),
            )
            is PDRadioButton -> {
                val options = field.onValues.toList()
                base.copy(
                    kind = FieldKind.RADIO,
                    value = field.value?.takeIf { it in options } ?: FieldFormat.OFF,
                    options = options,
                )
            }
            is PDChoice -> {
                val options = field.optionsExportValues
                val labels = field.optionsDisplayValues.takeIf { it.size == options.size } ?: options
                base.copy(
                    kind = FieldKind.CHOICE,
                    value = field.value.firstOrNull().orEmpty(),
                    options = options,
                    optionLabels = labels,
                )
            }
            else -> null
        }
    }

    /** The tooltip a form author wrote for the field, else its own name made readable. */
    private fun labelOf(field: PDField): String =
        field.alternateFieldName?.trim()?.takeIf { it.isNotEmpty() }
            ?: FieldFormat.readableName(field.partialName ?: field.fullyQualifiedName.orEmpty())

    /**
     * [rect], in [page]'s user space, as fractions of the page as displayed
     * from the top left: the inverse of [PdfDraw.applyDisplayTransform].
     */
    internal fun displayRect(page: PDPage, rect: PDRectangle): MarkRect {
        val box = page.cropBox
        val width = box.width
        val height = box.height
        val rotation = PdfOps.normalizeRotation(page.rotation)
        val quarter = rotation == 90 || rotation == 270
        val shownWidth = if (quarter) height else width
        val shownHeight = if (quarter) width else height

        fun toDisplay(x: Float, y: Float): Pair<Float, Float> {
            val px = x - box.lowerLeftX
            val py = y - box.lowerLeftY
            val (dx, dy) = when (rotation) {
                90 -> py to width - px
                180 -> width - px to height - py
                270 -> height - py to px
                else -> px to py
            }
            return dx / shownWidth to 1f - dy / shownHeight
        }
        val (x1, y1) = toDisplay(rect.lowerLeftX, rect.lowerLeftY)
        val (x2, y2) = toDisplay(rect.upperRightX, rect.upperRightY)
        return MarkRect(x1, y1, x2, y2).tidied()
    }
}

/** Naming and value conventions that need no PDF library, so they can be unit tested anywhere. */
object FieldFormat {
    const val OFF = FormField.OFF

    /**
     * Form tools name fields like "topmostSubform[0].Page1[0].first_name[0]" or
     * "txtFirstName". Turn that into "First name".
     */
    fun readableName(raw: String): String {
        val last = raw.substringAfterLast('.').replace(Regex("\\[\\d+]"), "")
        val words = last
            .replace(Regex("^(txt|chk|cb|rb|fld|tf|ddl|cmb|lst)(?=[A-Z_])"), "")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
            .lowercase()
        return words.replaceFirstChar { it.uppercase() }.ifEmpty { raw }
    }

    private val winAnsi = java.nio.charset.Charset.forName("windows-1252")

    /**
     * [value] with what the standard PDF fonts cannot draw swapped for '?'. Most
     * forms use those fonts, which cover Western European text and little else.
     */
    fun drawable(value: String): String {
        val encoder = winAnsi.newEncoder()
        return value.map { if (it == '\n' || encoder.canEncode(it)) it else '?' }.joinToString("")
    }
}
