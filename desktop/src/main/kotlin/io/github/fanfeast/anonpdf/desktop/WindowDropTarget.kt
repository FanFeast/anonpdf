package io.github.fanfeast.anonpdf.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.URI

/**
 * Lets files be dragged onto the window from the file manager. While they hover,
 * a card over the window says what dropping them will do.
 */
@Composable
internal fun WindowDropTarget(
    hint: String,
    onDrop: (List<File>) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    val latestOnDrop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                return latestOnDrop(droppedFiles(event))
            }
        }
    }
    Box(
        modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.carriesFiles() },
            target = target,
        ),
    ) {
        content()
        if (hovering) DropHint(hint)
    }
}

@Composable
private fun DropHint(hint: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.copy(alpha = 0.85f))
            .padding(24.dp)
            .border(2.dp, colors.primary, RoundedCornerShape(16.dp)),
    ) {
        Surface(
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(hint, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.carriesFiles(): Boolean = dragData() is DragData.FilesList

/** The dropped files, from the file: URIs the platform hands over. */
@OptIn(ExperimentalComposeUiApi::class)
private fun droppedFiles(event: DragAndDropEvent): List<File> {
    val data = event.dragData() as? DragData.FilesList ?: return emptyList()
    return data.readFiles().mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
}
