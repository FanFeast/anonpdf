package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.Recipe
import io.github.fanfeast.anonpdf.pdf.Recipes
import io.github.fanfeast.anonpdf.ui.common.rememberOpenRecipe
import io.github.fanfeast.anonpdf.ui.common.rememberRecipeStore
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import kotlinx.coroutines.launch

/** Which saved recipe the Run a recipe tool runs, with a way to bring one in from a file. */
@Composable
internal fun RecipePicker(options: ToolOptionsState) {
    val scope = rememberCoroutineScope()
    val store = rememberRecipeStore()
    var recipes by remember { mutableStateOf<List<Recipe>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        recipes = store.list().also { list ->
            // Keep the choice if it is still there; otherwise the only one, if there is only one.
            options.recipe = list.firstOrNull { it == options.recipe } ?: list.singleOrNull()
        }
    }

    val importRecipe = rememberOpenRecipe { text ->
        val decoded = text?.let { runCatching { Recipes.decode(it) } }
        if (decoded == null || decoded.isFailure) {
            problem = decoded?.exceptionOrNull()?.message ?: S.recipeCouldNotRead
        } else {
            problem = null
            val recipe = decoded.getOrThrow()
            scope.launch {
                store.save(recipe)
                recipes = store.list()
                options.recipe = recipe
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(S.recipeChoose, style = MaterialTheme.typography.labelLarge)
        val list = recipes
        when {
            list == null -> Unit
            list.isEmpty() -> InfoNote(S.recipeNoneYet, Modifier.padding(vertical = 8.dp))
            else -> list.forEach { recipe ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = recipe == options.recipe, role = Role.RadioButton) {
                            options.recipe = recipe
                        }
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(selected = recipe == options.recipe, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            recipe.steps.take(3).joinToString(" · ") { it.describe() } +
                                if (recipe.steps.size > 3) " …" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        problem?.let { ErrorNote(it) }
        TextButton(onClick = importRecipe) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Text(S.recipeImport, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
