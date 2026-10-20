package io.github.fanfeast.anonpdf.ui.sign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureFrameTest {

    // A phone-shaped pad: full width, about half as tall.
    private val padWidth = 1000
    private val padHeight = 500

    private fun onPad(vararg px: Pair<Float, Float>) =
        px.map { (x, y) -> SignatureFrame.padPoint(x, y, padWidth, padHeight) }

    private fun aspectOf(points: List<Pair<Float, Float>>): Float {
        val (w, h) = SignatureFrame.of(points)!!.bitmapSize(1200)
        return w / h.toFloat()
    }

    @Test
    fun `a square drawn on a wide pad saves as a square`() {
        val square = onPad(300f to 100f, 600f to 100f, 600f to 400f, 300f to 400f)
        assertEquals(1f, aspectOf(square), 0.01f)
    }

    @Test
    fun `a horizontal line saves wide, not stretched tall`() {
        val line = onPad(100f to 250f, 900f to 250f)
        assertTrue("was ${aspectOf(line)}", aspectOf(line) > 5f)
    }

    @Test
    fun `a signature twice as wide as tall keeps that shape`() {
        // 600 x 300 px of ink on a 2:1 pad. Measuring y against the pad height
        // would report it as 600/1000 by 300/500 — a square.
        val points = onPad(200f to 100f, 800f to 100f, 800f to 400f, 200f to 400f)
        val frame = SignatureFrame.of(points)!!
        val inkAspect = (frame.width - 2 * SignatureFrame.PAD) /
            (frame.height - 2 * SignatureFrame.PAD)
        assertEquals(2f, inkAspect, 0.001f)
    }

    @Test
    fun `mapping puts the ink inside the bitmap`() {
        val points = onPad(100f to 50f, 900f to 450f)
        val frame = SignatureFrame.of(points)!!
        val (w, h) = frame.bitmapSize(1200)
        points.forEach { (x, y) ->
            assertTrue(frame.mapX(x, 1200) in 0f..w.toFloat())
            assertTrue(frame.mapY(y, 1200) in 0f..h.toFloat())
        }
    }

    @Test
    fun `a lone dot still has a size`() {
        val (w, h) = SignatureFrame.of(onPad(500f to 250f))!!.bitmapSize(1200)
        assertEquals(1200, w)
        assertEquals(1200, h)
    }

    @Test
    fun `no ink, no frame`() {
        assertNull(SignatureFrame.of(emptyList()))
    }

    @Test
    fun `pad points use the width as the unit on both axes`() {
        assertEquals(0.5f to 0.25f, SignatureFrame.padPoint(500f, 250f, padWidth, padHeight))
        // Clamped to the pad: y can never exceed height / width.
        assertEquals(1f to 0.5f, SignatureFrame.padPoint(2000f, 900f, padWidth, padHeight))
    }
}
