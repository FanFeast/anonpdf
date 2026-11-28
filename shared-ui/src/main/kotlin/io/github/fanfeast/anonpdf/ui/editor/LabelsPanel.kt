package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.LabelRange
import io.github.fanfeast.anonpdf.pdf.LabelStyle
import io.github.fanfeast.anonpdf.pdf.PdfArrange
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips
import io.github.fanfeast.anonpdf.ui.tools.NumberStepper

private val LabelStyle.label: String
    get() = when (this) {
        LabelStyle.DECIMAL -> S.labelStyleDecimal
        LabelStyle.ROMAN_LOWER -> S.labelStyleRomanLower
        LabelStyle.ROMAN_UPPER -> S.labelStyleRomanUpper
        LabelStyle.LETTERS_LOWER -> S.labelStyleLettersLower
        LabelStyle.LETTERS_UPPER -> S.labelStyleLettersUpper
        LabelStyle.NONE -> S.labelStyleNone
    }

/**
 * Page labels as runs: each starts at a page and counts in its own style, with
 * an optional prefix. The first run always starts at page 1. What the reader
 * will show is spelled out underneath, so nobody has to work it out.
 */
@Composable
fun LabelsPanel(
    ranges: List<LabelRange>,
    pageCount: Int,
    hasExisting: Boolean,
    onChange: (List<LabelRange>) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.pageLabelsTitle,
        subtitle = null,
        onCancel = onCancel,
        onApply = onApply,
        onRemove = onRemove.takeIf { hasExisting },
    ) {
        Text(S.pageLabelsHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val sorted = ranges.sortedBy { it.firstPage }
        sorted.forEachIndexed { index, range ->
            fun update(changed: LabelRange) = onChange(sorted.toMutableList().also { it[index] = changed })
            Spacer(Modifier.height(8.dp))
            if (index > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) {
                    Text(S.labelFrom(1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                } else {
                    // Each run starts after the one before it and before the next.
                    val low = sorted[index - 1].firstPage + 2
                    val high = (sorted.getOrNull(index + 1)?.firstPage ?: pageCount)
                    Box(Modifier.weight(1f)) {
                        NumberStepper(
                            label = S.labelStartsOn,
                            value = range.firstPage + 1,
                            onChange = { update(range.copy(firstPage = it.coerceIn(low, high) - 1)) },
                            range = low..high,
                        )
                    }
                    IconButton(onClick = { onChange(sorted - range) }) {
                        Icon(Icons.Filled.Close, contentDescription = S.labelRemoveRange)
                    }
                }
            }
            ChoiceChips(
                label = S.labelStyle,
                options = LabelStyle.entries,
                selected = range.style,
                onSelect = { update(range.copy(style = it)) },
                optionLabel = { it.label },
            )
            OutlinedTextField(
                value = range.prefix,
                onValueChange = { update(range.copy(prefix = it.replace('\n', ' ').take(20))) },
                label = { Text(S.labelPrefix) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (range.style != LabelStyle.NONE) {
                NumberStepper(
                    label = S.labelStart,
                    value = range.start,
                    onChange = { update(range.copy(start = it.coerceAtLeast(1))) },
                    range = 1..9999,
                )
            }
        }
        val last = sorted.lastOrNull()?.firstPage ?: 0
        if (last + 1 < pageCount) {
            TextButton(onClick = { onChange(sorted + LabelRange(firstPage = last + 1)) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(S.labelAddRange, modifier = Modifier.padding(start = 8.dp))
            }
        }
        val shown = PdfArrange.labels(sorted, pageCount)
        Text(
            S.labelsPreview(
                shown.take(12).joinToString(", ") { it.ifEmpty { "–" } } + if (shown.size > 12) " …" else "",
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
