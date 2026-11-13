package io.github.fanfeast.anonpdf.pdf

import io.github.fanfeast.anonpdf.i18n.S

/**
 * Parses the "1-3, 5, 9-12" notation people already expect from print dialogs.
 *
 * Input is 1-based because that is what the user sees on screen; output is
 * 0-based because that is what PDFBox and PdfRenderer index by.
 */
object PageRanges {

    /** @throws IllegalArgumentException with a message fit to show the user. */
    fun parse(text: String, pageCount: Int): List<IntRange> {
        require(pageCount > 0) { S.noPagesInDocument }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { S.enterPages }

        return trimmed.split(',').mapNotNull { rawPart ->
            val part = rawPart.trim()
            if (part.isEmpty()) return@mapNotNull null

            if (part.contains('-')) {
                val halves = part.split('-')
                require(halves.size == 2) { S.notARange(part) }
                val start = halves[0].trim().toIntOrNull()
                val end = halves[1].trim().toIntOrNull()
                require(start != null && end != null) { S.notARange(part) }
                require(start in 1..pageCount && end in 1..pageCount) {
                    S.onlyHasPages(pageCount)
                }
                require(start <= end) { S.runsBackwards(part) }
                (start - 1)..(end - 1)
            } else {
                val single = part.toIntOrNull()
                require(single != null) { S.notAPageNumber(part) }
                require(single in 1..pageCount) {
                    S.onlyHasPages(pageCount)
                }
                (single - 1)..(single - 1)
            }
        }.also {
            require(it.isNotEmpty()) { S.enterPages }
        }
    }

    /** Flattens ranges into a de-duplicated, order-preserving page list. */
    fun toPageList(ranges: List<IntRange>): List<Int> {
        val seen = LinkedHashSet<Int>()
        ranges.forEach { range -> range.forEach { seen.add(it) } }
        return seen.toList()
    }

    /** Renders 0-based indices back into human notation, collapsing runs. */
    fun describe(pages: List<Int>): String {
        if (pages.isEmpty()) return S.rangeNone
        val sorted = pages.distinct().sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (page in sorted.drop(1)) {
            if (page == previous + 1) {
                previous = page
                continue
            }
            parts += formatRun(start, previous)
            start = page
            previous = page
        }
        parts += formatRun(start, previous)
        return parts.joinToString(", ")
    }

    private fun formatRun(start: Int, end: Int): String =
        if (start == end) "${start + 1}" else "${start + 1}-${end + 1}"
}
