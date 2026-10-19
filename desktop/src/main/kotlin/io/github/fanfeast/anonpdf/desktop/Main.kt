package io.github.fanfeast.anonpdf.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fanfeast.anonpdf.ui.editor.EditorScreen
import io.github.fanfeast.anonpdf.ui.theme.AnonPdfTheme
import java.io.File

fun main(args: Array<String>) {
    // First, before anything can create a temp file: see PrivateScratch.
    PrivateScratch.install()
    val initial = args.firstOrNull()?.let(::File)?.takeIf { it.isFile }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AnonPDF",
            state = rememberWindowState(width = 1000.dp, height = 1100.dp),
        ) {
            AnonPdfTheme { DesktopApp(initial) }
        }
    }
}

/**
 * The editor, which on desktop is the whole app for now.
 *
 * Each document gets its own ViewModel store, so leaving a document (the editor's
 * back arrow) clears it, and that is what closes the document's files. The
 * editor then starts empty, ready to open another.
 */
@Composable
internal fun DesktopApp(initial: File?) {
    var opening by remember { mutableStateOf(initial) }
    var generation by remember { mutableStateOf(0) }
    key(generation) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            EditorScreen(
                initialDocument = opening,
                onBack = {
                    opening = null
                    generation++
                },
                // The one-shot tools (compress, split, sign, …) are not on desktop yet.
                onOpenTool = null,
            )
        }
    }
}
