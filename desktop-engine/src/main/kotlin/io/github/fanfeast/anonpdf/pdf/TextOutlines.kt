package io.github.fanfeast.anonpdf.pdf

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.PathIterator

// Letter shapes for PdfText, from Java's own text engine. Its logical SansSerif
// font falls back through the system's fonts, so it finds one for most scripts,
// and TextLayout shapes them: Devanagari's joined letters come out joined.

private val context = FontRenderContext(null, true, true)

private fun sansSerif(bold: Boolean, size: Float) =
    Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)

internal fun canOutlineText(text: String): Boolean =
    text.isEmpty() || sansSerif(false, 12f).canDisplayUpTo(text) == -1

internal fun outlineText(text: String, bold: Boolean, fontSize: Float): TextOutline? {
    if (text.isEmpty()) return null
    val layout = TextLayout(text, sansSerif(bold, fontSize), context)
    val path = mutableListOf<PathCommand>()
    val iterator = layout.getOutline(null).getPathIterator(null)
    val points = FloatArray(6)
    var lastX = 0f
    var lastY = 0f
    // Java's y runs down from the baseline; PDF's runs up.
    while (!iterator.isDone) {
        when (iterator.currentSegment(points)) {
            PathIterator.SEG_MOVETO -> path += PathCommand.MoveTo(points[0], -points[1])
            PathIterator.SEG_LINETO -> path += PathCommand.LineTo(points[0], -points[1])
            PathIterator.SEG_QUADTO -> {
                // A quadratic is the cubic whose controls sit two thirds of the way to its one.
                val (cx, cy) = points[0] to points[1]
                val (x, y) = points[2] to points[3]
                path += PathCommand.CurveTo(
                    lastX + 2f / 3f * (cx - lastX), -(lastY + 2f / 3f * (cy - lastY)),
                    x + 2f / 3f * (cx - x), -(y + 2f / 3f * (cy - y)),
                    x, -y,
                )
            }
            PathIterator.SEG_CUBICTO -> path += PathCommand.CurveTo(
                points[0], -points[1], points[2], -points[3], points[4], -points[5],
            )
            PathIterator.SEG_CLOSE -> path += PathCommand.Close
        }
        when (val last = path.lastOrNull()) {
            is PathCommand.MoveTo -> { lastX = last.x; lastY = -last.y }
            is PathCommand.LineTo -> { lastX = last.x; lastY = -last.y }
            is PathCommand.CurveTo -> { lastX = last.x3; lastY = -last.y3 }
            else -> Unit
        }
        iterator.next()
    }
    return TextOutline(layout.advance, path)
}
