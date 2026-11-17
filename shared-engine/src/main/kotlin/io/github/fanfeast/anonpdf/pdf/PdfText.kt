package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont

/**
 * The outline of a line of text, from the platform's own text engine, in PDF
 * points: the origin on the baseline where the line starts, y pointing up.
 *
 * Each platform's [outlineText] makes these. Taking the shapes rather than a
 * font file is what lets any script be drawn: the system lays out Devanagari's
 * joined letters and picks a font with Chinese in it, which PDFBox can do
 * neither of.
 */
class TextOutline(val advance: Float, val path: List<PathCommand>) {
    val isEmpty: Boolean get() = path.none { it is PathCommand.LineTo || it is PathCommand.CurveTo }
}

sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CurveTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float,
    ) : PathCommand
    data object Close : PathCommand
}

/**
 * Text the engine writes onto pages: watermarks, page numbers, text boxes,
 * form answers.
 */
object PdfText {

    private val winAnsi = java.nio.charset.Charset.forName("windows-1252")

    /**
     * Whether [text] can be drawn as typed. Western text always can; other
     * scripts need a system font that has them, which a missing font or an
     * emoji can still defeat.
     */
    fun canDraw(text: String): Boolean {
        val encoder = winAnsi.newEncoder()
        val visible = text.filterNot { it == '\n' || it == '\r' || it == '\t' }
        return visible.all(encoder::canEncode) || canOutlineText(visible)
    }

    /**
     * One line, ready to measure and draw at [fontSize]. Uses [font] when it
     * covers every character, so ordinary text stays real, selectable text;
     * otherwise the platform's letter shapes; and only if the system has no
     * font for it either, [font] with '?' for what it cannot draw.
     */
    internal fun line(text: String, font: PDFont, bold: Boolean, fontSize: Float): Line {
        val flat = text.replace(Regex("[\\r\\n\\t]"), " ").trim()
        val standard = PdfDraw.sanitizeForFont(flat, font)
        if (standard == flat) return Line.Standard(standard, font, fontSize)
        val outline = runCatching { outlineText(flat, bold, fontSize) }.getOrNull()
        return if (outline != null && !outline.isEmpty) Line.Shapes(outline) else Line.Standard(standard, font, fontSize)
    }

    internal sealed interface Line {
        /** How far the line runs, in points. */
        val width: Float
        val isBlank: Boolean

        /** Draws the line with its baseline starting at [x], [y], in the current fill colour. */
        fun draw(stream: PDPageContentStream, x: Float, y: Float)

        class Standard(val text: String, val font: PDFont, val fontSize: Float) : Line {
            override val width get() = font.getStringWidth(text) / 1000f * fontSize
            override val isBlank get() = text.isBlank()
            override fun draw(stream: PDPageContentStream, x: Float, y: Float) {
                if (text.isEmpty()) return
                stream.beginText()
                stream.setFont(font, fontSize)
                stream.newLineAtOffset(x, y)
                stream.showText(text)
                stream.endText()
            }
        }

        class Shapes(val outline: TextOutline) : Line {
            override val width get() = outline.advance
            override val isBlank get() = outline.isEmpty
            override fun draw(stream: PDPageContentStream, x: Float, y: Float) {
                for (command in outline.path) {
                    when (command) {
                        is PathCommand.MoveTo -> stream.moveTo(x + command.x, y + command.y)
                        is PathCommand.LineTo -> stream.lineTo(x + command.x, y + command.y)
                        is PathCommand.CurveTo -> stream.curveTo(
                            x + command.x1, y + command.y1,
                            x + command.x2, y + command.y2,
                            x + command.x3, y + command.y3,
                        )
                        PathCommand.Close -> stream.closePath()
                    }
                }
                stream.fill()
            }
        }
    }
}
