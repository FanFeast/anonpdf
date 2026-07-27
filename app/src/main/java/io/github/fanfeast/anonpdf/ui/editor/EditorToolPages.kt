package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Everything the editor can do, in the order the pages present them. */
enum class EditorTool(val label: String, val icon: ImageVector) {
    ROTATE_LEFT("Left", Icons.AutoMirrored.Filled.RotateLeft),
    ROTATE_RIGHT("Right", Icons.AutoMirrored.Filled.RotateRight),
    CROP("Crop", Icons.Filled.Crop),
    RESIZE("Resize", Icons.Filled.ZoomOutMap),
    DELETE("Delete", Icons.Filled.Delete),
    RESTORE("Restore", Icons.Filled.RestoreFromTrash),
    REVERSE("Reverse", Icons.Filled.SwapVert),

    TEXT("Text", Icons.Filled.TextFields),
    WHITEOUT("White-out", Icons.Filled.FormatColorFill),
    HIGHLIGHT("Highlight", Icons.Filled.Highlight),
    DRAW("Draw", Icons.Filled.Brush),
    WATERMARK("Watermark", Icons.Filled.WaterDrop),
    PAGE_NUMBERS("Numbers", Icons.Filled.Numbers),

    MERGE("Merge", Icons.AutoMirrored.Filled.MergeType),
    SPLIT("Split", Icons.Filled.ContentCut),
    EXTRACT("Extract", Icons.Filled.FilterNone),
    COMPRESS("Compress", Icons.Filled.Compress),

    TO_IMAGES("To images", Icons.Filled.Image),
    FROM_IMAGES("From images", Icons.Filled.PictureAsPdf),
    EXTRACT_TEXT("Get text", Icons.AutoMirrored.Filled.Article),
    SIGN("Sign", Icons.Filled.Draw),
    PROTECT("Protect", Icons.Filled.Lock),
    UNLOCK("Unlock", Icons.Filled.LockOpen),
}

/**
 * A swipeable page of tools.
 *
 * Grouped by what they do rather than by how they are implemented — from the
 * user's side, adding a watermark and compressing a file are both just edits.
 */
data class ToolPage(val title: String, val tools: List<EditorTool>)

/**
 * Every tool is reachable here. The page ones change the pending plan; the file
 * ones act on the document with those pending changes already applied, so
 * "compress" after "delete three pages" means what it looks like it means.
 */
fun toolPages(currentPageDeleted: Boolean): List<ToolPage> = listOf(
    ToolPage(
        "Pages",
        listOf(
            EditorTool.ROTATE_LEFT,
            EditorTool.ROTATE_RIGHT,
            EditorTool.CROP,
            EditorTool.RESIZE,
            if (currentPageDeleted) EditorTool.RESTORE else EditorTool.DELETE,
            EditorTool.REVERSE,
        ),
    ),
    ToolPage(
        "Content",
        listOf(
            EditorTool.TEXT,
            EditorTool.WHITEOUT,
            EditorTool.HIGHLIGHT,
            EditorTool.DRAW,
            EditorTool.WATERMARK,
            EditorTool.PAGE_NUMBERS,
        ),
    ),
    ToolPage(
        "Whole file",
        listOf(
            EditorTool.MERGE,
            EditorTool.SPLIT,
            EditorTool.EXTRACT,
            EditorTool.COMPRESS,
        ),
    ),
    ToolPage(
        "Convert & lock",
        listOf(
            EditorTool.TO_IMAGES,
            EditorTool.FROM_IMAGES,
            EditorTool.EXTRACT_TEXT,
            EditorTool.SIGN,
            EditorTool.PROTECT,
            EditorTool.UNLOCK,
        ),
    ),
)

/** How many pages [toolPages] returns, for hoisting the pager's state. */
const val TOOL_PAGE_COUNT = 4

/** Two rows of [ToolCell]s: icon + label + padding, twice. */
private val TOOL_GRID_HEIGHT = 136.dp

@Composable
fun ToolPager(
    pages: List<ToolPage>,
    pagerState: PagerState,
    highlighted: Set<EditorTool>,
    onPick: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pages[pagerState.currentPage].title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // Dots, so it is obvious there is more than one page to swipe through.
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                CircleShape,
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        // Every page gets the same height, whether it holds one row of tools or
        // two. Without this, swiping from the one short page onto a taller one
        // resizes the pager mid-drag, which cancels the settle and bounces the
        // swipe back where it started.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(TOOL_GRID_HEIGHT),
        ) { page ->
            val tools = pages[page].tools
            Column {
                tools.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        row.forEach { tool ->
                            ToolCell(
                                tool = tool,
                                highlighted = tool in highlighted,
                                onClick = { onPick(tool) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep a short row left-aligned instead of stretched out.
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCell(
    tool: EditorTool,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Icon(
            tool.icon,
            contentDescription = tool.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tool.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
