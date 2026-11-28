package io.github.fanfeast.anonpdf.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AppFoldersTest {

    @Test
    fun `linux uses the XDG folders, then HOME`() {
        val env = mapOf("HOME" to "/home/ada", "XDG_CONFIG_HOME" to "/home/ada/conf", "XDG_DATA_HOME" to "/home/ada/data")
        assertEquals(File("/home/ada/conf/anonpdf/recipes"), AppFolders.recipes(env, "Linux", "/home/ada"))
        assertEquals(File("/home/ada/data/anonpdf/tessdata"), AppFolders.ocrPacks(env, "Linux", "/home/ada"))

        // A relative XDG path is ignored, as the spec says; a snap's HOME is its own folder.
        val snap = mapOf("HOME" to "/home/ada/snap/anonpdf/7", "XDG_CONFIG_HOME" to "conf")
        assertEquals(File("/home/ada/snap/anonpdf/7/.config/anonpdf/recipes"), AppFolders.recipes(snap, "Linux", "/home/ada"))
        assertEquals(File("/home/ada/snap/anonpdf/7/.local/share/anonpdf/tessdata"), AppFolders.ocrPacks(snap, "Linux", "/home/ada"))
    }

    @Test
    fun `windows uses the roaming app data folder`() {
        val env = mapOf("APPDATA" to "C:\\Users\\ada\\AppData\\Roaming")
        assertEquals(File("C:\\Users\\ada\\AppData\\Roaming", "AnonPDF\\recipes"), AppFolders.recipes(env, "Windows 11", "C:\\Users\\ada"))
        assertEquals(File("C:\\Users\\ada\\AppData\\Roaming", "AnonPDF\\tessdata"), AppFolders.ocrPacks(env, "Windows 11", "C:\\Users\\ada"))
    }
}
