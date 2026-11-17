@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import io.github.fanfeast.anonpdf.i18n.S
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
        topBar = { AnonTopBar(title = S.settings, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel(S.reading)
            SwitchRow(
                title = S.invertPageColours,
                subtitle = S.invertPageColoursHint,
                checked = invertColors,
                onChange = { scope.launch { preferences.setInvertPdfColors(it) } },
            )
            SwitchRow(
                title = S.keepScreenOn,
                subtitle = S.keepScreenOnHint,
                checked = keepScreenOn,
                onChange = { scope.launch { preferences.setKeepScreenOn(it) } },
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionLabel(S.privacy)
            SwitchRow(
                title = S.rememberRecent,
                subtitle = S.rememberRecentHint,
                checked = rememberRecents,
                onChange = { scope.launch { preferences.setRememberRecents(it) } },
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        preferences.clearRecents()
                        snackbarHost.showSnackbar(S.recentCleared)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(S.clearRecent) }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        store.clearWorkspace()
                        snackbarHost.showSnackbar(S.tempDeleted)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(S.deleteTemp) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionLabel(S.language)
            Text(
                S.languageHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Android 13 added a per-app language; before it the app follows the device.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(S.chooseLanguage) }
            }

            Spacer(Modifier.height(20.dp))
            InfoNote(S.nothingElseToConfigure)
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
