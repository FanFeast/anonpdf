package io.github.fanfeast.anonpdf.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.ImageFormat
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.PdfPageSizePreset
import io.github.fanfeast.anonpdf.pdf.PdfWrongPasswordException
import io.github.fanfeast.anonpdf.pdf.WatermarkLayout
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
import io.github.fanfeast.anonpdf.ui.components.friendlyMessage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PDF_MIME = "application/pdf"

@Composable
fun ToolScreen(
    toolId: ToolId,
    initialUri: Uri?,
    onBack: () -> Unit,
) {
    val spec = remember(toolId) { ToolCatalog.spec(toolId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DocumentStore(context) }
    val runner = remember { ToolRunner(context, store) }
    val options = remember { ToolOptionsState() }
    val snackbarHost = remember { SnackbarHostState() }

    var docs by remember { mutableStateOf<List<InputDoc>>(emptyList()) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
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
    fun addDocument(uri: Uri, replace: Boolean) {
        scope.launch {
            busyMessage = "Reading the file…"
            progress = 0f
            error = null
            try {
                val name = store.displayName(uri)
                val size = store.sizeBytes(uri)
                val local = store.materialize(uri, name)
                val protected = PdfOps.isPasswordProtected(local)
                val pageCount = if (protected) 0 else PdfOps.readMeta(local).pageCount
                val doc = InputDoc(
                    uri = uri,
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
            } catch (t: Throwable) {
                error = t.friendlyMessage("That file could not be opened as a PDF.")
            } finally {
                busyMessage = null
            }
        }
    }

    val pickSinglePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) addDocument(uri, replace = true) }

    val pickMultiplePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach { addDocument(it, replace = false) } }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) imageUris = imageUris + uris }

    LaunchedEffect(initialUri) {
        if (initialUri != null && docs.isEmpty() && spec.input != ToolInput.IMAGES) {
            addDocument(initialUri, replace = true)
        }
    }

    val inputsReady = when (spec.input) {
        ToolInput.SINGLE_PDF -> docs.size == 1 && docs.all { it.isReady }
        ToolInput.MULTIPLE_PDF -> docs.size >= 2 && docs.all { it.isReady }
        ToolInput.IMAGES -> imageUris.isNotEmpty()
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
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(spec.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                spec.caveat?.let {
                    InfoNote(it)
                    Spacer(Modifier.height(16.dp))
                }

                // ---- input selection ------------------------------------
                SectionLabel(if (spec.input == ToolInput.IMAGES) "Images" else "Document")

                when (spec.input) {
                    ToolInput.SINGLE_PDF -> {
                        val doc = docs.firstOrNull()
                        if (doc == null) {
                            PickButton("Choose a PDF", Icons.Filled.Description) {
                                pickSinglePdf.launch(arrayOf(PDF_MIME))
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
                                }) { Text("Enter password") }
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
                        PickButton("Add PDFs", Icons.Filled.Add) {
                            pickMultiplePdf.launch(arrayOf(PDF_MIME))
                        }
                        if (docs.size in 1..1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Add at least one more file. They are merged in the " +
                                    "order listed above.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ToolInput.IMAGES -> {
                        imageUris.forEachIndexed { index, uri ->
                            RemovableRow(
                                text = uri.lastPathSegment?.substringAfterLast('/')
                                    ?: "Image ${index + 1}",
                                supporting = "Image ${index + 1}",
                                onRemove = {
                                    imageUris = imageUris.filterIndexed { i, _ -> i != index }
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PickButton("Add images", Icons.Filled.Image) {
                            pickImages.launch(arrayOf("image/*"))
                        }
                    }
                }

                // ---- options --------------------------------------------
                if (inputsReady) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Options")
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
                                busyMessage = "Working…"
                                try {
                                    result = runner.run(
                                        spec = spec,
                                        docs = docs,
                                        imageUris = imageUris,
                                        options = options,
                                    ) { value -> progress = value }
                                } catch (t: Throwable) {
                                    error = t.friendlyMessage("That did not work.")
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
                message = "\"${doc.name}\" is protected. Enter its password to continue.",
                error = passwordError,
                onDismiss = {
                    passwordIndex = null
                    passwordError = null
                },
                onConfirm = { candidate ->
                    scope.launch {
                        try {
                            val local = doc.localFile ?: error("File is missing.")
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
                            passwordError = "That password did not work."
                        } catch (t: Throwable) {
                            passwordError = t.friendlyMessage("Could not open the file.")
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
            ?.let { "$it page${if (it == 1) "" else "s"}" },
        if (doc.needsPassword) "locked" else null,
        if (doc.password != null) "unlocked" else null,
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
            "Files are merged top to bottom. Remove and re-add a file to change " +
                "its place.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ToolId.SPLIT -> {
            ChoiceChips(
                label = "How to split",
                options = SplitMode.entries,
                selected = options.splitMode,
                onSelect = { options.splitMode = it },
                optionLabel = { it.label },
            )
            Spacer(Modifier.height(16.dp))
            if (options.splitMode == SplitMode.RANGES) {
                PageRangeField(options, pageCount, "One file per range")
            } else {
                NumberStepper(
                    label = "Pages per file",
                    value = options.splitEvery,
                    onChange = { options.splitEvery = it.coerceIn(1, maxOf(1, pageCount - 1)) },
                    range = 1..maxOf(1, pageCount - 1),
                )
            }
        }

        ToolId.EXTRACT -> PageRangeField(options, pageCount, "Pages to keep")

        ToolId.COMPRESS -> {
            ChoiceChips(
                label = "Mode",
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
                label = "Strength",
                options = CompressStrength.entries,
                selected = options.compressStrength,
                onSelect = { options.compressStrength = it },
                optionLabel = { it.label },
            )
        }

        ToolId.PDF_TO_IMAGES -> {
            ChoiceChips(
                label = "Format",
                options = ImageFormat.entries,
                selected = options.exportFormat,
                onSelect = { options.exportFormat = it },
                optionLabel = { it.extension.uppercase() },
            )
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                label = "Resolution",
                value = options.exportDpi.toFloat(),
                onValueChange = { options.exportDpi = it.roundToInt() },
                valueRange = 72f..400f,
                valueLabel = "${options.exportDpi} dpi",
            )
            if (options.exportFormat == ImageFormat.JPEG) {
                LabeledSlider(
                    label = "JPEG quality",
                    value = options.exportQuality.toFloat(),
                    onValueChange = { options.exportQuality = it.roundToInt() },
                    valueRange = 40f..100f,
                    valueLabel = "${options.exportQuality}%",
                )
            }
        }

        ToolId.IMAGES_TO_PDF -> {
            ChoiceChips(
                label = "Page size",
                options = PdfPageSizePreset.entries,
                selected = options.imagesPageSize,
                onSelect = { options.imagesPageSize = it },
                optionLabel = {
                    when (it) {
                        PdfPageSizePreset.FIT_IMAGE -> "Fit image"
                        PdfPageSizePreset.A4 -> "A4"
                        PdfPageSizePreset.LETTER -> "Letter"
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            LabeledSlider(
                label = "Margin",
                value = options.imagesMargin,
                onValueChange = { options.imagesMargin = it },
                valueRange = 0f..72f,
                valueLabel = "${options.imagesMargin.roundToInt()} pt",
            )
        }

        ToolId.EXTRACT_TEXT -> Text(
            "Text is written to a .txt file in reading order.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ToolId.PROTECT -> {
            OutlinedTextField(
                value = options.protectPassword,
                onValueChange = { options.protectPassword = it },
                label = { Text("New password") },
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
                label = { Text("Confirm password") },
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
            SectionLabel("Allow readers to")
            CheckRow("Print", options.allowPrinting) { options.allowPrinting = it }
            CheckRow("Copy text", options.allowCopying) { options.allowCopying = it }
            CheckRow("Edit", options.allowModifying) { options.allowModifying = it }
            Spacer(Modifier.height(12.dp))
            InfoNote(
                "AES-256 encryption, done on this device. AnonPDF never stores or " +
                    "transmits the password — if you lose it the file cannot be opened.",
            )
        }

        ToolId.UNLOCK -> Text(
            "The password you entered when opening the file is used to remove its " +
                "protection.",
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
                Text("This document has $pageCount page${if (pageCount == 1) "" else "s"}")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { options.pageRange = "1-$pageCount" }) {
                Text("All pages")
            }
            if (pageCount > 1) {
                OutlinedButton(onClick = { options.pageRange = "1" }) { Text("First") }
                OutlinedButton(onClick = { options.pageRange = "$pageCount" }) { Text("Last") }
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
