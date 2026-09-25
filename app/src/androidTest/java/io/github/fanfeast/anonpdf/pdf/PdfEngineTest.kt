package io.github.fanfeast.anonpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Exercises every PDF operation against real documents on a real device.
 *
 * These are instrumented rather than JVM tests on purpose: the engine leans on
 * Android's own PdfRenderer, Bitmap and ImageDecoder, so testing it off-device
 * would test something other than what ships.
 */
@RunWith(AndroidJUnit4::class)
class PdfEngineTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        workDir = File(context.cacheDir, "enginetest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    // ------------------------------------------------------------- fixtures

    private fun textPdf(pages: Int, marker: String): File {
        val file = File(workDir, "text-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 22f)
                    stream.newLineAtOffset(64f, 700f)
                    stream.showText("$marker page ${index + 1}")
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    /**
     * A PDF whose weight is an uncompressed image, so compression has something
     * to do. Photos and scans — the things people actually compress — carry no
     * alpha channel, so [opaque] defaults to matching that.
     */
    private fun imageHeavyPdf(opaque: Boolean = true): File {
        val file = File(workDir, "image-${System.nanoTime()}.pdf")
        val bitmap = noiseBitmap(1400, 1000, opaque)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            // Lossless means Flate-encoded raw pixels: big, and very compressible.
            val image = LosslessFactory.createFromImage(document, bitmap)
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(image, 20f, 20f, 555f, 400f)
            }
            document.save(file)
        }
        bitmap.recycle()
        return file
    }

    private fun noiseBitmap(width: Int, height: Int, opaque: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = Random(7)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                row[x] = Color.rgb(
                    (x + y) % 256,
                    random.nextInt(256),
                    (x * 3 + y) % 256,
                )
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        // An ARGB_8888 bitmap reports hasAlpha() even when every pixel is opaque,
        // and PDFBox then emits a soft mask. Say so explicitly.
        if (opaque) bitmap.setHasAlpha(false)
        return bitmap
    }

    /**
     * Collapses whitespace before matching.
     *
     * PDFTextStripper reconstructs lines from glyph positions, so a watermark set
     * at 45 degrees comes back broken across lines ("CONFID/ENTIA/L"). The glyphs
     * are on the page; only the line breaks are an artefact of extraction.
     */
    private fun squashed(text: String) = text.replace(Regex("\\s+"), "")

    private suspend fun renderFirstPage(file: File, width: Int = 400): Bitmap =
        PdfRasterizer.open(file).use { it.renderByWidth(0, width) }

    /** All the page's content streams concatenated, for operator-level assertions. */
    private fun contentStreamOf(file: File, pageIndex: Int = 0): String =
        PdfOps.load(file).use { document ->
            buildString {
                val streams = document.getPage(pageIndex).contentStreams
                while (streams.hasNext()) append(String(streams.next().toByteArray()))
            }
        }

    /**
     * Fraction of pixels that differ inside the middle half of the page, which is
     * where a centred or diagonal watermark has to show up.
     */
    private fun centreDifferenceFraction(before: Bitmap, after: Bitmap): Float {
        if (before.width != after.width || before.height != after.height) return 1f
        val fromX = before.width / 4
        val toX = before.width * 3 / 4
        val fromY = before.height / 4
        val toY = before.height * 3 / 4
        var differing = 0
        var total = 0
        for (y in fromY until toY step 2) {
            for (x in fromX until toX step 2) {
                total++
                if (before.getPixel(x, y) != after.getPixel(x, y)) differing++
            }
        }
        return if (total == 0) 0f else differing.toFloat() / total
    }

    private fun pngFile(width: Int, height: Int): File {
        val file = File(workDir, "img-${System.nanoTime()}.png")
        val bitmap = noiseBitmap(width, height)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun pageCountOf(file: File, password: String? = null): Int =
        PdfOps.load(file, password).use { it.numberOfPages }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    // ---------------------------------------------------------------- tests

    @Test
    fun readsMetadataAndPageCount() = runBlocking {
        val source = textPdf(4, "Meta")
        val meta = PdfOps.readMeta(source)
        assertEquals(4, meta.pageCount)
        assertTrue(!meta.encrypted)
    }

    @Test
    fun mergesDocumentsInOrder() = runBlocking {
        val first = textPdf(3, "Alpha")
        val second = textPdf(2, "Bravo")
        val output = out("merged")

        PdfOps.merge(listOf(first, second), output)

        assertEquals(5, pageCountOf(output))
        val text = PdfOps.extractText(output)
        assertTrue("first document survived", text.contains("Alpha page 1"))
        assertTrue("second document survived", text.contains("Bravo page 2"))
        assertTrue(
            "order preserved",
            text.indexOf("Alpha page 1") < text.indexOf("Bravo page 1"),
        )
    }

    @Test
    fun organizeReordersDeletesAndRotates() = runBlocking {
        val source = textPdf(5, "Org")
        val output = out("organised")

        // Keep pages 5, 3, 1 in that order; turn the first of them 90 degrees.
        PdfOps.organize(
            input = source,
            output = output,
            plan = listOf(
                PagePlan(sourceIndex = 4, rotationDelta = 90),
                PagePlan(sourceIndex = 2),
                PagePlan(sourceIndex = 0),
            ),
        )

        assertEquals(3, pageCountOf(output))
        PdfOps.load(output).use { document ->
            assertEquals(90, document.getPage(0).rotation)
            assertEquals(0, document.getPage(1).rotation)
            // Page geometry must survive being detached from the page tree.
            assertEquals(PDRectangle.A4.width, document.getPage(0).mediaBox.width, 0.5f)
        }
        val text = PdfOps.extractText(output)
        assertTrue(text.indexOf("Org page 5") < text.indexOf("Org page 3"))
        assertTrue(text.indexOf("Org page 3") < text.indexOf("Org page 1"))
    }

    @Test
    fun extractsChosenPages() = runBlocking {
        val source = textPdf(6, "Pick")
        val output = out("extracted")

        PdfOps.extractPages(source, output, listOf(1, 3))

        assertEquals(2, pageCountOf(output))
        val text = PdfOps.extractText(output)
        assertTrue(text.contains("Pick page 2"))
        assertTrue(text.contains("Pick page 4"))
        assertTrue("page 1 excluded", !text.contains("Pick page 1"))
    }

    @Test
    fun splitsIntoSeparateFiles() = runBlocking {
        val source = textPdf(6, "Split")
        val directory = File(workDir, "split").apply { mkdirs() }

        val files = PdfOps.splitToFiles(
            input = source,
            outputDir = directory,
            ranges = listOf(0..1, 2..2, 3..5),
            baseName = "chunk",
        )

        assertEquals(3, files.size)
        assertEquals(2, pageCountOf(files[0]))
        assertEquals(1, pageCountOf(files[1]))
        assertEquals(3, pageCountOf(files[2]))
        assertTrue(PdfOps.extractText(files[1]).contains("Split page 3"))
    }

    @Test
    fun rotatesEveryPageCumulatively() = runBlocking {
        val source = textPdf(2, "Rot")
        val once = out("rot1")
        val twice = out("rot2")

        PdfOps.rotateAll(source, once, 90)
        PdfOps.rotateAll(once, twice, 90)

        PdfOps.load(twice).use { document ->
            assertEquals(180, document.getPage(0).rotation)
            assertEquals(180, document.getPage(1).rotation)
        }
    }

    @Test
    fun cropShrinksTheCropBoxOnly() = runBlocking {
        val source = textPdf(2, "Crop")
        val output = out("cropped")
        val originalWidth = PdfOps.load(source).use { it.getPage(0).cropBox.width }

        PdfOps.crop(source, output, CropInsets(0.1f, 0.1f, 0.1f, 0.1f))

        PdfOps.load(output).use { document ->
            val page = document.getPage(0)
            assertEquals(originalWidth * 0.8f, page.cropBox.width, 1f)
            // MediaBox is untouched, which is what makes a crop reversible.
            assertEquals(originalWidth, page.mediaBox.width, 1f)
        }
    }

    @Test
    fun watermarkKeepsPagesAndAddsText() = runBlocking {
        val source = textPdf(3, "Mark")
        val output = out("watermarked")

        PdfOps.watermark(
            source,
            output,
            WatermarkOptions(text = "CONFIDENTIAL", layout = WatermarkLayout.DIAGONAL),
        )

        assertEquals(3, pageCountOf(output))
        val text = PdfOps.extractText(output)
        assertTrue("watermark present", squashed(text).contains("CONFIDENTIAL"))
        assertTrue("original content intact", text.contains("Mark page 2"))
        // Every page, not just the first.
        assertEquals(3, Regex("CONFIDENTIAL").findAll(squashed(text)).count())
    }

    @Test
    fun watermarkSurvivesUnsupportedCharacters() = runBlocking {
        val source = textPdf(1, "Uni")
        val output = out("watermark-unicode")

        // An emoji cannot be encoded by the built-in fonts; it must degrade, not throw.
        PdfOps.watermark(source, output, WatermarkOptions(text = "draft 😀 only"))

        assertEquals(1, pageCountOf(output))
        val text = squashed(PdfOps.extractText(output))
        // The emoji is a surrogate pair, so it degrades to two question marks.
        assertTrue("readable text survives, got $text", text.contains("draft??only"))
    }

    @Test
    fun watermarkLandsOnTheVisiblePageArea() = runBlocking {
        val source = textPdf(1, "PixelMark")
        val output = out("watermark-pixels")

        PdfOps.watermark(source, output, WatermarkOptions(text = "CONFIDENTIAL"))

        // Rendering is the honest check: it proves ink reached the page rather
        // than landing off-canvas or under a clip.
        val before = renderFirstPage(source)
        val after = renderFirstPage(output)
        val changed = centreDifferenceFraction(before, after)
        assertTrue(
            "watermark should mark the middle of the page, changed=$changed",
            changed > 0.01f,
        )
        before.recycle()
        after.recycle()
    }

    @Test
    fun watermarkHandlesRotatedPages() = runBlocking {
        val source = textPdf(1, "RotMark")
        val rotated = out("prerotated")
        PdfOps.rotateAll(source, rotated, 90)
        val output = out("rotated-watermark")

        PdfOps.watermark(rotated, output, WatermarkOptions(text = "SIDEWAYS"))

        assertEquals(1, pageCountOf(output))
        // The page keeps its rotation; the watermark rides along with it.
        PdfOps.load(output).use { assertEquals(90, it.getPage(0).rotation) }
        // Content-stream inspection, because extracting text from a page that is
        // both /Rotate-d and drawn at an angle reorders glyphs unpredictably.
        assertTrue(
            "the show-text operator must be present",
            contentStreamOf(output).contains("(SIDEWAYS) Tj"),
        )

        val before = renderFirstPage(rotated)
        val after = renderFirstPage(output)
        assertTrue("rotated page renders landscape", after.width > after.height)
        val changed = centreDifferenceFraction(before, after)
        assertTrue("watermark visible on the rotated page, changed=$changed", changed > 0.01f)
        before.recycle()
        after.recycle()
    }

    @Test
    fun addsPageNumbersSkippingACover() = runBlocking {
        val source = textPdf(4, "Num")
        val output = out("numbered")

        PdfOps.addPageNumbers(
            source,
            output,
            PageNumberOptions(
                format = "{n} of {total}",
                startNumber = 1,
                firstPageIndex = 1,
                position = NumberPosition.BOTTOM_CENTER,
            ),
        )

        val text = PdfOps.extractText(output)
        assertEquals(4, pageCountOf(output))
        // Three pages get numbered, starting on the second sheet.
        assertTrue(text.contains("1 of 3"))
        assertTrue(text.contains("3 of 3"))
    }

    @Test
    fun protectThenUnlockRoundTrips() = runBlocking {
        val source = textPdf(2, "Secret")
        val locked = out("locked")
        val unlocked = out("unlocked")

        PdfOps.protect(
            source,
            locked,
            ProtectOptions(
                userPassword = "correct horse",
                ownerPassword = "correct horse",
                allowCopying = true,
            ),
        )

        assertTrue("file is now protected", PdfOps.isPasswordProtected(locked))
        // The right password opens it; page content is unchanged.
        assertEquals(2, pageCountOf(locked, "correct horse"))

        PdfOps.unlock(locked, unlocked, "correct horse")

        assertTrue("protection removed", !PdfOps.isPasswordProtected(unlocked))
        assertTrue(PdfOps.extractText(unlocked).contains("Secret page 1"))
    }

    @Test
    fun restrictionsHoldForAReaderWithTheOpenPassword() = runBlocking {
        val source = textPdf(1, "Restricted")
        val locked = out("locked-restricted")
        // No owner password, as the Protect tool calls it.
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(
                userPassword = "open",
                allowPrinting = false,
                allowCopying = false,
                allowModifying = false,
            ),
        )

        PdfOps.load(locked, "open").use { doc ->
            val permission = doc.currentAccessPermission
            assertFalse("open password must not grant owner rights", permission.isOwnerPermission)
            assertFalse(permission.canPrint())
            assertFalse(permission.canExtractContent())
            assertFalse(permission.canModify())
        }
        val refused = runCatching { PdfOps.extractText(locked, "open") }.exceptionOrNull()
        assertTrue("copying is refused, was $refused", refused != null)

        // Unlock still works with only the open password.
        val unlocked = out("unlocked-restricted")
        PdfOps.unlock(locked, unlocked, "open")
        assertTrue(!PdfOps.isPasswordProtected(unlocked))
        assertTrue(PdfOps.extractText(unlocked).contains("Restricted page 1"))
    }

    @Test
    fun allowedPermissionsAreGrantedToTheOpenPassword() = runBlocking {
        val source = textPdf(1, "Open")
        val locked = out("locked-permissive")
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(userPassword = "open", allowPrinting = true, allowCopying = true),
        )

        PdfOps.load(locked, "open").use { doc ->
            val permission = doc.currentAccessPermission
            assertFalse(permission.isOwnerPermission)
            assertTrue(permission.canPrint())
            assertTrue(permission.canExtractContent())
        }
        assertTrue(PdfOps.extractText(locked, "open").contains("Open page 1"))
    }

    @Test
    fun wrongPasswordIsReportedDistinctly() = runBlocking {
        val source = textPdf(1, "Locked")
        val locked = out("locked-wrong")
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(userPassword = "right", ownerPassword = "right"),
        )

        val noPassword = runCatching { PdfOps.readMeta(locked) }.exceptionOrNull()
        assertTrue(
            "missing password reported as such, was $noPassword",
            noPassword is PdfPasswordRequiredException,
        )

        val badPassword = runCatching { PdfOps.readMeta(locked, "wrong") }.exceptionOrNull()
        assertTrue(
            "bad password reported as such, was $badPassword",
            badPassword is PdfWrongPasswordException,
        )
    }

    @Test
    fun editsWorkOnAnEncryptedDocument() = runBlocking {
        val source = textPdf(3, "Enc")
        val locked = out("locked-edit")
        PdfOps.protect(
            source,
            locked,
            ProtectOptions(userPassword = "pw", ownerPassword = "pw", allowCopying = true),
        )
        val output = out("encrypted-extract")

        PdfOps.extractPages(locked, output, listOf(0, 2), password = "pw")

        assertEquals(2, pageCountOf(output))
        assertTrue("result is decrypted", !PdfOps.isPasswordProtected(output))
    }

    @Test
    fun extractsTextWithPageMarkers() = runBlocking {
        val source = textPdf(3, "Words")

        val text = PdfOps.extractText(source)

        assertTrue(text.contains("--- Page 1 ---"))
        assertTrue(text.contains("--- Page 3 ---"))
        assertTrue(text.contains("Words page 3"))
    }

    @Test
    fun smartCompressionShrinksAnImagePdfAndKeepsPageCount() = runBlocking {
        val source = imageHeavyPdf()
        val output = out("compressed-smart")
        val originalBytes = source.length()

        val result = PdfConvert.compressSmart(
            input = source,
            output = output,
            jpegQuality = 0.6f,
            maxImageDimension = 1200,
        )

        assertEquals(1, pageCountOf(output))
        assertTrue(
            "expected a smaller file, was ${result.compressedBytes} vs $originalBytes",
            result.compressedBytes < originalBytes,
        )
        assertTrue("reports a positive saving", result.savedPercent > 0)
    }

    @Test
    fun smartCompressionShrinksAnImageSharedAcrossPagesOnce() = runBlocking {
        // A letterhead or logo: one image, drawn on every page. Recompressing it
        // per page used to write twelve copies, which outgrew the original, so the
        // never-grow guard handed back the input and nothing was saved at all.
        val pages = 12
        val source = File(workDir, "shared-${System.nanoTime()}.pdf")
        val bitmap = noiseBitmap(1400, 1000)
        PDDocument().use { document ->
            val image = LosslessFactory.createFromImage(document, bitmap)
            repeat(pages) {
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, 20f, 20f, 555f, 400f)
                }
            }
            document.save(source)
        }
        bitmap.recycle()
        val output = out("compressed-shared")

        val result = PdfConvert.compressSmart(source, output, 0.6f, 1200)

        assertTrue(
            "expected under half the size, was ${result.compressedBytes} vs ${result.originalBytes}",
            result.compressedBytes * 2 < result.originalBytes,
        )
        PdfOps.load(output).use { document ->
            assertEquals(pages, document.numberOfPages)
            val images = (0 until document.numberOfPages).map { index ->
                val resources = document.getPage(index).resources
                val image = resources.getXObject(resources.xObjectNames.first())
                assertTrue(
                    "page ${index + 1} still has its image",
                    image is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject,
                )
                image!!.cosObject
            }
            assertEquals("every page shares one replacement", 1, images.toSet().size)
        }
        // The shared replacement still draws: the image band of the last page is
        // not blank paper.
        val last = PdfRasterizer.open(output).use { it.renderByWidth(pages - 1, 300) }
        val inked = (0 until last.height step 4).sumOf { y ->
            (0 until last.width step 4).count { x -> last.getPixel(x, y) != Color.WHITE }
        }
        last.recycle()
        assertTrue("last page shows its image, $inked inked samples", inked > 500)
    }

    @Test
    fun smartCompressionLeavesTransparentImagesUntouched() = runBlocking {
        // An image with a soft mask cannot become a JPEG without losing its
        // transparency, so the compressor must decline rather than corrupt it.
        val source = imageHeavyPdf(opaque = false)
        val output = out("compressed-alpha")

        val result = PdfConvert.compressSmart(source, output, 0.6f, 1200)

        assertEquals(0, result.savedPercent)
        PdfOps.load(output).use { document ->
            val resources = document.getPage(0).resources
            val image = resources.getXObject(resources.xObjectNames.first())
            assertTrue("still an image", image is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject)
            assertNotNull(
                "transparency preserved",
                (image as com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject).softMask,
            )
        }
    }

    @Test
    fun smartCompressionNeverGrowsAFile() = runBlocking {
        // A text-only PDF has no images to squeeze, so the guard should kick in.
        val source = textPdf(2, "Lean")
        val output = out("compressed-lean")

        val result = PdfConvert.compressSmart(source, output, 0.6f, 1200)

        assertTrue(
            "output must not exceed the input",
            result.compressedBytes <= result.originalBytes,
        )
        assertEquals(2, pageCountOf(output))
    }

    @Test
    fun rasterizingCompressionPreservesPageGeometry() = runBlocking {
        val source = imageHeavyPdf()
        val output = out("compressed-raster")

        PdfConvert.compressByRasterizing(source, output, dpi = 110, jpegQuality = 0.6f)

        PdfOps.load(output).use { document ->
            assertEquals(1, document.numberOfPages)
            assertEquals(PDRectangle.A4.width, document.getPage(0).mediaBox.width, 1f)
            assertEquals(PDRectangle.A4.height, document.getPage(0).mediaBox.height, 1f)
        }
    }

    @Test
    fun exportsOnePerPageAsImages() = runBlocking {
        val source = textPdf(3, "Export")
        val directory = File(workDir, "images").apply { mkdirs() }

        val files = PdfConvert.pdfToImages(
            input = source,
            outputDir = directory,
            dpi = 96,
            format = ImageFormat.PNG,
            jpegQuality = 90,
            baseName = "page",
        )

        assertEquals(3, files.size)
        files.forEach { file ->
            assertTrue("${file.name} has content", file.length() > 0)
            val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("${file.name} decodes", decoded)
            assertTrue(decoded.width > 100)
            decoded.recycle()
        }
    }

    @Test
    fun buildsAPdfFromImages() = runBlocking {
        val first = pngFile(800, 600)
        val second = pngFile(600, 900)
        val output = out("from-images")

        PdfConvert.imagesToPdf(
            context = context,
            images = listOf(Uri.fromFile(first), Uri.fromFile(second)),
            output = output,
            options = ImagesToPdfOptions(pageSize = PdfPageSizePreset.FIT_IMAGE),
        )

        PdfOps.load(output).use { document ->
            assertEquals(2, document.numberOfPages)
            val landscape = document.getPage(0).mediaBox
            val portrait = document.getPage(1).mediaBox
            assertTrue("first page is landscape", landscape.width > landscape.height)
            assertTrue("second page is portrait", portrait.height > portrait.width)
        }
    }

    @Test
    fun buildsAnA4PdfFromImagesWithMargins() = runBlocking {
        val image = pngFile(1000, 700)
        val output = out("from-images-a4")

        PdfConvert.imagesToPdf(
            context = context,
            images = listOf(Uri.fromFile(image)),
            output = output,
            options = ImagesToPdfOptions(pageSize = PdfPageSizePreset.A4, marginPoints = 36f),
        )

        PdfOps.load(output).use { document ->
            assertEquals(PDRectangle.A4.width, document.getPage(0).mediaBox.width, 0.5f)
        }
    }

    @Test
    fun stampsAnImageOntoAChosenPage() = runBlocking {
        val source = textPdf(3, "Sign")
        val output = out("signed")
        val signature = noiseBitmap(600, 200)

        PdfOps.stampImages(
            input = source,
            output = output,
            stamps = listOf(
                ImageStamp(
                    pageIndex = 1,
                    bitmap = signature,
                    centerXRatio = 0.5f,
                    centerYRatio = 0.8f,
                    widthRatio = 0.3f,
                ),
            ),
        )

        assertEquals(3, pageCountOf(output))
        // The stamped page must now carry an image resource; the others must not.
        PdfOps.load(output).use { document ->
            val stamped = document.getPage(1).resources.xObjectNames.toList()
            val untouched = document.getPage(0).resources.xObjectNames.toList()
            assertTrue("stamped page gained an XObject", stamped.isNotEmpty())
            assertTrue("other pages untouched", untouched.isEmpty())
        }
        signature.recycle()
    }

    @Test
    fun rasterizerReportsSizesAndRendersPages() = runBlocking {
        val source = textPdf(2, "Render")

        PdfRasterizer.open(source).use { rasterizer ->
            assertEquals(2, rasterizer.pageCount)
            val sizes = rasterizer.pageSizes()
            assertEquals(2, sizes.size)
            assertEquals(PDRectangle.A4.width, sizes[0].width, 1.5f)

            val bitmap = rasterizer.renderByWidth(0, 640)
            assertEquals(640, bitmap.width)
            assertTrue("aspect ratio preserved", bitmap.height in 880..920)
            bitmap.recycle()

            val atDpi = rasterizer.renderAtDpi(1, 72)
            assertTrue(atDpi.width in 590..600)
            atDpi.recycle()
        }
    }

    @Test
    fun rejectsAFileThatIsNotAPdf() = runBlocking {
        val notPdf = File(workDir, "fake.pdf").apply { writeText("this is not a pdf") }
        val failure = runCatching { PdfOps.readMeta(notPdf) }.exceptionOrNull()
        assertNotNull("must reject non-PDF input", failure)
    }

    @Test
    fun organizeRefusesAnEmptyPlan() = runBlocking {
        val source = textPdf(2, "Empty")
        val failure = runCatching {
            PdfOps.organize(source, out("empty"), emptyList())
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
