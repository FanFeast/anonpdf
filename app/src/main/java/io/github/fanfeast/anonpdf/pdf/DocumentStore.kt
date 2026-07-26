package io.github.fanfeast.anonpdf.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves bytes between the Storage Access Framework and our private cache.
 *
 * Everything we touch lives in [Context.getCacheDir], which is app-private and
 * wiped by [clearWorkspace] when the user leaves a document. We never ask for a
 * storage permission and never hold a path outside our own sandbox, so the only
 * files AnonPDF can read are the ones the user explicitly handed us.
 */
class DocumentStore(private val context: Context) {

    private val workDir: File = File(context.cacheDir, "work").apply { mkdirs() }
    private val outDir: File = File(context.cacheDir, "out").apply { mkdirs() }

    /**
     * Copies the contents of [uri] into a private file we can seek over.
     *
     * Both PdfRenderer and PDFBox want a real seekable file, and some document
     * providers (cloud-backed ones especially) hand back a one-shot pipe. Copying
     * once up front makes every downstream operation behave identically.
     */
    suspend fun materialize(uri: Uri, fileName: String = "input.pdf"): File =
        withContext(Dispatchers.IO) {
            val target = File(workDir, "${System.nanoTime()}-${sanitize(fileName)}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: error("Could not open the selected file.")
            target
        }

    /** Allocates a scratch file for an operation's result. */
    fun newOutputFile(baseName: String, extension: String): File {
        outDir.mkdirs()
        return File(outDir, "${sanitize(baseName)}.$extension")
    }

    /** Creates a directory for operations that emit more than one file. */
    fun newOutputDir(name: String): File =
        File(outDir, "${sanitize(name)}-${System.nanoTime()}").apply { mkdirs() }

    /** Streams a finished result out to the location the user picked. */
    suspend fun export(source: File, destination: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        } ?: error("Could not write to the selected location.")
    }

    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    suspend fun sizeBytes(uri: Uri): Long = withContext(Dispatchers.IO) {
        val fromCursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (fromCursor != null) return@withContext fromCursor

        // Not every provider fills in SIZE (a plain file:// URI never does).
        // Asking the descriptor how long it is works where the column does not.
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0 }
            }
        }.getOrNull() ?: -1L
    }

    /** Drops every scratch file. Called when a document is closed. */
    fun clearWorkspace() {
        workDir.deleteRecursively()
        outDir.deleteRecursively()
        workDir.mkdirs()
        outDir.mkdirs()
    }

    companion object {
        fun sanitize(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "document" }

        /** "report.pdf" -> "report", so we can build "report-compressed.pdf". */
        fun stem(name: String): String = name.substringBeforeLast('.', name).ifEmpty { "document" }
    }
}
