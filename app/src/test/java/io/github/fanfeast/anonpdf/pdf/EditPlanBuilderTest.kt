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
