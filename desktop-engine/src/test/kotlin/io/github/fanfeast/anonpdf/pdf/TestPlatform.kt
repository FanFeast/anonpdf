package io.github.fanfeast.anonpdf.pdf

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** What the shared engine tests (shared-engine/src/deviceTest) need from desktop. */
object TestPlatform {
    const val WHITE = 0xFFFFFFFF.toInt()

    private val root: File by lazy { Files.createTempDirectory("anonpdf-test").toFile() }

    fun scratchDir(name: String): File = File(root, name)

    /** An image from packed ARGB [pixels], row by row. */
    fun imageOf(width: Int, height: Int, pixels: IntArray, hasAlpha: Boolean): PageImage {
        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        return BufferedImage(width, height, type).apply { setRGB(0, 0, width, height, pixels, 0, width) }
    }

    fun readImage(file: File): PageImage? = ImageIO.read(file)
}

/** The pixel at [x], [y] as packed ARGB. */
fun PageImage.argbAt(x: Int, y: Int): Int = getRGB(x, y)
