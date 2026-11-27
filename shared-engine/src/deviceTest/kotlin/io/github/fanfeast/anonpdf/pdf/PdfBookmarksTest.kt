package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfBookmarksTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("bookmarkstest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun out(name: String) = File(workDir, "$name-${System.nanoTime()}.pdf")

    /**
     * Four pages, and an outline: Intro, Chapter 1 with Section 1.1 under it, and
     * an entry that opens no page with Orphan under it.
     */
    private fun outlinedPdf(): File {
        val file = File(workDir, "src-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val pages = (1..4).map { number ->
                PDPage(PDRectangle.A4).also { page ->
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 20f)
                        stream.newLineAtOffset(70f, 700f)
                        stream.showText("Page $number")
                        stream.endText()
                    }
                }
            }
            fun PDOutlineNode.add(title: String, page: PDPage?) = PDOutlineItem().also { item ->
                item.title = title
                if (page != null) item.destination = PDPageFitDestination().apply { this.page = page }
                addLast(item)
            }
            val outline = PDDocumentOutline()
            outline.add("Intro", pages[0])
            outline.add("Chapter 1", pages[1]).add("Section 1.1", pages[2])
            outline.add("Nowhere", null).add("Orphan", pages[3])
            document.documentCatalog.documentOutline = outline
            document.save(file)
        }
        return file
    }

    private fun bookmarksOf(file: File) = runBlocking { PdfBookmarks.read(file) }

    @Test
    fun anOutlineIsReadInOrderWithItsNesting() {
        assertEquals(
            listOf(
                Bookmark("Intro", 0, 0),
                Bookmark("Chapter 1", 1, 0),
                Bookmark("Section 1.1", 2, 1),
                // Its parent opens no page, so it moves up a level.
                Bookmark("Orphan", 3, 0),
            ),
            bookmarksOf(outlinedPdf()),
        )
    }

    @Test
    fun setBookmarksFollowTheirPagesThroughAReorder(): Unit = runBlocking {
        val output = out("reversed")
        PdfEditor.applyPlan(
            outlinedPdf(),
            output,
            EditPlanBuilder.build(
                4,
                listOf(
                    SetBookmarks(listOf(Bookmark("Start", 0), Bookmark("Inside", 2, 1))),
                    ReversePages,
                ),
            ),
        )
        assertEquals(listOf(Bookmark("Start", 3, 0), Bookmark("Inside", 1, 1)), bookmarksOf(output))
    }

    @Test
    fun aDeletedPageTakesItsBookmarkAndLeavesNothingOfItselfInTheFile(): Unit = runBlocking {
        val output = out("deleted")
        PdfEditor.applyPlan(outlinedPdf(), output, EditPlanBuilder.build(4, listOf(DeletePages(setOf(1)))))

        // Section 1.1 moves up to take its deleted parent's place.
        assertEquals(
            listOf(Bookmark("Intro", 0, 0), Bookmark("Section 1.1", 1, 0), Bookmark("Orphan", 2, 0)),
            bookmarksOf(output),
        )
        // An outline entry still pointing at the page would have written it out anyway.
        PdfOps.load(output).use { document ->
            assertEquals(3, document.document.getObjectsByType(COSName.PAGE).size)
        }
    }

    @Test
    fun removingAllBookmarksLeavesNoOutline(): Unit = runBlocking {
        val output = out("none")
        PdfEditor.applyPlan(outlinedPdf(), output, EditPlanBuilder.build(4, listOf(SetBookmarks(emptyList()))))
        PdfOps.load(output).use { assertNull(it.documentCatalog.documentOutline) }
    }

    @Test
    fun anUntouchedDocumentKeepsItsOutlineAsItWas(): Unit = runBlocking {
        val output = out("rotated")
        PdfEditor.applyPlan(outlinedPdf(), output, EditPlanBuilder.build(4, listOf(RotatePages(90, setOf(0)))))
        PdfOps.load(output).use { document ->
            val titles = document.documentCatalog.documentOutline.children().map { it.title }
            // "Nowhere" survives: nothing asked for the outline to be rewritten.
            assertEquals(listOf("Intro", "Chapter 1", "Nowhere"), titles)
        }
    }
}
