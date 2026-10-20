package io.github.fanfeast.anonpdf.ui.sign

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The box a signature's ink occupies, in *pad units*: fractions of the pad's
 * width on both axes.
 *
 * One unit for both axes is the point. The pad is much wider than it is tall, so
 * measuring x against the width and y against the height would make a square
 * drawn on it come out about twice as tall as it is wide. Measured against the
 * width alone, the proportions the user drew are the proportions that get saved.
 */
internal data class SignatureFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    /** Pixel size of a bitmap [targetWidth] wide with the drawing's proportions. */
    fun bitmapSize(targetWidth: Int): Pair<Int, Int> =
        targetWidth to max(1, (targetWidth * height / width).roundToInt())

    fun mapX(x: Float, targetWidth: Int): Float = (x - left) / width * targetWidth
    fun mapY(y: Float, targetWidth: Int): Float = (y - top) / width * targetWidth

    companion object {
        /** Margin so round caps and dots at the edge of the ink are not clipped. */
        const val PAD = 0.04f

        /** Smallest extent on either axis, so a lone dot still has a size. */
        private const val MIN_SPAN = 0.02f

        /** Null when there is no ink at all. */
        fun of(points: List<Pair<Float, Float>>): SignatureFrame? {
            if (points.isEmpty()) return null
            val minX = points.minOf { it.first } - PAD
            val minY = points.minOf { it.second } - PAD
            val maxX = points.maxOf { it.first } + PAD
            val maxY = points.maxOf { it.second } + PAD
            return SignatureFrame(
                left = minX,
                top = minY,
                width = max(MIN_SPAN, maxX - minX),
                height = max(MIN_SPAN, maxY - minY),
            )
        }

        /** A touch at ([x], [y]) px on a pad [widthPx] x [heightPx], in pad units. */
        fun padPoint(x: Float, y: Float, widthPx: Int, heightPx: Int): Pair<Float, Float> {
            if (widthPx <= 0) return 0f to 0f
            val w = widthPx.toFloat()
            return (x / w).coerceIn(0f, 1f) to (y / w).coerceIn(0f, heightPx / w)
        }
    }
}
