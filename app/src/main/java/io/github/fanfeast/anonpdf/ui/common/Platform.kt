package io.github.fanfeast.anonpdf.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.OcrPacks
import io.github.fanfeast.anonpdf.pdf.PageImage
import io.github.fanfeast.anonpdf.pdf.Recipe
import io.github.fanfeast.anonpdf.pdf.RecipeStore
import io.github.fanfeast.anonpdf.pdf.Recipes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

/** Opens the system picker for any number of text and Markdown files, for Text to PDF. */
@Composable
fun rememberOpenTextFiles(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) onPicked(it)
    }
    // Markdown is labelled text/markdown by some providers and a bare binary by others.
    return { launcher.launch(arrayOf("text/*", "application/octet-stream")) }
}

/** Opens the system picker for any number of images, for Images to PDF. */
@Composable
fun rememberOpenImages(onPicked: (List<DocumentRef>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) onPicked(it)
    }
    return { launcher.launch(arrayOf("image/*")) }
}

@Composable
fun rememberOcrPacks(): OcrPacks {
    val context = LocalContext.current
    return remember { OcrPacks(File(context.filesDir, "tessdata")) }
}

/** Opens the system picker for OCR language packs, any number at once. */
@Composable
fun rememberOpenOcrPacks(onPicked: (List<PickedFile>) -> Unit): () -> Unit = rememberOpenAnyFiles(onPicked)

/** Opens the system picker for files of any kind, any number at once. */
@Composable
fun rememberOpenAnyFiles(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = rememberDocumentStore()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                onPicked(
                    uris.map { uri ->
                        PickedFile(store.displayName(uri)) {
                            context.contentResolver.openInputStream(uri) ?: error(S.couldNotOpenFile)
                        }
                    },
                )
            }
        }
    }
    // Any type: attachments are anything, and a .traineddata file has no type of
    // its own (a language pack is checked when it arrives).
    return { launcher.launch(arrayOf("*/*")) }
}

/** Saved recipes live in the app's own files, which are kept out of backups like everything else. */
@Composable
fun rememberRecipeStore(): RecipeStore {
    val context = LocalContext.current
    return remember { RecipeStore(File(context.filesDir, "recipes")) }
}

/** Opens the system picker for a recipe file; [onPicked] gets its text, or null if it couldn't be read. */
@Composable
fun rememberOpenRecipe(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                onPicked(
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                // A recipe is a few kilobytes; anything far bigger is something else.
                                val bytes = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (bytes.size() <= MAX_RECIPE_BYTES) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    bytes.write(buffer, 0, read)
                                }
                                bytes.takeIf { it.size() <= MAX_RECIPE_BYTES }?.toString(Charsets.UTF_8.name())
                            }
                        }.getOrNull()
                    },
                )
            }
        }
    }
    // Providers label JSON inconsistently, so offer anything and let the reader decide.
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}

/** Saves a recipe wherever the user picks; [onDone] says whether it worked. */
@Composable
fun rememberExportRecipe(onDone: (Boolean) -> Unit): (Recipe) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Recipe?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val recipe = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri != null) {
            scope.launch {
                onDone(
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri, "wt")!!.use {
                                it.write(Recipes.encode(recipe).toByteArray())
                            }
                        }.isSuccess
                    },
                )
            }
        }
    }
    return { recipe ->
        pending = recipe
        launcher.launch("${DocumentStore.sanitize(recipe.name)}.${Recipes.EXTENSION}")
    }
}

private const val MAX_RECIPE_BYTES = 1 shl 20

/** The file's name as far as the reference shows it, without asking the provider. */
fun DocumentRef.fileName(): String? = lastPathSegment?.substringAfterLast('/')

fun PageImage.toImageBitmap(): ImageBitmap = asImageBitmap()

fun ImageBitmap.toPageImage(): PageImage = asAndroidBitmap()

/** How much memory the image's pixels take, for sizing caches. */
val PageImage.memoryBytes: Int get() = byteCount
