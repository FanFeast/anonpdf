package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderCapTest {

    private val a4 = 595 to 842
    private val a0 = 2384 to 3370

    private fun pixels(page: Pair<Int, Int>, scale: Float): Double =
        page.first * scale.toDouble() * page.second * scale

    @Test
    fun `a4 at the highest export dpi is left alone`() {
        val scale = 400 / 72f
        assertEquals(scale, PdfRasterizer.cappedScale(a4.first, a4.second, scale), 0f)
    }

    @Test
    fun `a0 at the highest export dpi is scaled down to the cap`() {
        val requested = 400 / 72f
        val capped = PdfRasterizer.cappedScale(a0.first, a0.second, requested)
        assertTrue(capped < requested)
        assertTrue(pixels(a0, capped) <= PdfRasterizer.MAX_RENDER_PIXELS * 1.001)
        // Reduced just enough, not more.
        assertTrue(pixels(a0, capped) > PdfRasterizer.MAX_RENDER_PIXELS * 0.99)
    }
}
