package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.bytedeco.leptonica.global.leptonica.pixDestroy
import org.bytedeco.leptonica.global.leptonica.pixRead
import org.bytedeco.leptonica.global.leptonica.pixSetResolution
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.TessPDFRenderer
import java.io.File
import kotlin.math.roundToInt

/** What recognising a document did. */
data class OcrResult(val recognised: Int, val skipped: Int, val total: Int)

/**
 * Text recognition: makes a scanned PDF searchable, selectable and redactable.
 *
 * Each page is drawn as a picture, Tesseract reads it, and the words it finds go
 * back onto the original page as invisible text, exactly over the letters they
 * came from. Nothing visible changes: the page keeps its own picture and vector
 * artwork, and only gains a text layer, the way a scanner's own "searchable PDF"
 * does. Tesseract writes that layer itself (its text-only PDF), with a font that
 * covers every script, so no font of ours is involved.
 *
 * Tesseract runs in the app's own process, from the library inside the app. The
 * language packs it reads are data the user adds; see [OcrPacks].
 */
object PdfOcr {

    /**
     * Whether the recogniser can run here at all. It can't on a 32-bit phone,
     * where the app has no native library for it; everything else still works.
     */
    val isAvailable: Boolean by lazy {
        runCatching { TessBaseAPI.Version().string.isNotEmpty() }.getOrElse { false }
    }

    /** Resolution pages are read at: Tesseract's own recommendation for printed text. */
    private const val DPI = 300

    /** A page with this many letters or digits already has its text, so is left alone. */
    private const val HAS_TEXT = 20

    /**
     * Recognises the text on [input]'s pages in [languages] (pack codes, as in
     * [OcrPacks.installed]) and writes the result to [output]. Pages that
     * already have text are skipped unless [redoPagesWithText].
     */
    suspend fun recognise(
        input: File,
        output: File,
        packs: OcrPacks,
        languages: List<String>,
        password: String? = null,
        redoPagesWithText: Boolean = false,
        onProgress: Progress = {},
    ): OcrResult = withContext(Dispatchers.IO) {
        require(isAvailable) { S.ocrUnavailable }
        require(languages.isNotEmpty()) { S.ocrPickLanguage }
        val scratch = File(output.parentFile, "ocr-${System.nanoTime()}").apply { mkdirs() }
        val source = PdfConvert.decryptedCopyIfNeeded(input, password)
        val api = TessBaseAPI()
        try {
            if (api.Init(packs.dir.absolutePath, languages.joinToString("+")) != 0) error(S.ocrCouldNotStart)
            PdfOps.load(input, password).use { document ->
                if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
                val layers = LayerUtility(document)
                var recognised = 0
                PdfRasterizer.open(source).use { rasterizer ->
                    val total = document.numberOfPages
                    for (index in 0 until total) {
                        ensureActive()
                        val page = document.getPage(index)
                        if (redoPagesWithText || !hasText(document, index)) {
                            val text = readPage(api, rasterizer, index, scratch, page)
                            text?.let { layer ->
                                PdfOps.load(layer).use { overlay -> lay(document, page, layers, overlay) }
                                recognised++
                            }
                        }
                        onProgress((index + 1f) / total * 0.95f)
                    }
                    document.save(output)
                    onProgress(1f)
                    OcrResult(recognised = recognised, skipped = total - recognised, total = total)
                }
            }
        } finally {
            api.End()
            api.close()
            scratch.deleteRecursively()
            if (source != input) source.delete()
        }
    }

    private fun hasText(document: com.tom_roush.pdfbox.pdmodel.PDDocument, index: Int): Boolean {
        val stripper = PDFTextStripper().apply {
            startPage = index + 1
            endPage = index + 1
        }
        return runCatching { stripper.getText(document).count(Char::isLetterOrDigit) >= HAS_TEXT }.getOrDefault(false)
    }

    /** Tesseract's text-only PDF of one page, or null when it found no words. */
    private suspend fun readPage(
        api: TessBaseAPI,
        rasterizer: PdfRasterizer,
        index: Int,
        scratch: File,
        page: PDPage,
    ): File? {
        val picture = File(scratch, "page-$index.png")
        val image = rasterizer.renderAtDpi(index, DPI)
        // The renderer caps very large pages, so the resolution is what came out.
        val shown = rasterizer.pageSize(index)
        val dpi = (image.width / shown.width * 72f).roundToInt().coerceAtLeast(1)
        picture.outputStream().use { image.writeTo(it, ImageFormat.PNG) }
        image.release()

        val pix = pixRead(picture.absolutePath) ?: return null
        val base = File(scratch, "text-$index")
        try {
            pixSetResolution(pix, dpi, dpi)
            val renderer = TessPDFRenderer(base.absolutePath, api.GetDatapath().string, true)
            try {
                if (!renderer.BeginDocument("")) return null
                val ok = api.ProcessPage(pix, 0, picture.absolutePath, null as String?, 0, renderer)
                renderer.EndDocument()
                if (!ok) return null
            } finally {
                renderer.close()
            }
        } finally {
            pixDestroy(pix)
            picture.delete()
        }
        val text = api.GetUTF8Text()?.let { pointer -> pointer.string.also { pointer.deallocate() } }.orEmpty()
        if (text.none(Char::isLetterOrDigit)) return null
        return File(scratch, "text-$index.pdf").takeIf { it.isFile }
    }

    /**
     * Draws the text layer over [page]. The layer was made from the page as
     * displayed — turned by its /Rotate and cut to its crop box — so it is mapped
     * back into the page's own coordinates, and stretched to fit exactly.
     */
    private fun lay(
        document: com.tom_roush.pdfbox.pdmodel.PDDocument,
        page: PDPage,
        layers: LayerUtility,
        overlay: com.tom_roush.pdfbox.pdmodel.PDDocument,
    ) {
        val form = layers.importPageAsForm(overlay, 0)
        val box = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val turned = rotation == 90 || rotation == 270
        val shownWidth = if (turned) box.height else box.width
        val shownHeight = if (turned) box.width else box.height
        val sx = shownWidth / form.bBox.width
        val sy = shownHeight / form.bBox.height
        // Shown coordinates (x', y') to the page's own (u, v), from the crop box's corner.
        val (a, b, c, d, e, f) = when (rotation) {
            90 -> listOf(0f, 1f, -1f, 0f, box.width, 0f)
            180 -> listOf(-1f, 0f, 0f, -1f, box.width, box.height)
            270 -> listOf(0f, -1f, 1f, 0f, 0f, box.height)
            else -> listOf(1f, 0f, 0f, 1f, 0f, 0f)
        }
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            stream.transform(Matrix(a, b, c, d, e + box.lowerLeftX, f + box.lowerLeftY))
            stream.transform(Matrix(sx, 0f, 0f, sy, -form.bBox.lowerLeftX * sx, -form.bBox.lowerLeftY * sy))
            stream.drawForm(form)
            stream.restoreGraphicsState()
        }
    }

    private operator fun List<Float>.component6() = this[5]
}
