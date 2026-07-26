package io.github.fanfeast.anonpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AnonPdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // PDFBox spills large documents to disk via File.createTempFile, which on
        // Android defaults to a directory we cannot write. Point it at our cache.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)

        // PDFBox ships its font metrics and glyph lists as assets; this points the
        // library at them so text operations work without touching the network.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
