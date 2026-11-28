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

/** Mixing documents page by page, and page labels. */
class PdfArrangeTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("arrangetest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** A document whose pages say [names], one each. */
    private fun pdf(file: String, vararg names: String): File = File(workDir, file).also { out ->
        PDDocument().use { document ->
            names.forEach { name ->
                val page = PDPage(PDRectangle.A6)
                document.addPage(page)
                PDPageContentStream(document, page).use {
                    it.beginText()
                    it.setFont(PDType1Font.HELVETICA, 14f)
                    it.newLineAtOffset(20f, 300f)
                    it.showText(name)
                    it.endText()
                }
            }
            document.save(out)
        }
    }

    private fun pageTexts(file: File): List<String> = PdfOps.load(file).use { document ->
        (1..document.numberOfPages).map { page ->
            com.tom_roush.pdfbox.text.PDFTextStripper().apply { startPage = page; endPage = page }.getText(document).trim()
        }
    }

    @Test
    fun aTwoSidedScanIsPutBackInOrder() = runBlocking {
        // Fronts 1, 3, 5; backs scanned from the end: 6, 4, 2.
        val fronts = pdf("fronts.pdf", "p1", "p3", "p5")
        val backs = pdf("backs.pdf", "p6", "p4", "p2")
        val output = File(workDir, "mixed.pdf")

        PdfArrange.mix(listOf(fronts, backs), output, reverseLast = true)

        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6"), pageTexts(output))
    }

    @Test
    fun filesOfDifferentLengthsAlternateUntilOneRunsOut() = runBlocking {
        val output = File(workDir, "mixed.pdf")
        PdfArrange.mix(listOf(pdf("a.pdf", "a1", "a2", "a3"), pdf("b.pdf", "b1")), output, reverseLast = false)
        assertEquals(listOf("a1", "b1", "a2", "a3"), pageTexts(output))
    }

    @Test
    fun labelsAreWrittenAndReadBack() = runBlocking {
        val input = pdf("book.pdf", "cover", "toc", "preface", "one", "two", "appendix")
        val ranges = listOf(
            LabelRange(0, LabelStyle.NONE, prefix = "Cover"),
            LabelRange(1, LabelStyle.ROMAN_LOWER),
            LabelRange(3, LabelStyle.DECIMAL),
            LabelRange(5, LabelStyle.DECIMAL, prefix = "A-", start = 1),
        )
        val output = File(workDir, "labelled.pdf")
        PdfEditor.applyPlan(input, output, EditPlanBuilder.build(6, listOf(SetPageLabels(ranges))))

        assertEquals(ranges, PdfArrange.readLabels(output))
        assertEquals(listOf("Cover", "i", "ii", "1", "2", "A-1"), PdfArrange.labels(ranges, 6))
        PdfOps.load(output).use { document ->
            assertEquals(listOf("Cover", "i", "ii", "1", "2", "A-1"), document.documentCatalog.pageLabels.labelsByPageIndices.toList())
        }

        val cleared = File(workDir, "cleared.pdf")
        PdfEditor.applyPlan(output, cleared, EditPlanBuilder.build(6, listOf(SetPageLabels(emptyList()))))
        assertTrue(PdfArrange.readLabels(cleared).isEmpty())
    }

    @Test
    fun romanNumeralsAndLettersCountTheWayReadersDo() {
        assertEquals(listOf("i", "iv", "ix", "xiv", "xl", "mcmxcix"), listOf(1, 4, 9, 14, 40, 1999).map(LabelStyle.ROMAN_LOWER::format))
        assertEquals(listOf("A", "Z", "AA", "BB"), listOf(1, 26, 27, 28).map(LabelStyle.LETTERS_UPPER::format))
    }
}
