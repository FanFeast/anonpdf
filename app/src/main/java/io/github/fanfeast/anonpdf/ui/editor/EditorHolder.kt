package io.github.fanfeast.anonpdf.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.fanfeast.anonpdf.ui.common.DocumentSession

/**
 * The editor's document and pending edits, kept for as long as the editor sits in
 * the back stack.
 *
 * Handing the document to a one-shot tool navigates away, which takes the editor
 * out of composition. Anything held in `remember` would be lost with it, and the
 * user would come back to their document with every edit gone. A ViewModel scoped
 * to the editor's back stack entry survives that trip, and is cleared only when
 * the editor is really closed — which is when the documents are released.
 */
class EditorHolder : ViewModel() {

    var session by mutableStateOf<DocumentSession?>(null)
    var editor by mutableStateOf<EditorState?>(null)

    /**
     * Documents merged into this session, in the order they arrived. Their pages
     * hold the global indices after the original document's, which is the order
     * the export assembles them in — so the list must never be reordered.
     */
    val extraSources = mutableStateListOf<DocumentSession>()

    /**
     * The merge pipeline: picked files waiting to be opened, then opened documents
     * waiting for the user to say where they go.
     */
    var insertQueue by mutableStateOf<List<Uri>>(emptyList())
    var insertReady by mutableStateOf<List<DocumentSession>>(emptyList())

    override fun onCleared() {
        session?.close()
        extraSources.forEach { it.close() }
        insertReady.forEach { it.close() }
    }
}
