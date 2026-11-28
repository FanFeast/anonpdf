package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Text and Markdown files laid out as PDFs. */
class TextToPdfTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("texttest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun convert(name: String, text: String): File {
        val input = File(workDir, name).apply { writeText(text) }
        return File(workDir, "$name.pdf").also { runBlocking { TextToPdf.convert(input, it, markdown = name.endsWith(".md")) } }
    }

    private fun text(file: File) = PdfOps.load(file).use { PDFTextStripper().getText(it) }

    @Test
    fun markdownKeepsItsShapeAndDropsItsMarks() {
        val pdf = convert(
            "notes.md",
            """
            # Release notes

            The **new** tool is *fast* and `small`. See [the docs](https://example.com/docs).

            - First point
            - Second point

            1. Step one

            ```
            val x = 1   // kept as typed
            ```
            """.trimIndent(),
        )
        val found = text(pdf)
        listOf(
            "Release notes",
            "The new tool is fast and small. See the docs (https://example.com/docs).",
            "• First point",
            "1. Step one",
            "val x = 1   // kept as typed",
        ).forEach { assertTrue("missing: $it\n$found", found.contains(it)) }
        listOf("**", "`", "# ").forEach { assertTrue("left in: $it", !found.contains(it)) }
    }

    @Test
    fun longTextWrapsAndRunsOntoMorePages() {
        val sentence = "Every word of this sentence has to fit inside the margins of the page. "
        val pdf = convert("long.md", (1..120).joinToString("\n\n") { "$it. " + sentence.repeat(3) })
        PdfOps.load(pdf).use { document ->
            assertTrue(document.numberOfPages > 3)
            val page = document.getPage(0).mediaBox
            var widest = 0f
            object : PDFTextStripper() {
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    positions.forEach { widest = maxOf(widest, it.xDirAdj + it.widthDirAdj) }
                }
            }.getText(document)
            assertTrue("text runs to $widest of ${page.width}", widest <= page.width - 50f)
        }
    }

    @Test
    fun plainTextKeepsItsIndentation() {
        val pdf = convert("list.txt", "Name\n    indented\nEnd")
        val starts = mutableMapOf<String, Float>()
        PdfOps.load(pdf).use { document ->
            object : PDFTextStripper() {
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    starts[text.trim()] = positions.first().xDirAdj
                }
            }.getText(document)
        }
        assertEquals(starts["Name"]!!, starts["End"]!!, 0.5f)
        assertTrue(starts.toString(), starts["indented"]!! > starts["Name"]!! + 20f)
    }

    @Test
    fun inlineMarksReadAsPlainText() {
        assertEquals("a bold and italic word", TextToPdf.inline("a **bold** and _italic_ word"))
        assertEquals("snake_case_name stays", TextToPdf.inline("snake_case_name stays"))
        assertEquals("site", TextToPdf.inline("[site](site)"))
        assertEquals("a picture", TextToPdf.inline("![a picture](pic.png)"))
        assertEquals("2 * 3 = 6", TextToPdf.inline("2 * 3 = 6"))
    }
}
