package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The recogniser's native library, loaded from the APK and run on the device.
 * CI puts an English pack among the test's assets; without one, only loading is checked.
 */
@RunWith(AndroidJUnit4::class)
class PdfOcrDeviceTest {

    @Test
    fun the_recogniser_loads_from_the_app() {
        assertTrue(PdfOcr.isAvailable)
    }

    @Test
    fun a_scanned_page_becomes_searchable(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue("no language pack to test with", "eng.traineddata" in assets.list("").orEmpty())
        val scratch = TestPlatform.scratchDir("ocr").apply { deleteRecursively(); mkdirs() }
        val packs = OcrPacks(File(scratch, "packs"))
        assets.open("eng.traineddata").use { packs.install("eng.traineddata", it) }

        val image = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.WHITE)
                drawText("Quarterly figures for 2026", 120f, 300f, Paint().apply {
                    color = Color.BLACK
                    textSize = 56f
                    typeface = Typeface.SERIF
                    isAntiAlias = true
                })
            }
        }
        val input = File(scratch, "scan.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val picture = LosslessFactory.createFromImage(document, image)
            PDPageContentStream(document, page).use { it.drawImage(picture, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
            document.save(input)
        }
        val output = File(scratch, "read.pdf")

        val result = PdfOcr.recognise(input, output, packs, listOf("eng"))

        assertEquals(1, result.recognised)
        val text = PdfOps.extractText(output)
        assertTrue(text, text.contains("Quarterly figures for 2026"))
        scratch.deleteRecursively()
    }
}
