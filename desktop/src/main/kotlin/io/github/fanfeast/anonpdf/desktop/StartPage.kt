package io.github.fanfeast.anonpdf.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.ui.common.choosePdfs
import io.github.fanfeast.anonpdf.ui.components.InfoNote
import io.github.fanfeast.anonpdf.ui.components.readableWidth
import io.github.fanfeast.anonpdf.ui.tools.ToolCatalog
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolSpec
import java.io.File

/** What the window shows first: the editor up front, and every tool laid out below it. */
@Composable
internal fun StartPage(
    onEdit: (File) -> Unit,
    onOpenTool: (ToolId) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth(maxWidth = 960.dp, gutter = 32.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("What would you like to do?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Edit a PDF", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Rotate, reorder, delete or crop pages, add pages from other PDFs, " +
                            "fill, highlight, draw, add text, a watermark or page numbers. " +
                            "See every change before you save a new copy.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { choosePdfs(multiple = false).firstOrNull()?.let(onEdit) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open a PDF…")
                }
            }
        }

        ToolCatalog.grouped.forEach { (group, tools) ->
            Spacer(Modifier.height(24.dp))
            Text(
                group.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ToolGrid(tools, onOpenTool)
        }

        Spacer(Modifier.height(28.dp))
        InfoNote(
            "AnonPDF works on this computer only. A file you open is copied into a " +
                "private scratch folder that only you can read, and that folder is " +
                "deleted when the app closes. Nothing is uploaded, and nothing is " +
                "written back to your files until you choose where to save.",
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToolGrid(tools: List<ToolSpec>, onOpenTool: (ToolId) -> Unit) {
    BoxWithConstraints {
        val columns = (maxWidth / TOOL_CARD_MIN_WIDTH).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tools.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        ToolCard(tool, onClick = { onOpenTool(tool.id) }, modifier = Modifier.weight(1f))
                    }
                    // Keep a short last row's cards the same width as the rest.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolSpec, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tool.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    tool.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

private val TOOL_CARD_MIN_WIDTH = 260.dp
