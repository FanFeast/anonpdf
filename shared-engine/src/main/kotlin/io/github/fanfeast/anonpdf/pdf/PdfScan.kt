package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tools for pages that came off a scanner or a phone camera: straightening a
 * crooked page, making a page grey, and the blank pages to insert between them.
 */
object PdfScan {

    /** Straightening is for scans; past this a page is better turned a quarter. */
    const val MAX_TILT = 45f

    /**
     * Turns a page's content by [degrees] about the middle of what is shown,
     * anticlockwise for a positive angle, leaving the page's size alone. Like
     * scaling, it wraps the content rather than rewriting it, so text stays text.
     */
    internal fun tiltPage(document: PDDocument, page: PDPage, degrees: Float) {
        if (degrees == 0f) return
        val box = page.cropBox
        val centreX = box.lowerLeftX + box.width / 2f
        val centreY = box.lowerLeftY + box.height / 2f
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.PREPEND, true, false).use { stream ->
            stream.saveGraphicsState()
            stream.transform(Matrix.getTranslateInstance(centreX, centreY))
            stream.transform(Matrix.getRotateInstance(Math.toRadians(degrees.toDouble()), 0f, 0f))
            stream.transform(Matrix.getTranslateInstance(-centreX, -centreY))
        }
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, false)
            .use { stream -> stream.restoreGraphicsState() }
    }

    /**
     * Makes a page grey without turning it into a picture: a grey sheet laid over
     * everything in the Saturation blend mode keeps each colour's lightness and
     * takes away its colour. Text stays text, and searchable.
     */
    internal fun greyPage(document: PDDocument, page: PDPage) {
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { blendMode = BlendMode.SATURATION })
            stream.setNonStrokingColor(0.5f, 0.5f, 0.5f)
            val box = page.mediaBox
            stream.addRect(box.lowerLeftX, box.lowerLeftY, box.width, box.height)
            stream.fill()
            stream.restoreGraphicsState()
        }
    }

    /** Writes a one-page document with a blank page [width] by [height] points. */
    suspend fun writeBlankPage(width: Float, height: Float, output: File) = withContext(Dispatchers.IO) {
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(width, height)))
            document.save(output)
        }
    }

    /**
     * How far the page at [outputPosition] of [plan] leans, rendered [widthPx]
     * wide; see [detectSkew]. 0 if the page cannot be drawn.
     */
    suspend fun measureLean(
        sources: List<PdfEditor.PlanSource>,
        plan: EditPlan,
        outputPosition: Int,
        widthPx: Int,
        workDir: File,
    ): Float {
        val image = PdfEditor.renderPreview(sources, plan, outputPosition, widthPx, workDir) ?: return 0f
        return try {
            withContext(Dispatchers.Default) { detectSkew(image) }
        } finally {
            image.release()
        }
    }

    /**
     * How far a scanned page leans, in degrees: the angle to tilt it by to make
     * its lines of text level, or 0 if it has none to go by.
     *
     * Lines of text are rows of ink with paper between them. Seen at the right
     * angle, the ink piles up in a few rows and the rest are empty; at any other
     * angle it smears across them. So each candidate angle is scored by how
     * unevenly the ink falls into rows, and the most uneven one wins.
     */
    fun detectSkew(image: PageImage): Float {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        for (y in 0 until image.height step SAMPLE_STEP) {
            for (x in 0 until image.width step SAMPLE_STEP) {
                val pixel = image.pixelAt(x, y)
                if ((pixel ushr 24) < 128) continue
                val darkest = minOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
                if (darkest < INK_LEVEL) {
                    xs += x
                    ys += y
                }
            }
        }
        // Too little ink to find lines in: a blank or a picture-only page.
        if (xs.size < MIN_INK_POINTS) return 0f

        // Rows as tall as the sampling step: one-pixel rows would leave every other
        // row empty at exactly level, and that alone would make level look best.
        val rows = IntArray(image.height + image.width + 2)
        fun score(degrees: Float): Double {
            rows.fill(0)
            val radians = Math.toRadians(degrees.toDouble())
            val s = sin(radians)
            val c = cos(radians)
            val offset = image.width
            for (i in xs.indices) {
                val row = ((ys[i] * c - xs[i] * s + offset) / SAMPLE_STEP).toInt()
                if (row in rows.indices) rows[row]++
            }
            var sum = 0.0
            for (count in rows) sum += count.toDouble() * count
            return sum
        }

        // Coarse, then fine around the best coarse angle.
        var best = 0f
        var bestScore = score(0f)
        var angle = -MAX_DETECTED
        while (angle <= MAX_DETECTED) {
            val value = score(angle)
            if (value > bestScore) {
                bestScore = value
                best = angle
            }
            angle += 0.5f
        }
        val coarse = best
        angle = coarse - 0.5f
        while (angle <= coarse + 0.5f) {
            val value = score(angle)
            if (value > bestScore) {
                bestScore = value
                best = angle
            }
            angle += 0.05f
        }
        // The image's y runs down the page, so the angle that levels the rows here
        // is already the turn, anticlockwise positive, that levels the page.
        return (best * 20).roundToInt() / 20f
    }

    /** Scans are rarely more than a few degrees out; a bigger lean is a different problem. */
    private const val MAX_DETECTED = 10f
    private const val INK_LEVEL = 140
    private const val MIN_INK_POINTS = 200
    private const val SAMPLE_STEP = 2
}
