package io.github.fanfeast.anonpdf.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fanfeast.anonpdf.pdf.ImageFormat
import io.github.fanfeast.anonpdf.pdf.NumberPosition
import io.github.fanfeast.anonpdf.pdf.PdfPageSizePreset
import io.github.fanfeast.anonpdf.pdf.WatermarkLayout
import java.io.File

/** A document the user handed to a tool, plus what we learned by opening it. */
data class InputDoc(
    val uri: android.net.Uri,
    val name: String,
    val sizeBytes: Long,
    val localFile: File? = null,
    val pageCount: Int = 0,
    val password: String? = null,
    val needsPassword: Boolean = false,
    val error: String? = null,
) {
    val isReady: Boolean get() = localFile != null && !needsPassword && error == null
}

sealed interface ToolResult {
    val note: String?

    data class One(
        val file: File,
        val suggestedName: String,
        val isText: Boolean = false,
        override val note: String? = null,
    ) : ToolResult

    data class Many(
        val files: List<File>,
        val isText: Boolean = false,
        override val note: String? = null,
    ) : ToolResult
}

enum class CompressMode(val label: String, val explanation: String) {
    SMART(
        "Keep text",
        "Re-encodes the pictures inside the PDF and leaves text and vector " +
            "artwork alone. The document stays selectable and searchable.",
    ),
    RASTERIZE(
        "Smallest file",
        "Redraws every page as a flat image. This gives the biggest saving, but " +
            "text stops being selectable or searchable afterwards.",
    ),
}

enum class CompressStrength(val label: String, val quality: Float, val maxEdge: Int, val dpi: Int) {
    LIGHT("Light", 0.85f, 2400, 200),
    BALANCED("Balanced", 0.70f, 1600, 150),
    STRONG("Strong", 0.50f, 1100, 110),
}

enum class SplitMode(val label: String) {
    RANGES("By page ranges"),
    EVERY("Every N pages"),
}

enum class CropPreset(val label: String, val inset: Float) {
    NONE("None", 0f),
    SLIM("Slim", 0.03f),
    MEDIUM("Medium", 0.06f),
    WIDE("Wide", 0.10f),
}

/**
 * Every knob any tool can expose, held in one place.
 *
 * One flat bag rather than a per-tool hierarchy: the tools share most of these
 * (quality, page range, position) and a sealed hierarchy would cost more
 * ceremony than it saves at this size.
 */
class ToolOptionsState {
    var pageRange by mutableStateOf("")

    var splitMode by mutableStateOf(SplitMode.RANGES)
    var splitEvery by mutableStateOf(1)

    var rotation by mutableStateOf(90)

    var cropPreset by mutableStateOf(CropPreset.MEDIUM)
    var cropLeft by mutableStateOf(0.06f)
    var cropTop by mutableStateOf(0.06f)
    var cropRight by mutableStateOf(0.06f)
    var cropBottom by mutableStateOf(0.06f)
    var cropPerEdge by mutableStateOf(false)

    var compressMode by mutableStateOf(CompressMode.SMART)
    var compressStrength by mutableStateOf(CompressStrength.BALANCED)

    var exportFormat by mutableStateOf(ImageFormat.JPEG)
    var exportDpi by mutableStateOf(150)
    var exportQuality by mutableStateOf(90)

    var imagesPageSize by mutableStateOf(PdfPageSizePreset.FIT_IMAGE)
    var imagesMargin by mutableStateOf(0f)

    var watermarkText by mutableStateOf("")
    var watermarkLayout by mutableStateOf(WatermarkLayout.DIAGONAL)
    var watermarkOpacity by mutableStateOf(0.22f)
    var watermarkFontSize by mutableStateOf(52f)

    var numberFormat by mutableStateOf("{n}")
    var numberPosition by mutableStateOf(NumberPosition.BOTTOM_CENTER)
    var numberStart by mutableStateOf(1)
    var numberSkip by mutableStateOf(0)
    var numberFontSize by mutableStateOf(11f)

    var protectPassword by mutableStateOf("")
    var protectConfirm by mutableStateOf("")
    var allowPrinting by mutableStateOf(true)
    var allowCopying by mutableStateOf(false)
    var allowModifying by mutableStateOf(false)

    var unlockPassword by mutableStateOf("")
}
