package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** A page's shape read back: headings, paragraphs, lists and tables. */
class PdfStructureTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("structuretest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** A report: a title, a paragraph over two lines, a list, and a three-column table. */
    private fun report(): File = File(workDir, "report.pdf").also { out ->
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                fun write(text: String, x: Float, y: Float, font: PDFont = PDType1Font.HELVETICA, size: Float = 11f) {
                    stream.beginText()
                    stream.setFont(font, size)
                    stream.newLineAtOffset(x, y)
                    stream.showText(text)
                    stream.endText()
                }
                write("Quarterly report", 72f, 760f, PDType1Font.HELVETICA_BOLD, 24f)
                write("Sales grew in every region this quarter, led by the", 72f, 720f)
                write("north, where two new shops opened.", 72f, 706f)
                write("- Costs fell", 72f, 680f)
                write("- Staff grew", 72f, 666f)
                val table = listOf(listOf("Region", "Shops", "Sales"), listOf("North", "12", "1,200"), listOf("South", "9", "870"), listOf("East", "7", "640"))
                table.forEachIndexed { row, cells ->
                    cells.forEachIndexed { column, cell -> write(cell, 72f + column * 150f, 620f - row * 16f) }
                }
            }
            document.save(out)
        }
    }

    @Test
    fun aTableIsFoundWithItsRowsAndColumns() = runBlocking {
        val read = PdfStructure.read(report())
        val tables = PdfStructure.tables(read.pages)
        assertEquals(1, tables.size)
        assertEquals(
            listOf(listOf("Region", "Shops", "Sales"), listOf("North", "12", "1,200"), listOf("South", "9", "870"), listOf("East", "7", "640")),
            tables.single().rows,
        )
        assertEquals("Region,Shops,Sales\nNorth,12,\"1,200\"\nSouth,9,870\nEast,7,640\n", PdfStructure.csv(tables.single().rows))
    }

    @Test
    fun markdownHasTheHeadingTheJoinedParagraphTheListAndTheTable() = runBlocking {
        val markdown = PdfStructure.markdown(PdfStructure.read(report()).pages)
        listOf(
            "# Quarterly report",
            "Sales grew in every region this quarter, led by the north, where two new shops opened.",
            "- Costs fell\n- Staff grew",
            "| Region | Shops | Sales |\n| --- | --- | --- |\n| North | 12 | 1,200 |",
        ).forEach { assertTrue("missing: $it\n$markdown", markdown.contains(it)) }
    }

    @Test
    fun jsonGivesEachLineWithItsPlace() = runBlocking {
        val read = PdfStructure.read(report())
        val json = Json.parse(PdfStructure.json(read.pages, read.sizes)) as Map<*, *>
        val page = (json["pages"] as List<*>).single() as Map<*, *>
        assertEquals(595.3, page["width"] as Double, 0.1)
        val title = (page["lines"] as List<*>).first() as Map<*, *>
        assertEquals("Quarterly report", title["text"])
        assertEquals(72.0, title["x"] as Double, 1.0)
        assertEquals(24.0, title["size"] as Double, 0.5)
    }

    @Test
    fun ordinaryTextIsNotTakenForATable() = runBlocking {
        val read = PdfStructure.read(report())
        // The paragraph and list lines above the table are left out of it.
        assertEquals(4, PdfStructure.tables(read.pages).single().rows.size)
    }
}
