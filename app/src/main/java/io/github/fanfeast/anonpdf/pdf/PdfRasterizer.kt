package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.SizeF
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * so every access funnels through [mutex]. Every operation also moves itself onto
 * [Dispatchers.IO]: callers are mostly composables on the main thread, and a heavy
 * page can take long enough to render that doing it there would freeze the UI.
 *
 * [close] may be called from anywhere, including mid-render. It never closes the
 * renderer under a page that is still drawing: it marks the rasterizer closed, and
 * whichever side ends up holding the lock last releases the native resources.
 */
class PdfRasterizer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()

    @Volatile
    private var closed = false

    /** Guarded by [mutex]. */
    private var released = false

    val pageCount: Int = renderer.pageCount

    /** Page dimensions in PostScript points (1/72 inch). */
    suspend fun pageSize(index: Int): SizeF = locked {
        renderer.openPage(index).use { page ->
            SizeF(page.width.toFloat(), page.height.toFloat())
        }
    }

    suspend fun pageSizes(): List<SizeF> = locked {
        (0 until renderer.pageCount).map { index ->
            renderer.openPage(index).use { page ->
                SizeF(page.width.toFloat(), page.height.toFloat())
            }
        }
    }

    /** Renders [index] scaled so the result is [targetWidthPx] wide. */
    suspend fun renderByWidth(index: Int, targetWidthPx: Int): Bitmap = locked {
        renderer.openPage(index).use { page ->
            val scale = targetWidthPx.toFloat() / page.width.toFloat()
            val height = max(1, (page.height * scale).roundToInt())
            drawPage(page, max(1, targetWidthPx), height)
        }
    }

    /**
     * Renders one [width] x [height] window of [index], as it would appear in a
     * rendering of the whole page [fullWidthPx] wide, with [left], [top] its corner.
     *
     * The cost follows the window, not the page, so a zoomed-in view stays sharp
     * at any magnification without holding a giant bitmap of the whole page.
     */
    suspend fun renderRegion(
        index: Int,
        fullWidthPx: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Bitmap = locked {
        renderer.openPage(index).use { page ->
            val scale = fullWidthPx.toFloat() / page.width.toFloat()
            val bitmap = createBitmap(max(1, width), max(1, height))
            bitmap.eraseColor(Color.WHITE)
            val transform = Matrix().apply {
                setScale(scale, scale)
                postTranslate(-left.toFloat(), -top.toFloat())
            }
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Renders [index] at a physical resolution, used by the export tools. */
    suspend fun renderAtDpi(index: Int, dpi: Int): Bitmap = locked {
        renderer.openPage(index).use { page ->
            val scale = RenderLimits.cappedScale(page.width, page.height, dpi / RenderLimits.POINTS_PER_INCH)
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

    /**
     * Runs [block] on the IO dispatcher with the renderer to itself.
     *
     * @throws IllegalStateException if the rasterizer has been closed.
     */
    private suspend fun <T> locked(block: () -> T): T = try {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed) { "This document has been closed." }
                block()
            }
        }
    } finally {
        // A close that arrived while we held the lock could not release; do it now.
        releaseIfClosed()
    }

    /**
     * Safe to call from any thread at any time, including while a page renders.
     *
     * Marks the rasterizer closed first, then tries to release. If an operation
     * holds the lock, that operation releases once it finishes instead: it checks
     * [closed] after unlocking, which is after this write, so one side always
     * sees the other and the resources are released exactly once.
     */
    override fun close() {
        closed = true
        releaseIfClosed()
    }

    private fun releaseIfClosed() {
        if (!closed || !mutex.tryLock()) return
        try {
            if (!released) {
                released = true
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
            }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
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
