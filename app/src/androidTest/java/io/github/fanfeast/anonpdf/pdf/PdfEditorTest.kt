package io.github.fanfeast.anonpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.graphics.scale
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The editor's promise is that the preview is the output. These tests hold it to
 * that by rendering both and comparing pixels.
 */
@RunWith(AndroidJUnit4::class)
class PdfEditorTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        workDir = File(context.cacheDir, "editortest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun textPdf(pages: Int, marker: String): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 30f)
                    stream.newLineAtOffset(70f, 690f)
                    stream.showText("$marker ${index + 1}")
                    stream.endText()
                    // A filled band, so a blank render is obvious.
                    stream.setNonStrokingColor(0.1f, 0.4f, 0.8f)
                    stream.addRect(70f, 200f, 300f, 60f)
                    stream.fill()
                }
            }
            document.save(file)
        }
        return file
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    private fun pageCountOf(file: File) = PdfOps.load(file).use { it.numberOfPages }

    private fun plan(pageCount: Int, vararg ops: EditOp) =
        EditPlanBuilder.build(pageCount, ops.toList())

    /** Fraction of sampled pixels that differ between two same-size bitmaps. */
    private fun differingFraction(a: Bitmap, b: Bitmap): Float {
        if (a.width != b.width || a.height != b.height) return 1f
        var differing = 0
        var total = 0
        for (y in 0 until a.height step 3) {
            for (x in 0 until a.width step 3) {
                total++
                if (a.getPixel(x, y) != b.getPixel(x, y)) differing++
            }
        }
        return if (total == 0) 0f else differing.toFloat() / total
    }

    /**
     * Mean per-channel difference, 0..255, over sampled pixels.
     *
     * Tolerant of resampling and anti-aliasing in a way that exact pixel equality
     * is not, while still being nowhere near able to hide a page whose content was
     * clipped or moved.
     */
    private fun averageChannelDifference(a: Bitmap, b: Bitmap): Float {
        if (a.width != b.width || a.height != b.height) return 255f
        var total = 0L
        var samples = 0
        for (y in 0 until a.height step 3) {
            for (x in 0 until a.width step 3) {
                val left = a.getPixel(x, y)
                val right = b.getPixel(x, y)
                total += abs(((left shr 16) and 0xFF) - ((right shr 16) and 0xFF))
                total += abs(((left shr 8) and 0xFF) - ((right shr 8) and 0xFF))
                total += abs((left and 0xFF) - (right and 0xFF))
                samples += 3
            }
        }
        return if (samples == 0) 0f else total.toFloat() / samples
    }

    /** Fraction of pixels that are not white — i.e. how much was actually drawn. */
    private fun inkFraction(bitmap: Bitmap): Float {
        var ink = 0
        var total = 0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                total++
                if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) ink++
            }
        }
        return if (total == 0) 0f else ink.toFloat() / total
    }

    // ---------------------------------------------------------------- tests

    @Test
    fun combinedEditsAllLandInASinglePass(): Unit = runBlocking {
        // Marker deliberately not "Page": extractText writes "--- Page 4 ---"
        // separators, which a substring check for a page's own text would match.
        val source = textPdf(5, "Sheet")
        val output = out("combined")

        // Crop two pages, delete one, rotate another, scale the first — the exact
        // mixture the old one-tool-at-a-time flow could not express.
        val editPlan = plan(
            5,
            CropPages(CropInsets(0.1f, 0.1f, 0.1f, 0.1f), setOf(1, 2)),
            DeletePages(setOf(3)),
            RotatePages(90, setOf(4)),
            ScalePages(0.5f, setOf(0)),
        )
        PdfEditor.applyPlan(source, output, editPlan)

        assertEquals(4, pageCountOf(output))
        PdfOps.load(output).use { document ->
            // Page 1 scaled to half size.
            assertEquals(PDRectangle.A4.width * 0.5f, document.getPage(0).mediaBox.width, 1f)
            // Pages 2 and 3 cropped by 10% each edge.
            assertEquals(PDRectangle.A4.width * 0.8f, document.getPage(1).cropBox.width, 1f)
            assertEquals(PDRectangle.A4.width * 0.8f, document.getPage(2).cropBox.width, 1f)
            // Old page 5 is now page 4, and rotated.
            assertEquals(90, document.getPage(3).rotation)
        }
        val text = PdfOps.extractText(output)
        assertTrue("deleted page is gone", !text.contains("Sheet 4"))
        assertTrue("kept pages survive", text.contains("Sheet 5"))
        assertTrue("and the rest survive", text.contains("Sheet 1"))
    }

    @Test
    fun previewMatchesTheExportedPageExactly(): Unit = runBlocking {
        val source = textPdf(4, "Doc")
        val editPlan = plan(
            4,
            DeletePages(setOf(1)),
            RotatePages(90, setOf(2)),
            CropPages(CropInsets(0.05f, 0.05f, 0.05f, 0.05f), setOf(0)),
            SetWatermark(WatermarkOptions(text = "DRAFT")),
            SetPageNumbers(PageNumberOptions(format = "{n} of {total}")),
        )
        val output = out("planned")
        PdfEditor.applyPlan(source, output, editPlan)

        val keptCount = editPlan.kept.size
        assertEquals(3, keptCount)
        assertEquals(keptCount, pageCountOf(output))

        PdfRasterizer.open(output).use { rasterizer ->
            for (position in 0 until keptCount) {
                val preview = PdfEditor.renderPreview(
                    input = source,
                    plan = editPlan,
                    outputPosition = position,
                    targetWidthPx = 420,
                    workDir = workDir,
                )
                assertNotNull("preview $position rendered", preview)
                val exported = rasterizer.renderByWidth(position, 420)

                assertEquals(
                    "preview $position is the same size as the export",
                    exported.width,
                    preview!!.width,
                )
                assertEquals(exported.height, preview.height)
                val difference = differingFraction(preview, exported)
                assertTrue(
                    "preview $position must match the export, differing=$difference",
                    difference < 0.01f,
                )
                preview.recycle()
                exported.recycle()
            }
        }
    }

    @Test
    fun cropOnARotatedPageTrimsTheEdgeTheUserDragged(): Unit = runBlocking {
        // The crop frame is dragged over the page *as displayed*. A quarter-turned
        // page shows its CropBox bottom on the left, so applying a "left" inset
        // straight to the box would trim the wrong side — invisible on an upright
        // page, wrong on every rotated scan.
        val source = textPdf(1, "Turned")
        val output = out("rotated-crop")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                RotatePages(90, setOf(0)),
                // Trim 20% off the left of what the user is looking at.
                CropPages(CropInsets(left = 0.2f), setOf(0)),
            ),
        )

        PdfOps.load(output).use { document ->
            val page = document.getPage(0)
            assertEquals(90, page.rotation)
            val box = page.cropBox
            // Displayed-left is the page bottom at 90 degrees, so the box loses
            // height and keeps its full width.
            assertEquals(
                "width must be untouched; if it shrank, the insets were not rotated",
                PDRectangle.A4.width,
                box.width,
                1f,
            )
            assertEquals(PDRectangle.A4.height * 0.8f, box.height, 1f)
            assertEquals(PDRectangle.A4.height * 0.2f, box.lowerLeftY, 1f)
        }

        // And it shows up in the render: displayed width is the box height at 90
        // degrees, so the visible page gets narrower, not shorter.
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        val aspect = rendered.width.toFloat() / rendered.height
        assertEquals(
            "rendered aspect should reflect a narrowed landscape page",
            (PDRectangle.A4.height * 0.8f) / PDRectangle.A4.width,
            aspect,
            0.02f,
        )
        rendered.recycle()
    }

    /**
     * Scaling has to move the boxes *and* the artwork by the same amount.
     *
     * The strong assertion is the render comparison: a scaled page drawn to a given
     * width must look like the unscaled page drawn to that width, because scaling
     * changes the page's size, not its composition. Checking box dimensions and
     * "is there any ink" is not enough — an earlier version of this test passed
     * while the content was being clipped to a quarter of the page.
     */
    private suspend fun assertScaleKeepsLayout(factor: Float) {
        val source = textPdf(1, "Scaled")
        val output = out("scaled-$factor")

        PdfEditor.applyPlan(source, output, plan(1, ScalePages(factor, setOf(0))))

        PdfOps.load(output).use { document ->
            val page = document.getPage(0)
            assertEquals(
                "mediaBox width",
                PDRectangle.A4.width * factor,
                page.mediaBox.width,
                1f,
            )
            assertEquals(
                "mediaBox height",
                PDRectangle.A4.height * factor,
                page.mediaBox.height,
                1f,
            )
            // The visible area has to track the page, not shrink faster than it.
            assertEquals(
                "cropBox width must match the mediaBox, not be scaled twice",
                PDRectangle.A4.width * factor,
                page.cropBox.width,
                1f,
            )
            assertEquals(
                PDRectangle.A4.height * factor,
                page.cropBox.height,
                1f,
            )
        }

        assertTrue(PdfOps.extractText(output).contains("Scaled 1"))

        val before = PdfRasterizer.open(source).use { it.renderByWidth(0, 380) }
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 380) }

        // Scaling the boxes by a float leaves the aspect ratio a hair different, so
        // the rendered height can land a pixel or two out. That is rounding, not a
        // layout change; normalise it away rather than asserting exact equality.
        assertEquals("width", before.width, rendered.width)
        assertTrue(
            "height should differ only by rounding: ${before.height} vs ${rendered.height}",
            abs(before.height - rendered.height) <= 3,
        )
        val after = if (rendered.height == before.height) {
            rendered
        } else {
            rendered.scale(before.width, before.height)
        }

        val difference = averageChannelDifference(before, after)
        assertTrue(
            "a scaled page must contain the same layout; mean channel difference " +
                "$difference at factor $factor",
            difference < 12f,
        )
        assertTrue("and must not be blank", inkFraction(after) > 0.005f)
        before.recycle()
        if (after !== rendered) rendered.recycle()
        after.recycle()
    }

    @Test
    fun scalingDownKeepsTheWholeLayout(): Unit = runBlocking {
        assertScaleKeepsLayout(0.5f)
    }

    @Test
    fun scalingDownHardKeepsTheWholeLayout(): Unit = runBlocking {
        assertScaleKeepsLayout(0.25f)
    }

    @Test
    fun scalingUpKeepsTheWholeLayout(): Unit = runBlocking {
        assertScaleKeepsLayout(1.5f)
    }

    @Test
    fun pageNumbersFollowTheFinalOrderNotTheOriginal(): Unit = runBlocking {
        val source = textPdf(3, "Orig")
        val output = out("renumbered")

        PdfEditor.applyPlan(
            source,
            output,
            plan(3, ReversePages, SetPageNumbers(PageNumberOptions(format = "[{n}]"))),
        )

        // Reversed, so original page 3 is now first and must carry the number 1.
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        PdfOps.load(output).use { document ->
            stripper.startPage = 1
            stripper.endPage = 1
            val first = stripper.getText(document)
            assertTrue("first page is the old page 3, got: $first", first.contains("Orig 3"))
            assertTrue("and is numbered 1, got: $first", first.contains("[1]"))
        }
    }

    @Test
    fun pageNumbersSkipACoverWithoutWastingANumber(): Unit = runBlocking {
        val source = textPdf(3, "Cover")
        val output = out("cover")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                3,
                SetPageNumbers(PageNumberOptions(format = "[{n}]", firstPageIndex = 1)),
            ),
        )

        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        PdfOps.load(output).use { document ->
            stripper.startPage = 1
            stripper.endPage = 1
            assertTrue("cover is unnumbered", !stripper.getText(document).contains("["))
            stripper.startPage = 2
            stripper.endPage = 2
            assertTrue("numbering starts at 1 on page 2", stripper.getText(document).contains("[1]"))
        }
    }

    @Test
    fun deletingEveryPageIsRefused(): Unit = runBlocking {
        val source = textPdf(2, "Gone")
        val failure = runCatching {
            PdfEditor.applyPlan(source, out("empty"), plan(2, DeletePages(setOf(0, 1))))
        }.exceptionOrNull()
        assertTrue("must refuse an empty document", failure is IllegalArgumentException)
    }

    @Test
    fun anUnchangedPlanRoundTripsTheDocument(): Unit = runBlocking {
        val source = textPdf(3, "Same")
        val output = out("unchanged")
        PdfEditor.applyPlan(source, output, EditPlanBuilder.identity(3))

        assertEquals(3, pageCountOf(output))
        val before = PdfRasterizer.open(source).use { it.renderByWidth(1, 400) }
        val after = PdfRasterizer.open(output).use { it.renderByWidth(1, 400) }
        assertTrue(
            "a no-op plan must not alter the pages",
            differingFraction(before, after) < 0.005f,
        )
        before.recycle()
        after.recycle()
    }

    @Test
    fun previewOfAnOutOfRangePageReturnsNothingRatherThanCrashing(): Unit = runBlocking {
        val source = textPdf(2, "Range")
        val editPlan = plan(2, DeletePages(setOf(1)))
        assertEquals(
            null,
            PdfEditor.renderPreview(source, editPlan, outputPosition = 5, 300, workDir),
        )
    }

    @Test
    fun editsSurviveOnAnEncryptedDocument(): Unit = runBlocking {
        val source = textPdf(3, "Locked")
        val locked = out("locked")
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(userPassword = "pw", ownerPassword = "pw", allowCopying = true),
        )
        val output = out("locked-edited")

        PdfEditor.applyPlan(
            input = locked,
            output = output,
            plan = plan(3, DeletePages(setOf(0)), RotatePages(180, setOf(2))),
            password = "pw",
        )

        assertEquals(2, pageCountOf(output))
        assertTrue("result comes out decrypted", !PdfOps.isPasswordProtected(output))

        // Preview must handle the password too.
        val preview = PdfEditor.renderPreview(
            input = locked,
            plan = plan(3, DeletePages(setOf(0))),
            outputPosition = 0,
            targetWidthPx = 300,
            workDir = workDir,
            password = "pw",
        )
        assertNotNull("preview works on an encrypted source", preview)
        preview?.recycle()
    }
}
