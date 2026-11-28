package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.Recipe
import io.github.fanfeast.anonpdf.pdf.Recipes
import io.github.fanfeast.anonpdf.ui.common.rememberExportRecipe
import io.github.fanfeast.anonpdf.ui.common.rememberOpenRecipe
import io.github.fanfeast.anonpdf.ui.common.rememberRecipeStore
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import kotlinx.coroutines.launch

/**
 * Saves the edits made so far as a recipe, and runs saved recipes on this
 * document. Running one adds its steps to the edit list like any other edits,
 * so the preview shows them and undo takes them back.
 */
@Composable
fun RecipesDialog(
    state: EditorState,
    onRun: (Recipe) -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = rememberRecipeStore()
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    suspend fun reload() { recipes = store.list() }
    LaunchedEffect(Unit) { reload() }

    val recorded = remember(state.ops) { Recipes.record(state.pageCount, state.ops) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    val importRecipe = rememberOpenRecipe { text ->
        val recipe = text?.let { runCatching { Recipes.decode(it) } }
        when {
            recipe == null -> onMessage(S.recipeCouldNotRead)
            recipe.isFailure -> onMessage(recipe.exceptionOrNull()?.message ?: S.recipeCouldNotRead)
            else -> scope.launch {
                store.save(recipe.getOrThrow())
                reload()
                onMessage(S.recipeImported(recipe.getOrThrow().name))
            }
        }
    }
    val exportRecipe = rememberExportRecipe { ok -> onMessage(if (ok) S.recipeExported else S.couldNotSaveFile) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.recipesTitle) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(S.recipesHint, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))

                if (state.ops.isNotEmpty()) {
                    if (recorded.steps.isEmpty()) {
                        InfoNote(S.recipeNothingToSave)
                    } else if (!naming) {
                        FilledTonalButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(S.recipeSaveCurrent)
                        }
                    } else {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(80) },
                            label = { Text(S.recipeName) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            S.recipeSteps(recorded.steps.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        recorded.steps.forEach { step ->
                            Text("• ${step.describe()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (recorded.skipped > 0) {
                            Spacer(Modifier.height(6.dp))
                            InfoNote(S.recipeSkipped(recorded.skipped))
                        }
                        if (recorded.steps.any { it.isPlaced }) {
                            Spacer(Modifier.height(6.dp))
                            InfoNote(S.recipePlacedNote)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { naming = false }, modifier = Modifier.weight(1f)) { Text(S.cancel) }
                            Button(
                                onClick = {
                                    val recipe = Recipe(name.trim(), recorded.steps)
                                    scope.launch {
                                        store.save(recipe)
                                        reload()
                                        naming = false
                                        name = ""
                                        onMessage(S.recipeSaved(recipe.name))
                                    }
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { Text(S.recipeSave) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                if (recipes.isEmpty()) {
                    Text(
                        S.recipesEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    recipes.forEach { recipe ->
                        RecipeRow(
                            recipe = recipe,
                            onRun = { onRun(recipe) },
                            onExport = { exportRecipe(recipe) },
                            onDelete = {
                                scope.launch {
                                    store.delete(recipe.name)
                                    reload()
                                    onMessage(S.recipeDeleted(recipe.name))
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = importRecipe) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Text(S.recipeImport, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.close) } },
    )
}

/** A saved recipe: its name and size, its steps on a tap, and what can be done with it. */
@Composable
fun RecipeRow(
    recipe: Recipe,
    onRun: (() -> Unit)?,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { open = !open }
                    .padding(vertical = 6.dp),
            ) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    S.recipeSteps(recipe.steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onExport) { Icon(Icons.Filled.FileDownload, contentDescription = S.recipeExport) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = S.recipeDelete) }
            onRun?.let { run -> FilledTonalButton(onClick = run) { Text(S.recipeRun) } }
        }
        if (open) {
            recipe.steps.forEach { step ->
                Text(
                    "• ${step.describe()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
