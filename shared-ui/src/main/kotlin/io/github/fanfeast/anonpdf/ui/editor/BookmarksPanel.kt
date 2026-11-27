package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.Bookmark

/**
 * The document's bookmarks as an editable list: pick one to go to its page,
 * rename it, move it, indent it under the one above, or add one for the page
 * on screen. Nothing changes until Apply, which makes it one undoable edit.
 */
@Composable
fun BookmarksPanel(
    items: List<Bookmark>,
    selected: Int?,
    /** Where a source page sits in the finished document, counting from 1; null if deleted. */
    pageNumberOf: (Int) -> Int?,
    currentSource: Int?,
    hasExisting: Boolean,
    onChange: (items: List<Bookmark>, selected: Int?) -> Unit,
    onOpen: (Bookmark) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.bookmarksTitle,
        subtitle = null,
        onCancel = onCancel,
        onApply = onApply,
        onRemove = if (hasExisting) onRemove else null,
    ) {
        if (items.isEmpty()) {
            Text(S.bookmarksEmpty, style = MaterialTheme.typography.bodyMedium)
        }
        items.forEachIndexed { index, bookmark ->
            val page = pageNumberOf(bookmark.sourceIndex)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (index == selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    )
                    .clickable {
                        onChange(items, index)
                        onOpen(bookmark)
                    }
                    .padding(start = (8 + 16 * bookmark.level).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bookmark.title.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    page?.let(S::bookmarkPage) ?: S.bookmarkPageGone,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (page == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val chosen = selected?.let(items::getOrNull)
        if (chosen != null && selected != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = chosen.title,
                onValueChange = { title ->
                    onChange(items.toMutableList().also { it[selected] = chosen.copy(title = title) }, selected)
                },
                label = { Text(S.bookmarkTitle) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Action(Icons.Filled.ArrowUpward, S.bookmarkUp, enabled = selected > 0) {
                    onChange(tidied(items.swapped(selected, selected - 1)), selected - 1)
                }
                Action(Icons.Filled.ArrowDownward, S.bookmarkDown, enabled = selected < items.lastIndex) {
                    onChange(tidied(items.swapped(selected, selected + 1)), selected + 1)
                }
                Action(Icons.AutoMirrored.Filled.FormatIndentDecrease, S.bookmarkOutdent, enabled = chosen.level > 0) {
                    onChange(tidied(items.withLevel(selected, chosen.level - 1)), selected)
                }
                Action(
                    Icons.AutoMirrored.Filled.FormatIndentIncrease,
                    S.bookmarkIndent,
                    enabled = selected > 0 && chosen.level <= items[selected - 1].level,
                ) {
                    onChange(tidied(items.withLevel(selected, chosen.level + 1)), selected)
                }
                Action(Icons.Filled.Delete, S.bookmarkDelete) {
                    onChange(tidied(items.filterIndexed { index, _ -> index != selected }), null)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val source = currentSource ?: return@OutlinedButton
                val page = pageNumberOf(source) ?: return@OutlinedButton
                // After the chosen one, as its sibling; otherwise where the page falls.
                val at = selected?.plus(1)
                    ?: items.indexOfFirst { (pageNumberOf(it.sourceIndex) ?: Int.MAX_VALUE) > page }
                        .takeIf { it >= 0 }
                    ?: items.size
                val level = selected?.let { items[it].level } ?: 0
                val added = items.toMutableList().apply { add(at, Bookmark(S.bookmarkDefault(page), source, level)) }
                onChange(tidied(added), at)
            },
            enabled = currentSource != null && pageNumberOf(currentSource) != null,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(S.bookmarkAdd)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            S.bookmarksHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label)
    }
}

private fun List<Bookmark>.swapped(a: Int, b: Int) =
    toMutableList().also { list -> list[a] = this[b].also { list[b] = this[a] } }

private fun List<Bookmark>.withLevel(index: Int, level: Int) =
    toMutableList().also { it[index] = this[index].copy(level = level) }

/** Levels a tree can have: the first at the top, none more than one deeper than the one above. */
internal fun tidied(items: List<Bookmark>): List<Bookmark> {
    var previous = -1
    return items.map { bookmark ->
        val level = bookmark.level.coerceIn(0, previous + 1)
        previous = level
        if (level == bookmark.level) bookmark else bookmark.copy(level = level)
    }
}
