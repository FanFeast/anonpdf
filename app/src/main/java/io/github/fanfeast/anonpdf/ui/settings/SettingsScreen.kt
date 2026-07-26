@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.data.AppPreferences
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.SectionLabel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val store = remember { DocumentStore(context) }
    val snackbarHost = remember { SnackbarHostState() }

    val rememberRecents by preferences.rememberRecents.collectAsState(initial = true)
    val invertColors by preferences.invertPdfColors.collectAsState(initial = false)
    val keepScreenOn by preferences.keepScreenOn.collectAsState(initial = false)

    Scaffold(
        topBar = { AnonTopBar(title = "Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel("Reading")
            SwitchRow(
                title = "Invert page colours",
                subtitle = "Dark background for night reading. Only changes how pages " +
                    "are drawn, never the file.",
                checked = invertColors,
                onChange = { scope.launch { preferences.setInvertPdfColors(it) } },
            )
            SwitchRow(
                title = "Keep the screen on while reading",
                subtitle = "Stops the display sleeping in the viewer.",
                checked = keepScreenOn,
                onChange = { scope.launch { preferences.setKeepScreenOn(it) } },
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionLabel("Privacy")
            SwitchRow(
                title = "Remember recent files",
                subtitle = "Keeps up to 20 file names on this device so you can reopen " +
                    "them quickly. Switch this off and the list is deleted immediately.",
                checked = rememberRecents,
                onChange = { scope.launch { preferences.setRememberRecents(it) } },
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        preferences.clearRecents()
                        snackbarHost.showSnackbar("Recent files cleared.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear recent files") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        store.clearWorkspace()
                        snackbarHost.showSnackbar("Temporary files deleted.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete temporary working files") }

            Spacer(Modifier.height(20.dp))
            InfoNote(
                "There is nothing else to configure, because there is nothing else " +
                    "to switch off. AnonPDF has no account, no sync, no ads and no " +
                    "network permission.",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
