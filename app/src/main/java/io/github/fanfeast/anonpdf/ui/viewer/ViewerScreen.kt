@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fanfeast.anonpdf.ui.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.SizeF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.data.AppPreferences
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.PdfRasterizer
import io.github.fanfeast.anonpdf.pdf.PdfWrongPasswordException
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Zoom is capped: past this the bitmaps get bigger than any benefit. */
private const val MAX_ZOOM = 5f
private const val MIN_ZOOM = 1f

/** Render widths snap to this grid so a pinch does not trigger a render per pixel. */
private const val WIDTH_BUCKET_PX = 256
private const val MAX_RENDER_WIDTH_PX = 2560

private val INVERT_MATRIX = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

private class OpenedDocument(
    val rasterizer: PdfRasterizer,
    val pageSizes: List<SizeF>,
    /** The file PDFBox should read for text search — decrypted if it had to be. */
    val textSource: File,
    val password: String?,
)

@Composable
fun ViewerScreen(
    uri: Uri,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val store = remember { DocumentStore(context) }
    val preferences = remember { AppPreferences(context) }

    var title by remember { mutableStateOf("Loading…") }
    var opened by remember { mutableStateOf<OpenedDocument?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var localFile by remember { mutableStateOf<File?>(null) }

    var zoom by remember { mutableStateOf(1f) }
    var invert by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()

    // A modest bitmap budget. Entries are never recycled on eviction because a
    // composable may still be drawing one; the GC reclaims them instead.
    val bitmapCache = remember {
        object : LruCache<String, Bitmap>(64 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    LaunchedEffect(uri) {
        invert = preferences.invertPdfColors.first()
        if (preferences.keepScreenOn.first()) view.keepScreenOn = true
    }

    DisposableEffect(Unit) {
        onDispose {
            opened?.rasterizer?.close()
            view.keepScreenOn = false
        }
    }

    suspend fun openWith(password: String?) {
        loading = true
        error = null
        try {
            val name = store.displayName(uri)
            title = name
            val file = localFile ?: store.materialize(uri, name).also { localFile = it }

            if (password == null && PdfOps.isPasswordProtected(file)) {
                askPassword = true
                loading = false
                return
            }

            // PdfRenderer cannot open an encrypted PDF, so when we have the
            // password we hand it a decrypted copy kept in our private cache.
            val readable = if (password == null) {
                file
            } else {
                withContext(Dispatchers.IO) {
                    val plain = File(file.parentFile, "open-${System.nanoTime()}.pdf")
                    PdfOps.load(file, password).use { document ->
                        document.isAllSecurityToBeRemoved = true
                        document.save(plain)
                    }
                    plain
                }
            }

            val rasterizer = withContext(Dispatchers.IO) { PdfRasterizer.open(readable) }
            opened?.rasterizer?.close()
            bitmapCache.evictAll()
            opened = OpenedDocument(
                rasterizer = rasterizer,
                pageSizes = rasterizer.pageSizes(),
                textSource = file,
                password = password,
            )
            preferences.tryPersistAccess(uri)
            preferences.addRecent(uri, name)
            askPassword = false
        } catch (t: PdfWrongPasswordException) {
            passwordError = "That password did not work."
            askPassword = true
        } catch (t: Throwable) {
            error = t.friendlyMessage("This file could not be opened as a PDF.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(uri) { openWith(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { currentPage = it }
    }

    val document = opened

    Scaffold(
        topBar = {
            AnonTopBar(title = title, onBack = onBack) {
                IconButton(onClick = { searchOpen = true }, enabled = document != null) {
                    Icon(Icons.Filled.Search, contentDescription = "Search text")
                }
                // One entry point for everything: the editor holds every tool,
                // including the whole-file ones. A second "tools" menu here would
                // just be the same list reached a different way.
                IconButton(onClick = onEdit, enabled = document != null) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = {
                    invert = !invert
                    scope.launch { preferences.setInvertPdfColors(invert) }
                }) {
                    Icon(Icons.Filled.Contrast, contentDescription = "Invert colours")
                }
            }
        },
        bottomBar = {
            if (document != null) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showJump = true }) {
                            Text("${currentPage + 1} / ${document.pageSizes.size}")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { zoom = (zoom - 0.5f).coerceAtLeast(MIN_ZOOM) },
                            enabled = zoom > MIN_ZOOM,
                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { zoom = 1f }) {
                            Text("${(zoom * 100).roundToInt()}%")
                        }
                        IconButton(
                            onClick = { zoom = (zoom + 0.5f).coerceAtMost(MAX_ZOOM) },
                            enabled = zoom < MAX_ZOOM,
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            when {
                error != null -> Column(Modifier.padding(16.dp)) { ErrorNote(error!!) }

                loading || document == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val containerWidthPx = with(density) { maxWidth.toPx() }
                    val contentWidthPx = containerWidthPx * zoom
                    val contentWidthDp = with(density) { contentWidthPx.toDp() }
                    val renderWidthPx = bucketWidth(contentWidthPx.roundToInt())

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                            // Only claim the gesture once a second finger lands, so
                            // one-finger scrolling still belongs to the list.
                            .pinchToZoom { factor ->
                                zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(contentWidthDp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                vertical = 10.dp,
                            ),
                        ) {
                            items(document.pageSizes.size) { index ->
                                PdfPage(
                                    index = index,
                                    pageSize = document.pageSizes[index],
                                    contentWidthPx = contentWidthPx,
                                    renderWidthPx = renderWidthPx,
                                    rasterizer = document.rasterizer,
                                    cache = bitmapCache,
                                    invert = invert,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (askPassword) {
        PasswordDialog(
            error = passwordError,
            onDismiss = {
                askPassword = false
                if (opened == null) onBack()
            },
            onConfirm = { candidate ->
                passwordError = null
                scope.launch { openWith(candidate) }
            },
        )
    }

    if (showJump && document != null) {
        JumpToPageDialog(
            pageCount = document.pageSizes.size,
            onDismiss = { showJump = false },
            onJump = { page ->
                showJump = false
                scope.launch { listState.scrollToItem(page) }
            },
        )
    }

    if (searchOpen && document != null) {
        SearchDialog(
            source = document.textSource,
            password = document.password,
            pageCount = document.pageSizes.size,
            onDismiss = { searchOpen = false },
            onGoToPage = { page ->
                searchOpen = false
                scope.launch { listState.scrollToItem(page) }
            },
        )
    }
}

/**
 * One page. Sized from the PDF's own aspect ratio so the list has a correct
 * scroll extent before any bitmap exists, then filled in asynchronously.
 */
@Composable
private fun PdfPage(
    index: Int,
    pageSize: SizeF,
    contentWidthPx: Float,
    renderWidthPx: Int,
    rasterizer: PdfRasterizer,
    cache: LruCache<String, Bitmap>,
    invert: Boolean,
) {
    val density = LocalDensity.current
    val aspect = if (pageSize.width > 0f) pageSize.height / pageSize.width else 1.414f
    val heightDp = with(density) { (contentWidthPx * aspect).toDp() }
    val key = "$index@$renderWidthPx"

    var bitmap by remember(key) { mutableStateOf(cache.get(key)) }

    LaunchedEffect(key) {
        if (bitmap != null) return@LaunchedEffect
        val rendered = runCatching { rasterizer.renderByWidth(index, renderWidthPx) }.getOrNull()
        if (rendered != null) {
            cache.put(key, rendered)
            bitmap = rendered
        }
    }

    Surface(
        color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = if (invert) {
                    ColorFilter.colorMatrix(ColorMatrix(INVERT_MATRIX))
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text("Page number") },
                supportingText = { Text("1 to $pageCount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { page?.let { onJump(it - 1) } },
                enabled = page != null && page in 1..pageCount,
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class SearchHit(val pageIndex: Int, val snippet: String)

@Composable
private fun SearchDialog(
    source: File,
    password: String?,
    pageCount: Int,
    onDismiss: () -> Unit,
    onGoToPage: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search text") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                when {
                    searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Reading the document…")
                    }

                    message != null -> Text(
                        message!!,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    hits != null -> {
                        val found = hits!!
                        if (found.isEmpty()) {
                            Text("No matches.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyColumn(Modifier.height(240.dp)) {
                                items(found.size) { index ->
                                    val hit = found[index]
                                    TextButton(
                                        onClick = { onGoToPage(hit.pageIndex) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                "Page ${hit.pageIndex + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            Text(
                                                hit.snippet,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        searching = true
                        message = null
                        hits = null
                        try {
                            val text = PdfOps.extractText(source, password)
                            hits = findHits(text, query, pageCount)
                        } catch (t: Throwable) {
                            message = t.friendlyMessage("Could not read the text.")
                        } finally {
                            searching = false
                        }
                    }
                },
                enabled = query.isNotBlank() && !searching,
            ) { Text("Search") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Splits the extracted text back into pages using the "--- Page n ---" markers
 * [PdfOps.extractText] writes, then collects one hit per page with context.
 */
private fun findHits(text: String, query: String, pageCount: Int): List<SearchHit> {
    if (query.isBlank()) return emptyList()
    val hits = mutableListOf<SearchHit>()

    val pages: List<String> = if (pageCount > 1) {
        // drop(1): everything before the first marker is empty by construction.
        text.split(Regex("--- Page \\d+ ---")).drop(1)
    } else {
        listOf(text)
    }

    pages.forEachIndexed { index, pageText ->
        val position = pageText.indexOf(query, ignoreCase = true)
        if (position >= 0) {
            val start = max(0, position - 40)
            val end = minOf(pageText.length, position + query.length + 60)
            val snippet = pageText.substring(start, end)
                .replace(Regex("\\s+"), " ")
                .trim()
            hits += SearchHit(index, if (start > 0) "…$snippet" else snippet)
        }
    }
    return hits
}

/** Recognises a two-finger pinch without stealing single-finger drags. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var previousPointerCount = 0
        do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }
            // Ignore the first multi-touch frame: its zoom factor is measured
            // against a single-pointer centroid and jumps wildly.
            if (pointerCount >= 2 && previousPointerCount >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) {
                    onZoom(factor)
                    event.changes.forEach { it.consume() }
                }
            }
            previousPointerCount = pointerCount
        } while (event.changes.any { it.pressed })
    }
}

private fun bucketWidth(px: Int): Int {
    val bucketed = ((px + WIDTH_BUCKET_PX - 1) / WIDTH_BUCKET_PX) * WIDTH_BUCKET_PX
    return bucketed.coerceIn(WIDTH_BUCKET_PX, MAX_RENDER_WIDTH_PX)
}
