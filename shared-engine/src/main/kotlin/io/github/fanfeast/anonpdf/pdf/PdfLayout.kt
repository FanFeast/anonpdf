package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.util.Matrix
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/** How pages are laid out on the sheets they will be printed on. */
sealed interface PrintLayout {
    /** Several pages to a sheet, left to right, top to bottom: 2, 4, 6 or 9. */
    data class PerSheet(val pages: Int) : PrintLayout

    /**
     * Two pages to each side of a sheet, in the order that, printed on both
     * sides (flipping on the short edge), folded and stacked, reads as a book.
     */
    data object Booklet : PrintLayout

    /** Each page enlarged across [across] × [down] sheets, to be trimmed and joined. */
    data class Poster(val across: Int, val down: Int) : PrintLayout
}

/**
 * Lays a document's pages out for printing: several to a sheet, as a booklet,
 * or as a poster. The pages are placed as they are, vector content and text
 * intact, never turned into pictures.
 */
object PdfLayout {

    /** A little white around each page, so nothing is lost to a printer's margin. */
    private const val GAP = 12f

    suspend fun apply(
        input: File,
        output: File,
        layout: PrintLayout,
        paper: PaperSize,
        password: String? = null,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        PdfOps.load(input, password).use { source ->
            require(source.numberOfPages > 0) { S.pdfNeedsAPage }
            PDDocument().use { target ->
                val layers = LayerUtility(target)
                val forms = (0 until source.numberOfPages).map { index ->
                    ensureActive()
                    Placed.of(layers, source, index).also { onProgress(0.4f * (index + 1f) / source.numberOfPages) }
                }
                when (layout) {
                    is PrintLayout.PerSheet -> perSheet(target, forms, layout.pages, paper)
                    PrintLayout.Booklet -> booklet(target, forms, paper)
                    is PrintLayout.Poster -> poster(target, forms, layout.across, layout.down, paper)
                }
                onProgress(0.95f)
                target.save(output)
                onProgress(1f)
            }
        }
    }

    /** Columns and rows for each count, and whether the sheet is turned sideways for portrait pages. */
    private fun grid(pages: Int): Triple<Int, Int, Boolean> = when (pages) {
        2 -> Triple(2, 1, true)
        4 -> Triple(2, 2, false)
        6 -> Triple(3, 2, true)
        9 -> Triple(3, 3, false)
        else -> throw IllegalArgumentException("$pages pages to a sheet")
    }

    private fun perSheet(target: PDDocument, forms: List<Placed>, perSheet: Int, paper: PaperSize) {
        val (columns, rows, sideways) = grid(perSheet)
        // Landscape pages want the sheet the other way round from portrait ones.
        val landscapePages = forms.count { it.width > it.height } > forms.size / 2
        val sheet = sheet(paper, landscape = sideways != landscapePages)
        // Landscape pages fill a grid on its side: the same count, rows and columns swapped.
        val (cols, rws) = if (landscapePages) rows to columns else columns to rows
        forms.chunked(perSheet).forEach { group ->
            val page = PDPage(sheet).also(target::addPage)
            PDPageContentStream(target, page).use { stream ->
                group.forEachIndexed { slot, form ->
                    val cellWidth = sheet.width / cols
                    val cellHeight = sheet.height / rws
                    val left = (slot % cols) * cellWidth
                    val bottom = sheet.height - (slot / cols + 1) * cellHeight
                    place(stream, form, PDRectangle(left, bottom, cellWidth, cellHeight))
                }
            }
        }
    }

    private fun booklet(target: PDDocument, forms: List<Placed>, paper: PaperSize) {
        val count = ((forms.size + 3) / 4) * 4
        val sheet = sheet(paper, landscape = true)
        val half = sheet.width / 2
        // The outer sheet carries the last page and the first; each sheet inside
        // it the next pair in from either end.
        for (i in 0 until count / 4) {
            val sides = listOf(
                (count - 1 - 2 * i) to (2 * i),
                (2 * i + 1) to (count - 2 - 2 * i),
            )
            sides.forEach { (leftIndex, rightIndex) ->
                val page = PDPage(sheet).also(target::addPage)
                PDPageContentStream(target, page).use { stream ->
                    forms.getOrNull(leftIndex)?.let { place(stream, it, PDRectangle(0f, 0f, half, sheet.height)) }
                    forms.getOrNull(rightIndex)?.let { place(stream, it, PDRectangle(half, 0f, half, sheet.height)) }
                }
            }
        }
    }

    private fun poster(target: PDDocument, forms: List<Placed>, across: Int, down: Int, paper: PaperSize) {
        forms.forEach { form ->
            // Sheets the same way round as the page, so the grid matches its shape.
            val sheet = sheet(paper, landscape = form.width > form.height)
            val printable = PDRectangle(sheet.width - 2 * GAP, sheet.height - 2 * GAP)
            val scale = min(printable.width * across / form.width, printable.height * down / form.height)
            val totalWidth = form.width * scale
            val totalHeight = form.height * scale
            // Centred across the whole grid of sheets.
            val offsetX = (printable.width * across - totalWidth) / 2
            val offsetY = (printable.height * down - totalHeight) / 2
            for (row in 0 until down) {
                for (column in 0 until across) {
                    val page = PDPage(sheet).also(target::addPage)
                    PDPageContentStream(target, page).use { stream ->
                        stream.saveGraphicsState()
                        stream.addRect(GAP, GAP, printable.width, printable.height)
                        stream.clip()
                        // Row 0 is the top of the poster.
                        val x = GAP - column * printable.width + offsetX
                        val y = GAP - (down - 1 - row) * printable.height + offsetY
                        stream.transform(Matrix(scale, 0f, 0f, scale, x, y))
                        form.draw(stream)
                        stream.restoreGraphicsState()
                    }
                }
            }
        }
    }

    /** Draws [form] as large as fits in [cell], less the gap, centred. */
    private fun place(stream: PDPageContentStream, form: Placed, cell: PDRectangle) {
        val room = PDRectangle(cell.lowerLeftX + GAP, cell.lowerLeftY + GAP, cell.width - 2 * GAP, cell.height - 2 * GAP)
        val scale = min(room.width / form.width, room.height / form.height)
        val x = room.lowerLeftX + (room.width - form.width * scale) / 2
        val y = room.lowerLeftY + (room.height - form.height * scale) / 2
        stream.saveGraphicsState()
        stream.transform(Matrix(scale, 0f, 0f, scale, x, y))
        form.draw(stream)
        stream.restoreGraphicsState()
    }

    /**
     * A page as a form, with what it takes to draw it as it is shown: turned by
     * its /Rotate and cut to its crop box, with its corner at the origin.
     * [width] and [height] are as shown.
     */
    internal class Placed private constructor(private val form: PDFormXObject, private val rotation: Int) {
        private val box = form.bBox
        private val turned = rotation == 90 || rotation == 270
        val width: Float get() = if (turned) box.height else box.width
        val height: Float get() = if (turned) box.width else box.height

        fun draw(stream: PDPageContentStream) {
            when (rotation) {
                90 -> stream.transform(Matrix(0f, -1f, 1f, 0f, 0f, box.width))
                180 -> stream.transform(Matrix(-1f, 0f, 0f, -1f, box.width, box.height))
                270 -> stream.transform(Matrix(0f, 1f, -1f, 0f, box.height, 0f))
            }
            stream.transform(Matrix(1f, 0f, 0f, 1f, -box.lowerLeftX, -box.lowerLeftY))
            stream.drawForm(form)
        }

        companion object {
            fun of(layers: LayerUtility, source: PDDocument, index: Int): Placed {
                val page = source.getPage(index)
                val rotation = ((page.rotation % 360) + 360) % 360
                // Imported unturned and with no matrix of its own: the turn is
                // drawn here, where it keeps the page's shape. (PDFBox's own
                // handling squeezes a turned page into its unturned outline.)
                page.rotation = 0
                val form = try {
                    layers.importPageAsForm(source, index)
                } finally {
                    page.rotation = rotation
                }
                form.cosObject.removeItem(COSName.MATRIX)
                return Placed(form, rotation)
            }
        }
    }

    private fun sheet(paper: PaperSize, landscape: Boolean): PDRectangle {
        val short = min(paper.widthPoints, paper.heightPoints)
        val long = maxOf(paper.widthPoints, paper.heightPoints)
        return if (landscape) PDRectangle(long, short) else PDRectangle(short, long)
    }

    /** How many sheets [layout] makes of [pages] pages, to say before it's run. */
    fun sheets(layout: PrintLayout, pages: Int): Int = when (layout) {
        is PrintLayout.PerSheet -> ceil(pages / layout.pages.toDouble()).toInt()
        PrintLayout.Booklet -> (pages + 3) / 4 * 2
        is PrintLayout.Poster -> pages * layout.across * layout.down
    }
}
