package io.github.fanfeast.anonpdf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/** Files dropped onto the window, already sorted by the platform into what the screens take. */
data class Dropped(val pdfs: List<DocumentRef>, val images: List<DocumentRef>) {
    val isEmpty: Boolean get() = pdfs.isEmpty() && images.isEmpty()
}

/**
 * Hands files dropped onto the window to whichever screen is showing.
 *
 * The window's drop target doesn't know what each screen does with a file, so
 * it leaves the drop here and the screen takes it. A drop waits until a screen
 * takes it, so one made while switching to a tool reaches that tool.
 */
@Stable
class DropInbox {
    private var pending by mutableStateOf<Dropped?>(null)

    fun offer(dropped: Dropped) {
        if (!dropped.isEmpty) pending = dropped
    }

    internal fun take(): Dropped? = pending.also { pending = null }

    internal val hasPending: Boolean get() = pending != null
}

/** Null where nothing can be dropped onto the app, as on Android. */
val LocalDropInbox = staticCompositionLocalOf<DropInbox?> { null }

/** Calls [onDrop] with each drop made while this screen is showing. */
@Composable
fun OnFilesDropped(onDrop: (Dropped) -> Unit) {
    val inbox = LocalDropInbox.current ?: return
    val latest by rememberUpdatedState(onDrop)
    LaunchedEffect(inbox) {
        snapshotFlow { inbox.hasPending }.collect { pending ->
            if (pending) inbox.take()?.let { latest(it) }
        }
    }
}
