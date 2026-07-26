package io.github.fanfeast.anonpdf.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The "it worked, now what" panel: save the output where the user wants it, or
 * hand it to another app.
 *
 * Saving goes through the Storage Access Framework, so the destination is chosen
 * by the user in the system picker and the app never needs a storage permission.
 * Sharing is an explicit, user-initiated handoff — AnonPDF itself has no network
 * permission and cannot send anything anywhere on its own.
 */
@Composable
fun ResultCard(
    result: ToolResult,
    store: DocumentStore,
    onMessage: (String) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        val single = result as? ToolResult.One ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { store.export(single.file, destination) }
                .onSuccess { onMessage("Saved.") }
                .onFailure { onMessage(it.friendlyMessage("Could not save the file.")) }
        }
    }

    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        val single = result as? ToolResult.One ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { store.export(single.file, destination) }
                .onSuccess { onMessage("Saved.") }
                .onFailure { onMessage(it.friendlyMessage("Could not save the file.")) }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        val many = result as? ToolResult.Many ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                    ?: error("That folder is not writable.")
                withContext(Dispatchers.IO) {
                    many.files.forEach { file ->
                        val mime = mimeFor(file)
                        val created = tree.createFile(mime, file.name)
                            ?: error("Could not create ${file.name}.")
                        store.export(file, created.uri)
                    }
                }
            }
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
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }

            result.note?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            when (result) {
                is ToolResult.One -> {
                    Button(
                        onClick = {
                            if (result.isText) {
                                createText.launch(result.suggestedName)
                            } else {
                                createPdf.launch(result.suggestedName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save as…")
                    }
                }

                is ToolResult.Many -> {
                    Text(
                        "${result.files.size} files",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pickFolder.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save all to folder…")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { shareResult(context, result, onMessage) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = onStartOver,
                    modifier = Modifier.weight(1f),
                ) { Text("Start over") }
            }
        }
    }
}

private fun shareResult(
    context: android.content.Context,
    result: ToolResult,
    onMessage: (String) -> Unit,
) {
    runCatching {
        val authority = "${context.packageName}.files"
        val intent = when (result) {
            is ToolResult.One -> {
                val uri = FileProvider.getUriForFile(context, authority, result.file)
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeFor(result.file)
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            }

            is ToolResult.Many -> {
                val uris = ArrayList(
                    result.files.map { FileProvider.getUriForFile(context, authority, it) },
                )
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeFor(result.files.first())
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }.onFailure {
        onMessage(it.friendlyMessage("Nothing on this device can receive that file."))
    }
}

private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    else -> "application/octet-stream"
}

/** Surfaces the useful part of an exception without leaking a stack trace at the user. */
fun Throwable.friendlyMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() && it.length < 240 } ?: fallback
