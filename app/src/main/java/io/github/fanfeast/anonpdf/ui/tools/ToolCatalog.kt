package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolId {
    MERGE,
    SPLIT,
    EXTRACT,
    ORGANIZE,
    ROTATE,
    CROP,
    COMPRESS,
    PDF_TO_IMAGES,
    IMAGES_TO_PDF,
    WATERMARK,
    PAGE_NUMBERS,
    PROTECT,
    UNLOCK,
    EXTRACT_TEXT,
    SIGN,
}

/** What the tool needs the user to hand it. */
enum class ToolInput { SINGLE_PDF, MULTIPLE_PDF, IMAGES }

/** What the tool produces, which decides how we offer to save it. */
enum class ToolOutput { PDF, TEXT, MANY_FILES }

enum class ToolGroup(val label: String) {
    PAGES("Pages"),
    CONVERT("Convert & compress"),
    MARKUP("Markup"),
    SECURITY("Security"),
}

data class ToolSpec(
    val id: ToolId,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val group: ToolGroup,
    val input: ToolInput,
    val output: ToolOutput,
    /** True when the tool has a bespoke screen instead of the generic run flow. */
    val hasCustomScreen: Boolean = false,
    /** Shown as a caveat before running, when there is an honest trade-off. */
    val caveat: String? = null,
)

object ToolCatalog {

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            id = ToolId.MERGE,
            title = "Merge PDFs",
            summary = "Join several files into one, in the order you pick them",
            icon = Icons.Filled.MergeType,
            group = ToolGroup.PAGES,
            input = ToolInput.MULTIPLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.SPLIT,
            title = "Split PDF",
            summary = "Break one document into several files by page range",
            icon = Icons.Filled.ContentCut,
            group = ToolGroup.PAGES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.MANY_FILES,
        ),
        ToolSpec(
            id = ToolId.EXTRACT,
            title = "Extract pages",
            summary = "Pull chosen pages out into a single new PDF",
            icon = Icons.Filled.FilterNone,
            group = ToolGroup.PAGES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.ORGANIZE,
            title = "Organise pages",
            summary = "Reorder, rotate and delete pages on a thumbnail grid",
            icon = Icons.Filled.Reorder,
            group = ToolGroup.PAGES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
            hasCustomScreen = true,
        ),
        ToolSpec(
            id = ToolId.ROTATE,
            title = "Rotate PDF",
            summary = "Turn every page at once",
            icon = Icons.Filled.Rotate90DegreesCw,
            group = ToolGroup.PAGES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.CROP,
            title = "Crop margins",
            summary = "Trim whitespace off the edges of every page",
            icon = Icons.Filled.Crop,
            group = ToolGroup.PAGES,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.COMPRESS,
            title = "Compress PDF",
            summary = "Make the file smaller by re-encoding its images",
            icon = Icons.Filled.Compress,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.PDF_TO_IMAGES,
            title = "PDF to images",
            summary = "Save every page as a JPG or PNG",
            icon = Icons.Filled.Image,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.MANY_FILES,
        ),
        ToolSpec(
            id = ToolId.IMAGES_TO_PDF,
            title = "Images to PDF",
            summary = "Build a PDF from photos or scans",
            icon = Icons.Filled.PictureAsPdf,
            group = ToolGroup.CONVERT,
            input = ToolInput.IMAGES,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.EXTRACT_TEXT,
            title = "Extract text",
            summary = "Pull the text out into a plain .txt file",
            icon = Icons.Filled.Article,
            group = ToolGroup.CONVERT,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.TEXT,
            caveat = "Only finds real text. Scanned pages are pictures, so they " +
                "come out empty — reading them would need OCR, which AnonPDF does not do.",
        ),
        ToolSpec(
            id = ToolId.WATERMARK,
            title = "Add watermark",
            summary = "Stamp text across every page",
            icon = Icons.Filled.WaterDrop,
            group = ToolGroup.MARKUP,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.PAGE_NUMBERS,
            title = "Add page numbers",
            summary = "Number the pages, skipping a cover if you like",
            icon = Icons.Filled.Numbers,
            group = ToolGroup.MARKUP,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.SIGN,
            title = "Sign PDF",
            summary = "Draw your signature and place it on a page",
            icon = Icons.Filled.Draw,
            group = ToolGroup.MARKUP,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
            hasCustomScreen = true,
            caveat = "This draws your signature onto the page as an image. " +
                "It is not a cryptographic digital signature.",
        ),
        ToolSpec(
            id = ToolId.PROTECT,
            title = "Protect PDF",
            summary = "Add a password, with AES-256 encryption",
            icon = Icons.Filled.Lock,
            group = ToolGroup.SECURITY,
            input = ToolInput.SINGLE_PDF,
            output = ToolOutput.PDF,
        ),
        ToolSpec(
            id = ToolId.UNLOCK,
            title = "Remove password",
            summary = "Strip protection from a PDF you know the password to",
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
}
