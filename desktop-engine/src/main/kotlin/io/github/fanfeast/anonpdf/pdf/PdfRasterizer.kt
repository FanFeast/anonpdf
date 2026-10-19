package io.github.fanfeast.anonpdf.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns PDF pages into images with PDFBox's own renderer: the desktop
 * counterpart of the Android rasterizer, with the same API.
 *
 * Android has a renderer built in; desktop does not, so PDFBox draws the pages
 * itself. Nothing is downloaded or installed: it is plain Java2D.
 *
 * [PDFRenderer] is not thread safe, so every access funnels through [mutex] and
 * runs on [Dispatchers.IO]. [close] may be called from anywhere, including
 * mid-render: the document is released by whichever side holds the lock last.
 */
class PdfRasterizer private constructor(private val document: PDDocument) : Closeable {

    private val renderer = PDFRenderer(document)
    private val mutex = Mutex()

    @Volatile
    private var closed = false

    /** Guarded by [mutex]. */
    private var released = false

    val pageCount: Int = document.numberOfPages

    /** Page dimensions in PostScript points (1/72 inch), as displayed: rotation applied. */
    suspend fun pageSize(index: Int): SizeF = locked { displayedSize(document.getPage(index)) }

    suspend fun pageSizes(): List<SizeF> = locked { document.pages.map(::displayedSize) }

    /** Renders [index] scaled so the result is [targetWidthPx] wide. */
    suspend fun renderByWidth(index: Int, targetWidthPx: Int): BufferedImage = locked {
        val size = displayedSize(document.getPage(index))
        val scale = targetWidthPx.toFloat() / size.width
        val height = max(1, (size.height * scale).roundToInt())
        draw(index, scale, max(1, targetWidthPx), height, left = 0, top = 0)
    }

    /**
     * Renders one [width] x [height] window of [index], as it would appear in a
     * rendering of the whole page [fullWidthPx] wide, with [left], [top] its corner.
     */
    suspend fun renderRegion(
        index: Int,
        fullWidthPx: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): BufferedImage = locked {
        val scale = fullWidthPx.toFloat() / displayedSize(document.getPage(index)).width
        draw(index, scale, max(1, width), max(1, height), left, top)
    }

    /** Renders [index] at a physical resolution, used by the export tools. */
    suspend fun renderAtDpi(index: Int, dpi: Int): BufferedImage = locked {
        val size = displayedSize(document.getPage(index))
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val scale = RenderLimits.cappedScale(width, height, dpi / RenderLimits.POINTS_PER_INCH)
        draw(
            index,
            scale,
            max(1, (size.width * scale).roundToInt()),
            max(1, (size.height * scale).roundToInt()),
            left = 0,
            top = 0,
        )
    }

    private fun draw(index: Int, scale: Float, width: Int, height: Int, left: Int, top: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHints(QUALITY)
            // A PDF page is conceptually paper, so start from white.
            graphics.background = Color.WHITE
            graphics.clearRect(0, 0, width, height)
            graphics.translate(-left.toDouble(), -top.toDouble())
            renderer.renderPageToGraphics(index, graphics as Graphics2D, scale)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun displayedSize(page: PDPage): SizeF {
        val box = page.cropBox
        val quarterTurned = PdfOps.normalizeRotation(page.rotation).let { it == 90 || it == 270 }
        return if (quarterTurned) SizeF(box.height, box.width) else SizeF(box.width, box.height)
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
        releaseIfClosed()
    }

    /** Safe to call from any thread at any time, including while a page renders. */
    override fun close() {
        closed = true
        releaseIfClosed()
    }

    private fun releaseIfClosed() {
        if (!closed || !mutex.tryLock()) return
        try {
            if (!released) {
                released = true
                runCatching { document.close() }
            }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        private val QUALITY = RenderingHints(
            mapOf(
                RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            ),
        )

        /** @throws java.io.IOException if the file is not a PDF, or cannot be opened. */
        fun open(file: File): PdfRasterizer = PdfRasterizer(PdfOps.load(file))
    }
}
