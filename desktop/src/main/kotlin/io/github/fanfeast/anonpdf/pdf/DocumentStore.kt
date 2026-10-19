package io.github.fanfeast.anonpdf.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves documents between the user's folders and the app's private scratch
 * folder: the desktop counterpart of the Android store, with the same API.
 *
 * Opening a document copies it into scratch, so the original is never written
 * to; saving copies a finished file out to where the user chose. [root] is the
 * owner-only folder set up by `PrivateScratch`, and it is deleted on exit.
 */
class DocumentStore(private val root: File = File(System.getProperty("java.io.tmpdir"))) {

    private val workDir: File = File(root, "work").apply { mkdirs() }
    private val outDir: File = File(root, "out").apply { mkdirs() }

    /** Private scratch space for previews and other short-lived files. */
    val scratchDir: File get() = root

    /** Copies [file] into private scratch and returns the copy. */
    suspend fun materialize(file: File, fileName: String = "input.pdf"): File =
        withContext(Dispatchers.IO) {
            val target = File(workDir, "${System.nanoTime()}-${sanitize(fileName)}")
            file.copyTo(target)
        }

    /** Allocates a scratch file for an operation's result. */
    fun newOutputFile(baseName: String, extension: String): File {
        outDir.mkdirs()
        return File(outDir, "${sanitize(baseName)}.$extension")
    }

    /** Writes [source] to [destination], replacing whatever was there. */
    suspend fun export(source: File, destination: File) = withContext(Dispatchers.IO) {
        source.copyTo(destination, overwrite = true)
        Unit
    }

    suspend fun displayName(file: File): String = file.name

    companion object {
        fun sanitize(name: String): String = FileNames.sanitize(name)

        /** "report.pdf" -> "report", so we can build "report-compressed.pdf". */
        fun stem(name: String): String = FileNames.stem(name)
    }
}
