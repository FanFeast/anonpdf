package io.github.fanfeast.anonpdf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/**
 * What a finished operation offers: save it somewhere the user chooses.
 *
 * The desktop counterpart of the Android card. There is no Share button: a
 * desktop has no share sheet, and AnonPDF never sends anything anywhere itself.
 */
@Composable
fun ResultCard(
    result: ToolResult,
    store: DocumentStore,
    onMessage: (String) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    fun saveOne(single: ToolResult.One) {
        val destination = chooseSaveFile(single.suggestedName, if (single.isText) "txt" else "pdf")
            ?: return
        scope.launch {
            runCatching { store.export(single.file, destination) }
                .onSuccess { onMessage("Saved to ${destination.name}.") }
                .onFailure { onMessage(it.friendlyMessage("Could not save the file.")) }
        }
    }

    fun saveMany(many: ToolResult.Many) {
        val folder = chooseFolder() ?: return
        scope.launch {
            runCatching { many.files.forEach { store.export(it, File(folder, it.name)) } }
                .onSuccess { onMessage("Saved ${many.files.size} files.") }
                .onFailure { onMessage(it.friendlyMessage("Could not save to that folder.")) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }

            result.note?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            when (result) {
                is ToolResult.One -> Button(onClick = { saveOne(result) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save as…")
                }

                is ToolResult.Many -> {
                    Text("${result.files.size} files", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { saveMany(result) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save all to folder…")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onStartOver, modifier = Modifier.weight(1f)) { Text("Start over") }
            }
        }
    }
}

/** The platform's own save dialog; adds [extension] if the user typed a bare name. */
private fun chooseSaveFile(suggestedName: String, extension: String): File? {
    val dialog = FileDialog(null as Frame?, "Save as", FileDialog.SAVE).apply {
        file = suggestedName
        isVisible = true
    }
    val name = dialog.file ?: return null
    val withExtension = if (name.contains('.')) name else "$name.$extension"
    return File(dialog.directory, withExtension)
}

private fun chooseFolder(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save all to folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
