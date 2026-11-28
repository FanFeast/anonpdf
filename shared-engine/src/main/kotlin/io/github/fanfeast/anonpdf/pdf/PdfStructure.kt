package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** A run of text on one line, with clear space either side. */
data class TextCell(val text: String, val left: Float, val right: Float)

/** One line of a page as shown, in points from the top left, split where the gaps are wide. */
data class TextLine(val top: Float, val bottom: Float, val size: Float, val cells: List<TextCell>) {
    val text: String get() = cells.joinToString(" ") { it.text }
    val left: Float get() = cells.first().left
    val right: Float get() = cells.last().right
}

/** A table found on a page: its rows, each with one entry per column. */
data class FoundTable(val page: Int, val rows: List<List<String>>)

/**
 * A page's text with its shape: lines from the glyph positions, the gaps that
 * split a line into columns, and what follows from them — Markdown with
 * headings and paragraphs, JSON with positions, and the tables on the page.
 *
 * It reads what is drawn, which is all a PDF reliably has: a table is a run of
 * lines whose gaps line up, a heading is a line in larger type. That is right
 * for most documents written by a word processor or a spreadsheet, and for
 * pages that have been through OCR.
 */
object PdfStructure {

    /** A document's lines, page by page, and each page's size as shown, in points. */
    class Read(val pages: List<List<TextLine>>, val sizes: List<Pair<Float, Float>>)

    suspend fun read(file: File, password: String? = null, onProgress: Progress = {}): Read = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { document ->
            checkAllowed(document)
            val pages = (0 until document.numberOfPages).map { index ->
                ensureActive()
                linesOf(document, index).also { onProgress(0.9f * (index + 1f) / document.numberOfPages) }
            }
            val sizes = document.pages.map { page ->
                val turned = page.rotation % 180 != 0
                if (turned) page.cropBox.height to page.cropBox.width else page.cropBox.width to page.cropBox.height
            }
            Read(pages, sizes)
        }
    }

    private fun checkAllowed(document: PDDocument) {
        if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) error(S.noTextExtraction)
    }

    internal fun linesOf(document: PDDocument, index: Int): List<TextLine> {
        val glyphs = mutableListOf<TextPosition>()
        object : PDFTextStripper() {
            override fun writeString(text: String, positions: MutableList<TextPosition>) {
                glyphs += positions
            }
        }.apply {
            sortByPosition = true
            startPage = index + 1
            endPage = index + 1
        }.getText(document)

        val visible = glyphs.filter { it.unicode.isNotBlank() && it.heightDir > 0f }
        val rows = mutableListOf<MutableList<TextPosition>>()
        // Glyphs of one line share a baseline, give or take half their height.
        for (glyph in visible.sortedWith(compareBy({ it.yDirAdj }, { it.xDirAdj }))) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().yDirAdj - glyph.yDirAdj) < row.first().heightDir * 0.5f) {
                row += glyph
            } else {
                rows += mutableListOf(glyph)
            }
        }
        return rows.map { row -> lineOf(row.sortedBy { it.xDirAdj }) }
    }

    private fun lineOf(glyphs: List<TextPosition>): TextLine {
        val size = glyphs.map { it.fontSizeInPt.takeIf { s -> s > 0f } ?: it.heightDir }.sorted()[glyphs.size / 2]
        val space = glyphs.map { it.widthOfSpace.takeIf { w -> w > 0f } ?: (size * 0.25f) }.average().toFloat()
        val cells = mutableListOf<TextCell>()
        val text = StringBuilder()
        var left = glyphs.first().xDirAdj
        var right = left
        fun close() {
            if (text.isNotBlank()) cells += TextCell(text.toString().trim(), left, right)
            text.clear()
        }
        for (glyph in glyphs) {
            val gap = glyph.xDirAdj - right
            when {
                text.isEmpty() -> left = glyph.xDirAdj
                // Wider than a few spaces is a gap between columns.
                gap > maxOf(space * 2.5f, size * 0.9f) -> { close(); left = glyph.xDirAdj }
                gap > space * 0.5f -> text.append(' ')
            }
            text.append(glyph.unicode)
            right = glyph.xDirAdj + glyph.widthDirAdj
        }
        close()
        val top = glyphs.minOf { it.yDirAdj - it.heightDir }
        val bottom = glyphs.maxOf { it.yDirAdj }
        return TextLine(top, bottom, size, cells.ifEmpty { listOf(TextCell("", left, right)) })
    }

    // -------------------------------------------------------------- markdown

    /**
     * Markdown: a line in type well above the body's size is a heading, lines
     * close together are one paragraph, a list keeps its marks, and a table
     * becomes a Markdown table. Pages are separated by a rule.
     */
    fun markdown(pages: List<List<TextLine>>): String {
        val body = pages.flatten().filter { it.text.isNotBlank() }
            .groupingBy { (it.size * 2).roundToInt() / 2f }.eachCount().maxByOrNull { it.value }?.key ?: 11f
        val out = StringBuilder()
        pages.forEachIndexed { pageIndex, lines ->
            // A blank line first, or after a list the rule would read as part of it.
            if (pageIndex > 0) {
                if (!out.endsWith("\n\n")) out.append('\n')
                out.append("---\n\n")
            }
            val tables = tablesOn(lines)
            var paragraph = StringBuilder()
            var previous: TextLine? = null
            fun flush() {
                if (paragraph.isNotBlank()) out.append(paragraph.toString().trim()).append("\n\n")
                paragraph = StringBuilder()
            }
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                val table = tables.firstOrNull { it.first == index }
                if (table != null) {
                    flush()
                    out.append(markdownTable(table.second)).append('\n')
                    index += table.second.size
                    previous = null
                    continue
                }
                val text = line.text.trim()
                val ratio = line.size / body
                val gap = previous?.let { line.top - it.bottom } ?: 0f
                when {
                    text.isEmpty() -> flush()
                    ratio >= 1.25f && text.length < 120 -> {
                        flush()
                        val level = when {
                            ratio >= 1.8f -> 1
                            ratio >= 1.45f -> 2
                            else -> 3
                        }
                        out.append("#".repeat(level)).append(' ').append(text).append("\n\n")
                    }
                    Regex("^([•●▪◦‣\\-*]|\\d{1,3}[.)])\\s+.*").matches(text) -> {
                        flush()
                        out.append(text.replaceFirst(Regex("^[•●▪◦‣]"), "-")).append('\n')
                    }
                    else -> {
                        // A gap wider than a line apart starts a new paragraph.
                        if (gap > line.size * 0.9f) flush()
                        // A word broken over the line end is joined back together.
                        if (paragraph.endsWith("-") && text.firstOrNull()?.isLowerCase() == true) {
                            paragraph.setLength(paragraph.length - 1)
                        } else if (paragraph.isNotEmpty()) {
                            paragraph.append(' ')
                        }
                        paragraph.append(escapeMarkdown(text))
                    }
                }
                previous = line
                index++
            }
            flush()
        }
        return out.toString().trimEnd() + "\n"
    }

    private fun escapeMarkdown(text: String) = text.replace(Regex("([\\\\`*_\\[\\]])"), "\\\\$1")

    private fun markdownTable(rows: List<List<String>>): String {
        val width = rows.maxOf { it.size }
        fun row(cells: List<String>) = cells.plus(List(width - cells.size) { "" })
            .joinToString(" | ", "| ", " |") { it.replace("|", "\\|") }
        return buildString {
            appendLine(row(rows.first()))
            appendLine(List(width) { "---" }.joinToString(" | ", "| ", " |"))
            rows.drop(1).forEach { appendLine(row(it)) }
        }
    }

    // ------------------------------------------------------------------ json

    /** Every line with where it is on its page, in points from the top left, and its type size. */
    fun json(pages: List<List<TextLine>>, sizes: List<Pair<Float, Float>>): String = Json.write(
        linkedMapOf(
            "pages" to pages.mapIndexed { index, lines ->
                linkedMapOf(
                    "number" to index + 1,
                    "width" to round(sizes.getOrNull(index)?.first ?: 0f),
                    "height" to round(sizes.getOrNull(index)?.second ?: 0f),
                    "lines" to lines.map { line ->
                        linkedMapOf(
                            "text" to line.text,
                            "x" to round(line.left),
                            "y" to round(line.top),
                            "width" to round(line.right - line.left),
                            "height" to round(line.bottom - line.top),
                            "size" to round(line.size),
                        )
                    },
                )
            },
        ),
    )

    private fun round(value: Float) = (value * 10).roundToInt() / 10.0

    // ---------------------------------------------------------------- tables

    /** Every table in the document, page by page. */
    fun tables(pages: List<List<TextLine>>): List<FoundTable> =
        pages.flatMapIndexed { index, lines -> tablesOn(lines).map { FoundTable(index + 1, it.second) } }

    /**
     * Tables on one page, each as (first line's index, rows). A table is three
     * or more lines in a row, each split into at least two cells, whose cells
     * start at the same few places across the page.
     */
    internal fun tablesOn(lines: List<TextLine>): List<Pair<Int, List<List<String>>>> {
        val found = mutableListOf<Pair<Int, List<List<String>>>>()
        var start = 0
        while (start < lines.size) {
            if (lines[start].cells.size < 2) {
                start++
                continue
            }
            var end = start
            while (end + 1 < lines.size && lines[end + 1].cells.size >= 2 && closeBelow(lines[end], lines[end + 1])) end++
            val run = lines.subList(start, end + 1)
            val columns = columnsOf(run)
            if (run.size >= 3 && columns != null) {
                found += start to run.map { line -> place(line, columns) }
            }
            start = end + 1
        }
        return found
    }

    /** Rows of a table sit within a couple of lines of each other. */
    private fun closeBelow(above: TextLine, below: TextLine) = below.top - above.bottom < above.size * 2.5f

    /**
     * Where the columns start, if the run's cells line up: each cell's left edge
     * near one of a few shared positions, most of them used on most rows.
     */
    private fun columnsOf(run: List<TextLine>): List<Float>? {
        val tolerance = run.map { it.size }.average().toFloat() * 1.2f
        val starts = mutableListOf<MutableList<Float>>()
        for (cell in run.flatMap { it.cells }) {
            val near = starts.firstOrNull { abs(it.average().toFloat() - cell.left) < tolerance }
            if (near != null) near += cell.left else starts += mutableListOf(cell.left)
        }
        // A column is a position most rows have a cell at.
        val columns = starts.filter { it.size * 2 >= run.size }.map { it.average().toFloat() }.sorted()
        if (columns.size < 2) return null
        val aligned = run.count { line -> line.cells.count { cell -> columns.any { abs(it - cell.left) < tolerance } } >= 2 }
        return columns.takeIf { aligned * 4 >= run.size * 3 }
    }

    /** A line's cells in their columns; a cell between columns joins the one before it. */
    private fun place(line: TextLine, columns: List<Float>): List<String> {
        val row = MutableList(columns.size) { "" }
        line.cells.forEach { cell ->
            val column = columns.indexOfLast { it <= cell.left + 2f }.coerceAtLeast(0)
            row[column] = listOf(row[column], cell.text).filter { it.isNotEmpty() }.joinToString(" ")
        }
        return row
    }

    /** A table as CSV, quoted where it has to be, as spreadsheets read it. */
    fun csv(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            appendLine(
                row.joinToString(",") { cell ->
                    if (cell.any { it == ',' || it == '"' || it == '\n' } || cell != cell.trim()) {
                        "\"" + cell.replace("\"", "\"\"") + "\""
                    } else {
                        cell
                    }
                },
            )
        }
    }
}
