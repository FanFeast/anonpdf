package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A text or Markdown file as a PDF: wrapped to the page, headings set large,
 * lists indented, code in a fixed-width font.
 *
 * Markdown is read for its shape — headings, lists, quotes, code, rules — and
 * inline marks (emphasis, code spans, links) are read as their plain text, with
 * a link's address after it. A plain text file keeps its own line breaks and
 * spacing, in a fixed-width font, so columns still line up.
 */
object TextToPdf {

    private const val MARGIN = 56f
    private const val BODY = 11f

    /** What some editors put at the start of a UTF-8 file. */
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    /** A block of the document: its lines are wrapped to fit, each prefixed on its first line only. */
    private data class Block(
        val text: String,
        val font: PDFont,
        val size: Float,
        val indent: Float = 0f,
        val marker: String = "",
        val spaceBefore: Float = 0f,
        val wrap: Boolean = true,
        val rule: Boolean = false,
    )

    suspend fun convert(
        input: File,
        output: File,
        markdown: Boolean,
        paper: PaperSize = PaperSize.A4,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        val text = read(input)
        val blocks = if (markdown) markdownBlocks(text) else plainBlocks(text)
        PDDocument().use { document ->
            val box = PDRectangle(minOf(paper.widthPoints, paper.heightPoints), maxOf(paper.widthPoints, paper.heightPoints))
            val width = box.width - 2 * MARGIN
            var page: PDPage? = null
            var stream: PDPageContentStream? = null
            var y = 0f
            fun newPage() {
                stream?.close()
                page = PDPage(box).also(document::addPage)
                stream = PDPageContentStream(document, page)
                y = box.height - MARGIN
            }
            newPage()
            blocks.forEachIndexed { index, block ->
                ensureActive()
                val leading = block.size * 1.4f
                y -= block.spaceBefore
                if (block.rule) {
                    if (y - leading < MARGIN) newPage()
                    stream!!.apply {
                        setStrokingColor(0.75f, 0.75f, 0.75f)
                        moveTo(MARGIN, y - leading / 2)
                        lineTo(MARGIN + width, y - leading / 2)
                        stroke()
                        setStrokingColor(0f, 0f, 0f)
                    }
                    y -= leading
                    return@forEachIndexed
                }
                val markerWidth = if (block.marker.isEmpty()) 0f else PdfText.line(block.marker, block.font, false, block.size).width + 6f
                val room = width - block.indent - markerWidth
                val lines = if (block.wrap) wrap(block.text, block.font, block.size, room) else listOf(block.text)
                lines.forEachIndexed { lineIndex, line ->
                    if (y - leading < MARGIN) newPage()
                    y -= leading
                    val x = MARGIN + block.indent
                    if (lineIndex == 0 && block.marker.isNotEmpty()) {
                        PdfText.line(block.marker, block.font, false, block.size).draw(stream!!, x, y + leading * 0.25f)
                    }
                    PdfText.line(line, block.font, block.font.isBold(), block.size).draw(stream!!, x + markerWidth, y + leading * 0.25f)
                }
                onProgress(0.95f * (index + 1f) / blocks.size)
            }
            stream?.close()
            document.save(output)
        }
        onProgress(1f)
    }

    private fun PDFont.isBold() = this == PDType1Font.HELVETICA_BOLD

    /** UTF-8, or Windows-1252 for an older file that isn't. Too large a file is refused. */
    private fun read(file: File): String {
        require(file.length() <= 8L shl 20) { io.github.fanfeast.anonpdf.i18n.S.textTooLarge }
        val bytes = file.readBytes()
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }.removePrefix(BYTE_ORDER_MARK).replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
    }

    /** Each line as it is; its leading spaces kept as an indent, since drawing a line trims them. */
    private fun plainBlocks(text: String): List<Block> =
        text.split('\n').map { line ->
            val body = line.trimEnd()
            val spaces = body.length - body.trimStart().length
            Block(body.trimStart(), PDType1Font.COURIER, 10f, indent = (spaces * 6f).coerceAtMost(240f))
        }

    private fun markdownBlocks(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += Block(inline(paragraph.toString().trim()), PDType1Font.HELVETICA, BODY, spaceBefore = 6f)
            paragraph.clear()
        }
        var code = false
        for (raw in text.split('\n')) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                flush()
                code = !code
                continue
            }
            if (code) {
                blocks += Block(line, PDType1Font.COURIER, 9.5f, indent = 12f, wrap = false)
                continue
            }
            val trimmed = line.trimStart()
            val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            val bullet = Regex("^[-*+]\\s+(.*)$").find(trimmed)
            val numbered = Regex("^(\\d{1,4})[.)]\\s+(.*)$").find(trimmed)
            val depth = (line.length - trimmed.length) / 2
            when {
                trimmed.isEmpty() -> flush()
                heading != null -> {
                    flush()
                    val level = heading.groupValues[1].length
                    val size = when (level) { 1 -> 20f; 2 -> 16f; 3 -> 13.5f; else -> 12f }
                    blocks += Block(inline(heading.groupValues[2].trimEnd('#', ' ')), PDType1Font.HELVETICA_BOLD, size, spaceBefore = size * 0.6f)
                }
                Regex("^([-*_])(\\s*\\1){2,}$").matches(trimmed) -> {
                    flush()
                    blocks += Block("", PDType1Font.HELVETICA, BODY, rule = true)
                }
                bullet != null -> {
                    flush()
                    blocks += Block(inline(bullet.groupValues[1]), PDType1Font.HELVETICA, BODY, indent = 14f + 14f * depth, marker = "•", spaceBefore = 2f)
                }
                numbered != null -> {
                    flush()
                    blocks += Block(inline(numbered.groupValues[2]), PDType1Font.HELVETICA, BODY, indent = 14f + 14f * depth, marker = "${numbered.groupValues[1]}.", spaceBefore = 2f)
                }
                trimmed.startsWith(">") -> {
                    flush()
                    blocks += Block(inline(trimmed.trimStart('>', ' ')), PDType1Font.HELVETICA_OBLIQUE, BODY, indent = 18f, spaceBefore = 4f)
                }
                else -> paragraph.append(trimmed).append(' ')
            }
        }
        flush()
        return blocks
    }

    /** Inline Markdown as plain text: emphasis and code marks dropped, links as "text (address)". */
    internal fun inline(text: String): String = text
        .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)")) { match ->
            val (label, address) = match.destructured
            if (label == address) label else "$label ($address)"
        }
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
        .replace(Regex("(?<![\\w*])([*_])(?!\\s)(.+?)(?<!\\s)\\1(?![\\w*])"), "$2")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>])"), "$1")

    /**
     * Breaks [text] into lines no wider than [width]: at spaces where it can, and
     * between characters for a word too long for a line, or for a script that
     * doesn't put spaces between words.
     */
    private fun wrap(text: String, font: PDFont, size: Float, width: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        fun fits(candidate: String) = PdfText.line(candidate, font, false, size).width <= width
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            val joined = if (current.isEmpty()) word else "$current $word"
            if (fits(joined)) {
                current = joined
                continue
            }
            if (current.isNotEmpty()) lines += current
            current = ""
            var rest = word
            while (rest.isNotEmpty() && !fits(rest)) {
                // The longest start of it that fits, at least one character.
                var cut = rest.length - 1
                while (cut > 1 && !fits(rest.substring(0, cut))) cut = (cut * 3) / 4
                while (cut + 1 < rest.length && fits(rest.substring(0, cut + 1))) cut++
                lines += rest.substring(0, cut.coerceAtLeast(1))
                rest = rest.substring(cut.coerceAtLeast(1))
            }
            current = rest
        }
        lines += current
        return lines
    }
}
