package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.MarkupMode
import io.github.fanfeast.anonpdf.ui.components.ChoiceChips

/** Where a search for blank pages has got to. */
sealed interface BlankScan {
    data object NotRun : BlankScan
    data object Running : BlankScan

    /** Output page numbers, counting from 1, of the pages that look blank. */
    data class Found(val pageNumbers: List<Int>) : BlankScan
}

/**
 * Whole-document tidying: what happens to comments and markups, and finding the
 * blank pages. The markup choice previews like any edit and lands on Apply;
 * deleting blank pages is its own edit, made at once, so it can be undone alone.
 */
@Composable
fun CleanupPanel(
    markups: MarkupMode,
    /** Markups in the document, or null while they are being counted. */
    markupCount: Int?,
    blankScan: BlankScan,
    onMarkupsChange: (MarkupMode) -> Unit,
    onFindBlank: () -> Unit,
    onDeleteBlank: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(title = S.cleanupTitle, subtitle = null, onCancel = onCancel, onApply = onApply) {
        Text(S.markupsHeading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            when (markupCount) {
                null -> S.redactSearching
                0 -> S.markupsNone
                else -> S.markupsFound(markupCount)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            label = "",
            options = MarkupMode.entries,
            selected = markups,
            onSelect = onMarkupsChange,
            optionLabel = { it.label },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            S.markupsHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Text(S.blankHeading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        when (blankScan) {
            BlankScan.NotRun -> OutlinedButton(onClick = onFindBlank) { Text(S.blankFind) }
            BlankScan.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(S.blankSearching, style = MaterialTheme.typography.bodyMedium)
            }
            is BlankScan.Found -> if (blankScan.pageNumbers.isEmpty()) {
                Text(S.blankNone, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    S.blankFound(blankScan.pageNumbers.size, blankScan.pageNumbers.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDeleteBlank) { Text(S.blankDelete) }
            }
        }
    }
}
