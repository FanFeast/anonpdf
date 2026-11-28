package io.github.fanfeast.anonpdf.desktop

import java.io.File

/**
 * Where saved recipes live: the platform's folder for an app's own settings,
 * which, unlike the cache, is not something the system clears on its own.
 */
object RecipeFolder {

    /** The `anonpdf.recipes` property moves it, which is how the tests keep out of the real one. */
    val dir: File
        get() = System.getProperty("anonpdf.recipes")?.let(::File)
            ?: dir(System.getenv(), System.getProperty("os.name"), System.getProperty("user.home"))

    internal fun dir(env: Map<String, String>, osName: String, userHome: String): File {
        if (osName.startsWith("Windows")) {
            val roaming = env["APPDATA"]?.takeIf { it.isNotBlank() } ?: "$userHome\\AppData\\Roaming"
            return File(roaming, "AnonPDF\\recipes")
        }
        // As for the font cache: XDG first, then HOME, which a snap points into its own folder.
        val config = env["XDG_CONFIG_HOME"]?.takeIf { it.startsWith("/") }
            ?: "${env["HOME"]?.takeIf { it.startsWith("/") } ?: userHome}/.config"
        return File(config, "anonpdf/recipes")
    }
}
