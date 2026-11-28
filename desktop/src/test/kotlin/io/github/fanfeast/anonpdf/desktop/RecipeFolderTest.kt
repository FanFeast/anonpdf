package io.github.fanfeast.anonpdf.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RecipeFolderTest {

    @Test
    fun `linux uses XDG_CONFIG_HOME, then HOME`() {
        assertEquals(
            File("/home/ada/conf/anonpdf/recipes"),
            RecipeFolder.dir(mapOf("HOME" to "/home/ada", "XDG_CONFIG_HOME" to "/home/ada/conf"), "Linux", "/home/ada"),
        )
        assertEquals(
            File("/home/ada/snap/anonpdf/7/.config/anonpdf/recipes"),
            RecipeFolder.dir(mapOf("HOME" to "/home/ada/snap/anonpdf/7", "XDG_CONFIG_HOME" to "conf"), "Linux", "/home/ada"),
        )
    }

    @Test
    fun `windows uses the roaming app data folder`() {
        assertEquals(
            File("C:\\Users\\ada\\AppData\\Roaming", "AnonPDF\\recipes"),
            RecipeFolder.dir(mapOf("APPDATA" to "C:\\Users\\ada\\AppData\\Roaming"), "Windows 11", "C:\\Users\\ada"),
        )
    }
}
