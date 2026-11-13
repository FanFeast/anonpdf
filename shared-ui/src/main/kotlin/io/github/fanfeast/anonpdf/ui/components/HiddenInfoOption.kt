package io.github.fanfeast.anonpdf.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.DocumentStore
import io.github.fanfeast.anonpdf.pdf.HiddenInfo
import io.github.fanfeast.anonpdf.pdf.HiddenInfoCleaner
import io.github.fanfeast.anonpdf.ui.tools.ToolResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The optional "remove hidden info" step on a finished result: what the PDFs
 * carry beyond their pages, and whether to hand out cleaned copies instead.
 *
 * Off by default, because some people want the title and author kept. The
 * cleaned copies are made once, on the first save or share, and reused after.
 */
@Stable
class HiddenInfoChoice internal constructor(private val result: ToolResult) {

    /** What the result's PDFs carry; null until inspected, or if there is nothing to inspect. */
    var found by mutableStateOf<HiddenInfo?>(null)
        internal set

    var remove by mutableStateOf(false)

    private val pdfs: List<File> = when (result) {
        is ToolResult.One -> if (result.isText) emptyList() else listOf(result.file)
        is ToolResult.Many -> if (result.isText) emptyList() else result.files
    }.filter { it.extension.equals("pdf", ignoreCase = true) }

    /** The PDFs that could be read; a locked one (Protect's output) is left as it is. */
    private var readable: Set<File> = emptySet()

    private val lock = Mutex()
    private var cleaned: Map<File, File>? = null

    internal suspend fun inspect() {
        val reports = pdfs.mapNotNull { file -> HiddenInfoCleaner.inspect(file)?.let { file to it } }
        readable = reports.map { it.first }.toSet()
        found = reports.map { it.second }.reduceOrNull(HiddenInfo::plus)
    }

    /**
     * What to save or share in place of [file]: [file] itself, or its cleaned
     * copy when the user asked for one.
     */
    suspend fun fileFor(file: File, store: DocumentStore): File {
        if (!remove || file !in readable) return file
        return cleanedCopies(store)[file] ?: file
    }

    /** [result] with each file swapped for what [fileFor] hands out. */
    suspend fun resolve(store: DocumentStore): ToolResult = when (result) {
        is ToolResult.One -> result.copy(file = fileFor(result.file, store))
        is ToolResult.Many -> result.copy(files = result.files.map { fileFor(it, store) })
    }

    private suspend fun cleanedCopies(store: DocumentStore): Map<File, File> = lock.withLock {
        cleaned ?: run {
            // A folder of their own, so each copy keeps its file's name for
            // sharing without overwriting the file it was made from.
            val dir = store.newOutputDir("cleaned")
            readable.associateWith { file ->
                File(dir, file.name).also { HiddenInfoCleaner.clean(file, it) }
            }
        }.also { cleaned = it }
    }
}

@Composable
fun rememberHiddenInfoChoice(result: ToolResult): HiddenInfoChoice {
    val choice = remember(result) { HiddenInfoChoice(result) }
    LaunchedEffect(choice) { choice.inspect() }
    return choice
}

/** The checkbox and what it would remove. Shows nothing when there is nothing hidden. */
@Composable
fun HiddenInfoOption(choice: HiddenInfoChoice, modifier: Modifier = Modifier) {
    val found = choice.found ?: return
    if (found.isEmpty) return
    Row(
        modifier = modifier.toggleable(
            value = choice.remove,
            role = Role.Checkbox,
            onValueChange = { choice.remove = it },
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = choice.remove, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Text(S.removeHiddenInfo, style = MaterialTheme.typography.titleSmall)
            Text(
                describeHiddenInfo(found).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** One short phrase per kind of hidden information, most identifying first. */
fun describeHiddenInfo(info: HiddenInfo, formatDate: (Long) -> String = ::mediumDate): List<String> =
    buildList {
        info.author?.let { add(S.hiddenAuthor(it)) }
        if (info.commentAuthors.isNotEmpty()) {
            add(S.hiddenCommentAuthors(info.commentAuthors.joinToString(", ")))
        }
        info.creatorApp?.let { add(S.hiddenMadeWith(it)) }
        info.producerApp?.takeIf { it != info.creatorApp }?.let { add(S.hiddenPdfBy(it)) }
        info.created?.let { add(S.hiddenCreated(formatDate(it))) }
        info.modified?.let { add(S.hiddenModified(formatDate(it))) }
        info.title?.let { add(S.hiddenTitle(it)) }
        info.subject?.let { add(S.hiddenSubject(it)) }
        info.keywords?.let { add(S.hiddenKeywords(it)) }
        if (info.customProperties > 0) add(S.hiddenOtherProperties(info.customProperties))
        if (info.attachments > 0) add(S.hiddenAttachments(info.attachments))
        if (info.scripts > 0) add(S.hiddenScripts(info.scripts))
        if (info.hasXmp) add(S.hiddenXmp)
        if (info.hasPrivateData) add(S.hiddenEditorData)
    }

private fun mediumDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
