package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.NewFieldKind
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips

private val NewFieldKind.label: String
    get() = when (this) {
        NewFieldKind.TEXT -> S.fieldKindText
        NewFieldKind.MULTILINE -> S.fieldKindMultiline
        NewFieldKind.CHECKBOX -> S.fieldKindCheckbox
    }

/** A new form field: what kind, what it is called, and, dragged on the page, where. */
@Composable
fun FieldPanel(
    kind: NewFieldKind,
    name: String,
    placed: Boolean,
    onChange: (NewFieldKind, String) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.addFieldTitle,
        subtitle = null,
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = placed && name.isNotBlank(),
    ) {
        ChoiceChips(
            label = "",
            options = NewFieldKind.entries,
            selected = kind,
            onSelect = { onChange(it, name) },
            optionLabel = { it.label },
        )
        OutlinedTextField(
            value = name,
            onValueChange = { onChange(kind, it.replace('\n', ' ').take(120)) },
            label = { Text(S.fieldName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(S.fieldDrawHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
