package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecipeTest {

    private fun replay(recipe: Recipe, pageCount: Int): EditPlan = Recipes.plan(recipe, pageCount)

    private fun recipeOf(pageCount: Int, vararg ops: EditOp) =
        Recipe("test", Recipes.record(pageCount, ops.toList()).steps)

    @Test
    fun `whole-document choices are recognised and carry over to another length`() {
        val recipe = recipeOf(
            4,
            RotatePages(90, setOf(0, 1, 2, 3)),
            DeletePages(setOf(0)),
        )
        assertEquals(listOf(PageChoice.All, PageChoice.First), recipe.steps.map { it.pages })

        val plan = replay(recipe, 7)
        assertEquals((1..6).toList(), plan.kept.map { it.sourceIndex })
        assertTrue(plan.pages.all { it.rotationDelta == 90 })
    }

    @Test
    fun `positions are counted as the document stood when the edit was made`() {
        // After deleting page 1, what was page 3 is the second page on screen.
        val recipe = recipeOf(5, DeletePages(setOf(0)), ScalePages(0.5f, setOf(2)))
        assertEquals(PageChoice.Positions(listOf(1)), recipe.steps[1].pages)

        val plan = replay(recipe, 5)
        assertEquals(0.5f, plan.pages.first { it.sourceIndex == 2 }.scale)
    }

    @Test
    fun `last, odd and even pages`() {
        val recipe = recipeOf(
            6,
            DeletePages(setOf(5)),
            RotatePages(180, setOf(0, 2, 4)),
            RotatePages(90, setOf(1, 3)),
        )
        // Five pages remain after the delete; 1, 3 and 5 are odd, 2 and 4 even.
        assertEquals(listOf(PageChoice.Last, PageChoice.Odd, PageChoice.Even), recipe.steps.map { it.pages })

        val plan = replay(recipe, 4)
        assertEquals(listOf(0, 1, 2), plan.kept.map { it.sourceIndex })
        assertEquals(listOf(180, 90, 180), plan.kept.map { it.rotationDelta })
    }

    @Test
    fun `a page the other file doesn't have is skipped, not an error`() {
        val recipe = recipeOf(10, CropPages(CropInsets(0.1f, 0.1f, 0.1f, 0.1f), setOf(7)))
        val plan = replay(recipe, 3)
        assertFalse(plan.hasChanges)
    }

    @Test
    fun `restored pages are not deleted and replaced settings keep their last value`() {
        val recipe = recipeOf(
            4,
            DeletePages(setOf(1, 2)),
            RestorePages(setOf(2)),
            SetWatermark(WatermarkOptions("DRAFT")),
            SetWatermark(WatermarkOptions("FINAL")),
        )
        assertEquals(2, recipe.steps.size)
        assertEquals(PageChoice.Positions(listOf(1)), recipe.steps[0].pages)
        assertEquals("FINAL", (recipe.steps[1].op as SetWatermark).options?.text)
    }

    @Test
    fun `edits tied to one file are left out and counted`() {
        val recorded = Recipes.record(
            3,
            listOf(
                InsertPages(3, 2, 3, "other.pdf"),
                TiltPages(mapOf(0 to 2f)),
                SetBookmarks(listOf(Bookmark("Intro", 0))),
                SetAttachments(AttachmentChanges(removed = setOf("old.xlsx"))),
                SetGreyscale(true),
            ),
        )
        assertEquals(4, recorded.skipped)
        assertEquals(listOf<EditOp>(SetGreyscale(true)), recorded.steps.map { it.op })
    }

    @Test
    fun `a mark keeps its final shape and lands on fresh ids`() {
        val first = FillMark(id = 1, sourceIndex = 1, rect = MarkRect(0.1f, 0.1f, 0.3f, 0.2f))
        val moved = first.copy(rect = MarkRect(0.5f, 0.5f, 0.7f, 0.6f))
        val gone = TextMark(id = 2, sourceIndex = 0, text = "temp", left = 0f, top = 0f)
        val recipe = recipeOf(3, AddMark(first), AddMark(gone), UpdateMark(moved), RemoveMark(2))
        assertEquals(1, recipe.steps.size)
        assertTrue(recipe.hasPlacedContent)

        var next = 100L
        val ops = Recipes.replay(recipe, 3, existing = listOf(AddMark(first.copy(id = 7)))) { ++next }
        val placed = (ops.single() as AddMark).mark as FillMark
        assertEquals(101L, placed.id)
        assertEquals(1, placed.sourceIndex)
        assertEquals(moved.rect, placed.rect)
    }

    @Test
    fun `redactions record the search, so they find text in any file`() {
        val recipe = recipeOf(
            2,
            AddRedaction(Redaction(RedactRule(RedactKind.WORDS, "old"), setOf(0))),
            ClearRedactions,
            AddRedaction(Redaction(RedactRule(RedactKind.EMAILS), setOf(0, 1))),
        )
        val plan = replay(recipe, 9)
        assertEquals(listOf(Redaction(RedactRule(RedactKind.EMAILS), (0 until 9).toSet())), plan.redactions)
    }

    @Test
    fun `running a recipe on an edited document works on the pages as they now stand`() {
        val recipe = recipeOf(3, DeletePages(setOf(0)))
        val existing = listOf<EditOp>(ReversePages)
        val ops = Recipes.replay(recipe, 3, existing) { 1L }
        // Reversed, the first page on screen is the original page 3.
        assertEquals(listOf<EditOp>(DeletePages(setOf(2))), ops)
    }

    @Test
    fun `every kind of step survives the file format`() {
        val steps = listOf(
            RecipeStep(RotatePages(270, emptySet()), PageChoice.Odd),
            RecipeStep(CropPages(CropInsets(0.1f, 0.2f, 0.05f, 0f), emptySet()), PageChoice.All),
            RecipeStep(ScalePages(0.75f, emptySet()), PageChoice.Even),
            RecipeStep(ResizePages(ResizeTarget(PaperSize.A4, PageOrientation.LANDSCAPE, fill = true), emptySet()), PageChoice.Last),
            RecipeStep(DeletePages(emptySet()), PageChoice.Positions(listOf(0, 4, 5))),
            RecipeStep(MovePage(0, -2), PageChoice.Positions(listOf(3))),
            RecipeStep(ReversePages),
            RecipeStep(SetWatermark(WatermarkOptions("CONFIDENTIAL", 40f, 0.3f, WatermarkLayout.TILED, 0x80FF0000.toInt(), false))),
            RecipeStep(SetWatermark(null)),
            RecipeStep(SetPageNumbers(PageNumberOptions("{n} / {total}", NumberPosition.TOP_RIGHT, 3, 1, 9f, 20f, 0xFF112233.toInt()))),
            RecipeStep(SetHeaderFooter(HeaderFooterOptions(mapOf(NumberPosition.BOTTOM_LEFT to "ACME-{n:6}"), 100, 2, 8f, 30f))),
            RecipeStep(SetHeaderFooter(null)),
            RecipeStep(AddRedaction(Redaction(RedactRule(RedactKind.WORDS, "Jane \"JD\" Doe"), emptySet())), PageChoice.All),
            RecipeStep(AddRedaction(Redaction(RedactRule(RedactKind.PHONES), emptySet())), PageChoice.First),
            RecipeStep(SetMarkups(MarkupMode.FLATTEN)),
            RecipeStep(SetGreyscale(true)),
            RecipeStep(SetProperties(DocProperties("Report", "Ada", "", "q3"))),
            RecipeStep(FillForm(mapOf("name" to "Ada", "note" to "line 1\nline 2"), flatten = true)),
            RecipeStep(AddMark(TextMark(0, 0, "Approved ✓ अनुमोदित", 0.25f, 0.5f, 14f, 0xFF00AA00.toInt(), true)), PageChoice.Positions(listOf(1))),
            RecipeStep(AddMark(FillMark(0, 0, MarkRect(0.1f, 0.2f, 0.3f, 0.4f), 0xFFFFFF00.toInt(), 0.4f)), PageChoice.First),
            RecipeStep(AddMark(InkMark(0, 0, listOf(listOf(0.1f to 0.1f, 0.2f to 0.25f)), 0xFF0000FF.toInt(), 0.006f)), PageChoice.Last),
        )
        val recipe = Recipe("Monthly report ✓", steps)
        val decoded = Recipes.decode(Recipes.encode(recipe))
        assertEquals(recipe.name, decoded.name)
        assertEquals(recipe.steps.size, decoded.steps.size)
        recipe.steps.zip(decoded.steps).forEach { (expected, actual) -> assertEquals(expected, actual) }
    }

    @Test
    fun `pages are written counting from one, as people count`() {
        val text = Recipes.encode(Recipe("r", listOf(RecipeStep(DeletePages(emptySet()), PageChoice.Positions(listOf(0, 2))))))
        assertTrue(text, text.contains("[1, 3]"))
        assertTrue(text, text.contains("\"do\": \"delete\""))
    }

    @Test
    fun `steps this version doesn't know are skipped, and other files are refused`() {
        val text = """{"anonpdf-recipe": 1, "name": "r", "steps": [{"do": "teleport"}, {"do": "reverse"}]}"""
        assertEquals(listOf(RecipeStep(ReversePages)), Recipes.decode(text).steps)

        listOf("", "not json", "{}", "[1, 2]", """{"name": "x"}""").forEach { bad ->
            try {
                Recipes.decode(bad)
                fail("accepted $bad")
            } catch (expected: IllegalArgumentException) {
            }
        }
        try {
            Recipes.decode("""{"anonpdf-recipe": 2, "steps": []}""")
            fail("accepted a newer format")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `json strings round-trip whatever they hold`() {
        val tricky = "quote \" backslash \\ tab \t newline \n control \u0001 emoji 😀"
        assertEquals(mapOf("k" to tricky), Json.parse(Json.write(mapOf("k" to tricky))))
        assertEquals(listOf(1.5, -2.0, 1e-3, true, null), Json.parse("[1.5, -2, 1e-3, true, null]"))
    }
}
