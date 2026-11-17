package io.github.fanfeast.anonpdf.ui.components

import io.github.fanfeast.anonpdf.pdf.HiddenInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenInfoDescriptionTest {

    private fun describe(info: HiddenInfo) = describeHiddenInfo(info) { "day $it" }

    @Test
    fun `nothing hidden describes as nothing`() {
        assertTrue(describe(HiddenInfo()).isEmpty())
    }

    @Test
    fun `who and what come before counts`() {
        val lines = describe(
            HiddenInfo(
                author = "Jane",
                creatorApp = "Writer",
                producerApp = "Writer",
                created = 7,
                attachments = 2,
                scripts = 1,
                commentAuthors = setOf("Sam"),
            ),
        )
        assertEquals(
            listOf(
                "Author: Jane",
                "Comment authors: Sam",
                "Made with: Writer",
                "Created day 7",
                "2 attached files",
                "1 script",
            ),
            lines,
        )
    }

    @Test
    fun `a different PDF producer is named separately`() {
        val lines = describe(HiddenInfo(creatorApp = "Writer", producerApp = "Converter"))
        assertEquals(listOf("Made with: Writer", "PDF by: Converter"), lines)
    }
}
