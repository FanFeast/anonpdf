package io.github.fanfeast.anonpdf.desktop

import java.io.File

/**
 * Tells JavaCPP, which loads the text recogniser's native library, where to
 * unpack it.
 *
 * Left alone it unpacks into `.javacpp` in the home folder: clutter on a plain
 * install, and a folder the snap's sandbox doesn't let the app write. The
 * platform's cache folder is the right place, next to PDFBox's font list.
 */
object NativeCache {
    private const val PROPERTY = "org.bytedeco.javacpp.cachedir"

    /** Must run before the recogniser is first used. */
    fun install() {
        if (System.getProperty(PROPERTY) != null) return
        val dir = File(FontCache.dir(System.getenv(), System.getProperty("os.name"), System.getProperty("user.home")), "native")
        val usable = dir.takeIf { it.mkdirs() || it.isDirectory }?.takeIf { it.canWrite() }
        System.setProperty(PROPERTY, (usable ?: File(System.getProperty("java.io.tmpdir"), "native")).absolutePath)
    }
}
