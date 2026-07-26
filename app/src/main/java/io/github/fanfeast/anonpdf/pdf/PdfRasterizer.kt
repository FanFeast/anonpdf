package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.SizeF
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns PDF pages into bitmaps using the renderer built into Android.
 *
 * We deliberately use the platform renderer rather than a bundled engine: it is
 * already on the device, it is sandboxed by the OS, and it keeps the APK small.
 *
 * [PdfRenderer] permits exactly one open page at a time and is not thread safe,
 * so every access funnels through [mutex].
 */
class PdfRasterizer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()

    val pageCount: Int = renderer.pageCount

    /** Page dimensions in PostScript points (1/72 inch). */
    suspend fun pageSize(index: Int): SizeF = mutex.withLock {
        renderer.openPage(index).use { page ->
            SizeF(page.width.toFloat(), page.height.toFloat())
        }
    }

    suspend fun pageSizes(): List<SizeF> = mutex.withLock {
        (0 until renderer.pageCount).map { index ->
            renderer.openPage(index).use { page ->
                SizeF(page.width.toFloat(), page.height.toFloat())
            }
        }
    }

    /** Renders [index] scaled so the result is [targetWidthPx] wide. */
    suspend fun renderByWidth(index: Int, targetWidthPx: Int): Bitmap = mutex.withLock {
        renderer.openPage(index).use { page ->
            val scale = targetWidthPx.toFloat() / page.width.toFloat()
            val height = max(1, (page.height * scale).roundToInt())
            drawPage(page, max(1, targetWidthPx), height)
        }
    }

    /** Renders [index] at a physical resolution, used by the export tools. */
    suspend fun renderAtDpi(index: Int, dpi: Int): Bitmap = mutex.withLock {
        renderer.openPage(index).use { page ->
            val scale = dpi / POINTS_PER_INCH
            val width = max(1, (page.width * scale).roundToInt())
            val height = max(1, (page.height * scale).roundToInt())
            drawPage(page, width, height)
        }
    }

    private fun drawPage(page: PdfRenderer.Page, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        // PdfRenderer composites onto whatever is already there, and a PDF page is
        // conceptually paper, so start from white instead of transparent.
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val POINTS_PER_INCH = 72f

        /**
         * @throws java.io.IOException if the file is not a PDF, or is encrypted with
         * a scheme the platform renderer refuses to open.
         */
        fun open(file: File): PdfRasterizer {
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            return try {
                PdfRasterizer(descriptor, PdfRenderer(descriptor))
            } catch (t: Throwable) {
                runCatching { descriptor.close() }
                throw t
            }
        }
    }
}
