package io.github.fanfeast.anonpdf.pdf

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// The shared engine (shared-engine/) names these types; each platform says what
// they are. On desktop the images are AWT images, which upstream PDFBox embeds
// and renders directly.

/** A rendered page, or any raster the engine embeds into a PDF. */
typealias PageImage = BufferedImage

/** A width and height in PDF points. */
data class SizeF(val width: Float, val height: Float)

/** Pages are rendered as opaque RGB already, so there is no alpha channel to drop. */
internal fun PageImage.dropAlpha() = Unit

/** Frees the image's pixels now rather than at the next garbage collection. */
internal fun PageImage.release() = flush()

/** Whether the image may have see-through pixels, which JPEG cannot hold. */
internal val PageImage.hasTransparency: Boolean get() = colorModel.hasAlpha()

internal fun PageImage.scaledTo(width: Int, height: Int): PageImage {
    val type = if (hasTransparency) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    return redrawn(width, height, type, background = null)
}

/** A copy composited over white, or this image itself if it is already opaque RGB. */
internal fun PageImage.flattenedOntoWhite(): PageImage {
    // Not just "no alpha": JPEG encoding also wants plain RGB, and a decoded image
    // can be grey, indexed or 16-bit.
    if (!hasTransparency && type == BufferedImage.TYPE_INT_RGB) return this
    return redrawn(width, height, BufferedImage.TYPE_INT_RGB, background = Color.WHITE)
}

/** Encodes the image as [format]; [quality] (1–100) applies to JPEG only. */
internal fun PageImage.writeTo(stream: OutputStream, format: ImageFormat, quality: Int = 100) {
    when (format) {
        ImageFormat.PNG -> check(ImageIO.write(this, "png", stream)) { "No PNG encoder." }
        ImageFormat.JPEG -> {
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            try {
                ImageIO.createImageOutputStream(stream).use { output ->
                    writer.output = output
                    val parameters = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality.coerceIn(1, 100) / 100f
                    }
                    writer.write(null, IIOImage(flattenedOntoWhite(), null, null), parameters)
                }
            } finally {
                writer.dispose()
            }
        }
    }
}

private fun PageImage.redrawn(width: Int, height: Int, type: Int, background: Color?): PageImage {
    val target = BufferedImage(width, height, type)
    val graphics = target.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        if (background != null) {
            graphics.color = background
            graphics.fillRect(0, 0, width, height)
        }
        graphics.drawImage(this, 0, 0, width, height, null)
    } finally {
        graphics.dispose()
    }
    return target
}
