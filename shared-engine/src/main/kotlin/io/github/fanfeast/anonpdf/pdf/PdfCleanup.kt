package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** What happens to a document's comments, highlights, stamps and other markups. */
enum class MarkupMode {
    KEEP,

    /** Drawn into the page, where they can no longer be moved, edited or deleted. */
    FLATTEN,
    REMOVE,
    ;

    val label: String
        get() = when (this) {
            KEEP -> S.markupKeep
            FLATTEN -> S.markupFlatten
            REMOVE -> S.markupRemove
        }
}

/**
 * Whole-document tidying: what to do with markups, and finding blank pages.
 *
 * A markup is any annotation other than a link or a form field: a sticky note, a
 * highlight, a stamp, a drawn shape. Links are left alone because they are part
 * of how the document reads, and form fields have their own tool.
 */
object PdfCleanup {

    /** How many markups the document's pages carry. */
    suspend fun countMarkups(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { document ->
            document.pages.sumOf { page -> runCatching { page.annotations.count(::isMarkup) }.getOrDefault(0) }
        }
    }

    /**
     * Applies [mode] to every page. Done before pages are rotated, cropped or
     * resized: a flattened markup is then ordinary page content, and moves with
     * the rest of it.
     */
    internal fun applyMarkups(document: PDDocument, mode: MarkupMode) {
        if (mode == MarkupMode.KEEP) return
        // A flattened form is drawn into the page too, so the page as a whole is fixed.
        if (mode == MarkupMode.FLATTEN) PdfForms.flatten(document)
        for (page in document.pages) {
            val annotations = runCatching { page.annotations }.getOrNull() ?: continue
            if (annotations.none(::isMarkup)) continue
            if (mode == MarkupMode.FLATTEN) drawIntoPage(document, page, annotations.filter(::isMarkup))
            page.annotations = annotations.filterNot(::isMarkup)
        }
    }

    private fun isMarkup(annotation: PDAnnotation) =
        annotation !is PDAnnotationLink && annotation !is PDAnnotationWidget

    /**
     * Draws each annotation's appearance where a viewer shows it: its box fitted
     * into the annotation's rectangle, as the PDF specification lays down. One
     * that has no appearance, or is hidden, has nothing to draw. Pop-up windows
     * are what a viewer opens on a click, so they are never part of the page.
     */
    private fun drawIntoPage(document: PDDocument, page: PDPage, markups: List<PDAnnotation>) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            for (annotation in markups) {
                if (annotation.isHidden || annotation.isNoView || annotation.subtype == "Popup") continue
                val appearance = runCatching { annotation.normalAppearanceStream }.getOrNull() ?: continue
                val rect = annotation.rectangle ?: continue
                val box = appearance.bBox ?: continue
                if (rect.width <= 0f || rect.height <= 0f) continue

                // The appearance's box as its own matrix places it.
                val matrix = appearance.matrix
                val xs = FloatArray(4)
                val ys = FloatArray(4)
                listOf(
                    box.lowerLeftX to box.lowerLeftY,
                    box.upperRightX to box.lowerLeftY,
                    box.lowerLeftX to box.upperRightY,
                    box.upperRightX to box.upperRightY,
                ).forEachIndexed { index, (x, y) ->
                    xs[index] = matrix.getValue(0, 0) * x + matrix.getValue(1, 0) * y + matrix.getValue(2, 0)
                    ys[index] = matrix.getValue(0, 1) * x + matrix.getValue(1, 1) * y + matrix.getValue(2, 1)
                }
                val width = xs.max() - xs.min()
                val height = ys.max() - ys.min()
                if (width <= 0f || height <= 0f) continue

                stream.saveGraphicsState()
                stream.transform(Matrix.getTranslateInstance(rect.lowerLeftX, rect.lowerLeftY))
                stream.transform(Matrix.getScaleInstance(rect.width / width, rect.height / height))
                stream.transform(Matrix.getTranslateInstance(-xs.min(), -ys.min()))
                // Drawing the form applies its own matrix, completing the fit.
                stream.drawForm(appearance)
                stream.restoreGraphicsState()
            }
        }
    }

    /**
     * The pages that are blank: no text on them, nothing the user added, and next
     * to no ink once rendered, so a scanned blank page with a speck of dust still
     * counts but a page with one short line does not. Pages are taken as the plan
     * has them and returned as source indices.
     */
    suspend fun findBlankPages(
        sources: List<PdfEditor.PlanSource>,
        plan: EditPlan,
        workDir: File,
        onProgress: Progress = {},
    ): List<Int> = withContext(Dispatchers.IO) {
        // The pages as they arrived: a watermark or page number stamped on every
        // page should not hide a blank one.
        val bare = plan.copy(watermark = null, pageNumbers = null, headerFooter = null)
        val kept = bare.kept
        val withText = pagesWithText(sources, kept.map { it.sourceIndex }.toSet())
        val blank = mutableListOf<Int>()
        kept.forEachIndexed { position, state ->
            ensureActive()
            val candidate = state.sourceIndex !in withText && bare.marksFor(state.sourceIndex).isEmpty()
            if (candidate) {
                val image = try {
                    PdfEditor.renderPreview(sources, bare, position, BLANK_SCAN_WIDTH, workDir)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    null
                }
                if (image != null) {
                    if (isBlank(image)) blank += state.sourceIndex
                    image.release()
                }
            }
            onProgress((position + 1f) / kept.size)
        }
        blank
    }

    /** Which of [indices], global across [sources], have any text on them. */
    private fun pagesWithText(sources: List<PdfEditor.PlanSource>, indices: Set<Int>): Set<Int> {
        val found = HashSet<Int>()
        var first = 0
        for (source in sources) {
            val owned = indices.filter { it >= first && it < first + source.pageCount }
            if (owned.isNotEmpty()) {
                runCatching {
                    PdfOps.load(source.file, source.password).use { document ->
                        for (global in owned) {
                            val text = PDFTextStripper().apply {
                                startPage = global - first + 1
                                endPage = global - first + 1
                            }.getText(document)
                            if (text.isNotBlank()) found += global
                        }
                    }
                }
            }
            first += source.pageCount
        }
        return found
    }

    internal fun isBlank(image: PageImage): Boolean {
        var ink = 0
        var total = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                total++
                val pixel = image.pixelAt(x, y)
                // A renderer that leaves the page transparent has drawn nothing there.
                if ((pixel ushr 24) < 128) continue
                val darkest = minOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
                if (darkest < INK_LEVEL) ink++
            }
        }
        return total > 0 && ink.toFloat() / total < BLANK_INK_FRACTION
    }

    /** Wide enough to see a line of text, narrow enough to scan hundreds of pages quickly. */
    private const val BLANK_SCAN_WIDTH = 200

    /** Paper, even a scanner's slightly grey paper, is lighter than this. */
    private const val INK_LEVEL = 200

    /** A line of small print covers more than this; dust and a stray mark less. */
    private const val BLANK_INK_FRACTION = 0.002f
}
