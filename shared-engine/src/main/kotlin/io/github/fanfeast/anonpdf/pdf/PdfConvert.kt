package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Conversions between PDFs and pixels: compression, page export, and building a
 * PDF out of photos.
 */
object PdfConvert {

    // ------------------------------------------------------------- compress

    /**
     * Shrinks a PDF by re-encoding the images inside it, leaving text and vector
     * artwork untouched.
     *
     * This is the mode that should be tried first: the document stays selectable
     * and searchable. If a PDF is big because of its text or fonts rather than
     * its pictures, this will report a small saving and the user can escalate to
     * [compressByRasterizing].
     */
    suspend fun compressSmart(
        input: File,
        output: File,
        jpegQuality: Float,
        maxImageDimension: Int,
        password: String? = null,
        onProgress: Progress = {},
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalBytes = input.length()
        PdfOps.load(input, password).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val visited = mutableSetOf<COSBase>()
            val shrunk = mutableMapOf<COSBase, PDImageXObject?>()
            val total = max(1, doc.numberOfPages)
            for (index in 0 until doc.numberOfPages) {
                recompressResources(
                    doc = doc,
                    resources = doc.getPage(index).resources,
                    jpegQuality = jpegQuality,
                    maxImageDimension = maxImageDimension,
                    depth = 0,
                    visited = visited,
                    shrunk = shrunk,
                )
                onProgress(0.9f * (index + 1f) / total)
            }
            doc.save(output)
        }
        // A "compressed" file that grew is not a compressed file. Keep the original.
        if (output.length() >= originalBytes) {
            input.copyTo(output, overwrite = true)
        }
        onProgress(1f)
        CompressResult(originalBytes, output.length())
    }

    private fun recompressResources(
        doc: PDDocument,
        resources: PDResources?,
        jpegQuality: Float,
        maxImageDimension: Int,
        depth: Int,
        visited: MutableSet<COSBase>,
        shrunk: MutableMap<COSBase, PDImageXObject?>,
    ) {
        // Form XObjects can nest, and a malformed file can even make them cyclic.
        if (resources == null || depth > MAX_FORM_DEPTH) return

        for (name in resources.xObjectNames.toList()) {
            val xobject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            when (xobject) {
                is PDImageXObject -> {
                    // One image is often drawn on every page — a logo, a letterhead.
                    // Each page's resources point at the same stream, so decide once
                    // per stream and hand every page the same replacement. Shrinking
                    // it per page would write a separate copy for each one, and the
                    // "compressed" file would come out bigger than the original.
                    // Not getOrPut: it treats a stored null ("leave this one alone")
                    // as missing and would decode the image again on every page.
                    val key = xobject.cosObject
                    val replacement = if (key in shrunk) {
                        shrunk[key]
                    } else {
                        runCatching {
                            shrinkImage(doc, xobject, jpegQuality, maxImageDimension)
                        }.getOrNull().also { shrunk[key] = it }
                    }
                    if (replacement != null) resources.put(name, replacement)
                }

                is PDFormXObject -> {
                    if (visited.add(xobject.cosObject)) {
                        recompressResources(
                            doc, xobject.resources, jpegQuality,
                            maxImageDimension, depth + 1, visited, shrunk,
                        )
                    }
                }
            }
        }
    }

    /** Returns a smaller JPEG version of [image], or null to leave it alone. */
    private fun shrinkImage(
        doc: PDDocument,
        image: PDImageXObject,
        jpegQuality: Float,
        maxImageDimension: Int,
    ): PDImageXObject? {
        // A stencil is a 1-bit mask; a soft-masked image carries transparency.
        // JPEG can represent neither, so re-encoding would corrupt them.
        if (image.isStencil || image.softMask != null || image.mask != null) return null

        val bitmap = runCatching { image.image }.getOrNull() ?: return null
        try {
            val currentBytes = image.cosObject.getInt(COSName.LENGTH, Int.MAX_VALUE)
            val scaled = downscale(bitmap, maxImageDimension)
            val flattened = scaled.flattenedOntoWhite()

            val buffer = ByteArrayOutputStream()
            flattened.writeTo(buffer, ImageFormat.JPEG, (jpegQuality * 100).roundToInt())
            val encoded = buffer.toByteArray()
            if (flattened !== bitmap) flattened.release()
            if (scaled !== bitmap && scaled !== flattened) scaled.release()

            // Only swap in the new copy if it is actually a win.
            if (encoded.size >= currentBytes) return null
            return JPEGFactory.createFromStream(doc, ByteArrayInputStream(encoded))
        } finally {
            runCatching { bitmap.release() }
        }
    }

    /**
     * Rebuilds every page as a flat image. Maximum, predictable size reduction at
     * the cost of selectable text — the UI says so before the user commits.
     */
    suspend fun compressByRasterizing(
        input: File,
        output: File,
        dpi: Int,
        jpegQuality: Float,
        password: String? = null,
        onProgress: Progress = {},
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalBytes = input.length()
        val source = decryptedCopyIfNeeded(input, password)
        try {
            PdfRasterizer.open(source).use { rasterizer ->
                val sizes = rasterizer.pageSizes()
                PDDocument().use { doc ->
                    sizes.forEachIndexed { index, size ->
                        val bitmap = rasterizer.renderAtDpi(index, dpi)
                        val page = PDPage(PdfOps.presetRectangle(PdfPageSizePreset.FIT_IMAGE, 1, 1))
                        // Keep the original page geometry so print output matches.
                        page.mediaBox = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(
                            size.width,
                            size.height,
                        )
                        doc.addPage(page)
                        val image = JPEGFactory.createFromImage(doc, bitmap, jpegQuality)
                        PDPageContentStream(doc, page).use { cs ->
                            cs.drawImage(image, 0f, 0f, size.width, size.height)
                        }
                        bitmap.release()
                        onProgress(0.9f * (index + 1f) / sizes.size)
                    }
                    doc.save(output)
                }
            }
        } finally {
            if (source != input) source.delete()
        }
        onProgress(1f)
        CompressResult(originalBytes, output.length())
    }

    // -------------------------------------------------------- export images

    suspend fun pdfToImages(
        input: File,
        outputDir: File,
        dpi: Int,
        format: ImageFormat,
        jpegQuality: Int,
        baseName: String,
        password: String? = null,
        onProgress: Progress = {},
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val source = decryptedCopyIfNeeded(input, password)
        try {
            PdfRasterizer.open(source).use { rasterizer ->
                val count = rasterizer.pageCount
                val digits = count.toString().length
                (0 until count).map { index ->
                    val bitmap = rasterizer.renderAtDpi(index, dpi)
                    val label = (index + 1).toString().padStart(digits, '0')
                    val target = File(
                        outputDir,
                        "${FileNames.sanitize(baseName)}-$label.${format.extension}",
                    )
                    target.outputStream().use { stream ->
                        bitmap.writeTo(stream, format, jpegQuality)
                    }
                    bitmap.release()
                    onProgress((index + 1f) / count)
                    target
                }
            }
        } finally {
            if (source != input) source.delete()
        }
    }

    // --------------------------------------------------------- images -> pdf

    /**
     * One page per image, in order. [decode] reads an image the way its platform
     * should (upright, per its EXIF orientation, and no bigger than
     * [MAX_SOURCE_IMAGE_EDGE]), or returns null if it cannot.
     */
    suspend fun <T> imagesToPdf(
        images: List<T>,
        decode: suspend (T) -> PageImage?,
        output: File,
        options: ImagesToPdfOptions,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "Pick at least one image." }
        PDDocument().use { doc ->
            images.forEachIndexed { index, source ->
                val bitmap = decode(source)
                    ?: error("Could not read image ${index + 1}.")
                val flattened = bitmap.flattenedOntoWhite()

                val pageBox = PdfOps.presetRectangle(
                    options.pageSize,
                    flattened.width,
                    flattened.height,
                )
                val page = PDPage(pageBox)
                doc.addPage(page)

                val available = pageBox.width - options.marginPoints * 2
                val availableHeight = pageBox.height - options.marginPoints * 2
                val scale = min(
                    available / flattened.width,
                    availableHeight / flattened.height,
                )
                val drawWidth = flattened.width * scale
                val drawHeight = flattened.height * scale
                val x = (pageBox.width - drawWidth) / 2f
                val y = (pageBox.height - drawHeight) / 2f

                val image = JPEGFactory.createFromImage(doc, flattened, options.jpegQuality)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, x, y, drawWidth, drawHeight)
                }
                if (flattened !== bitmap) flattened.release()
                bitmap.release()
                onProgress(0.95f * (index + 1f) / images.size)
            }
            doc.save(output)
        }
        onProgress(1f)
    }

    // ------------------------------------------------------------- internals

    /**
     * Android's PdfRenderer cannot open an encrypted PDF at all, so when the user
     * has given us a password we hand the rasterizer a decrypted scratch copy.
     */
    private fun decryptedCopyIfNeeded(input: File, password: String?): File {
        if (password.isNullOrEmpty()) return input
        val plain = File(input.parentFile, "plain-${System.nanoTime()}.pdf")
        PdfOps.load(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(plain)
        }
        return plain
    }

    private fun downscale(bitmap: PageImage, maxDimension: Int): PageImage {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (maxDimension <= 0 || longestEdge <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longestEdge
        val width = max(1, (bitmap.width * scale).roundToInt())
        val height = max(1, (bitmap.height * scale).roundToInt())
        return bitmap.scaledTo(width, height)
    }

    private const val MAX_FORM_DEPTH = 6

    /** Longest edge a decoded source image is allowed, so a 50 MP photo fits in memory. */
    const val MAX_SOURCE_IMAGE_EDGE = 4000
}
