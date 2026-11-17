package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.fanfeast.anonpdf.i18n.S

/**
 * The one-shot tools.
 *
 * Anything that changes individual pages — rotate, crop, scale, delete, reorder,
 * watermark, page numbers — lives in the editor instead, where the result can be
 * previewed and several changes combined before saving. What is left here are the
 * jobs that act on whole files, or that produce something other than one PDF.
 */
enum class ToolId {
    MERGE,
    SPLIT,
    EXTRACT,
    COMPRESS,
    PDF_TO_IMAGES,
    IMAGES_TO_PDF,
    EXTRACT_TEXT,
    PROTECT,
    UNLOCK,
    SIGN,
}

/** What the tool needs the user to hand it. */
enum class ToolInput { SINGLE_PDF, MULTIPLE_PDF, IMAGES }

/** What the tool produces, which decides how we offer to save it. */
enum class ToolOutput { PDF, TEXT, MANY_FILES }

enum class ToolGroup(private val text: () -> String) {
    FILES({ S.groupWholeFiles }),
    CONVERT({ S.groupConvertCompress }),
    MARKUP({ S.groupMarkup }),
    SECURITY({ S.groupSecurity }),
    ;

    val label: String get() = text()
}

class ToolSpec(
    val id: ToolId,
    private val titleText: () -> String,
    private val summaryText: () -> String,
    val icon: ImageVector,
    val group: ToolGroup,
    val input: ToolInput,
    val output: ToolOutput,
    /** True when the tool has a bespoke screen instead of the generic run flow. */
    val hasCustomScreen: Boolean = false,
    /** Shown as a caveat before running, when there is an honest trade-off. */
    private val caveatText: (() -> String)? = null,
) {
    // Read on every use, not stored, so a change of language shows at once.
    val title: String get() = titleText()
    val summary: String get() = summaryText()
    val caveat: String? get() = caveatText?.invoke()
}

object ToolCatalog {

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            id = ToolId.MERGE,
            titleText = { S.mergeTitle },
            summaryText = { S.mergeSummary },
            icon = Icons.AutoMirrored.Filled.MergeType,
            group = ToolGroup.FILES,
            input = ToolInput.MULTIPLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.SPLIT,
            titleText = { S.splitTitle },
            summaryText = { S.splitSummary },
            icon = Icons.Filled.ContentCut,
            group = ToolGroup.FILES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.MANY_FILES,
        ),
        ToolSpec(
            id = ToolId.EXTRACT,
            titleText = { S.extractTitle },
            summaryText = { S.extractSummary },
            icon = Icons.Filled.FilterNone,
            group = ToolGroup.FILES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.COMPRESS,
            titleText = { S.compressTitle },
            summaryText = { S.compressSummary },
            icon = Icons.Filled.Compress,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.PDF_TO_IMAGES,
            titleText = { S.toImagesTitle },
            summaryText = { S.toImagesSummary },
            icon = Icons.Filled.Image,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.MANY_FILES,
        ),
        ToolSpec(
            id = ToolId.IMAGES_TO_PDF,
            titleText = { S.fromImagesTitle },
            summaryText = { S.fromImagesSummary },
            icon = Icons.Filled.PictureAsPdf,
            group = ToolGroup.CONVERT,
            input = ToolInput.IMAGES,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.EXTRACT_TEXT,
            titleText = { S.extractTextTitle },
            summaryText = { S.extractTextSummary },
            icon = Icons.AutoMirrored.Filled.Article,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.TEXT,
            caveatText = { S.extractTextCaveat },
        ),
        ToolSpec(
            id = ToolId.SIGN,
            titleText = { S.signTitle },
            summaryText = { S.signSummary },
            icon = Icons.Filled.Draw,
            group = ToolGroup.MARKUP,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
            hasCustomScreen = true,
            caveatText = { S.signCaveat },
        ),
        ToolSpec(
            id = ToolId.PROTECT,
            titleText = { S.protectTitle },
            summaryText = { S.protectSummary },
            icon = Icons.Filled.Lock,
            group = ToolGroup.SECURITY,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.UNLOCK,
            titleText = { S.unlockTitle },
            summaryText = { S.unlockSummary },
            icon = Icons.Filled.LockOpen,
            group = ToolGroup.SECURITY,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun spec(id: ToolId): ToolSpec = byId.getValue(id)

    val grouped: List<Pair<ToolGroup, List<ToolSpec>>> =
        ToolGroup.entries.map { group -> group to all.filter { it.group == group } }
            .filter { it.second.isNotEmpty() }
}
