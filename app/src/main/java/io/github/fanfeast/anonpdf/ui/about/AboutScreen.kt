@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.fanfeast.anonpdf.BuildConfig
import io.github.fanfeast.anonpdf.i18n.L10n
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.SectionLabel

private const val SOURCE_URL = "https://github.com/FanFeast/anonpdf"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(topBar = { AnonTopBar(title = S.about, onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("AnonPDF", style = MaterialTheme.typography.headlineMedium)
            Text(
                S.version(BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                S.aboutTagline,
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(S.cannotDo)
            Bullet(
                S.cannotNetwork,
            )
            Bullet(
                S.cannotTrack,
            )
            Bullet(
                S.cannotCharge,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(S.onePermission)
            Bullet(
                S.permissionWhy,
            )
            Bullet(
                S.permissionOptional,
            )
            Bullet(
                S.permissionWhere,
            )
            Bullet(
                S.permissionCannotLeak,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(S.whatItStores)
            Bullet(
                S.storesRecent,
            )
            Bullet(
                S.storesWorking,
            )
            Bullet(S.storesPrefs)

            Spacer(Modifier.height(24.dp))
            InfoNote(
                S.noTrustNeeded,
            )

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(S.viewSource) }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionLabel(S.licences)
            Licence(
                "AnonPDF",
                S.licenceMit,
            )
            Licence(
                "PdfBox-Android",
                S.licencePdfBox,
            )
            Licence(
                "AndroidX and Jetpack Compose",
                S.licenceAndroidX,
            )
            Licence(
                "Kotlin and kotlinx.coroutines",
                S.licenceKotlin,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                S.cryptoNote,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only worth saying in a language a machine helped write.
            if (S.translationsNote != L10n.English.translationsNote) {
                Spacer(Modifier.height(8.dp))
                Text(
                    S.translationsNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun Licence(name: String, detail: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
