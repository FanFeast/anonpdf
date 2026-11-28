package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Pages laid out for printing: several to a sheet, a booklet, a poster. */
class PdfLayoutTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("layouttest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** One A6 page per name, the name written near its top left. */
    private fun pdf(vararg names: String, rotation: Int = 0): File = File(workDir, "src-${System.nanoTime()}.pdf").also { out ->
        PDDocument().use { document ->
            names.forEach { name ->
                val page = PDPage(PDRectangle.A6).apply { this.rotation = rotation }
                document.addPage(page)
                PDPageContentStream(document, page).use {
                    it.beginText()
                    it.setFont(PDType1Font.HELVETICA, 20f)
                    it.newLineAtOffset(20f, 360f)
                    it.showText(name)
                    it.endText()
                }
            }
            document.save(out)
        }
    }

    private fun layOut(input: File, layout: PrintLayout): File = File(workDir, "out-${System.nanoTime()}.pdf").also {
        runBlocking { PdfLayout.apply(input, it, layout, PaperSize.A4) }
    }

    /** Each page's words, left to right and top to bottom. */
    private fun words(file: File): List<String> = PdfOps.load(file).use { document ->
        (1..document.numberOfPages).map { page ->
            PDFTextStripper().apply { startPage = page; endPage = page; sortByPosition = true }
                .getText(document).split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ")
        }
    }

    @Test
    fun twoPagesGoSideBySideOnALandscapeSheet() {
        val output = layOut(pdf("p1", "p2", "p3"), PrintLayout.PerSheet(2))
        assertEquals(listOf("p1 p2", "p3"), words(output))
        PdfOps.load(output).use { document ->
            val sheet = document.getPage(0).mediaBox
            assertTrue("landscape A4", sheet.width > sheet.height && sheet.width in 841f..843f)
        }
    }

    @Test
    fun fourToASheetReadInRows() {
        assertEquals(listOf("p1 p2 p3 p4", "p5"), words(layOut(pdf("p1", "p2", "p3", "p4", "p5"), PrintLayout.PerSheet(4))))
    }

    @Test
    fun aBookletIsImposedSoTheFoldedStackReadsInOrder() {
        // Five pages fill two sheets; pages 6 to 8 are blank.
        // Sheet 1: [8 | 1] then [2 | 7]; sheet 2: [6 | 3] then [4 | 5].
        assertEquals(listOf("p1", "p2", "p3", "p4 p5"), words(layOut(pdf("p1", "p2", "p3", "p4", "p5"), PrintLayout.Booklet)))
    }

    @Test
    fun aPosterSpreadsEachPageOverItsSheets() {
        val output = layOut(pdf("p1"), PrintLayout.Poster(2, 2))
        // The name sits near the page's top left, so it shows on the top-left
        // sheet only. Every sheet carries the whole page, clipped, so count only
        // the letters that land on the paper.
        val shown = PdfOps.load(output).use { document ->
            (0 until document.numberOfPages).map { index ->
                val sheet = document.getPage(index).mediaBox
                val letters = StringBuilder()
                object : PDFTextStripper() {
                    override fun writeString(text: String, positions: MutableList<TextPosition>) {
                        positions.filter { it.xDirAdj in 0f..sheet.width && it.yDirAdj in 0f..sheet.height }
                            .forEach { letters.append(it.unicode) }
                    }
                }.apply { startPage = index + 1; endPage = index + 1 }.getText(document)
                letters.toString()
            }
        }
        assertEquals(listOf("p1", "", "", ""), shown)
        assertEquals(4, PdfLayout.sheets(PrintLayout.Poster(2, 2), 1))
    }

    @Test
    fun aTurnedPageIsPlacedTheWayItIsShown() {
        val input = pdf("sideways", rotation = 90)
        fun directions(file: File) = PdfOps.load(file).use { document ->
            val found = mutableListOf<Float>()
            object : PDFTextStripper() {
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    positions.forEach { found += it.dir }
                }
            }.getText(document)
            found
        }
        val output = layOut(input, PrintLayout.PerSheet(2))
        // The extractor measures direction from the page as turned by its own
        // /Rotate: upright there (0) is a quarter turn clockwise (270) on a sheet
        // that isn't turned, which is exactly how the page is shown.
        val before = directions(input)
        assertTrue(before.toString(), before.isNotEmpty() && before.all { it == 0f })
        val after = directions(output)
        assertTrue(after.toString(), after.isNotEmpty() && after.all { it == 270f })
    }
}
