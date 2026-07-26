package io.github.fanfeast.anonpdf.ui.tools

import android.content.Context
import android.net.Uri
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImagesToPdfOptions
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.PageRanges
import io.github.fanfeast.anonpdf.pdf.PdfConvert
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.Progress
import io.github.fanfeast.anonpdf.pdf.ProtectOptions
import io.github.fanfeast.anonpdf.pdf.WatermarkOptions
import io.github.fanfeast.anonpdf.ui.components.formatBytes

/**
 * Translates a tool's on-screen options into calls on [PdfOps] / [PdfConvert].
 *
 * Kept out of the composable so the mapping is testable and the UI file stays
 * about layout.
 */
class ToolRunner(
    private val context: Context,
    private val store: DocumentStore,
) {

    suspend fun run(
        spec: ToolSpec,
        docs: List<InputDoc>,
        imageUris: List<Uri>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult = when (spec.id) {
        ToolId.MERGE -> runMerge(docs, onProgress)
        ToolId.SPLIT -> runSplit(docs.single(), options, onProgress)
        ToolId.EXTRACT -> runExtract(docs.single(), options, onProgress)
        ToolId.ROTATE -> runRotate(docs.single(), options, onProgress)
        ToolId.CROP -> runCrop(docs.single(), options, onProgress)
        ToolId.COMPRESS -> runCompress(docs.single(), options, onProgress)
        ToolId.PDF_TO_IMAGES -> runPdfToImages(docs.single(), options, onProgress)
        ToolId.IMAGES_TO_PDF -> runImagesToPdf(imageUris, options, onProgress)
        ToolId.EXTRACT_TEXT -> runExtractText(docs.single(), onProgress)
        ToolId.WATERMARK -> runWatermark(docs.single(), options, onProgress)
        ToolId.PAGE_NUMBERS -> runPageNumbers(docs.single(), options, onProgress)
        ToolId.PROTECT -> runProtect(docs.single(), options, onProgress)
        ToolId.UNLOCK -> runUnlock(docs.single(), onProgress)
        ToolId.ORGANIZE, ToolId.SIGN ->
            error("${spec.title} has its own screen and does not run here.")
    }

    private suspend fun runMerge(docs: List<InputDoc>, onProgress: Progress): ToolResult {
        require(docs.size >= 2) { "Pick at least two PDFs to merge." }
        docs.firstOrNull { it.password != null }?.let {
            error("Remove the password from \"${it.name}\" first, then merge.")
        }
        val inputs = docs.map { it.requireFile() }
        val output = store.newOutputFile("merged", "pdf")
        PdfOps.merge(inputs, output, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "merged.pdf",
            note = "${docs.size} files joined into ${formatBytes(output.length())}.",
        )
    }

    private suspend fun runSplit(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val ranges = when (options.splitMode) {
            SplitMode.RANGES -> PageRanges.parse(options.pageRange, doc.pageCount)
            SplitMode.EVERY -> {
                val size = options.splitEvery.coerceAtLeast(1)
                require(size < doc.pageCount) {
                    "Splitting every $size page${if (size == 1) "" else "s"} would " +
                        "not divide a ${doc.pageCount}-page document."
                }
                (0 until doc.pageCount step size).map { start ->
                    start..minOf(start + size - 1, doc.pageCount - 1)
                }
            }
        }
        val stem = DocumentStore.stem(doc.name)
        val directory = store.newOutputDir("$stem-split")
        val files = PdfOps.splitToFiles(
            input = doc.requireFile(),
            outputDir = directory,
            ranges = ranges,
            baseName = stem,
            password = doc.password,
            onProgress = onProgress,
        )
        return ToolResult.Many(
            files = files,
            note = "${files.size} file${if (files.size == 1) "" else "s"} ready.",
        )
    }

    private suspend fun runExtract(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val pages = PageRanges.toPageList(PageRanges.parse(options.pageRange, doc.pageCount))
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-pages", "pdf")
        PdfOps.extractPages(doc.requireFile(), output, pages, doc.password, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-pages.pdf",
            note = "Kept page${if (pages.size == 1) "" else "s"} ${PageRanges.describe(pages)}.",
        )
    }

    private suspend fun runRotate(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-rotated", "pdf")
        PdfOps.rotateAll(doc.requireFile(), output, options.rotation, doc.password, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-rotated.pdf",
            note = "Every page turned ${options.rotation}°.",
        )
    }

    private suspend fun runCrop(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val insets = if (options.cropPerEdge) {
            CropInsets(
                left = options.cropLeft,
                top = options.cropTop,
                right = options.cropRight,
                bottom = options.cropBottom,
            )
        } else {
            val inset = options.cropPreset.inset
            CropInsets(inset, inset, inset, inset)
        }
        require(!insets.isEmpty) { "Choose how much to trim." }
        require(insets.left + insets.right < 0.9f && insets.top + insets.bottom < 0.9f) {
            "That would crop away the whole page."
        }
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-cropped", "pdf")
        PdfOps.crop(doc.requireFile(), output, insets, doc.password, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-cropped.pdf",
            note = "Crop applied to all ${doc.pageCount} pages. " +
                "The trimmed content is hidden, not deleted, so it can be undone " +
                "by any PDF editor.",
        )
    }

    private suspend fun runCompress(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val strength = options.compressStrength
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-compressed", "pdf")
        val result = when (options.compressMode) {
            CompressMode.SMART -> PdfConvert.compressSmart(
                input = doc.requireFile(),
                output = output,
                jpegQuality = strength.quality,
                maxImageDimension = strength.maxEdge,
                password = doc.password,
                onProgress = onProgress,
            )

            CompressMode.RASTERIZE -> PdfConvert.compressByRasterizing(
                input = doc.requireFile(),
                output = output,
                dpi = strength.dpi,
                jpegQuality = strength.quality,
                password = doc.password,
                onProgress = onProgress,
            )
        }
        val note = if (result.savedPercent <= 0) {
            "No saving available in this mode — the file is already lean. " +
                "${formatBytes(result.compressedBytes)}." +
                if (options.compressMode == CompressMode.SMART) {
                    " Try \"Smallest file\" if you do not need selectable text."
                } else {
                    ""
                }
        } else {
            "${formatBytes(result.originalBytes)} → " +
                "${formatBytes(result.compressedBytes)} " +
                "(${result.savedPercent}% smaller)."
        }
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-compressed.pdf",
            note = note,
        )
    }

    private suspend fun runPdfToImages(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val stem = DocumentStore.stem(doc.name)
        val directory = store.newOutputDir("$stem-images")
        val files = PdfConvert.pdfToImages(
            input = doc.requireFile(),
            outputDir = directory,
            dpi = options.exportDpi,
            format = options.exportFormat,
            jpegQuality = options.exportQuality,
            baseName = stem,
            password = doc.password,
            onProgress = onProgress,
        )
        return ToolResult.Many(
            files = files,
            note = "${files.size} image${if (files.size == 1) "" else "s"} at " +
                "${options.exportDpi} dpi.",
        )
    }

    private suspend fun runImagesToPdf(
        images: List<Uri>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(images.isNotEmpty()) { "Pick at least one image." }
        val output = store.newOutputFile("images", "pdf")
        PdfConvert.imagesToPdf(
            context = context,
            images = images,
            output = output,
            options = ImagesToPdfOptions(
                pageSize = options.imagesPageSize,
                marginPoints = options.imagesMargin,
            ),
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "images.pdf",
            note = "${images.size} image${if (images.size == 1) "" else "s"} → " +
                formatBytes(output.length()) + ".",
        )
    }

    private suspend fun runExtractText(doc: InputDoc, onProgress: Progress): ToolResult {
        val text = PdfOps.extractText(doc.requireFile(), doc.password, onProgress)
        val stem = DocumentStore.stem(doc.name)
        val output = store.newOutputFile("$stem-text", "txt")
        output.writeText(text)
        val note = if (text.isBlank() || text.count { it.isLetterOrDigit() } < 20) {
            "Almost no text found. These pages are probably scans — images of " +
                "text rather than text itself."
        } else {
            "${text.length} characters extracted."
        }
        return ToolResult.One(
            file = output,
            suggestedName = "$stem.txt",
            isText = true,
            note = note,
        )
    }

    private suspend fun runWatermark(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(options.watermarkText.isNotBlank()) { "Enter the watermark text." }
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-watermarked", "pdf")
        PdfOps.watermark(
            input = doc.requireFile(),
            output = output,
            options = WatermarkOptions(
                text = options.watermarkText.trim(),
                fontSize = options.watermarkFontSize,
                opacity = options.watermarkOpacity,
                layout = options.watermarkLayout,
            ),
            password = doc.password,
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-watermarked.pdf",
            note = "Watermarked all ${doc.pageCount} pages.",
        )
    }

    private suspend fun runPageNumbers(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(options.numberFormat.contains("{n}")) {
            "The format needs to include {n}, which stands for the page number."
        }
        require(options.numberSkip in 0 until doc.pageCount) {
            "You cannot skip all ${doc.pageCount} pages."
        }
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-numbered", "pdf")
        PdfOps.addPageNumbers(
            input = doc.requireFile(),
            output = output,
            options = PageNumberOptions(
                format = options.numberFormat,
                position = options.numberPosition,
                startNumber = options.numberStart,
                firstPageIndex = options.numberSkip,
                fontSize = options.numberFontSize,
            ),
            password = doc.password,
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-numbered.pdf",
            note = if (options.numberSkip > 0) {
                "Numbered from page ${options.numberSkip + 1} onwards."
            } else {
                "All ${doc.pageCount} pages numbered."
            },
        )
    }

    private suspend fun runProtect(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(options.protectPassword.isNotEmpty()) { "Choose a password." }
        require(options.protectPassword == options.protectConfirm) {
            "The two passwords do not match."
        }
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-protected", "pdf")
        PdfOps.protect(
            input = doc.requireFile(),
            output = output,
            options = ProtectOptions(
                userPassword = options.protectPassword,
                ownerPassword = options.protectPassword,
                allowPrinting = options.allowPrinting,
                allowCopying = options.allowCopying,
                allowModifying = options.allowModifying,
            ),
            password = doc.password,
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-protected.pdf",
            note = "Encrypted with AES-256. There is no recovery if you forget " +
                "this password — nothing about it leaves your device.",
        )
    }

    private suspend fun runUnlock(doc: InputDoc, onProgress: Progress): ToolResult {
        val password = doc.password
            ?: error("Open the file with its password first.")
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-unlocked", "pdf")
        PdfOps.unlock(doc.requireFile(), output, password, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-unlocked.pdf",
            note = "Password removed. Keep the new file somewhere safe.",
        )
    }

    private fun InputDoc.requireFile() =
        localFile ?: error("\"$name\" is still being prepared.")
}
