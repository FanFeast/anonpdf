package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.FieldKind
import io.github.fanfeast.anonpdf.pdf.FormField
import io.github.fanfeast.anonpdf.pdf.FormInfo
import io.github.fanfeast.anonpdf.pdf.MarkRect
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips

/** More choices than this get a dropdown instead of a row of chips. */
private const val MAX_CHIPS = 5

/**
 * Fills in the fields on the page being shown.
 *
 * One page at a time, matching the preview above it: the user reads the page
 * and answers what is on it, then moves on. Every answer is previewed as it
 * will be saved, and Apply records them all as a single, undoable edit.
 */
@Composable
fun FormPanel(
    info: FormInfo?,
    /** The fields with a box on the page being shown. */
    pageFields: List<FormField>,
    pageNumber: Int,
    /** Pages before and after this one that have fields, as page numbers to show and jump to. */
    previousPage: Int?,
    nextPage: Int?,
    valueOf: (FormField) -> String,
    flatten: Boolean,
    hasChanges: Boolean,
    onValue: (FormField, String) -> Unit,
    onFocus: (FormField) -> Unit,
    onFlattenChange: (Boolean) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.toolFillForm,
        subtitle = S.pageNumber(pageNumber),
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = hasChanges,
    ) {
        when {
            info == null -> Hint(S.readingForm)
            info.xfaOnly -> Hint(S.xfaOnly)
            pageFields.isEmpty() -> Hint(S.noFieldsHere)
            else -> pageFields.forEach { field ->
                FieldInput(
                    field = field,
                    value = valueOf(field),
                    onValue = { onValue(field, it) },
                    onFocus = { onFocus(field) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (previousPage != null || nextPage != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previousPage != null) {
                    OutlinedButton(onClick = onPreviousPage) { Text(S.fieldsOnPage(previousPage)) }
                }
                if (nextPage != null) {
                    OutlinedButton(onClick = onNextPage) { Text(S.fieldsOnPage(nextPage)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (info?.isEmpty == false) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.toggleable(
                    value = flatten,
                    role = Role.Checkbox,
                    onValueChange = onFlattenChange,
                ),
            ) {
                Checkbox(checked = flatten, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.padding(top = 12.dp)) {
                    Text(S.lockAnswers, style = MaterialTheme.typography.bodyMedium)
                    Hint(S.lockAnswersHint)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FieldInput(
    field: FormField,
    value: String,
    onValue: (String) -> Unit,
    onFocus: () -> Unit,
) {
    val focusTracking = Modifier.onFocusChanged { if (it.isFocused) onFocus() }
    when (field.kind) {
        FieldKind.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                val limit = field.maxLength
                onValue(if (limit != null) typed.take(limit) else typed)
            },
            label = { Text(field.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = !field.multiline,
            minLines = if (field.multiline) 2 else 1,
            enabled = !field.readOnly,
            supportingText = field.maxLength?.let { limit -> { Text("${value.length} / $limit") } },
            modifier = Modifier.fillMaxWidth().then(focusTracking),
        )

        FieldKind.CHECKBOX -> {
            val on = field.options.firstOrNull() ?: "Yes"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(focusTracking)
                    .toggleable(
                        value = value != FormField.OFF,
                        enabled = !field.readOnly,
                        role = Role.Checkbox,
                        onValueChange = {
                            onFocus()
                            onValue(if (it) on else FormField.OFF)
                        },
                    ),
            ) {
                Checkbox(checked = value != FormField.OFF, onCheckedChange = null, enabled = !field.readOnly)
                Spacer(Modifier.padding(start = 8.dp))
                Text(field.label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        FieldKind.RADIO -> ChoiceChips(
            label = field.label,
            // A radio group can be left unanswered, so "None" stays a choice.
            options = listOf(FormField.OFF) + field.options,
            selected = value.takeIf { it in field.options } ?: FormField.OFF,
            onSelect = {
                onFocus()
                if (!field.readOnly) onValue(it)
            },
            optionLabel = { if (it == FormField.OFF) S.none else it },
        )

        FieldKind.CHOICE -> {
            fun labelOf(option: String): String =
                field.optionLabels.getOrNull(field.options.indexOf(option)) ?: option
            if (field.options.size <= MAX_CHIPS) {
                ChoiceChips(
                    label = field.label,
                    options = listOf("") + field.options,
                    selected = value.takeIf { it in field.options } ?: "",
                    onSelect = {
                        onFocus()
                        if (!field.readOnly) onValue(it)
                    },
                    optionLabel = { if (it.isEmpty()) S.none else labelOf(it) },
                )
            } else {
                var open by remember { mutableStateOf(false) }
                Column {
                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    Box {
                        OutlinedButton(
                            onClick = {
                                onFocus()
                                open = true
                            },
                            enabled = !field.readOnly,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (value.isEmpty()) S.choose else labelOf(value),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            field.options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(labelOf(option)) },
                                    onClick = {
                                        onValue(option)
                                        open = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Outlines the fields on the page so the user can see which box each input
 * fills, with the one being answered drawn stronger.
 */
@Composable
internal fun FieldBoxesOverlay(
    boxes: List<MarkRect>,
    focused: List<MarkRect>,
    modifier: Modifier = Modifier,
) {
    val colour = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        fun draw(rect: MarkRect, strong: Boolean) {
            val topLeft = Offset(rect.left * size.width, rect.top * size.height)
            val box = Size(rect.width * size.width, rect.height * size.height)
            drawRect(colour.copy(alpha = if (strong) 0.22f else 0.08f), topLeft, box)
            drawRect(
                colour.copy(alpha = if (strong) 1f else 0.45f),
                topLeft,
                box,
                style = Stroke(width = if (strong) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
        boxes.forEach { draw(it, strong = false) }
        focused.forEach { draw(it, strong = true) }
    }
}
