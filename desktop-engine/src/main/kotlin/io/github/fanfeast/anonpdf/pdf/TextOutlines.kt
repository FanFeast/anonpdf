package io.github.fanfeast.anonpdf.pdf

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.PathIterator
import java.text.AttributedString

// Letter shapes for PdfText, from Java's own text engine, which shapes them:
// Devanagari's joined letters come out joined.
//
// Java's logical SansSerif only falls back to the few fonts its own config
// names, which on Windows and on a plain Linux misses ones the system has for
// Hindi or Chinese. So each stretch of text SansSerif cannot draw is given an
// installed font that can, chosen from all of them.

private val context = FontRenderContext(null, true, true)

private val sansSerif = Font(Font.SANS_SERIF, Font.PLAIN, 1)

/** Fonts known to cover whole scripts well, tried before the rest, best first. */
private val preferred = listOf(
    "Noto Sans", "Nirmala UI", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans SC",
    "Noto Sans Devanagari", "Mangal", "SimSun", "Segoe UI", "Segoe UI Symbol",
    "Segoe UI Emoji", "DejaVu Sans", "FreeSans", "Arial Unicode MS",
)

private val installed: List<Font> by lazy {
    val all = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
        .filter { it.style == Font.PLAIN && !it.fontName.contains("Bold", ignoreCase = true) }
    val byFamily = all.groupBy { it.family }
    preferred.flatMap { byFamily[it].orEmpty() } + all.filter { it.family !in preferred }
}

private val chosen = HashMap<Int, Font?>()

/** A font with a glyph for [codePoint]: SansSerif if it has one, else the first installed font that does. */
private fun fontFor(codePoint: Int): Font? = synchronized(chosen) {
    chosen.getOrPut(codePoint) {
        if (sansSerif.canDisplay(codePoint)) sansSerif else installed.firstOrNull { it.canDisplay(codePoint) }
    }
}

private fun isMark(codePoint: Int) = when (Character.getType(codePoint).toByte()) {
    Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
    Character.FORMAT,
    -> true
    else -> false
}

/** [text] cut into stretches that each draw in one font, or null if some character has none. */
private fun runs(text: String): List<Pair<IntRange, Font>>? {
    val runs = mutableListOf<Pair<IntRange, Font>>()
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val end = index + Character.charCount(codePoint)
        val previous = runs.lastOrNull()
        // Spaces and marks stay with the letters before them, so a word is shaped
        // in one font: a vowel sign in another font would not join its consonant.
        val font = if (previous != null && (Character.isWhitespace(codePoint) || isMark(codePoint))) {
            previous.second
        } else if (Character.isWhitespace(codePoint)) {
            sansSerif
        } else {
            fontFor(codePoint) ?: return null
        }
        if (previous != null && previous.second == font) {
            runs[runs.lastIndex] = (previous.first.first until end) to font
        } else {
            runs += (index until end) to font
        }
        index = end
    }
    return runs
}

internal fun canOutlineText(text: String): Boolean = runs(text) != null

internal fun outlineText(text: String, bold: Boolean, fontSize: Float): TextOutline? {
    if (text.isEmpty()) return null
    val runs = runs(text) ?: return null
    val styled = AttributedString(text)
    for ((range, font) in runs) {
        val sized = font.deriveFont(if (bold) Font.BOLD else Font.PLAIN, fontSize)
        styled.addAttribute(TextAttribute.FONT, sized, range.first, range.last + 1)
    }
    val layout = TextLayout(styled.iterator, context)
    val path = mutableListOf<PathCommand>()
    val iterator = layout.getOutline(null).getPathIterator(null)
    val points = FloatArray(6)
    var lastX = 0f
    var lastY = 0f
    // Java's y runs down from the baseline; PDF's runs up.
    while (!iterator.isDone) {
        when (iterator.currentSegment(points)) {
            PathIterator.SEG_MOVETO -> {
                path += PathCommand.MoveTo(points[0], -points[1])
                lastX = points[0]
                lastY = points[1]
            }
            PathIterator.SEG_LINETO -> {
                path += PathCommand.LineTo(points[0], -points[1])
                lastX = points[0]
                lastY = points[1]
            }
            PathIterator.SEG_QUADTO -> {
                // A quadratic is the cubic whose controls sit two thirds of the way to its one.
                val (cx, cy) = points[0] to points[1]
                val (x, y) = points[2] to points[3]
                path += PathCommand.CurveTo(
                    lastX + 2f / 3f * (cx - lastX), -(lastY + 2f / 3f * (cy - lastY)),
                    x + 2f / 3f * (cx - x), -(y + 2f / 3f * (cy - y)),
                    x, -y,
                )
                lastX = x
                lastY = y
            }
            PathIterator.SEG_CUBICTO -> {
                path += PathCommand.CurveTo(
                    points[0], -points[1], points[2], -points[3], points[4], -points[5],
                )
                lastX = points[4]
                lastY = points[5]
            }
            PathIterator.SEG_CLOSE -> path += PathCommand.Close
        }
        iterator.next()
    }
    return TextOutline(layout.advance, path)
}
