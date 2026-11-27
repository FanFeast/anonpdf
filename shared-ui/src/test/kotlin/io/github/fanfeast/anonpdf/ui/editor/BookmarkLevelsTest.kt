package io.github.fanfeast.anonpdf.ui.editor

import io.github.fanfeast.anonpdf.pdf.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkLevelsTest {

    private fun levels(vararg levels: Int) = levels.mapIndexed { index, level -> Bookmark("$index", index, level) }

    @Test
    fun theFirstBookmarkIsAlwaysAtTheTop() {
        assertEquals(levels(0, 1), tidied(levels(2, 1)))
    }

    @Test
    fun noBookmarkSitsMoreThanOneDeeperThanTheOneAbove() {
        assertEquals(levels(0, 1, 2, 0, 1), tidied(levels(0, 3, 4, 0, 2)))
    }

    @Test
    fun aValidListIsLeftAlone() {
        val valid = levels(0, 1, 1, 2, 0)
        assertEquals(valid, tidied(valid))
    }
}
