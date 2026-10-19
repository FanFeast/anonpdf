package io.github.fanfeast.anonpdf.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class PrivateScratchTest {

    private val base = Files.createTempDirectory("scratch-test").toFile()

    private fun leftover(name: String, withLock: Boolean = true) = File(base, name).apply {
        mkdirs()
        if (withLock) File(this, ".lock").createNewFile()
        File(this, "preview-1.pdf").writeText("page content")
    }

    @Test
    fun `a folder whose app is gone is swept on the next start`() {
        val crashed = leftover("anonpdf-111")
        PrivateScratch.sweep(base)
        assertFalse(crashed.exists())
    }

    @Test
    fun `a folder whose app is still running is left alone`() {
        val running = leftover("anonpdf-222")
        RandomAccessFile(File(running, ".lock"), "rw").channel.use { channel ->
            channel.lock().use {
                PrivateScratch.sweep(base)
                assertTrue(running.exists())
            }
        }
    }

    @Test
    fun `only the app's own folders are touched`() {
        val someoneElses = leftover("other-app-333", withLock = false)
        PrivateScratch.sweep(base)
        assertTrue(someoneElses.exists())
    }

    @Test
    fun `the app's folder is private and becomes the temp folder`() {
        val dir = PrivateScratch.install(base)
        assertEquals(dir.absolutePath, System.getProperty("java.io.tmpdir"))
        assertTrue(dir.isDirectory)
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
        assertEquals(
            "rwx------",
            PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.toPath())),
        )
        // Our own running folder survives a sweep.
        PrivateScratch.sweep(base)
        assertTrue(dir.exists())
    }
}
