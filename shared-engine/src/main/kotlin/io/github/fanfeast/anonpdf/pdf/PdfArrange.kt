package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDPageLabelRange
import com.tom_roush.pdfbox.pdmodel.common.PDPageLabels
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How a run of page labels counts. */
enum class LabelStyle(internal val code: String?) {
    DECIMAL(PDPageLabelRange.STYLE_DECIMAL),
    ROMAN_LOWER(PDPageLabelRange.STYLE_ROMAN_LOWER),
    ROMAN_UPPER(PDPageLabelRange.STYLE_ROMAN_UPPER),
    LETTERS_LOWER(PDPageLabelRange.STYLE_LETTERS_LOWER),
    LETTERS_UPPER(PDPageLabelRange.STYLE_LETTERS_UPPER),

    /** Only the prefix, as in "Cover". */
    NONE(null),
    ;

    /** How [number] reads in this style. */
    fun format(number: Int): String = when (this) {
        DECIMAL -> number.toString()
        ROMAN_LOWER -> roman(number).lowercase()
        ROMAN_UPPER -> roman(number)
        LETTERS_LOWER -> letters(number).lowercase()
        LETTERS_UPPER -> letters(number)
        NONE -> ""
    }

    private fun roman(number: Int): String {
        var n = number.coerceIn(1, 3999)
        return buildString {
            for ((value, numeral) in NUMERALS) while (n >= value) { append(numeral); n -= value }
        }
    }

    /** A, B … Z, AA, BB …, as PDF counts letters. */
    private fun letters(number: Int): String {
        val n = number.coerceAtLeast(1) - 1
        return ('A' + n % 26).toString().repeat(n / 26 + 1)
    }

    private companion object {
        val NUMERALS = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
    }
}

/**
 * Page labels from [firstPage] (an output position, from 0) up to the next
 * range: what a reader shows as the page's number, such as "iv" for front matter
 * or "A-1" for an appendix. The printed page doesn't change.
 */
data class LabelRange(
    val firstPage: Int,
    val style: LabelStyle = LabelStyle.DECIMAL,
    val prefix: String = "",
    val start: Int = 1,
)

/** Rearranging pages across documents, and page labels. */
object PdfArrange {

    /**
     * Interleaves [inputs]: the first page of each in turn, then the second of
     * each, and so on, carrying on with whichever has pages left. With
     * [reverseLast] the last file is taken back to front, which is how the backs
     * of a two-sided scan come out of a scanner that only scans one side.
     */
    suspend fun mix(
        inputs: List<File>,
        output: File,
        reverseLast: Boolean,
        onProgress: Progress = {},
    ) = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { S.pickTwoPdfs }
        val merged = File(output.parentFile, "mix-${System.nanoTime()}.pdf")
        try {
            val counts = inputs.map { PdfOps.readMeta(it).pageCount }
            PdfOps.merge(inputs, merged) { onProgress(it * 0.5f) }
            val offsets = counts.runningFold(0, Int::plus)
            val order = mutableListOf<PagePlan>()
            for (round in 0 until counts.max()) {
                counts.forEachIndexed { file, count ->
                    if (round < count) {
                        val within = if (reverseLast && file == counts.lastIndex) count - 1 - round else round
                        order += PagePlan(offsets[file] + within)
                    }
                }
            }
            PdfOps.organize(merged, output, order) { onProgress(0.5f + it * 0.5f) }
        } finally {
            merged.delete()
        }
    }

    /** What the reader shows for each of [count] pages, under [ranges]. Pages before the first range get plain numbers. */
    fun labels(ranges: List<LabelRange>, count: Int): List<String> {
        val sorted = ranges.sortedBy { it.firstPage }
        return (0 until count).map { position ->
            val range = sorted.lastOrNull { it.firstPage <= position }
                ?: return@map (position + 1).toString()
            range.prefix + range.style.format(range.start + position - range.firstPage)
        }
    }

    suspend fun readLabels(file: File, password: String? = null): List<LabelRange> = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { document -> readLabels(document) }
    }

    internal fun readLabels(document: PDDocument): List<LabelRange> {
        val labels = runCatching { document.documentCatalog.pageLabels }.getOrNull() ?: return emptyList()
        return labels.pageIndices.sorted().mapNotNull { index ->
            val range = labels.getPageLabelRange(index) ?: return@mapNotNull null
            LabelRange(
                firstPage = index,
                style = LabelStyle.entries.firstOrNull { it.code == range.style } ?: LabelStyle.NONE,
                prefix = range.prefix.orEmpty(),
                start = range.start.coerceAtLeast(1),
            )
        }
    }

    /** Replaces [document]'s labels with [ranges]; none removes them. */
    internal fun writeLabels(document: PDDocument, ranges: List<LabelRange>) {
        val catalog = document.documentCatalog
        val usable = ranges.filter { it.firstPage in 0 until document.numberOfPages }.distinctBy { it.firstPage }
        if (usable.isEmpty()) {
            catalog.cosObject.removeItem(COSName.PAGE_LABELS)
            return
        }
        // A PDF's labels must say what page 1 is; plain numbers, unless the user said otherwise.
        catalog.pageLabels = PDPageLabels(document).apply {
            (listOf(LabelRange(0)) + usable).associateBy { it.firstPage }.values.forEach { range ->
                setLabelItem(
                    range.firstPage,
                    PDPageLabelRange().apply {
                        range.style.code?.let { style = it }
                        if (range.prefix.isNotEmpty()) prefix = range.prefix
                        start = range.start.coerceAtLeast(1)
                    },
                )
            }
        }
    }
}
