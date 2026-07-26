package io.github.fanfeast.anonpdf.pdf

import android.util.SizeF
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Every PDF transformation AnonPDF can perform. Pure file-in / file-out, no
 * Android UI types, no network, no temp files outside the app cache.
 *
 * Page manipulation is done *in place* on the loaded document (pin inherited
 * attributes, detach, re-attach in the new order) rather than by copying pages
 * into a fresh document. Copying is the obvious approach but it silently drops
 * annotations and page-tree-inherited resources; editing in place keeps them.
 */
object PdfOps {

    /** Above this, PDFBox spills to a temp file instead of holding the doc in RAM. */
    private const val MAX_MAIN_MEMORY_BYTES = 24L * 1024 * 1024

    private fun memorySetting(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)

    /**
     * Opens a document, translating PDFBox's password failures into our own types
     * so the UI can tell "needs a password" apart from "wrong password".
     */
    fun load(file: File, password: String? = null): PDDocument = try {
        if (password.isNullOrEmpty()) {
            PDDocument.load(file, memorySetting())
        } else {
            PDDocument.load(file, password, memorySetting())
        }
    } catch (e: InvalidPasswordException) {
        if (password.isNullOrEmpty()) throw PdfPasswordRequiredException() else throw PdfWrongPasswordException()
    }

    /**
     * A document opened with a password stays flagged as encrypted, and PDFBox
     * refuses to re-save it. The user gave us the password, so drop the flag.
     */
    private fun PDDocument.prepareForSave() {
        if (isEncrypted) isAllSecurityToBeRemoved = true
    }

    suspend fun readMeta(file: File, password: String? = null): PdfMeta =
        withContext(Dispatchers.IO) {
            load(file, password).use { doc ->
                PdfMeta(
                    pageCount = doc.numberOfPages,
                    encrypted = doc.isEncrypted,
                    title = doc.documentInformation?.title?.takeIf { it.isNotBlank() },
                    author = doc.documentInformation?.author?.takeIf { it.isNotBlank() },
                    producer = doc.documentInformation?.producer?.takeIf { it.isNotBlank() },
                )
            }
        }

    /** Cheap probe used before we bother showing a password prompt. */
    suspend fun isPasswordProtected(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file, memorySetting()).use { false }
        } catch (e: InvalidPasswordException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- merge

    suspend fun merge(
        inputs: List<File>,
        output: File,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "Pick at least two PDFs to merge." }
        val merger = PDFMergerUtility()
        merger.destinationFileName = output.absolutePath
        inputs.forEachIndexed { index, file ->
            merger.addSource(file)
            onProgress((index + 1f) / (inputs.size + 1f))
        }
        merger.mergeDocuments(memorySetting())
        onProgress(1f)
    }

    // ------------------------------------------------------ organise pages

    /**
     * Rewrites the page list: [plan] is the output document in order. Pages not
     * mentioned are dropped; [PagePlan.rotationDelta] turns the ones that are.
     */
    suspend fun organize(
        input: File,
        output: File,
        plan: List<PagePlan>,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(plan.isNotEmpty()) { "A PDF needs at least one page." }
        load(input, password).use { doc ->
            val originals = (0 until doc.numberOfPages).map { doc.getPage(it) }
            originals.forEach { pinInheritedAttributes(it) }

            // Detach every page, then re-attach only the ones the plan asks for.
            originals.forEach { doc.pages.remove(it) }

            plan.forEachIndexed { index, item ->
                val page = originals.getOrNull(item.sourceIndex)
                    ?: error("Page ${item.sourceIndex + 1} is not in this document.")
                page.rotation = normalizeRotation(page.rotation + item.rotationDelta)
                doc.addPage(page)
                onProgress((index + 1f) / plan.size)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    suspend fun extractPages(
        input: File,
        output: File,
        pageIndices: List<Int>,
        password: String? = null,
        onProgress: Progress = {},
    ) = organize(input, output, pageIndices.map { PagePlan(it) }, password, onProgress)

    suspend fun rotateAll(
        input: File,
        output: File,
        degrees: Int,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        load(input, password).use { doc ->
            for (index in 0 until doc.numberOfPages) {
                val page = doc.getPage(index)
                page.rotation = normalizeRotation(page.rotation + degrees)
                onProgress((index + 1f) / doc.numberOfPages)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    /**
     * Splits into one file per range. Each range is loaded fresh and trimmed
     * down, which keeps per-page annotations intact in every output.
     */
    suspend fun splitToFiles(
        input: File,
        outputDir: File,
        ranges: List<IntRange>,
        baseName: String,
        password: String? = null,
        onProgress: Progress = {},
    ): List<File> = withContext(Dispatchers.IO) {
        require(ranges.isNotEmpty()) { "Describe at least one page range." }
        outputDir.mkdirs()
        ranges.mapIndexed { index, range ->
            val target = File(outputDir, "${DocumentStore.sanitize(baseName)}-${index + 1}.pdf")
            load(input, password).use { doc ->
                val keep = range.filter { it in 0 until doc.numberOfPages }
                require(keep.isNotEmpty()) { "Range ${index + 1} does not cover any pages." }
                val originals = (0 until doc.numberOfPages).map { doc.getPage(it) }
                originals.forEach { pinInheritedAttributes(it) }
                originals.forEach { doc.pages.remove(it) }
                keep.forEach { doc.addPage(originals[it]) }
                doc.prepareForSave()
                doc.save(target)
            }
            onProgress((index + 1f) / ranges.size)
            target
        }
    }

    // ----------------------------------------------------------------- crop

    suspend fun crop(
        input: File,
        output: File,
        insets: CropInsets,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        load(input, password).use { doc ->
            for (index in 0 until doc.numberOfPages) {
                val page = doc.getPage(index)
                val box = page.cropBox
                val left = box.width * insets.left
                val right = box.width * insets.right
                val top = box.height * insets.top
                val bottom = box.height * insets.bottom
                val newWidth = box.width - left - right
                val newHeight = box.height - top - bottom
                if (newWidth > 1f && newHeight > 1f) {
                    page.cropBox = PDRectangle(
                        box.lowerLeftX + left,
                        box.lowerLeftY + bottom,
                        newWidth,
                        newHeight,
                    )
                }
                onProgress((index + 1f) / doc.numberOfPages)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    // ------------------------------------------------------------ watermark

    suspend fun watermark(
        input: File,
        output: File,
        options: WatermarkOptions,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(options.text.isNotBlank()) { "Enter some watermark text." }
        load(input, password).use { doc ->
            val font: PDFont =
                if (options.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
            val text = sanitizeForFont(options.text, font)
            if (text.isBlank()) error("That text cannot be drawn with the built-in fonts.")

            for (index in 0 until doc.numberOfPages) {
                val page = doc.getPage(index)
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true,
                ).use { cs ->
                    cs.saveGraphicsState()
                    val display = applyDisplayTransform(cs, page)

                    val graphicsState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = options.opacity
                        strokingAlphaConstant = options.opacity
                    }
                    cs.setGraphicsStateParameters(graphicsState)
                    cs.setNonStrokingColor(
                        red(options.color),
                        green(options.color),
                        blue(options.color),
                    )

                    val textWidth = font.getStringWidth(text) / 1000f * options.fontSize
                    when (options.layout) {
                        WatermarkLayout.DIAGONAL -> drawRotatedText(
                            cs, font, text, options.fontSize,
                            display.width / 2f, display.height / 2f, textWidth, 45.0,
                        )
                        WatermarkLayout.CENTER -> drawRotatedText(
                            cs, font, text, options.fontSize,
                            display.width / 2f, display.height / 2f, textWidth, 0.0,
                        )
                        WatermarkLayout.TOP -> drawRotatedText(
                            cs, font, text, options.fontSize,
                            display.width / 2f, display.height - options.fontSize * 2f,
                            textWidth, 0.0,
                        )
                        WatermarkLayout.BOTTOM -> drawRotatedText(
                            cs, font, text, options.fontSize,
                            display.width / 2f, options.fontSize * 2f, textWidth, 0.0,
                        )
                        WatermarkLayout.TILED -> drawTiled(
                            cs, font, text, options.fontSize, display, textWidth,
                        )
                    }
                    cs.restoreGraphicsState()
                }
                onProgress((index + 1f) / doc.numberOfPages)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    private fun drawTiled(
        cs: PDPageContentStream,
        font: PDFont,
        text: String,
        fontSize: Float,
        display: SizeF,
        textWidth: Float,
    ) {
        val stepX = max(textWidth * 1.6f, fontSize * 4f)
        val stepY = max(fontSize * 5f, 90f)
        val columns = ceil(display.width / stepX).toInt() + 1
        val rows = ceil(display.height / stepY).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                // Offset alternate rows so the pattern does not read as a grid.
                val offset = if (row % 2 == 0) 0f else stepX / 2f
                drawRotatedText(
                    cs, font, text, fontSize,
                    column * stepX + offset, row * stepY, textWidth, 30.0,
                )
            }
        }
    }

    private fun drawRotatedText(
        cs: PDPageContentStream,
        font: PDFont,
        text: String,
        fontSize: Float,
        centerX: Float,
        centerY: Float,
        textWidth: Float,
        degrees: Double,
    ) {
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance(centerX, centerY))
        if (degrees != 0.0) {
            cs.transform(Matrix.getRotateInstance(Math.toRadians(degrees), 0f, 0f))
        }
        cs.beginText()
        cs.setFont(font, fontSize)
        // Nudge down by roughly a third of the cap height to sit on the centre line.
        cs.newLineAtOffset(-textWidth / 2f, -fontSize / 3f)
        cs.showText(text)
        cs.endText()
        cs.restoreGraphicsState()
    }

    // --------------------------------------------------------- page numbers

    suspend fun addPageNumbers(
        input: File,
        output: File,
        options: PageNumberOptions,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        load(input, password).use { doc ->
            val font: PDFont = PDType1Font.HELVETICA
            val total = doc.numberOfPages
            val numbered = total - options.firstPageIndex

            for (index in 0 until total) {
                if (index < options.firstPageIndex) {
                    onProgress((index + 1f) / total)
                    continue
                }
                val number = options.startNumber + (index - options.firstPageIndex)
                val label = sanitizeForFont(
                    options.format
                        .replace("{n}", number.toString())
                        .replace("{total}", (options.startNumber + numbered - 1).toString()),
                    font,
                )
                if (label.isBlank()) {
                    onProgress((index + 1f) / total)
                    continue
                }

                val page = doc.getPage(index)
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true,
                ).use { cs ->
                    cs.saveGraphicsState()
                    val display = applyDisplayTransform(cs, page)
                    cs.setNonStrokingColor(
                        red(options.color),
                        green(options.color),
                        blue(options.color),
                    )
                    val width = font.getStringWidth(label) / 1000f * options.fontSize
                    val x = when (options.position) {
                        NumberPosition.TOP_LEFT, NumberPosition.BOTTOM_LEFT ->
                            options.marginPoints
                        NumberPosition.TOP_CENTER, NumberPosition.BOTTOM_CENTER ->
                            (display.width - width) / 2f
                        NumberPosition.TOP_RIGHT, NumberPosition.BOTTOM_RIGHT ->
                            display.width - options.marginPoints - width
                    }
                    val y = when (options.position) {
                        NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER,
                        NumberPosition.TOP_RIGHT,
                        -> display.height - options.marginPoints
                        else -> options.marginPoints
                    }
                    cs.beginText()
                    cs.setFont(font, options.fontSize)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
                onProgress((index + 1f) / total)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    // ---------------------------------------------------------- stamp image

    suspend fun stampImages(
        input: File,
        output: File,
        stamps: List<ImageStamp>,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(stamps.isNotEmpty()) { "Nothing to place." }
        load(input, password).use { doc ->
            stamps.forEachIndexed { index, stamp ->
                val page = doc.getPage(stamp.pageIndex)
                // Lossless keeps the alpha channel, so a signature drawn on a
                // transparent canvas does not arrive with a white box behind it.
                val image = LosslessFactory.createFromImage(doc, stamp.bitmap)
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true,
                ).use { cs ->
                    cs.saveGraphicsState()
                    val display = applyDisplayTransform(cs, page)
                    val width = display.width * stamp.widthRatio
                    val height = width * stamp.bitmap.height / stamp.bitmap.width.toFloat()
                    val x = display.width * stamp.centerXRatio - width / 2f
                    // The caller measures y downward from the top of the page.
                    val y = display.height * (1f - stamp.centerYRatio) - height / 2f
                    cs.drawImage(image, x, y, width, height)
                    cs.restoreGraphicsState()
                }
                onProgress((index + 1f) / stamps.size)
            }
            doc.prepareForSave()
            doc.save(output)
        }
    }

    // -------------------------------------------------------------- security

    suspend fun protect(
        input: File,
        output: File,
        options: ProtectOptions,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(options.userPassword.isNotEmpty()) { "Choose a password." }
        load(input, password).use { doc ->
            onProgress(0.3f)
            val permissions = AccessPermission().apply {
                setCanPrint(options.allowPrinting)
                setCanPrintFaithful(options.allowPrinting)
                setCanExtractContent(options.allowCopying)
                setCanExtractForAccessibility(true)
                setCanModify(options.allowModifying)
                setCanAssembleDocument(options.allowModifying)
                setCanModifyAnnotations(options.allowAnnotations)
                setCanFillInForm(options.allowAnnotations)
            }
            val policy = StandardProtectionPolicy(
                options.ownerPassword.ifEmpty { options.userPassword },
                options.userPassword,
                permissions,
            ).apply {
                encryptionKeyLength = 256
                // AES-256 rather than the legacy RC4 scheme. Called as a method
                // because PDFBox keeps the backing field private.
                setPreferAES(true)
            }
            // Re-protecting means we must NOT strip security on save.
            doc.isAllSecurityToBeRemoved = false
            doc.protect(policy)
            onProgress(0.7f)
            doc.save(output)
            onProgress(1f)
        }
    }

    suspend fun unlock(
        input: File,
        output: File,
        password: String,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        load(input, password).use { doc ->
            onProgress(0.4f)
            if (!doc.isEncrypted) error("This PDF is not password protected.")
            doc.isAllSecurityToBeRemoved = true
            onProgress(0.7f)
            doc.save(output)
            onProgress(1f)
        }
    }

    // ------------------------------------------------------------------ text

    suspend fun extractText(
        input: File,
        password: String? = null,
        onProgress: Progress = {},
    ): String = withContext(Dispatchers.IO) {
        load(input, password).use { doc ->
            if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) {
                error("This PDF does not allow text extraction.")
            }
            val builder = StringBuilder()
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (index in 1..doc.numberOfPages) {
                stripper.startPage = index
                stripper.endPage = index
                if (doc.numberOfPages > 1) builder.append("--- Page $index ---\n")
                builder.append(stripper.getText(doc)).append('\n')
                onProgress(index.toFloat() / doc.numberOfPages)
            }
            builder.toString()
        }
    }

    // ------------------------------------------------------------- internals

    /**
     * Copies page-tree-inherited attributes onto the page itself.
     *
     * MediaBox, CropBox, Resources and Rotate can live on an ancestor node. Once
     * we detach a page from its parent those lookups fail and the page renders
     * blank or wrongly sized. Reading then writing each value pins it in place.
     */
    private fun pinInheritedAttributes(page: PDPage) {
        page.mediaBox = page.mediaBox
        page.cropBox = page.cropBox
        page.rotation = page.rotation
        page.resources?.let { page.resources = it }
    }

    /**
     * Maps "what the user sees" onto PDF user space and returns the displayed
     * page size.
     *
     * A page carries a /Rotate that viewers apply before showing it, so a
     * watermark placed with raw page coordinates lands sideways on a rotated
     * page. This pre-multiplies the inverse rotation, letting every caller work
     * in upright display coordinates with the origin at the bottom left.
     */
    private fun applyDisplayTransform(cs: PDPageContentStream, page: PDPage): SizeF {
        val box = page.cropBox
        cs.transform(Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY))
        return when (normalizeRotation(page.rotation)) {
            90 -> {
                cs.transform(Matrix.getRotateInstance(Math.PI / 2, box.width, 0f))
                SizeF(box.height, box.width)
            }
            180 -> {
                cs.transform(Matrix.getRotateInstance(Math.PI, box.width, box.height))
                SizeF(box.width, box.height)
            }
            270 -> {
                cs.transform(Matrix.getRotateInstance(-Math.PI / 2, 0f, box.height))
                SizeF(box.height, box.width)
            }
            else -> SizeF(box.width, box.height)
        }
    }

    fun normalizeRotation(degrees: Int): Int {
        val snapped = (degrees / 90) * 90
        return ((snapped % 360) + 360) % 360
    }

    /**
     * The 14 built-in PDF fonts cover WinAnsi only, and [PDFont.showText] throws
     * on anything else. Swap unsupported characters for '?' so a stray emoji in a
     * watermark degrades instead of failing the whole operation.
     */
    private fun sanitizeForFont(text: String, font: PDFont): String {
        val flattened = text.replace(Regex("[\\r\\n\\t]"), " ")
        val builder = StringBuilder(flattened.length)
        for (character in flattened) {
            val candidate = character.toString()
            val supported = runCatching { font.getStringWidth(candidate) }.isSuccess
            builder.append(if (supported) candidate else "?")
        }
        return builder.toString().trim()
    }

    private fun red(color: Int) = ((color shr 16) and 0xFF) / 255f
    private fun green(color: Int) = ((color shr 8) and 0xFF) / 255f
    private fun blue(color: Int) = (color and 0xFF) / 255f

    /** Standard page boxes used when building a PDF from images. */
    fun presetRectangle(preset: PdfPageSizePreset, imageWidth: Int, imageHeight: Int): PDRectangle =
        when (preset) {
            PdfPageSizePreset.A4 -> PDRectangle.A4
            PdfPageSizePreset.LETTER -> PDRectangle.LETTER
            PdfPageSizePreset.FIT_IMAGE -> {
                // Treat the image as 72 dpi so a 1000px-wide photo becomes a
                // 1000pt-wide page: the ratio is what matters, not the absolute size.
                val width = max(1, imageWidth).toFloat()
                val height = max(1, imageHeight).toFloat()
                val scale = min(1f, MAX_FIT_POINTS / max(width, height))
                PDRectangle(width * scale, height * scale)
            }
        }

    private const val MAX_FIT_POINTS = 2200f
}
