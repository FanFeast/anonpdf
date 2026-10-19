package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fanfeast.anonpdf.pdf.EditOp
import io.github.fanfeast.anonpdf.pdf.EditPlan
import io.github.fanfeast.anonpdf.pdf.EditPlanBuilder
import io.github.fanfeast.anonpdf.pdf.PageRanges

/** Which pages the next operation lands on. */
enum class ApplyScope(val label: String) {
    PAGE("This page"),
    SELECTION("Selected"),
    ALL("All pages"),
}

/**
 * The editor's whole mutable state: an ordered op list plus what the user has
 * selected.
 *
 * The op list is the source of truth and [plan] is derived from it, which is what
 * makes undo trivial — drop the last op and the plan recomputes. Nothing is
 * written to disk until the user saves.
 */
class EditorState(val pageCount: Int) {

    var ops by mutableStateOf<List<EditOp>>(emptyList())
        private set

    /** Ops that were undone, newest first, available to redo. */
    var undone by mutableStateOf<List<EditOp>>(emptyList())
        private set

    /** Position within the *output* document, i.e. an index into [EditPlan.kept]. */
    var position by mutableStateOf(0)
        private set

    /** Source page indices the user has ticked. */
    var selection by mutableStateOf<Set<Int>>(emptySet())
        private set

    var scope by mutableStateOf(ApplyScope.PAGE)

    private var markIds = 0L

    /** Ids only need to be unique within this editing session. */
    fun nextMarkId(): Long = ++markIds

    /**
     * Total pages across the original document and every inserted one — the top
     * of the global index space. Grows only; an undone insert keeps its indices
     * reserved so redo puts the same pages back.
     */
    var registeredPages = pageCount
        private set

    /** Allocates global indices for an inserted document; returns the first. */
    fun registerInsert(count: Int): Int {
        val start = registeredPages
        registeredPages += count
        return start
    }

    private val planState = derivedStateOf { EditPlanBuilder.build(pageCount, ops) }

    val plan: EditPlan get() = planState.value

    val canUndo: Boolean get() = ops.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** The source page currently on screen, or null if everything was deleted. */
    val currentSourceIndex: Int?
        get() = plan.kept.getOrNull(position)?.sourceIndex

    fun apply(op: EditOp) {
        ops = ops + op
        // A new edit invalidates the redo branch, as in any editor.
        undone = emptyList()
        clampPosition()
    }

    fun undo() {
        val last = ops.lastOrNull() ?: return
        ops = ops.dropLast(1)
        undone = listOf(last) + undone
        clampPosition()
    }

    fun redo() {
        val next = undone.firstOrNull() ?: return
        undone = undone.drop(1)
        ops = ops + next
        clampPosition()
    }

    fun reset() {
        ops = emptyList()
        undone = emptyList()
        selection = emptySet()
        position = 0
    }

    fun goTo(newPosition: Int) {
        position = newPosition.coerceIn(0, (plan.kept.size - 1).coerceAtLeast(0))
    }

    /**
     * Moves the preview onto a page the next edit will actually affect.
     *
     * Without this, opening Scale while looking at page 1 with pages 3 and 5
     * selected would show a preview that never changes as the slider moves — the
     * tool is working, just not on anything visible.
     */
    fun showFirstTarget() {
        val targets = targets()
        if (currentSourceIndex in targets) return
        val index = plan.kept.indexOfFirst { it.sourceIndex in targets }
        if (index >= 0) position = index
    }

    fun toggleSelection(sourceIndex: Int) {
        selection = if (sourceIndex in selection) {
            selection - sourceIndex
        } else {
            selection + sourceIndex
        }
        if (selection.isNotEmpty()) scope = ApplyScope.SELECTION
    }

    fun selectAll() {
        selection = plan.pages.map { it.sourceIndex }.toSet()
        scope = ApplyScope.SELECTION
    }

    fun clearSelection() {
        selection = emptySet()
        if (scope == ApplyScope.SELECTION) scope = ApplyScope.PAGE
    }

    /**
     * The source pages the next operation applies to.
     *
     * "Selected" quietly falls back to the current page when nothing is ticked,
     * so an operation never silently does nothing.
     */
    fun targets(): Set<Int> = when (scope) {
        ApplyScope.PAGE -> setOfNotNull(currentSourceIndex)
        ApplyScope.SELECTION -> selection.ifEmpty { setOfNotNull(currentSourceIndex) }
        // "All" means every page in the document as it now stands, inserted
        // ones included — not every page the original file arrived with.
        ApplyScope.ALL -> plan.pages.map { it.sourceIndex }.toSet()
    }

    /**
     * Human description of [targets], for button subtitles.
     *
     * Spoken in *current* positions, not source indices: once pages have been
     * inserted or reordered, "page 4" must mean the fourth page on screen.
     */
    fun targetLabel(): String {
        val targets = targets()
        val positions = targets.mapNotNull { target ->
            plan.pages.indexOfFirst { it.sourceIndex == target }.takeIf { it >= 0 }
        }.sorted()
        return when {
            positions.isEmpty() -> "no pages"
            positions.size == plan.pages.size -> "all ${positions.size} pages"
            positions.size == 1 -> "page ${positions.first() + 1}"
            else -> "pages " + PageRanges.describe(positions)
        }
    }

    /** Keeps the visible page in range after a delete or a reorder. */
    private fun clampPosition() {
        val lastIndex = (plan.kept.size - 1).coerceAtLeast(0)
        if (position > lastIndex) position = lastIndex
    }
}
