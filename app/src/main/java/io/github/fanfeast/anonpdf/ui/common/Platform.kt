package io.github.fanfeast.anonpdf.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.PageImage
import java.io.File

// What the shared screens (shared-ui/) need from Android. The desktop app has
// its own file with the same names.

/** Where a document comes from: a content or file URI. */
typealias DocumentRef = Uri

fun documentRefOf(file: File): DocumentRef = "file://${file.absolutePath}".toUri()

@Composable
fun rememberDocumentStore(): DocumentStore {
    val context = LocalContext.current
    return remember { DocumentStore(context) }
}

/** Opens the system picker for one PDF; [onPicked] runs only if the user chose one. */
@Composable
fun rememberOpenPdf(onPicked: (DocumentRef) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

/** Opens the system picker for any number of PDFs. */
@Composable
fun rememberOpenPdfs(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        onPicked(it)
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

/** Opens the system picker for any number of images, for Images to PDF. */
@Composable
fun rememberOpenImages(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) onPicked(it)
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/** The file's name as far as the reference shows it, without asking the provider. */
fun DocumentRef.fileName(): String? = lastPathSegment?.substringAfterLast('/')

fun PageImage.toImageBitmap(): ImageBitmap = asImageBitmap()

fun ImageBitmap.toPageImage(): PageImage = asAndroidBitmap()

/** How much memory the image's pixels take, for sizing caches. */
val PageImage.memoryBytes: Int get() = byteCount
