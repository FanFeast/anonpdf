package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.launch

/**
 * Everything the editor can do, in the order the pages present them.
 *
 * [handsOff] tools open a separate one-shot screen with the document, rather than
 * changing the pending plan; a platform without those screens leaves them out.
 */
enum class EditorTool(private val text: () -> String, val icon: ImageVector, val handsOff: Boolean = false) {
    ROTATE_LEFT({ S.toolLeft }, Icons.AutoMirrored.Filled.RotateLeft),
    ROTATE_RIGHT({ S.toolRight }, Icons.AutoMirrored.Filled.RotateRight),
    CROP({ S.toolCrop }, Icons.Filled.Crop),
    RESIZE({ S.toolResize }, Icons.Filled.ZoomOutMap),
    DELETE({ S.toolDelete }, Icons.Filled.Delete),
    RESTORE({ S.toolRestore }, Icons.Filled.RestoreFromTrash),
    REVERSE({ S.toolReverse }, Icons.Filled.SwapVert),
    DUPLICATE({ S.toolDuplicate }, Icons.Filled.ContentCopy),
    BLANK_PAGE({ S.toolBlankPage }, Icons.AutoMirrored.Filled.InsertDriveFile),

    STRAIGHTEN({ S.toolStraighten }, Icons.Filled.Straighten),
    GREYSCALE({ S.toolGreyscale }, Icons.Filled.FilterBAndW),

    FILL_FORM({ S.toolFillForm }, Icons.Filled.EditNote),
    TEXT({ S.toolText }, Icons.Filled.TextFields),
    WHITEOUT({ S.toolWhiteout }, Icons.Filled.FormatColorFill),
    REDACT({ S.toolRedact }, Icons.Filled.FindInPage),
    HIGHLIGHT({ S.toolHighlight }, Icons.Filled.Highlight),
    DRAW({ S.toolDraw }, Icons.Filled.Brush),
    WATERMARK({ S.toolWatermark }, Icons.Filled.WaterDrop),
    PAGE_NUMBERS({ S.toolNumbers }, Icons.Filled.Numbers),
    HEADER_FOOTER({ S.toolHeaderFooter }, Icons.AutoMirrored.Filled.ShortText),

    MERGE({ S.toolMerge }, Icons.AutoMirrored.Filled.MergeType),
    BOOKMARKS({ S.toolBookmarks }, Icons.Filled.Bookmarks),
    CLEANUP({ S.toolCleanup }, Icons.Filled.CleaningServices),
    SPLIT({ S.toolSplit }, Icons.Filled.ContentCut, handsOff = true),
    EXTRACT({ S.toolExtract }, Icons.Filled.FilterNone, handsOff = true),
    COMPRESS({ S.toolCompress }, Icons.Filled.Compress, handsOff = true),

    TO_IMAGES({ S.toolToImages }, Icons.Filled.Image, handsOff = true),
    FROM_IMAGES({ S.toolFromImages }, Icons.Filled.PictureAsPdf, handsOff = true),
    EXTRACT_TEXT({ S.toolGetText }, Icons.AutoMirrored.Filled.Article, handsOff = true),
    SIGN({ S.toolSign }, Icons.Filled.Draw, handsOff = true),
    PROTECT({ S.toolProtect }, Icons.Filled.Lock, handsOff = true),
    UNLOCK({ S.toolUnlock }, Icons.Filled.LockOpen, handsOff = true),
    ;

    val label: String get() = text()
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
fun toolPages(
    currentPageDeleted: Boolean,
    includeHandOffs: Boolean = true,
    /** Whether the document has a form, so Fill form is worth a place. */
    hasForm: Boolean = false,
): List<ToolPage> = allToolPages(currentPageDeleted, hasForm)
    .map { page -> if (includeHandOffs) page else page.copy(tools = page.tools.filterNot { it.handsOff }) }
    .filter { it.tools.isNotEmpty() }

private fun allToolPages(currentPageDeleted: Boolean, hasForm: Boolean): List<ToolPage> = listOf(
    ToolPage(
        S.groupPages,
        listOf(
            EditorTool.ROTATE_LEFT,
            EditorTool.ROTATE_RIGHT,
            EditorTool.CROP,
            EditorTool.RESIZE,
            if (currentPageDeleted) EditorTool.RESTORE else EditorTool.DELETE,
            EditorTool.REVERSE,
            EditorTool.DUPLICATE,
            EditorTool.BLANK_PAGE,
        ),
    ),
    ToolPage(
        S.groupContent,
        listOfNotNull(
            EditorTool.FILL_FORM.takeIf { hasForm },
            EditorTool.TEXT,
            EditorTool.WHITEOUT,
            EditorTool.REDACT,
            EditorTool.HIGHLIGHT,
            EditorTool.DRAW,
        ),
    ),
    ToolPage(
        S.groupStamps,
        listOf(
            EditorTool.WATERMARK,
            EditorTool.PAGE_NUMBERS,
            EditorTool.HEADER_FOOTER,
        ),
    ),
    ToolPage(
        S.groupScan,
        listOf(
            EditorTool.STRAIGHTEN,
            EditorTool.GREYSCALE,
        ),
    ),
    ToolPage(
        S.groupWholeFile,
        listOf(
            EditorTool.MERGE,
            EditorTool.BOOKMARKS,
            EditorTool.CLEANUP,
            EditorTool.SPLIT,
            EditorTool.EXTRACT,
            EditorTool.COMPRESS,
        ),
    ),
    ToolPage(
        S.groupConvertLock,
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
fun toolPageCount(includeHandOffs: Boolean = true): Int =
    toolPages(currentPageDeleted = false, includeHandOffs = includeHandOffs).size

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
    val scope = rememberCoroutineScope()
    Column(modifier) {
        // Every group by name, so it is plain what else there is and any of it
        // is one tap away instead of several swipes.
        val tabs = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabs)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pages.forEachIndexed { index, page ->
                val selected = index == pagerState.targetPage
                val tabScroll = remember { mutableIntStateOf(0) }
                // Keep the group being shown in view as the pager is swiped along.
                LaunchedEffect(selected) {
                    if (selected) tabs.animateScrollTo(tabScroll.intValue - 40)
                }
                Text(
                    page.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .onPlaced { tabScroll.intValue = it.positionInParent().x.toInt() }
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        )
                        .selectable(selected = selected, role = Role.Tab) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

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
            ToolGrid(pages[page].tools, highlighted, onPick)
        }
    }
}

/**
 * Every page of tools at once, one titled grid under the next.
 *
 * For a side panel, which has the height to show them all. A pager there would
 * hide most tools behind a swipe, and a mouse cannot swipe.
 */
@Composable
fun ToolList(
    pages: List<ToolPage>,
    highlighted: Set<EditorTool>,
    onPick: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        pages.forEach { page ->
            Text(
                page.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
            )
            ToolGrid(page.tools, highlighted, onPick)
        }
    }
}

@Composable
private fun ToolGrid(tools: List<EditorTool>, highlighted: Set<EditorTool>, onPick: (EditorTool) -> Unit) {
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
