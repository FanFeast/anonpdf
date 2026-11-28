package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** What a document says about itself in its properties. Blank means none. */
data class DocProperties(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
)

/** A file kept inside a PDF, by the name it is listed under. */
data class AttachedFile(val name: String, val bytes: Long)

/** A file to put inside the document: listed as [name], read from [file] when saved. */
data class Attachment(val name: String, val file: File)

/**
 * Changes to the files inside a document: some of its own taken out, others
 * put in. A name added that matches one already there replaces it.
 */
data class AttachmentChanges(
    val removed: Set<String> = emptySet(),
    val added: List<Attachment> = emptyList(),
) {
    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()
}

/**
 * A document's properties (title, author, subject, keywords) and the files
 * attached to it: reading them, changing them, and taking the files out.
 */
object PdfDocInfo {

    suspend fun readProperties(file: File, password: String? = null): DocProperties = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { document ->
            val info = document.documentInformation
            DocProperties(
                title = info.title.orEmpty().trim(),
                author = info.author.orEmpty().trim(),
                subject = info.subject.orEmpty().trim(),
                keywords = info.keywords.orEmpty().trim(),
            )
        }
    }

    suspend fun readAttachments(file: File, password: String? = null): List<AttachedFile> = withContext(Dispatchers.IO) {
        PdfOps.load(file, password).use { document ->
            embedded(document).map { (name, spec) -> AttachedFile(name, spec.embeddedFileOf()?.length?.toLong() ?: 0L) }
        }
    }

    /**
     * Writes [properties] into [document]. The XMP packet some writers add
     * repeats the old values, and readers may prefer it, so it goes: the
     * properties are then what every reader shows.
     */
    internal fun applyProperties(document: PDDocument, properties: DocProperties) {
        val info = document.documentInformation
        info.title = properties.title.trim().ifEmpty { null }
        info.author = properties.author.trim().ifEmpty { null }
        info.subject = properties.subject.trim().ifEmpty { null }
        info.keywords = properties.keywords.trim().ifEmpty { null }
        info.modificationDate = Calendar.getInstance()
        document.documentInformation = info
        document.documentCatalog.metadata = null
    }

    internal fun applyAttachments(document: PDDocument, changes: AttachmentChanges) {
        if (changes.isEmpty) return
        val files = embedded(document).toMap(LinkedHashMap())
        changes.removed.forEach { files.remove(it) }
        changes.added.forEach { attachment ->
            val embedded = PDEmbeddedFile(document, attachment.file.inputStream()).apply {
                size = attachment.file.length().toInt()
                creationDate = Calendar.getInstance()
                modDate = Calendar.getInstance()
            }
            files[attachment.name] = PDComplexFileSpecification().apply {
                file = attachment.name
                fileUnicode = attachment.name
                embeddedFile = embedded
                embeddedFileUnicode = embedded
            }
        }
        val names = document.documentCatalog.names ?: PDDocumentNameDictionary(document.documentCatalog)
        if (files.isEmpty()) {
            names.cosObject.removeItem(COSName.EMBEDDED_FILES)
        } else {
            // One flat node: simplest to write, and what readers handle best.
            names.embeddedFiles = PDEmbeddedFilesNameTreeNode().apply { this.names = files }
        }
        document.documentCatalog.names = names
    }

    /** Saves every attached file into [outputDir]; returns them in the order the document lists them. */
    suspend fun extractAttachments(
        input: File,
        outputDir: File,
        password: String? = null,
        onProgress: Progress = {},
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        PdfOps.load(input, password).use { document ->
            val files = embedded(document)
            val used = mutableSetOf<String>()
            files.mapIndexedNotNull { index, (name, spec) ->
                val data = spec.embeddedFileOf() ?: return@mapIndexedNotNull null
                // The name is the sender's: keep only its last part, and make it safe.
                val safe = uniqueName(FileNames.sanitize(name.substringAfterLast('/').substringAfterLast('\\')), used)
                File(outputDir, safe).also { target ->
                    data.createInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    onProgress((index + 1f) / files.size)
                }
            }
        }
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var candidate = name
        var n = 2
        while (!used.add(candidate.lowercase())) candidate = "$stem-${n++}$extension"
        return candidate
    }

    /** Every attached file, by the name the document lists it under, the whole tree walked. */
    private fun embedded(document: PDDocument): List<Pair<String, PDComplexFileSpecification>> {
        val root = document.documentCatalog.names?.embeddedFiles ?: return emptyList()
        val out = mutableListOf<Pair<String, PDComplexFileSpecification>>()
        fun walk(node: PDNameTreeNode<PDComplexFileSpecification>, depth: Int) {
            if (depth > 32) return
            runCatching { node.names }.getOrNull()?.forEach { (name, spec) -> out += name to spec }
            runCatching { node.kids }.getOrNull()?.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out
    }

    private fun PDComplexFileSpecification.embeddedFileOf(): PDEmbeddedFile? =
        embeddedFileUnicode ?: embeddedFile ?: embeddedFileDos ?: embeddedFileMac ?: embeddedFileUnix
}
