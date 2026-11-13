package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.CropInsets
import io.github.fanfeast.anonpdf.pdf.MarkRect
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PageOrientation
import io.github.fanfeast.anonpdf.pdf.PaperSize
import io.github.fanfeast.anonpdf.pdf.ResizeTarget
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.TextMark
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
internal fun PanelFrame(
    title: String,
    subtitle: String?,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    applyLabel: String = S.apply,
    applyEnabled: Boolean = true,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
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
        // Takes what is left and scrolls, so the Cancel/Apply row below stays put
        // however far the drawer is pulled down.
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) { content() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRemove != null) {
                OutlinedButton(onClick = onRemove) { Text(S.remove) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text(S.cancel) }
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
        title = S.toolCrop,
        subtitle = targetLabel,
        applyLabel = S.applyCrop,
        onCancel = onCancel,
        onApply = onApply,
    ) {
        Text(
            S.cropHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            S.cropEdges(pct(insets.left), pct(insets.top), pct(insets.right), pct(insets.bottom)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(CropInsets(0.06f, 0.06f, 0.06f, 0.06f)) }) {
                Text(S.cropAllRound)
            }
            OutlinedButton(onClick = { onChange(CropInsets()) }) { Text(S.clear) }
        }
    }
}

/** Whether the page is being put on a named sheet or just scaled by a number. */
enum class SizeMode(private val text: () -> String) {
    PAPER({ S.sizePaper }),
    PERCENT({ S.sizePercent }),
    ;

    val label: String get() = text()
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
        title = S.toolResize,
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
                    S.resizePaperHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = S.sheet,
                    options = PaperSize.entries,
                    selected = target.paper,
                    onSelect = { onTargetChange(target.copy(paper = it)) },
                    optionLabel = { it.label },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = S.orientation,
                    options = PageOrientation.entries,
                    selected = target.orientation,
                    onSelect = { onTargetChange(target.copy(orientation = it)) },
                    optionLabel = { it.label },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChips(
                    label = S.whereTheyDoNotMatch,
                    options = listOf(false, true),
                    selected = target.fill,
                    onSelect = { onTargetChange(target.copy(fill = it)) },
                    optionLabel = { if (it) S.fillCropOverflow else S.fitLeaveMargins },
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
                    S.resizePercentHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LabeledSlider(
                    label = S.size,
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
        title = S.toolWatermark,
        subtitle = S.every_page,
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = options.text.isNotBlank(),
        onRemove = if (hasExisting) onRemove else null,
    ) {
        OutlinedTextField(
            value = options.text,
            onValueChange = { onChange(options.copy(text = it)) },
            label = { Text(S.text) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            label = S.placement,
            options = WatermarkLayout.entries,
            selected = options.layout,
            onSelect = { onChange(options.copy(layout = it)) },
            optionLabel = { it.label },
        )
        Spacer(Modifier.height(12.dp))
        LabeledSlider(
            label = S.opacity,
            value = options.opacity,
            onValueChange = { onChange(options.copy(opacity = it)) },
            valueRange = 0.05f..1f,
            valueLabel = "${(options.opacity * 100).roundToInt()}%",
        )
        LabeledSlider(
            label = S.textSize,
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
        title = S.pageNumbers,
        subtitle = S.every_page,
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = options.format.contains("{n}"),
        onRemove = if (hasExisting) onRemove else null,
    ) {
        OutlinedTextField(
            value = options.format,
            onValueChange = { onChange(options.copy(format = it)) },
            label = { Text(S.format) },
            supportingText = { Text(S.formatHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("{n}", "{n} / {total}", S.numberPresetPage).forEach { preset ->
                OutlinedButton(onClick = { onChange(options.copy(format = preset)) }) {
                    Text(preset, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            label = S.position,
            options = NumberPosition.entries,
            selected = options.position,
            onSelect = { onChange(options.copy(position = it)) },
            optionLabel = { it.label },
        )
        Spacer(Modifier.height(12.dp))
        LabeledSlider(
            label = S.startNumberingAt,
            value = options.startNumber.toFloat(),
            onValueChange = { onChange(options.copy(startNumber = it.roundToInt())) },
            valueRange = 0f..50f,
            valueLabel = options.startNumber.toString(),
        )
        LabeledSlider(
            label = S.skipFirstPages,
            value = options.firstPageIndex.toFloat(),
            onValueChange = { onChange(options.copy(firstPageIndex = it.roundToInt())) },
            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
            valueLabel = options.firstPageIndex.toString(),
        )
        LabeledSlider(
            label = S.textSize,
            value = options.fontSize,
            onValueChange = { onChange(options.copy(fontSize = it)) },
            valueRange = 7f..24f,
            valueLabel = "${options.fontSize.roundToInt()} pt",
        )
    }
}

@Composable
fun TextPanel(
    mark: TextMark,
    placed: Boolean,
    existingCount: Int,
    onChange: (TextMark) -> Unit,
    onClearPage: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.addText,
        subtitle = S.pageNumber(mark.sourceIndex + 1),
        applyEnabled = placed && mark.text.isNotBlank(),
        onCancel = onCancel,
        onApply = onApply,
        onRemove = if (existingCount > 0) onClearPage else null,
    ) {
        Text(
            if (placed) S.dragMarker else S.tapToPlace,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = mark.text,
            onValueChange = { onChange(mark.copy(text = it)) },
            label = { Text(S.text) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = S.size,
            value = mark.fontSize,
            onValueChange = { onChange(mark.copy(fontSize = it)) },
            valueRange = 6f..48f,
            valueLabel = "${mark.fontSize.roundToInt()} pt",
        )
        ChoiceChips(
            label = S.colour,
            options = InkColours,
            selected = InkColours.firstOrNull { it.second == mark.color } ?: InkColours.first(),
            onSelect = { onChange(mark.copy(color = it.second)) },
            optionLabel = { it.first },
        )
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            label = S.weight,
            options = listOf(false, true),
            selected = mark.bold,
            onSelect = { onChange(mark.copy(bold = it)) },
            optionLabel = { if (it) S.bold else S.regular },
        )
        if (existingCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                S.alreadyAdded(existingCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FillPanel(
    highlight: Boolean,
    rect: MarkRect?,
    colour: Int,
    pageNumber: Int,
    existingCount: Int,
    onColourChange: (Int) -> Unit,
    onReset: () -> Unit,
    onClearPage: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = if (highlight) S.toolHighlight else S.toolWhiteout,
        subtitle = S.pageNumber(pageNumber),
        applyEnabled = rect != null && !rect.tidied().isDegenerate,
        onCancel = onCancel,
        onApply = onApply,
        onRemove = if (existingCount > 0) onClearPage else null,
    ) {
        Text(
            if (highlight) S.highlightHint else S.whiteoutHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val palette = if (highlight) HighlightColours else WhiteoutColours
        ChoiceChips(
            label = S.colour,
            options = palette,
            selected = palette.firstOrNull { it.second == colour } ?: palette.first(),
            onSelect = { onColourChange(it.second) },
            optionLabel = { it.first },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReset) { Text(S.startAgain) }
        }
    }
}

@Composable
fun InkPanel(
    strokeCount: Int,
    colour: Int,
    widthRatio: Float,
    pageNumber: Int,
    existingCount: Int,
    onColourChange: (Int) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onClearPage: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.toolDraw,
        subtitle = S.pageNumber(pageNumber),
        applyEnabled = strokeCount > 0,
        onCancel = onCancel,
        onApply = onApply,
        onRemove = if (existingCount > 0) onClearPage else null,
    ) {
        Text(
            S.drawHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            label = S.colour,
            options = InkColours,
            selected = InkColours.firstOrNull { it.second == colour } ?: InkColours.first(),
            onSelect = { onColourChange(it.second) },
            optionLabel = { it.first },
        )
        Spacer(Modifier.height(8.dp))
        LabeledSlider(
            label = S.thickness,
            value = widthRatio,
            onValueChange = onWidthChange,
            valueRange = 0.001f..0.02f,
            valueLabel = "${(widthRatio * 1000).roundToInt()}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndoStroke, enabled = strokeCount > 0) {
                Text(S.undoStroke)
            }
            OutlinedButton(onClick = onClearStrokes, enabled = strokeCount > 0) {
                Text(S.clear)
            }
        }
    }
}

/** Small named palettes, so the pickers stay short enough to be useful. */
val InkColours: List<Pair<String, Int>>
    get() = listOf(
        S.colourBlack to 0xFF1A1A1A.toInt(),
        S.colourBlue to 0xFF1B5FC1.toInt(),
        S.colourRed to 0xFFC62828.toInt(),
        S.colourGreen to 0xFF2E7D32.toInt(),
        S.colourWhite to 0xFFFFFFFF.toInt(),
    )

val HighlightColours: List<Pair<String, Int>>
    get() = listOf(
        S.colourYellow to 0xFFFFEB3B.toInt(),
        S.colourGreen to 0xFF8BC34A.toInt(),
        S.colourBlue to 0xFF64B5F6.toInt(),
        S.colourPink to 0xFFF48FB1.toInt(),
    )

val WhiteoutColours: List<Pair<String, Int>>
    get() = listOf(
        S.colourWhite to 0xFFFFFFFF.toInt(),
        S.colourBlack to 0xFF000000.toInt(),
        S.colourGrey to 0xFF9E9E9E.toInt(),
    )

/** The running list of edits, so the user can see and unwind what they have done. */
@Composable
fun PendingEditsDialog(
    descriptions: List<String>,
    onDismiss: () -> Unit,
    onUndoAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.pendingEdits(descriptions.size)) },
        text = {
            if (descriptions.isEmpty()) {
                Text(S.nothingYet, style = MaterialTheme.typography.bodyMedium)
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
                        S.nothingWrittenYet,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.close) } },
        dismissButton = {
            if (descriptions.isNotEmpty()) {
                TextButton(onClick = onUndoAll) { Text(S.undoAll) }
            }
        },
    )
}

private fun pct(value: Float) = "${(value * 100).roundToInt()}%"

/** Where merged-in pages land, relative to the document as it now stands. */
enum class InsertPosition(private val text: () -> String) {
    AFTER({ S.insertAfter }),
    START({ S.insertStart }),
    END({ S.insertEnd }),
    ;

    val label: String get() = text()
}

/**
 * Confirms a merge. The pages do not become special: once inserted they are
 * ordinary pages in the plan, open to every other tool, which is the whole
 * point of merging here instead of in a one-shot tool.
 */
@Composable
fun InsertPanel(
    docs: List<Pair<String, Int>>,
    where: InsertPosition,
    onWhereChange: (InsertPosition) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val total = docs.sumOf { it.second }
    PanelFrame(
        title = S.toolMerge,
        subtitle = S.toInsert(total),
        applyLabel = S.insert,
        applyEnabled = docs.isNotEmpty(),
        onCancel = onCancel,
        onApply = onApply,
    ) {
        docs.forEach { (name, count) ->
            Text(
                S.docPages(name, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        ChoiceChips(
            label = S.where,
            options = InsertPosition.entries,
            selected = where,
            onSelect = onWhereChange,
            optionLabel = { it.label },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            S.insertHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
