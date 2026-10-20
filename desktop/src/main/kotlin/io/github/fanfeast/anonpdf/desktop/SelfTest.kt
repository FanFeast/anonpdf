package io.github.fanfeast.anonpdf.desktop

import io.github.fanfeast.anonpdf.pdf.AddMark
import io.github.fanfeast.anonpdf.pdf.EditPlanBuilder
import io.github.fanfeast.anonpdf.pdf.FillMark
import io.github.fanfeast.anonpdf.pdf.ImageFiles
import io.github.fanfeast.anonpdf.pdf.ImageFormat
import io.github.fanfeast.anonpdf.pdf.ImageStamp
import io.github.fanfeast.anonpdf.pdf.ImagesToPdfOptions
import io.github.fanfeast.anonpdf.pdf.InkMark
import io.github.fanfeast.anonpdf.pdf.MarkRect
import io.github.fanfeast.anonpdf.pdf.PageNumberOptions
import io.github.fanfeast.anonpdf.pdf.PdfConvert
import io.github.fanfeast.anonpdf.pdf.PdfEditor
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.ProtectOptions
import io.github.fanfeast.anonpdf.pdf.RotatePages
import io.github.fanfeast.anonpdf.pdf.SetPageNumbers
import io.github.fanfeast.anonpdf.pdf.SetWatermark
import io.github.fanfeast.anonpdf.pdf.TextMark
import io.github.fanfeast.anonpdf.pdf.WatermarkOptions
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.image.BufferedImage
import java.io.File

/**
 * `AnonPDF --self-test`: runs the engine's riskiest paths inside the packaged app
 * and exits 0 if they all work.
 *
 * The release build is shrunk, and PDFBox reaches some of its own code only by
 * name, so a shrinker rule that is too eager breaks features without breaking the
 * build. CI runs this against every packaged build, so what is checked is exactly
 * what people download. Everything happens in the private scratch folder.
 */
internal object SelfTest {

    fun run(): Int = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "self-test").apply { mkdirs() }
        val failures = mutableListOf<String>()
        var checks = 0
        fun check(what: String, ok: Boolean) {
            checks++
            if (!ok) failures += what
        }

        val source = File(dir, "source.pdf")
        PDDocument().use { document ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 30f)
                    stream.newLineAtOffset(70f, 720f)
                    stream.showText("Secret${index + 1}")
                    stream.newLineAtOffset(0f, -300f)
                    stream.showText("Body${index + 1}")
                    stream.endText()
                }
            }
            document.save(source)
        }

        // White-out flattens its page: render, JPEG-encode, re-embed. Then text,
        // ink, a watermark and page numbers on top, and a rotation.
        val plan = EditPlanBuilder.build(
            2,
            listOf(
                AddMark(FillMark(id = 1, sourceIndex = 0, rect = MarkRect(0.02f, 0.05f, 0.98f, 0.2f), color = 0xFFFFFFFF.toInt(), opacity = 1f)),
                AddMark(TextMark(id = 2, sourceIndex = 0, text = "Replacement", left = 0.1f, top = 0.4f)),
                AddMark(InkMark(id = 3, sourceIndex = 1, strokes = listOf((0..10).map { 0.1f + it * 0.05f to 0.6f }), color = 0xFFD32F2F.toInt(), widthRatio = 0.01f)),
                SetWatermark(WatermarkOptions(text = "DRAFT")),
                SetPageNumbers(PageNumberOptions(format = "{n} of {total}")),
                RotatePages(90, setOf(1)),
            ),
        )
        val edited = File(dir, "edited.pdf")
        PdfEditor.applyPlan(source, edited, plan)

        PdfOps.load(edited).use { document ->
            check("both pages kept", document.numberOfPages == 2)
            check("rotation applied", document.getPage(1).rotation == 90)
        }
        val text = PdfOps.extractText(edited)
        check("white-out removes the covered text", !text.contains("Secret1"))
        // The flattened page keeps no text of its own (by design: nothing hidden can
        // be recovered), so the untouched page is the one that proves text survives.
        check("the untouched page keeps its body text", text.contains("Body2"))
        check("added text is real text", text.contains("Replacement"))
        check("an untouched page keeps its text", text.contains("Secret2"))
        check("page numbers are stamped", text.contains("2 of 2"))

        val preview = PdfEditor.renderPreview(source, plan, outputPosition = 0, targetWidthPx = 300, workDir = dir)
        check("preview renders", preview != null && preview.width == 300)

        val locked = File(dir, "locked.pdf")
        PdfOps.protect(edited, locked, ProtectOptions(userPassword = "open sesame"))
        check("protect encrypts", PdfOps.isPasswordProtected(locked))
        val unlocked = File(dir, "unlocked.pdf")
        PdfOps.unlock(locked, unlocked, "open sesame")
        check("unlock restores the text", PdfOps.extractText(unlocked).contains("Replacement"))

        // The tools: ImageIO's encoders and decoders, JPEG re-encoding, merge and split.
        val pagesDir = File(dir, "pages")
        val jpegs = PdfConvert.pdfToImages(edited, pagesDir, dpi = 60, format = ImageFormat.JPEG, jpegQuality = 80, baseName = "page")
        val pngs = PdfConvert.pdfToImages(edited, pagesDir, dpi = 60, format = ImageFormat.PNG, jpegQuality = 80, baseName = "page")
        check("pages export as images", (jpegs + pngs).size == 4 && (jpegs + pngs).all { ImageFiles.decode(it) != null })
        val fromImages = File(dir, "from-images.pdf")
        PdfConvert.imagesToPdf(jpegs + pngs, ImageFiles::decode, fromImages, ImagesToPdfOptions())
        check("images become a PDF", PdfOps.readMeta(fromImages).pageCount == 4)
        val smaller = File(dir, "smaller.pdf")
        val smart = PdfConvert.compressSmart(fromImages, smaller, jpegQuality = 0.4f, maxImageDimension = 200)
        check("compression keeps every page and never grows a file", PdfOps.readMeta(smaller).pageCount == 4 && smart.compressedBytes <= smart.originalBytes)
        val flat = File(dir, "flat.pdf")
        PdfConvert.compressByRasterizing(edited, flat, dpi = 60, jpegQuality = 0.5f)
        check("rasterizing compression keeps every page", PdfOps.readMeta(flat).pageCount == 2)
        val merged = File(dir, "merged.pdf")
        PdfOps.merge(listOf(source, edited), merged)
        check("merge joins documents", PdfOps.readMeta(merged).pageCount == 4)
        val parts = PdfOps.splitToFiles(merged, File(dir, "parts"), listOf(0..0, 1..3), "part")
        check("split makes one file per range", parts.map { PdfOps.readMeta(it).pageCount } == listOf(1, 3))
        val signed = File(dir, "signed.pdf")
        val ink = BufferedImage(120, 40, BufferedImage.TYPE_INT_ARGB).apply { setRGB(10, 10, 0xFF000000.toInt()) }
        PdfOps.stampImages(source, signed, listOf(ImageStamp(pageIndex = 1, bitmap = ink, centerXRatio = 0.5f, centerYRatio = 0.8f, widthRatio = 0.3f)))
        check("a signature is stamped", PdfOps.load(signed).use { it.getPage(1).resources.xObjectNames.any() })

        dir.deleteRecursively()
        if (failures.isEmpty()) {
            println("AnonPDF self-test passed: $checks checks.")
            0
        } else {
            System.err.println("AnonPDF self-test FAILED (${failures.size} of $checks):")
            failures.forEach { System.err.println("  - $it") }
            1
        }
    }
}
