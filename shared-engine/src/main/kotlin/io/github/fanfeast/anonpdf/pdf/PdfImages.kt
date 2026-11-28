package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The pictures inside a PDF, as they were put in: a photo that went in as a
 * JPEG comes out as that same JPEG, byte for byte, not a screenshot of the page.
 * Anything stored another way is decoded and saved as a PNG, which loses nothing.
 */
object PdfImages {

    /** Smaller than this is a rule, a bullet or a spacer, not a picture anyone wants. */
    private const val MIN_EDGE = 16

    suspend fun extract(
        input: File,
        outputDir: File,
        baseName: String,
        password: String? = null,
        onProgress: Progress = {},
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        PdfOps.load(input, password).use { document ->
            val seen = HashSet<COSBase>()
            val files = mutableListOf<File>()
            val stem = FileNames.sanitize(baseName)
            val total = document.numberOfPages
            for (index in 0 until total) {
                ensureActive()
                collect(document.getPage(index).resources, seen, depth = 0) { image ->
                    val number = files.size + 1
                    save(image, File(outputDir, "$stem-p${index + 1}-$number"))?.let(files::add)
                }
                onProgress((index + 1f) / total)
            }
            require(files.isNotEmpty()) { S.noImagesFound }
            files
        }
    }

    private fun collect(resources: PDResources?, seen: MutableSet<COSBase>, depth: Int, found: (PDImageXObject) -> Unit) {
        if (resources == null || depth > 6) return
        for (name in resources.xObjectNames) {
            val xobject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            // A logo on every page is one picture, saved once.
            if (!seen.add(xobject.cosObject)) continue
            when (xobject) {
                is PDImageXObject -> if (xobject.width >= MIN_EDGE && xobject.height >= MIN_EDGE) found(xobject)
                is PDFormXObject -> collect(xobject.resources, seen, depth + 1, found)
            }
        }
    }

    /** [base] plus the extension of whatever it was saved as, or null when it couldn't be read. */
    private fun save(image: PDImageXObject, base: File): File? = runCatching {
        val filters = image.stream.filters.orEmpty()
        val last = filters.lastOrNull()
        // Only when the JPEG is the whole story: a mask or an odd colour space
        // means the bytes alone would not look like the page.
        val plainJpeg = last == COSName.DCT_DECODE && filters.size == 1 && image.cosObject.getItem(COSName.SMASK) == null &&
            image.cosObject.getItem(COSName.MASK) == null && image.colorSpace?.name in setOf("DeviceRGB", "DeviceGray")
        if (plainJpeg) {
            val target = File("${base.path}.jpg")
            image.cosObject.createRawInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            target
        } else {
            val target = File("${base.path}.png")
            val picture = image.image
            target.outputStream().use { picture.writeTo(it, ImageFormat.PNG) }
            picture.release()
            target
        }
    }.getOrNull()
}
