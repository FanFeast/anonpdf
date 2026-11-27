package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.StringWriter
import kotlin.math.hypot

/** What a redaction looks for. */
enum class RedactKind {
    /** The words the user typed. */
    WORDS,
    EMAILS,
    PHONES,

    /** Runs of eight or more digits: card, account and ID numbers. */
    NUMBERS,
    ;

    val label: String
        get() = when (this) {
            WORDS -> S.redactWords
            EMAILS -> S.redactEmails
            PHONES -> S.redactPhones
            NUMBERS -> S.redactNumbers
        }
}

/**
 * A search to black out. Kept as the search rather than as the boxes it found,
 * so it still finds its text after the page is rotated, cropped or refitted, and
 * so it means the same thing when replayed on another document.
 */
data class RedactRule(val kind: RedactKind, val text: String = "") {

    val isUsable: Boolean get() = kind != RedactKind.WORDS || text.isNotBlank()

    fun describe(): String = if (kind == RedactKind.WORDS) S.quoted(text.trim()) else kind.label

    internal val regex: Regex? by lazy {
        when (kind) {
            // Whitespace in the query matches any whitespace or none: a PDF often
            // positions words apart without a space character between them.
            RedactKind.WORDS -> text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\\s*") { Regex.escape(it) }
                ?.let { Regex(it, setOf(RegexOption.IGNORE_CASE)) }
            RedactKind.EMAILS -> EMAIL
            RedactKind.PHONES -> PHONE
            RedactKind.NUMBERS -> NUMBER
        }
    }

    internal fun accepts(match: String): Boolean {
        val digits = match.count(Char::isDigit)
        return when (kind) {
            RedactKind.PHONES -> digits in 7..15
            RedactKind.NUMBERS -> digits >= 8
            else -> true
        }
    }

    private companion object {
        val EMAIL = Regex("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*\\.\\p{L}{2,}")
        val PHONE = Regex("(?<![\\p{L}\\p{N}])\\+?\\(?\\d[\\d ().-]{5,}\\d(?![\\p{L}\\p{N}])")
        val NUMBER = Regex("(?<![\\p{L}\\p{N}])\\d(?:[ -]?\\d){7,}(?![\\p{L}\\p{N}])")
    }
}

/** A [rule] applied to some pages, by source page index. */
data class Redaction(val rule: RedactRule, val targets: Set<Int>)

/** How much a search found. */
data class RedactCount(val matches: Int, val pages: Int)

/**
 * Finds text on a page and covers it.
 *
 * Covering is the editor's ordinary opaque fill, so a redacted page goes through
 * the same flattening as a white-out: the page becomes a picture, and the text
 * under the box is gone from the file, not just hidden.
 *
 * Only text the page draws as text is found. Words that are part of a picture,
 * as on a scan, are not.
 */
object PdfRedact {

    /**
     * Black boxes over every match of [rules] on [page], as it stands now —
     * after rotation, crop and resizing, so the boxes land on the text as shown.
     */
    internal fun cover(page: PDPage, rules: List<RedactRule>, sourceIndex: Int): List<FillMark> =
        rules.flatMap { matches(page, it) }.flatten().mapIndexed { index, rect ->
            FillMark(
                id = -1L - index,
                sourceIndex = sourceIndex,
                rect = rect,
                color = 0xFF000000.toInt(),
            )
        }

    /**
     * Counts what [rule] finds on the [targets] pages, global indices across
     * [sources] as the editor numbers them.
     */
    suspend fun count(
        sources: List<PdfEditor.PlanSource>,
        rule: RedactRule,
        targets: Set<Int>,
    ): RedactCount = withContext(Dispatchers.IO) {
        var matches = 0
        var pages = 0
        var first = 0
        for (source in sources) {
            val owned = targets.filter { it >= first && it < first + source.pageCount }
            if (owned.isNotEmpty()) {
                PdfOps.load(source.file, source.password).use { document ->
                    for (global in owned.sorted()) {
                        ensureActive()
                        val local = global - first
                        if (local >= document.numberOfPages) continue
                        val found = matches(document.getPage(local), rule).size
                        if (found > 0) {
                            matches += found
                            pages++
                        }
                    }
                }
            }
            first += source.pageCount
        }
        RedactCount(matches, pages)
    }

    /** One entry per match: its box on each line it spans. */
    internal fun matches(page: PDPage, rule: RedactRule): List<List<MarkRect>> {
        val regex = rule.regex ?: return emptyList()
        val glyphs = Glyphs.of(page) ?: return emptyList()
        return regex.findAll(glyphs.text)
            .filter { rule.accepts(it.value) }
            .map { match -> glyphs.boxes(match.range) }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /** A page's text, and which glyph each of its characters came from. */
    private class Glyphs(
        val text: String,
        private val sources: List<TextPosition?>,
        private val page: Sheet,
    ) {
        /** The glyphs in [range], merged into one box per line, in page fractions. */
        fun boxes(range: IntRange): List<MarkRect> {
            val lines = mutableListOf<MarkRect>()
            var previous: TextPosition? = null
            for (index in range) {
                val glyph = sources[index] ?: continue
                if (glyph === previous) continue
                previous = glyph
                val box = page.box(glyph) ?: continue
                val last = lines.lastOrNull()
                lines += if (last != null && sameLine(last, box)) {
                    lines.removeAt(lines.lastIndex)
                    MarkRect(
                        minOf(last.left, box.left),
                        minOf(last.top, box.top),
                        maxOf(last.right, box.right),
                        maxOf(last.bottom, box.bottom),
                    )
                } else {
                    box
                }
            }
            return lines.map { it.tidied() }.filterNot { it.isDegenerate }
        }

        /** Neighbours along a line, whichever way the line runs on the page. */
        private fun sameLine(a: MarkRect, b: MarkRect): Boolean {
            val across = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val down = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (across < 0f || down < 0f) return false
            return down > 0.5f * minOf(a.height, b.height) || across > 0.5f * minOf(a.width, b.width)
        }

        companion object {
            fun of(page: PDPage): Glyphs? {
                val collector = Collector()
                runCatching { collector.processPage(page) }.onFailure { return null }
                if (collector.text.isEmpty()) return null
                return Glyphs(collector.text.toString(), collector.glyphs, Sheet(page))
            }
        }
    }

    /**
     * A page as displayed, for turning a glyph into a box on it.
     *
     * TextPosition's own x and y are measured along the reading direction, which
     * on a page turned a quarter runs down the screen. So boxes are worked out
     * from each glyph's drawing matrix instead — page space measured from the
     * crop box's corner — and turned the way the page is shown, which holds for
     * text drawn at any angle.
     */
    private class Sheet(page: PDPage) {
        private val rotation = PdfOps.normalizeRotation(page.rotation)
        private val boxWidth = page.cropBox.width
        private val boxHeight = page.cropBox.height
        private val quarterTurned = rotation == 90 || rotation == 270
        private val width = if (quarterTurned) boxHeight else boxWidth
        private val height = if (quarterTurned) boxWidth else boxHeight

        /**
         * The glyph's cell, from below its descenders to above its capitals and a
         * little either side, so no edge of ink shows past the box.
         */
        fun box(glyph: TextPosition): MarkRect? {
            val m = glyph.textMatrix
            val advance = advanceOf(glyph, m)
            val xs = FloatArray(4)
            val ys = FloatArray(4)
            var corner = 0
            for (x in floatArrayOf(-CELL_PAD, advance + CELL_PAD)) {
                for (y in floatArrayOf(CELL_BOTTOM, CELL_TOP)) {
                    val pageX = m.getValue(0, 0) * x + m.getValue(1, 0) * y + m.getValue(2, 0)
                    val pageY = m.getValue(0, 1) * x + m.getValue(1, 1) * y + m.getValue(2, 1)
                    val (shownX, shownY) = shown(pageX, pageY)
                    xs[corner] = shownX
                    ys[corner] = shownY
                    corner++
                }
            }
            if (xs.any { it.isNaN() } || ys.any { it.isNaN() }) return null
            return MarkRect(xs.min() / width, ys.min() / height, xs.max() / width, ys.max() / height)
        }

        /** A point in page space, measured on the page as shown, from its top-left. */
        private fun shown(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
            90 -> y to x
            180 -> (boxWidth - x) to y
            270 -> (boxHeight - y) to (boxWidth - x)
            else -> x to (boxHeight - y)
        }

        /**
         * How far the glyph moves the pen, in text space, where 1 is the size the
         * glyph is drawn at. PDFBox has worked out the distance on the page; the
         * matrix's first row says how long one unit of text space is there.
         */
        private fun advanceOf(glyph: TextPosition, m: Matrix): Float {
            val unit = hypot(m.getValue(0, 0), m.getValue(0, 1))
            val advance = if (unit > 0f) glyph.widthDirAdj / unit else 0f
            // A font that reports no widths still has letters on the page.
            return if (advance > 0f && advance.isFinite()) advance else 0.5f * glyph.unicode.orEmpty().length.coerceAtLeast(1)
        }

        private companion object {
            const val CELL_TOP = 0.95f
            const val CELL_BOTTOM = -0.3f
            const val CELL_PAD = 0.08f
        }
    }

    /**
     * Collects a page's text with one [TextPosition] per character, in reading
     * order. Fed one page directly, so it works on a page that is not (or not yet)
     * part of a document's page tree.
     */
    private class Collector : PDFTextStripper() {
        val text = StringBuilder()
        val glyphs = ArrayList<TextPosition?>()

        init {
            sortByPosition = true
            // processPage only reads a page whose number is in range, and nothing
            // numbers a page handed over directly: it stays at zero.
            setStartPage(0)
            output = StringWriter()
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            for (glyph in textPositions) {
                for (character in glyph.unicode ?: continue) {
                    this.text.append(character)
                    glyphs += glyph
                }
            }
        }

        override fun writeWordSeparator() = separate(' ')

        override fun writeLineSeparator() = separate('\n')

        private fun separate(character: Char) {
            text.append(character)
            glyphs += null
        }
    }
}
