package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.IdentityHashMap

/**
 * What a PDF carries besides its pages: who wrote it, what made it and when,
 * files tucked inside it, and scripts that run when it is opened.
 *
 * None of it shows on the page, which is the point: people send a PDF without
 * knowing it names them, or that it still has the spreadsheet it came from
 * attached. [HiddenInfoCleaner.inspect] reports it and [HiddenInfoCleaner.clean]
 * removes it.
 */
data class HiddenInfo(
    val author: String? = null,
    val title: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    /** The application the document was written in, e.g. a word processor. */
    val creatorApp: String? = null,
    /** The library that produced the PDF itself. */
    val producerApp: String? = null,
    /** Milliseconds since the epoch. */
    val created: Long? = null,
    val modified: Long? = null,
    /** Document-information entries beyond the standard ones. */
    val customProperties: Int = 0,
    /** An XMP metadata packet, which usually repeats the above and often adds more. */
    val hasXmp: Boolean = false,
    val attachments: Int = 0,
    /** Scripts and automatic actions: JavaScript, launching programs, submitting forms. */
    val scripts: Int = 0,
    /** Names on comments and sticky notes. */
    val commentAuthors: Set<String> = emptySet(),
    /** Editors' private data and page thumbnails: rarely readable, never needed. */
    val hasPrivateData: Boolean = false,
) {
    val isEmpty: Boolean
        get() = this == HiddenInfo()

    /** Combines what several files carry, for a result made of many. */
    operator fun plus(other: HiddenInfo) = HiddenInfo(
        author = author ?: other.author,
        title = title ?: other.title,
        subject = subject ?: other.subject,
        keywords = keywords ?: other.keywords,
        creatorApp = creatorApp ?: other.creatorApp,
        producerApp = producerApp ?: other.producerApp,
        created = created ?: other.created,
        modified = modified ?: other.modified,
        customProperties = customProperties + other.customProperties,
        hasXmp = hasXmp || other.hasXmp,
        attachments = attachments + other.attachments,
        scripts = scripts + other.scripts,
        commentAuthors = commentAuthors + other.commentAuthors,
        hasPrivateData = hasPrivateData || other.hasPrivateData,
    )
}

object HiddenInfoCleaner {

    private val STANDARD_INFO_KEYS = setOf(
        "Title", "Author", "Subject", "Keywords", "Creator", "Producer",
        "CreationDate", "ModDate", "Trapped",
    )

    /** Actions that do something other than move around the document. */
    private val ACTIVE_ACTIONS = setOf("JavaScript", "Launch", "SubmitForm", "ImportData")

    /** Annotations whose /T is a field name or nothing at all, not an author. */
    private val UNAUTHORED_ANNOTATIONS = setOf("Widget", "Link", "Popup")

    private val EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles")
    private val JAVA_SCRIPT = COSName.getPDFName("JavaScript")
    private val PIECE_INFO = COSName.getPDFName("PieceInfo")
    private val SPIDER_INFO = COSName.getPDFName("SpiderInfo")
    private val THUMB = COSName.getPDFName("Thumb")
    private val LAST_MODIFIED = COSName.getPDFName("LastModified")
    private val FILE_ATTACHMENT = "FileAttachment"

    /**
     * Reports what [file] carries, or null if it cannot be read without a
     * password: a protected result is left as it is.
     */
    suspend fun inspect(file: File): HiddenInfo? = withContext(Dispatchers.IO) {
        val document = runCatching { PdfOps.load(file) }.getOrNull() ?: return@withContext null
        document.use { walk(it, remove = false) }
    }

    /** Writes [input] to [output] without any of what [inspect] reports. */
    suspend fun clean(input: File, output: File, password: String? = null) =
        withContext(Dispatchers.IO) {
            PdfOps.load(input, password).use { document ->
                walk(document, remove = true)
                with(PdfOps) { document.prepareForSave() }
                // A full save writes only the current objects, so earlier
                // revisions appended to the file go too.
                document.save(output)
            }
        }

    /**
     * Visits every place hidden information lives, recording what it finds and,
     * when [remove] is set, taking it out as it goes, so the report and the
     * cleaning can never disagree about what counts.
     */
    private fun walk(document: PDDocument, remove: Boolean): HiddenInfo {
        val catalog = document.documentCatalog.cosObject
        val trailer = document.document.trailer
        val info = document.documentInformation

        var scripts = 0
        var attachments = 0
        val commentAuthors = linkedSetOf<String>()
        var privateData = false

        val customProperties = info.metadataKeys.count { it !in STANDARD_INFO_KEYS }
        val hasXmp = catalog.containsKey(COSName.METADATA) ||
            document.pages.any { it.cosObject.containsKey(COSName.METADATA) }
        val found = HiddenInfo(
            author = info.author.nonBlank(),
            title = info.title.nonBlank(),
            subject = info.subject.nonBlank(),
            keywords = info.keywords.nonBlank(),
            creatorApp = info.creator.nonBlank(),
            producerApp = info.producer.nonBlank(),
            created = info.creationDate?.timeInMillis,
            modified = info.modificationDate?.timeInMillis,
            customProperties = customProperties,
            hasXmp = hasXmp,
        )
        if (remove) {
            document.documentInformation = PDDocumentInformation()
            catalog.removeItem(COSName.METADATA)
            // The file identifier is meant to stay the same across versions of a
            // document, so it links this copy to the one it came from. A new one
            // is written on save.
            trailer.removeItem(COSName.ID)
        }

        // Document level: names trees, open action, triggers, private data.
        (catalog.getDictionaryObject(COSName.NAMES) as? COSDictionary)?.let { names ->
            (names.getDictionaryObject(EMBEDDED_FILES) as? COSDictionary)?.let {
                attachments += countNameTree(it)
                if (remove) names.removeItem(EMBEDDED_FILES)
            }
            (names.getDictionaryObject(JAVA_SCRIPT) as? COSDictionary)?.let {
                scripts += countNameTree(it)
                if (remove) names.removeItem(JAVA_SCRIPT)
            }
        }
        if (isActive(catalog.getDictionaryObject(COSName.OPEN_ACTION))) {
            scripts++
            if (remove) catalog.removeItem(COSName.OPEN_ACTION)
        }
        scripts += triggers(catalog, remove)
        for (key in listOf(PIECE_INFO, SPIDER_INFO)) {
            if (catalog.containsKey(key)) {
                privateData = true
                if (remove) catalog.removeItem(key)
            }
        }

        // Pages and what sits on them.
        for (page in document.pages) {
            val dict = page.cosObject
            scripts += triggers(dict, remove)
            for (key in listOf(PIECE_INFO, THUMB, LAST_MODIFIED)) {
                if (dict.containsKey(key)) {
                    privateData = true
                    if (remove) dict.removeItem(key)
                }
            }
            if (remove) dict.removeItem(COSName.METADATA)

            val annots = dict.getDictionaryObject(COSName.ANNOTS) as? COSArray ?: continue
            val dropped = mutableListOf<COSBase>()
            for (i in 0 until annots.size()) {
                val annot = annots.getObject(i) as? COSDictionary ?: continue
                val subtype = annot.getNameAsString(COSName.SUBTYPE)
                if (subtype == FILE_ATTACHMENT) {
                    attachments++
                    dropped += annots.get(i)
                    continue
                }
                scripts += triggers(annot, remove)
                if (isActive(annot.getDictionaryObject(COSName.A))) {
                    scripts++
                    if (remove) annot.removeItem(COSName.A)
                }
                if (subtype !in UNAUTHORED_ANNOTATIONS) {
                    annot.getString(COSName.T).nonBlank()?.let { commentAuthors += it }
                    if (remove) {
                        annot.removeItem(COSName.T)
                        annot.removeItem(COSName.M)
                        annot.removeItem(COSName.CREATION_DATE)
                    }
                }
            }
            if (remove) dropped.forEach { annots.remove(it) }
        }

        // Form fields carry their own triggers: format, keystroke and calculate
        // scripts. Their values are already in the file, so nothing is lost.
        val acroForm = catalog.getDictionaryObject(COSName.ACRO_FORM) as? COSDictionary
        (acroForm?.getDictionaryObject(COSName.FIELDS) as? COSArray)?.let { fields ->
            forEachField(fields) { field -> scripts += triggers(field, remove) }
        }

        // Bookmarks can run scripts when clicked.
        val outlines = catalog.getDictionaryObject(COSName.OUTLINES) as? COSDictionary
        outlines?.let { root ->
            forEachOutlineItem(root) { item ->
                if (isActive(item.getDictionaryObject(COSName.A))) {
                    scripts++
                    if (remove) item.removeItem(COSName.A)
                }
            }
        }

        return found.copy(
            attachments = attachments,
            scripts = scripts,
            commentAuthors = commentAuthors,
            hasPrivateData = privateData,
        )
    }

    /**
     * Additional actions run on their own — when a page opens, a field changes,
     * the document closes — so every one of them counts, whatever it does.
     */
    private fun triggers(dict: COSDictionary, remove: Boolean): Int {
        val aa = dict.getDictionaryObject(COSName.AA) as? COSDictionary ?: return 0
        val count = aa.size()
        if (remove) dict.removeItem(COSName.AA)
        return count
    }

    private fun isActive(action: COSBase?): Boolean {
        val dict = action as? COSDictionary ?: return false
        if (dict.getNameAsString(COSName.S) in ACTIVE_ACTIONS) return true
        // A harmless action can chain to a script through /Next.
        return when (val next = dict.getDictionaryObject(COSName.NEXT)) {
            is COSDictionary -> isActive(next)
            is COSArray -> (0 until next.size()).any { isActive(next.getObject(it)) }
            else -> false
        }
    }

    /** Leaf entries in a name tree. Guards against loops, which broken files have. */
    private fun countNameTree(root: COSDictionary): Int {
        val seen = IdentityHashMap<COSDictionary, Unit>()
        fun count(node: COSDictionary): Int {
            if (seen.put(node, Unit) != null) return 0
            val names = (node.getDictionaryObject(COSName.NAMES) as? COSArray)?.size()?.div(2) ?: 0
            val kids = node.getDictionaryObject(COSName.KIDS) as? COSArray ?: return names
            return names + (0 until kids.size()).sumOf { i ->
                (kids.getObject(i) as? COSDictionary)?.let(::count) ?: 0
            }
        }
        return count(root)
    }

    private fun forEachField(fields: COSArray, action: (COSDictionary) -> Unit) {
        val seen = IdentityHashMap<COSDictionary, Unit>()
        val pending = ArrayDeque<COSBase?>()
        (0 until fields.size()).forEach { pending += fields.getObject(it) }
        while (pending.isNotEmpty()) {
            val field = pending.removeFirst() as? COSDictionary ?: continue
            if (seen.put(field, Unit) != null) continue
            action(field)
            (field.getDictionaryObject(COSName.KIDS) as? COSArray)?.let { kids ->
                (0 until kids.size()).forEach { pending += kids.getObject(it) }
            }
        }
    }

    private fun forEachOutlineItem(root: COSDictionary, action: (COSDictionary) -> Unit) {
        val seen = IdentityHashMap<COSDictionary, Unit>()
        val pending = ArrayDeque<COSDictionary>()
        (root.getDictionaryObject(COSName.FIRST) as? COSDictionary)?.let { pending += it }
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (seen.put(item, Unit) != null) continue
            action(item)
            (item.getDictionaryObject(COSName.FIRST) as? COSDictionary)?.let { pending += it }
            (item.getDictionaryObject(COSName.NEXT) as? COSDictionary)?.let { pending += it }
        }
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
