package io.github.fanfeast.anonpdf.desktop

import java.io.File

/**
 * Tells PDFBox where to keep its list of the installed fonts.
 *
 * Left alone, PDFBox writes `.pdfbox.cache` straight into the home folder. That is
 * clutter on a plain install, and inside the snap's sandbox, which doesn't let the
 * app write there, it rebuilds the list on every start and logs a stack trace. The
 * platform's cache folder is the right place, and the snap gives the app a writable
 * one of its own.
 */
object FontCache {
    private const val PROPERTY = "pdfbox.fontcache"

    /** Must run before PDFBox first looks up a font, which reads the property once. */
    fun install() {
        val dir = dir(System.getenv(), System.getProperty("os.name"), System.getProperty("user.home"))
        // PDFBox ignores a folder that doesn't exist or can't be written, and falls
        // back to the home folder; the private scratch folder keeps it out of there.
        val usable = dir.takeIf { it.mkdirs() || it.isDirectory }?.takeIf { it.canWrite() }
        System.setProperty(PROPERTY, (usable ?: File(System.getProperty("java.io.tmpdir"))).absolutePath)
    }

    internal fun dir(env: Map<String, String>, osName: String, userHome: String): File {
        if (osName.startsWith("Windows")) {
            val local = env["LOCALAPPDATA"]?.takeIf { it.isNotBlank() } ?: "$userHome\\AppData\\Local"
            return File(local, "AnonPDF\\cache")
        }
        // XDG_CACHE_HOME first, as the spec says. A snap points HOME into its own
        // folder, while user.home stays the real one.
        val cache = env["XDG_CACHE_HOME"]?.takeIf { it.startsWith("/") }
            ?: "${env["HOME"]?.takeIf { it.startsWith("/") } ?: userHome}/.cache"
        return File(cache, "anonpdf")
    }
}
