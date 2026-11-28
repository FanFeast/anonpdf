package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Properties, attached files and the pictures inside a document. */
class PdfDocInfoTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("docinfotest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun file(name: String) = File(workDir, name)

    /** Two pages, an old title and an XMP packet that repeats it. */
    private fun source(): File = file("source.pdf").also { out ->
        PDDocument().use { document ->
            repeat(2) { document.addPage(PDPage(PDRectangle.A4)) }
            document.documentInformation.title = "Old title"
            document.documentCatalog.metadata = PDMetadata(document).apply {
                importXMPMetadata("<x:xmpmeta xmlns:x='adobe:ns:meta/'><dc:title>Old title</dc:title></x:xmpmeta>".toByteArray())
            }
            document.save(out)
        }
    }

    private fun edit(input: File, vararg ops: EditOp): File = file("edited-${System.nanoTime()}.pdf").also { out ->
        runBlocking { PdfEditor.applyPlan(input, out, EditPlanBuilder.build(2, ops.toList())) }
    }

    @Test
    fun propertiesAreWrittenAndTheStaleXmpGoes() = runBlocking {
        val input = source()
        assertEquals(DocProperties(title = "Old title"), PdfDocInfo.readProperties(input))

        val wanted = DocProperties("Annual report", "Ada Lovelace", "Finances", "budget, 2026")
        val output = edit(input, SetProperties(wanted))

        assertEquals(wanted, PdfDocInfo.readProperties(output))
        PdfOps.load(output).use { assertNull(it.documentCatalog.metadata) }
    }

    @Test
    fun filesAreAttachedListedTakenOutAndRemoved() = runBlocking {
        val notes = file("notes.txt").apply { writeText("Meeting notes, 3 March") }
        val sheet = file("figures.csv").apply { writeText("a,b\n1,2\n") }
        val attached = edit(
            source(),
            SetAttachments(AttachmentChanges(added = listOf(Attachment("notes.txt", notes), Attachment("figures.csv", sheet)))),
        )
        // A PDF keeps them sorted by name, as its name trees must be.
        assertEquals(
            listOf(AttachedFile("figures.csv", sheet.length()), AttachedFile("notes.txt", notes.length())),
            PdfDocInfo.readAttachments(attached),
        )

        val out = PdfDocInfo.extractAttachments(attached, file("out"))
        assertEquals(listOf("figures.csv", "notes.txt"), out.map { it.name })
        assertArrayEquals(notes.readBytes(), out[1].readBytes())

        val fewer = edit(attached, SetAttachments(AttachmentChanges(removed = setOf("notes.txt"))))
        assertEquals(listOf("figures.csv"), PdfDocInfo.readAttachments(fewer).map { it.name })
        val none = edit(fewer, SetAttachments(AttachmentChanges(removed = setOf("figures.csv"))))
        assertTrue(PdfDocInfo.readAttachments(none).isEmpty())
    }

    @Test
    fun aJpegComesOutAsTheSameJpegAndTheRestAsPng() = runBlocking {
        val input = file("pictures.pdf")
        val raw: ByteArray
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val pixels = IntArray(64 * 48) { if (it % 7 == 0) 0xFF2060A0.toInt() else 0xFFE0E0E0.toInt() }
            val photo = JPEGFactory.createFromImage(document, TestPlatform.imageOf(64, 48, pixels, hasAlpha = false))
            val drawing = LosslessFactory.createFromImage(document, TestPlatform.imageOf(32, 32, IntArray(32 * 32) { 0xFF000000.toInt() }, hasAlpha = false))
            raw = photo.cosObject.createRawInputStream().use { it.readBytes() }
            PDPageContentStream(document, page).use {
                it.drawImage(photo, 50f, 500f, 200f, 150f)
                it.drawImage(drawing, 50f, 300f, 64f, 64f)
                // The same photo again is not saved twice.
                it.drawImage(photo, 300f, 500f, 100f, 75f)
            }
            document.save(input)
        }

        val out = PdfImages.extract(input, file("images"), "pictures")

        assertEquals(listOf("pictures-p1-1.jpg", "pictures-p1-2.png"), out.map { it.name }.sorted())
        assertArrayEquals(raw, out.first { it.extension == "jpg" }.readBytes())
        val png = TestPlatform.readImage(out.first { it.extension == "png" })!!
        assertEquals(32, png.width)
    }
}
