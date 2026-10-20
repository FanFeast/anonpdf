package io.github.fanfeast.anonpdf.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fanfeast.anonpdf.ui.editor.EditorScreen
import io.github.fanfeast.anonpdf.ui.sign.SignScreen
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolScreen
import java.io.File

/** What fills the window beside the sidebar. */
sealed interface Pane {
    data object Start : Pane

    data object Editor : Pane

    /**
     * A one-shot tool, optionally handed a [document] already. [fromEditor] is set
     * when the editor handed it over, so leaving the tool goes back to the edits.
     */
    data class Tool(val id: ToolId, val document: File? = null, val fromEditor: Boolean = false) : Pane
}

/**
 * Where the window is, and the editor's document, which outlives visits to other
 * panes.
 *
 * The editor keeps its document and pending edits in a ViewModel. Each document
 * gets its own ViewModel store, kept here rather than in the editor's composition,
 * so a trip to a tool and back finds the edits where they were. Closing the
 * document clears the store, and that is what closes the document's files.
 */
@Stable
class DesktopNavigator(initial: File? = null) {

    var pane by mutableStateOf<Pane>(if (initial != null) Pane.Editor else Pane.Start)
        private set

    /** The document the current editor session was opened with, if any. */
    var editorDocument by mutableStateOf(initial)
        private set

    /** Bumped for every new editor session, so it starts from a clean composition. */
    var editorGeneration by mutableStateOf(0)
        private set

    var editorOwner: ViewModelStoreOwner = newOwner()
        private set

    /**
     * Whether the editor has a session that "Close document" would end. Set on any
     * visit, because the editor can open a document by itself.
     */
    var hasEditorSession by mutableStateOf(initial != null)
        private set

    fun show(next: Pane) {
        if (next == Pane.Editor) hasEditorSession = true
        pane = next
    }

    /** Starts a new editor session on [file], replacing any open one. */
    fun edit(file: File?) {
        closeEditorSession()
        editorDocument = file
        show(Pane.Editor)
    }

    fun closeEditor() {
        closeEditorSession()
        pane = Pane.Start
    }

    /** Back from a tool: to the edits it came from, or to the start page. */
    fun leaveTool(tool: Pane.Tool) {
        pane = if (tool.fromEditor) Pane.Editor else Pane.Start
    }

    private fun closeEditorSession() {
        editorOwner.viewModelStore.clear()
        editorOwner = newOwner()
        editorDocument = null
        editorGeneration++
        hasEditorSession = false
    }

    private fun newOwner(): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}

@Composable
internal fun DesktopApp(navigator: DesktopNavigator) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                current = navigator.pane,
                onShow = navigator::show,
                modifier = Modifier
                    .width(SIDEBAR_WIDTH)
                    .fillMaxHeight(),
            )
            VerticalDivider()
            when (val pane = navigator.pane) {
                Pane.Start -> StartPage(
                    onEdit = { file -> navigator.edit(file) },
                    onOpenTool = { navigator.show(Pane.Tool(it)) },
                )

                Pane.Editor -> key(navigator.editorGeneration) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides navigator.editorOwner) {
                        EditorScreen(
                            initialDocument = navigator.editorDocument,
                            onBack = navigator::closeEditor,
                            onOpenTool = { id, document ->
                                navigator.show(Pane.Tool(id, document, fromEditor = true))
                            },
                        )
                    }
                }

                // Keyed on the whole pane: a tool opened afresh starts with empty fields.
                is Pane.Tool -> key(pane) {
                    if (pane.id == ToolId.SIGN) {
                        SignScreen(initialDocument = pane.document, onBack = { navigator.leaveTool(pane) })
                    } else {
                        ToolScreen(
                            toolId = pane.id,
                            initialDocument = pane.document,
                            onBack = { navigator.leaveTool(pane) },
                        )
                    }
                }
            }
        }
    }
}

private val SIDEBAR_WIDTH = 248.dp
