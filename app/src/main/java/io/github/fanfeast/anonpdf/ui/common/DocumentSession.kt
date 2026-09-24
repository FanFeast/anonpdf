package io.github.fanfeast.anonpdf.ui.common

import android.net.Uri
import android.util.SizeF
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.PdfRasterizer
import io.github.fanfeast.anonpdf.pdf.PdfWrongPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * An open document plus the renderer for its pages.
 *
 * Shared by the screens that need page previews (organise, sign) so the
 * copy-to-cache, unlock-if-needed, open-renderer dance lives in one place.
 */
class DocumentSession(
    val uri: Uri,
    val name: String,
    /** The original file, still encrypted if it arrived that way. */
    val sourceFile: File,
    val rasterizer: PdfRasterizer,
    val pageSizes: List<SizeF>,
    val password: String?,
    private val decryptedCopy: File?,
) : Closeable {

    val pageCount: Int get() = pageSizes.size

    override fun close() {
        rasterizer.close()
        decryptedCopy?.delete()
    }
}

sealed interface OpenOutcome {
    data class Ready(val session: DocumentSession) : OpenOutcome
    data object NeedsPassword : OpenOutcome
    data class WrongPassword(val message: String) : OpenOutcome
    data class Failed(val message: String) : OpenOutcome
}

/**
 * Copies [uri] into private cache, unlocks it if [password] is supplied, and
 * opens a renderer over it.
 */
suspend fun openDocumentSession(
    store: DocumentStore,
    uri: Uri,
    password: String? = null,
    existingFile: File? = null,
): OpenOutcome = try {
    val name = store.displayName(uri)
    val source = existingFile ?: store.materialize(uri, name)

    if (password == null && PdfOps.isPasswordProtected(source)) {
        OpenOutcome.NeedsPassword
    } else {
        // The platform renderer refuses encrypted files outright, so when we hold
        // the password we render from a decrypted scratch copy instead.
        val decrypted = if (password == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                File(source.parentFile, "session-${System.nanoTime()}.pdf").also { plain ->
                    PdfOps.load(source, password).use { document ->
                        document.isAllSecurityToBeRemoved = true
                        document.save(plain)
                    }
                }
            }
        }
        val rasterizer = withContext(Dispatchers.IO) {
            PdfRasterizer.open(decrypted ?: source)
        }
        // If measuring is interrupted, close the renderer rather than leak it.
        val pageSizes = try {
            rasterizer.pageSizes()
        } catch (t: Throwable) {
            rasterizer.close()
            decrypted?.delete()
            throw t
        }
        OpenOutcome.Ready(
            DocumentSession(
                uri = uri,
                name = name,
                sourceFile = source,
                rasterizer = rasterizer,
                pageSizes = pageSizes,
                password = password,
                decryptedCopy = decrypted,
            ),
        )
    }
} catch (t: PdfWrongPasswordException) {
    OpenOutcome.WrongPassword("That password did not work.")
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    OpenOutcome.Failed(
        t.message?.takeIf { it.isNotBlank() && it.length < 240 }
            ?: "This file could not be opened as a PDF.",
    )
}
