package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

class PdfScanTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("scantest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    /**
     * A page of text, as a crooked scan would hold it: every line turned
     * [lean] degrees anticlockwise about the middle of the page. A red block
     * below the text gives greyscale something to work on.
     */
    private fun leaningPdf(lean: Float): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.saveGraphicsState()
                stream.transform(Matrix.getRotateInstance(Math.toRadians(lean.toDouble()), 297f, 421f))
                stream.transform(Matrix.getTranslateInstance(-297f, -421f))
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                for (line in 0 until 30) {
                    stream.setTextMatrix(Matrix.getTranslateInstance(60f, 760f - line * 18f))
                    stream.showText("The quick brown fox jumps over the lazy dog, line ${line + 1} of the scan.")
                }
                stream.endText()
                stream.restoreGraphicsState()
                stream.setNonStrokingColor(0.9f, 0.1f, 0.1f)
                stream.addRect(60f, 60f, 200f, 100f)
                stream.fill()
            }
            document.save(file)
        }
        return file
    }

    private fun sources(file: File) = listOf(PdfEditor.PlanSource(file, pageCount = 1))

    private fun lean(file: File, plan: EditPlan = EditPlanBuilder.identity(1)) =
        runBlocking { PdfScan.measureLean(sources(file), plan, 0, 900, workDir) }

    @Test
    fun aLevelPageMeasuresLevel() {
        assertEquals(0f, lean(leaningPdf(0f)), 0.15f)
    }

    @Test
    fun aCrookedPageMeasuresTheTurnThatLevelsIt() {
        assertEquals(-3f, lean(leaningPdf(3f)), 0.3f)
        assertEquals(2f, lean(leaningPdf(-2f)), 0.3f)
    }

    @Test
    fun aBlankPageHasNoLeanToFind(): Unit = runBlocking {
        val file = out("blank")
        PdfScan.writeBlankPage(400f, 600f, file)
        assertEquals(0f, lean(file), 0f)
        PdfOps.load(file).use {
            assertEquals(400f, it.getPage(0).mediaBox.width)
            assertEquals(600f, it.getPage(0).mediaBox.height)
        }
    }

    @Test
    fun straighteningByTheMeasuredTurnLevelsThePageAndKeepsItsText(): Unit = runBlocking {
        val source = leaningPdf(4f)
        val turn = lean(source)
        val plan = EditPlanBuilder.build(1, listOf(TiltPages(mapOf(0 to turn))))
        val output = out("straight")
        PdfEditor.applyPlan(source, output, plan)
        assertEquals("level once straightened", 0f, lean(output), 0.3f)
        PdfOps.load(output).use { document ->
            assertTrue(PDFTextStripper().getText(document).contains("lazy dog"))
        }
    }

    @Test
    fun greyscaleTakesTheColourAndLeavesTheText(): Unit = runBlocking {
        val output = out("grey")
        PdfEditor.applyPlan(leaningPdf(0f), output, EditPlanBuilder.build(1, listOf(SetGreyscale(true))))
        val image = PdfRasterizer.open(output).use { it.renderByWidth(0, 595) }
        val pixel = image.argbAt(160, 842 - 110)
        val red = pixel shr 16 and 0xFF
        val green = pixel shr 8 and 0xFF
        val blue = pixel and 0xFF
        assertTrue("the red block is grey now: $red $green $blue", abs(red - green) < 12 && abs(green - blue) < 12)
        assertTrue("and not white", red < 200)
        image.release()
        PdfOps.load(output).use { document ->
            assertTrue("text is still text", PDFTextStripper().getText(document).contains("lazy dog"))
        }
    }

    @Test
    fun aDuplicateIsThePageAsItStandsWithoutTheStamps(): Unit = runBlocking {
        val plan = EditPlanBuilder.build(
            1,
            listOf(
                RotatePages(90, setOf(0)),
                AddMark(TextMark(id = 1L, sourceIndex = 0, text = "Reviewed", left = 0.1f, top = 0.1f)),
                SetWatermark(WatermarkOptions(text = "DRAFT")),
            ),
        )
        val output = out("copy")
        assertTrue(PdfEditor.writePageAsShown(sources(leaningPdf(0f)), plan, 0, workDir, output))
        PdfOps.load(output).use { document ->
            assertEquals(1, document.numberOfPages)
            val text = PDFTextStripper().getText(document)
            assertTrue("the added text came along", text.contains("Reviewed"))
            assertTrue("the watermark did not: the copy gets it again on export", !text.contains("DRAFT"))
            assertEquals("turned as it was", 90, PdfOps.normalizeRotation(document.getPage(0).rotation))
        }
    }
}
