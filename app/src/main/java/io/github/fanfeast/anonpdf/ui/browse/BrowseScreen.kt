@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.browse

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.storage.DeviceFile
import io.github.fanfeast.anonpdf.storage.DeviceFiles
import io.github.fanfeast.anonpdf.storage.StorageAccess
import io.github.fanfeast.anonpdf.storage.StorageAccessLevel
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.formatBytes
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private enum class BrowseMode(val label: String) {
    ALL("All PDFs"),
    FOLDERS("Folders"),
}

/**
 * Lists the PDFs on the device, so opening a file does not mean walking the
 * system picker's folder tree every time.
 *
 * Needs all-files access. Without it the screen explains what is missing and
 * offers the picker instead, which works with no permission at all.
 */
@Composable
fun BrowseScreen(
    onOpen: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var accessLevel by remember { mutableStateOf(StorageAccess.level(context)) }
    var mode by remember { mutableStateOf(BrowseMode.ALL) }
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(DeviceFiles.externalRoot()) }
    var subfolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var folderPdfs by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }

    // Returning from the Settings toggle is the moment to re-read the permission.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { accessLevel = StorageAccess.level(context) }

    val pickViaSaf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onOpen(uri) }

    fun rescan() {
        scope.launch {
            scanning = true
            found = runCatching { DeviceFiles.findPdfs() }.getOrDefault(emptyList())
            scanning = false
        }
    }

    fun openFolder(target: File) {
        scope.launch {
            folder = target
            val listing = DeviceFiles.listFolder(target)
            subfolders = listing.subfolders
            folderPdfs = listing.pdfs
        }
    }

    LaunchedEffect(accessLevel) {
        if (accessLevel == StorageAccessLevel.ALL_FILES) {
            rescan()
            openFolder(DeviceFiles.externalRoot())
        }
    }

    val visible = remember(found, query) {
        if (query.isBlank()) found
        else found.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            AnonTopBar(title = "Browse", onBack = onBack) {
                if (accessLevel == StorageAccessLevel.ALL_FILES) {
                    IconButton(onClick = { rescan() }, enabled = !scanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (accessLevel != StorageAccessLevel.ALL_FILES) {
                Column(Modifier.padding(16.dp)) {
                    InfoNote(
                        "To list the PDFs on your device, AnonPDF needs Android's " +
                            "\"All files access\". It is a switch in Settings rather than " +
                            "a normal permission prompt.\n\nThis only lets the app read " +
                            "files. It still has no network permission, so nothing can " +
                            "leave this device either way.",
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Devices vary in which of these Settings screens exist,
                            // so try them in order of usefulness.
                            val opened = StorageAccess.allFilesSettingsIntents(context)
                                .any { intent ->
                                    runCatching { settingsLauncher.launch(intent) }.isSuccess
                                }
                            if (!opened) {
                                scope.launch {
                                    snackbarHost.showSnackbar(
                                        "Could not open Settings on this device.",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open Settings and allow file access") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { pickViaSaf.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Just pick one file instead") }
                }
                return@Column
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    label = "",
                    options = BrowseMode.entries,
                    selected = mode,
                    onSelect = { mode = it },
                    optionLabel = { it.label },
                )
                Spacer(Modifier.height(12.dp))
            }

            when (mode) {
                BrowseMode.ALL -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search by name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                scanning -> "Looking…"
                                visible.isEmpty() && query.isNotBlank() -> "No matches"
                                else -> "${visible.size} PDF${if (visible.size == 1) "" else "s"}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (scanning && found.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(visible.size) { index ->
                                val document = visible[index]
                                FileRow(
                                    name = document.name,
                                    detail = "${formatBytes(document.sizeBytes)} · " +
                                        "${document.parentName} · " +
                                        relativeTime(document.lastModified),
                                    onClick = { onOpen(Uri.fromFile(document.file)) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }

                BrowseMode.FOLDERS -> {
                    val root = DeviceFiles.externalRoot()
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            folder.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (folder.absolutePath != root.absolutePath) {
                            item {
                                FolderRow(
                                    name = "..",
                                    onClick = {
                                        folder.parentFile?.let { openFolder(it) }
                                    },
                                )
                            }
                        }
                        items(subfolders.size) { index ->
                            FolderRow(
                                name = subfolders[index].name,
                                onClick = { openFolder(subfolders[index]) },
                            )
                        }
                        items(folderPdfs.size) { index ->
                            val document = folderPdfs[index]
                            FileRow(
                                name = document.name,
                                detail = "${formatBytes(document.sizeBytes)} · " +
                                    relativeTime(document.lastModified),
                                onClick = { onOpen(Uri.fromFile(document.file)) },
                            )
                        }
                        if (subfolders.isEmpty() && folderPdfs.isEmpty()) {
                            item {
                                Text(
                                    "Nothing here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(name: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            if (name == "..") Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** "3 days ago", good enough to tell one download from another. */
private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "unknown date"
    val elapsed = System.currentTimeMillis() - epochMillis
    if (elapsed < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    if (hours < 24) return "$hours hour${if (hours == 1L) "" else "s"} ago"
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    if (days < 30) return "$days day${if (days == 1L) "" else "s"} ago"
    val months = days / 30
    if (months < 12) return "$months month${if (months == 1L) "" else "s"} ago"
    return "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
}
