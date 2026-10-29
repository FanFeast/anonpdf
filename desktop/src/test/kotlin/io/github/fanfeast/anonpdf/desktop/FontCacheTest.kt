package io.github.fanfeast.anonpdf.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FontCacheTest {

    private fun linux(vararg env: Pair<String, String>) = FontCache.dir(env.toMap(), "Linux", "/home/ada")

    @Test
    fun `a plain install keeps it in the cache folder, not the home folder`() {
        assertEquals(File("/home/ada/.cache/anonpdf"), linux("HOME" to "/home/ada"))
    }

    @Test
    fun `the Flatpak's own cache folder is used`() {
        val dir = linux("HOME" to "/home/ada", "XDG_CACHE_HOME" to "/home/ada/.var/app/io.github.fanfeast.anonpdf/cache")
        assertEquals(File("/home/ada/.var/app/io.github.fanfeast.anonpdf/cache/anonpdf"), dir)
    }

    @Test
    fun `a snap's own home folder is used, not the real one`() {
        assertEquals(File("/home/ada/snap/anonpdf/x1/.cache/anonpdf"), linux("HOME" to "/home/ada/snap/anonpdf/x1"))
    }

    @Test
    fun `a relative XDG_CACHE_HOME is ignored, as the spec says`() {
        assertEquals(File("/home/ada/.cache/anonpdf"), linux("HOME" to "/home/ada", "XDG_CACHE_HOME" to "cache"))
    }

    @Test
    fun `Windows uses the local app data folder`() {
        val dir = FontCache.dir(mapOf("LOCALAPPDATA" to "C:\\Users\\ada\\AppData\\Local"), "Windows 11", "C:\\Users\\ada")
        assertEquals(File("C:\\Users\\ada\\AppData\\Local", "AnonPDF\\cache"), dir)
    }
}
