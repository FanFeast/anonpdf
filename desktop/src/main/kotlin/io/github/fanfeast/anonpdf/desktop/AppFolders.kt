package io.github.fanfeast.anonpdf.desktop

import java.io.File

/**
 * Where the app keeps what the user chose to keep: saved recipes in the
 * platform's settings folder, OCR language packs in its data folder. Neither is
 * the cache, which the system may clear on its own.
 *
 * The `anonpdf.recipes` and `anonpdf.ocrPacks` properties move them, which is how
 * the tests keep out of the real ones.
 */
object AppFolders {

    val recipes: File
        get() = System.getProperty("anonpdf.recipes")?.let(::File) ?: recipes(env(), os(), home())

    val ocrPacks: File
        get() = System.getProperty("anonpdf.ocrPacks")?.let(::File) ?: ocrPacks(env(), os(), home())

    internal fun recipes(env: Map<String, String>, osName: String, userHome: String): File =
        if (osName.startsWith("Windows")) {
            File(roaming(env, userHome), "AnonPDF\\recipes")
        } else {
            File(xdg(env, "XDG_CONFIG_HOME", ".config", userHome), "anonpdf/recipes")
        }

    internal fun ocrPacks(env: Map<String, String>, osName: String, userHome: String): File =
        if (osName.startsWith("Windows")) {
            File(roaming(env, userHome), "AnonPDF\\tessdata")
        } else {
            File(xdg(env, "XDG_DATA_HOME", ".local/share", userHome), "anonpdf/tessdata")
        }

    private fun roaming(env: Map<String, String>, userHome: String) =
        env["APPDATA"]?.takeIf { it.isNotBlank() } ?: "$userHome\\AppData\\Roaming"

    // As for the font cache: the XDG variable first, then HOME, which a snap points into its own folder.
    private fun xdg(env: Map<String, String>, variable: String, fallback: String, userHome: String) =
        env[variable]?.takeIf { it.startsWith("/") }
            ?: "${env["HOME"]?.takeIf { it.startsWith("/") } ?: userHome}/$fallback"

    private fun env() = System.getenv()

    private fun os() = System.getProperty("os.name")

    private fun home() = System.getProperty("user.home")
}
