package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The op-folding rules. Pure Kotlin, so these run on the JVM in milliseconds and
 * pin down the semantics the editor depends on.
 */
class EditPlanBuilderTest {

    private fun plan(pageCount: Int, vararg ops: EditOp) =
        EditPlanBuilder.build(pageCount, ops.toList())

    @Test
    fun `identity plan keeps every page in order and untouched`() {
        val result = EditPlanBuilder.identity(4)
        assertEquals(listOf(0, 1, 2, 3), result.pages.map { it.sourceIndex })
        assertTrue(result.pages.all { it.isUntouched })
        assertFalse("an untouched plan has nothing to save", result.hasChanges)
    }

    @Test
    fun `rotation accumulates so two quarter turns make a half turn`() {
        val result = plan(3, RotatePages(90, setOf(1)), RotatePages(90, setOf(1)))
        assertEquals(180, result.pages[1].rotationDelta)
        assertEquals(0, result.pages[0].rotationDelta)
    }

    @Test
    fun `rotation only touches the targeted pages`() {
        val result = plan(4, RotatePages(-90, setOf(0, 3)))
        assertEquals(listOf(-90, 0, 0, -90), result.pages.map { it.rotationDelta })
    }

    @Test
    fun `crop replaces rather than accumulating`() {
        // The crop control reports an absolute rectangle, so the last one wins.
        val result = plan(
            2,
            CropPages(CropInsets(0.1f, 0.1f, 0.1f, 0.1f), setOf(0)),
            CropPages(CropInsets(0.2f, 0f, 0f, 0f), setOf(0)),
        )
        assertEquals(0.2f, result.pages[0].crop.left, 0.0001f)
        assertEquals(0f, result.pages[0].crop.top, 0.0001f)
    }

    @Test
    fun `scale replaces rather than compounding`() {
        val result = plan(1, ScalePages(0.5f, setOf(0)), ScalePages(0.8f, setOf(0)))
        assertEquals(0.8f, result.pages[0].scale, 0.0001f)
    }

    @Test
    fun `resizing to a sheet and scaling by a factor each clear the other`() {
        // They are two answers to the same question, so stacking them would be
        // meaningless — whichever came last is what the user meant.
        val scaledThenResized = plan(
            1,
            ScalePages(0.5f, setOf(0)),
            ResizePages(ResizeTarget(PaperSize.LETTER), setOf(0)),
        )
        assertEquals(1f, scaledThenResized.pages[0].scale, 0.0001f)
        assertEquals(PaperSize.LETTER, scaledThenResized.pages[0].resize?.paper)

        val resizedThenScaled = plan(
            1,
            ResizePages(ResizeTarget(PaperSize.LETTER), setOf(0)),
            ScalePages(0.5f, setOf(0)),
        )
        assertEquals(null, resizedThenScaled.pages[0].resize)
        assertEquals(0.5f, resizedThenScaled.pages[0].scale, 0.0001f)
    }

    @Test
    fun `resizing counts as a change and only touches its targets`() {
        val result = plan(3, ResizePages(ResizeTarget(PaperSize.A5), setOf(1)))
        assertTrue(result.hasChanges)
        assertEquals(null, result.pages[0].resize)
        assertEquals(PaperSize.A5, result.pages[1].resize?.paper)
        assertEquals(null, result.pages[2].resize)
    }

    @Test
    fun `auto orientation follows the page, and can be overridden`() {
        val target = ResizeTarget(PaperSize.LETTER, PageOrientation.AUTO)
        // A landscape page onto Letter should stay landscape.
        assertEquals(792f to 612f, target.sizeFor(sourceWidth = 900f, sourceHeight = 600f))
        // A portrait one should stay portrait.
        assertEquals(612f to 792f, target.sizeFor(sourceWidth = 600f, sourceHeight = 900f))

        val forced = ResizeTarget(PaperSize.LETTER, PageOrientation.PORTRAIT)
        assertEquals(612f to 792f, forced.sizeFor(sourceWidth = 900f, sourceHeight = 600f))
    }

    @Test
    fun `resize descriptions name the sheet and the fit`() {
        assertEquals(
            "Resize to Letter (fit) · page 1",
            ResizePages(ResizeTarget(PaperSize.LETTER), setOf(0)).describe(),
        )
        assertEquals(
            "Resize to A4 landscape (fill) · pages 1-2",
            ResizePages(
                ResizeTarget(PaperSize.A4, PageOrientation.LANDSCAPE, fill = true),
                setOf(0, 1),
            ).describe(),
        )
    }

    @Test
    fun `deleting drops a page from kept but leaves it recoverable`() {
        val result = plan(3, DeletePages(setOf(1)))
        assertEquals(3, result.pages.size)
        assertEquals(listOf(0, 2), result.kept.map { it.sourceIndex })
        assertTrue(result.hasChanges)
    }

    @Test
    fun `restore brings a deleted page back in its original place`() {
        val result = plan(3, DeletePages(setOf(1)), RestorePages(setOf(1)))
        assertEquals(listOf(0, 1, 2), result.kept.map { it.sourceIndex })
    }

    @Test
    fun `moving a page shifts it by the offset`() {
        val result = plan(4, MovePage(sourceIndex = 0, offset = 2))
        assertEquals(listOf(1, 2, 0, 3), result.pages.map { it.sourceIndex })
    }

    @Test
    fun `moving past the ends clamps instead of throwing`() {
        assertEquals(
            listOf(1, 2, 3, 0),
            plan(4, MovePage(0, 99)).pages.map { it.sourceIndex },
        )
        assertEquals(
            listOf(3, 0, 1, 2),
            plan(4, MovePage(3, -99)).pages.map { it.sourceIndex },
        )
    }

    @Test
    fun `moving a page that is not present is a no-op`() {
        val result = plan(2, MovePage(sourceIndex = 7, offset = 1))
        assertEquals(listOf(0, 1), result.pages.map { it.sourceIndex })
    }

    @Test
    fun `reverse flips the order`() {
        assertEquals(
            listOf(2, 1, 0),
            plan(3, ReversePages).pages.map { it.sourceIndex },
        )
    }

    @Test
    fun `edits survive a reorder because they travel with the page`() {
        val result = plan(3, RotatePages(90, setOf(0)), ReversePages)
        assertEquals(listOf(2, 1, 0), result.pages.map { it.sourceIndex })
        assertEquals(90, result.pages.last { it.sourceIndex == 0 }.rotationDelta)
    }

    @Test
    fun `watermark and page numbers are document-wide and replaceable`() {
        val result = plan(
            2,
            SetWatermark(WatermarkOptions(text = "DRAFT")),
            SetWatermark(WatermarkOptions(text = "FINAL")),
            SetPageNumbers(PageNumberOptions(format = "{n}")),
        )
        assertEquals("FINAL", result.watermark?.text)
        assertEquals("{n}", result.pageNumbers?.format)
        assertTrue(result.hasChanges)
    }

    @Test
    fun `setting then clearing a watermark leaves no change behind`() {
        val result = plan(2, SetWatermark(WatermarkOptions(text = "X")), SetWatermark(null))
        assertEquals(null, result.watermark)
        assertFalse(result.hasChanges)
    }

    @Test
    fun `combined per-page edits coexist in one plan`() {
        // This is the case the old one-tool-at-a-time flow could not express.
        val result = plan(
            5,
            CropPages(CropInsets(0.06f, 0.06f, 0.06f, 0.06f), setOf(1, 2)),
            DeletePages(setOf(3)),
            RotatePages(90, setOf(4)),
            ScalePages(0.75f, setOf(0)),
        )
        assertEquals(listOf(0, 1, 2, 4), result.kept.map { it.sourceIndex })
        assertEquals(0.75f, result.pages[0].scale, 0.0001f)
        assertEquals(0.06f, result.pages[1].crop.left, 0.0001f)
        assertEquals(0.06f, result.pages[2].crop.left, 0.0001f)
        assertTrue(result.pages[3].deleted)
        assertEquals(90, result.pages[4].rotationDelta)
    }

    @Test
    fun `marks are added, replaced by id, and removed`() {
        val first = TextMark(id = 1L, sourceIndex = 0, text = "one", left = 0.1f, top = 0.1f)
        val second = TextMark(id = 2L, sourceIndex = 1, text = "two", left = 0.2f, top = 0.2f)

        var result = plan(2, AddMark(first), AddMark(second))
        assertEquals(2, result.marks.size)
        assertTrue(result.hasChanges)

        // Updating replaces in place rather than appending a duplicate.
        result = plan(
            2,
            AddMark(first),
            AddMark(second),
            UpdateMark(first.copy(text = "one edited", left = 0.4f)),
        )
        assertEquals(2, result.marks.size)
        val updated = result.marks.first { it.id == 1L } as TextMark
        assertEquals("one edited", updated.text)
        assertEquals(0.4f, updated.left, 0.0001f)

        result = plan(2, AddMark(first), AddMark(second), RemoveMark(1L))
        assertEquals(listOf(2L), result.marks.map { it.id })
    }

    @Test
    fun `marks are looked up by the page they belong to`() {
        val result = plan(
            3,
            AddMark(TextMark(id = 1L, sourceIndex = 2, text = "a", left = 0f, top = 0f)),
            AddMark(TextMark(id = 2L, sourceIndex = 2, text = "b", left = 0f, top = 0f)),
            AddMark(TextMark(id = 3L, sourceIndex = 0, text = "c", left = 0f, top = 0f)),
        )
        assertEquals(listOf(1L, 2L), result.marksFor(2).map { it.id })
        assertEquals(listOf(3L), result.marksFor(0).map { it.id })
        assertEquals(emptyList<Long>(), result.marksFor(1).map { it.id })
    }

    @Test
    fun `removing the only mark leaves nothing to save`() {
        val mark = TextMark(id = 1L, sourceIndex = 0, text = "x", left = 0f, top = 0f)
        assertFalse(plan(1, AddMark(mark), RemoveMark(1L)).hasChanges)
    }

    @Test
    fun `a dragged rectangle is tidied whichever way it was drawn`() {
        // Dragging up and to the left produces a rectangle with swapped edges.
        val backwards = MarkRect(left = 0.8f, top = 0.9f, right = 0.2f, bottom = 0.3f).tidied()
        assertEquals(0.2f, backwards.left, 0.0001f)
        assertEquals(0.3f, backwards.top, 0.0001f)
        assertEquals(0.8f, backwards.right, 0.0001f)
        assertEquals(0.9f, backwards.bottom, 0.0001f)
    }

    @Test
    fun `moving a rectangle stops at the page edges`() {
        val rect = MarkRect(0.1f, 0.1f, 0.3f, 0.3f)
        // Pushed hard left and up, it should clamp without changing size.
        val clamped = rect.movedBy(-5f, -5f)
        assertEquals(0f, clamped.left, 0.0001f)
        assertEquals(0f, clamped.top, 0.0001f)
        assertEquals(0.2f, clamped.width, 0.0001f)
        assertEquals(0.2f, clamped.height, 0.0001f)

        val other = rect.movedBy(5f, 5f)
        assertEquals(1f, other.right, 0.0001f)
        assertEquals(0.2f, other.width, 0.0001f)
    }

    @Test
    fun `inserted pages land where asked, in order`() {
        // A 4-page document, with a 3-page document (global indices 4..6)
        // inserted after page 2.
        val result = plan(4, InsertPages(startIndex = 4, count = 3, position = 2, label = "b.pdf"))
        assertEquals(listOf(0, 1, 4, 5, 6, 2, 3), result.pages.map { it.sourceIndex })
        assertTrue(result.hasChanges)
    }

    @Test
    fun `insert at the end still counts as a change`() {
        // Appended pages keep index == position, which the positional check
        // alone would read as "nothing happened".
        val result = plan(2, InsertPages(startIndex = 2, count = 2, position = 2, label = "b"))
        assertEquals(listOf(0, 1, 2, 3), result.pages.map { it.sourceIndex })
        assertTrue(result.hasChanges)
    }

    @Test
    fun `an out-of-range insert position clamps instead of crashing`() {
        val result = plan(2, InsertPages(startIndex = 2, count = 1, position = 99, label = "b"))
        assertEquals(listOf(0, 1, 2), result.pages.map { it.sourceIndex })
    }

    @Test
    fun `inserted pages take edits like any other page`() {
        val result = plan(
            2,
            InsertPages(startIndex = 2, count = 2, position = 1, label = "b"),
            RotatePages(90, setOf(2)),
            DeletePages(setOf(3)),
            MovePage(sourceIndex = 2, offset = -1),
        )
        // Order: inserted page 2 moved to the front, inserted page 3 deleted.
        assertEquals(listOf(2, 0, 1), result.kept.map { it.sourceIndex })
        assertEquals(90, result.kept.first().rotationDelta)
    }

    @Test
    fun `marks stick to inserted pages through a reorder`() {
        val result = plan(
            2,
            InsertPages(startIndex = 2, count = 1, position = 2, label = "b"),
            AddMark(TextMark(id = 1L, sourceIndex = 2, text = "x", left = 0f, top = 0f)),
            ReversePages,
        )
        assertEquals(listOf(2, 1, 0), result.pages.map { it.sourceIndex })
        assertEquals(listOf(1L), result.marksFor(2).map { it.id })
    }

    @Test
    fun `dropping the insert op removes its pages, as undo does`() {
        val withInsert = listOf<EditOp>(
            InsertPages(startIndex = 3, count = 2, position = 1, label = "b"),
            RotatePages(90, setOf(0)),
        )
        val undone = EditPlanBuilder.build(3, withInsert.dropLast(2))
        assertEquals(listOf(0, 1, 2), undone.pages.map { it.sourceIndex })
        assertFalse(undone.hasChanges)
    }

    @Test
    fun `op descriptions read as history a person can scan`() {
        assertEquals("Rotate 90° · pages 1-2", RotatePages(90, setOf(0, 1)).describe())
        assertEquals("Delete · page 3", DeletePages(setOf(2)).describe())
        assertEquals(
            "Crop 6% · page 1",
            CropPages(CropInsets(0.06f, 0.06f, 0.06f, 0.06f), setOf(0)).describe(),
        )
        assertEquals("Scale 75% · page 1", ScalePages(0.75f, setOf(0)).describe())
        assertEquals("Reverse page order", ReversePages.describe())
        assertEquals("Remove watermark", SetWatermark(null).describe())
    }
}
