package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.OutputStream

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

/** Whether the image may have see-through pixels, which JPEG cannot hold. */
internal val PageImage.hasTransparency: Boolean get() = hasAlpha()

internal fun PageImage.scaledTo(width: Int, height: Int): PageImage = scale(width, height)

/** A copy composited over white, or this image itself if it is already opaque. */
internal fun PageImage.flattenedOntoWhite(): PageImage {
    if (!hasAlpha()) return this
    val flattened = createBitmap(width, height)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(this@flattenedOntoWhite, 0f, 0f, null)
    }
    return flattened
}

/** Encodes the image as [format]; [quality] (1–100) applies to JPEG only. */
internal fun PageImage.writeTo(stream: OutputStream, format: ImageFormat, quality: Int = 100) {
    val encoding = when (format) {
        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
    }
    compress(encoding, quality.coerceIn(1, 100), stream)
}
