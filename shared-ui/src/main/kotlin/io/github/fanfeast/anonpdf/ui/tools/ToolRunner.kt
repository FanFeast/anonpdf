package io.github.fanfeast.anonpdf.ui.tools

import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImagesToPdfOptions
import io.github.fanfeast.anonpdf.pdf.OcrPacks
import io.github.fanfeast.anonpdf.pdf.PageRanges
import io.github.fanfeast.anonpdf.pdf.PdfCompare
import io.github.fanfeast.anonpdf.pdf.PdfConvert
import io.github.fanfeast.anonpdf.pdf.PdfDocInfo
import io.github.fanfeast.anonpdf.pdf.PdfEditor
import io.github.fanfeast.anonpdf.pdf.PdfImages
import io.github.fanfeast.anonpdf.pdf.PdfOcr
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.Progress
import io.github.fanfeast.anonpdf.pdf.ProtectOptions
import io.github.fanfeast.anonpdf.pdf.Recipes
import io.github.fanfeast.anonpdf.ui.common.DocumentRef
import io.github.fanfeast.anonpdf.ui.components.formatBytes
import java.io.File

/**
 * Translates a tool's on-screen options into calls on [PdfOps] / [PdfConvert].
 *
 * Kept out of the composable so the mapping is testable and the UI file stays
 * about layout.
 */
class ToolRunner(private val store: DocumentStore, private val ocrPacks: OcrPacks) {

    suspend fun run(
        spec: ToolSpec,
        docs: List<InputDoc>,
        images: List<DocumentRef>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult = when (spec.id) {
        ToolId.MERGE -> runMerge(docs, onProgress)
        ToolId.COMPARE -> runCompare(docs, onProgress)
        ToolId.RECIPE -> runRecipe(docs, options, onProgress)
        ToolId.SPLIT -> runSplit(docs.single(), options, onProgress)
        ToolId.EXTRACT -> runExtract(docs.single(), options, onProgress)
        ToolId.COMPRESS -> runCompress(docs.single(), options, onProgress)
        ToolId.PDF_TO_IMAGES -> runPdfToImages(docs.single(), options, onProgress)
        ToolId.IMAGES_TO_PDF -> runImagesToPdf(images, options, onProgress)
        ToolId.EXTRACT_TEXT -> runExtractText(docs.single(), onProgress)
        ToolId.OCR -> runOcr(docs.single(), options, onProgress)
        ToolId.EXTRACT_FILES -> runExtractFiles(docs.single(), options, onProgress)
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

    private suspend fun runCompare(docs: List<InputDoc>, onProgress: Progress): ToolResult {
        require(docs.size == 2) { S.pickExactlyTwo }
        val (original, changed) = docs
        val output = store.newOutputFile("comparison", "pdf")
        val found = PdfCompare.compare(
            original = original.requireFile(),
            changed = changed.requireFile(),
            output = output,
            originalPassword = original.password,
            changedPassword = changed.password,
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "${DocumentStore.stem(changed.name)}-compared.pdf",
            note = if (found.identical) {
                S.compareIdentical
            } else {
                S.compareNote(found.added, found.removed, found.pagesWithChanges)
            },
        )
    }

    /** Every file edited from scratch by the same recipe, each saved as its own copy. */
    private suspend fun runRecipe(
        docs: List<InputDoc>,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val recipe = options.recipe ?: error(S.recipePickOne)
        require(docs.isNotEmpty()) { S.choosePdf }
        // Checked before anything is written, so one bad file doesn't leave half a batch.
        val plans = docs.map { doc ->
            Recipes.plan(recipe, doc.pageCount).also { require(it.kept.isNotEmpty()) { S.recipeEmptiesFile(doc.name) } }
        }
        val directory = store.newOutputDir("recipe")
        val used = mutableSetOf<String>()
        val files = docs.mapIndexed { index, doc ->
            val stem = DocumentStore.stem(doc.name)
            // Two inputs with the same name would otherwise overwrite each other.
            var name = "$stem-edited.pdf"
            var n = 2
            while (!used.add(name)) name = "$stem-edited-${n++}.pdf"
            val output = File(directory, name)
            PdfEditor.applyPlan(doc.requireFile(), output, plans[index], doc.password) { value ->
                onProgress((index + value) / docs.size)
            }
            output
        }
        val note = S.recipeEditedWith(recipe.name)
        return if (files.size == 1) {
            ToolResult.One(file = files.single(), suggestedName = files.single().name, note = note)
        } else {
            ToolResult.Many(files = files, note = "$note ${S.filesReady(files.size)}")
        }
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

    private suspend fun runExtractFiles(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val stem = DocumentStore.stem(doc.name)
        val files = when (options.extractWhat) {
            ExtractWhat.IMAGES ->
                PdfImages.extract(doc.requireFile(), store.newOutputDir("$stem-images"), stem, doc.password, onProgress)
            ExtractWhat.ATTACHMENTS ->
                PdfDocInfo.extractAttachments(doc.requireFile(), store.newOutputDir("$stem-attachments"), doc.password, onProgress)
                    .also { require(it.isNotEmpty()) { S.noAttachmentsFound } }
        }
        return ToolResult.Many(files = files, note = S.filesReady(files.size))
    }

    private suspend fun runOcr(
        doc: InputDoc,
        options: ToolOptionsState,
        onProgress: Progress,
    ): ToolResult {
        val stem = DocumentStore.stem(doc.name)
        val output = store.newOutputFile("$stem-searchable", "pdf")
        val found = PdfOcr.recognise(
            input = doc.requireFile(),
            output = output,
            packs = ocrPacks,
            languages = options.ocrLanguages,
            password = doc.password,
            redoPagesWithText = options.ocrRedoText,
            onProgress = onProgress,
        )
        return ToolResult.One(
            file = output,
            suggestedName = "$stem-searchable.pdf",
            note = if (found.recognised == 0) S.ocrNothingRead(found.skipped) else S.ocrNote(found.recognised, found.skipped),
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
