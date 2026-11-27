package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One entry in a document's table of contents.
 *
 * Kept flat, with a nesting [level], rather than as a tree: a list is what the
 * editor shows and what moving, indenting and deleting act on, and any list
 * whose levels never jump by more than one is a valid tree.
 */
data class Bookmark(
    val title: String,
    /** The page it opens, by source page index, so it follows the page through a reorder. */
    val sourceIndex: Int,
    val level: Int = 0,
)

/**
 * Reads and writes a document's bookmarks (its outline).
 *
 * Writing replaces the outline with plain entries that each open a page. What a
 * bookmark looked like (colour, bold) and any other action it ran are not kept:
 * an entry that ran a script or opened a web address is exactly what this app
 * would rather not write.
 */
object PdfBookmarks {

    /** Plenty for any real table of contents, and a stop for a looping one. */
    private const val MAX_ENTRIES = 10_000

    suspend fun read(file: File, password: String? = null): List<Bookmark> = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { read(it) }
    }

    /**
     * The document's outline in reading order. Entries that open no page of this
     * document are left out, and their children move up to take their place.
     */
    internal fun read(document: PDDocument): List<Bookmark> {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        val pages = document.pages
        val result = mutableListOf<Bookmark>()
        val seen = HashSet<COSDictionary>()

        fun walk(node: PDOutlineNode, level: Int) {
            for (item in node.children()) {
                if (result.size >= MAX_ENTRIES || !seen.add(item.cosObject)) return
                val page = runCatching { item.findDestinationPage(document) }.getOrNull()
                val index = page?.let { pages.indexOf(it) } ?: -1
                if (index >= 0) {
                    result += Bookmark(item.title.orEmpty().trim(), index, level)
                    walk(item, level + 1)
                } else {
                    walk(item, level)
                }
            }
        }
        runCatching { walk(outline, 0) }
        return result
    }

    /**
     * Replaces the document's outline with [bookmarks]. [pageFor] gives the page a
     * source index now sits on, or null if that page is not in the document;
     * such entries are dropped and their children move up.
     */
    internal fun write(document: PDDocument, bookmarks: List<Bookmark>, pageFor: (Int) -> PDPage?) {
        val outline = PDDocumentOutline()
        // parents[n] is the entry a bookmark at level n goes under.
        val parents = mutableListOf<PDOutlineNode>(outline)
        val dropped = mutableListOf<Int>()
        for (bookmark in bookmarks) {
            // Levels are relative to what survived: a dropped entry's children
            // take its level rather than hanging one deeper than their parent.
            while (dropped.isNotEmpty() && dropped.last() >= bookmark.level) dropped.removeAt(dropped.lastIndex)
            val level = (bookmark.level - dropped.size).coerceIn(0, parents.size - 1)
            val page = pageFor(bookmark.sourceIndex)
            if (page == null) {
                dropped += bookmark.level
                continue
            }
            while (parents.size > level + 1) parents.removeAt(parents.lastIndex)
            val item = PDOutlineItem().apply {
                title = bookmark.title.ifBlank { " " }
                destination = PDPageFitDestination().apply { this.page = page }
            }
            parents.last().addLast(item)
            parents += item
        }
        document.documentCatalog.documentOutline = if (outline.hasChildren()) outline else null
    }
}
