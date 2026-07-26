package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap

/** Reports 0f..1f completion so long operations can drive a progress bar. */
typealias Progress = (Float) -> Unit

data class PdfMeta(
    val pageCount: Int,
    val encrypted: Boolean,
    val title: String?,
    val author: String?,
    val producer: String?,
)

/**
 * One output page: which source page it comes from, and how much to turn it.
 *
 * A list of these expresses reordering, deletion (leave the page out) and
 * rotation in a single operation, which is exactly what the Organise screen
 * produces when the user taps Apply.
 */
data class PagePlan(
    val sourceIndex: Int,
    val rotationDelta: Int = 0,
)

enum class WatermarkLayout { DIAGONAL, CENTER, TILED, TOP, BOTTOM }

data class WatermarkOptions(
    val text: String,
    val fontSize: Float = 52f,
    val opacity: Float = 0.22f,
    val layout: WatermarkLayout = WatermarkLayout.DIAGONAL,
    val color: Int = 0xFF757575.toInt(),
    val bold: Boolean = true,
)

enum class NumberPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

data class PageNumberOptions(
    /** Supports the tokens {n} for the current number and {total} for the count. */
    val format: String = "{n}",
    val position: NumberPosition = NumberPosition.BOTTOM_CENTER,
    val startNumber: Int = 1,
    /** Skip a cover page by starting the stamping later in the document. */
    val firstPageIndex: Int = 0,
    val fontSize: Float = 11f,
    val marginPoints: Float = 28f,
    val color: Int = 0xFF000000.toInt(),
)

data class ProtectOptions(
    val userPassword: String,
    val ownerPassword: String,
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = false,
    val allowModifying: Boolean = false,
    val allowAnnotations: Boolean = false,
)

/** Crop amounts as a fraction of each edge, so they survive mixed page sizes. */
data class CropInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val isEmpty: Boolean get() = left == 0f && top == 0f && right == 0f && bottom == 0f
}

/**
 * Where to drop a bitmap (a drawn signature, usually) on a page.
 *
 * Ratios are in the page's *displayed* orientation with the origin at the top
 * left, which is the coordinate space the user was looking at when they tapped.
 */
data class ImageStamp(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val centerXRatio: Float,
    val centerYRatio: Float,
    val widthRatio: Float,
)

enum class ImageFormat(val extension: String, val mime: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
}

enum class PdfPageSizePreset { FIT_IMAGE, A4, LETTER }

data class ImagesToPdfOptions(
    val pageSize: PdfPageSizePreset = PdfPageSizePreset.FIT_IMAGE,
    val marginPoints: Float = 0f,
    val jpegQuality: Float = 0.9f,
)

data class CompressResult(
    val originalBytes: Long,
    val compressedBytes: Long,
) {
    val savedPercent: Int
        get() = if (originalBytes <= 0) 0
        else (((originalBytes - compressedBytes).toDouble() / originalBytes) * 100).toInt()
}

/** Thrown when a document needs a password we do not have. */
class PdfPasswordRequiredException(message: String = "This PDF is password protected.") :
    Exception(message)

/** Thrown when the supplied password does not open the document. */
class PdfWrongPasswordException(message: String = "That password did not work.") :
    Exception(message)
