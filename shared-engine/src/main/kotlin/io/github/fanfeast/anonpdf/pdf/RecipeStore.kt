package io.github.fanfeast.anonpdf.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The user's saved recipes: one small file each, in the app's own storage.
 *
 * Names are the user's, in any script, so a file is named after a safe version of
 * the name plus a hash of the real one; the real name lives inside the file.
 * Saving under a name that already exists replaces that recipe.
 */
class RecipeStore(private val dir: File) {

    suspend fun list(): List<Recipe> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.name.endsWith(".${Recipes.EXTENSION}") }.orEmpty()
            .mapNotNull { runCatching { Recipes.decode(it.readText()) }.getOrNull() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun save(recipe: Recipe) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileFor(recipe.name)
        val temp = File(dir, "${target.name}.tmp")
        temp.writeText(Recipes.encode(recipe))
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        fileFor(name).delete()
    }

    private fun fileFor(name: String): File {
        val hash = name.hashCode().toUInt().toString(16)
        return File(dir, "${FileNames.sanitize(name).take(40)}-$hash.${Recipes.EXTENSION}")
    }
}
