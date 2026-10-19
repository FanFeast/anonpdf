package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap

// The shared engine (shared-engine/) names these types; each platform says what
// they are. On Android they are the platform's own, so the UI passes them
// straight through.

/** A rendered page, or any raster the engine embeds into a PDF. */
typealias PageImage = Bitmap

/** A width and height in PDF points. */
typealias SizeF = android.util.SizeF

/** Marks an image rendered onto white as opaque, so it is embedded without an alpha channel. */
internal fun PageImage.dropAlpha() = setHasAlpha(false)

/** Frees the image's pixels now rather than at the next garbage collection. */
internal fun PageImage.release() = recycle()
