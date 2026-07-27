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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.CropPages
import io.github.fanfeast.anonpdf.pdf.DeletePages
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.EditPlan
import io.github.fanfeast.anonpdf.pdf.MovePage
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.PageState
import io.github.fanfeast.anonpdf.pdf.PaperSize
import io.github.fanfeast.anonpdf.pdf.PdfEditor
import io.github.fanfeast.anonpdf.pdf.ResizePages
import io.github.fanfeast.anonpdf.pdf.ResizeTarget
import io.github.fanfeast.anonpdf.pdf.RestorePages
import io.github.fanfeast.anonpdf.pdf.ReversePages
import io.github.fanfeast.anonpdf.pdf.RotatePages
import io.github.fanfeast.anonpdf.pdf.ScalePages
import io.github.fanfeast.anonpdf.pdf.SetPageNumbers
import io.github.fanfeast.anonpdf.pdf.SetWatermark
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
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val THUMBNAIL_WIDTH_PX = 160
private const val THUMBNAIL_SPACING_DP = 8

/** Preview render widths snap to this grid, so a resize does not thrash. */
private const val PREVIEW_BUCKET_PX = 200
private const val PREVIEW_MAX_PX = 1400

/** Settling time before re-rendering, so dragging a slider does not queue renders. */
private const val PREVIEW_DEBOUNCE_MS = 170L

/** How close to the strip's edge a drag has to get before it starts scrolling. */
private const val AUTO_SCROLL_EDGE_PX = 110f
private const val AUTO_SCROLL_STEP_PX = 14f

/**
 * Which tool is currently open.
 *
 * Each variant carries its own draft, which feeds the preview, so a slider drag
 * shows the real result before anything is committed to the edit history.
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
}

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
    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Normal) }
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
    val currentSource = state.currentSourceIndex

    /**
     * The plan the preview should show: the committed one, plus whatever the open
     * tool is currently drafting. Cropping is the exception — it clears the crop so
     * the user can see the whole page underneath the frame they are dragging.
     */
    val previewPlan: EditPlan = remember(plan, mode, currentSource) {
        val targets = state.targets()
        when (val current = mode) {
            EditorMode.Normal -> plan
            is EditorMode.Cropping -> plan.copy(
                pages = plan.pages.map {
                    if (it.sourceIndex == currentSource) it.copy(crop = CropInsets()) else it
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
        }
    }

    val kept = previewPlan.kept

    /**
     * Page sizes as they will appear, so the preview can show them at their true
     * relative size. Without this every page renders to the same width, which
     * makes scaling invisible and a landscape page look the same as a portrait one.
     */
    val effectiveSizes = remember(previewPlan, document) {
        kept.map { pageState ->
            effectiveSize(
                document.pageSizes.getOrNull(pageState.sourceIndex) ?: SizeF(595f, 842f),
                pageState,
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
                // ---------------------------------------------------- preview
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    // One points-to-dp scale for the whole document, chosen so the
                    // largest page just fits. That keeps relative page sizes honest
                    // and stops a tall page overflowing the space and being clipped.
                    val widestPage = effectiveSizes.maxOfOrNull { it.width } ?: 1f
                    val tallestPage = effectiveSizes.maxOfOrNull { it.height } ?: 1f
                    val dpPerPoint = minOf(
                        (maxWidth * 0.96f).value / widestPage,
                        (maxHeight * 0.96f).value / tallestPage,
                    )
                    val shownSize = effectiveSizes.getOrNull(state.position)
                    previewWidthPx = with(density) {
                        bucket((widestPage * dpPerPoint).dp.toPx().roundToInt())
                    }
                    val bitmap = preview

                    when {
                        kept.isEmpty() -> Text(
                            "Every page is deleted.\nRestore one to carry on.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )

                        bitmap != null -> {
                            // Sized from the page's own dimensions rather than the
                            // bitmap's, so a refit or a scale shows up immediately.
                            Box(
                                Modifier
                                    .width(((shownSize?.width ?: 1f) * dpPerPoint).dp)
                                    .height(((shownSize?.height ?: 1f) * dpPerPoint).dp),
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White),
                                )
                                (mode as? EditorMode.Cropping)?.let { cropping ->
                                    CropOverlay(
                                        insets = cropping.insets,
                                        onChange = { mode = EditorMode.Cropping(it) },
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
                                .padding(14.dp),
                            contentAlignment = Alignment.TopEnd,
                        ) { CircularProgressIndicator(Modifier.size(16.dp)) }
                    }

                    if (kept.size > 1 && mode is EditorMode.Normal) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp),
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
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 5.dp,
                                    ),
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

                // -------------------------------------------------- controls
                Surface(tonalElevation = 3.dp) {
                    when (val current = mode) {
                        EditorMode.Normal -> EditorControls(
                            state = state,
                            plan = plan,
                            onRotate = { state.apply(RotatePages(it, state.targets())) },
                            onCrop = {
                                state.showFirstTarget()
                                val existing = state.currentSourceIndex
                                    ?.let { source ->
                                        plan.pages.firstOrNull { it.sourceIndex == source }
                                    }
                                    ?.crop
                                mode = EditorMode.Cropping(
                                    existing?.takeIf { !it.isEmpty }
                                        ?: CropInsets(0.06f, 0.06f, 0.06f, 0.06f),
                                )
                            },
                            onResize = {
                                state.showFirstTarget()
                                val page = state.currentSourceIndex?.let { source ->
                                    plan.pages.firstOrNull { it.sourceIndex == source }
                                }
                                mode = EditorMode.Resizing(
                                    // Reopen on whichever way the page was last
                                    // sized; paper sizes otherwise, since "put this
                                    // on Letter" is the common request.
                                    mode = if (page != null && page.scale != 1f) {
                                        SizeMode.PERCENT
                                    } else {
                                        SizeMode.PAPER
                                    },
                                    factor = page?.scale ?: 1f,
                                    target = page?.resize ?: ResizeTarget(PaperSize.A4),
                                )
                            },
                            onDelete = { state.apply(DeletePages(state.targets())) },
                            onRestore = { state.apply(RestorePages(state.targets())) },
                            onReverse = { state.apply(ReversePages) },
                            onWatermark = {
                                mode = EditorMode.Watermarking(
                                    plan.watermark ?: WatermarkOptions(text = ""),
                                )
                            },
                            onNumbers = {
                                mode = EditorMode.Numbering(
                                    plan.pageNumbers ?: PageNumberOptions(),
                                )
                            },
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
                                            note = "${plan.kept.size} page" +
                                                (if (plan.kept.size == 1) "" else "s") +
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

                        is EditorMode.Cropping -> CropPanel(
                            insets = current.insets,
                            targetLabel = state.targetLabel(),
                            onChange = { mode = EditorMode.Cropping(it) },
                            onCancel = { mode = EditorMode.Normal },
                            onApply = {
                                state.apply(CropPages(current.insets, state.targets()))
                                mode = EditorMode.Normal
                            },
                        )

                        is EditorMode.Resizing -> ResizePanel(
                            mode = current.mode,
                            factor = current.factor,
                            target = current.target,
                            targetLabel = state.targetLabel(),
                            resultingSize = describeResult(
                                effectiveSizes.getOrNull(state.position),
                            ),
                            onModeChange = { mode = current.copy(mode = it) },
                            onFactorChange = { mode = current.copy(factor = it) },
                            onTargetChange = { mode = current.copy(target = it) },
                            onCancel = { mode = EditorMode.Normal },
                            onApply = {
                                state.apply(
                                    if (current.mode == SizeMode.PAPER) {
                                        ResizePages(current.target, state.targets())
                                    } else {
                                        ScalePages(current.factor, state.targets())
                                    },
                                )
                                mode = EditorMode.Normal
                            },
                        )

                        is EditorMode.Watermarking -> WatermarkPanel(
                            options = current.options,
                            hasExisting = plan.watermark != null,
                            onChange = { mode = EditorMode.Watermarking(it) },
                            onRemove = {
                                state.apply(SetWatermark(null))
                                mode = EditorMode.Normal
                            },
                            onCancel = { mode = EditorMode.Normal },
                            onApply = {
                                state.apply(SetWatermark(current.options))
                                mode = EditorMode.Normal
                            },
                        )

                        is EditorMode.Numbering -> PageNumbersPanel(
                            pageCount = plan.kept.size,
                            options = current.options,
                            hasExisting = plan.pageNumbers != null,
                            onChange = { mode = EditorMode.Numbering(it) },
                            onRemove = {
                                state.apply(SetPageNumbers(null))
                                mode = EditorMode.Normal
                            },
                            onCancel = { mode = EditorMode.Normal },
                            onApply = {
                                state.apply(SetPageNumbers(current.options))
                                mode = EditorMode.Normal
                            },
                        )
                    }
                }

                error?.let { Column(Modifier.padding(12.dp)) { ErrorNote(it) } }
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
}

/**
 * The page's size as the reader will see it: scaled, cropped, and with a quarter
 * turn swapping the axes.
 *
 * The base size already accounts for the page's stored /Rotate, because that is
 * what the renderer reports.
 */
private fun effectiveSize(base: SizeF, state: PageState): SizeF {
    // Same order the engine uses: turn, then crop what is on show, then size it.
    val quarterTurned = ((state.rotationDelta / 90) % 2 + 2) % 2 == 1
    val turnedWidth = if (quarterTurned) base.height else base.width
    val turnedHeight = if (quarterTurned) base.width else base.height

    val croppedWidth = turnedWidth * (1f - state.crop.left - state.crop.right)
    val croppedHeight = turnedHeight * (1f - state.crop.top - state.crop.bottom)

    val resize = state.resize
    return if (resize != null) {
        val (sheetWidth, sheetHeight) = resize.sizeFor(croppedWidth, croppedHeight)
        SizeF(sheetWidth, sheetHeight)
    } else {
        SizeF(
            max(1f, croppedWidth * state.scale),
            max(1f, croppedHeight * state.scale),
        )
    }
}

/** "612 × 792 pt", for the readout under the size controls. */
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
    session: DocumentSession,
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
    // info they are derived from is deliberately never read during composition:
    // doing so recomposes the whole strip on every scroll frame.
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTarget by remember { mutableStateOf<Int?>(null) }
    var dragTranslation by remember { mutableStateOf(0f) }
    var scrollDirection by remember { mutableStateOf(0) }
    var itemWidthPx by remember { mutableStateOf(0f) }

    /**
     * Works out the drop slot from where the finger is over the strip, not from how
     * far it has travelled. Position-based means the list can scroll underneath
     * mid-drag and the target stays right, which is what makes edge auto-scroll
     * possible at all.
     */
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
            else -> visible.firstOrNull { pointerX >= it.offset && pointerX <= it.offset + it.size }
                ?.index
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

    // Auto-scroll while a drag is parked near either edge, so a long document can
    // be reordered without letting go.
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

    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(102.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(plan.pages.size) { position ->
                val page = plan.pages[position]
                val from = dragFrom
                val to = dragTarget
                val isDragged = from == position

                // Open a gap: everything between the origin and the target slides
                // one place over, so the drop position is obvious.
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
                    session = session,
                    cache = cache,
                    selected = page.sourceIndex in selection,
                    current = page.sourceIndex == currentSourceIndex,
                    selecting = selecting,
                    dragging = isDragged,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (isDragged) dragTranslation else slideBy
                            if (isDragged) {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                shadowElevation = 12f
                            }
                        }
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
                        .clickable { onTap(page.sourceIndex) },
                )
            }
        }
        Text(
            if (selecting) {
                "Tap pages to select them. Long-press and drag to reorder."
            } else {
                "Long-press and drag a page to reorder it."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
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
    selecting: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
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
        dragging -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.tertiary
        current && !selecting -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (dragging || selected || (current && !selecting)) 2.dp else 1.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(60.dp)
                .aspectRatio(0.72f)
                .border(borderWidth, borderColour)
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
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
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

@Composable
private fun EditorControls(
    state: EditorState,
    plan: EditPlan,
    onRotate: (Int) -> Unit,
    onCrop: () -> Unit,
    onResize: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onReverse: () -> Unit,
    onWatermark: () -> Unit,
    onNumbers: () -> Unit,
    onShowPending: () -> Unit,
    onSave: () -> Unit,
) {
    val currentDeleted = state.currentSourceIndex
        ?.let { source -> plan.pages.firstOrNull { it.sourceIndex == source }?.deleted }
        ?: false

    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(
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

        Text(
            "Next edit hits ${state.targetLabel()}.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        // Two rows of four. Reordering lives on the strip now, so the old
        // Earlier/Later arrows are gone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(Icons.AutoMirrored.Filled.RotateLeft, "Left") { onRotate(-90) }
            ToolButton(Icons.AutoMirrored.Filled.RotateRight, "Right") { onRotate(90) }
            ToolButton(Icons.Filled.Crop, "Crop", onClick = onCrop)
            ToolButton(Icons.Filled.ZoomOutMap, "Resize", onClick = onResize)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (currentDeleted) {
                ToolButton(Icons.Filled.RestoreFromTrash, "Restore", onClick = onRestore)
            } else {
                ToolButton(Icons.Filled.Delete, "Delete", onClick = onDelete)
            }
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

        Spacer(Modifier.height(6.dp))
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
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick)
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

private fun bucket(px: Int): Int {
    val bucketed = ((px + PREVIEW_BUCKET_PX - 1) / PREVIEW_BUCKET_PX) * PREVIEW_BUCKET_PX
    return bucketed.coerceIn(PREVIEW_BUCKET_PX, PREVIEW_MAX_PX)
}
