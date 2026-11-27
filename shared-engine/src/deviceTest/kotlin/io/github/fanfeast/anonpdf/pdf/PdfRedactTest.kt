package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Find and redact has one job: what it finds is gone from the file, and the box
 * sits on it. These tests check both, through rotation and against the preview.
 */
class PdfRedactTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("redacttest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private val font = PDType1Font.HELVETICA
    private val size = 14f
    private val left = 70f

    /** Baselines of the sample lines, in points from the bottom of an A4 page. */
    private val lines = listOf(
        700f to "Contact jane.doe@example.com today",
        650f to "Call +1 (555) 123-4567 now",
        600f to "Card 4111 1111 1111 1111",
        550f to "Keep this line",
        500f to "Project Falcon is secret",
    )

    /** Page 1 holds every sample line; any later page only the last one's opposite. */
    private fun samplePdf(pages: Int = 1): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, size)
                    for ((baseline, text) in if (index == 0) lines else listOf(550f to "Nothing to hide")) {
                        stream.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(left, baseline))
                        stream.showText(text)
                    }
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    private fun words(text: String) = RedactRule(RedactKind.WORDS, text)

    private fun redact(pageCount: Int, rule: RedactRule, targets: Set<Int> = (0 until pageCount).toSet()) =
        EditPlanBuilder.build(pageCount, listOf(AddRedaction(Redaction(rule, targets))))

    private fun pageText(file: File, pageNumber: Int): String =
        PdfOps.load(file).use { document ->
            PDFTextStripper().apply {
                startPage = pageNumber
                endPage = pageNumber
            }.getText(document)
        }

    private fun isDark(bitmap: PageImage, x: Int, y: Int): Boolean {
        val pixel = bitmap.argbAt(x, y)
        return maxOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF) < 60
    }

    /**
     * Whether most of [word]'s area is dark: a box over it, rather than letters,
     * which leave paper between their strokes. The page is upright A4 rendered at
     * one pixel per point, unless [turned] says it was turned a quarter clockwise.
     */
    private fun covered(bitmap: PageImage, line: String, word: String, baseline: Float, turned: Boolean = false): Boolean {
        val start = left + font.getStringWidth(line.substringBefore(word)) / 1000f * size
        val end = start + font.getStringWidth(word) / 1000f * size
        val top = PDRectangle.A4.height - baseline - size * 0.6f
        val bottom = PDRectangle.A4.height - baseline
        var dark = 0
        var total = 0
        var y = top
        while (y < bottom) {
            var x = start + 1f
            while (x < end - 1f) {
                total++
                val (px, py) = if (turned) (PDRectangle.A4.height - y) to x else x to y
                if (isDark(bitmap, px.toInt(), py.toInt())) dark++
                x += 1f
            }
            y += 1f
        }
        return dark > total * 0.9f
    }

    @Test
    fun wordsAreBlackedOutAndGoneFromTheFile(): Unit = runBlocking {
        val source = samplePdf()
        val output = out("words")
        PdfEditor.applyPlan(source, output, redact(1, words("falcon")))

        assertFalse("the redacted word must not be extractable", pageText(output, 1).contains("Falcon", ignoreCase = true))

        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, PDRectangle.A4.width.toInt()) }
        assertTrue("the box covers the word", covered(rendered, "Project Falcon is secret", "Falcon", 500f))
        assertFalse("words beside it are not covered", covered(rendered, "Project Falcon is secret", "secret", 500f))
        assertFalse("other lines are not covered", covered(rendered, "Keep this line", "this", 550f))
        rendered.release()
    }

    @Test
    fun eachKindFindsItsOwnSortOfText(): Unit = runBlocking {
        val sources = listOf(PdfEditor.PlanSource(samplePdf(), pageCount = 1))
        fun count(kind: RedactKind) = runBlocking { PdfRedact.count(sources, RedactRule(kind), setOf(0)) }

        assertEquals(RedactCount(1, 1), count(RedactKind.EMAILS))
        assertEquals("the phone number, not the card", RedactCount(1, 1), count(RedactKind.PHONES))
        assertEquals("the card, not the phone number", RedactCount(1, 1), count(RedactKind.NUMBERS))
        assertEquals(RedactCount(0, 0), PdfRedact.count(sources, words("nowhere"), setOf(0)))
        assertEquals("whitespace in the query matches the page's", RedactCount(1, 1), PdfRedact.count(sources, words("keep   THIS"), setOf(0)))
    }

    @Test
    fun theEmailIsRemovedButTheRestOfTheLineStaysVisible(): Unit = runBlocking {
        val source = samplePdf()
        val output = out("email")
        PdfEditor.applyPlan(source, output, redact(1, RedactRule(RedactKind.EMAILS)))

        assertFalse(pageText(output, 1).contains("example.com"))
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, PDRectangle.A4.width.toInt()) }
        val line = "Contact jane.doe@example.com today"
        assertTrue(covered(rendered, line, "jane.doe@example.com", 700f))
        assertFalse("the word after the address is left alone", covered(rendered, line, "today", 700f))
        assertFalse("and the word before it", covered(rendered, line, "Contact", 700f))
        rendered.release()
    }

    @Test
    fun onlyTheChosenPagesAreSearched(): Unit = runBlocking {
        val source = samplePdf(pages = 2)
        val sources = listOf(PdfEditor.PlanSource(source, pageCount = 2))
        assertEquals(RedactCount(0, 0), PdfRedact.count(sources, words("falcon"), setOf(1)))

        val output = out("pages")
        PdfEditor.applyPlan(source, output, redact(2, words("hide")))
        assertFalse(pageText(output, 2).contains("hide"))
        assertTrue("a page without a match keeps its text", pageText(output, 1).contains("Falcon"))
    }

    @Test
    fun aRotatedPageIsRedactedWhereItsTextIsAndPreviewsAsExported(): Unit = runBlocking {
        val source = samplePdf()
        val plan = EditPlanBuilder.build(
            1,
            listOf(RotatePages(90, setOf(0)), AddRedaction(Redaction(words("falcon"), setOf(0)))),
        )
        val output = out("rotated")
        PdfEditor.applyPlan(source, output, plan)
        assertFalse(pageText(output, 1).contains("Falcon", ignoreCase = true))

        val exported = PdfRasterizer.open(output).use { it.renderByWidth(0, PDRectangle.A4.height.toInt()) }
        assertTrue("turned a quarter, the page is landscape", exported.width > exported.height)
        assertTrue(
            "the box follows the text round",
            covered(exported, "Project Falcon is secret", "Falcon", 500f, turned = true),
        )
        assertFalse(covered(exported, "Project Falcon is secret", "secret", 500f, turned = true))

        val preview = PdfEditor.renderPreview(source, plan, 0, exported.width, workDir)
        assertNotNull(preview)
        var differing = 0
        var total = 0
        for (py in 0 until exported.height step 4) {
            for (px in 0 until exported.width step 4) {
                total++
                if (isDark(preview!!, px, py) != isDark(exported, px, py)) differing++
            }
        }
        assertTrue("the preview shows the same boxes as the export", differing.toFloat() / total < 0.01f)
        preview!!.release()
        exported.release()
    }

    @Test
    fun redactionsAddUpAndClearTogether() {
        val plan = EditPlanBuilder.build(
            1,
            listOf(
                AddRedaction(Redaction(words("a"), setOf(0))),
                AddRedaction(Redaction(RedactRule(RedactKind.EMAILS), setOf(0))),
            ),
        )
        assertEquals(2, plan.redactions.size)
        assertTrue(plan.hasChanges)
        assertEquals(listOf(words("a"), RedactRule(RedactKind.EMAILS)), plan.redactionsFor(0))
        assertTrue(plan.redactionsFor(1).isEmpty())

        val cleared = EditPlanBuilder.build(
            1,
            listOf(AddRedaction(Redaction(words("a"), setOf(0))), ClearRedactions),
        )
        assertTrue(cleared.redactions.isEmpty())
        assertFalse(cleared.hasChanges)
    }
}
