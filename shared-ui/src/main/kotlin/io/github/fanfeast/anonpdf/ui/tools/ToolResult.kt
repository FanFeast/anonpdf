package io.github.fanfeast.anonpdf.ui.tools

import java.io.File

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
