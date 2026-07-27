package io.github.fanfeast.anonpdf.pdf

import android.util.SizeF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Page-level drawing shared by the one-shot tools and the editor.
 *
 * Everything here works a single page at a time so the editor can render a
 * preview of one page without building the whole document.
 */
internal object PdfDraw {

    fun watermarkFont(options: WatermarkOptions): PDFont =
        if (options.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA

    val labelFont: PDFont get() = PDType1Font.HELVETICA

    /** Stamps [options] across one page. Text is pre-sanitised by the caller. */
    fun watermarkPage(
        document: PDDocument,
        page: PDPage,
        options: WatermarkOptions,
        text: String,
        font: PDFont,
    ) {
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { stream ->
            stream.saveGraphicsState()
            val display = applyDisplayTransform(stream, page)

            stream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = options.opacity
                    strokingAlphaConstant = options.opacity
                },
            )
            stream.setNonStrokingColor(
                red(options.color),
                green(options.color),
                blue(options.color),
            )

            val textWidth = font.getStringWidth(text) / 1000f * options.fontSize
            when (options.layout) {
                WatermarkLayout.DIAGONAL -> drawRotatedText(
                    stream, font, text, options.fontSize,
                    display.width / 2f, display.height / 2f, textWidth, 45.0,
                )
                WatermarkLayout.CENTER -> drawRotatedText(
                    stream, font, text, options.fontSize,
                    display.width / 2f, display.height / 2f, textWidth, 0.0,
                )
                WatermarkLayout.TOP -> drawRotatedText(
                    stream, font, text, options.fontSize,
                    display.width / 2f, display.height - options.fontSize * 2f,
                    textWidth, 0.0,
                )
                WatermarkLayout.BOTTOM -> drawRotatedText(
                    stream, font, text, options.fontSize,
                    display.width / 2f, options.fontSize * 2f, textWidth, 0.0,
                )
                WatermarkLayout.TILED -> drawTiled(
                    stream, font, text, options.fontSize, display, textWidth,
                )
            }
            stream.restoreGraphicsState()
        }
    }

    /**
     * Writes one short label — a page number, typically — at a fixed corner.
     *
     * Taking the finished label rather than a number lets the editor preview a
     * single page while still showing the number it will carry in the final
     * document.
     */
    fun labelPage(
        document: PDDocument,
        page: PDPage,
        label: String,
        options: PageNumberOptions,
        font: PDFont,
    ) {
        if (label.isBlank()) return
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { stream ->
            stream.saveGraphicsState()
            val display = applyDisplayTransform(stream, page)
            stream.setNonStrokingColor(
                red(options.color),
                green(options.color),
                blue(options.color),
            )
            val width = font.getStringWidth(label) / 1000f * options.fontSize
            val x = when (options.position) {
                NumberPosition.TOP_LEFT, NumberPosition.BOTTOM_LEFT ->
                    options.marginPoints
                NumberPosition.TOP_CENTER, NumberPosition.BOTTOM_CENTER ->
                    (display.width - width) / 2f
                NumberPosition.TOP_RIGHT, NumberPosition.BOTTOM_RIGHT ->
                    display.width - options.marginPoints - width
            }
            val y = when (options.position) {
                NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER,
                NumberPosition.TOP_RIGHT,
                -> display.height - options.marginPoints
                else -> options.marginPoints
            }
            stream.beginText()
            stream.setFont(font, options.fontSize)
            stream.newLineAtOffset(x, y)
            stream.showText(label)
            stream.endText()
            stream.restoreGraphicsState()
        }
    }

    /**
     * Resizes a page and the artwork on it by [factor].
     *
     * The scale is prepended to the content and closed off by an appended
     * restore, so the page's own drawing operators run inside a scaled coordinate
     * system without being rewritten.
     */
    fun scalePage(document: PDDocument, page: PDPage, factor: Float) {
        if (factor <= 0f || factor == 1f) return

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.PREPEND,
            true,
            false,
        ).use { stream ->
            stream.saveGraphicsState()
            stream.transform(Matrix.getScaleInstance(factor, factor))
        }
        // The matching restore lives in a separate stream at the end; PDF
        // concatenates a page's streams, so the pair still balances.
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            false,
        ).use { stream -> stream.restoreGraphicsState() }

        // Read both boxes before writing either. PDFBox clips getCropBox() to the
        // MediaBox, so shrinking the MediaBox first makes the next CropBox read
        // return the already-shrunk value — and scaling that again lands the crop
        // at factor squared, cutting most of the page away.
        val media = page.mediaBox
        val crop = page.cropBox
        page.mediaBox = media.scaledBy(factor)
        page.cropBox = crop.scaledBy(factor)
    }

    /**
     * Refits a page onto a standard sheet, centring what was visible on it.
     *
     * Unlike [scalePage], which changes a page's size but keeps its proportions,
     * this puts the content on a sheet of an exact size — the "fit my page onto
     * Letter" operation. Where the aspect ratios differ the leftover becomes even
     * margins, or is cropped away if [ResizeTarget.fill] is set.
     *
     * Any /Rotate is baked into the content and the page left at rotation zero.
     * Otherwise a quarter-turned page asked for "Letter" would end up with a
     * Letter box that a viewer then displays sideways, which is not what anybody
     * means by the request.
     *
     * Reads the page's current /Rotate and CropBox, so the caller must settle
     * rotation and cropping first.
     */
    fun resizePage(document: PDDocument, page: PDPage, target: ResizeTarget) {
        val box = page.cropBox
        val rotation = PdfOps.normalizeRotation(page.rotation)
        val quarterTurned = rotation == 90 || rotation == 270

        // What the reader currently sees, which is what has to be made to fit.
        val displayWidth = if (quarterTurned) box.height else box.width
        val displayHeight = if (quarterTurned) box.width else box.height
        if (displayWidth <= 0f || displayHeight <= 0f) return

        val (sheetWidth, sheetHeight) = target.sizeFor(displayWidth, displayHeight)
        val scale = if (target.fill) {
            max(sheetWidth / displayWidth, sheetHeight / displayHeight)
        } else {
            min(sheetWidth / displayWidth, sheetHeight / displayHeight)
        }
        if (scale <= 0f) return

        // Page space -> upright display space, origin at the display bottom-left.
        // The inverse of what applyDisplayTransform does, derived the same way.
        val toDisplay = when (rotation) {
            90 -> Matrix(0f, -1f, 1f, 0f, -box.lowerLeftY, box.width + box.lowerLeftX)
            180 -> Matrix(
                -1f, 0f, 0f, -1f,
                box.width + box.lowerLeftX,
                box.height + box.lowerLeftY,
            )
            270 -> Matrix(0f, 1f, -1f, 0f, box.height + box.lowerLeftY, -box.lowerLeftX)
            else -> Matrix(1f, 0f, 0f, 1f, -box.lowerLeftX, -box.lowerLeftY)
        }
        // Then scale onto the sheet and centre whatever is left over.
        val ontoSheet = Matrix(
            scale, 0f, 0f, scale,
            (sheetWidth - displayWidth * scale) / 2f,
            (sheetHeight - displayHeight * scale) / 2f,
        )

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.PREPEND,
            true,
            false,
        ).use { stream ->
            stream.saveGraphicsState()
            stream.transform(toDisplay.multiply(ontoSheet))
            // Clip in the original page's own coordinates — the transform above is
            // already in effect, so this keeps anything the CropBox was hiding from
            // reappearing in the new margins.
            stream.addRect(box.lowerLeftX, box.lowerLeftY, box.width, box.height)
            stream.clip()
        }
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            false,
        ).use { stream -> stream.restoreGraphicsState() }

        val sheet = PDRectangle(0f, 0f, sheetWidth, sheetHeight)
        page.rotation = 0
        page.mediaBox = sheet
        page.cropBox = sheet
    }

    /**
     * Shrinks the visible area, given insets as fractions of each edge **of the
     * page as displayed**.
     *
     * The caller drags a frame over a rendered page, so "left" means the left the
     * user saw. A page carrying a /Rotate is shown turned, so that edge is not the
     * CropBox's left — on a quarter-turned page it is the bottom. The insets are
     * rotated into page space first; skipping that step trims the wrong side, and
     * the mistake is invisible until someone crops a rotated scan.
     *
     * Reads the page's current /Rotate, so callers must set the final rotation
     * before cropping.
     */
    fun cropPage(page: PDPage, displayInsets: CropInsets) {
        if (displayInsets.isEmpty) return
        val insets = toPageSpace(displayInsets, PdfOps.normalizeRotation(page.rotation))
        val box = page.cropBox
        val left = box.width * insets.left
        val right = box.width * insets.right
        val top = box.height * insets.top
        val bottom = box.height * insets.bottom
        val width = box.width - left - right
        val height = box.height - top - bottom
        if (width <= 1f || height <= 1f) return
        page.cropBox = PDRectangle(
            box.lowerLeftX + left,
            box.lowerLeftY + bottom,
            width,
            height,
        )
    }

    /**
     * Rotates display-space edge insets into unrotated page space.
     *
     * Derived from the same display-to-page mapping [applyDisplayTransform] uses:
     * at 90° a display point (dx, dy) lands at page (W - dy, dx), so the display
     * left edge is the page bottom, and so on round.
     */
    private fun toPageSpace(insets: CropInsets, rotation: Int): CropInsets = when (rotation) {
        90 -> CropInsets(
            left = insets.top,
            top = insets.right,
            right = insets.bottom,
            bottom = insets.left,
        )
        180 -> CropInsets(
            left = insets.right,
            top = insets.bottom,
            right = insets.left,
            bottom = insets.top,
        )
        270 -> CropInsets(
            left = insets.bottom,
            top = insets.left,
            right = insets.top,
            bottom = insets.right,
        )
        else -> insets
    }

    /**
     * Maps "what the user sees" onto PDF user space and returns the displayed
     * page size.
     *
     * A page carries a /Rotate that viewers apply before showing it, so anything
     * placed with raw page coordinates lands sideways on a rotated page. This
     * pre-multiplies the inverse rotation, letting callers work in upright
     * display coordinates with the origin at the bottom left.
     */
    fun applyDisplayTransform(stream: PDPageContentStream, page: PDPage): SizeF {
        val box = page.cropBox
        stream.transform(Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY))
        return when (PdfOps.normalizeRotation(page.rotation)) {
            90 -> {
                stream.transform(Matrix.getRotateInstance(Math.PI / 2, box.width, 0f))
                SizeF(box.height, box.width)
            }
            180 -> {
                stream.transform(Matrix.getRotateInstance(Math.PI, box.width, box.height))
                SizeF(box.width, box.height)
            }
            270 -> {
                stream.transform(Matrix.getRotateInstance(-Math.PI / 2, 0f, box.height))
                SizeF(box.height, box.width)
            }
            else -> SizeF(box.width, box.height)
        }
    }

    /**
     * The 14 built-in PDF fonts cover WinAnsi only, and showText throws on
     * anything else. Swap unsupported characters for '?' so a stray emoji
     * degrades instead of failing the whole operation.
     */
    fun sanitizeForFont(text: String, font: PDFont): String {
        val flattened = text.replace(Regex("[\\r\\n\\t]"), " ")
        val builder = StringBuilder(flattened.length)
        for (character in flattened) {
            val candidate = character.toString()
            val supported = runCatching { font.getStringWidth(candidate) }.isSuccess
            builder.append(if (supported) candidate else "?")
        }
        return builder.toString().trim()
    }

    private fun drawTiled(
        stream: PDPageContentStream,
        font: PDFont,
        text: String,
        fontSize: Float,
        display: SizeF,
        textWidth: Float,
    ) {
        val stepX = max(textWidth * 1.6f, fontSize * 4f)
        val stepY = max(fontSize * 5f, 90f)
        val columns = ceil(display.width / stepX).toInt() + 1
        val rows = ceil(display.height / stepY).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                // Offset alternate rows so the pattern does not read as a grid.
                val offset = if (row % 2 == 0) 0f else stepX / 2f
                drawRotatedText(
                    stream, font, text, fontSize,
                    column * stepX + offset, row * stepY, textWidth, 30.0,
                )
            }
        }
    }

    private fun drawRotatedText(
        stream: PDPageContentStream,
        font: PDFont,
        text: String,
        fontSize: Float,
        centerX: Float,
        centerY: Float,
        textWidth: Float,
        degrees: Double,
    ) {
        stream.saveGraphicsState()
        stream.transform(Matrix.getTranslateInstance(centerX, centerY))
        if (degrees != 0.0) {
            stream.transform(Matrix.getRotateInstance(Math.toRadians(degrees), 0f, 0f))
        }
        stream.beginText()
        stream.setFont(font, fontSize)
        // Nudge down by roughly a third of the cap height to sit on the centre line.
        stream.newLineAtOffset(-textWidth / 2f, -fontSize / 3f)
        stream.showText(text)
        stream.endText()
        stream.restoreGraphicsState()
    }

    private fun PDRectangle.scaledBy(factor: Float) = PDRectangle(
        lowerLeftX * factor,
        lowerLeftY * factor,
        width * factor,
        height * factor,
    )

    fun red(color: Int) = ((color shr 16) and 0xFF) / 255f
    fun green(color: Int) = ((color shr 8) and 0xFF) / 255f
    fun blue(color: Int) = (color and 0xFF) / 255f
}
