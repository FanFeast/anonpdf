package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Form fields made in the editor: real fields in the saved file, where they were drawn. */
class PdfFieldsTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("fieldstest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun blank(rotation: Int = 0): File = File(workDir, "blank-$rotation.pdf").also { out ->
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4).apply { this.rotation = rotation })
            document.save(out)
        }
    }

    private val name = MarkRect(0.1f, 0.1f, 0.6f, 0.15f)
    private val notes = MarkRect(0.1f, 0.2f, 0.9f, 0.4f)
    private val agree = MarkRect(0.1f, 0.5f, 0.14f, 0.53f)

    private fun fields() = listOf(
        AddMark(FieldMark(1, 0, NewFieldKind.TEXT, "Full name", name)),
        AddMark(FieldMark(2, 0, NewFieldKind.MULTILINE, "Notes", notes)),
        AddMark(FieldMark(3, 0, NewFieldKind.CHECKBOX, "I agree", agree)),
    )

    private fun close(a: MarkRect, b: MarkRect) =
        abs(a.left - b.left) < 0.005f && abs(a.top - b.top) < 0.005f && abs(a.right - b.right) < 0.005f && abs(a.bottom - b.bottom) < 0.005f

    @Test
    fun theSavedFileHasRealFieldsWhereTheyWereDrawn() = runBlocking {
        for (rotation in listOf(0, 90)) {
            val output = File(workDir, "form-$rotation.pdf")
            PdfEditor.applyPlan(blank(rotation), output, EditPlanBuilder.build(1, fields()))

            val form = PdfForms.read(output)
            assertEquals(listOf("Full name", "Notes", "I agree"), form.fields.map { it.name })
            assertEquals(listOf(FieldKind.TEXT, FieldKind.TEXT, FieldKind.CHECKBOX), form.fields.map { it.kind })
            assertTrue(form.fields[1].multiline)
            val placed = form.fields.map { it.widgets.single().rect }
            listOf(name, notes, agree).zip(placed).forEach { (drawn, saved) ->
                assertTrue("rotation $rotation: drew $drawn, saved $saved", close(drawn, saved))
            }
        }
    }

    @Test
    fun theNewFieldsCanBeFilledIn() = runBlocking {
        val output = File(workDir, "form.pdf")
        PdfEditor.applyPlan(blank(), output, EditPlanBuilder.build(1, fields()))
        val filled = File(workDir, "filled.pdf")
        PdfEditor.applyPlan(
            output, filled,
            EditPlanBuilder.build(1, listOf(FillForm(mapOf("Full name" to "Ada Lovelace", "I agree" to "Yes"), flatten = true))),
        )
        assertTrue(PdfOps.extractText(filled).contains("Ada Lovelace"))
    }

    @Test
    fun aNameAlreadyTakenIsMadeUnique() = runBlocking {
        val output = File(workDir, "twice.pdf")
        PdfEditor.applyPlan(
            blank(), output,
            EditPlanBuilder.build(1, listOf(AddMark(FieldMark(1, 0, NewFieldKind.TEXT, "Name", name)), AddMark(FieldMark(2, 0, NewFieldKind.TEXT, "Name", notes)))),
        )
        assertEquals(listOf("Name", "Name 2"), PdfForms.read(output).fields.map { it.name })
    }

    @Test
    fun theFieldShowsInThePreview() = runBlocking {
        val plan = EditPlanBuilder.build(1, fields())
        val preview = PdfEditor.renderPreview(blank(), plan, outputPosition = 0, targetWidthPx = 400, workDir = workDir)
        assertNotNull(preview)
        // The middle of the notes box is the field's pale fill, not the white page.
        val x = ((notes.left + notes.right) / 2 * preview!!.width).toInt()
        val y = ((notes.top + notes.bottom) / 2 * preview.height).toInt()
        assertTrue("got ${Integer.toHexString(preview.argbAt(x, y))}", preview.argbAt(x, y) != TestPlatform.WHITE)
    }
}
