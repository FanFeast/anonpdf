package io.github.fanfeast.anonpdf.pdf

/**
 * A rectangle on a page, in fractions of the page **as displayed**, measured from
 * the top-left.
 *
 * Fractions rather than points so a mark keeps its place if the page is later
 * cropped, rotated or refitted onto a different sheet.
 */
data class MarkRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isDegenerate: Boolean get() = width <= 0.001f || height <= 0.001f

    /** Normalised so left < right and top < bottom, whichever way it was dragged. */
    fun tidied() = MarkRect(
        left = minOf(left, right).coerceIn(0f, 1f),
        top = minOf(top, bottom).coerceIn(0f, 1f),
        right = maxOf(left, right).coerceIn(0f, 1f),
        bottom = maxOf(top, bottom).coerceIn(0f, 1f),
    )

    fun movedBy(dx: Float, dy: Float): MarkRect {
        val shiftX = dx.coerceIn(-left, 1f - right)
        val shiftY = dy.coerceIn(-top, 1f - bottom)
        return MarkRect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }
}

/**
 * Content added on top of a page.
 *
 * Marks belong to a *source* page, so they travel with it when pages are
 * reordered, and are drawn after the page has been rotated, cropped and resized —
 * which is what lets a white-out block reliably cover what is underneath.
 */
sealed interface PageMark {
    val id: Long
    val sourceIndex: Int

    /** For the pending-edits list. */
    fun describe(): String
}

/** A block of text laid on the page. Newlines start a new line. */
data class TextMark(
    override val id: Long,
    override val sourceIndex: Int,
    val text: String,
    /** Top-left anchor, in page fractions. */
    val left: Float,
    val top: Float,
    /** Point size on the finished page. */
    val fontSize: Float = 12f,
    val color: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
) : PageMark {
    override fun describe(): String {
        val snippet = text.lineSequence().firstOrNull()?.take(24).orEmpty()
        return "Text \"$snippet\" · page ${sourceIndex + 1}"
    }
}

/**
 * A filled rectangle: opaque to cover something up, translucent to highlight it.
 *
 * Covering and retyping is how a PDF's existing text gets "changed" — the original
 * glyphs cannot be rewritten in place without the document's fonts, so an opaque
 * block plus a [TextMark] is the honest way to do it.
 */
data class FillMark(
    override val id: Long,
    override val sourceIndex: Int,
    val rect: MarkRect,
    val color: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1f,
) : PageMark {
    val isHighlight: Boolean get() = opacity < 1f

    override fun describe() =
        (if (isHighlight) "Highlight" else "White-out") + " · page ${sourceIndex + 1}"
}

/** Freehand ink. Points are page fractions, so strokes survive a resize. */
data class InkMark(
    override val id: Long,
    override val sourceIndex: Int,
    val strokes: List<List<Pair<Float, Float>>>,
    val color: Int = 0xFF1A1A1A.toInt(),
    /** Stroke width as a fraction of the page's shorter side. */
    val widthRatio: Float = 0.004f,
) : PageMark {
    override fun describe() =
        "Drawing (${strokes.size} stroke${if (strokes.size == 1) "" else "s"}) · " +
            "page ${sourceIndex + 1}"
}
