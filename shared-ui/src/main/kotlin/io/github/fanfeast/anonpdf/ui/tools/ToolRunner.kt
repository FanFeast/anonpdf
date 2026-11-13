package io.github.fanfeast.anonpdf.ui.tools

import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImagesToPdfOptions
import io.github.fanfeast.anonpdf.pdf.PageRanges
import io.github.fanfeast.anonpdf.pdf.PdfConvert
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.Progress
import io.github.fanfeast.anonpdf.pdf.ProtectOptions
import io.github.fanfeast.anonpdf.ui.common.DocumentRef
import io.github.fanfeast.anonpdf.ui.components.formatBytes

/**
 * Translates a tool's on-screen options into calls on [PdfOps] / [PdfConvert].
 *
 * Kept out of the composable so the mapping is testable and the UI file stays
 * about layout.
 */
class ToolRunner(private val store: DocumentStore) {

    suspend fun run(
        spec: ToolSpec,
        docs: List<InputDoc>,
        images: List<DocumentRef>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult = when (spec.id) {
        ToolId.MERGE -> runMerge(docs, onProgress)
        ToolId.SPLIT -> runSplit(docs.single(), options, onProgress)
        ToolId.EXTRACT -> runExtract(docs.single(), options, onProgress)
        ToolId.COMPRESS -> runCompress(docs.single(), options, onProgress)
        ToolId.PDF_TO_IMAGES -> runPdfToImages(docs.single(), options, onProgress)
        ToolId.IMAGES_TO_PDF -> runImagesToPdf(images, options, onProgress)
        ToolId.EXTRACT_TEXT -> runExtractText(docs.single(), onProgress)
        ToolId.PROTECT -> runProtect(docs.single(), options, onProgress)
        ToolId.UNLOCK -> runUnlock(docs.single(), onProgress)
        ToolId.SIGN -> error(S.hasOwnScreen(spec.title))
    }

    private suspend fun runMerge(docs: List<InputDoc>, onProgress: Progress): ToolResult {
        require(docs.size >= 2) { S.pickTwoPdfs }
        docs.firstOrNull { it.password != null }?.let {
            error(S.removePasswordFirst(it.name))
        }
        val inputs = docs.map { it.requireFile() }
        val output = store.newOutputFile("merged", "pdf")
        PdfOps.merge(inputs, output, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "merged.pdf",
            note = S.mergedNote(docs.size, formatBytes(output.length())),
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
                    S.splitDoesNotDivide(size, doc.pageCount)
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
            note = S.filesReady(files.size),
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
            note = S.keptPages(pages.size, PageRanges.describe(pages)),
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
            S.alreadyLean(formatBytes(result.compressedBytes), smart = options.compressMode == CompressMode.SMART)
        } else {
            S.compressedNote(
                formatBytes(result.originalBytes),
                formatBytes(result.compressedBytes),
                result.savedPercent,
            )
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
            note = S.imagesAtDpi(files.size, options.exportDpi),
        )
    }

    private suspend fun runImagesToPdf(
        images: List<DocumentRef>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(images.isNotEmpty()) { S.pickOneImage }
        val output = store.newOutputFile("images", "pdf")
        PdfConvert.imagesToPdf(
            images = images,
            decode = store::decodeImage,
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
            note = S.imagesToPdfNote(images.size, formatBytes(output.length())),
        )
    }

    private suspend fun runExtractText(doc: InputDoc, onProgress: Progress): ToolResult {
        val text = PdfOps.extractText(doc.requireFile(), doc.password, onProgress)
        val stem = DocumentStore.stem(doc.name)
        val output = store.newOutputFile("$stem-text", "txt")
        output.writeText(text)
        val note = if (text.isBlank() || text.count { it.isLetterOrDigit() } < 20) {
            S.probablyScans
        } else {
            S.charactersExtracted(text.length)
        }
        return ToolResult.One(
            file = output,
            suggestedName = "$stem.txt",
            isText = true,
            note = note,
        )
    }

    private suspend fun runProtect(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        require(options.protectPassword.isNotEmpty()) { S.choosePassword }
        require(options.protectPassword == options.protectConfirm) {
            S.passwordsDoNotMatch
        }
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-protected", "pdf")
        PdfOps.protect(
            input = doc.requireFile(),
            output = output,
            options = ProtectOptions(
                userPassword = options.protectPassword,
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
            note = S.protectedNote,
        )
    }

    private suspend fun runUnlock(doc: InputDoc, onProgress: Progress): ToolResult {
        val password = doc.password
            ?: error(S.openWithPasswordFirst)
        val output = store.newOutputFile("${DocumentStore.stem(doc.name)}-unlocked", "pdf")
        PdfOps.unlock(doc.requireFile(), output, password, onProgress)
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(doc.name)}-unlocked.pdf",
            note = S.unlockedNote,
        )
    }

    private fun InputDoc.requireFile() =
        localFile ?: error(S.stillPreparing(name))
}
