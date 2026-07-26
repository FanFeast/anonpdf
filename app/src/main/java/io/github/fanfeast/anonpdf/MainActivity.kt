package io.github.fanfeast.anonpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.ui.AnonPdfApp
import io.github.fanfeast.anonpdf.ui.theme.AnonPdfTheme

class MainActivity : ComponentActivity() {

    private var pendingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // Start each launch with an empty scratch area rather than accumulating
            // working copies of documents across sessions.
            DocumentStore(this).clearWorkspace()
        }

        pendingUri = incomingPdf(intent)

        setContent {
            AnonPdfTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AnonPdfApp(initialUri = pendingUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPdf(intent)?.let { pendingUri = it }
    }

    /** Pulls a PDF out of a VIEW or SEND intent from another app. */
    private fun incomingPdf(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        else -> null
    }
}
