package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfCleanupTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("cleanuptest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    /** Where the markup sits on page 1, in points from the bottom-left. */
    private val square = PDRectangle(100f, 500f, 120f, 80f)

    /**
     * Three pages: the first with text, a red square markup and a link; the
     * second blank; the third with a line of text.
     */
    private fun markedUpPdf(): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            repeat(3) { document.addPage(PDPage(PDRectangle.A4)) }
            for (index in listOf(0, 2)) {
                PDPageContentStream(document, document.getPage(index)).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(70f, 750f)
                    stream.showText("Page ${index + 1} has words on it")
                    stream.endText()
                }
            }
            val page = document.getPage(0)
            val markup = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
                rectangle = square
                val look = PDAppearanceStream(document).apply { bBox = PDRectangle(square.width, square.height) }
                PDPageContentStream(document, look).use { stream ->
                    stream.setNonStrokingColor(0.9f, 0.1f, 0.1f)
                    stream.addRect(0f, 0f, square.width, square.height)
                    stream.fill()
                }
                appearance = PDAppearanceDictionary().apply { setNormalAppearance(look) }
            }
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(70f, 745f, 200f, 20f)
                action = PDActionURI().apply { uri = "https://example.com" }
            }
            page.annotations = listOf(markup, link)
            document.save(file)
        }
        return file
    }

    private fun annotationTypes(file: File, pageIndex: Int) =
        PdfOps.load(file).use { document -> document.getPage(pageIndex).annotations.map { it.subtype } }

    /** Whether the middle of the square is red on a render of page 1 at one pixel per point. */
    private fun squareIsRed(image: PageImage): Boolean {
        val pixel = image.argbAt(
            (square.lowerLeftX + square.width / 2).toInt(),
            (PDRectangle.A4.height - square.lowerLeftY - square.height / 2).toInt(),
        )
        return (pixel shr 16 and 0xFF) > 180 && (pixel shr 8 and 0xFF) < 90 && (pixel and 0xFF) < 90
    }

    private suspend fun renderFirstPage(file: File) =
        PdfRasterizer.open(file).use { it.renderByWidth(0, PDRectangle.A4.width.toInt()) }

    @Test
    fun markupsAreCountedButLinksAreNot(): Unit = runBlocking {
        assertEquals(1, PdfCleanup.countMarkups(markedUpPdf()))
    }

    @Test
    fun burningInDrawsTheMarkupIntoThePageAndKeepsTheLink(): Unit = runBlocking {
        val source = markedUpPdf()
        val plan = EditPlanBuilder.build(3, listOf(SetMarkups(MarkupMode.FLATTEN)))
        val output = out("flatten")
        PdfEditor.applyPlan(source, output, plan)

        assertEquals(listOf("Link"), annotationTypes(output, 0))
        val exported = renderFirstPage(output)
        assertTrue("the square is now part of the page", squareIsRed(exported))
        exported.release()

        val preview = PdfEditor.renderPreview(source, plan, 0, PDRectangle.A4.width.toInt(), workDir)
        assertNotNull(preview)
        assertTrue("and the preview shows it the same way", squareIsRed(preview!!))
        preview.release()
    }

    @Test
    fun removingTakesTheMarkupAwayAndKeepsTheLink(): Unit = runBlocking {
        val output = out("remove")
        PdfEditor.applyPlan(markedUpPdf(), output, EditPlanBuilder.build(3, listOf(SetMarkups(MarkupMode.REMOVE))))

        assertEquals(listOf("Link"), annotationTypes(output, 0))
        val rendered = renderFirstPage(output)
        assertFalse(squareIsRed(rendered))
        rendered.release()
        assertEquals(0, PdfCleanup.countMarkups(output))
    }

    @Test
    fun aBurnedInMarkupMovesWithItsPageWhenTurned(): Unit = runBlocking {
        val output = out("turned")
        PdfEditor.applyPlan(
            markedUpPdf(),
            output,
            EditPlanBuilder.build(3, listOf(SetMarkups(MarkupMode.FLATTEN), RotatePages(180, setOf(0)))),
        )
        val rendered = renderFirstPage(output)
        // Turned half round, the square's middle lands opposite where it was.
        val pixel = rendered.argbAt(
            (PDRectangle.A4.width - square.lowerLeftX - square.width / 2).toInt(),
            (square.lowerLeftY + square.height / 2).toInt(),
        )
        assertTrue((pixel shr 16 and 0xFF) > 180 && (pixel shr 8 and 0xFF) < 90)
        rendered.release()
    }

    @Test
    fun theBlankPageIsFoundAndOnlyIt(): Unit = runBlocking {
        val source = markedUpPdf()
        val sources = listOf(PdfEditor.PlanSource(source, pageCount = 3))
        assertEquals(listOf(1), PdfCleanup.findBlankPages(sources, EditPlanBuilder.identity(3), workDir))
        // Something the user wrote on it makes it not blank.
        val written = EditPlanBuilder.build(
            3,
            listOf(AddMark(TextMark(id = 1L, sourceIndex = 1, text = "Notes", left = 0.2f, top = 0.2f, fontSize = 30f))),
        )
        assertEquals(emptyList<Int>(), PdfCleanup.findBlankPages(sources, written, workDir))
    }
}
