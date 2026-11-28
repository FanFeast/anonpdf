package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.fanfeast.anonpdf.pdf.OcrPack
import io.github.fanfeast.anonpdf.pdf.PdfOcr
import io.github.fanfeast.anonpdf.ui.common.rememberOcrPacks
import io.github.fanfeast.anonpdf.ui.common.rememberOpenOcrPacks
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Which languages to read, and the language packs themselves: added from a file
 * the user fetched, removed with a tap. The app never fetches one itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OcrOptions(options: ToolOptionsState) {
    if (!PdfOcr.isAvailable) {
        ErrorNote(S.ocrUnavailable)
        return
    }
    val scope = rememberCoroutineScope()
    val packs = rememberOcrPacks()
    var installed by remember { mutableStateOf<List<OcrPack>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        val list = packs.installed()
        installed = list
        val codes = list.map { it.code }.toSet()
        // Drop what is gone; with one pack and nothing chosen, choose it.
        options.ocrLanguages = options.ocrLanguages.filter { it in codes }
            .ifEmpty { listOfNotNull(list.singleOrNull()?.code) }
    }
    LaunchedEffect(Unit) { reload() }

    val addPacks = rememberOpenOcrPacks { picked ->
        scope.launch {
            busy = true
            problem = null
            val failed = mutableListOf<String>()
            picked.forEach { file ->
                try {
                    val pack = file.open().use { packs.install(file.name, it) }
                    if (pack.code !in options.ocrLanguages) options.ocrLanguages = options.ocrLanguages + pack.code
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    failed += file.name
                }
            }
            reload()
            if (failed.isNotEmpty()) problem = S.ocrNotAPackNamed(failed.joinToString(", "))
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(S.ocrLanguages, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        val list = installed
        when {
            list == null -> Unit
            list.isEmpty() -> InfoNote(S.ocrNoPacks)
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { pack ->
                        val chosen = pack.code in options.ocrLanguages
                        FilterChip(
                            selected = chosen,
                            onClick = {
                                options.ocrLanguages =
                                    if (chosen) options.ocrLanguages - pack.code else options.ocrLanguages + pack.code
                            },
                            label = { Text(pack.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    S.ocrPacksHere,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                list.forEach { pack ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pack.label} · ${pack.code} · ${formatBytes(pack.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            scope.launch {
                                packs.remove(pack.code)
                                reload()
                            }
                        }) { Icon(Icons.Filled.Close, contentDescription = S.ocrRemovePack(pack.label)) }
                    }
                }
            }
        }
        problem?.let { ErrorNote(it) }
        TextButton(onClick = addPacks, enabled = !busy) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Text(S.ocrAddPack, modifier = Modifier.padding(start = 8.dp))
        }
        if (!list.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = options.ocrRedoText, role = Role.Checkbox) { options.ocrRedoText = it },
            ) {
                Checkbox(checked = options.ocrRedoText, onCheckedChange = null)
                Text(S.ocrRedoText, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
