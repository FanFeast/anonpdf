package io.github.fanfeast.anonpdf.pdf

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Reads pictures for Images to PDF: upright, and no bigger than the engine wants.
 *
 * ImageIO ignores a JPEG's EXIF orientation tag, and phones store most photos
 * sideways with that tag saying which way is up, so it is read here and applied.
 */
internal object ImageFiles {

    /** Extensions the file dialog offers; ImageIO reads all of them without plugins. */
    val extensions = setOf("jpg", "jpeg", "png", "gif", "bmp")

    fun decode(file: File, maxEdge: Int = PdfConvert.MAX_SOURCE_IMAGE_EDGE): BufferedImage? = runCatching {
        ImageIO.createImageInputStream(file).use { input ->
            val reader: ImageReader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: return null
            try {
                reader.input = input
                val longestEdge = maxOf(reader.getWidth(0), reader.getHeight(0))
                val parameters = reader.defaultReadParam
                if (longestEdge > maxEdge) {
                    // Subsampling while reading: a 50 MP photo never sits in memory whole.
                    val step = (longestEdge + maxEdge - 1) / maxEdge
                    parameters.setSourceSubsampling(step, step, 0, 0)
                }
                reader.read(0, parameters)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()?.let { upright(it, orientationOf(file)) }

    /**
     * The EXIF orientation of a JPEG, 1 (as stored) to 8; 1 for anything else.
     *
     * Walks the JPEG markers to the APP1 "Exif" segment and reads tag 0x0112 from
     * its first directory. Anything unexpected means "as stored".
     */
    fun orientationOf(file: File): Int = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readUnsignedShort() != 0xFFD8) return 1
            var marker = input.readUnsignedShort()
            // Start of scan means the headers are over and there was no EXIF.
            while (marker and 0xFF00 == 0xFF00 && marker != 0xFFDA) {
                val length = input.readUnsignedShort() - 2
                if (marker == 0xFFE1) {
                    // APP1 also carries XMP, so only an "Exif" one settles it.
                    exifOrientation(ByteArray(length).also(input::readFully))?.let { return it }
                } else {
                    input.skipNBytes(length.toLong())
                }
                marker = input.readUnsignedShort()
            }
            1
        }
    }.getOrDefault(1)

    /** Null if [segment] is not EXIF; otherwise its orientation, 1 if it gives none. */
    private fun exifOrientation(segment: ByteArray): Int? {
        if (segment.size < 14 || String(segment, 0, 4, Charsets.US_ASCII) != "Exif") return null
        val tiff = 6
        val little = segment[tiff] == 'I'.code.toByte()
        fun u16(at: Int): Int {
            val a = segment[at].toInt() and 0xFF
            val b = segment[at + 1].toInt() and 0xFF
            return if (little) a or (b shl 8) else (a shl 8) or b
        }
        fun u32(at: Int): Int = if (little) u16(at) or (u16(at + 2) shl 16) else (u16(at) shl 16) or u16(at + 2)
        val directory = tiff + u32(tiff + 4)
        if (directory + 2 > segment.size) return 1
        val entries = u16(directory)
        for (index in 0 until entries) {
            val entry = directory + 2 + index * 12
            if (entry + 12 > segment.size) return 1
            if (u16(entry) == 0x0112) return u16(entry + 8).takeIf { it in 1..8 } ?: 1
        }
        return 1
    }

    /** Applies EXIF [orientation] so the picture shows the way the camera was held. */
    fun upright(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return image
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val transform = AffineTransform()
        // Each case maps the stored pixels onto the upright picture.
        when (orientation) {
            2 -> { transform.translate(w, 0.0); transform.scale(-1.0, 1.0) }
            3 -> { transform.translate(w, h); transform.rotate(Math.PI) }
            4 -> { transform.translate(0.0, h); transform.scale(1.0, -1.0) }
            5 -> { transform.rotate(Math.PI / 2); transform.scale(1.0, -1.0) }
            6 -> { transform.translate(h, 0.0); transform.rotate(Math.PI / 2) }
            7 -> { transform.scale(-1.0, 1.0); transform.translate(-h, 0.0); transform.translate(0.0, w); transform.rotate(3 * Math.PI / 2) }
            8 -> { transform.translate(0.0, w); transform.rotate(3 * Math.PI / 2) }
        }
        val turned = orientation >= 5
        val target = BufferedImage(
            if (turned) image.height else image.width,
            if (turned) image.width else image.height,
            if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
        )
        // drawImage rather than AffineTransformOp, which refuses some of the pixel
        // layouts ImageIO decodes JPEGs into.
        val graphics = target.createGraphics()
        try {
            graphics.drawImage(image, transform, null)
        } finally {
            graphics.dispose()
        }
        return target
    }
}
