@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.fanfeast.anonpdf.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ZoomOutMap
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.CropPages
import io.github.fanfeast.anonpdf.pdf.DeletePages
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.EditPlan
import io.github.fanfeast.anonpdf.pdf.MovePage
import io.github.fanfeast.anonpdf.pdf.PageState
import io.github.fanfeast.anonpdf.pdf.PdfEditor
import io.github.fanfeast.anonpdf.pdf.RestorePages
import io.github.fanfeast.anonpdf.pdf.ReversePages
import io.github.fanfeast.anonpdf.pdf.RotatePages
import io.github.fanfeast.anonpdf.pdf.ScalePages
import io.github.fanfeast.anonpdf.pdf.SetPageNumbers
import io.github.fanfeast.anonpdf.pdf.SetWatermark
import io.github.fanfeast.anonpdf.ui.common.DocumentSession
import io.github.fanfeast.anonpdf.ui.common.OpenOutcome
import io.github.fanfeast.anonpdf.ui.common.openDocumentSession
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.BusyOverlay
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.ResultCard
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val THUMBNAIL_WIDTH_PX = 160

/** Preview render widths snap to this grid, so a resize does not thrash. */
private const val PREVIEW_BUCKET_PX = 200
private const val PREVIEW_MAX_PX = 1400

/** Settling time before re-rendering, so dragging a slider does not queue renders. */
private const val PREVIEW_DEBOUNCE_MS = 160L

@Composable
fun EditorScreen(
    initialUri: Uri?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val store = remember { DocumentStore(context) }
    val snackbarHost = remember { SnackbarHostState() }

    var session by remember { mutableStateOf<DocumentSession?>(null) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var cropDraft by remember { mutableStateOf<CropInsets?>(null) }

    var showScale by remember { mutableStateOf(false) }
    var showWatermark by remember { mutableStateOf(false) }
    var showNumbers by remember { mutableStateOf(false) }
    var showPending by remember { mutableStateOf(false) }

    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<ToolResult?>(null) }

    val thumbnails = remember {
        object : LruCache<Int, Bitmap>(20 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    DisposableEffect(Unit) { onDispose { session?.close() } }

    fun load(uri: Uri, password: String?) {
        scope.launch {
            loading = true
            error = null
            pendingUri = uri
            when (val outcome = openDocumentSession(store, uri, password, session?.sourceFile)) {
                is OpenOutcome.Ready -> {
                    session?.close()
                    thumbnails.evictAll()
                    session = outcome.session
                    editor = EditorState(outcome.session.pageCount)
                    preview = null
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

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) load(uri, null) }

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
    val kept = plan.kept
    val currentSource = state.currentSourceIndex

    // While cropping, the preview must show the page *uncropped* so the user can
    // see what they are about to cut away.
    val previewPlan: EditPlan = remember(plan, cropDraft, currentSource) {
        val target = currentSource
        if (cropDraft == null || target == null) {
            plan
        } else {
            plan.copy(
                pages = plan.pages.map {
                    if (it.sourceIndex == target) it.copy(crop = CropInsets()) else it
                },
            )
        }
    }

    var previewWidthPx by remember { mutableStateOf(600) }

    LaunchedEffect(previewPlan, state.position, previewWidthPx, document) {
        if (kept.isEmpty()) {
            preview = null
            return@LaunchedEffect
        }
        previewBusy = true
        delay(PREVIEW_DEBOUNCE_MS)
        preview = PdfEditor.renderPreview(
            input = document.sourceFile,
            plan = previewPlan,
            outputPosition = state.position,
            targetWidthPx = previewWidthPx,
            workDir = document.sourceFile.parentFile ?: context.cacheDir,
            password = document.password,
        )
        previewBusy = false
    }

    Scaffold(
        topBar = {
            AnonTopBar(title = document.name, onBack = onBack) {
                IconButton(onClick = { state.undo() }, enabled = state.canUndo) {
                    Icon(Icons.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { state.redo() }, enabled = state.canRedo) {
                    Icon(Icons.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = {
                        state.reset()
                        result = null
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
                // ---------------------------------------------------- preview
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    previewWidthPx = with(density) {
                        bucket((maxWidth.toPx() * 0.94f).roundToInt())
                    }

                    when {
                        kept.isEmpty() -> Text(
                            "Every page is deleted.\nRestore one to carry on.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )

                        preview != null -> Box(contentAlignment = Alignment.Center) {
                            val bitmap = preview!!
                            Box(
                                Modifier
                                    .padding(12.dp)
                                    .width(with(density) { bitmap.width.toDp() })
                                    .height(with(density) { bitmap.height.toDp() }),
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White),
                                )
                                cropDraft?.let { insets ->
                                    CropOverlay(
                                        insets = insets,
                                        onChange = { cropDraft = it },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        else -> CircularProgressIndicator()
                    }

                    if (previewBusy && preview != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                        }
                    }

                    if (kept.size > 1 && cropDraft == null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { state.goTo(state.position - 1) },
                                enabled = state.position > 0,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous page",
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    "page ${state.position + 1} / ${kept.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            IconButton(
                                onClick = { state.goTo(state.position + 1) },
                                enabled = state.position < kept.lastIndex,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next page",
                                )
                            }
                        }
                    }
                }

                // ------------------------------------------------ page strip
                PageStrip(
                    plan = plan,
                    session = document,
                    cache = thumbnails,
                    selection = state.selection,
                    currentSourceIndex = currentSource,
                    onTap = { sourceIndex ->
                        val index = kept.indexOfFirst { it.sourceIndex == sourceIndex }
                        if (index >= 0) state.goTo(index)
                    },
                    onLongPress = { state.toggleSelection(it) },
                )

                // -------------------------------------------------- controls
                Surface(tonalElevation = 3.dp) {
                    if (cropDraft != null) {
                        CropControls(
                            insets = cropDraft!!,
                            targetLabel = state.targetLabel(),
                            onChange = { cropDraft = it },
                            onCancel = { cropDraft = null },
                            onApply = {
                                state.apply(CropPages(cropDraft!!, state.targets()))
                                cropDraft = null
                            },
                        )
                    } else {
                        EditorControls(
                            state = state,
                            plan = plan,
                            onRotate = { state.apply(RotatePages(it, state.targets())) },
                            onCrop = {
                                val existing = currentSource
                                    ?.let { source -> plan.pages.first { it.sourceIndex == source } }
                                    ?.crop
                                cropDraft = existing?.takeIf { !it.isEmpty }
                                    ?: CropInsets(0.06f, 0.06f, 0.06f, 0.06f)
                            },
                            onScale = { showScale = true },
                            onDelete = { state.apply(DeletePages(state.targets())) },
                            onRestore = { state.apply(RestorePages(state.targets())) },
                            onMove = { offset ->
                                currentSource?.let { state.apply(MovePage(it, offset)) }
                            },
                            onReverse = { state.apply(ReversePages) },
                            onWatermark = { showWatermark = true },
                            onNumbers = { showNumbers = true },
                            onShowPending = { showPending = true },
                            onSave = {
                                scope.launch {
                                    busyMessage = "Writing the document…"
                                    progress = 0f
                                    error = null
                                    try {
                                        val stem = DocumentStore.stem(document.name)
                                        val output = store.newOutputFile("$stem-edited", "pdf")
                                        PdfEditor.applyPlan(
                                            input = document.sourceFile,
                                            output = output,
                                            plan = plan,
                                            password = document.password,
                                        ) { value -> progress = value }
                                        result = ToolResult.One(
                                            file = output,
                                            suggestedName = "$stem-edited.pdf",
                                            note = "${kept.size} page" +
                                                (if (kept.size == 1) "" else "s") +
                                                ", ${state.ops.size} edit" +
                                                (if (state.ops.size == 1) "" else "s") +
                                                " applied.",
                                        )
                                    } catch (t: Throwable) {
                                        error = t.friendlyMessage("Could not save the document.")
                                    } finally {
                                        busyMessage = null
                                    }
                                }
                            },
                        )
                    }
                }

                error?.let {
                    Column(Modifier.padding(12.dp)) { ErrorNote(it) }
                }
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

    if (showScale) {
        val existing = currentSource
            ?.let { source -> plan.pages.first { it.sourceIndex == source } }
            ?.scale ?: 1f
        ScaleDialog(
            targetLabel = state.targetLabel(),
            initial = existing,
            onDismiss = { showScale = false },
            onApply = { factor ->
                state.apply(ScalePages(factor, state.targets()))
                showScale = false
            },
        )
    }

    if (showWatermark) {
        WatermarkDialog(
            initial = plan.watermark,
            onDismiss = { showWatermark = false },
            onApply = { options ->
                state.apply(SetWatermark(options))
                showWatermark = false
            },
        )
    }

    if (showNumbers) {
        PageNumbersDialog(
            pageCount = kept.size,
            initial = plan.pageNumbers,
            onDismiss = { showNumbers = false },
            onApply = { options ->
                state.apply(SetPageNumbers(options))
                showNumbers = false
            },
        )
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
                "Rotate, crop, scale, delete and reorder pages, add a watermark or " +
                    "page numbers — all at once, with a preview of the real result. " +
                    "Nothing is written until you save.",
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

@Composable
private fun PageStrip(
    plan: EditPlan,
    session: DocumentSession,
    cache: LruCache<Int, Bitmap>,
    selection: Set<Int>,
    currentSourceIndex: Int?,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentSourceIndex) {
        val index = plan.pages.indexOfFirst { it.sourceIndex == currentSourceIndex }
        if (index >= 0) runCatching { listState.animateScrollToItem(index) }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(plan.pages.size) { index ->
            val page = plan.pages[index]
            Thumbnail(
                page = page,
                position = if (page.deleted) null else {
                    plan.kept.indexOfFirst { it.sourceIndex == page.sourceIndex } + 1
                },
                session = session,
                cache = cache,
                selected = page.sourceIndex in selection,
                current = page.sourceIndex == currentSourceIndex,
                onTap = { onTap(page.sourceIndex) },
                onLongPress = { onLongPress(page.sourceIndex) },
            )
        }
    }
}

@Composable
private fun Thumbnail(
    page: PageState,
    position: Int?,
    session: DocumentSession,
    cache: LruCache<Int, Bitmap>,
    selected: Boolean,
    current: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var bitmap by remember(page.sourceIndex) { mutableStateOf(cache.get(page.sourceIndex)) }

    LaunchedEffect(page.sourceIndex) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = runCatching {
            session.rasterizer.renderByWidth(page.sourceIndex, THUMBNAIL_WIDTH_PX)
        }.getOrNull()
        if (rendered != null) {
            cache.put(page.sourceIndex, rendered)
            bitmap = rendered
        }
    }

    val borderColour = when {
        current -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Box(
            modifier = Modifier
                .height(62.dp)
                .aspectRatio(0.72f)
                .border(if (current || selected) 2.dp else 1.dp, borderColour)
                .background(Color.White)
                .alpha(if (page.deleted) 0.32f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${page.sourceIndex + 1}",
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
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = position?.toString() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EditorControls(
    state: EditorState,
    plan: EditPlan,
    onRotate: (Int) -> Unit,
    onCrop: () -> Unit,
    onScale: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onMove: (Int) -> Unit,
    onReverse: () -> Unit,
    onWatermark: () -> Unit,
    onNumbers: () -> Unit,
    onShowPending: () -> Unit,
    onSave: () -> Unit,
) {
    val currentDeleted = state.currentSourceIndex
        ?.let { source -> plan.pages.firstOrNull { it.sourceIndex == source }?.deleted }
        ?: false

    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Apply to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ApplyScope.entries.forEach { option ->
                FilterChip(
                    selected = state.scope == option,
                    onClick = { state.scope = option },
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
            if (state.selection.isEmpty()) {
                TextButton(onClick = { state.selectAll() }) { Text("Select all") }
            } else {
                TextButton(onClick = { state.clearSelection() }) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "Long-press a thumbnail to add it to the selection. " +
                "Next edit hits ${state.targetLabel()}.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        // Two rows of five rather than one scrolling row of ten: a horizontal
        // scroller hid the last four tools entirely, and shrinking the buttons
        // enough to fit would put them under the 48dp touch-target minimum.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(Icons.Filled.RotateLeft, "Left") { onRotate(-90) }
            ToolButton(Icons.Filled.RotateRight, "Right") { onRotate(90) }
            ToolButton(Icons.Filled.Crop, "Crop", onClick = onCrop)
            ToolButton(Icons.Filled.ZoomOutMap, "Scale", onClick = onScale)
            if (currentDeleted) {
                ToolButton(Icons.Filled.RestoreFromTrash, "Restore", onClick = onRestore)
            } else {
                ToolButton(Icons.Filled.Delete, "Delete", onClick = onDelete)
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") { onMove(-1) }
            ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") { onMove(1) }
            ToolButton(Icons.Filled.SwapVert, "Reverse", onClick = onReverse)
            ToolButton(
                Icons.Filled.WaterDrop,
                "Watermark",
                highlighted = plan.watermark != null,
                onClick = onWatermark,
            )
            ToolButton(
                Icons.Filled.Numbers,
                "Numbers",
                highlighted = plan.pageNumbers != null,
                onClick = onNumbers,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun CropControls(
    insets: CropInsets,
    targetLabel: String,
    onChange: (CropInsets) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            "Drag the frame. Crop will apply to $targetLabel.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "L ${pct(insets.left)}  T ${pct(insets.top)}  " +
                "R ${pct(insets.right)}  B ${pct(insets.bottom)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(CropInsets(0.06f, 0.06f, 0.06f, 0.06f)) }) {
                Text("Reset")
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onApply) { Text("Apply crop") }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .combinedClickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun pct(value: Float) = "${(value * 100).roundToInt()}%"

private fun bucket(px: Int): Int {
    val bucketed = ((px + PREVIEW_BUCKET_PX - 1) / PREVIEW_BUCKET_PX) * PREVIEW_BUCKET_PX
    return bucketed.coerceIn(PREVIEW_BUCKET_PX, PREVIEW_MAX_PX)
}
