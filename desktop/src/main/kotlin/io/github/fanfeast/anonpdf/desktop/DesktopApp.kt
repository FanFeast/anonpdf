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
import io.github.fanfeast.anonpdf.pdf.ImageFiles
import io.github.fanfeast.anonpdf.ui.common.DropInbox
import io.github.fanfeast.anonpdf.ui.common.Dropped
import io.github.fanfeast.anonpdf.ui.common.LocalDropInbox
import io.github.fanfeast.anonpdf.ui.editor.EditorScreen
import io.github.fanfeast.anonpdf.ui.sign.SignScreen
import io.github.fanfeast.anonpdf.ui.tools.ToolCatalog
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolInput
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

    /** Where a drop on the window goes when the screen showing should take it. */
    val drops = DropInbox()

    /**
     * Takes files dropped onto the window. The start page sends them where they
     * fit: one PDF to the editor, several to Merge, pictures to Images to PDF.
     * Any other pane's screen takes them itself. False when none of the files is
     * something AnonPDF opens.
     */
    fun drop(files: List<File>): Boolean {
        val dropped = sortDropped(files)
        if (dropped.isEmpty) return false
        if (pane == Pane.Start) {
            when {
                dropped.pdfs.size == 1 -> {
                    edit(dropped.pdfs.single())
                    return true
                }
                dropped.pdfs.isNotEmpty() -> show(Pane.Tool(ToolId.MERGE))
                else -> show(Pane.Tool(ToolId.IMAGES_TO_PDF))
            }
        }
        drops.offer(dropped)
        return true
    }

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
        WindowDropTarget(
            hint = dropHint(navigator.pane),
            onDrop = navigator::drop,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    current = navigator.pane,
                    onShow = navigator::show,
                    modifier = Modifier
                        .width(SIDEBAR_WIDTH)
                        .fillMaxHeight(),
                )
                VerticalDivider()
                CompositionLocalProvider(LocalDropInbox provides navigator.drops) {
                    PaneContent(navigator)
                }
            }
        }
    }
}

@Composable
private fun PaneContent(navigator: DesktopNavigator) {
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

/** What dropping onto [pane] will do, shown while files are dragged over the window. */
private fun dropHint(pane: Pane): String = when (pane) {
    Pane.Start -> "Drop a PDF to edit it, several to merge them, or pictures to make a PDF"
    Pane.Editor -> "Drop a PDF to open it, or to add its pages to the one open"
    is Pane.Tool -> {
        val spec = ToolCatalog.spec(pane.id)
        when (spec.input) {
            ToolInput.SINGLE_PDF -> "Drop a PDF for ${spec.title}"
            ToolInput.MULTIPLE_PDF -> "Drop PDFs to add them to ${spec.title}"
            ToolInput.IMAGES -> "Drop pictures to add them"
        }
    }
}

/** Sorts dropped files by what the screens take; anything else, folders included, is left out. */
internal fun sortDropped(files: List<File>): Dropped {
    val usable = files.filter { it.isFile }
    return Dropped(
        pdfs = usable.filter { it.extension.equals("pdf", ignoreCase = true) },
        images = usable.filter { it.extension.lowercase() in ImageFiles.extensions },
    )
}

private val SIDEBAR_WIDTH = 248.dp
