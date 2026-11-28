package io.github.fanfeast.anonpdf.pdf

import io.github.fanfeast.anonpdf.i18n.S

/**
 * Which pages a recipe step lands on, said in a way that still means something
 * on a document with a different number of pages.
 *
 * Positions count the pages as they stand when the step runs — after any earlier
 * step has deleted or moved some — which is what the user was looking at when
 * they made the edit.
 */
sealed interface PageChoice {
    data object All : PageChoice
    data object First : PageChoice
    data object Last : PageChoice

    /** Pages 1, 3, 5… */
    data object Odd : PageChoice

    /** Pages 2, 4, 6… */
    data object Even : PageChoice

    /** By position, from 0. Positions past the end of a shorter document are skipped. */
    data class Positions(val positions: List<Int>) : PageChoice

    /** The source pages this means, given the pages in order as they now stand. */
    fun resolve(order: List<Int>): Set<Int> = when (this) {
        All -> order.toSet()
        First -> setOfNotNull(order.firstOrNull())
        Last -> setOfNotNull(order.lastOrNull())
        Odd -> order.filterIndexed { i, _ -> i % 2 == 0 }.toSet()
        Even -> order.filterIndexed { i, _ -> i % 2 == 1 }.toSet()
        is Positions -> positions.mapNotNull { order.getOrNull(it) }.toSet()
    }

    fun describe(): String = when (this) {
        All -> S.recipeAllPages
        First -> S.recipeFirstPage
        Last -> S.recipeLastPage
        Odd -> S.recipeOddPages
        Even -> S.recipeEvenPages
        is Positions -> S.pagesScope(positions.size, PageRanges.describe(positions))
    }

    companion object {
        /**
         * The plainest choice that picks exactly [positions] out of [count] pages:
         * "all", "the first", "the last", "odd" or "even" when it is one of those,
         * since those are what carry over to a document of another length.
         */
        fun of(positions: Set<Int>, count: Int): PageChoice? {
            if (positions.isEmpty()) return null
            val sorted = positions.sorted()
            return when {
                positions.size == count -> All
                sorted == listOf(0) -> First
                sorted == listOf(count - 1) -> Last
                count >= 3 && sorted == (0 until count step 2).toList() -> Odd
                count >= 3 && sorted == (1 until count step 2).toList() -> Even
                else -> Positions(sorted)
            }
        }
    }
}

/**
 * One step of a recipe: an edit, and the pages it lands on.
 *
 * The page numbers inside [op] — its targets, a mark's page — are placeholders,
 * replaced when the step runs by whatever [pages] means on that document. Steps
 * with no [pages] act on the whole document.
 */
data class RecipeStep(val op: EditOp, val pages: PageChoice? = null) {

    /** Text boxes, cover-ups, highlights and drawings land where they were on the first file. */
    val isPlaced: Boolean get() = op is AddMark

    fun describe(): String {
        val scope = pages?.describe() ?: S.recipeAllPages
        return when (op) {
            is RotatePages -> op.describeOn(scope)
            is CropPages -> op.describeOn(scope)
            is ScalePages -> op.describeOn(scope)
            is ResizePages -> op.describeOn(scope)
            is DeletePages -> S.editDelete(scope)
            is MovePage -> S.recipeMove(scope, earlier = op.offset < 0)
            is AddRedaction -> S.editRedact(op.redaction.rule.describe(), scope)
            is AddMark -> S.recipePlaced(op.mark.kind(), scope)
            else -> op.describe()
        }
    }
}

private fun PageMark.kind(): String = when (this) {
    is TextMark -> S.quoted(text.lineSequence().firstOrNull()?.take(24).orEmpty())
    is FillMark -> if (isHighlight) S.recipeHighlight else S.recipeWhiteout
    is InkMark -> S.recipeDrawing
}

/** A named list of steps, recorded from one edit and replayed on other documents. */
data class Recipe(val name: String, val steps: List<RecipeStep>) {
    val hasPlacedContent: Boolean get() = steps.any { it.isPlaced }
}

/** What recording an edit kept, and how many of its edits only made sense on that one file. */
data class Recorded(val steps: List<RecipeStep>, val skipped: Int)

object Recipes {

    /**
     * Turns an edit history into steps that mean the same on another document.
     *
     * Pages are recorded as positions at the moment each edit was made, then
     * simplified to "all", "first", "last", "odd" or "even" where that is what
     * they were. Settings that replace each other (the watermark, page numbers,
     * header and footer, markups, grey) keep only their final value; deletes
     * that were later restored are dropped; each mark keeps its final shape.
     *
     * Inserting another file's pages, straightening (measured page by page) and
     * bookmarks (which name pages of this document) are left out and counted in
     * [Recorded.skipped].
     */
    fun record(pageCount: Int, ops: List<EditOp>): Recorded {
        // A restore undoes part of an earlier delete; fold it in so neither is recorded.
        val folded = ops.toMutableList()
        ops.forEachIndexed { i, op ->
            if (op is RestorePages) {
                for (j in 0 until i) {
                    val earlier = folded[j]
                    if (earlier is DeletePages) folded[j] = DeletePages(earlier.targets - op.targets)
                }
            }
        }

        val finalPlan = EditPlanBuilder.build(pageCount, ops)
        val finalMarks = finalPlan.marks.associateBy { it.id }
        val lastTouch = HashMap<Long, Int>()
        val lastOfKind = HashMap<Class<*>, Int>()
        var lastClear = -1
        ops.forEachIndexed { i, op ->
            when (op) {
                is AddMark -> lastTouch[op.mark.id] = i
                is UpdateMark -> lastTouch[op.mark.id] = i
                is SetWatermark, is SetPageNumbers, is SetHeaderFooter, is SetMarkups, is SetGreyscale, is FillForm ->
                    lastOfKind[op.javaClass] = i
                ClearRedactions -> lastClear = i
                else -> Unit
            }
        }
        val formValues = ops.filterIsInstance<FillForm>().fold(emptyMap<String, String>()) { acc, op -> acc + op.values }

        val steps = mutableListOf<RecipeStep>()
        var skipped = 0
        var plan = EditPlanBuilder.identity(pageCount)
        folded.forEachIndexed { i, op ->
            val order = plan.kept.map { it.sourceIndex }
            fun choice(targets: Set<Int>): PageChoice? =
                PageChoice.of(order.withIndex().filter { it.value in targets }.map { it.index }.toSet(), order.size)
            fun targeted(targets: Set<Int>) { choice(targets)?.let { steps += RecipeStep(op, it) } }

            when (op) {
                is RotatePages -> if (op.degrees % 360 != 0) targeted(op.targets)
                is CropPages -> targeted(op.targets)
                is ScalePages -> targeted(op.targets)
                is ResizePages -> targeted(op.targets)
                is DeletePages -> targeted(op.targets)
                is MovePage -> targeted(setOf(op.sourceIndex))
                ReversePages -> steps += RecipeStep(op)
                is AddRedaction -> if (i > lastClear) targeted(op.redaction.targets)
                is AddMark, is UpdateMark -> {
                    val id = if (op is AddMark) op.mark.id else (op as UpdateMark).mark.id
                    val mark = finalMarks[id]
                    if (lastTouch[id] == i && mark != null) {
                        choice(setOf(mark.sourceIndex))?.let { steps += RecipeStep(AddMark(mark), it) }
                    }
                }
                is FillForm -> if (lastOfKind[op.javaClass] == i) {
                    steps += RecipeStep(FillForm(formValues, op.flatten))
                }
                is SetWatermark, is SetPageNumbers, is SetHeaderFooter, is SetMarkups, is SetGreyscale ->
                    if (lastOfKind[op.javaClass] == i) steps += RecipeStep(op)
                is InsertPages, is TiltPages, is SetBookmarks -> skipped++
                // Folded into the deletes above; removed marks and cleared
                // redactions simply never appear.
                is RestorePages, is RemoveMark, ClearRedactions -> Unit
            }
            plan = EditPlanBuilder.build(pageCount, ops.subList(0, i + 1))
        }
        return Recorded(steps, skipped)
    }

    /**
     * The edits that running [recipe] makes on a document of [pageCount] pages
     * that already has [existing] edits. Each step's pages are worked out on the
     * document as the steps before it left it; a step that finds no pages there
     * (page 7 of a 3-page file) is left out.
     */
    fun replay(
        recipe: Recipe,
        pageCount: Int,
        existing: List<EditOp> = emptyList(),
        newMarkId: () -> Long,
    ): List<EditOp> {
        val made = mutableListOf<EditOp>()
        for (step in recipe.steps) {
            val plan = EditPlanBuilder.build(pageCount, existing + made)
            val order = plan.kept.map { it.sourceIndex }
            val targets = step.pages?.resolve(order)
            if (targets != null && targets.isEmpty()) continue
            made += when (val op = step.op) {
                is RotatePages -> op.copy(targets = targets!!)
                is CropPages -> op.copy(targets = targets!!)
                is ScalePages -> op.copy(targets = targets!!)
                is ResizePages -> op.copy(targets = targets!!)
                is DeletePages -> op.copy(targets = targets!!)
                is MovePage -> op.copy(sourceIndex = targets!!.first())
                is AddRedaction -> op.copy(redaction = op.redaction.copy(targets = targets!!))
                is AddMark -> {
                    val page = targets?.first() ?: order.firstOrNull() ?: continue
                    AddMark(op.mark.moved(newMarkId(), page))
                }
                else -> op
            }
        }
        return made
    }

    /** Edits a document from scratch with [recipe]: what a batch run saves for each file. */
    fun plan(recipe: Recipe, pageCount: Int): EditPlan {
        var id = 0L
        return EditPlanBuilder.build(pageCount, replay(recipe, pageCount) { ++id })
    }

    // ------------------------------------------------------------------- file

    private const val FORMAT = "anonpdf-recipe"
    const val EXTENSION = "recipe.json"

    fun encode(recipe: Recipe): String = Json.write(
        linkedMapOf(
            FORMAT to 1,
            "name" to recipe.name,
            "steps" to recipe.steps.map { RecipeCodec.encode(it) },
        ),
    )

    /** Throws [IllegalArgumentException] with a message for the user when [text] is not a recipe. */
    fun decode(text: String): Recipe {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>
        require(root != null && root[FORMAT] != null) { S.notARecipe }
        require((root[FORMAT] as? Double ?: 0.0) <= 1.0) { S.recipeFromNewerVersion }
        val name = (root["name"] as? String)?.takeIf { it.isNotBlank() } ?: S.recipeUntitled
        val steps = (root["steps"] as? List<*>).orEmpty().mapNotNull { step ->
            runCatching { RecipeCodec.decode(step as Map<*, *>) }.getOrNull()
        }
        return Recipe(name.take(80), steps)
    }
}

private fun PageMark.moved(id: Long, page: Int): PageMark = when (this) {
    is TextMark -> copy(id = id, sourceIndex = page)
    is FillMark -> copy(id = id, sourceIndex = page)
    is InkMark -> copy(id = id, sourceIndex = page)
}
