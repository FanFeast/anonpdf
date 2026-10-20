package io.github.fanfeast.anonpdf.pdf

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Android's main thread is the UI thread; the shared engine tests have no such thing. */
@RunWith(AndroidJUnit4::class)
class PdfRasterizerThreadTest {

    @Test
    fun renderingLeavesTheMainThreadFree() = runBlocking(Dispatchers.Main) {
        // Callers are composables on the main thread. If the render ran there, the
        // main thread could do nothing else until it finished; off it, other main
        // thread work gets in while the page is still drawing.
        val source = imageHeavyPdf()
        PdfRasterizer.open(source).use { rasterizer ->
            assertTrue(Looper.getMainLooper().isCurrentThread)
            val render = async { rasterizer.renderAtDpi(0, 300) }
            val mainRanDuringRender = async { !render.isCompleted }
            assertTrue("main thread was blocked by the render", mainRanDuringRender.await())
            render.await().release()
        }
    }

    private fun imageHeavyPdf(): File {
        val directory = TestPlatform.scratchDir("threadtest").apply { mkdirs() }
        val file = File(directory, "image-${System.nanoTime()}.pdf")
        val random = Random(7)
        val pixels = IntArray(1400 * 1000) { (0xFF shl 24) or random.nextInt(0xFFFFFF) }
        val bitmap = TestPlatform.imageOf(1400, 1000, pixels, hasAlpha = false)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val image = LosslessFactory.createFromImage(document, bitmap)
            PDPageContentStream(document, page).use { it.drawImage(image, 20f, 20f, 555f, 400f) }
            document.save(file)
        }
        return file
    }
}
