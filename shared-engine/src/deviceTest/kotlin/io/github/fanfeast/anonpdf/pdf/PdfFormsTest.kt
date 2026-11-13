package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfFormsTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("formstest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** Appearance states for a checkbox or radio button: [on] and Off. */
    private fun states(document: PDDocument, on: String) = PDAppearanceDictionary(
        COSDictionary().apply {
            setItem(COSName.N, COSDictionary().apply {
                for (state in listOf(on, "Off")) {
                    setItem(state, PDAppearanceStream(document).apply { bBox = PDRectangle(12f, 12f) })
                }
            })
        },
    )

    private fun place(widget: PDAnnotationWidget, page: PDPage, rect: PDRectangle) {
        widget.rectangle = rect
        widget.page = page
        page.annotations.add(widget)
    }

    /** A one-page form: a name, a read-only reference, a checkbox, a radio pair and a dropdown. */
    private fun formPdf(rotation: Int = 0): File {
        val file = File(workDir, "form-$rotation.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4).apply { this.rotation = rotation }
            document.addPage(page)
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply {
                put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            }
            form.defaultAppearance = "/Helv 0 Tf 0 g"

            val name = PDTextField(form).apply {
                partialName = "txtFirstName"
                defaultAppearance = "/Helv 12 Tf 0 g"
            }
            place(name.widgets.single(), page, PDRectangle(50f, 700f, 200f, 20f))

            val reference = PDTextField(form).apply {
                partialName = "reference"
                alternateFieldName = "Reference number"
                defaultAppearance = "/Helv 12 Tf 0 g"
                setValue("R-1")
                isReadOnly = true
            }
            place(reference.widgets.single(), page, PDRectangle(50f, 660f, 200f, 20f))

            val agree = PDCheckBox(form).apply { partialName = "agree" }
            agree.widgets.single().apply {
                appearance = states(document, "Yes")
                setAppearanceState("Off")
            }
            place(agree.widgets.single(), page, PDRectangle(50f, 620f, 12f, 12f))

            val size = PDRadioButton(form).apply { partialName = "size" }
            val small = PDAnnotationWidget().apply {
                appearance = states(document, "small")
                setAppearanceState("Off")
            }
            val large = PDAnnotationWidget().apply {
                appearance = states(document, "large")
                setAppearanceState("Off")
            }
            size.widgets = listOf(small, large)
            place(small, page, PDRectangle(50f, 580f, 12f, 12f))
            place(large, page, PDRectangle(80f, 580f, 12f, 12f))

            val colour = PDComboBox(form).apply {
                partialName = "colour"
                defaultAppearance = "/Helv 12 Tf 0 g"
                setOptions(listOf("r", "g"), listOf("Red", "Green"))
            }
            place(colour.widgets.single(), page, PDRectangle(50f, 540f, 120f, 20f))

            form.fields.addAll(listOf(name, reference, agree, size, colour))
            document.save(file)
        }
        return file
    }

    @Test
    fun readsEveryFillableField() = runBlocking {
        val fields = PdfForms.read(formPdf()).fields.associateBy { it.name }

        val name = fields.getValue("txtFirstName")
        assertEquals(FieldKind.TEXT, name.kind)
        assertEquals("First name", name.label)
        assertEquals("", name.value)

        val reference = fields.getValue("reference")
        assertEquals("Reference number", reference.label)
        assertEquals("R-1", reference.value)
        assertTrue(reference.readOnly)

        val agree = fields.getValue("agree")
        assertEquals(FieldKind.CHECKBOX, agree.kind)
        assertEquals(listOf("Yes"), agree.options)
        assertEquals(FormField.OFF, agree.value)

        val size = fields.getValue("size")
        assertEquals(FieldKind.RADIO, size.kind)
        assertEquals(setOf("small", "large"), size.options.toSet())
        assertEquals(2, size.widgets.size)

        val colour = fields.getValue("colour")
        assertEquals(FieldKind.CHOICE, colour.kind)
        assertEquals(listOf("r", "g"), colour.options)
        assertEquals(listOf("Red", "Green"), colour.optionLabels)
    }

    @Test
    fun widgetsArePlacedAsThePageIsDisplayed() = runBlocking {
        val a4 = PDRectangle.A4
        val upright = PdfForms.read(formPdf()).fields.first { it.name == "txtFirstName" }.widgets.single()
        assertEquals(0, upright.sourceIndex)
        assertEquals(50f / a4.width, upright.rect.left, 0.001f)
        assertEquals(1f - 720f / a4.height, upright.rect.top, 0.001f)
        assertEquals(250f / a4.width, upright.rect.right, 0.001f)
        assertEquals(1f - 700f / a4.height, upright.rect.bottom, 0.001f)

        // Turned a quarter: the page shows sideways, so the field's box does too.
        val turned = PdfForms.read(formPdf(rotation = 90)).fields.first { it.name == "txtFirstName" }.widgets.single()
        assertEquals(700f / a4.height, turned.rect.left, 0.001f)
        assertEquals(50f / a4.width, turned.rect.top, 0.001f)
        assertEquals(720f / a4.height, turned.rect.right, 0.001f)
        assertEquals(250f / a4.width, turned.rect.bottom, 0.001f)
    }

    private fun fill(values: Map<String, String>, flatten: Boolean = false): File {
        val input = formPdf()
        val output = File(workDir, "filled-${System.nanoTime()}.pdf")
        val plan = EditPlanBuilder.build(1, listOf(FillForm(values, flatten)))
        assertTrue(plan.hasChanges)
        runBlocking { PdfEditor.applyPlan(input, output, plan) }
        return output
    }

    @Test
    fun fillingSetsEveryKindOfField() = runBlocking {
        val output = fill(
            mapOf(
                "txtFirstName" to "Jane",
                "agree" to "Yes",
                "size" to "large",
                "colour" to "g",
                // Neither of these may change anything.
                "reference" to "R-2",
                "no such field" to "x",
            ),
        )
        val fields = PdfForms.read(output).fields.associateBy { it.name }
        assertEquals("Jane", fields.getValue("txtFirstName").value)
        assertEquals("Yes", fields.getValue("agree").value)
        assertEquals("large", fields.getValue("size").value)
        assertEquals("g", fields.getValue("colour").value)
        assertEquals("R-1", fields.getValue("reference").value)
    }

    @Test
    fun unfillingAndUncheckingWork() = runBlocking {
        val output = fill(mapOf("agree" to FormField.OFF, "size" to FormField.OFF, "txtFirstName" to ""))
        val fields = PdfForms.read(output).fields.associateBy { it.name }
        assertEquals(FormField.OFF, fields.getValue("agree").value)
        assertEquals(FormField.OFF, fields.getValue("size").value)
        assertEquals("", fields.getValue("txtFirstName").value)
    }

    @Test
    fun textTheFormsFontCannotDrawIsStillKept() = runBlocking {
        val output = fill(mapOf("txtFirstName" to "Jane \u65e5\u672c"))
        val name = PdfForms.read(output).fields.first { it.name == "txtFirstName" }
        assertEquals("Jane \u65e5\u672c", name.value)
    }

    @Test
    fun lockingBurnsTheAnswersIntoThePage() = runBlocking {
        val output = fill(mapOf("txtFirstName" to "Jane"), flatten = true)
        assertTrue(PdfForms.read(output).isEmpty)
        PDDocument.load(output).use { document ->
            assertTrue(PDFTextStripper().getText(document).contains("Jane"))
            val widgets = document.getPage(0).annotations.filter { it.subtype == "Widget" }
            assertTrue(widgets.isEmpty())
        }
    }

    /** Dark pixels inside the first-name field's box, in a [width]-wide render. */
    private fun inkInNameField(image: PageImage, width: Int): Int {
        val scale = width / PDRectangle.A4.width
        val left = (50f * scale).toInt()
        val right = (250f * scale).toInt()
        val top = ((PDRectangle.A4.height - 720f) * scale).toInt()
        val bottom = ((PDRectangle.A4.height - 700f) * scale).toInt()
        var dark = 0
        for (x in left until right) {
            for (y in top until bottom) {
                val argb = image.argbAt(x, y)
                val luminance = ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)
                if (luminance < 3 * 128) dark++
            }
        }
        return dark
    }

    @Test
    fun thePreviewDrawsTheFilledValue() = runBlocking {
        val input = formPdf()
        val sources = listOf(PdfEditor.PlanSource(input, null, 1))
        val width = 600
        val empty = PdfEditor.renderPreview(sources, EditPlanBuilder.identity(1), 0, width, workDir)!!
        // Left as a live field, not locked: the platform renderer must draw the
        // field's appearance, or the user would not see what they typed.
        val plan = EditPlanBuilder.build(1, listOf(FillForm(mapOf("txtFirstName" to "Jane"), flatten = false)))
        val filled = PdfEditor.renderPreview(sources, plan, 0, width, workDir)!!
        assertEquals(0, inkInNameField(empty, width))
        assertTrue("no ink where Jane should be", inkInNameField(filled, width) > 20)
    }

    @Test
    fun aDocumentWithoutAFormHasNoFields() = runBlocking {
        val file = File(workDir, "plain.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.save(file)
        }
        val info = PdfForms.read(file)
        assertTrue(info.isEmpty)
        assertFalse(info.xfaOnly)
        assertNull(info.fields.firstOrNull())
    }
}
