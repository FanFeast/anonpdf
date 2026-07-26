package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One entry in the user-visible edit history.
 *
 * Operations are kept as an ordered list rather than being applied straight to
 * the document, which is what makes undo, redo and a readable "pending edits"
 * list possible. The list is folded into an [EditPlan] — the normalised state —
 * and both preview and export are driven from that plan, so what you see is
 * produced by the same code as what you save.
 */
sealed interface EditOp {
    /** One line for the pending-edits list. */
    fun describe(): String
}

private fun scopeOf(targets: Set<Int>): String =
    if (targets.isEmpty()) "no pages" else "page${if (targets.size == 1) "" else "s"} " +
        PageRanges.describe(targets.toList())

data class RotatePages(val degrees: Int, val targets: Set<Int>) : EditOp {
    override fun describe() = "Rotate ${degrees}° · ${scopeOf(targets)}"
}

data class CropPages(val insets: CropInsets, val targets: Set<Int>) : EditOp {
    override fun describe(): String {
        val even = insets.left == insets.right && insets.top == insets.bottom &&
            insets.left == insets.top
        val amount = if (even) {
            "${(insets.left * 100).toInt()}%"
        } else {
            "L${(insets.left * 100).toInt()} T${(insets.top * 100).toInt()} " +
                "R${(insets.right * 100).toInt()} B${(insets.bottom * 100).toInt()}"
        }
        return "Crop $amount · ${scopeOf(targets)}"
    }
}

data class ScalePages(val factor: Float, val targets: Set<Int>) : EditOp {
    override fun describe() = "Scale ${(factor * 100).toInt()}% · ${scopeOf(targets)}"
}

data class DeletePages(val targets: Set<Int>) : EditOp {
    override fun describe() = "Delete · ${scopeOf(targets)}"
}

data class RestorePages(val targets: Set<Int>) : EditOp {
    override fun describe() = "Restore · ${scopeOf(targets)}"
}

data class MovePage(val sourceIndex: Int, val offset: Int) : EditOp {
    override fun describe() =
        "Move page ${sourceIndex + 1} ${if (offset < 0) "earlier" else "later"}"
}

data object ReversePages : EditOp {
    override fun describe() = "Reverse page order"
}

data class SetWatermark(val options: WatermarkOptions?) : EditOp {
    override fun describe() =
        options?.let { "Watermark \"${it.text}\"" } ?: "Remove watermark"
}

data class SetPageNumbers(val options: PageNumberOptions?) : EditOp {
    override fun describe() =
        options?.let { "Page numbers \"${it.format}\"" } ?: "Remove page numbers"
}

/** What one output page looks like: where it came from and what was done to it. */
data class PageState(
    val sourceIndex: Int,
    val rotationDelta: Int = 0,
    val crop: CropInsets = CropInsets(),
    val scale: Float = 1f,
    val deleted: Boolean = false,
) {
    val isUntouched: Boolean
        get() = rotationDelta == 0 && crop.isEmpty && scale == 1f && !deleted
}

/** The normalised result of folding an op list. Drives both preview and export. */
data class EditPlan(
    val pages: List<PageState>,
    val watermark: WatermarkOptions? = null,
    val pageNumbers: PageNumberOptions? = null,
) {
    val kept: List<PageState> get() = pages.filterNot { it.deleted }

    val hasChanges: Boolean
        get() = watermark != null || pageNumbers != null ||
            pages.withIndex().any { (position, state) ->
                state.sourceIndex != position || !state.isUntouched
            }
}

object EditPlanBuilder {

    fun identity(pageCount: Int) = EditPlan((0 until pageCount).map { PageState(it) })

    fun build(pageCount: Int, ops: List<EditOp>): EditPlan =
        ops.fold(identity(pageCount)) { plan, op -> apply(plan, op) }

    private fun apply(plan: EditPlan, op: EditOp): EditPlan = when (op) {
        // Rotation accumulates: turning twice by 90 should end up at 180.
        is RotatePages -> plan.mapTargets(op.targets) {
            it.copy(rotationDelta = it.rotationDelta + op.degrees)
        }

        // Crop and scale replace, because the controls that produce them report an
        // absolute value rather than a delta.
        is CropPages -> plan.mapTargets(op.targets) { it.copy(crop = op.insets) }
        is ScalePages -> plan.mapTargets(op.targets) { it.copy(scale = op.factor) }

        is DeletePages -> plan.mapTargets(op.targets) { it.copy(deleted = true) }
        is RestorePages -> plan.mapTargets(op.targets) { it.copy(deleted = false) }

        is MovePage -> {
            val current = plan.pages.indexOfFirst { it.sourceIndex == op.sourceIndex }
            if (current < 0) {
                plan
            } else {
                val target = (current + op.offset).coerceIn(0, plan.pages.lastIndex)
                plan.copy(
                    pages = plan.pages.toMutableList().also { list ->
                        list.add(target, list.removeAt(current))
                    },
                )
            }
        }

        ReversePages -> plan.copy(pages = plan.pages.reversed())

        is SetWatermark -> plan.copy(watermark = op.options)
        is SetPageNumbers -> plan.copy(pageNumbers = op.options)
    }

    private fun EditPlan.mapTargets(
        targets: Set<Int>,
        transform: (PageState) -> PageState,
    ) = copy(
        pages = pages.map { if (it.sourceIndex in targets) transform(it) else it },
    )
}

/**
 * Applies an [EditPlan] to a document.
 *
 * Order matters and is fixed here: pages are placed in their final order first,
 * then each page is scaled, cropped and rotated, then the watermark goes on, and
 * page numbers go on last so they reflect the order and count the reader will
 * actually see.
 */
object PdfEditor {

    suspend fun applyPlan(
        input: File,
        output: File,
        plan: EditPlan,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        val kept = plan.kept
        require(kept.isNotEmpty()) { "Keep at least one page." }

        PdfOps.load(input, password).use { document ->
            val originals = (0 until document.numberOfPages).map { document.getPage(it) }
            originals.forEach { PdfOps.pinInheritedAttributes(it) }
            // Detach everything, then re-attach only what the plan keeps, in order.
            originals.forEach { document.pages.remove(it) }

            kept.forEachIndexed { position, state ->
                val page = originals.getOrNull(state.sourceIndex)
                    ?: error("Page ${state.sourceIndex + 1} is not in this document.")
                applyPageState(document, page, state)
                document.addPage(page)
                onProgress(0.6f * (position + 1f) / kept.size)
            }

            stampWatermark(document, plan)
            onProgress(0.85f)
            stampPageNumbers(document, plan, firstOutputPosition = 0)

            with(PdfOps) { document.prepareForSave() }
            document.save(output)
            onProgress(1f)
        }
    }

    /**
     * Renders exactly one output page, as the export would produce it.
     *
     * Built as a single-page document so preview cost does not grow with the size
     * of the original — a 400-page file previews as fast as a 4-page one.
     */
    suspend fun renderPreview(
        input: File,
        plan: EditPlan,
        outputPosition: Int,
        targetWidthPx: Int,
        workDir: File,
        password: String? = null,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val kept = plan.kept
        val state = kept.getOrNull(outputPosition) ?: return@withContext null

        val temp = File(workDir, "preview-${System.nanoTime()}.pdf")
        try {
            PdfOps.load(input, password).use { document ->
                val originals = (0 until document.numberOfPages).map { document.getPage(it) }
                val page = originals.getOrNull(state.sourceIndex)
                    ?: return@withContext null
                originals.forEach { PdfOps.pinInheritedAttributes(it) }
                originals.forEach { document.pages.remove(it) }

                applyPageState(document, page, state)
                document.addPage(page)

                stampWatermark(document, plan)
                // Tell the stamper where this page sits in the finished document so
                // the number shown is the number that will be printed.
                stampPageNumbers(
                    document = document,
                    plan = plan,
                    firstOutputPosition = outputPosition,
                    totalOutputPages = kept.size,
                )

                with(PdfOps) { document.prepareForSave() }
                document.save(temp)
            }
            PdfRasterizer.open(temp).use { it.renderByWidth(0, targetWidthPx) }
        } catch (t: Throwable) {
            null
        } finally {
            temp.delete()
        }
    }

    private fun applyPageState(
        document: com.tom_roush.pdfbox.pdmodel.PDDocument,
        page: com.tom_roush.pdfbox.pdmodel.PDPage,
        state: PageState,
    ) {
        // Scale first: it resizes the boxes, and the crop is a fraction of them.
        PdfDraw.scalePage(document, page, state.scale)
        PdfDraw.cropPage(page, state.crop)
        page.rotation = PdfOps.normalizeRotation(page.rotation + state.rotationDelta)
    }

    private fun stampWatermark(
        document: com.tom_roush.pdfbox.pdmodel.PDDocument,
        plan: EditPlan,
    ) {
        val options = plan.watermark ?: return
        val font = PdfDraw.watermarkFont(options)
        val text = PdfDraw.sanitizeForFont(options.text, font)
        if (text.isBlank()) return
        for (index in 0 until document.numberOfPages) {
            PdfDraw.watermarkPage(document, document.getPage(index), options, text, font)
        }
    }

    /**
     * @param firstOutputPosition the position, in the finished document, of this
     * document's first page. Zero for a real export; the previewed page's index
     * when rendering a one-page preview.
     * @param totalOutputPages the finished document's page count, which a preview
     * cannot infer from the single page it holds.
     */
    private fun stampPageNumbers(
        document: com.tom_roush.pdfbox.pdmodel.PDDocument,
        plan: EditPlan,
        firstOutputPosition: Int,
        totalOutputPages: Int = document.numberOfPages,
    ) {
        val options = plan.pageNumbers ?: return
        val font = PdfDraw.labelFont
        for (index in 0 until document.numberOfPages) {
            val outputPosition = firstOutputPosition + index
            if (outputPosition < options.firstPageIndex) continue
            val label = PdfDraw.sanitizeForFont(
                PdfOps.pageNumberLabel(
                    options = options,
                    positionAmongNumbered = outputPosition - options.firstPageIndex,
                    totalPages = totalOutputPages,
                ),
                font,
            )
            PdfDraw.labelPage(document, document.getPage(index), label, options, font)
        }
    }
}
