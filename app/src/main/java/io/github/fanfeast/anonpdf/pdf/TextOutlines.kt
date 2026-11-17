package io.github.fanfeast.anonpdf.pdf

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

// Letter shapes for PdfText, from Android's own text engine, which picks a
// system font for each script and shapes it: Devanagari's joined letters come
// out joined, the same as anywhere else on the phone.

private fun paint(bold: Boolean, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    textSize = size
}

/**
 * Whether every character has a shape to draw. Not just a glyph: colour emoji
 * have glyphs that are pictures, with no outline to put in a PDF.
 */
internal fun canOutlineText(text: String): Boolean {
    val paint = paint(false, 12f)
    val shape = Path()
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val length = Character.charCount(codePoint)
        if (!Character.isWhitespace(codePoint)) {
            if (!paint.hasGlyph(text.substring(index, index + length))) return false
            paint.getTextPath(text, index, index + length, 0f, 0f, shape)
            if (shape.isEmpty) return false
        }
        index += length
    }
    return true
}

internal fun outlineText(text: String, bold: Boolean, fontSize: Float): TextOutline? {
    if (text.isEmpty()) return null
    val paint = paint(bold, fontSize)
    val glyphs = Path()
    paint.getTextPath(text, 0, text.length, 0f, 0f, glyphs)
    // Flattened into short lines, finer than anything visible at this size.
    // approximate() returns (fraction, x, y) triples; a new contour starts
    // where two neighbours share a fraction, the jump between them.
    val points = glyphs.approximate(fontSize / 400f)
    val path = mutableListOf<PathCommand>()
    var index = 0
    while (index < points.size) {
        val fraction = points[index]
        val x = points[index + 1]
        // Android's y runs down from the baseline; PDF's runs up.
        val y = -points[index + 2]
        val startsContour = index == 0 || fraction == points[index - 3]
        if (startsContour) {
            if (index > 0) path += PathCommand.Close
            path += PathCommand.MoveTo(x, y)
        } else {
            path += PathCommand.LineTo(x, y)
        }
        index += 3
    }
    if (path.isNotEmpty()) path += PathCommand.Close
    return TextOutline(paint.measureText(text), path)
}
