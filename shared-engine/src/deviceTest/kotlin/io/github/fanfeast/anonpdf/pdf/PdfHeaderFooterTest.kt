package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PdfHeaderFooterTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("headerfootertest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun blankPdf(pages: Int): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage(PDRectangle.A4)) }
            document.save(file)
        }
        return file
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    private fun pageText(file: File, pageNumber: Int): String =
        PdfOps.load(file).use { document ->
            PDFTextStripper().apply {
                startPage = pageNumber
                endPage = pageNumber
            }.getText(document)
        }

    @Test
    fun tokensAreFilledInAndPadded() {
        val day = LocalDate.of(2026, 11, 27)
        assertEquals("ACME-000042", PdfOps.fillTokens("ACME-{n:6}", 42, 99, day))
        assertEquals("Page 3 of 12", PdfOps.fillTokens("Page {n} of {total}", 3, 12, day))
        assertEquals("2026-11-27 · 012", PdfOps.fillTokens("{date} · {total:3}", 1, 12, day))
        assertEquals("a number wider than its padding is kept whole", "1234567", PdfOps.fillTokens("{n:3}", 1234567, 1, day))
        assertEquals("{x} {n:} {n", PdfOps.fillTokens("{x} {n:} {n", 1, 1, day))
    }

    @Test
    fun batesNumbersRunOnFromTheStartNumberAfterTheCover(): Unit = runBlocking {
        val output = out("bates")
        val options = HeaderFooterOptions(
            slots = mapOf(
                NumberPosition.BOTTOM_RIGHT to "ACME-{n:6}",
                NumberPosition.TOP_LEFT to "Confidential",
            ),
            startNumber = 1001,
            firstPageIndex = 1,
        )
        PdfEditor.applyPlan(
            blankPdf(3),
            output,
            EditPlanBuilder.build(3, listOf(SetHeaderFooter(options))),
        )
        assertTrue("the cover is left unmarked", pageText(output, 1).isBlank())
        val second = pageText(output, 2)
        assertTrue(second, second.contains("ACME-001001") && second.contains("Confidential"))
        assertTrue(pageText(output, 3).contains("ACME-001002"))
    }

    @Test
    fun numbersFollowTheFinalOrderAndTheDeletedPagesAreNotCounted(): Unit = runBlocking {
        val output = out("order")
        PdfEditor.applyPlan(
            blankPdf(3),
            output,
            EditPlanBuilder.build(
                3,
                listOf(
                    DeletePages(setOf(1)),
                    ReversePages,
                    SetHeaderFooter(HeaderFooterOptions(mapOf(NumberPosition.BOTTOM_CENTER to "{n} / {total}"))),
                ),
            ),
        )
        assertEquals("1 / 2", pageText(output, 1).trim())
        assertEquals("2 / 2", pageText(output, 2).trim())
    }

    @Test
    fun aBlankHeaderAndFooterIsNoEdit() {
        val plan = EditPlanBuilder.build(1, listOf(SetHeaderFooter(HeaderFooterOptions(mapOf(NumberPosition.TOP_LEFT to "  ")))))
        assertEquals(null, plan.headerFooter)
        assertFalse(plan.hasChanges)
    }

    @Test
    fun thePreviewShowsWhatIsSaved(): Unit = runBlocking {
        val source = blankPdf(2)
        val plan = EditPlanBuilder.build(
            2,
            listOf(
                SetHeaderFooter(
                    HeaderFooterOptions(
                        slots = mapOf(NumberPosition.TOP_CENTER to "Draft", NumberPosition.BOTTOM_RIGHT to "X{n:4}"),
                        fontSize = 18f,
                    ),
                ),
            ),
        )
        val output = out("preview")
        PdfEditor.applyPlan(source, output, plan)
        val exported = PdfRasterizer.open(output).use { it.renderByWidth(1, 300) }
        val preview = PdfEditor.renderPreview(source, plan, 1, 300, workDir)
        assertNotNull(preview)
        var differing = 0
        var inked = 0
        for (y in 0 until exported.height) {
            for (x in 0 until exported.width) {
                val a = exported.argbAt(x, y)
                if (a != TestPlatform.WHITE) inked++
                if (a != preview!!.argbAt(x, y)) differing++
            }
        }
        assertTrue("something was stamped", inked > 0)
        assertTrue("the preview matches the saved page", differing <= inked / 20)
        preview!!.release()
        exported.release()
    }
}
