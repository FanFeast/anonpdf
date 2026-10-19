package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangesTest {

    @Test
    fun `parses a single page`() {
        assertEquals(listOf(2..2), PageRanges.parse("3", 10))
    }

    @Test
    fun `parses a mix of ranges and singles`() {
        assertEquals(
            listOf(0..2, 4..4, 7..9),
            PageRanges.parse("1-3, 5, 8-10", 10),
        )
    }

    @Test
    fun `tolerates loose whitespace and trailing commas`() {
        assertEquals(listOf(0..1, 4..4), PageRanges.parse("  1 - 2 ,, 5 , ", 6))
    }

    @Test
    fun `rejects a range beyond the document`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PageRanges.parse("1-11", 10)
        }
        assertEquals("This document only has 10 pages.", error.message)
    }

    @Test
    fun `rejects a backwards range`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PageRanges.parse("8-4", 10)
        }
        assertEquals("\"8-4\" runs backwards.", error.message)
    }

    @Test
    fun `rejects page zero because users count from one`() {
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("0", 10) }
    }

    @Test
    fun `rejects nonsense`() {
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("abc", 10) }
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("1-2-3", 10) }
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("", 10) }
    }

    @Test
    fun `singular wording for a one page document`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PageRanges.parse("2", 1)
        }
        assertEquals("This document only has 1 page.", error.message)
    }

    @Test
    fun `flattening removes duplicates but keeps first-seen order`() {
        val ranges = PageRanges.parse("5, 1-3, 2", 10)
        assertEquals(listOf(4, 0, 1, 2), PageRanges.toPageList(ranges))
    }

    @Test
    fun `describe collapses consecutive runs`() {
        assertEquals("1-3, 5, 8-10", PageRanges.describe(listOf(0, 1, 2, 4, 7, 8, 9)))
        assertEquals("4", PageRanges.describe(listOf(3)))
        assertEquals("none", PageRanges.describe(emptyList()))
    }

    @Test
    fun `describe sorts unordered input`() {
        assertEquals("1-2, 9", PageRanges.describe(listOf(8, 0, 1)))
    }
}
