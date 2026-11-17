package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Text in scripts the built-in PDF fonts lack is drawn as the system's letter shapes, never as "?". */
class PdfTextTest {

    private lateinit var workDir: File
    private lateinit var blank: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("texttest").apply {
            deleteRecursively()
            mkdirs()
        }
        blank = File(workDir, "blank.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.save(blank)
        }
    }

    private val samples = listOf("Привет мир", "你好世界", "नमस्ते दुनिया", "Ελληνικά", "Mixed: Jane 日本")

    @Test
    fun otherScriptsBecomeShapesAndWesternTextStaysText() {
        for (text in samples) {
            val line = PdfText.line(text, PDType1Font.HELVETICA, bold = false, 20f)
            assertTrue("$text was not shaped", line is PdfText.Line.Shapes)
            assertTrue("$text has no width", line.width > 20f)
            assertTrue("$text should be drawable", PdfText.canDraw(text))
        }
        assertTrue(PdfText.line("Café 12", PDType1Font.HELVETICA, bold = false, 20f) is PdfText.Line.Standard)
        assertFalse(PdfText.canDraw("͸"))
    }

    /** Dark pixels in the band [topPt]..[bottomPt] points from the top of an A4 page. */
    private fun ink(image: PageImage, width: Int, topPt: Float, bottomPt: Float): Int {
        val scale = width / PDRectangle.A4.width
        var dark = 0
        for (x in 0 until width) {
            for (y in (topPt * scale).toInt() until (bottomPt * scale).toInt()) {
                val argb = image.argbAt(x, y)
                val luminance = ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)
                if (luminance < 3 * 128) dark++
            }
        }
        return dark
    }

    @Test
    fun aTextBoxInEveryScriptIsDrawnAndExported() = runBlocking {
        for (text in samples) {
            val mark = TextMark(id = 1, sourceIndex = 0, text = "$text\n$text", left = 0.1f, top = 0.1f, fontSize = 24f)
            val plan = EditPlanBuilder.build(1, listOf(AddMark(mark)))
            val width = 600
            val preview = PdfEditor.renderPreview(blank, plan, 0, width, workDir)!!
            // Both lines, 24pt apart from about 84pt down the page.
            val pageHeight = PDRectangle.A4.height
            assertTrue("no ink for $text", ink(preview, width, pageHeight * 0.1f, pageHeight * 0.1f + 30f) > 100)
            assertTrue("no second line for $text", ink(preview, width, pageHeight * 0.1f + 30f, pageHeight * 0.1f + 60f) > 100)

            val output = File(workDir, "text.pdf")
            PdfEditor.applyPlan(blank, output, plan)
            PDDocument.load(output).use { assertFalse("? in $text", PDFTextStripper().getText(it).contains('?')) }
        }
    }

    @Test
    fun watermarksAndPageNumbersInOtherScriptsHaveNoQuestionMarks() = runBlocking {
        val watermarked = File(workDir, "watermarked.pdf")
        PdfOps.watermark(blank, watermarked, WatermarkOptions(text = "गोपनीय", layout = WatermarkLayout.TILED))
        val numbered = File(workDir, "numbered.pdf")
        PdfOps.addPageNumbers(watermarked, numbered, PageNumberOptions(format = "Страница {n} / 页 {total}"))
        PDDocument.load(numbered).use { assertFalse(PDFTextStripper().getText(it).contains('?')) }

        val width = 600
        val plan = EditPlanBuilder.identity(1)
        val preview = PdfEditor.renderPreview(numbered, plan, 0, width, workDir)!!
        val height = PDRectangle.A4.height
        // The number sits 28pt above the bottom edge.
        assertTrue("no page number", ink(preview, width, height - 45f, height - 20f) > 20)
    }
}
