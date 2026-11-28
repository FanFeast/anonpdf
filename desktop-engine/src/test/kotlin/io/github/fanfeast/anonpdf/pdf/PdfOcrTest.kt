package io.github.fanfeast.anonpdf.pdf

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * Recognition with the real engine and a real English pack. CI downloads the
 * pack and passes its folder as `anonpdf.tessdata`; without it these are skipped.
 */
class PdfOcrTest {

    private lateinit var scratch: File
    private lateinit var packs: OcrPacks

    @Before
    fun setUp() {
        val tessdata = System.getProperty("anonpdf.tessdata")?.let(::File)
        assumeTrue("no language pack to test with", tessdata != null && File(tessdata, "eng.traineddata").isFile)
        scratch = Files.createTempDirectory("ocr").toFile()
        packs = OcrPacks(File(scratch, "packs"))
        runBlocking { File(tessdata, "eng.traineddata").inputStream().use { packs.install("eng.traineddata", it) } }
    }

    /** A "scan": each page only a picture of its words, with no text in it at all. */
    private fun scan(vararg lines: String, rotation: Int = 0): File {
        val file = File(scratch, "scan.pdf")
        PDDocument().use { document ->
            lines.forEach { line ->
                val image = BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB)
                image.createGraphics().apply {
                    color = Color.WHITE
                    fillRect(0, 0, image.width, image.height)
                    color = Color.BLACK
                    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    font = Font(Font.SERIF, Font.PLAIN, 48)
                    drawString(line, 120, 300)
                    dispose()
                }
                // A page stored on its side, as some scanners do, and turned upright
                // by /Rotate: the picture is drawn turned back the other way.
                val stored = if (rotation == 90) turnedAnticlockwise(image) else image
                val box = if (rotation == 90) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                val page = PDPage(box).apply { this.rotation = rotation }
                document.addPage(page)
                val picture = LosslessFactory.createFromImage(document, stored)
                PDPageContentStream(document, page).use { it.drawImage(picture, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
            }
            document.save(file)
        }
        return file
    }

    private fun turnedAnticlockwise(image: BufferedImage): BufferedImage {
        val turned = BufferedImage(image.height, image.width, image.type)
        for (y in 0 until image.height) for (x in 0 until image.width) turned.setRGB(y, image.width - 1 - x, image.getRGB(x, y))
        return turned
    }

    private fun text(file: File, page: Int = 1) = PdfOps.load(file).use { document ->
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)
    }

    @Test
    fun `a scanned page becomes searchable and looks the same`() = runBlocking {
        val input = scan("The quick brown fox jumps over 2026 lazy dogs")
        assertFalse(text(input).contains("quick"))
        val output = File(scratch, "out.pdf")

        val result = PdfOcr.recognise(input, output, packs, listOf("eng"))

        assertEquals(OcrResult(recognised = 1, skipped = 0, total = 1), result)
        val found = text(output)
        assertTrue(found, found.contains("quick brown fox"))
        assertTrue(found, found.contains("2026"))
        // Where the words were found is where they now are: the redaction search
        // relies on it. The line was drawn about a sixth of the way down.
        PdfOps.load(output).use { document ->
            val glyphs = PageGlyphs.of(document.getPage(0))!!
            val box = glyphs.boxes(glyphs.text.indexOf("quick").let { it until it + 5 }).first()
            assertTrue("$box", box.top in 0.13f..0.19f && box.left in 0.15f..0.4f)
        }
    }

    @Test
    fun `pages that already have text are left alone`() = runBlocking {
        val input = File(scratch, "typed.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText("This page was typed, so it already has all of its text.")
                stream.endText()
            }
            document.save(input)
        }
        val result = PdfOcr.recognise(input, File(scratch, "out.pdf"), packs, listOf("eng"))
        assertEquals(OcrResult(recognised = 0, skipped = 1, total = 1), result)
    }

    @Test
    fun `a page turned sideways gets its text where it shows`() = runBlocking {
        val output = File(scratch, "out.pdf")
        PdfOcr.recognise(scan("Sideways scanning works fine", rotation = 90), output, packs, listOf("eng"))
        val found = text(output).filterNot(Char::isWhitespace)
        assertTrue(found, found.contains("Sidewaysscanningworksfine"))
    }

    @Test
    fun `only real language packs are kept`() = runBlocking {
        assertEquals(listOf("eng"), packs.installed().map { it.code })
        listOf("photo.jpg" to ByteArray(5000) { 7 }, "fake.traineddata" to "%PDF-1.7 not a pack".toByteArray()).forEach { (name, bytes) ->
            val refused = runCatching { packs.install(name, ByteArrayInputStream(bytes)) }
            assertTrue(name, refused.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(listOf("eng"), packs.installed().map { it.code })
        packs.remove("eng")
        assertTrue(packs.installed().isEmpty())
    }
}
