package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
        val glyphs = PageGlyphs.of(page) ?: return emptyList()
        return regex.findAll(glyphs.text)
            .filter { rule.accepts(it.value) }
            .map { match -> glyphs.boxes(match.range) }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
