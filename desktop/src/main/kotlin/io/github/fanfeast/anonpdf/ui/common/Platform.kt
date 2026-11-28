package io.github.fanfeast.anonpdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImageFiles
import io.github.fanfeast.anonpdf.pdf.OcrPacks
import io.github.fanfeast.anonpdf.pdf.PageImage
import io.github.fanfeast.anonpdf.pdf.Recipe
import io.github.fanfeast.anonpdf.pdf.RecipeStore
import io.github.fanfeast.anonpdf.pdf.Recipes
import io.github.fanfeast.anonpdf.desktop.AppFolders
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
            chooseFiles(multiple = true, title = S.addImages, extensions = ImageFiles.extensions)
                .takeIf { it.isNotEmpty() }
                ?.let(callback)
        }
    }
}

/** Shows the platform's own open dialog. Blocks until the user closes it. */
fun choosePdfs(multiple: Boolean, title: String = S.openAPdf): List<File> =
    chooseFiles(multiple, title, setOf("pdf"))

private fun chooseFiles(multiple: Boolean, title: String, extensions: Set<String>): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = multiple
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        isVisible = true
    }
    return dialog.files.orEmpty().filter { it.isFile }
}

@Composable
fun rememberOcrPacks(): OcrPacks = remember { OcrPacks(AppFolders.ocrPacks) }

/** Opens the system file dialog for OCR language packs, any number at once. */
@Composable
fun rememberOpenOcrPacks(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            chooseFiles(multiple = true, title = S.ocrAddPack, extensions = setOf("traineddata"))
                .takeIf { it.isNotEmpty() }
                ?.let { files -> callback(files.map { file -> PickedFile(file.name) { file.inputStream() } }) }
        }
    }
}

@Composable
fun rememberRecipeStore(): RecipeStore = remember { RecipeStore(AppFolders.recipes) }

/** Opens the system file dialog for a recipe; [onPicked] gets its text, or null if it couldn't be read. */
@Composable
fun rememberOpenRecipe(onPicked: (String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            chooseFiles(multiple = false, title = S.recipeImport, extensions = setOf("json")).firstOrNull()?.let { file ->
                // A recipe is a few kilobytes; anything far bigger is something else.
                callback(runCatching { file.takeIf { it.length() <= 1 shl 20 }?.readText() }.getOrNull())
            }
        }
    }
}

/** Saves a recipe wherever the user picks; [onDone] says whether it worked. */
@Composable
fun rememberExportRecipe(onDone: (Boolean) -> Unit): (Recipe) -> Unit {
    val callback by rememberUpdatedState(onDone)
    return remember {
        { recipe ->
            val dialog = FileDialog(null as Frame?, S.recipeExport, FileDialog.SAVE).apply {
                file = "${DocumentStore.sanitize(recipe.name)}.${Recipes.EXTENSION}"
                isVisible = true
            }
            dialog.file?.let { name ->
                val target = File(dialog.directory, if (name.endsWith(".json")) name else "$name.json")
                callback(runCatching { target.writeText(Recipes.encode(recipe)) }.isSuccess)
            }
        }
    }
}

fun DocumentRef.fileName(): String? = name

fun PageImage.toImageBitmap(): ImageBitmap = toComposeImageBitmap()

fun ImageBitmap.toPageImage(): PageImage = toAwtImage()

/** How much memory the image's pixels take, for sizing caches. */
val PageImage.memoryBytes: Int get() = width * height * 4
