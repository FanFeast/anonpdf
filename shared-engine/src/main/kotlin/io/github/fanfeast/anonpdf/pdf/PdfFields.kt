package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlin.math.min

/**
 * New form fields: text boxes and checkboxes put onto a page, with the
 * appearance a viewer shows before anyone has filled them in.
 */
internal object PdfFields {

    private val BORDER = floatArrayOf(0.45f, 0.45f, 0.5f)
    private val FILL = floatArrayOf(0.93f, 0.95f, 1f)

    fun add(document: PDDocument, page: PDPage, fields: List<FieldMark>) {
        if (fields.isEmpty()) return
        val form = document.documentCatalog.acroForm ?: PDAcroForm(document).also { document.documentCatalog.acroForm = it }
        if (form.defaultResources == null) form.defaultResources = PDResources()
        form.defaultResources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        if (form.defaultAppearance.isNullOrEmpty()) form.defaultAppearance = "/Helv 0 Tf 0 g"
        // Without this, a viewer may draw the fields its own way, or not at all.
        form.needAppearances = false

        val taken = form.fieldTree.mapTo(HashSet()) { it.fullyQualifiedName }
        for (mark in fields) {
            val rect = userRect(page, mark.rect)
            if (rect.width < 2f || rect.height < 2f) continue
            val name = unique(mark.name.trim().ifEmpty { "Field" }, taken)
            val field: PDField = when (mark.kind) {
                NewFieldKind.TEXT, NewFieldKind.MULTILINE -> PDTextField(form).apply {
                    isMultiline = mark.kind == NewFieldKind.MULTILINE
                    // Sized to the box: a short box takes smaller type.
                    defaultAppearance = "/Helv ${fontSizeFor(rect, isMultiline)} Tf 0 g"
                }
                NewFieldKind.CHECKBOX -> PDCheckBox(form)
            }
            field.partialName = name
            field.alternateFieldName = name
            val widget = field.widgets.first().apply {
                rectangle = rect
                this.page = page
                isPrinted = true
                appearanceCharacteristics = PDAppearanceCharacteristicsDictionary(com.tom_roush.pdfbox.cos.COSDictionary()).apply {
                    borderColour = PDColor(BORDER, PDDeviceRGB.INSTANCE)
                    background = PDColor(FILL, PDDeviceRGB.INSTANCE)
                    // Shown the way the page is shown, on a turned page too.
                    rotation = PdfOps.normalizeRotation(page.rotation)
                }
                borderStyle = PDBorderStyleDictionary().apply { width = 1f }
            }
            page.annotations = page.annotations + widget
            form.fields = form.fields + field
            when (field) {
                is PDTextField -> field.setValue("")
                is PDCheckBox -> {
                    widget.appearance = checkboxAppearance(document, rect, PdfOps.normalizeRotation(page.rotation))
                    field.cosObject.setItem(COSName.V, COSName.Off)
                    widget.cosObject.setItem(COSName.AS, COSName.Off)
                }
            }
        }
    }

    private fun unique(name: String, taken: MutableSet<String>): String {
        var candidate = name.replace('.', ' ')
        var n = 2
        while (!taken.add(candidate)) candidate = "${name.replace('.', ' ')} ${n++}"
        return candidate
    }

    private fun fontSizeFor(rect: PDRectangle, multiline: Boolean): Int {
        val short = min(rect.width, rect.height)
        return if (multiline) 10 else (short * 0.6f).toInt().coerceIn(6, 12)
    }

    /** Off: an empty box. Yes: the box with a tick. Drawn upright on the page as shown. */
    private fun checkboxAppearance(document: PDDocument, rect: PDRectangle, rotation: Int): PDAppearanceDictionary {
        fun stream(ticked: Boolean) = PDAppearanceStream(document).apply {
            val turned = rotation == 90 || rotation == 270
            val w = if (turned) rect.height else rect.width
            val h = if (turned) rect.width else rect.height
            bBox = PDRectangle(w, h)
            // Back from the page as shown to the widget's own unturned box.
            when (rotation) {
                90 -> setMatrix(com.tom_roush.pdfbox.util.Matrix(0f, 1f, -1f, 0f, h, 0f).createAffineTransform())
                180 -> setMatrix(com.tom_roush.pdfbox.util.Matrix(-1f, 0f, 0f, -1f, w, h).createAffineTransform())
                270 -> setMatrix(com.tom_roush.pdfbox.util.Matrix(0f, -1f, 1f, 0f, 0f, w).createAffineTransform())
            }
            resources = PDResources()
            PDPageContentStream(document, this).use { s ->
                s.setNonStrokingColor(FILL[0], FILL[1], FILL[2])
                s.addRect(0f, 0f, w, h)
                s.fill()
                s.setStrokingColor(BORDER[0], BORDER[1], BORDER[2])
                s.setLineWidth(1f)
                s.addRect(0.5f, 0.5f, w - 1f, h - 1f)
                s.stroke()
                if (ticked) {
                    s.setStrokingColor(0f, 0f, 0f)
                    s.setLineWidth(min(w, h) * 0.12f)
                    s.moveTo(w * 0.2f, h * 0.52f)
                    s.lineTo(w * 0.42f, h * 0.25f)
                    s.lineTo(w * 0.8f, h * 0.78f)
                    s.stroke()
                }
            }
        }
        return PDAppearanceDictionary().apply {
            normalAppearance = PDAppearanceEntry(com.tom_roush.pdfbox.cos.COSDictionary().apply {
                setItem(COSName.getPDFName("Yes"), stream(true))
                setItem(COSName.Off, stream(false))
            })
        }
    }

    /**
     * [rect], a fraction of the page as shown from the top left, in the page's
     * own user space: the inverse of [PdfForms.displayRect].
     */
    fun userRect(page: PDPage, rect: MarkRect): PDRectangle {
        val box = page.cropBox
        val width = box.width
        val height = box.height
        val rotation = PdfOps.normalizeRotation(page.rotation)
        val quarter = rotation == 90 || rotation == 270
        val shownWidth = if (quarter) height else width
        val shownHeight = if (quarter) width else height
        fun toUser(fx: Float, fy: Float): Pair<Float, Float> {
            val dx = fx * shownWidth
            val dy = (1f - fy) * shownHeight
            val (px, py) = when (rotation) {
                90 -> width - dy to dx
                180 -> width - dx to height - dy
                270 -> dy to height - dx
                else -> dx to dy
            }
            return px + box.lowerLeftX to py + box.lowerLeftY
        }
        val (x1, y1) = toUser(rect.left, rect.top)
        val (x2, y2) = toUser(rect.right, rect.bottom)
        return PDRectangle(minOf(x1, x2), minOf(y1, y2), kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
    }
}
