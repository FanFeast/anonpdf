package io.github.fanfeast.anonpdf.pdf

import org.bytedeco.leptonica.PIX
import org.bytedeco.leptonica.global.leptonica.IFF_PNG
import org.bytedeco.leptonica.global.leptonica.IFF_TIFF
import org.bytedeco.leptonica.global.leptonica.IFF_TIFF_ZIP
import org.bytedeco.leptonica.global.leptonica.L_CLONE
import org.bytedeco.leptonica.global.leptonica.findFileFormat
import org.bytedeco.leptonica.global.leptonica.pixDestroy
import org.bytedeco.leptonica.global.leptonica.pixRead
import org.bytedeco.leptonica.global.leptonica.pixWrite
import org.bytedeco.leptonica.global.leptonica.pixaDestroy
import org.bytedeco.leptonica.global.leptonica.pixaGetCount
import org.bytedeco.leptonica.global.leptonica.pixaGetPix
import org.bytedeco.leptonica.global.leptonica.pixaReadMultipageTiff
import java.io.File

/**
 * Pictures the platform's own decoder can't read: TIFF (every page of it),
 * WebP and JPEG 2000 on desktop, TIFF on Android.
 *
 * Leptonica, which the text recogniser brings along, reads all of them. Each
 * picture is handed back as a PNG, which the platform then decodes as usual,
 * upright and capped in size like any other.
 */
object PictureFiles {

    /**
     * What Images to PDF offers to open on desktop. Android offers every picture
     * and reads HEIC itself; desktop has nothing that reads HEIC.
     */
    val extensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "jp2", "j2k")

    /** Every picture in [file], as PNGs in [scratch]; empty if Leptonica can't read it. */
    fun throughLeptonica(file: File, scratch: File): List<File> = runCatching {
        scratch.mkdirs()
        val format = IntArray(1)
        findFileFormat(file.absolutePath, format)
        val pages = mutableListOf<File>()
        fun save(pix: PIX) {
            val target = File(scratch, "picture-${System.nanoTime()}-${pages.size}.png")
            if (pixWrite(target.absolutePath, pix, IFF_PNG) == 0) pages += target
        }
        if (format[0] in IFF_TIFF..IFF_TIFF_ZIP) {
            val all = pixaReadMultipageTiff(file.absolutePath) ?: return emptyList()
            try {
                for (index in 0 until pixaGetCount(all)) {
                    val pix = pixaGetPix(all, index, L_CLONE) ?: continue
                    try { save(pix) } finally { pixDestroy(pix) }
                }
            } finally {
                pixaDestroy(all)
            }
        } else {
            val pix = pixRead(file.absolutePath) ?: return emptyList()
            try { save(pix) } finally { pixDestroy(pix) }
        }
        pages
    }.getOrDefault(emptyList())
}
