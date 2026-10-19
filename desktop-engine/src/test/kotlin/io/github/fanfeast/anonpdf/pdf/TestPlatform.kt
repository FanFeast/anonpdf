package io.github.fanfeast.anonpdf.pdf

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files

/** What the shared engine tests (shared-engine/src/deviceTest) need from desktop. */
object TestPlatform {
    const val WHITE = 0xFFFFFFFF.toInt()

    private val root: File by lazy { Files.createTempDirectory("anonpdf-test").toFile() }

    fun scratchDir(name: String): File = File(root, name)
}

/** The pixel at [x], [y] as packed ARGB. */
fun PageImage.argbAt(x: Int, y: Int): Int = getRGB(x, y)

fun PageImage.scaledTo(width: Int, height: Int): PageImage {
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = scaled.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.drawImage(this, 0, 0, width, height, null)
    graphics.dispose()
    return scaled
}
