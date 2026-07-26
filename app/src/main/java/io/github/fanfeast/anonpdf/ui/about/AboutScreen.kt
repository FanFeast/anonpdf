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
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.SectionLabel

private const val SOURCE_URL = "https://github.com/FanFeast/anonpdf"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(topBar = { AnonTopBar(title = "About", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("AnonPDF", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                "A PDF reader and editor that does its work on your device and " +
                    "nowhere else.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("What this app cannot do")
            Bullet(
                "Reach the network. AnonPDF declares no INTERNET permission, so " +
                    "Android will refuse any connection it tried to make. There is no " +
                    "server to upload to and no way to reach one.",
            )
            Bullet(
                "Track you. No analytics, no crash reporting, no advertising SDK, " +
                    "no device identifiers, no account.",
            )
            Bullet(
                "Charge you. Every tool is included. There is no paid tier and no " +
                    "feature held back.",
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("The one permission it asks for")
            Bullet(
                "All files access, so the browser can list the PDFs on your device " +
                    "instead of making you find each one in the system picker. It is " +
                    "used for reading documents and nothing else.",
            )
            Bullet(
                "It is optional. Decline it and everything still works through the " +
                    "picker, one file at a time.",
            )
            Bullet(
                "Android calls this a \"special app access\", so it does not appear " +
                    "on this app's Permissions page — that page says \"no permissions " +
                    "requested\" either way. Look under Settings, Apps, Special app " +
                    "access, All files access to see the real state.",
            )
            Bullet(
                "Reading files still cannot leak them: without network permission " +
                    "there is nowhere for them to go.",
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("What it stores")
            Bullet(
                "A list of recently opened file names, kept in app-private storage " +
                    "and excluded from cloud backup. You can switch it off or clear it " +
                    "in Settings.",
            )
            Bullet(
                "Working copies of the file you are editing, in the app cache. " +
                    "These are temporary and Android may delete them at any time.",
            )
            Bullet("Your display preferences. That is the entire list.")

            Spacer(Modifier.height(24.dp))
            InfoNote(
                "You do not have to take any of this on trust. The source is public " +
                    "and the permission list is visible in the app's Play Store entry " +
                    "and in Android's own app info screen.",
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
            ) { Text("View the source code") }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionLabel("Open source licences")
            Licence(
                "AnonPDF",
                "MIT Licence. Copyright (c) 2026 the AnonPDF contributors.",
            )
            Licence(
                "PdfBox-Android",
                "Apache License 2.0. Copyright the Apache Software Foundation and " +
                    "Tom Roush. Used for reading and writing PDF structure.",
            )
            Licence(
                "AndroidX and Jetpack Compose",
                "Apache License 2.0. Copyright The Android Open Source Project.",
            )
            Licence(
                "Kotlin and kotlinx.coroutines",
                "Apache License 2.0. Copyright JetBrains s.r.o.",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Password protection uses AES-256 through Android's own cryptography " +
                    "libraries. No third-party crypto library is bundled.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
