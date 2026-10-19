package io.github.fanfeast.anonpdf.pdf

import kotlin.math.sqrt

/** How large a rendered page may get, whichever renderer the platform uses. */
object RenderLimits {
    const val POINTS_PER_INCH = 72f

    /**
     * The most pixels one rendered page may have: 40 MP, about 160 MB as
     * ARGB_8888. A4 at 400 dpi is ~15 MP and fits untouched; an A0 drawing at
     * 400 dpi would be ~250 MP (~1 GB) and run the app out of memory.
     */
    const val MAX_RENDER_PIXELS = 40_000_000L

    /**
     * [requested] scale, reduced just enough that a [widthPt] x [heightPt]
     * page stays within [MAX_RENDER_PIXELS].
     */
    fun cappedScale(widthPt: Int, heightPt: Int, requested: Float): Float {
        val pixels = widthPt.toDouble() * heightPt * requested * requested
        if (pixels <= MAX_RENDER_PIXELS) return requested
        return (requested * sqrt(MAX_RENDER_PIXELS / pixels)).toFloat()
    }
}
