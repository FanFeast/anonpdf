package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
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
import io.github.fanfeast.anonpdf.pdf.AttachedFile
import io.github.fanfeast.anonpdf.pdf.Attachment
import io.github.fanfeast.anonpdf.pdf.AttachmentChanges
import io.github.fanfeast.anonpdf.pdf.DocProperties
import io.github.fanfeast.anonpdf.ui.components.formatBytes

/**
 * The document's title, author, subject and keywords, and the files attached to
 * it: the document's own, less any taken out, plus any added.
 */
@Composable
fun PropertiesPanel(
    properties: DocProperties,
    own: List<AttachedFile>,
    changes: AttachmentChanges,
    onPropertiesChange: (DocProperties) -> Unit,
    onChangesChange: (AttachmentChanges) -> Unit,
    onAttach: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(title = S.propertiesTitle, subtitle = null, onCancel = onCancel, onApply = onApply) {
        listOf(
            S.propTitle to properties.title,
            S.propAuthor to properties.author,
            S.propSubject to properties.subject,
            S.propKeywords to properties.keywords,
        ).forEachIndexed { index, (label, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { text ->
                    val clean = text.replace('\n', ' ').take(500)
                    onPropertiesChange(
                        when (index) {
                            0 -> properties.copy(title = clean)
                            1 -> properties.copy(author = clean)
                            2 -> properties.copy(subject = clean)
                            else -> properties.copy(keywords = clean)
                        },
                    )
                },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            S.propertiesHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(14.dp))
        Text(S.attachmentsLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val kept = own.filter { it.name !in changes.removed && changes.added.none { added -> added.name == it.name } }
        if (kept.isEmpty() && changes.added.isEmpty()) {
            Text(S.attachmentsNone, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
        }
        kept.forEach { file ->
            AttachmentRow(file.name, formatBytes(file.bytes)) {
                onChangesChange(changes.copy(removed = changes.removed + file.name))
            }
        }
        changes.added.forEach { file ->
            AttachmentRow(file.name, "${formatBytes(file.file.length())} · ${S.attachmentNew}") {
                onChangesChange(changes.copy(added = changes.added - file))
            }
        }
        TextButton(onClick = onAttach) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Text(S.attachFile, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AttachmentRow(name: String, detail: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$name · $detail",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = S.removeAttachment(name)) }
    }
}

/** A file just picked, to be added as an attachment under its own name. */
internal fun Attachment.replacing(list: List<Attachment>) = list.filterNot { it.name == name } + this
