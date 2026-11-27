package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** What a comparison found, counted in words. */
data class CompareResult(val added: Int, val removed: Int, val pagesWithChanges: Int) {
    val identical: Boolean get() = added == 0 && removed == 0
}

/**
 * Compares the words of two versions of a document and writes a report: each
 * page of the original beside its counterpart in the changed version, removed
 * words marked red on the left and added words green on the right.
 *
 * Words, not pixels: a document saved again, or by another program, draws the
 * same text differently, and what a reader wants to know is what it now says.
 * Text that is part of a picture, as on a scan, is not compared.
 */
object PdfCompare {

    suspend fun compare(
        original: File,
        changed: File,
        output: File,
        originalPassword: String? = null,
        changedPassword: String? = null,
        onProgress: Progress = {},
    ): CompareResult = withContext(Dispatchers.IO) {
        PdfOps.load(original, originalPassword).use { before ->
            PdfOps.load(changed, changedPassword).use { after ->
                val oldWords = wordsOf(before)
                onProgress(0.15f)
                val newWords = wordsOf(after)
                onProgress(0.3f)
                ensureActive()

                val found = diffWords(oldWords, newWords, before.numberOfPages, after.numberOfPages)
                onProgress(0.5f)

                val sheets = pairPages(before.numberOfPages, after.numberOfPages, found.matches)
                var pagesWithChanges = 0
                PDDocument().use { report ->
                    sheets.forEachIndexed { index, (oldPage, newPage) ->
                        ensureActive()
                        val removed = marksFor(oldWords, found.removed, oldPage)
                        val added = marksFor(newWords, found.added, newPage)
                        if (removed.isNotEmpty() || added.isNotEmpty() || oldPage == null || newPage == null) {
                            pagesWithChanges++
                        }
                        drawSheet(report, before, oldPage, removed, after, newPage, added)
                        onProgress(0.5f + 0.45f * (index + 1f) / sheets.size)
                    }
                    report.save(output)
                }
                CompareResult(
                    added = found.added.count { it },
                    removed = found.removed.count { it },
                    pagesWithChanges = pagesWithChanges,
                )
            }
        }
    }

    // --------------------------------------------------------------- words

    /** A word, the page it is on, and where it is drawn there. */
    internal class Word(val text: String, val page: Int, val boxes: List<MarkRect>)

    internal fun wordsOf(document: PDDocument): List<Word> {
        val words = mutableListOf<Word>()
        for ((index, page) in document.pages.withIndex()) {
            val glyphs = PageGlyphs.of(page) ?: continue
            for (range in wordRanges(glyphs.text)) {
                words += Word(glyphs.text.substring(range), index, glyphs.boxes(range))
            }
        }
        return words
    }

    /**
     * Where the words are in [text]. A word is a run of letters and digits, with
     * the apostrophes, points and commas inside it ("don't", "1,500.00"); any
     * other mark is a word of its own, so "Alice." and "Alice and Bob." differ by
     * two words and not three. Chinese and Japanese put no spaces between words,
     * so each of their characters counts as one: a whole line as one word would
     * mark it all as changed.
     */
    internal fun wordRanges(text: String): List<IntRange> {
        fun spaceless(c: Char) = c in '\u3040'..'\u30ff' || c in '\u3400'..'\u4dbf' ||
            c in '\u4e00'..'\u9fff' || c in '\uf900'..'\ufaff'
        fun wordy(c: Char) = c.isLetterOrDigit() && !spaceless(c)
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                wordy(c) -> {
                    val start = i
                    i++
                    while (i < text.length) {
                        if (wordy(text[i])) {
                            i++
                        } else if (text[i] in INSIDE_WORD && i + 1 < text.length && wordy(text[i + 1])) {
                            i += 2
                        } else {
                            break
                        }
                    }
                    ranges += start until i
                }
                else -> {
                    ranges += i..i
                    i++
                }
            }
        }
        return ranges
    }

    private const val INSIDE_WORD = "'’.,-"

    /**
     * The marks for the changed words on [page]: a run of changed words reads as
     * one change, so its boxes join up along each line instead of leaving gaps
     * between the words.
     */
    private fun marksFor(words: List<Word>, changed: BooleanArray, page: Int?): List<MarkRect> {
        if (page == null) return emptyList()
        val marks = mutableListOf<MarkRect>()
        var joinable = false
        for ((index, word) in words.withIndex()) {
            if (word.page != page) continue
            if (!changed[index]) {
                joinable = false
                continue
            }
            for (box in word.boxes) {
                val last = marks.lastOrNull()
                if (joinable && last != null && onSameLineNear(last, box)) {
                    marks[marks.lastIndex] = MarkRect(
                        minOf(last.left, box.left), minOf(last.top, box.top),
                        maxOf(last.right, box.right), maxOf(last.bottom, box.bottom),
                    )
                } else {
                    marks += box
                }
                joinable = true
            }
        }
        return marks
    }

    /** Whether [b] carries on [a]'s line, no more than a few letters further along. */
    private fun onSameLineNear(a: MarkRect, b: MarkRect): Boolean {
        val down = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (down < 0.5f * minOf(a.height, b.height)) return false
        val gap = maxOf(b.left - a.right, a.left - b.right)
        return gap < 1.5f * maxOf(a.height, b.height)
    }

    // ---------------------------------------------------------------- diff

    /**
     * Which words were removed and added, and which original page each changed
     * page's unchanged words came from (counts by original page, per new page).
     */
    internal class Changes(
        val removed: BooleanArray,
        val added: BooleanArray,
        val matches: List<Map<Int, Int>>,
    )

    internal fun diffWords(old: List<Word>, new: List<Word>, oldPages: Int, newPages: Int): Changes {
        val removed = BooleanArray(old.size)
        val added = BooleanArray(new.size)
        val matches = List(newPages) { HashMap<Int, Int>() }
        fun matched(i: Int, j: Int) {
            val counts = matches[new[j].page]
            counts[old[i].page] = (counts[old[i].page] ?: 0) + 1
        }

        val whole = shortestEdit(old.map { it.text }, new.map { it.text }, 0, old.size, 0, new.size)
        if (whole != null) {
            whole.apply(removed, added, ::matched)
        } else {
            // Too different to line up as one: compare page by page instead.
            for (page in 0 until maxOf(oldPages, newPages)) {
                val oldRange = old.indices.filter { old[it].page == page }
                val newRange = new.indices.filter { new[it].page == page }
                val aStart = oldRange.firstOrNull() ?: old.size
                val bStart = newRange.firstOrNull() ?: new.size
                val edit = shortestEdit(
                    old.map { it.text }, new.map { it.text },
                    aStart, aStart + oldRange.size, bStart, bStart + newRange.size,
                )
                if (edit != null) {
                    edit.apply(removed, added, ::matched)
                } else {
                    oldRange.forEach { removed[it] = true }
                    newRange.forEach { added[it] = true }
                }
            }
        }
        return Changes(removed, added, matches)
    }

    /** The edits that turn one run of words into another, as Myers' algorithm finds them. */
    internal class Edit(
        private val deletes: List<Int>,
        private val inserts: List<Int>,
        private val equal: List<Pair<Int, Int>>,
    ) {
        fun apply(removed: BooleanArray, added: BooleanArray, matched: (Int, Int) -> Unit) {
            deletes.forEach { removed[it] = true }
            inserts.forEach { added[it] = true }
            equal.forEach { (i, j) -> matched(i, j) }
        }
    }

    /**
     * The shortest list of deletions and insertions turning a[aFrom, aTo) into
     * b[bFrom, bTo), or null if it would take more than [MAX_EDITS]: past that
     * the two are not versions of one text, and the search would cost too much.
     */
    internal fun shortestEdit(a: List<String>, b: List<String>, aFrom: Int, aTo: Int, bFrom: Int, bTo: Int): Edit? {
        val equal = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (aFrom + start < aTo && bFrom + start < bTo && a[aFrom + start] == b[bFrom + start]) {
            equal += (aFrom + start) to (bFrom + start)
            start++
        }
        var end = 0
        while (aTo - end > aFrom + start && bTo - end > bFrom + start && a[aTo - end - 1] == b[bTo - end - 1]) {
            equal += (aTo - end - 1) to (bTo - end - 1)
            end++
        }
        val a0 = aFrom + start
        val b0 = bFrom + start
        val n = aTo - end - a0
        val m = bTo - end - b0
        if (n == 0 || m == 0) {
            return Edit((a0 until a0 + n).toList(), (b0 until b0 + m).toList(), equal)
        }

        val limit = minOf(n + m, MAX_EDITS)
        val offset = limit + 1
        val v = IntArray(2 * limit + 3)
        val trace = ArrayList<IntArray>()
        var steps = -1
        search@ for (d in 0..limit) {
            // What the previous step reached on each diagonal this one starts from.
            trace += IntArray(2 * d + 1) { v[offset - d + it] }
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    v[offset + k + 1]
                } else {
                    v[offset + k - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && a[a0 + x] == b[b0 + y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) {
                    steps = d
                    break@search
                }
                k += 2
            }
        }
        if (steps < 0) return null

        val deletes = mutableListOf<Int>()
        val inserts = mutableListOf<Int>()
        var x = n
        var y = m
        for (d in steps downTo 1) {
            val before = trace[d]
            fun reached(k: Int) = before[k + d]
            val k = x - y
            val fromK = if (k == -d || (k != d && reached(k - 1) < reached(k + 1))) k + 1 else k - 1
            val fromX = reached(fromK)
            val fromY = fromX - fromK
            while (x > fromX && y > fromY) {
                x--
                y--
                equal += (a0 + x) to (b0 + y)
            }
            if (x == fromX) inserts += b0 + fromY else deletes += a0 + fromX
            x = fromX
            y = fromY
        }
        while (x > 0 && y > 0) {
            x--
            y--
            equal += (a0 + x) to (b0 + y)
        }
        return Edit(deletes, inserts, equal)
    }

    /**
     * Enough for a heavily revised contract; the search's memory grows with the
     * square of this, so it is also what keeps a phone from running out.
     */
    private const val MAX_EDITS = 2000

    // --------------------------------------------------------------- pages

    /**
     * Which original page sits beside which changed page. A changed page goes
     * beside the original page most of its unchanged words came from, keeping
     * both in order, so an inserted or deleted page sits alone rather than
     * shifting every page after it out of line.
     */
    internal fun pairPages(oldPages: Int, newPages: Int, matches: List<Map<Int, Int>>): List<Pair<Int?, Int?>> {
        val partnered = matches.flatMap { it.keys }.toSet()
        val sheets = mutableListOf<Pair<Int?, Int?>>()
        var nextOld = 0
        for (newPage in 0 until newPages) {
            val best = matches[newPage].maxByOrNull { it.value }?.key
            // A page with no words in common, such as a picture, takes the next
            // original page that nothing else claimed.
            val partner = best?.takeIf { it >= nextOld }
                ?: nextOld.takeIf { best == null && it < oldPages && it !in partnered }
            if (partner == null) {
                sheets += null to newPage
                continue
            }
            while (nextOld < partner) sheets += nextOld++ to null
            sheets += partner to newPage
            nextOld = partner + 1
        }
        while (nextOld < oldPages) sheets += nextOld++ to null
        return sheets
    }

    // -------------------------------------------------------------- report

    private val SHEET = PDRectangle(842f, 595f)
    private const val MARGIN = 24f
    private const val LABEL_SPACE = 20f

    private fun drawSheet(
        report: PDDocument,
        before: PDDocument,
        oldPage: Int?,
        removed: List<MarkRect>,
        after: PDDocument,
        newPage: Int?,
        added: List<MarkRect>,
    ) {
        val sheet = PDPage(SHEET)
        report.addPage(sheet)
        val half = (SHEET.width - 3 * MARGIN) / 2f
        PDPageContentStream(report, sheet).use { stream ->
            drawSide(
                report, stream, before, oldPage, removed, MARGIN, half,
                label = oldPage?.let { S.compareOriginal(it + 1) } ?: S.compareNotInOriginal,
                red = 0.9f, green = 0.15f, blue = 0.15f,
            )
            drawSide(
                report, stream, after, newPage, added, 2 * MARGIN + half, half,
                label = newPage?.let { S.compareChanged(it + 1) } ?: S.compareNotInChanged,
                red = 0.1f, green = 0.7f, blue = 0.25f,
            )
        }
    }

    private fun drawSide(
        report: PDDocument,
        stream: PDPageContentStream,
        source: PDDocument,
        pageIndex: Int?,
        marks: List<MarkRect>,
        left: Float,
        width: Float,
        label: String,
        red: Float,
        green: Float,
        blue: Float,
    ) {
        val top = SHEET.height - MARGIN
        val line = PdfText.line(label, PDType1Font.HELVETICA, bold = false, fontSize = 10f)
        stream.setNonStrokingColor(0.3f, 0.3f, 0.3f)
        line.draw(stream, left, top - 10f)
        val page = pageIndex?.let(source::getPage) ?: return

        val rotation = PdfOps.normalizeRotation(page.rotation)
        val box = page.cropBox
        val quarterTurned = rotation == 90 || rotation == 270
        val shownWidth = if (quarterTurned) box.height else box.width
        val shownHeight = if (quarterTurned) box.width else box.height
        val height = top - LABEL_SPACE - MARGIN
        val scale = minOf(width / shownWidth, height / shownHeight)
        val x = left
        val y = top - LABEL_SPACE - shownHeight * scale

        // A thin frame, so a white page shows where it ends.
        stream.saveGraphicsState()
        stream.setStrokingColor(0.75f, 0.75f, 0.75f)
        stream.setLineWidth(0.5f)
        stream.addRect(x, y, shownWidth * scale, shownHeight * scale)
        stream.stroke()
        stream.restoreGraphicsState()

        stream.saveGraphicsState()
        stream.transform(Matrix.getTranslateInstance(x, y))
        stream.transform(Matrix.getScaleInstance(scale, scale))
        // From the page's own space to the page as shown, bottom-left at 0, 0.
        stream.transform(
            when (rotation) {
                90 -> Matrix(0f, -1f, 1f, 0f, 0f, box.width)
                180 -> Matrix(-1f, 0f, 0f, -1f, box.width, box.height)
                270 -> Matrix(0f, 1f, -1f, 0f, box.height, 0f)
                else -> Matrix()
            },
        )
        stream.transform(Matrix.getTranslateInstance(-box.lowerLeftX, -box.lowerLeftY))
        stream.drawForm(asForm(report, page))
        stream.restoreGraphicsState()

        if (marks.isEmpty()) return
        stream.saveGraphicsState()
        stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f })
        stream.setNonStrokingColor(red, green, blue)
        for (mark in marks) {
            val rect = mark.tidied()
            stream.addRect(
                x + rect.left * shownWidth * scale,
                y + (1f - rect.bottom) * shownHeight * scale,
                rect.width * shownWidth * scale,
                rect.height * shownHeight * scale,
            )
        }
        stream.fill()
        stream.restoreGraphicsState()
    }

    /** The page's drawing as a reusable form in [report], clipped to what the page shows. */
    private fun asForm(report: PDDocument, page: PDPage): PDFormXObject {
        val cloner = PDFCloneUtility(report)
        val form = PDFormXObject(PDStream(report, page.contents, com.tom_roush.pdfbox.cos.COSName.FLATE_DECODE))
        page.resources?.let { resources ->
            form.resources = PDResources(cloner.cloneForNewDocument(resources.cosObject) as COSDictionary)
        }
        form.bBox = page.cropBox
        return form
    }
}
