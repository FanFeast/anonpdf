package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Phone photos are stored sideways with an EXIF tag saying which way is up.
 * ImageIO ignores the tag, so ImageFiles applies it, or Images to PDF would put
 * every portrait photo on its side.
 */
class ImageFilesTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("image-files").toFile()
    }

    /**
     * A 40 x 20 JPEG, white with a red block in its stored top-left corner,
     * tagged with [orientation] (null for no EXIF at all). [order] is the byte
     * order of the EXIF block: cameras write both.
     */
    private fun photo(orientation: Int?, order: ByteOrder = ByteOrder.BIG_ENDIAN, xmpFirst: Boolean = false): File {
        val image = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 20) for (x in 0 until 40) {
            image.setRGB(x, y, if (x < 10 && y < 10) RED else WHITE)
        }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpeg", it) }.toByteArray()
        val segments = ByteArrayOutputStream()
        if (xmpFirst) segments.write(app1("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>".toByteArray()))
        if (orientation != null) segments.write(app1(exif(orientation, order)))
        // Right after the start-of-image marker, where cameras put them.
        val file = File(dir, "photo-${System.nanoTime()}.jpg")
        file.writeBytes(jpeg.copyOfRange(0, 2) + segments.toByteArray() + jpeg.copyOfRange(2, jpeg.size))
        return file
    }

    private fun app1(payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    /** "Exif\0\0", a TIFF header, and one directory holding only the orientation tag. */
    private fun exif(orientation: Int, order: ByteOrder): ByteArray {
        val tiff = ByteBuffer.allocate(26).order(order)
        tiff.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        tiff.putShort(42)
        tiff.putInt(8) // first directory right after the header
        tiff.putShort(1) // one entry
        tiff.putShort(0x0112).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0)
        tiff.putInt(0) // no next directory
        return "Exif\u0000\u0000".toByteArray() + tiff.array()
    }

    private fun BufferedImage.isRedAt(x: Int, y: Int): Boolean {
        val rgb = getRGB(x, y)
        return (rgb shr 16 and 0xFF) > 200 && (rgb shr 8 and 0xFF) < 80
    }

    @Test
    fun readsTheOrientationTagInEitherByteOrder() {
        assertEquals(6, ImageFiles.orientationOf(photo(6)))
        assertEquals(8, ImageFiles.orientationOf(photo(8, ByteOrder.LITTLE_ENDIAN)))
        assertEquals(1, ImageFiles.orientationOf(photo(null)))
    }

    @Test
    fun findsTheTagBehindAnXmpBlock() {
        assertEquals(3, ImageFiles.orientationOf(photo(3, xmpFirst = true)))
    }

    @Test
    fun aFileThatIsNotAJpegCountsAsUpright() {
        val png = File(dir, "plain.png").also { ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", it) }
        assertEquals(1, ImageFiles.orientationOf(png))
    }

    @Test
    fun eachOrientationPutsTheStoredCornerWhereTheCameraSawIt() {
        // Where the stored top-left corner belongs once the picture is upright.
        val expected = mapOf(
            1 to Corner.TOP_LEFT,
            2 to Corner.TOP_RIGHT,
            3 to Corner.BOTTOM_RIGHT,
            4 to Corner.BOTTOM_LEFT,
            5 to Corner.TOP_LEFT,
            6 to Corner.TOP_RIGHT,
            7 to Corner.BOTTOM_RIGHT,
            8 to Corner.BOTTOM_LEFT,
        )
        expected.forEach { (orientation, corner) ->
            val decoded = ImageFiles.decode(photo(orientation))
            assertNotNull(decoded)
            val turned = orientation >= 5
            assertEquals("width for $orientation", if (turned) 20 else 40, decoded!!.width)
            assertEquals("height for $orientation", if (turned) 40 else 20, decoded.height)
            val (x, y) = corner.sample(decoded.width, decoded.height)
            assertTrue("orientation $orientation: red block at $corner", decoded.isRedAt(x, y))
        }
    }

    @Test
    fun aHugeImageIsReadSmaller() {
        val decoded = ImageFiles.decode(photo(6), maxEdge = 16)
        assertNotNull(decoded)
        assertTrue("longest edge ${maxOf(decoded!!.width, decoded.height)}", maxOf(decoded.width, decoded.height) <= 16)
        assertTrue("still upright", decoded.height > decoded.width)
    }

    private enum class Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** A pixel well inside the 10 x 10 block, clear of JPEG ringing at its edge. */
        fun sample(width: Int, height: Int): Pair<Int, Int> = when (this) {
            TOP_LEFT -> 3 to 3
            TOP_RIGHT -> width - 4 to 3
            BOTTOM_LEFT -> 3 to height - 4
            BOTTOM_RIGHT -> width - 4 to height - 4
        }
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
