@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.organize

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.PagePlan
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.ui.common.DocumentSession
import io.github.fanfeast.anonpdf.ui.common.OpenOutcome
import io.github.fanfeast.anonpdf.ui.common.openDocumentSession
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.BusyOverlay
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.ResultCard
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.launch

private const val THUMBNAIL_WIDTH_PX = 220

/** One tile in the grid: which source page it shows, and how it has been turned. */
private data class PageItem(
    val sourceIndex: Int,
    val rotationDelta: Int = 0,
)

@Composable
fun OrganizeScreen(
    initialUri: Uri?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DocumentStore(context) }
    val snackbarHost = remember { SnackbarHostState() }

    var session by remember { mutableStateOf<DocumentSession?>(null) }
    var items by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<ToolResult?>(null) }

    val thumbnails = remember {
        object : LruCache<Int, Bitmap>(24 * 1024) {
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
                    items = (0 until outcome.session.pageCount).map { PageItem(it) }
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

    val current = session

    Scaffold(
        topBar = {
            AnonTopBar(title = "Organise pages", onBack = onBack) {
                if (current != null) {
                    IconButton(
                        onClick = { items = (0 until current.pageCount).map { PageItem(it) } },
                    ) { Icon(Icons.Filled.Restore, contentDescription = "Reset") }
                }
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
                if (current == null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            "Reorder, rotate and delete pages, then save the result as a " +
                                "new file. The original is never modified.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose a PDF")
                        }
                        error?.let {
                            Spacer(Modifier.height(16.dp))
                            ErrorNote(it)
                        }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            current.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            "${items.size} of ${current.pageCount} pages kept",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { items = items.reversed() }) {
                                Text("Reverse")
                            }
                            OutlinedButton(
                                onClick = {
                                    items = items.map {
                                        it.copy(rotationDelta = it.rotationDelta + 90)
                                    }
                                },
                            ) { Text("Rotate all") }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (result != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            ResultCard(
                                result = result!!,
                                store = store,
                                onMessage = { scope.launch { snackbarHost.showSnackbar(it) } },
                                onStartOver = { result = null },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    error?.let {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            ErrorNote(it)
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    if (items.isEmpty()) {
                        Column(Modifier.padding(12.dp)) {
                            InfoNote(
                                "Every page has been removed. A PDF needs at least one " +
                                    "page — undo with the reset button above.",
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items.size) { position ->
                            val item = items[position]
                            PageTile(
                                item = item,
                                position = position,
                                total = items.size,
                                session = current,
                                cache = thumbnails,
                                onRotate = { degrees ->
                                    items = items.toMutableList().also { list ->
                                        list[position] =
                                            item.copy(
                                                rotationDelta = item.rotationDelta + degrees,
                                            )
                                    }
                                },
                                onDelete = {
                                    items = items.filterIndexed { i, _ -> i != position }
                                },
                                onMove = { offset ->
                                    val target = position + offset
                                    if (target in items.indices) {
                                        items = items.toMutableList().also { list ->
                                            val moved = list.removeAt(position)
                                            list.add(target, moved)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    Surface(tonalElevation = 3.dp) {
                        Column(Modifier.padding(12.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busyMessage = "Rebuilding the document…"
                                        progress = 0f
                                        error = null
                                        try {
                                            val output = store.newOutputFile(
                                                "${DocumentStore.stem(current.name)}-organised",
                                                "pdf",
                                            )
                                            PdfOps.organize(
                                                input = current.sourceFile,
                                                output = output,
                                                plan = items.map {
                                                    PagePlan(it.sourceIndex, it.rotationDelta)
                                                },
                                                password = current.password,
                                            ) { value -> progress = value }
                                            result = ToolResult.One(
                                                file = output,
                                                suggestedName =
                                                    "${DocumentStore.stem(current.name)}" +
                                                        "-organised.pdf",
                                                note = "${items.size} page" +
                                                    (if (items.size == 1) "" else "s") +
                                                    " in the new order.",
                                            )
                                        } catch (t: Throwable) {
                                            error = t.friendlyMessage("Could not save changes.")
                                        } finally {
                                            busyMessage = null
                                        }
                                    }
                                },
                                enabled = items.isNotEmpty() && busyMessage == null,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Apply and save") }
                        }
                    }
                }
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
            onConfirm = { candidate ->
                val uri = pendingUri
                if (uri != null) load(uri, candidate)
            },
        )
    }
}

@Composable
private fun PageTile(
    item: PageItem,
    position: Int,
    total: Int,
    session: DocumentSession,
    cache: LruCache<Int, Bitmap>,
    onRotate: (Int) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var bitmap by remember(item.sourceIndex) {
        mutableStateOf(cache.get(item.sourceIndex))
    }

    LaunchedEffect(item.sourceIndex) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = runCatching {
            session.rasterizer.renderByWidth(item.sourceIndex, THUMBNAIL_WIDTH_PX)
        }.getOrNull()
        if (rendered != null) {
            cache.put(item.sourceIndex, rendered)
            bitmap = rendered
        }
    }

    val size = session.pageSizes.getOrNull(item.sourceIndex)
    val baseAspect = if (size != null && size.width > 0f) size.width / size.height else 0.707f
    // A quarter turn swaps the tile's proportions.
    val quarterTurned = ((item.rotationDelta / 90) % 2 + 2) % 2 == 1
    val aspect = if (quarterTurned) 1f / baseAspect else baseAspect

    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect.coerceIn(0.3f, 3f))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center,
            ) {
                val current = bitmap
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = "Page ${item.sourceIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(item.rotationDelta.toFloat()),
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(20.dp))
                }
            }

            Text(
                text = "${position + 1}  ·  was ${item.sourceIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )

            // Two rows rather than one: five 48dp touch targets do not fit across a
            // grid tile, and shrinking them below 48dp fails accessibility sizing.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { onMove(-1) }, enabled = position > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Move earlier",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = position < total - 1) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Move later",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { onRotate(-90) }) {
                    Icon(
                        Icons.Filled.RotateLeft,
                        contentDescription = "Rotate left",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onRotate(90) }) {
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = "Rotate right",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove page",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
