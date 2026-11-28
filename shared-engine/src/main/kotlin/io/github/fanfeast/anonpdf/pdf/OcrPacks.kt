package io.github.fanfeast.anonpdf.pdf

import io.github.fanfeast.anonpdf.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** An installed language pack: Tesseract's code for it ("eng", "chi_sim") and its size. */
data class OcrPack(val code: String, val bytes: Long) {
    /** The language's name in the app's language, where the code is one we know. */
    val label: String
        get() = TAGS[code]?.let { tag ->
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayName(Locale.getDefault())
            name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } ?: code

    private companion object {
        /** Tesseract names packs by ISO 639-2 code; the display name needs the two-letter one. */
        val TAGS = mapOf(
            "eng" to "en", "deu" to "de", "spa" to "es", "fra" to "fr", "hin" to "hi", "por" to "pt",
            "rus" to "ru", "chi_sim" to "zh-Hans", "chi_tra" to "zh-Hant", "ita" to "it", "nld" to "nl",
            "pol" to "pl", "tur" to "tr", "ukr" to "uk", "jpn" to "ja", "kor" to "ko", "ara" to "ar",
            "ben" to "bn", "tam" to "ta", "tel" to "te", "mar" to "mr", "guj" to "gu", "pan" to "pa",
            "urd" to "ur", "vie" to "vi", "ind" to "id", "tha" to "th", "heb" to "he", "ell" to "el",
            "swe" to "sv", "dan" to "da", "nor" to "no", "fin" to "fi", "ces" to "cs", "ron" to "ro",
            "hun" to "hu", "bul" to "bg",
        )
    }
}

/**
 * The OCR language packs the user has added: Tesseract's `.traineddata` files,
 * kept in one folder of the app's own storage.
 *
 * The app ships the recognition engine but no languages, and never downloads
 * one: a pack is a file the user fetches themselves and hands to the app. It is
 * data, never code, and is checked to be a pack before it is kept.
 */
class OcrPacks(val dir: File) {

    suspend fun installed(): List<OcrPack> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty()
            .map { OcrPack(it.name.removeSuffix(SUFFIX), it.length()) }
            .filter { CODE.matches(it.code) && it.code != "osd" }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Adds the pack in [input], which the user picked as [fileName]. Replaces a
     * pack with the same code. Throws [IllegalArgumentException] with a message
     * for the user when the file is not a language pack.
     */
    suspend fun install(fileName: String, input: InputStream): OcrPack = withContext(Dispatchers.IO) {
        val code = fileName.substringAfterLast('/').removeSuffix(SUFFIX)
        require(fileName.endsWith(SUFFIX) && CODE.matches(code)) { S.ocrNotAPack }
        dir.mkdirs()
        val temp = File(dir, "$code$SUFFIX.part")
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { S.ocrNotAPack }
                    out.write(buffer, 0, read)
                }
            }
            require(looksLikePack(temp)) { S.ocrNotAPack }
            val target = File(dir, "$code$SUFFIX")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
            OcrPack(code, target.length())
        } finally {
            temp.delete()
        }
    }

    suspend fun remove(code: String) = withContext(Dispatchers.IO) {
        require(CODE.matches(code))
        File(dir, "$code$SUFFIX").delete()
    }

    companion object {
        const val SUFFIX = ".traineddata"

        /** Tesseract's codes: "eng", "chi_sim", "script/Latin" is not offered. */
        private val CODE = Regex("[a-z]{3}(_[a-z]{3,4})?")

        /** The largest "best" packs are around 15 MB; nothing real comes near this. */
        private const val MAX_BYTES = 64L shl 20

        /**
         * A traineddata file starts with how many parts it has (a few dozen at
         * most), then where each part starts: -1 for one it doesn't have, or an
         * offset inside the file, in order. That is enough to tell one from a
         * picture or a PDF picked by mistake, before Tesseract ever reads it.
         */
        internal fun looksLikePack(file: File): Boolean {
            val size = file.length()
            if (size < 12) return false
            val header = file.inputStream().use { it.readNBytesCompat(4 + 8 * 64) }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int
            if (count !in 1..64 || header.size < 4 + 8 * count) return false
            var last = 0L
            var any = false
            repeat(count) {
                val offset = buffer.long
                if (offset != -1L) {
                    if (offset < 4 + 8L * count || offset >= size || offset < last) return false
                    last = offset
                    any = true
                }
            }
            return any
        }

        private fun InputStream.readNBytesCompat(n: Int): ByteArray {
            val out = ByteArray(n)
            var filled = 0
            while (filled < n) {
                val read = read(out, filled, n - filled)
                if (read < 0) break
                filled += read
            }
            return out.copyOf(filled)
        }
    }
}
