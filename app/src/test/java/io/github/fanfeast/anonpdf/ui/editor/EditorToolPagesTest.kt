package io.github.fanfeast.anonpdf.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor is meant to be the one place every tool is reachable from, which is
 * only true if each one is actually placed on a page. Adding a tool and forgetting
 * to list it would otherwise make it silently unreachable.
 */
class EditorToolPagesTest {

    @Test
    fun `every tool is reachable exactly once`() {
        val placed = toolPages(currentPageDeleted = false).flatMap { it.tools }
        // Restore stands in for Delete on a deleted page, so it is the one tool
        // that is absent from this arrangement.
        val expected = EditorTool.entries - EditorTool.RESTORE

        assertEquals(expected.toSet(), placed.toSet())
        assertEquals("a tool is listed twice", placed.size, placed.toSet().size)
    }

    @Test
    fun `restore replaces delete on a deleted page`() {
        val onDeleted = toolPages(currentPageDeleted = true).flatMap { it.tools }

        assertTrue(EditorTool.RESTORE in onDeleted)
        assertTrue(EditorTool.DELETE !in onDeleted)
        assertEquals(
            "swapping one tool must not change the layout",
            toolPages(currentPageDeleted = false).flatMap { it.tools }.size,
            onDeleted.size,
        )
    }

    @Test
    fun `page count constant matches the pages built`() {
        assertEquals(TOOL_PAGE_COUNT, toolPages(currentPageDeleted = false).size)
        assertEquals(TOOL_PAGE_COUNT, toolPages(currentPageDeleted = true).size)
    }

    @Test
    fun `no page holds more tools than a row of four can show twice`() {
        toolPages(currentPageDeleted = false).forEach { page ->
            assertTrue(
                "${page.title} has ${page.tools.size} tools, which will not fit",
                page.tools.size <= 8,
            )
        }
    }
}
