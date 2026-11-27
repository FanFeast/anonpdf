package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfCompareTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("comparetest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** One page per entry, each holding that text on one line. */
    private fun pdf(vararg pages: String, rotateFirst: Int = 0): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            pages.forEachIndexed { index, text ->
                val page = PDPage(PDRectangle.A4)
                if (index == 0) page.rotation = rotateFirst
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 16f)
                    stream.newLineAtOffset(70f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private fun compare(original: File, changed: File): Pair<CompareResult, File> = runBlocking {
        val output = File(workDir, "report-${System.nanoTime()}.pdf")
        PdfCompare.compare(original, changed, output) to output
    }

    private fun pagesOf(file: File) = PdfOps.load(file).use { it.numberOfPages }

    /** How many pixels in [xRange] of the image are mostly [channel] (16 red, 8 green). */
    private fun tinted(image: PageImage, xRange: IntRange, channel: Int): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in xRange) {
                val pixel = image.argbAt(x, y)
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                val main = if (channel == 16) red else green
                val other = if (channel == 16) green else red
                // A translucent mark over white is a pale tint, not a strong colour.
                if (main - other > 25 && main - blue > 25) count++
            }
        }
        return count
    }

    @Test
    fun theShortestEditIsFound() {
        val a = listOf("the", "cat", "sat", "on", "the", "mat")
        val b = listOf("the", "dog", "sat", "on", "the", "mat", "today")
        val removed = BooleanArray(a.size)
        val added = BooleanArray(b.size)
        PdfCompare.shortestEdit(a, b, 0, a.size, 0, b.size)!!.apply(removed, added) { _, _ -> }
        assertEquals(listOf(1), removed.indices.filter { removed[it] })
        assertEquals(listOf(1, 6), added.indices.filter { added[it] })
    }

    @Test
    fun changedWordsAreCountedAndMarkedOnTheirOwnSide() {
        val (found, report) = compare(
            pdf("The fee is 500 dollars per month.", "Signed by Alice."),
            pdf("The fee is 650 dollars per month.", "Signed by Alice and Bob."),
        )
        assertEquals(CompareResult(added = 3, removed = 1, pagesWithChanges = 2), found)
        assertEquals(2, pagesOf(report))

        val image = runBlocking { PdfRasterizer.open(report).use { it.renderByWidth(0, 842) } }
        val half = image.width / 2
        assertTrue("removed words are red on the original's side", tinted(image, 0 until half, 16) > 20)
        assertTrue("and not on the changed side", tinted(image, half until image.width, 16) == 0)
        assertTrue("added words are green on the changed side", tinted(image, half until image.width, 8) > 20)
        image.release()
    }

    @Test
    fun anInsertedPageSitsAloneAndTheRestStayInLine() {
        val (found, report) = compare(
            pdf("First page text.", "Second page text."),
            pdf("A new cover page.", "First page text.", "Second page text."),
        )
        // "A new cover page." is five: the full stop counts as a word of its own.
        assertEquals(CompareResult(added = 5, removed = 0, pagesWithChanges = 1), found)
        assertEquals("one sheet per page, the new cover alone", 3, pagesOf(report))
        assertEquals(
            listOf(null to 0, 0 to 1, 1 to 2),
            PdfCompare.pairPages(2, 3, listOf(emptyMap(), mapOf(0 to 3), mapOf(1 to 3))),
        )
    }

    @Test
    fun identicalDocumentsSayTheSame() {
        val (found, report) = compare(pdf("Nothing changed here."), pdf("Nothing changed here."))
        assertTrue(found.identical)
        assertEquals(0, found.pagesWithChanges)
        assertEquals(1, pagesOf(report))
    }

    @Test
    fun chineseIsComparedCharacterByCharacter() {
        fun split(text: String) = PdfCompare.wordRanges(text).map { text.substring(it) }
        assertEquals(listOf("Fee", "1,500.00", "isn't", "due", "."), split("Fee 1,500.00 isn't due."))
        assertEquals(listOf("金", "额", "500", "元"), split("金额500元"))
        // Chinese has no spaces: one changed character is one change, not the whole line.
        val a = listOf("合", "同", "五", "百", "元")
        val b = listOf("合", "同", "六", "百", "元")
        val removed = BooleanArray(a.size)
        val added = BooleanArray(b.size)
        PdfCompare.shortestEdit(a, b, 0, a.size, 0, b.size)!!.apply(removed, added) { _, _ -> }
        assertEquals(1, removed.count { it })
        assertEquals(1, added.count { it })
    }

    @Test
    fun aTurnedPageIsShownTheWayItIsRead() {
        val (found, report) = compare(pdf("Quarterly report draft"), pdf("Quarterly report final", rotateFirst = 90))
        assertEquals(1, found.added)
        val image = runBlocking { PdfRasterizer.open(report).use { it.renderByWidth(0, 842) } }
        // The changed page, turned a quarter, is drawn landscape: wider than it is tall.
        var minX = Int.MAX_VALUE
        var maxX = 0
        var minY = Int.MAX_VALUE
        var maxY = 0
        for (y in 0 until image.height) {
            for (x in image.width / 2 until image.width) {
                val pixel = image.argbAt(x, y)
                val green = pixel shr 8 and 0xFF
                if (green - (pixel shr 16 and 0xFF) > 25) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue("the added word is marked", maxX > minX)
        // Text on a page turned clockwise runs down the sheet, so its mark is tall and thin.
        assertTrue("the mark follows the turned text: ${maxX - minX} x ${maxY - minY}", maxY - minY > maxX - minX)
        image.release()
    }
}
