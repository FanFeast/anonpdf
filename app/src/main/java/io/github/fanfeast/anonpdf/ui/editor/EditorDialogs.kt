package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.WatermarkLayout
import io.github.fanfeast.anonpdf.pdf.WatermarkOptions
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.components.LabeledSlider
import kotlin.math.roundToInt

@Composable
fun ScaleDialog(
    targetLabel: String,
    initial: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var factor by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scale") },
        text = {
            Column {
                Text(
                    "Resizes the page and everything on it. Applies to $targetLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    label = "Size",
                    value = factor,
                    onValueChange = { factor = it },
                    valueRange = 0.25f..2f,
                    valueLabel = "${(factor * 100).roundToInt()}%",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.5f).forEach { preset ->
                        OutlinedButton(onClick = { factor = preset }) {
                            Text("${(preset * 100).roundToInt()}%")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(factor) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WatermarkDialog(
    initial: WatermarkOptions?,
    onDismiss: () -> Unit,
    onApply: (WatermarkOptions?) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var layout by remember { mutableStateOf(initial?.layout ?: WatermarkLayout.DIAGONAL) }
    var opacity by remember { mutableStateOf(initial?.opacity ?: 0.22f) }
    var fontSize by remember { mutableStateOf(initial?.fontSize ?: 52f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watermark") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "A watermark covers every page in the document.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                ChoiceChips(
                    label = "Placement",
                    options = WatermarkLayout.entries,
                    selected = layout,
                    onSelect = { layout = it },
                    optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                )
                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    label = "Opacity",
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.05f..1f,
                    valueLabel = "${(opacity * 100).roundToInt()}%",
                )
                LabeledSlider(
                    label = "Text size",
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..140f,
                    valueLabel = "${fontSize.roundToInt()} pt",
                )
                if (initial != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onApply(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove watermark") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        WatermarkOptions(
                            text = text.trim(),
                            fontSize = fontSize,
                            opacity = opacity,
                            layout = layout,
                        ),
                    )
                },
                enabled = text.isNotBlank(),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PageNumbersDialog(
    pageCount: Int,
    initial: PageNumberOptions?,
    onDismiss: () -> Unit,
    onApply: (PageNumberOptions?) -> Unit,
) {
    var format by remember { mutableStateOf(initial?.format ?: "{n}") }
    var position by remember {
        mutableStateOf(initial?.position ?: NumberPosition.BOTTOM_CENTER)
    }
    var startNumber by remember { mutableStateOf(initial?.startNumber ?: 1) }
    var skip by remember { mutableStateOf(initial?.firstPageIndex ?: 0) }
    var fontSize by remember { mutableStateOf(initial?.fontSize ?: 11f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page numbers") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    label = { Text("Format") },
                    supportingText = { Text("{n} is the number, {total} the count") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("{n}", "{n} / {total}", "Page {n}").forEach { preset ->
                        OutlinedButton(onClick = { format = preset }) {
                            Text(preset, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ChoiceChips(
                    label = "Position",
                    options = NumberPosition.entries,
                    selected = position,
                    onSelect = { position = it },
                    optionLabel = {
                        it.name.lowercase().split('_')
                            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
                    },
                )
                Spacer(Modifier.height(16.dp))
                LabeledSlider(
                    label = "Start numbering at",
                    value = startNumber.toFloat(),
                    onValueChange = { startNumber = it.roundToInt() },
                    valueRange = 0f..50f,
                    valueLabel = startNumber.toString(),
                )
                LabeledSlider(
                    label = "Skip first pages",
                    value = skip.toFloat(),
                    onValueChange = { skip = it.roundToInt() },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                    valueLabel = skip.toString(),
                )
                LabeledSlider(
                    label = "Text size",
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 7f..24f,
                    valueLabel = "${fontSize.roundToInt()} pt",
                )
                if (initial != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onApply(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove page numbers") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        PageNumberOptions(
                            format = format,
                            position = position,
                            startNumber = startNumber,
                            firstPageIndex = skip,
                            fontSize = fontSize,
                        ),
                    )
                },
                enabled = format.contains("{n}"),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The running list of edits, so the user can see and unwind what they have done. */
@Composable
fun PendingEditsDialog(
    descriptions: List<String>,
    onDismiss: () -> Unit,
    onUndoAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pending edits (${descriptions.size})") },
        text = {
            if (descriptions.isEmpty()) {
                Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    descriptions.forEachIndexed { index, description ->
                        Text(
                            "${index + 1}.  $description",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Nothing has been written yet. The original file is untouched " +
                            "until you save.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (descriptions.isNotEmpty()) {
                TextButton(onClick = onUndoAll) { Text("Undo all") }
            }
        },
    )
}
