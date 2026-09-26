@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.SizeF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fanfeast.anonpdf.pdf.AddMark
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.CropPages
import io.github.fanfeast.anonpdf.pdf.DeletePages
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.EditPlan
import io.github.fanfeast.anonpdf.pdf.FillMark
import io.github.fanfeast.anonpdf.pdf.InkMark
import io.github.fanfeast.anonpdf.pdf.InsertPages
import io.github.fanfeast.anonpdf.pdf.MarkRect
import io.github.fanfeast.anonpdf.pdf.MovePage
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.PageState
import io.github.fanfeast.anonpdf.pdf.PaperSize
import io.github.fanfeast.anonpdf.pdf.PdfEditor
import io.github.fanfeast.anonpdf.pdf.RemoveMark
import io.github.fanfeast.anonpdf.pdf.ResizePages
import io.github.fanfeast.anonpdf.pdf.ResizeTarget
import io.github.fanfeast.anonpdf.pdf.RestorePages
import io.github.fanfeast.anonpdf.pdf.ReversePages
import io.github.fanfeast.anonpdf.pdf.RotatePages
import io.github.fanfeast.anonpdf.pdf.ScalePages
import io.github.fanfeast.anonpdf.pdf.SetPageNumbers
import io.github.fanfeast.anonpdf.pdf.SetWatermark
import io.github.fanfeast.anonpdf.pdf.TextMark
import io.github.fanfeast.anonpdf.pdf.WatermarkOptions
import io.github.fanfeast.anonpdf.ui.common.DocumentSession
import io.github.fanfeast.anonpdf.ui.common.OpenOutcome
import io.github.fanfeast.anonpdf.ui.common.openDocumentSession
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.BusyOverlay
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.ResultCard
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val THUMBNAIL_WIDTH_PX = 160
private const val THUMBNAIL_SPACING_DP = 8

private const val PREVIEW_BUCKET_PX = 200
private const val PREVIEW_MAX_PX = 1400
private const val PREVIEW_DEBOUNCE_MS = 170L

private const val AUTO_SCROLL_EDGE_PX = 110f
private const val AUTO_SCROLL_STEP_PX = 14f

/** Drawer heights it snaps to: out of the way, tools showing, everything showing. */
private val DRAWER_HIDDEN = 36.dp
private val DRAWER_COMPACT = 356.dp
private val DRAWER_FULL = 460.dp

/** Below this the drawer shows nothing but its handle. */
private val DRAWER_CONTENT_THRESHOLD = 90.dp

/**
 * Which tool is currently open.
 *
 * Each variant carries its own draft, which feeds either the preview or an overlay,
 * so what the user is adjusting is what they see before anything is committed to
 * the edit history.
 */
private sealed interface EditorMode {
    data object Normal : EditorMode
    data class Cropping(val insets: CropInsets) : EditorMode
    data class Resizing(
        val mode: SizeMode,
        val factor: Float,
        val target: ResizeTarget,
    ) : EditorMode
    data class Watermarking(val options: WatermarkOptions) : EditorMode
    data class Numbering(val options: PageNumberOptions) : EditorMode
    data class AddingText(val mark: TextMark, val placed: Boolean) : EditorMode
    data class Filling(
        val highlight: Boolean,
        val rect: MarkRect?,
        val colour: Int,
    ) : EditorMode
    data class Inking(
        val strokes: List<List<Pair<Float, Float>>>,
        val colour: Int,
        val widthRatio: Float,
    ) : EditorMode
    data class Inserting(val where: InsertPosition) : EditorMode

    /** True when the user works directly on the page, so page paging gets out of the way. */
    val interactsWithPage: Boolean
        get() = this is Cropping || this is Filling || this is Inking || this is AddingText
}

@Composable
fun EditorScreen(
    initialUri: Uri?,
    onBack: () -> Unit,
    onOpenTool: (ToolId, Uri) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val store = remember { DocumentStore(context) }
    val snackbarHost = remember { SnackbarHostState() }

    // The document and its edits live in the holder, not in remember: opening a
    // one-shot tool takes the editor out of composition, and remembered state
    // would be gone — with every pending edit — when the user came back.
    val holder: EditorHolder = viewModel()
    var session by holder::session
    var editor by holder::editor
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Normal) }
    var showPending by remember { mutableStateOf(false) }
    var drawerHeight by remember { mutableStateOf(DRAWER_COMPACT) }
    // Hoisted so the group the user swiped to survives opening and closing a tool.
    val toolPager = rememberPagerState(pageCount = { TOOL_PAGE_COUNT })

    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<ToolResult?>(null) }

    val thumbnails = remember {
        object : LruCache<Int, Bitmap>(20 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    val extraSources = holder.extraSources
    var insertQueue by holder::insertQueue
    var insertReady by holder::insertReady
    var insertNeedsPassword by remember { mutableStateOf(false) }
    var insertPasswordError by remember { mutableStateOf<String?>(null) }

    fun load(uri: Uri, password: String?) {
        scope.launch {
            loading = true
            error = null
            pendingUri = uri
            when (val outcome = openDocumentSession(store, uri, password, session?.sourceFile)) {
                is OpenOutcome.Ready -> {
                    session?.close()
                    extraSources.forEach { it.close() }
                    extraSources.clear()
                    insertReady.forEach { it.close() }
                    insertReady = emptyList()
                    insertQueue = emptyList()
                    thumbnails.evictAll()
                    session = outcome.session
                    editor = EditorState(outcome.session.pageCount)
                    preview = null
                    mode = EditorMode.Normal
                    askPassword = false
                    passwordError = null
                }
                OpenOutcome.NeedsPassword -> {
                    askPassword = true
                    passwordError = null
                }
                is OpenOutcome.WrongPassword -> {
                    askPassword = true
                    passwordError = outcome.message
                }
                is OpenOutcome.Failed -> error = outcome.message
            }
            loading = false
        }
    }

    /**
     * Opens picked files one at a time. A protected file pauses the queue for a
     * password; dismissing the dialog skips that file and carries on.
     */
    fun processInsertQueue(password: String? = null) {
        scope.launch {
            var candidate = password
            while (insertQueue.isNotEmpty()) {
                val uri = insertQueue.first()
                when (val outcome = openDocumentSession(store, uri, candidate)) {
                    is OpenOutcome.Ready -> {
                        insertQueue = insertQueue.drop(1)
                        insertReady = insertReady + outcome.session
                        candidate = null
                        insertNeedsPassword = false
                        insertPasswordError = null
                    }
                    OpenOutcome.NeedsPassword -> {
                        insertNeedsPassword = true
                        insertPasswordError = null
                        return@launch
                    }
                    is OpenOutcome.WrongPassword -> {
                        insertNeedsPassword = true
                        insertPasswordError = outcome.message
                        return@launch
                    }
                    is OpenOutcome.Failed -> {
                        insertQueue = insertQueue.drop(1)
                        error = outcome.message
                        candidate = null
                    }
                }
            }
            if (insertReady.isNotEmpty()) {
                mode = EditorMode.Inserting(InsertPosition.AFTER)
                if (drawerHeight < DRAWER_COMPACT) drawerHeight = DRAWER_COMPACT
            }
        }
    }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) load(uri, null) }

    val pickInsert = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            insertQueue = insertQueue + uris
            processInsertQueue()
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && session == null) load(initialUri, null)
    }

    val document = session
    val state = editor

    if (document == null || state == null) {
        EmptyEditor(
            loading = loading,
            error = error,
            onPick = { pickPdf.launch(arrayOf("application/pdf")) },
            onBack = onBack,
        )
        if (askPassword) {
            PasswordDialog(
                error = passwordError,
                onDismiss = {
                    askPassword = false
                    if (session == null) onBack()
                },
                onConfirm = { candidate -> pendingUri?.let { load(it, candidate) } },
            )
        }
        return
    }

    val plan = state.plan
    val currentSource = state.currentSourceIndex

    /** The original document plus everything merged in, in global-index order. */
    val allSources = listOf(document) + extraSources

    /** Which document a global page index belongs to, and the page's index there. */
    fun resolveSource(global: Int): Pair<DocumentSession, Int>? {
        var first = 0
        for (source in allSources) {
            if (global < first + source.pageCount) return source to (global - first)
            first += source.pageCount
        }
        return null
    }

    fun planSources() = allSources.map {
        PdfEditor.PlanSource(it.sourceFile, it.password, it.pageCount)
    }

    /**
     * The plan the preview renders: what is committed, plus the open tool's draft.
     *
     * Added content is deliberately *not* folded in here — an overlay draws the
     * draft instead, so dragging a highlight or scribbling does not re-render the
     * page on every frame.
     */
    val previewPlan: EditPlan = remember(plan, mode, currentSource) {
        val targets = state.targets()
        val page = currentSource
        when (val current = mode) {
            is EditorMode.Cropping -> plan.copy(
                pages = plan.pages.map {
                    if (it.sourceIndex == page) it.copy(crop = CropInsets()) else it
                },
            )
            is EditorMode.Resizing -> plan.copy(
                pages = plan.pages.map {
                    when {
                        it.sourceIndex !in targets -> it
                        current.mode == SizeMode.PAPER ->
                            it.copy(resize = current.target, scale = 1f)
                        else -> it.copy(scale = current.factor, resize = null)
                    }
                },
            )
            is EditorMode.Watermarking -> plan.copy(watermark = current.options)
            is EditorMode.Numbering -> plan.copy(pageNumbers = current.options)
            else -> plan
        }
    }

    val kept = previewPlan.kept

    val effectiveSizes = remember(previewPlan, document, extraSources.size) {
        kept.map { pageState ->
            effectiveSize(
                resolveSource(pageState.sourceIndex)
                    ?.let { (owner, local) -> owner.pageSizes.getOrNull(local) }
                    ?: SizeF(595f, 842f),
                pageState,
            )
        }
    }

    var previewWidthPx by remember { mutableStateOf(600) }

    LaunchedEffect(previewPlan, state.position, previewWidthPx, document, extraSources.size) {
        if (kept.isEmpty()) {
            preview = null
            return@LaunchedEffect
        }
        previewBusy = true
        delay(PREVIEW_DEBOUNCE_MS)
        preview = PdfEditor.renderPreview(
            sources = planSources(),
            plan = previewPlan,
            outputPosition = state.position,
            targetWidthPx = previewWidthPx,
            workDir = document.sourceFile.parentFile ?: context.cacheDir,
        )
        previewBusy = false
    }

    /** Opening a tool must not leave its controls off-screen. */
    fun openMode(next: EditorMode) {
        mode = next
        if (drawerHeight < DRAWER_COMPACT) drawerHeight = DRAWER_COMPACT
    }

    /**
     * Hands the document to a one-shot tool with the pending edits already applied,
     * so "delete three pages, then compress" compresses what you are looking at.
     */
    fun handOff(tool: ToolId) {
        scope.launch {
            error = null
            if (!plan.hasChanges) {
                onOpenTool(tool, document.uri)
                return@launch
            }
            busyMessage = "Applying your edits first…"
            progress = 0f
            try {
                val staged = store.newOutputFile(
                    "${DocumentStore.stem(document.name)}-edited",
                    "pdf",
                )
                PdfEditor.applyPlan(
                    sources = planSources(),
                    output = staged,
                    plan = plan,
                ) { value -> progress = value }
                onOpenTool(tool, "file://${staged.absolutePath}".toUri())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.friendlyMessage("Could not prepare the document.")
            } finally {
                busyMessage = null
            }
        }
    }

    fun pick(tool: EditorTool) {
        val page = currentSource
        when (tool) {
            EditorTool.ROTATE_LEFT -> state.apply(RotatePages(-90, state.targets()))
            EditorTool.ROTATE_RIGHT -> state.apply(RotatePages(90, state.targets()))
            EditorTool.DELETE -> state.apply(DeletePages(state.targets()))
            EditorTool.RESTORE -> state.apply(RestorePages(state.targets()))
            EditorTool.REVERSE -> state.apply(ReversePages)

            EditorTool.CROP -> {
                state.showFirstTarget()
                val existing = state.currentSourceIndex?.let { source ->
                    plan.pages.firstOrNull { it.sourceIndex == source }
                }?.crop
                openMode(
                    EditorMode.Cropping(
                        existing?.takeIf { !it.isEmpty }
                            ?: CropInsets(0.06f, 0.06f, 0.06f, 0.06f),
                    ),
                )
            }

            EditorTool.RESIZE -> {
                state.showFirstTarget()
                val target = state.currentSourceIndex?.let { source ->
                    plan.pages.firstOrNull { it.sourceIndex == source }
                }
                openMode(
                    EditorMode.Resizing(
                        mode = if (target != null && target.scale != 1f) {
                            SizeMode.PERCENT
                        } else {
                            SizeMode.PAPER
                        },
                        factor = target?.scale ?: 1f,
                        target = target?.resize ?: ResizeTarget(PaperSize.A4),
                    ),
                )
            }

            EditorTool.WATERMARK -> openMode(
                EditorMode.Watermarking(plan.watermark ?: WatermarkOptions(text = "")),
            )
            EditorTool.PAGE_NUMBERS -> openMode(
                EditorMode.Numbering(plan.pageNumbers ?: PageNumberOptions()),
            )

            EditorTool.TEXT -> if (page != null) {
                openMode(
                    EditorMode.AddingText(
                        mark = TextMark(
                            id = state.nextMarkId(),
                            sourceIndex = page,
                            text = "",
                            left = 0.12f,
                            top = 0.15f,
                        ),
                        placed = false,
                    ),
                )
            }

            EditorTool.WHITEOUT -> if (page != null) {
                openMode(EditorMode.Filling(false, null, WhiteoutColours.first().second))
            }
            EditorTool.HIGHLIGHT -> if (page != null) {
                openMode(EditorMode.Filling(true, null, HighlightColours.first().second))
            }
            EditorTool.DRAW -> if (page != null) {
                openMode(
                    EditorMode.Inking(
                        strokes = emptyList(),
                        colour = InkColours.first().second,
                        widthRatio = 0.004f,
                    ),
                )
            }

            // Merge happens here rather than in the one-shot tool: the picked
            // documents' pages join the plan, where the whole arsenal — reorder,
            // rotate, crop, marks, delete — applies to them like any other page.
            EditorTool.MERGE -> pickInsert.launch(arrayOf("application/pdf"))
            EditorTool.SPLIT -> handOff(ToolId.SPLIT)
            EditorTool.EXTRACT -> handOff(ToolId.EXTRACT)
            EditorTool.COMPRESS -> handOff(ToolId.COMPRESS)
            EditorTool.TO_IMAGES -> handOff(ToolId.PDF_TO_IMAGES)
            EditorTool.FROM_IMAGES -> handOff(ToolId.IMAGES_TO_PDF)
            EditorTool.EXTRACT_TEXT -> handOff(ToolId.EXTRACT_TEXT)
            EditorTool.SIGN -> handOff(ToolId.SIGN)
            EditorTool.PROTECT -> handOff(ToolId.PROTECT)
            EditorTool.UNLOCK -> handOff(ToolId.UNLOCK)
        }
    }

    fun save() {
        scope.launch {
            busyMessage = "Writing the document…"
            progress = 0f
            error = null
            try {
                val stem = DocumentStore.stem(document.name)
                val output = store.newOutputFile("$stem-edited", "pdf")
                PdfEditor.applyPlan(
                    sources = planSources(),
                    output = output,
                    plan = plan,
                ) { value -> progress = value }
                result = ToolResult.One(
                    file = output,
                    suggestedName = "$stem-edited.pdf",
                    note = "${plan.kept.size} page" +
                        (if (plan.kept.size == 1) "" else "s") +
                        ", ${state.ops.size} edit" +
                        (if (state.ops.size == 1) "" else "s") + " applied.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.friendlyMessage("Could not save the document.")
            } finally {
                busyMessage = null
            }
        }
    }

    /**
     * Commits the opened documents into the plan. Pages go in as one run per
     * document, in the order they were picked, at the chosen spot.
     */
    fun applyInsert(where: InsertPosition) {
        val docs = insertReady
        if (docs.isEmpty()) {
            mode = EditorMode.Normal
            return
        }
        var cursor = when (where) {
            InsertPosition.START -> 0
            InsertPosition.END -> plan.pages.size
            InsertPosition.AFTER -> plan.pages
                .indexOfFirst { it.sourceIndex == currentSource }
                .let { if (it < 0) plan.pages.size else it + 1 }
        }
        docs.forEach { doc ->
            val start = state.registerInsert(doc.pageCount)
            extraSources.add(doc)
            state.apply(InsertPages(start, doc.pageCount, cursor, doc.name))
            cursor += doc.pageCount
        }
        insertReady = emptyList()
        mode = EditorMode.Normal
    }

    fun cancelInsert() {
        insertReady.forEach { it.close() }
        insertReady = emptyList()
        insertQueue = emptyList()
        mode = EditorMode.Normal
    }

    Scaffold(
        topBar = {
            AnonTopBar(title = document.name, onBack = onBack) {
                IconButton(onClick = { state.undo() }, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { state.redo() }, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = {
                        state.reset()
                        result = null
                        mode = EditorMode.Normal
                    },
                    enabled = state.canUndo,
                ) { Icon(Icons.Filled.Restore, contentDescription = "Revert all") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                PreviewArea(
                    bitmap = preview,
                    busy = previewBusy,
                    empty = kept.isEmpty(),
                    shownSize = effectiveSizes.getOrNull(state.position),
                    referenceSize = referenceSize(
                        state = kept.getOrNull(state.position),
                        base = kept.getOrNull(state.position)?.let { current ->
                            resolveSource(current.sourceIndex)
                                ?.let { (owner, local) -> owner.pageSizes.getOrNull(local) }
                        },
                        shown = effectiveSizes.getOrNull(state.position),
                    ),
                    mode = mode,
                    onModeChange = { mode = it },
                    onWidthChange = { previewWidthPx = it },
                    stepper = if (kept.size > 1 && !mode.interactsWithPage) {
                        { PageStepper(state.position, kept.size) { state.goTo(state.position + it) } }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )

                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(drawerHeight),
                ) {
                    Column {
                        DrawerHandle(
                            onDrag = { deltaPx ->
                                val delta = with(density) { deltaPx.toDp() }
                                drawerHeight = (drawerHeight - delta)
                                    .coerceIn(DRAWER_HIDDEN, DRAWER_FULL)
                            },
                            onSettle = { drawerHeight = snapDrawer(drawerHeight) },
                            onToggle = {
                                drawerHeight = if (drawerHeight <= DRAWER_CONTENT_THRESHOLD) {
                                    DRAWER_COMPACT
                                } else {
                                    DRAWER_HIDDEN
                                }
                            },
                        )

                        // The body takes whatever height is left, and scrolls inside
                        // it, so no control is ever pushed out of reach whatever the
                        // drawer is dragged to.
                        if (drawerHeight > DRAWER_CONTENT_THRESHOLD) {
                            Box(Modifier.weight(1f)) {
                                DrawerBody(
                                    state = state,
                                    plan = plan,
                                    mode = mode,
                                    toolPager = toolPager,
                                    resolveSource = { global -> resolveSource(global) },
                                    thumbnails = thumbnails,
                                    currentSource = currentSource,
                                    insertDocs = insertReady.map { it.name to it.pageCount },
                                    onModeChange = { mode = it },
                                    onExitMode = { mode = EditorMode.Normal },
                                    onPickTool = ::pick,
                                    onShowPending = { showPending = true },
                                    onSave = ::save,
                                    onInsertApply = {
                                        (mode as? EditorMode.Inserting)
                                            ?.let { applyInsert(it.where) }
                                    },
                                    onInsertCancel = ::cancelInsert,
                                    resultingSize = describeResult(
                                        effectiveSizes.getOrNull(state.position),
                                    ),
                                )
                            }
                        }
                    }
                }

                error?.let { Column(Modifier.padding(10.dp)) { ErrorNote(it) } }
            }

            result?.let { finished ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ResultCard(
                        result = finished,
                        store = store,
                        onMessage = { scope.launch { snackbarHost.showSnackbar(it) } },
                        onStartOver = { result = null },
                    )
                }
            }

            busyMessage?.let { BusyOverlay(progress = progress, message = it) }
        }
    }

    if (showPending) {
        PendingEditsDialog(
            descriptions = state.ops.map { it.describe() },
            onDismiss = { showPending = false },
            onUndoAll = {
                state.reset()
                showPending = false
            },
        )
    }

    if (insertNeedsPassword) {
        PasswordDialog(
            message = "A PDF you picked is protected. Enter its password to merge it, " +
                "or cancel to skip that file.",
            error = insertPasswordError,
            onDismiss = {
                insertNeedsPassword = false
                insertPasswordError = null
                insertQueue = insertQueue.drop(1)
                processInsertQueue()
            },
            onConfirm = { candidate -> processInsertQueue(candidate) },
        )
    }
}

/**
 * The page under edit, with its overlays and its page label.
 *
 * The points-to-dp scale is chosen so the *unedited* page just fits. An untouched
 * page therefore fills whatever room it is given — which is the point of being able
 * to fold the drawer away — while a page that has been cropped or scaled down draws
 * smaller than the space it has, so the change is visible rather than silently
 * re-fitted. The label sits in its own row below the page rather than floating over
 * it, for the same reason.
 */
@Composable
private fun PreviewArea(
    bitmap: Bitmap?,
    busy: Boolean,
    empty: Boolean,
    shownSize: SizeF?,
    referenceSize: SizeF,
    mode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    onWidthChange: (Int) -> Unit,
    stepper: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val dpPerPoint = minOf(
                (maxWidth * 0.96f).value / referenceSize.width,
                (maxHeight * 0.96f).value / referenceSize.height,
            )
            val drawn = shownSize ?: referenceSize
            val pageWidth = (drawn.width * dpPerPoint).dp
            val pageHeight = (drawn.height * dpPerPoint).dp
            onWidthChange(with(density) { bucket(pageWidth.toPx().roundToInt()) })

            when {
                empty -> Text(
                    "Every page is deleted.\nRestore one to carry on.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                bitmap != null -> Box(
                    Modifier
                        .width(pageWidth)
                        .height(pageHeight),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                    )
                    PageOverlays(
                        mode = mode,
                        pageHeightPoints = drawn.height,
                        onModeChange = onModeChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> CircularProgressIndicator()
            }

            if (busy && bitmap != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.TopEnd,
                ) { CircularProgressIndicator(Modifier.size(16.dp)) }
            }
        }

        if (stepper != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { stepper() }
        }
    }
}

/** The grab bar. Drag it to resize the drawer, tap it to get it out of the way. */
@Composable
private fun DrawerHandle(
    onDrag: (Float) -> Unit,
    onSettle: () -> Unit,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .draggable(
                state = rememberDraggableState { onDrag(it) },
                orientation = Orientation.Vertical,
                onDragStopped = { onSettle() },
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}

private fun snapDrawer(height: Dp): Dp =
    listOf(DRAWER_HIDDEN, DRAWER_COMPACT, DRAWER_FULL)
        .minByOrNull { abs(it.value - height.value) } ?: DRAWER_COMPACT

/** Whatever the open tool needs the user to do directly on the page. */
@Composable
private fun PageOverlays(
    mode: EditorMode,
    pageHeightPoints: Float,
    onModeChange: (EditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        is EditorMode.Cropping -> CropOverlay(
            insets = mode.insets,
            onChange = { onModeChange(EditorMode.Cropping(it)) },
            modifier = modifier,
        )

        is EditorMode.Filling -> RectMarkOverlay(
            rect = mode.rect,
            colour = Color(mode.colour),
            opacity = if (mode.highlight) 0.4f else 1f,
            onChange = { onModeChange(mode.copy(rect = it)) },
            modifier = modifier,
        )

        is EditorMode.Inking -> InkOverlay(
            strokes = mode.strokes,
            colour = Color(mode.colour),
            strokeWidthRatio = mode.widthRatio,
            onStroke = { onModeChange(mode.copy(strokes = mode.strokes + listOf(it))) },
            modifier = modifier,
        )

        is EditorMode.AddingText -> TextPlacementOverlay(
            mark = mode.mark,
            placed = mode.placed,
            pageHeightPoints = pageHeightPoints,
            onPlace = { point ->
                onModeChange(
                    mode.copy(
                        mark = mode.mark.copy(left = point.first, top = point.second),
                        placed = true,
                    ),
                )
            },
            modifier = modifier,
        )

        else -> Unit
    }
}

@Composable
private fun PageStepper(position: Int, total: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }, enabled = position > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous page",
            )
        }
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Text(
                "page ${position + 1} / $total",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        IconButton(onClick = { onStep(1) }, enabled = position < total - 1) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next page",
            )
        }
    }
}

@Composable
private fun DrawerBody(
    state: EditorState,
    plan: EditPlan,
    mode: EditorMode,
    toolPager: PagerState,
    resolveSource: (Int) -> Pair<DocumentSession, Int>?,
    thumbnails: LruCache<Int, Bitmap>,
    currentSource: Int?,
    insertDocs: List<Pair<String, Int>>,
    onModeChange: (EditorMode) -> Unit,
    onExitMode: () -> Unit,
    onPickTool: (EditorTool) -> Unit,
    onShowPending: () -> Unit,
    onSave: () -> Unit,
    onInsertApply: () -> Unit,
    onInsertCancel: () -> Unit,
    resultingSize: String,
) {
    val markCount = currentSource?.let { plan.marksFor(it).size } ?: 0

    fun clearPageMarks() {
        currentSource?.let { source ->
            plan.marksFor(source).forEach { state.apply(RemoveMark(it.id)) }
        }
    }

    when (val current = mode) {
        EditorMode.Normal -> Column(Modifier.fillMaxHeight()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                PageStrip(
                    plan = plan,
                    resolveSource = resolveSource,
                    cache = thumbnails,
                    selection = state.selection,
                    currentSourceIndex = currentSource,
                    selecting = state.scope == ApplyScope.SELECTION,
                    onTap = { sourceIndex ->
                        if (state.scope == ApplyScope.SELECTION) {
                            state.toggleSelection(sourceIndex)
                        } else {
                            val index = plan.kept.indexOfFirst { it.sourceIndex == sourceIndex }
                            if (index >= 0) state.goTo(index)
                        }
                    },
                    onMove = { sourceIndex, offset ->
                        state.apply(MovePage(sourceIndex, offset))
                    },
                )
                ScopeRow(state)
                ToolPager(
                    pagerState = toolPager,
                    pages = toolPages(
                        currentPageDeleted = currentSource?.let { source ->
                            plan.pages.firstOrNull { it.sourceIndex == source }?.deleted
                        } ?: false,
                    ),
                    highlighted = buildSet {
                        if (plan.watermark != null) add(EditorTool.WATERMARK)
                        if (plan.pageNumbers != null) add(EditorTool.PAGE_NUMBERS)
                    },
                    onPick = onPickTool,
                )
            }
            // Pinned, so "Save as…" is reachable however far the drawer is pulled down.
            SaveRow(state, plan, onShowPending, onSave)
        }

        is EditorMode.Inserting -> InsertPanel(
            docs = insertDocs,
            where = current.where,
            onWhereChange = { onModeChange(current.copy(where = it)) },
            onCancel = onInsertCancel,
            onApply = onInsertApply,
        )

        is EditorMode.Cropping -> CropPanel(
            insets = current.insets,
            targetLabel = state.targetLabel(),
            onChange = { onModeChange(EditorMode.Cropping(it)) },
            onCancel = onExitMode,
            onApply = {
                state.apply(CropPages(current.insets, state.targets()))
                onExitMode()
            },
        )

        is EditorMode.Resizing -> ResizePanel(
            mode = current.mode,
            factor = current.factor,
            target = current.target,
            targetLabel = state.targetLabel(),
            resultingSize = resultingSize,
            onModeChange = { onModeChange(current.copy(mode = it)) },
            onFactorChange = { onModeChange(current.copy(factor = it)) },
            onTargetChange = { onModeChange(current.copy(target = it)) },
            onCancel = onExitMode,
            onApply = {
                state.apply(
                    if (current.mode == SizeMode.PAPER) {
                        ResizePages(current.target, state.targets())
                    } else {
                        ScalePages(current.factor, state.targets())
                    },
                )
                onExitMode()
            },
        )

        is EditorMode.Watermarking -> WatermarkPanel(
            options = current.options,
            hasExisting = plan.watermark != null,
            onChange = { onModeChange(current.copy(options = it)) },
            onRemove = {
                state.apply(SetWatermark(null))
                onExitMode()
            },
            onCancel = onExitMode,
            onApply = {
                state.apply(SetWatermark(current.options))
                onExitMode()
            },
        )

        is EditorMode.Numbering -> PageNumbersPanel(
            pageCount = plan.kept.size,
            options = current.options,
            hasExisting = plan.pageNumbers != null,
            onChange = { onModeChange(current.copy(options = it)) },
            onRemove = {
                state.apply(SetPageNumbers(null))
                onExitMode()
            },
            onCancel = onExitMode,
            onApply = {
                state.apply(SetPageNumbers(current.options))
                onExitMode()
            },
        )

        is EditorMode.AddingText -> TextPanel(
            mark = current.mark,
            placed = current.placed,
            existingCount = markCount,
            onChange = { onModeChange(current.copy(mark = it)) },
            onClearPage = {
                clearPageMarks()
                onExitMode()
            },
            onCancel = onExitMode,
            onApply = {
                state.apply(AddMark(current.mark))
                onExitMode()
            },
        )

        is EditorMode.Filling -> FillPanel(
            highlight = current.highlight,
            rect = current.rect,
            colour = current.colour,
            pageNumber = (currentSource ?: 0) + 1,
            existingCount = markCount,
            onColourChange = { onModeChange(current.copy(colour = it)) },
            onReset = { onModeChange(current.copy(rect = null)) },
            onClearPage = {
                clearPageMarks()
                onExitMode()
            },
            onCancel = onExitMode,
            onApply = {
                val rect = current.rect?.tidied()
                if (rect != null && !rect.isDegenerate && currentSource != null) {
                    state.apply(
                        AddMark(
                            FillMark(
                                id = state.nextMarkId(),
                                sourceIndex = currentSource,
                                rect = rect,
                                color = current.colour,
                                opacity = if (current.highlight) 0.4f else 1f,
                            ),
                        ),
                    )
                }
                onExitMode()
            },
        )

        is EditorMode.Inking -> InkPanel(
            strokeCount = current.strokes.size,
            colour = current.colour,
            widthRatio = current.widthRatio,
            pageNumber = (currentSource ?: 0) + 1,
            existingCount = markCount,
            onColourChange = { onModeChange(current.copy(colour = it)) },
            onWidthChange = { onModeChange(current.copy(widthRatio = it)) },
            onUndoStroke = {
                onModeChange(current.copy(strokes = current.strokes.dropLast(1)))
            },
            onClearStrokes = { onModeChange(current.copy(strokes = emptyList())) },
            onClearPage = {
                clearPageMarks()
                onExitMode()
            },
            onCancel = onExitMode,
            onApply = {
                if (current.strokes.isNotEmpty() && currentSource != null) {
                    state.apply(
                        AddMark(
                            InkMark(
                                id = state.nextMarkId(),
                                sourceIndex = currentSource,
                                strokes = current.strokes,
                                color = current.colour,
                                widthRatio = current.widthRatio,
                            ),
                        ),
                    )
                }
                onExitMode()
            },
        )
    }
}

@Composable
private fun ScopeRow(state: EditorState) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ApplyScope.entries.forEach { option ->
            FilterChip(
                selected = state.scope == option,
                onClick = {
                    state.scope = option
                    if (option != ApplyScope.SELECTION) state.clearSelection()
                },
                label = {
                    Text(
                        if (option == ApplyScope.SELECTION && state.selection.isNotEmpty()) {
                            "${option.label} (${state.selection.size})"
                        } else {
                            option.label
                        },
                    )
                },
            )
        }
        if (state.scope == ApplyScope.SELECTION) {
            if (state.selection.isEmpty()) {
                TextButton(onClick = { state.selectAll() }) { Text("All") }
            } else {
                TextButton(onClick = { state.clearSelection() }) { Text("None") }
            }
        }
    }
}

@Composable
private fun SaveRow(
    state: EditorState,
    plan: EditPlan,
    onShowPending: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onShowPending,
            label = {
                Text(
                    if (state.ops.isEmpty()) {
                        "No edits yet"
                    } else {
                        "${state.ops.size} edit${if (state.ops.size == 1) "" else "s"}"
                    },
                )
            },
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onSave,
            enabled = plan.hasChanges && plan.kept.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { Text("Save as…") }
    }
}

/**
 * The box the preview is fitted into: the page as it arrived, but never smaller
 * than what the edits have made of it, so an enlarged page is not clipped.
 */
private fun referenceSize(
    state: PageState?,
    base: SizeF?,
    shown: SizeF?,
): SizeF {
    val turned = turnedBy(base ?: SizeF(595f, 842f), state?.rotationDelta ?: 0)
    return if (shown == null) {
        turned
    } else {
        SizeF(max(turned.width, shown.width), max(turned.height, shown.height))
    }
}

/** Swaps the sides on a quarter turn. */
private fun turnedBy(base: SizeF, rotationDelta: Int): SizeF =
    if (((rotationDelta / 90) % 2 + 2) % 2 == 1) {
        SizeF(base.height, base.width)
    } else {
        base
    }

/**
 * The page's size as the reader will see it: turned, cropped, then sized.
 *
 * The base size already accounts for the page's stored /Rotate, because that is
 * what the renderer reports.
 */
private fun effectiveSize(base: SizeF, state: PageState): SizeF {
    val turned = turnedBy(base, state.rotationDelta)
    val croppedWidth = turned.width * (1f - state.crop.left - state.crop.right)
    val croppedHeight = turned.height * (1f - state.crop.top - state.crop.bottom)

    val resize = state.resize
    return if (resize != null) {
        val (sheetWidth, sheetHeight) = resize.sizeFor(croppedWidth, croppedHeight)
        SizeF(sheetWidth, sheetHeight)
    } else {
        SizeF(max(1f, croppedWidth * state.scale), max(1f, croppedHeight * state.scale))
    }
}

private fun describeResult(size: SizeF?): String = if (size == null) {
    ""
} else {
    "Result: ${size.width.roundToInt()} × ${size.height.roundToInt()} pt"
}

@Composable
private fun EmptyEditor(
    loading: Boolean,
    error: String?,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { AnonTopBar(title = "Edit PDF", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                "Rotate, crop, resize, delete and reorder pages, add text, white-out, " +
                    "highlights or drawings, and reach every other tool from here — all " +
                    "with a preview of the real result. Nothing is written until you save.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose a PDF")
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                ErrorNote(it)
            }
            if (loading) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * The thumbnail strip, with long-press-and-drag reordering.
 *
 * The drop target is worked out from the pointer's position over the strip rather
 * than from how far the finger has travelled. That makes it immune to the list
 * scrolling underneath mid-drag, which is what lets edge auto-scroll work at all.
 */
@Composable
private fun PageStrip(
    plan: EditPlan,
    resolveSource: (Int) -> Pair<DocumentSession, Int>?,
    cache: LruCache<Int, Bitmap>,
    selection: Set<Int>,
    currentSourceIndex: Int?,
    selecting: Boolean,
    onTap: (Int) -> Unit,
    onMove: (sourceIndex: Int, offset: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val spacingPx = with(density) { THUMBNAIL_SPACING_DP.dp.toPx() }

    // Drag state is plain values, updated from the gesture callbacks. The layout
    // info they come from is deliberately never read during composition: that
    // recomposes the whole strip on every scroll frame.
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var dragTranslation by remember { mutableStateOf(0f) }
    var scrollDirection by remember { mutableStateOf(0) }
    var itemWidthPx by remember { mutableStateOf(0f) }

    fun refreshDrag(index: Int, localX: Float) {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val own = visible.firstOrNull { it.index == index }
        val width = own?.size?.toFloat() ?: itemWidthPx
        if (width > 0f) itemWidthPx = width

        val pointerX = (own?.offset?.toFloat() ?: 0f) + localX
        dragTranslation = pointerX - ((own?.offset?.toFloat() ?: 0f) + width / 2f)

        dragTarget = when {
            visible.isEmpty() -> index
            else -> visible.firstOrNull {
                pointerX >= it.offset && pointerX <= it.offset + it.size
            }?.index
                ?: if (pointerX < visible.first().offset) {
                    visible.first().index
                } else {
                    visible.last().index
                }
        }

        scrollDirection = when {
            pointerX < AUTO_SCROLL_EDGE_PX -> -1
            pointerX > info.viewportEndOffset - AUTO_SCROLL_EDGE_PX -> 1
            else -> 0
        }
    }

    fun endDrag(commit: Boolean) {
        val from = dragFrom
        val to = dragTarget
        if (commit && from != null && to != null && to != from) {
            onMove(plan.pages[from].sourceIndex, to - from)
        }
        dragFrom = null
        dragTarget = null
        dragTranslation = 0f
        scrollDirection = 0
    }

    LaunchedEffect(scrollDirection) {
        if (scrollDirection == 0) return@LaunchedEffect
        while (true) {
            listState.scrollBy(scrollDirection * AUTO_SCROLL_STEP_PX)
            awaitFrame()
        }
    }

    LaunchedEffect(currentSourceIndex, dragFrom) {
        if (dragFrom != null) return@LaunchedEffect
        val index = plan.pages.indexOfFirst { it.sourceIndex == currentSourceIndex }
        if (index >= 0) runCatching { listState.animateScrollToItem(index) }
    }

    Column {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(plan.pages.size) { position ->
                val page = plan.pages[position]
                val from = dragFrom
                val to = dragTarget
                val isDragged = from == position

                // Open a gap: everything between the origin and the target slides one
                // place over, so the drop position is obvious.
                val slideBy = if (from != null && to != null && !isDragged) {
                    val step = itemWidthPx + spacingPx
                    when {
                        from < to && position in (from + 1)..to -> -step
                        from > to && position in to until from -> step
                        else -> 0f
                    }
                } else {
                    0f
                }

                Thumbnail(
                    page = page,
                    position = if (page.deleted) {
                        null
                    } else {
                        plan.kept.indexOfFirst { it.sourceIndex == page.sourceIndex } + 1
                    },
                    resolveSource = resolveSource,
                    cache = cache,
                    selected = page.sourceIndex in selection,
                    current = page.sourceIndex == currentSourceIndex,
                    selecting = selecting,
                    dragging = isDragged,
                    markCount = plan.marksFor(page.sourceIndex).size,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        // The gesture sits *outside* the layer that moves the tile.
                        // Inside it, the tile's own translation would be subtracted
                        // from every reported position and the drop target would
                        // trail the finger at half speed.
                        .pointerInput(position, plan.pages.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start ->
                                    dragFrom = position
                                    refreshDrag(position, start.x)
                                },
                                onDrag = { change, _ ->
                                    refreshDrag(position, change.position.x)
                                },
                                onDragEnd = { endDrag(commit = true) },
                                onDragCancel = { endDrag(commit = false) },
                            )
                        }
                        .graphicsLayer {
                            translationX = if (isDragged) dragTranslation else slideBy
                            if (isDragged) {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                shadowElevation = 12f
                            }
                        }
                        .clickable { onTap(page.sourceIndex) },
                )
            }
        }
        Text(
            if (selecting) {
                "Tap to select. Long-press and drag to reorder."
            } else {
                "Long-press and drag a page to reorder it."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Thumbnail(
    page: PageState,
    position: Int?,
    resolveSource: (Int) -> Pair<DocumentSession, Int>?,
    cache: LruCache<Int, Bitmap>,
    selected: Boolean,
    current: Boolean,
    selecting: Boolean,
    dragging: Boolean,
    markCount: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(page.sourceIndex) { mutableStateOf(cache.get(page.sourceIndex)) }

    LaunchedEffect(page.sourceIndex) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = resolveSource(page.sourceIndex)?.let { (owner, local) ->
            runCatching {
                owner.rasterizer.renderByWidth(local, THUMBNAIL_WIDTH_PX)
            }.getOrNull()
        }
        if (rendered != null) {
            cache.put(page.sourceIndex, rendered)
            bitmap = rendered
        }
    }

    val borderColour = when {
        dragging -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.tertiary
        current && !selecting -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (dragging || selected || (current && !selecting)) 2.dp else 1.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .aspectRatio(0.72f)
                .border(borderWidth, borderColour)
                .background(Color.White)
                .alpha(if (page.deleted) 0.32f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = position?.let { "Page $it" } ?: "Deleted page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(page.rotationDelta.toFloat()),
                )
            }
            if (page.deleted) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Deleted",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                )
            }
            // A dot in the corner when the page carries added content.
            if (markCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(7.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary,
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
        }
        Text(
            text = position?.toString() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (current || selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (current || selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun bucket(px: Int): Int {
    val bucketed = ((px + PREVIEW_BUCKET_PX - 1) / PREVIEW_BUCKET_PX) * PREVIEW_BUCKET_PX
    return bucketed.coerceIn(PREVIEW_BUCKET_PX, PREVIEW_MAX_PX)
}
