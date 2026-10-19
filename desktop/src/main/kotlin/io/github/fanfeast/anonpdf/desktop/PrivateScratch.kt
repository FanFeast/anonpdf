package io.github.fanfeast.anonpdf.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Gives this process a scratch folder only its user can open, and empties it on exit.
 *
 * PDFBox spills large documents to `java.io.tmpdir`, and the editor writes its
 * preview pages to scratch files. On Android that is the app's private cache. On
 * Linux it is /tmp, which every account on the machine can list; on Windows it is
 * the user's temp folder, which other programs the user runs can browse. So the
 * app makes its own folder (owner-only on Linux), points `java.io.tmpdir` at it,
 * and deletes it when the app exits.
 *
 * Each folder holds a lock for as long as its app runs. A folder whose lock can
 * be taken belongs to an app that is gone (killed, or crashed), and the next
 * start removes it, so nothing outlives a crash for longer than one launch.
 */
object PrivateScratch {
    private const val PREFIX = "anonpdf-"
    private const val LOCK = ".lock"

    /** Holds the lock open for the life of the process. */
    private var held: FileLock? = null

    /**
     * Must run first thing in `main`: `java.io.File` reads `java.io.tmpdir` once,
     * on first use, and keeps it.
     */
    fun install(base: File = File(System.getProperty("java.io.tmpdir"))): File {
        check(held == null) { "Already installed." }
        base.mkdirs()
        sweep(base)
        val dir = createOwnerOnlyDir(base)
        val lockChannel = RandomAccessFile(File(dir, LOCK), "rw").channel
        held = lockChannel.lock()
        System.setProperty("java.io.tmpdir", dir.absolutePath)
        Runtime.getRuntime().addShutdownHook(
            Thread({
                runCatching { held?.release(); lockChannel.close() }
                dir.deleteRecursively()
            }, "anonpdf-scratch-cleanup"),
        )
        return dir
    }

    /** Removes folders left behind by an app that is no longer running. */
    internal fun sweep(base: File) {
        base.listFiles { file -> file.isDirectory && file.name.startsWith(PREFIX) }?.forEach { dir ->
            val lockFile = File(dir, LOCK)
            val abandoned = !lockFile.exists() || runCatching {
                RandomAccessFile(lockFile, "rw").channel.use { channel ->
                    // Taking the lock means nobody holds it: its app is gone.
                    channel.tryLock()?.also { it.release() } != null
                }
            }.getOrDefault(false)
            if (abandoned) dir.deleteRecursively()
        }
    }

    private fun createOwnerOnlyDir(base: File): File {
        val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()
        val path = if (posix) {
            Files.createTempDirectory(
                base.toPath(),
                PREFIX,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        } else {
            // Windows: the user's temp folder is already closed to other accounts.
            Files.createTempDirectory(base.toPath(), PREFIX)
        }
        return path.toFile()
    }
}
