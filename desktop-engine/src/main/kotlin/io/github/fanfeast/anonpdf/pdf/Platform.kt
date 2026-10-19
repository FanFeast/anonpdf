package io.github.fanfeast.anonpdf.pdf

import java.awt.image.BufferedImage

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
