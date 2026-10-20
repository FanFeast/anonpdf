@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.sign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImageStamp
import io.github.fanfeast.anonpdf.pdf.PageImage
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.ui.common.DocumentRef
import io.github.fanfeast.anonpdf.ui.common.DocumentSession
import io.github.fanfeast.anonpdf.ui.common.OnFilesDropped
import io.github.fanfeast.anonpdf.ui.common.OpenOutcome
import io.github.fanfeast.anonpdf.ui.common.openDocumentSession
import io.github.fanfeast.anonpdf.ui.common.rememberDocumentStore
import io.github.fanfeast.anonpdf.ui.common.rememberOpenPdf
import io.github.fanfeast.anonpdf.ui.common.toImageBitmap
import io.github.fanfeast.anonpdf.ui.common.toPageImage
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.BusyOverlay
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.LabeledSlider
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.ResultCard
import io.github.fanfeast.anonpdf.ui.components.SectionLabel
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import io.github.fanfeast.anonpdf.ui.components.readableWidth
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PREVIEW_WIDTH_PX = 900

/** Signature ink options. Deliberately short: black or blue, like a real pen. */
private enum class InkColor(val label: String, val color: Color) {
    BLACK("Black", Color(0xFF111111)),
    BLUE("Blue", Color(0xFF12327A)),
}

@Composable
fun SignScreen(
    initialDocument: DocumentRef?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = rememberDocumentStore()
    val snackbarHost = remember { SnackbarHostState() }

    var session by remember { mutableStateOf<DocumentSession?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DocumentRef?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<ToolResult?>(null) }

    // Strokes are stored in pad units (fractions of the pad's width on both axes),
    // so the drawing survives a size change and keeps its proportions.
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var ink by remember { mutableStateOf(InkColor.BLACK) }

    var pageIndex by remember { mutableStateOf(0) }
    var centerX by remember { mutableStateOf(0.5f) }
    var centerY by remember { mutableStateOf(0.8f) }
    var widthRatio by remember { mutableStateOf(0.32f) }
    var pagePreview by remember { mutableStateOf<PageImage?>(null) }

    DisposableEffect(Unit) { onDispose { session?.close() } }

    fun load(ref: DocumentRef, password: String?) {
        scope.launch {
            loading = true
            error = null
            pending = ref
            when (val outcome = openDocumentSession(store, ref, password, session?.sourceFile)) {
                is OpenOutcome.Ready -> {
                    session?.close()
                    session = outcome.session
                    pageIndex = 0
                    pagePreview = null
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

    val pickPdf = rememberOpenPdf { load(it, null) }

    OnFilesDropped { dropped ->
        val pdf = dropped.pdfs.firstOrNull()
        if (pdf != null) {
            load(pdf, null)
        } else {
            scope.launch { snackbarHost.showSnackbar("Only a PDF can be signed.") }
        }
    }

    LaunchedEffect(initialDocument) {
        if (initialDocument != null && session == null) load(initialDocument, null)
    }

    val current = session

    LaunchedEffect(current, pageIndex) {
        val document = current ?: return@LaunchedEffect
        pagePreview = null
        pagePreview = runCatching {
            document.rasterizer.renderByWidth(pageIndex, PREVIEW_WIDTH_PX)
        }.getOrNull()
    }

    val signatureBitmap = remember(strokes, ink) {
        if (strokes.isEmpty()) null else renderSignature(strokes, ink.color)
    }

    Scaffold(
        topBar = { AnonTopBar(title = "Sign PDF", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth(),
            ) {
                Spacer(Modifier.height(4.dp))

                if (current == null) {
                    Text(
                        "Draw your signature and drop it onto a page. Everything " +
                            "happens on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = pickPdf,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose a PDF")
                    }
                } else {
                    Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Spacer(Modifier.height(16.dp))

                    SectionLabel("1. Draw your signature")
                    SignaturePad(
                        strokes = strokes,
                        activeStroke = activeStroke,
                        ink = ink.color,
                        onStrokeStart = { activeStroke = listOf(it) },
                        onStrokeMove = { activeStroke = activeStroke + it },
                        onStrokeEnd = {
                            if (activeStroke.isNotEmpty()) strokes = strokes + listOf(activeStroke)
                            activeStroke = emptyList()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { strokes = strokes.dropLast(1) },
                            enabled = strokes.isNotEmpty(),
                        ) { Text("Undo") }
                        OutlinedButton(
                            onClick = { strokes = emptyList() },
                            enabled = strokes.isNotEmpty(),
                        ) { Text("Clear") }
                    }
                    Spacer(Modifier.height(12.dp))
                    ChoiceChips(
                        label = "Ink",
                        options = InkColor.entries,
                        selected = ink,
                        onSelect = { ink = it },
                        optionLabel = { it.label },
                    )

                    Spacer(Modifier.height(24.dp))
                    SectionLabel("2. Place it")

                    if (current.pageCount > 1) {
                        PageChooser(
                            pageCount = current.pageCount,
                            selected = pageIndex,
                            onSelect = { pageIndex = it },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        "Tap or click the page where you want your signature.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    PagePlacement(
                        preview = pagePreview,
                        pageAspect = current.pageSizes.getOrNull(pageIndex)?.let { size ->
                            if (size.height > 0f) size.width / size.height else 0.707f
                        } ?: 0.707f,
                        signature = signatureBitmap,
                        centerX = centerX,
                        centerY = centerY,
                        widthRatio = widthRatio,
                        onMove = { x, y ->
                            centerX = x
                            centerY = y
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    LabeledSlider(
                        label = "Signature width",
                        value = widthRatio,
                        onValueChange = { widthRatio = it },
                        valueRange = 0.08f..0.7f,
                        valueLabel = "${(widthRatio * 100).roundToInt()}% of page",
                    )

                    error?.let {
                        Spacer(Modifier.height(16.dp))
                        ErrorNote(it)
                    }

                    result?.let { finished ->
                        Spacer(Modifier.height(20.dp))
                        ResultCard(
                            result = finished,
                            store = store,
                            onMessage = { scope.launch { snackbarHost.showSnackbar(it) } },
                            onStartOver = { result = null },
                        )
                    }

                    if (result == null) {
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val bitmap = signatureBitmap
                                if (bitmap == null) {
                                    error = "Draw a signature first."
                                    return@Button
                                }
                                scope.launch {
                                    busyMessage = "Adding your signature…"
                                    progress = 0f
                                    error = null
                                    try {
                                        val stem = DocumentStore.stem(current.name)
                                        val output = store.newOutputFile("$stem-signed", "pdf")
                                        PdfOps.stampImages(
                                            input = current.sourceFile,
                                            output = output,
                                            stamps = listOf(
                                                ImageStamp(
                                                    pageIndex = pageIndex,
                                                    bitmap = bitmap.toPageImage(),
                                                    centerXRatio = centerX,
                                                    centerYRatio = centerY,
                                                    widthRatio = widthRatio,
                                                ),
                                            ),
                                            password = current.password,
                                        ) { value -> progress = value }
                                        result = ToolResult.One(
                                            file = output,
                                            suggestedName = "$stem-signed.pdf",
                                            note = "Signature placed on page ${pageIndex + 1}.",
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (t: Throwable) {
                                        error = t.friendlyMessage("Could not sign the document.")
                                    } finally {
                                        busyMessage = null
                                    }
                                }
                            },
                            enabled = busyMessage == null && strokes.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sign and save") }
                    }

                    Spacer(Modifier.height(16.dp))
                    InfoNote(
                        "This stamps a picture of your signature onto the page. It is " +
                            "not a cryptographic digital signature, so it proves no more " +
                            "than ink on paper does.",
                    )
                }

                error?.takeIf { current == null }?.let {
                    Spacer(Modifier.height(16.dp))
                    ErrorNote(it)
                }

                Spacer(Modifier.height(40.dp))
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            busyMessage?.let { BusyOverlay(progress = progress, message = it) }
        }
    }

    if (askPassword) {
        PasswordDialog(
            error = passwordError,
            onDismiss = {
                askPassword = false
                if (session == null) onBack()
            },
            onConfirm = { candidate -> pending?.let { load(it, candidate) } },
        )
    }
}

@Composable
private fun SignaturePad(
    strokes: List<List<Offset>>,
    activeStroke: List<Offset>,
    ink: Color,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Signature pad" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onStrokeStart(offset.inPadUnits(size)) },
                    onDrag = { change, _ -> onStrokeMove(change.position.inPadUnits(size)) },
                    onDragEnd = { onStrokeEnd() },
                    onDragCancel = { onStrokeEnd() },
                )
            }
            // A tap is a dot: the one over an "i", a full stop after an initial.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onStrokeStart(offset.inPadUnits(size))
                    onStrokeEnd()
                }
            },
    ) {
        // Pad units are fractions of the width on both axes.
        val unit = this.size.width
        val all = if (activeStroke.isEmpty()) strokes else strokes + listOf(activeStroke)
        all.forEach { stroke ->
            for (index in 1 until stroke.size) {
                drawLine(
                    color = ink,
                    start = Offset(stroke[index - 1].x * unit, stroke[index - 1].y * unit),
                    end = Offset(stroke[index].x * unit, stroke[index].y * unit),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (stroke.size == 1) {
                drawCircle(
                    color = ink,
                    radius = 2.dp.toPx(),
                    center = Offset(stroke[0].x * unit, stroke[0].y * unit),
                )
            }
        }
        if (strokes.isEmpty() && activeStroke.isEmpty()) {
            drawLine(
                color = Color(0xFFBBBBBB),
                start = Offset(this.size.width * 0.08f, this.size.height * 0.72f),
                end = Offset(this.size.width * 0.92f, this.size.height * 0.72f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(12f, 12f),
                ),
            )
        }
    }
}

@Composable
private fun PageChooser(pageCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    ChoiceChips(
        label = "Page",
        options = (0 until pageCount).toList(),
        selected = selected,
        onSelect = onSelect,
        optionLabel = { "${it + 1}" },
    )
}

@Composable
private fun PagePlacement(
    preview: PageImage?,
    pageAspect: Float,
    signature: ImageBitmap?,
    centerX: Float,
    centerY: Float,
    widthRatio: Float,
    onMove: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            // Bounded height, and driven by height rather than width, so a portrait
            // page does not take over the whole scrolling screen.
            .heightIn(max = 440.dp)
            .aspectRatio(pageAspect.coerceIn(0.3f, 3f), matchHeightConstraintsFirst = true)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            // Taps only, deliberately. A drag handler here would consume the
            // vertical gesture and trap the surrounding scroll, leaving the user
            // unable to reach the button below.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = offset.normalizedIn(size)
                    onMove(normalized.x, normalized.y)
                }
            },
    ) {
        val previewImage = remember(preview) { preview?.toImageBitmap() }
        if (previewImage != null) {
            Image(
                bitmap = previewImage,
                contentDescription = "Page preview",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        }

        if (signature != null) {
            val signatureWidth = maxWidth * widthRatio
            val signatureHeight =
                signatureWidth * (signature.height / signature.width.toFloat())
            Image(
                bitmap = signature,
                contentDescription = "Signature",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset(
                        x = maxWidth * centerX - signatureWidth / 2,
                        y = maxHeight * centerY - signatureHeight / 2,
                    )
                    .width(signatureWidth)
                    .height(signatureHeight),
            )
        }
    }
}

private fun Offset.inPadUnits(size: androidx.compose.ui.unit.IntSize): Offset =
    SignatureFrame.padPoint(x, y, size.width, size.height).let { (px, py) -> Offset(px, py) }

private fun Offset.normalizedIn(size: androidx.compose.ui.unit.IntSize): Offset = Offset(
    if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f,
    if (size.height > 0) (y / size.height).coerceIn(0f, 1f) else 0f,
)

/**
 * Rasterises the drawn strokes onto a transparent bitmap, cropped to the ink's
 * bounding box so placement on the page is predictable. Strokes are in pad units,
 * so the bitmap keeps the proportions the signature was drawn with.
 */
private fun renderSignature(strokes: List<List<Offset>>, color: Color): ImageBitmap? {
    val frame = SignatureFrame.of(strokes.flatten().map { it.x to it.y }) ?: return null
    val targetWidth = 1200
    val (width, height) = frame.bitmapSize(targetWidth)

    val bitmap = ImageBitmap(width, height)
    val canvas = ImageCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = PaintingStyle.Stroke
        strokeWidth = targetWidth * 0.012f
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    }
    val dotPaint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = PaintingStyle.Fill
    }

    strokes.forEach { stroke ->
        val x0 = frame.mapX(stroke.firstOrNull()?.x ?: return@forEach, targetWidth)
        val y0 = frame.mapY(stroke.first().y, targetWidth)
        if (stroke.size == 1) {
            canvas.drawCircle(Offset(x0, y0), paint.strokeWidth / 2f, dotPaint)
            return@forEach
        }
        val path = Path()
        path.moveTo(x0, y0)
        stroke.drop(1).forEach { point ->
            path.lineTo(frame.mapX(point.x, targetWidth), frame.mapY(point.y, targetWidth))
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
