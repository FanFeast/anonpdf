package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
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

data class ResizePages(val target: ResizeTarget, val targets: Set<Int>) : EditOp {
    override fun describe(): String {
        val how = if (target.fill) "fill" else "fit"
        val orientation = when (target.orientation) {
            PageOrientation.AUTO -> ""
            PageOrientation.PORTRAIT -> " portrait"
            PageOrientation.LANDSCAPE -> " landscape"
        }
        return "Resize to ${target.paper.label}$orientation ($how) · ${scopeOf(targets)}"
    }
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

/**
 * Splices another document's pages into the plan, so they can be reordered,
 * rotated, marked and deleted like any other page before anything is written.
 *
 * [startIndex] is the first *global* index of the inserted document's pages.
 * The editor session owns the global index space — the original document holds
 * 0 until its page count, and each inserted document is allocated the next run
 * of indices in the order it arrived. [position] is where the run lands in the
 * page list as it stands when the op is applied.
 */
data class InsertPages(
    val startIndex: Int,
    val count: Int,
    val position: Int,
    val label: String,
) : EditOp {
    override fun describe() =
        "Insert \"$label\" ($count page${if (count == 1) "" else "s"})"
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

/** Adds content on top of a page: text, a white-out block, a highlight, ink. */
data class AddMark(val mark: PageMark) : EditOp {
    override fun describe() = "Add ${mark.describe()}"
}

/** Replaces a mark with the same id — moving it, retyping it, restyling it. */
data class UpdateMark(val mark: PageMark) : EditOp {
    override fun describe() = "Edit ${mark.describe()}"
}

data class RemoveMark(val id: Long) : EditOp {
    override fun describe() = "Remove added content"
}

/** What one output page looks like: where it came from and what was done to it. */
data class PageState(
    val sourceIndex: Int,
    val rotationDelta: Int = 0,
    val crop: CropInsets = CropInsets(),
    val scale: Float = 1f,
    /** Refit onto a standard sheet. Mutually exclusive with [scale]. */
    val resize: ResizeTarget? = null,
    val deleted: Boolean = false,
) {
    val isUntouched: Boolean
        get() = rotationDelta == 0 && crop.isEmpty && scale == 1f &&
            resize == null && !deleted
}

/** The normalised result of folding an op list. Drives both preview and export. */
data class EditPlan(
    val pages: List<PageState>,
    val watermark: WatermarkOptions? = null,
    val pageNumbers: PageNumberOptions? = null,
    /** Content added on top of pages, in the order it was added. */
    val marks: List<PageMark> = emptyList(),
    /**
     * How many pages the plan started with. Defaults to the page count at
     * construction, and survives every [copy], which is what lets [hasChanges]
     * notice that an insert-at-the-end changed the document even though every
     * page still sits at its own index, untouched.
     */
    val basePageCount: Int = pages.size,
) {
    val kept: List<PageState> get() = pages.filterNot { it.deleted }

    fun marksFor(sourceIndex: Int) = marks.filter { it.sourceIndex == sourceIndex }

    val hasChanges: Boolean
        get() = watermark != null || pageNumbers != null || marks.isNotEmpty() ||
            pages.size != basePageCount ||
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

        // Scaling by a factor and refitting onto a sheet are two answers to the
        // same question, so each clears the other rather than stacking.
        is ScalePages -> plan.mapTargets(op.targets) {
            it.copy(scale = op.factor, resize = null)
        }
        is ResizePages -> plan.mapTargets(op.targets) {
            it.copy(resize = op.target, scale = 1f)
        }

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

        is InsertPages -> plan.copy(
            pages = plan.pages.toMutableList().also { list ->
                list.addAll(
                    op.position.coerceIn(0, list.size),
                    (0 until op.count).map { PageState(op.startIndex + it) },
                )
            },
        )

        ReversePages -> plan.copy(pages = plan.pages.reversed())

        is SetWatermark -> plan.copy(watermark = op.options)
        is SetPageNumbers -> plan.copy(pageNumbers = op.options)

        is AddMark -> plan.copy(marks = plan.marks + op.mark)
        is UpdateMark -> plan.copy(
            marks = plan.marks.map { if (it.id == op.mark.id) op.mark else it },
        )
        is RemoveMark -> plan.copy(marks = plan.marks.filterNot { it.id == op.id })
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

    /**
     * One document contributing pages to a plan.
     *
     * [pageCount] is only consulted to work out which source owns a global page
     * index; the export path never needs it because the combined document's page
     * order *is* the global order.
     */
    data class PlanSource(
        val file: File,
        val password: String? = null,
        val pageCount: Int = 0,
    )

    /** Single-document convenience: every index belongs to the one source. */
    suspend fun applyPlan(
        input: File,
        output: File,
        plan: EditPlan,
        password: String? = null,
        onProgress: Progress = {},
    ) = applyPlan(
        sources = listOf(PlanSource(input, password, Int.MAX_VALUE)),
        output = output,
        plan = plan,
        onProgress = onProgress,
    )

    suspend fun applyPlan(
        sources: List<PlanSource>,
        output: File,
        plan: EditPlan,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        val kept = plan.kept
        require(kept.isNotEmpty()) { "Keep at least one page." }
        require(sources.isNotEmpty()) { "No document to work on." }

        val open = mutableListOf<PDDocument>()
        try {
            val document = PdfOps.load(sources[0].file, sources[0].password)
                .also { open += it }

            // Append every inserted document to the end, in the order they were
            // registered. The combined page list then lines up exactly with the
            // global index space the plan speaks in — page k of source n sits at
            // (pages before n) + k — so no other mapping is needed. Sources are
            // never skipped, even ones an undo left unreferenced: skipping would
            // shift every index after them.
            val merger = PDFMergerUtility()
            sources.drop(1).forEachIndexed { index, source ->
                val extra = PdfOps.load(source.file, source.password).also { open += it }
                merger.appendDocument(document, extra)
                onProgress(0.25f * (index + 1f) / (sources.size - 1f))
            }

            val originals = (0 until document.numberOfPages).map { document.getPage(it) }
            originals.forEach { PdfOps.pinInheritedAttributes(it) }
            // Detach everything, then re-attach only what the plan keeps, in order.
            originals.forEach { document.pages.remove(it) }

            kept.forEachIndexed { position, state ->
                val page = originals.getOrNull(state.sourceIndex)
                    ?: error("Page ${state.sourceIndex + 1} is not in this document.")
                applyPageState(document, page, state)
                // Marks go on after the page geometry is settled, so an opaque
                // white-out lands exactly where the user drew it.
                PdfDraw.drawMarks(document, page, plan.marksFor(state.sourceIndex))
                document.addPage(page)
                onProgress(0.25f + 0.5f * (position + 1f) / kept.size)
            }

            stampWatermark(document, plan)
            onProgress(0.85f)
            stampPageNumbers(document, plan, firstOutputPosition = 0)

            with(PdfOps) { document.prepareForSave() }
            document.save(output)
            onProgress(1f)
        } finally {
            open.forEach { runCatching { it.close() } }
        }
    }

    /** Single-document convenience: every index belongs to the one source. */
    suspend fun renderPreview(
        input: File,
        plan: EditPlan,
        outputPosition: Int,
        targetWidthPx: Int,
        workDir: File,
        password: String? = null,
    ): Bitmap? = renderPreview(
        sources = listOf(PlanSource(input, password, Int.MAX_VALUE)),
        plan = plan,
        outputPosition = outputPosition,
        targetWidthPx = targetWidthPx,
        workDir = workDir,
    )

    /**
     * Renders exactly one output page, as the export would produce it.
     *
     * Built as a single-page document so preview cost does not grow with the size
     * of the original — a 400-page file previews as fast as a 4-page one. Only
     * the source that owns the page is opened, so inserting a large document
     * does not slow down previews of the others' pages either.
     */
    suspend fun renderPreview(
        sources: List<PlanSource>,
        plan: EditPlan,
        outputPosition: Int,
        targetWidthPx: Int,
        workDir: File,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val kept = plan.kept
        val state = kept.getOrNull(outputPosition) ?: return@withContext null

        // Walk the sources' index runs to find the one this page belongs to.
        var owner: PlanSource? = null
        var localIndex = state.sourceIndex
        var firstIndex = 0
        for (source in sources) {
            if (state.sourceIndex < firstIndex + source.pageCount) {
                owner = source
                localIndex = state.sourceIndex - firstIndex
                break
            }
            firstIndex += source.pageCount
        }
        val resolved = owner ?: return@withContext null

        val temp = File(workDir, "preview-${System.nanoTime()}.pdf")
        try {
            PdfOps.load(resolved.file, resolved.password).use { document ->
                val originals = (0 until document.numberOfPages).map { document.getPage(it) }
                val page = originals.getOrNull(localIndex)
                    ?: return@withContext null
                originals.forEach { PdfOps.pinInheritedAttributes(it) }
                originals.forEach { document.pages.remove(it) }

                applyPageState(document, page, state)
                PdfDraw.drawMarks(document, page, plan.marksFor(state.sourceIndex))
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
        // Order matters, and this is the only order that is right.
        //
        // Rotation first: both of the steps after it read the page's final /Rotate
        // to work out which edge is which. Cropping second, so that resizing fits
        // what the reader can actually see onto the sheet rather than the full
        // uncropped page. Sizing last.
        page.rotation = PdfOps.normalizeRotation(page.rotation + state.rotationDelta)
        PdfDraw.cropPage(page, state.crop)
        val resize = state.resize
        if (resize != null) {
            PdfDraw.resizePage(document, page, resize)
        } else {
            PdfDraw.scalePage(document, page, state.scale)
        }
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
