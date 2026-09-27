package io.github.fanfeast.anonpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.graphics.scale
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
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

    /**
     * A page filled edge to edge, so the tests can see exactly where the content
     * lands after a refit — a page with only text in the middle would look the same
     * whether it was letterboxed or cropped.
     */
    private fun filledPdf(width: Float, height: Float): File {
        val file = File(workDir, "filled-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(width, height))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.setNonStrokingColor(0.15f, 0.35f, 0.75f)
                stream.addRect(0f, 0f, width, height)
                stream.fill()
            }
            document.save(file)
        }
        return file
    }

    /**
     * Visibly not paper. A white-out flattens its page to a JPEG, whose white can
     * come back a shade or two off, so near-white still counts as blank.
     */
    private fun isInk(bitmap: Bitmap, x: Int, y: Int): Boolean {
        val pixel = bitmap.getPixel(x, y)
        return minOf(
            android.graphics.Color.red(pixel),
            android.graphics.Color.green(pixel),
            android.graphics.Color.blue(pixel),
        ) < 235
    }

    @Test
    fun resizingProducesASheetOfExactlyTheRequestedSize(): Unit = runBlocking {
        val source = textPdf(2, "Fit")
        val output = out("letter")

        PdfEditor.applyPlan(
            source,
            output,
            plan(2, ResizePages(ResizeTarget(PaperSize.LETTER), setOf(0, 1))),
        )

        PdfOps.load(output).use { document ->
            for (index in 0 until 2) {
                val page = document.getPage(index)
                assertEquals("page $index width", 612f, page.mediaBox.width, 0.5f)
                assertEquals("page $index height", 792f, page.mediaBox.height, 0.5f)
                assertEquals("cropBox must match", 612f, page.cropBox.width, 0.5f)
                assertEquals(792f, page.cropBox.height, 0.5f)
            }
        }
        // Content came along rather than being left behind on the old sheet.
        assertTrue(PdfOps.extractText(output).contains("Fit 1"))
    }

    @Test
    fun fittingLetterboxesAndFillingCoversTheSheet(): Unit = runBlocking {
        // A4 and Letter have different proportions, so one of the two has to give.
        val source = filledPdf(PDRectangle.A4.width, PDRectangle.A4.height)

        val fitted = out("fitted")
        PdfEditor.applyPlan(
            source,
            fitted,
            plan(1, ResizePages(ResizeTarget(PaperSize.LETTER, fill = false), setOf(0))),
        )
        val filled = out("filled-sheet")
        PdfEditor.applyPlan(
            source,
            filled,
            plan(1, ResizePages(ResizeTarget(PaperSize.LETTER, fill = true), setOf(0))),
        )

        val fitRender = PdfRasterizer.open(fitted).use { it.renderByWidth(0, 400) }
        val fillRender = PdfRasterizer.open(filled).use { it.renderByWidth(0, 400) }

        val middleRow = fitRender.height / 2
        // Fitting leaves the sheet's own colour showing down the sides.
        assertTrue("fit should leave a left margin", !isInk(fitRender, 1, middleRow))
        assertTrue(
            "fit should leave a right margin",
            !isInk(fitRender, fitRender.width - 2, middleRow),
        )
        assertTrue("fit content still present", isInk(fitRender, fitRender.width / 2, middleRow))

        // Filling covers the sheet corner to corner, cropping the overflow instead.
        assertTrue("fill should reach the left edge", isInk(fillRender, 1, middleRow))
        assertTrue(
            "fill should reach the right edge",
            isInk(fillRender, fillRender.width - 2, middleRow),
        )
        assertTrue(
            "fill should reach the top",
            isInk(fillRender, fillRender.width / 2, 1),
        )

        fitRender.recycle()
        fillRender.recycle()
    }

    @Test
    fun resizingAQuarterTurnedPageBakesTheRotationAway(): Unit = runBlocking {
        // An upright A4 turned 90 degrees reads as landscape, so "Letter" with auto
        // orientation must mean landscape Letter — and the page must not be left
        // with a /Rotate that a viewer would then apply on top.
        val source = textPdf(1, "Turned")
        val output = out("turned-letter")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                RotatePages(90, setOf(0)),
                ResizePages(ResizeTarget(PaperSize.LETTER), setOf(0)),
            ),
        )

        PdfOps.load(output).use { document ->
            val page = document.getPage(0)
            assertEquals("rotation must be baked in, not left on the page", 0, page.rotation)
            assertEquals("landscape Letter width", 792f, page.mediaBox.width, 0.5f)
            assertEquals("landscape Letter height", 612f, page.mediaBox.height, 0.5f)
        }

        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        assertTrue("renders landscape", rendered.width > rendered.height)
        assertTrue("and is not blank", inkFraction(rendered) > 0.005f)
        rendered.recycle()
    }

    @Test
    fun forcedOrientationOverridesThePage(): Unit = runBlocking {
        val source = filledPdf(842f, 595f) // landscape source
        val output = out("forced-portrait")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                ResizePages(
                    ResizeTarget(PaperSize.A4, PageOrientation.PORTRAIT),
                    setOf(0),
                ),
            ),
        )

        PdfOps.load(output).use { document ->
            val box = document.getPage(0).mediaBox
            assertEquals(PDRectangle.A4.width, box.width, 0.5f)
            assertEquals(PDRectangle.A4.height, box.height, 0.5f)
        }
    }

    @Test
    fun resizingKeepsCroppedAwayContentHidden(): Unit = runBlocking {
        // Refitting enlarges the visible box, so anything the crop was hiding must
        // stay hidden rather than reappearing in the new margins.
        val source = filledPdf(PDRectangle.A4.width, PDRectangle.A4.height)
        val output = out("cropped-then-resized")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                CropPages(CropInsets(0.25f, 0.25f, 0.25f, 0.25f), setOf(0)),
                ResizePages(ResizeTarget(PaperSize.A4), setOf(0)),
            ),
        )

        PdfOps.load(output).use { document ->
            assertEquals(PDRectangle.A4.width, document.getPage(0).mediaBox.width, 0.5f)
        }
        // The cropped page is 1:1.41 like A4, so it fits with no margin at all and
        // the ink covers the sheet — what matters is that it is the *cropped*
        // content, scaled up, not the original with its hidden parts restored.
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 320) }
        assertTrue("content fills the refitted sheet", inkFraction(rendered) > 0.9f)
        rendered.recycle()
    }

    @Test
    fun previewMatchesTheExportForARefittedPage(): Unit = runBlocking {
        val source = textPdf(2, "Sheet")
        val editPlan = plan(
            2,
            ResizePages(ResizeTarget(PaperSize.LEGAL), setOf(0)),
            RotatePages(90, setOf(1)),
        )
        val output = out("resized-preview")
        PdfEditor.applyPlan(source, output, editPlan)

        PdfRasterizer.open(output).use { rasterizer ->
            for (position in 0 until editPlan.kept.size) {
                val preview = PdfEditor.renderPreview(
                    input = source,
                    plan = editPlan,
                    outputPosition = position,
                    targetWidthPx = 360,
                    workDir = workDir,
                )
                assertNotNull("preview $position", preview)
                val exported = rasterizer.renderByWidth(position, 360)
                assertEquals(exported.height, preview!!.height)
                assertTrue(
                    "refitted preview must match its export",
                    differingFraction(preview, exported) < 0.01f,
                )
                preview.recycle()
                exported.recycle()
            }
        }
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

    // ------------------------------------------------- added page content

    @Test
    fun aWhiteOutBlockActuallyCoversWhatIsUnderIt(): Unit = runBlocking {
        // This is the mechanism behind "changing" existing text: the original glyphs
        // cannot be rewritten without the document's fonts, so they get covered.
        val source = filledPdf(PDRectangle.A4.width, PDRectangle.A4.height)
        val output = out("whiteout")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                AddMark(
                    FillMark(
                        id = 1L,
                        sourceIndex = 0,
                        rect = MarkRect(0.25f, 0.25f, 0.75f, 0.75f),
                        color = 0xFFFFFFFF.toInt(),
                        opacity = 1f,
                    ),
                ),
            ),
        )

        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        val midX = rendered.width / 2
        val midY = rendered.height / 2
        assertTrue("middle should be covered", !isInk(rendered, midX, midY))
        // Just outside the block the original fill must survive.
        assertTrue(
            "outside the block should be untouched",
            isInk(rendered, (rendered.width * 0.1f).toInt(), midY),
        )
        rendered.recycle()
    }

    /** Covers the "<marker> 1" line [textPdf] draws near the top of the page. */
    private fun coverTopLine(id: Long, sourceIndex: Int) = FillMark(
        id = id,
        sourceIndex = sourceIndex,
        rect = MarkRect(0.02f, 0.1f, 0.98f, 0.25f),
        color = 0xFF000000.toInt(),
        opacity = 1f,
    )

    /** Two pages, each with a filled-in text field whose value is [valuePrefix]N. */
    private fun formPdf(valuePrefix: String): File {
        val file = File(workDir, "form-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply {
                put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            }
            form.defaultAppearance = "/Helv 12 Tf 0 g"
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val field = PDTextField(form).apply { partialName = "field${index + 1}" }
                val widget = field.widgets.first().apply {
                    rectangle = PDRectangle(60f, 700f, 300f, 24f)
                    this.page = page
                }
                page.annotations.add(widget)
                form.fields.add(field)
                field.value = "$valuePrefix${index + 1}"
            }
            document.save(file)
        }
        return file
    }

    @Test
    fun aWhiteOutAlsoRemovesTheFormFieldsOnThatPage(): Unit = runBlocking {
        // A field's typed value lives in the form, not on the page. Flattening the
        // page drops its widget, but the value would survive in the form unless
        // the field goes too.
        val source = formPdf("SecretValue")
        val output = out("form-redacted")

        PdfEditor.applyPlan(source, output, plan(2, AddMark(coverTopLine(id = 30L, sourceIndex = 0))))

        val raw = String(output.readBytes(), Charsets.ISO_8859_1)
        assertTrue("the covered field's value must not be in the file", !raw.contains("SecretValue1"))
        PdfOps.load(output).use { document ->
            val names = document.documentCatalog.acroForm?.fieldTree?.map { it.fullyQualifiedName }.orEmpty()
            assertTrue("the covered field is gone, got $names", "field1" !in names)
            assertTrue("a field on an untouched page stays, got $names", "field2" in names)
            val kept = document.documentCatalog.acroForm.getField("field2")
            assertEquals("SecretValue2", kept.valueAsString)
        }
    }

    @Test
    fun aWhiteOutRemovesTheTextItCoversFromTheFile(): Unit = runBlocking {
        // Covering is not enough: a box drawn over text leaves the text in the
        // content stream, where copy, search and extractors still find it.
        val source = textPdf(2, "Secret")
        val output = out("redacted")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                2,
                AddMark(coverTopLine(id = 20L, sourceIndex = 0)),
                // Added after the white-out, so it stays real, selectable text.
                AddMark(
                    TextMark(id = 21L, sourceIndex = 0, text = "Replacement", left = 0.1f, top = 0.4f),
                ),
            ),
        )

        val first = pageText(output, 1)
        assertTrue("covered text must be gone, got: $first", !first.contains("Secret"))
        assertTrue("text added after the white-out survives, got: $first", first.contains("Replacement"))
        assertTrue("a page without a white-out keeps its text", pageText(output, 2).contains("Secret2"))

        // The page still looks like the page: the band below the box is still drawn.
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        assertTrue(
            "content outside the box survives flattening",
            isInk(rendered, rendered.width / 3, (rendered.height * (1f - 230f / 842f)).toInt()),
        )
        rendered.recycle()
    }

    @Test
    fun aFlattenedPagePreviewsExactlyAsExported(): Unit = runBlocking {
        val source = textPdf(1, "Hidden")
        val editPlan = plan(
            1,
            RotatePages(90, setOf(0)),
            AddMark(coverTopLine(id = 22L, sourceIndex = 0)),
            AddMark(TextMark(id = 23L, sourceIndex = 0, text = "on top", left = 0.2f, top = 0.5f)),
        )
        val output = out("redacted-preview")
        PdfEditor.applyPlan(source, output, editPlan)

        val preview = PdfEditor.renderPreview(
            input = source,
            plan = editPlan,
            outputPosition = 0,
            targetWidthPx = 380,
            workDir = workDir,
        )
        assertNotNull("preview of a flattened page", preview)
        val exported = PdfRasterizer.open(output).use { it.renderByWidth(0, 380) }
        assertTrue("a rotated page stays landscape once flattened", exported.width > exported.height)
        assertTrue(
            "flattened page must preview exactly as exported",
            differingFraction(preview!!, exported) < 0.01f,
        )
        preview.recycle()
        exported.recycle()
        // Nothing left behind in the scratch folder.
        assertTrue(workDir.listFiles().orEmpty().none { it.name.startsWith("flatten-") })
    }

    @Test
    fun aWhiteOutOnAnEncryptedDocumentStillRemovesTheText(): Unit = runBlocking {
        val source = textPdf(1, "Locked")
        val locked = out("locked-redact")
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(userPassword = "pw", ownerPassword = "pw", allowCopying = true),
        )
        val output = out("locked-redacted")

        PdfEditor.applyPlan(
            input = locked,
            output = output,
            plan = plan(1, AddMark(coverTopLine(id = 24L, sourceIndex = 0))),
            password = "pw",
        )

        assertTrue("covered text must be gone", !pageText(output, 1).contains("Locked"))
    }

    @Test
    fun aHighlightLetsTheContentShowThrough(): Unit = runBlocking {
        val source = textPdf(1, "Marked")
        val output = out("highlight")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                AddMark(
                    FillMark(
                        id = 2L,
                        sourceIndex = 0,
                        rect = MarkRect(0.05f, 0.1f, 0.95f, 0.25f),
                        color = 0xFFFFEB3B.toInt(),
                        opacity = 0.4f,
                    ),
                ),
            ),
        )

        // The text under the highlight is still extractable, and still drawn.
        assertTrue(PdfOps.extractText(output).contains("Marked 1"))
        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        assertTrue("highlight band has ink", isInk(rendered, rendered.width / 2, (rendered.height * 0.17f).toInt()))
        rendered.recycle()
    }

    @Test
    fun addedTextEndsUpInTheDocumentAsRealText(): Unit = runBlocking {
        val source = textPdf(2, "Base")
        val output = out("added-text")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                2,
                AddMark(
                    TextMark(
                        id = 3L,
                        sourceIndex = 1,
                        text = "Added line one\nAdded line two",
                        left = 0.1f,
                        top = 0.2f,
                        fontSize = 14f,
                    ),
                ),
            ),
        )

        val text = PdfOps.extractText(output)
        // Selectable and searchable, not a picture of text.
        assertTrue("first line", text.contains("Added line one"))
        assertTrue("second line", text.contains("Added line two"))
        // And only on the page it was put on.
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        PdfOps.load(output).use { document ->
            stripper.startPage = 1
            stripper.endPage = 1
            assertTrue(
                "page 1 must be untouched",
                !stripper.getText(document).contains("Added line"),
            )
        }
    }

    @Test
    fun addedTextSurvivesCharactersTheBuiltInFontsLack(): Unit = runBlocking {
        val source = textPdf(1, "Uni")
        val output = out("added-emoji")
        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                AddMark(
                    TextMark(id = 4L, sourceIndex = 0, text = "note 😀 here", left = 0.1f, top = 0.3f),
                ),
            ),
        )
        assertEquals(1, pageCountOf(output))
        assertTrue(PdfOps.extractText(output).contains("note"))
    }

    @Test
    fun inkIsDrawnOnThePage(): Unit = runBlocking {
        val source = textPdf(1, "Ink")
        val output = out("ink")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                AddMark(
                    InkMark(
                        id = 5L,
                        sourceIndex = 0,
                        strokes = listOf(
                            // A thick horizontal line across an empty band of the page.
                            (0..20).map { 0.1f + it * 0.04f to 0.55f },
                        ),
                        color = 0xFFD32F2F.toInt(),
                        widthRatio = 0.02f,
                    ),
                ),
            ),
        )

        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        assertTrue(
            "the stroke should be visible where it was drawn",
            isInk(rendered, rendered.width / 2, (rendered.height * 0.55f).toInt()),
        )
        rendered.recycle()
    }

    @Test
    fun marksTravelWithTheirPageThroughAReorder(): Unit = runBlocking {
        val source = textPdf(3, "Move")
        val output = out("marks-reordered")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                3,
                AddMark(
                    TextMark(id = 6L, sourceIndex = 2, text = "STAMPED", left = 0.2f, top = 0.4f),
                ),
                ReversePages,
            ),
        )

        // Source page 3 is now first, and must have brought its text with it.
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        PdfOps.load(output).use { document ->
            stripper.startPage = 1
            stripper.endPage = 1
            val first = stripper.getText(document)
            assertTrue("page moved, got: $first", first.contains("Move 3"))
            assertTrue("and its mark came along, got: $first", first.contains("STAMPED"))
        }
    }

    @Test
    fun marksLandCorrectlyOnARotatedPage(): Unit = runBlocking {
        // Marks are placed against the page as displayed, so a quarter turn must not
        // send them off the edge or onto the wrong side.
        val source = filledPdf(PDRectangle.A4.width, PDRectangle.A4.height)
        val output = out("marks-rotated")

        PdfEditor.applyPlan(
            source,
            output,
            plan(
                1,
                RotatePages(90, setOf(0)),
                AddMark(
                    FillMark(
                        id = 7L,
                        sourceIndex = 0,
                        // Top-left quarter of the page as the user sees it.
                        rect = MarkRect(0.02f, 0.02f, 0.35f, 0.35f),
                        color = 0xFFFFFFFF.toInt(),
                    ),
                ),
            ),
        )

        val rendered = PdfRasterizer.open(output).use { it.renderByWidth(0, 400) }
        assertTrue("renders landscape", rendered.width > rendered.height)
        // The covered patch must be in the top-left of the rendered page.
        assertTrue(
            "top-left should be covered",
            !isInk(rendered, (rendered.width * 0.15f).toInt(), (rendered.height * 0.15f).toInt()),
        )
        assertTrue(
            "bottom-right should be untouched",
            isInk(rendered, (rendered.width * 0.85f).toInt(), (rendered.height * 0.85f).toInt()),
        )
        rendered.recycle()
    }

    @Test
    fun previewMatchesTheExportWithAddedContent(): Unit = runBlocking {
        val source = textPdf(2, "Both")
        val editPlan = plan(
            2,
            AddMark(TextMark(id = 8L, sourceIndex = 0, text = "hello", left = 0.15f, top = 0.3f)),
            AddMark(
                FillMark(
                    id = 9L,
                    sourceIndex = 0,
                    rect = MarkRect(0.1f, 0.5f, 0.6f, 0.6f),
                    color = 0xFF90CAF9.toInt(),
                    opacity = 0.5f,
                ),
            ),
            AddMark(
                InkMark(
                    id = 10L,
                    sourceIndex = 1,
                    strokes = listOf(listOf(0.2f to 0.2f, 0.5f to 0.4f, 0.8f to 0.25f)),
                ),
            ),
        )
        val output = out("marks-preview")
        PdfEditor.applyPlan(source, output, editPlan)

        PdfRasterizer.open(output).use { rasterizer ->
            for (position in 0 until 2) {
                val preview = PdfEditor.renderPreview(
                    input = source,
                    plan = editPlan,
                    outputPosition = position,
                    targetWidthPx = 380,
                    workDir = workDir,
                )
                assertNotNull("preview $position", preview)
                val exported = rasterizer.renderByWidth(position, 380)
                assertTrue(
                    "added content must preview exactly as exported, page $position",
                    differingFraction(preview!!, exported) < 0.01f,
                )
                preview.recycle()
                exported.recycle()
            }
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

    // ------------------------------------------------------------ merging

    /**
     * One page's text with all whitespace squashed out — PDFTextStripper breaks
     * rotated text into fragments, and only the characters matter here.
     */
    private fun pageText(file: File, pageNumber: Int): String =
        PdfOps.load(file).use { document ->
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            stripper.getText(document)
        }.replace(Regex("\\s+"), "")

    @Test
    fun mergedDocumentLandsAtTheChosenPosition(): Unit = runBlocking {
        val first = textPdf(4, "Alpha")
        val second = textPdf(3, "Beta")
        // Insert the 3-page document after page 2: Alpha 1-2, Beta 1-3, Alpha 3-4.
        val editPlan = plan(4, InsertPages(startIndex = 4, count = 3, position = 2, label = "b"))

        val output = out("merged")
        PdfEditor.applyPlan(
            sources = listOf(
                PdfEditor.PlanSource(first, pageCount = 4),
                PdfEditor.PlanSource(second, pageCount = 3),
            ),
            output = output,
            plan = editPlan,
        )

        assertEquals(7, pageCountOf(output))
        assertTrue(pageText(output, 1).contains("Alpha1"))
        assertTrue(pageText(output, 2).contains("Alpha2"))
        assertTrue(pageText(output, 3).contains("Beta1"))
        assertTrue(pageText(output, 5).contains("Beta3"))
        assertTrue(pageText(output, 6).contains("Alpha3"))
        assertTrue(pageText(output, 7).contains("Alpha4"))
    }

    @Test
    fun mergedPagesTakeEveryKindOfEdit(): Unit = runBlocking {
        val first = textPdf(2, "Alpha")
        val second = textPdf(2, "Beta")
        // Insert at the end, rotate the first Beta page, delete the second,
        // move the rotated one to the front, and mark it.
        val editPlan = plan(
            2,
            InsertPages(startIndex = 2, count = 2, position = 2, label = "b"),
            RotatePages(90, setOf(2)),
            DeletePages(setOf(3)),
            MovePage(sourceIndex = 2, offset = -2),
            AddMark(
                TextMark(id = 1L, sourceIndex = 2, text = "Stamped", left = 0.1f, top = 0.1f),
            ),
        )

        val output = out("merged-edited")
        PdfEditor.applyPlan(
            sources = listOf(
                PdfEditor.PlanSource(first, pageCount = 2),
                PdfEditor.PlanSource(second, pageCount = 2),
            ),
            output = output,
            plan = editPlan,
        )

        assertEquals(3, pageCountOf(output))
        val firstPage = pageText(output, 1)
        assertTrue("moved Beta page leads", firstPage.contains("Beta1"))
        assertTrue("mark landed on the merged page", firstPage.contains("Stamped"))
        val whole = PdfOps.extractText(output).replace(Regex("\\s+"), "")
        assertTrue("deleted Beta page is gone", !whole.contains("Beta2"))
        PdfOps.load(output).use { document ->
            assertEquals(90, document.getPage(0).rotation)
        }
    }

    @Test
    fun previewMatchesTheExportWithMergedPages(): Unit = runBlocking {
        val first = textPdf(3, "Alpha")
        val second = textPdf(2, "Beta")
        val sources = listOf(
            PdfEditor.PlanSource(first, pageCount = 3),
            PdfEditor.PlanSource(second, pageCount = 2),
        )
        val editPlan = plan(
            3,
            InsertPages(startIndex = 3, count = 2, position = 1, label = "b"),
            RotatePages(90, setOf(3)),
            CropPages(CropInsets(0.05f, 0.05f, 0.05f, 0.05f), setOf(4)),
            SetWatermark(WatermarkOptions(text = "DRAFT")),
            SetPageNumbers(PageNumberOptions(format = "{n} of {total}")),
        )

        val output = out("merged-preview")
        PdfEditor.applyPlan(sources = sources, output = output, plan = editPlan)
        assertEquals(5, pageCountOf(output))

        PdfRasterizer.open(output).use { rasterizer ->
            for (position in 0 until editPlan.kept.size) {
                val preview = PdfEditor.renderPreview(
                    sources = sources,
                    plan = editPlan,
                    outputPosition = position,
                    targetWidthPx = 420,
                    workDir = workDir,
                )
                assertNotNull("preview $position rendered", preview)
                val exported = rasterizer.renderByWidth(position, 420)
                assertEquals(exported.width, preview!!.width)
                assertEquals(exported.height, preview.height)
                val difference = differingFraction(preview, exported)
                assertTrue(
                    "merged preview $position must match the export, differing=$difference",
                    difference < 0.01f,
                )
                preview.recycle()
                exported.recycle()
            }
        }
    }
}
