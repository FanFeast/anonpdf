package io.github.fanfeast.anonpdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImageFiles
import io.github.fanfeast.anonpdf.pdf.PageImage
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// What the shared screens (shared-ui/) need from desktop. The Android app has
// its own file with the same names.

/** Where a document comes from: a file the user chose. */
typealias DocumentRef = File

fun documentRefOf(file: File): DocumentRef = file

@Composable
fun rememberDocumentStore(): DocumentStore = remember { DocumentStore() }

/** Opens the system file dialog for one PDF; [onPicked] runs only if the user chose one. */
@Composable
fun rememberOpenPdf(onPicked: (DocumentRef) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember { { choosePdfs(multiple = false).firstOrNull()?.let(callback) } }
}

/** Opens the system file dialog for any number of PDFs. */
@Composable
fun rememberOpenPdfs(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember { { callback(choosePdfs(multiple = true)) } }
}

/** Opens the system file dialog for any number of images, for Images to PDF. */
@Composable
fun rememberOpenImages(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            chooseFiles(multiple = true, title = "Add images", extensions = ImageFiles.extensions)
                .takeIf { it.isNotEmpty() }
                ?.let(callback)
        }
    }
}

/** Shows the platform's own open dialog. Blocks until the user closes it. */
fun choosePdfs(multiple: Boolean, title: String = "Open a PDF"): List<File> =
    chooseFiles(multiple, title, setOf("pdf"))

private fun chooseFiles(multiple: Boolean, title: String, extensions: Set<String>): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = multiple
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        isVisible = true
    }
    return dialog.files.orEmpty().filter { it.isFile }
}

fun DocumentRef.fileName(): String? = name

fun PageImage.toImageBitmap(): ImageBitmap = toComposeImageBitmap()

fun ImageBitmap.toPageImage(): PageImage = toAwtImage()

/** How much memory the image's pixels take, for sizing caches. */
val PageImage.memoryBytes: Int get() = width * height * 4
