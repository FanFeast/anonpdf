package io.github.fanfeast.anonpdf.pdf

/** File-name helpers shared by every platform's document store. */
object FileNames {
    /** Keeps a name safe to use as a file name anywhere: no paths, no odd characters. */
    fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "document" }

    /** "report.pdf" -> "report", so we can build "report-compressed.pdf". */
    fun stem(name: String): String = name.substringBeforeLast('.', name).ifEmpty { "document" }
}
