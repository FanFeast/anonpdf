package io.github.fanfeast.anonpdf.storage

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val parentName: String get() = file.parentFile?.name ?: ""
}

data class FolderListing(
    val folder: File,
    val subfolders: List<File>,
    val pdfs: List<DeviceFile>,
)

/**
 * Finds PDFs on the device once the user has granted all-files access.
 *
 * Reading only — nothing here writes or deletes. Saving still goes through the
 * document picker so the destination is always something the user named.
 */
object DeviceFiles {

    /**
     * Directories that are pointless or forbidden to walk.
     *
     * Android/data and Android/obb stay unreadable even with all-files access, and
     * descending into them just burns time on permission errors. Caches and
     * dot-directories hold nothing a person is looking for.
     */
    private val SKIPPED = setOf(
        "Android", ".thumbnails", ".trashed", "cache", ".cache",
        "LOST.DIR", ".nomedia",
    )

    private const val DEFAULT_MAX_DEPTH = 8
    private const val DEFAULT_LIMIT = 800

    fun externalRoot(): File = Environment.getExternalStorageDirectory()

    /** The places a PDF is actually likely to be, most likely first. */
    fun likelyFolders(): List<File> = listOfNotNull(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        File(externalRoot(), "WhatsApp/Media/WhatsApp Documents"),
        File(externalRoot(), "Telegram/Telegram Documents"),
        externalRoot(),
    ).filter { it.isDirectory }

    /**
     * Walks [roots] for PDFs, newest first.
     *
     * Breadth-first with a depth cap and a result cap, because a full recursive
     * walk of a large phone can take many seconds and the user is waiting.
     */
    suspend fun findPdfs(
        roots: List<File> = likelyFolders(),
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        limit: Int = DEFAULT_LIMIT,
    ): List<DeviceFile> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DeviceFile>()
        val queue = ArrayDeque<Pair<File, Int>>()
        val visited = mutableSetOf<String>()

        roots.forEach { root ->
            if (root.isDirectory && visited.add(root.canonicalPathOrNull())) {
                queue.add(root to 0)
            }
        }

        while (queue.isNotEmpty() && found.size < limit) {
            val (dir, depth) = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue

            for (child in children) {
                if (found.size >= limit) break
                val name = child.name
                if (name.startsWith(".") || name in SKIPPED) continue

                if (child.isDirectory) {
                    if (depth < maxDepth && visited.add(child.canonicalPathOrNull())) {
                        queue.add(child to depth + 1)
                    }
                } else if (name.endsWith(".pdf", ignoreCase = true) && child.length() > 0) {
                    found.putIfAbsent(
                        child.canonicalPathOrNull(),
                        DeviceFile(child, name, child.length(), child.lastModified()),
                    )
                }
            }
        }

        found.values.sortedByDescending { it.lastModified }
    }

    /** One folder's contents, for the browse-by-folder view. */
    suspend fun listFolder(folder: File): FolderListing = withContext(Dispatchers.IO) {
        val children = runCatching { folder.listFiles() }.getOrNull().orEmpty()
        FolderListing(
            folder = folder,
            subfolders = children
                .filter { it.isDirectory && !it.name.startsWith(".") && it.name !in SKIPPED }
                .sortedBy { it.name.lowercase() },
            pdfs = children
                .filter { it.isFile && it.name.endsWith(".pdf", ignoreCase = true) }
                .map { DeviceFile(it, it.name, it.length(), it.lastModified()) }
                .sortedBy { it.name.lowercase() },
        )
    }

    /** Guards against symlink loops turning the walk into an infinite one. */
    private fun File.canonicalPathOrNull(): String =
        runCatching { canonicalPath }.getOrDefault(absolutePath)
}
