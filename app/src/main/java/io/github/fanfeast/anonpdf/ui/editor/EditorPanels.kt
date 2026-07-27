package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PageOrientation
import io.github.fanfeast.anonpdf.pdf.PaperSize
import io.github.fanfeast.anonpdf.pdf.ResizeTarget
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.WatermarkLayout
import io.github.fanfeast.anonpdf.pdf.WatermarkOptions
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.components.LabeledSlider
import kotlin.math.roundToInt

/**
 * Shared shell for the tool panels.
 *
 * These live in the control area rather than in a dialog, deliberately: every
 * control here changes the page, and a modal would cover the very preview the
 * user needs to watch while dragging a slider.
 */
@Composable
private fun PanelFrame(
    title: String,
    subtitle: String?,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    applyLabel: String = "Apply",
    applyEnabled: Boolean = true,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .heightIn(max = 210.dp)
                .verticalScroll(rememberScrollState()),
        ) { content() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRemove != null) {
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApply, enabled = applyEnabled) { Text(applyLabel) }
        }
    }
}

@Composable
fun CropPanel(
    insets: CropInsets,
    targetLabel: String,
    onChange: (CropInsets) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = "Crop",
        subtitle = targetLabel,
        applyLabel = "Apply crop",
        onCancel = onCancel,
        onApply = onApply,
    ) {
        Text(
            "Drag the frame on the page above. Edges are measured from the page as " +
                "you see it, so a rotated page crops the way it looks.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "L ${pct(insets.left)}   T ${pct(insets.top)}   " +
                "R ${pct(insets.right)}   B ${pct(insets.bottom)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(CropInsets(0.06f, 0.06f, 0.06f, 0.06f)) }) {
                Text("6% all round")
            }
            OutlinedButton(onClick = { onChange(CropInsets()) }) { Text("Clear") }
        }
    }
}

/** Whether the page is being put on a named sheet or just scaled by a number. */
enum class SizeMode(val label: String) {
    PAPER("Paper size"),
    PERCENT("Percentage"),
}

@Composable
fun ResizePanel(
    mode: SizeMode,
    factor: Float,
    target: ResizeTarget,
    targetLabel: String,
    resultingSize: String,
    onModeChange: (SizeMode) -> Unit,
    onFactorChange: (Float) -> Unit,
    onTargetChange: (ResizeTarget) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = "Resize",
        subtitle = targetLabel,
        onCancel = onCancel,
        onApply = onApply,
    ) {
        ChoiceChips(
            label = "",
            options = SizeMode.entries,
            selected = mode,
            onSelect = onModeChange,
            optionLabel = { it.label },
        )
        Spacer(Modifier.height(10.dp))

        when (mode) {
            SizeMode.PAPER -> {
                Text(
                    "Puts the page on a sheet of this exact size. The preview above " +
                        "shows the result.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = "Sheet",
                    options = PaperSize.entries,
                    selected = target.paper,
                    onSelect = { onTargetChange(target.copy(paper = it)) },
                    optionLabel = { it.label },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = "Orientation",
                    options = PageOrientation.entries,
                    selected = target.orientation,
                    onSelect = { onTargetChange(target.copy(orientation = it)) },
                    optionLabel = { it.label },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = "Where they do not match",
                    options = listOf(false, true),
                    selected = target.fill,
                    onSelect = { onTargetChange(target.copy(fill = it)) },
                    optionLabel = { if (it) "Fill, crop overflow" else "Fit, leave margins" },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    resultingSize,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    target.paper.physical,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SizeMode.PERCENT -> {
                Text(
                    "Keeps the page's proportions and changes its size. The preview " +
                        "above changes size as you drag.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LabeledSlider(
                    label = "Size",
                    value = factor,
                    onValueChange = onFactorChange,
                    valueRange = 0.25f..2f,
                    valueLabel = "${(factor * 100).roundToInt()}%",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.5f).forEach { preset ->
                        OutlinedButton(onClick = { onFactorChange(preset) }) {
                            Text("${(preset * 100).roundToInt()}%")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    resultingSize,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun WatermarkPanel(
    options: WatermarkOptions,
    hasExisting: Boolean,
    onChange: (WatermarkOptions) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = "Watermark",
        subtitle = "every page",
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = options.text.isNotBlank(),
        onRemove = if (hasExisting) onRemove else null,
    ) {
        OutlinedTextField(
            value = options.text,
            onValueChange = { onChange(options.copy(text = it)) },
            label = { Text("Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            label = "Placement",
            options = WatermarkLayout.entries,
            selected = options.layout,
            onSelect = { onChange(options.copy(layout = it)) },
            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        )
        Spacer(Modifier.height(12.dp))
        LabeledSlider(
            label = "Opacity",
            value = options.opacity,
            onValueChange = { onChange(options.copy(opacity = it)) },
            valueRange = 0.05f..1f,
            valueLabel = "${(options.opacity * 100).roundToInt()}%",
        )
        LabeledSlider(
            label = "Text size",
            value = options.fontSize,
            onValueChange = { onChange(options.copy(fontSize = it)) },
            valueRange = 12f..140f,
            valueLabel = "${options.fontSize.roundToInt()} pt",
        )
    }
}

@Composable
fun PageNumbersPanel(
    pageCount: Int,
    options: PageNumberOptions,
    hasExisting: Boolean,
    onChange: (PageNumberOptions) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = "Page numbers",
        subtitle = "every page",
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = options.format.contains("{n}"),
        onRemove = if (hasExisting) onRemove else null,
    ) {
        OutlinedTextField(
            value = options.format,
            onValueChange = { onChange(options.copy(format = it)) },
            label = { Text("Format") },
            supportingText = { Text("{n} is the number, {total} the count") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("{n}", "{n} / {total}", "Page {n}").forEach { preset ->
                OutlinedButton(onClick = { onChange(options.copy(format = preset)) }) {
                    Text(preset, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            label = "Position",
            options = NumberPosition.entries,
            selected = options.position,
            onSelect = { onChange(options.copy(position = it)) },
            optionLabel = {
                it.name.lowercase().split('_')
                    .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
            },
        )
        Spacer(Modifier.height(12.dp))
        LabeledSlider(
            label = "Start numbering at",
            value = options.startNumber.toFloat(),
            onValueChange = { onChange(options.copy(startNumber = it.roundToInt())) },
            valueRange = 0f..50f,
            valueLabel = options.startNumber.toString(),
        )
        LabeledSlider(
            label = "Skip first pages",
            value = options.firstPageIndex.toFloat(),
            onValueChange = { onChange(options.copy(firstPageIndex = it.roundToInt())) },
            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
            valueLabel = options.firstPageIndex.toString(),
        )
        LabeledSlider(
            label = "Text size",
            value = options.fontSize,
            onValueChange = { onChange(options.copy(fontSize = it)) },
            valueRange = 7f..24f,
            valueLabel = "${options.fontSize.roundToInt()} pt",
        )
    }
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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

private fun pct(value: Float) = "${(value * 100).roundToInt()}%"
