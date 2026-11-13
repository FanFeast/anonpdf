package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.ImageFormat
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.PdfPageSizePreset
import io.github.fanfeast.anonpdf.pdf.PdfWrongPasswordException
import io.github.fanfeast.anonpdf.ui.common.DocumentRef
import io.github.fanfeast.anonpdf.ui.common.OnFilesDropped
import io.github.fanfeast.anonpdf.ui.common.fileName
import io.github.fanfeast.anonpdf.ui.common.rememberDocumentStore
import io.github.fanfeast.anonpdf.ui.common.rememberOpenImages
import io.github.fanfeast.anonpdf.ui.common.rememberOpenPdf
import io.github.fanfeast.anonpdf.ui.common.rememberOpenPdfs
import io.github.fanfeast.anonpdf.ui.components.AnonTopBar
import io.github.fanfeast.anonpdf.ui.components.BusyOverlay
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.components.ErrorNote
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.LabeledSlider
import io.github.fanfeast.anonpdf.ui.components.PasswordDialog
import io.github.fanfeast.anonpdf.ui.components.RemovableRow
import io.github.fanfeast.anonpdf.ui.components.ResultCard
import io.github.fanfeast.anonpdf.ui.components.SectionLabel
import io.github.fanfeast.anonpdf.ui.components.formatBytes
import io.github.fanfeast.anonpdf.ui.components.readableWidth
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ToolScreen(
    toolId: ToolId,
    initialDocument: DocumentRef?,
    onBack: () -> Unit,
) {
    val spec = remember(toolId) { ToolCatalog.spec(toolId) }
    val scope = rememberCoroutineScope()
    val store = rememberDocumentStore()
    val runner = remember { ToolRunner(store) }
    val options = remember { ToolOptionsState() }
    val snackbarHost = remember { SnackbarHostState() }

    var docs by remember { mutableStateOf<List<InputDoc>>(emptyList()) }
    var images by remember { mutableStateOf<List<DocumentRef>>(emptyList()) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ToolResult?>(null) }
    var passwordIndex by remember { mutableStateOf<Int?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHost.showSnackbar(text) }
    }

    /**
     * Copies the picked document into our cache and finds out what it is: how
     * many pages, and whether it needs a password before we can touch it.
     */
    fun addDocument(ref: DocumentRef, replace: Boolean) {
        scope.launch {
            busyMessage = S.readingFile
            progress = 0f
            error = null
            try {
                val name = store.displayName(ref)
                val size = store.sizeBytes(ref)
                val local = store.materialize(ref, name)
                val protected = PdfOps.isPasswordProtected(local)
                val pageCount = if (protected) 0 else PdfOps.readMeta(local).pageCount
                val doc = InputDoc(
                    ref = ref,
                    name = name,
                    sizeBytes = size,
                    localFile = local,
                    pageCount = pageCount,
                    needsPassword = protected,
                )
                docs = if (replace) listOf(doc) else docs + doc
                if (protected) {
                    passwordIndex = docs.lastIndex
                    passwordError = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.friendlyMessage(S.notAPdfThat)
            } finally {
                busyMessage = null
            }
        }
    }

    val pickSinglePdf = rememberOpenPdf { addDocument(it, replace = true) }
    val pickMultiplePdf = rememberOpenPdfs { picked -> picked.forEach { addDocument(it, replace = false) } }
    val pickImages = rememberOpenImages { picked -> images = images + picked }

    OnFilesDropped { dropped ->
        when (spec.input) {
            ToolInput.SINGLE_PDF -> dropped.pdfs.firstOrNull()
                ?.let { addDocument(it, replace = true) }
                ?: showMessage(S.onlyOnePdf)
            ToolInput.MULTIPLE_PDF -> if (dropped.pdfs.isEmpty()) {
                showMessage(S.onlyPdfsAdd)
            } else {
                dropped.pdfs.forEach { addDocument(it, replace = false) }
            }
            ToolInput.IMAGES -> if (dropped.images.isEmpty()) {
                showMessage(S.onlyPictures)
            } else {
                images = images + dropped.images
            }
        }
    }

    LaunchedEffect(initialDocument) {
        if (initialDocument != null && docs.isEmpty() && spec.input != ToolInput.IMAGES) {
            addDocument(initialDocument, replace = true)
        }
    }

    val inputsReady = when (spec.input) {
        ToolInput.SINGLE_PDF -> docs.size == 1 && docs.all { it.isReady }
        ToolInput.MULTIPLE_PDF -> docs.size >= 2 && docs.all { it.isReady }
        ToolInput.IMAGES -> images.isNotEmpty()
    }

    Scaffold(
        topBar = { AnonTopBar(title = spec.title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth(),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(spec.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                spec.caveat?.let {
                    InfoNote(it)
                    Spacer(Modifier.height(16.dp))
                }

                // ---- input selection ------------------------------------
                SectionLabel(if (spec.input == ToolInput.IMAGES) S.images_ else S.document)

                when (spec.input) {
                    ToolInput.SINGLE_PDF -> {
                        val doc = docs.firstOrNull()
                        if (doc == null) {
                            PickButton(S.choosePdf, Icons.Filled.Description) {
                                pickSinglePdf()
                            }
                        } else {
                            DocumentRow(
                                doc = doc,
                                onRemove = { docs = emptyList(); result = null },
                            )
                            if (doc.needsPassword) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = {
                                    passwordIndex = 0
                                    passwordError = null
                                }) { Text(S.enterPassword) }
                            }
                        }
                    }

                    ToolInput.MULTIPLE_PDF -> {
                        docs.forEachIndexed { index, doc ->
                            DocumentRow(
                                doc = doc,
                                onRemove = { docs = docs.filterIndexed { i, _ -> i != index } },
                                prefix = "${index + 1}.",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PickButton(S.addPdfs, Icons.Filled.Add) {
                            pickMultiplePdf()
                        }
                        if (docs.size in 1..1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                S.addOneMore,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ToolInput.IMAGES -> {
                        images.forEachIndexed { index, image ->
                            RemovableRow(
                                text = image.fileName() ?: S.imageNumber(index + 1),
                                supporting = S.imageNumber(index + 1),
                                onRemove = {
                                    images = images.filterIndexed { i, _ -> i != index }
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PickButton(S.addImages, Icons.Filled.Image) {
                            pickImages()
                        }
                    }
                }

                // ---- options --------------------------------------------
                if (inputsReady) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    SectionLabel(S.options)
                    ToolOptions(
                        spec = spec,
                        options = options,
                        pageCount = docs.firstOrNull()?.pageCount ?: 0,
                    )
                }

                error?.let {
                    Spacer(Modifier.height(16.dp))
                    ErrorNote(it)
                }

                result?.let { finished ->
                    Spacer(Modifier.height(20.dp))
                    ResultCard(
                        result = finished,
                        store = store,
                        onMessage = ::showMessage,
                        onStartOver = {
                            result = null
                            error = null
                        },
                    )
                }

                if (result == null) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                error = null
                                progress = 0f
                                busyMessage = S.working
                                try {
                                    result = runner.run(
                                        spec = spec,
                                        docs = docs,
                                        images = images,
                                        options = options,
                                    ) { value -> progress = value }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    error = t.friendlyMessage(S.thatDidNotWork)
                                } finally {
                                    busyMessage = null
                                }
                            }
                        },
                        enabled = inputsReady && busyMessage == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(spec.title)
                    }
                }

                Spacer(Modifier.height(40.dp))
            }

            busyMessage?.let { message ->
                BusyOverlay(progress = progress, message = message)
            }
        }
    }

    passwordIndex?.let { index ->
        val doc = docs.getOrNull(index)
        if (doc == null) {
            passwordIndex = null
        } else {
            PasswordDialog(
                message = S.protectedNamed(doc.name),
                error = passwordError,
                onDismiss = {
                    passwordIndex = null
                    passwordError = null
                },
                onConfirm = { candidate ->
                    scope.launch {
                        try {
                            val local = doc.localFile ?: error(S.fileMissing)
                            val meta = PdfOps.readMeta(local, candidate)
                            docs = docs.toMutableList().also { list ->
                                list[index] = doc.copy(
                                    password = candidate,
                                    pageCount = meta.pageCount,
                                    needsPassword = false,
                                )
                            }
                            // Unlock pre-fills its own field so the user types it once.
                            if (spec.id == ToolId.UNLOCK) options.unlockPassword = candidate
                            passwordIndex = null
                            passwordError = null
                        } catch (t: PdfWrongPasswordException) {
                            passwordError = S.wrongPassword
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            passwordError = t.friendlyMessage(S.couldNotOpenFile)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PickButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DocumentRow(doc: InputDoc, onRemove: () -> Unit, prefix: String? = null) {
    // Only mention what we actually know; "unknown size" is noise, not information.
    val supporting = listOfNotNull(
        doc.sizeBytes.takeIf { it >= 0 }?.let(::formatBytes),
        doc.pageCount.takeIf { it > 0 }
            ?.let { S.pages(it) },
        if (doc.needsPassword) S.locked else null,
        if (doc.password != null) S.unlocked else null,
    ).joinToString(" · ").ifEmpty { null }
    RemovableRow(
        text = listOfNotNull(prefix, doc.name).joinToString(" "),
        supporting = supporting,
        onRemove = onRemove,
    )
}

@Composable
private fun ToolOptions(spec: ToolSpec, options: ToolOptionsState, pageCount: Int) {
    when (spec.id) {
        ToolId.MERGE -> Text(
            S.mergeOrderHint,
            style = MaterialTheme.typography.bodyMedium,
        )

        ToolId.SPLIT -> {
            ChoiceChips(
                label = S.howToSplit,
                options = SplitMode.entries,
                selected = options.splitMode,
                onSelect = { options.splitMode = it },
                optionLabel = { it.label },
            )
            Spacer(Modifier.height(16.dp))
            if (options.splitMode == SplitMode.RANGES) {
                PageRangeField(options, pageCount, S.oneFilePerRange)
            } else {
                NumberStepper(
                    label = S.pagesPerFile,
                    value = options.splitEvery,
                    onChange = { options.splitEvery = it.coerceIn(1, maxOf(1, pageCount - 1)) },
                    range = 1..maxOf(1, pageCount - 1),
                )
            }
        }

        ToolId.EXTRACT -> PageRangeField(options, pageCount, S.pagesToKeep)

        ToolId.COMPRESS -> {
            ChoiceChips(
                label = S.mode,
                options = CompressMode.entries,
                selected = options.compressMode,
                onSelect = { options.compressMode = it },
                optionLabel = { it.label },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                options.compressMode.explanation,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ChoiceChips(
                label = S.strength,
                options = CompressStrength.entries,
                selected = options.compressStrength,
                onSelect = { options.compressStrength = it },
                optionLabel = { it.label },
            )
        }

        ToolId.PDF_TO_IMAGES -> {
            ChoiceChips(
                label = S.format,
                options = ImageFormat.entries,
                selected = options.exportFormat,
                onSelect = { options.exportFormat = it },
                optionLabel = { it.extension.uppercase() },
            )
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                label = S.resolution,
                value = options.exportDpi.toFloat(),
                onValueChange = { options.exportDpi = it.roundToInt() },
                valueRange = 72f..400f,
                valueLabel = "${options.exportDpi} dpi",
            )
            if (options.exportFormat == ImageFormat.JPEG) {
                LabeledSlider(
                    label = S.jpegQuality,
                    value = options.exportQuality.toFloat(),
                    onValueChange = { options.exportQuality = it.roundToInt() },
                    valueRange = 40f..100f,
                    valueLabel = "${options.exportQuality}%",
                )
            }
        }

        ToolId.IMAGES_TO_PDF -> {
            ChoiceChips(
                label = S.pageSize,
                options = PdfPageSizePreset.entries,
                selected = options.imagesPageSize,
                onSelect = { options.imagesPageSize = it },
                optionLabel = {
                    when (it) {
                        PdfPageSizePreset.FIT_IMAGE -> S.fitImage
                        PdfPageSizePreset.A4 -> "A4"
                        PdfPageSizePreset.LETTER -> S.paperLetter
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                label = S.margin,
                value = options.imagesMargin,
                onValueChange = { options.imagesMargin = it },
                valueRange = 0f..72f,
                valueLabel = "${options.imagesMargin.roundToInt()} pt",
            )
        }

        ToolId.EXTRACT_TEXT -> Text(
            S.textToTxt,
            style = MaterialTheme.typography.bodyMedium,
        )

        ToolId.PROTECT -> {
            OutlinedTextField(
                value = options.protectPassword,
                onValueChange = { options.protectPassword = it },
                label = { Text(S.newPassword) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = options.protectConfirm,
                onValueChange = { options.protectConfirm = it },
                label = { Text(S.confirmPassword) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = options.protectConfirm.isNotEmpty() &&
                    options.protectConfirm != options.protectPassword,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(S.allowReadersTo)
            CheckRow(S.allowPrint, options.allowPrinting) { options.allowPrinting = it }
            CheckRow(S.allowCopy, options.allowCopying) { options.allowCopying = it }
            CheckRow(S.allowEdit, options.allowModifying) { options.allowModifying = it }
            Spacer(Modifier.height(12.dp))
            InfoNote(
                S.protectHint,
            )
        }

        ToolId.UNLOCK -> Text(
            S.unlockHint,
            style = MaterialTheme.typography.bodyMedium,
        )

        ToolId.SIGN -> Unit
    }
}

@Composable
private fun PageRangeField(options: ToolOptionsState, pageCount: Int, label: String) {
    Column {
        OutlinedTextField(
            value = options.pageRange,
            onValueChange = { options.pageRange = it },
            label = { Text(label) },
            placeholder = { Text("1-3, 5, 8-10") },
            supportingText = {
                Text(S.documentHasPages(pageCount))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { options.pageRange = "1-$pageCount" }) {
                Text(S.allPagesButton)
            }
            if (pageCount > 1) {
                OutlinedButton(onClick = { options.pageRange = "1" }) { Text(S.first) }
                OutlinedButton(onClick = { options.pageRange = "$pageCount" }) { Text(S.last) }
            }
        }
    }
}

@Composable
private fun EdgeSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    LabeledSlider(
        label = label,
        value = value,
        onValueChange = onChange,
        valueRange = 0f..0.4f,
        valueLabel = "${(value * 100).roundToInt()}%",
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .width(52.dp)
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        IconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
