package io.github.fanfeast.anonpdf.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performScrollTo
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.pdf.PdfOps
import io.github.fanfeast.anonpdf.pdf.PdfForms
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * The desktop app driven end to end with real files: the editor, the tools it
 * hands documents to, and the screens shared with Android, on the desktop engine.
 * The window is desktop-sized, so the editor shows its wide layout.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {

    private lateinit var scratch: File
    private lateinit var pdf: File

    @Before
    fun setUp() {
        scratch = Files.createTempDirectory("desktop-editor").toFile()
        // Where DocumentStore keeps its files, as PrivateScratch would set it.
        System.setProperty("java.io.tmpdir", scratch.absolutePath)
        pdf = File(scratch, "three pages.pdf")
        PDDocument().use { document ->
            repeat(3) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 30f)
                    stream.newLineAtOffset(70f, 690f)
                    stream.showText("Page ${index + 1}")
                    stream.endText()
                }
            }
            document.save(pdf)
        }
    }

    @Test
    fun `an edit can be undone, and a saved file carries it`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(pdf)) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No edits yet").assertExists()

        onNodeWithText("Right").performClick()
        onNodeWithText("1 edit").assertExists()
        onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        onNodeWithText("No edits yet").assertExists()
        onNodeWithContentDescription("Undo").assertIsNotEnabled()

        onNodeWithText("Right").performClick()
        onNodeWithText("Save as…").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }

        val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
        PdfOps.load(saved).use { document ->
            assertEquals(3, document.numberOfPages)
            assertEquals(90, document.getPage(0).rotation)
            assertEquals(0, document.getPage(1).rotation)
        }
    }

    /** A one-page form: a name to type and a box to tick. */
    private fun formPdf(): File {
        val file = File(scratch, "form.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val name = PDTextField(form).apply {
                partialName = "full_name"
                defaultAppearance = "/Helv 12 Tf 0 g"
            }
            name.widgets.single().apply {
                rectangle = PDRectangle(60f, 700f, 240f, 22f)
                this.page = page
                page.annotations.add(this)
            }
            val agree = PDCheckBox(form).apply { partialName = "agree" }
            agree.widgets.single().apply {
                rectangle = PDRectangle(60f, 650f, 14f, 14f)
                appearance = PDAppearanceDictionary(
                    COSDictionary().apply {
                        setItem(COSName.N, COSDictionary().apply {
                            for (state in listOf("Yes", "Off")) {
                                setItem(state, PDAppearanceStream(document).apply { bBox = PDRectangle(14f, 14f) })
                            }
                        })
                    },
                )
                appearanceState = COSName.Off
                this.page = page
                page.annotations.add(this)
            }
            form.fields.addAll(listOf(name, agree))
            document.save(file)
        }
        return file
    }

    @Test
    fun `a form is filled in the editor, previewed and saved`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(formPdf())) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Fill form")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Fill form").performClick()
        onNode(hasSetTextAction()).performTextInput("Jane Doe")
        onNodeWithText("Agree").performClick()
        onNodeWithText("Apply").performClick()
        onNodeWithText("1 edit").assertExists()

        onNodeWithText("Save as…").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
        val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
        val fields = PdfForms.read(saved).fields.associateBy { it.name }
        assertEquals("Jane Doe", fields.getValue("full_name").value)
        assertEquals("Yes", fields.getValue("agree").value)
    }

    @Test
    fun `found text is blacked out on the chosen pages and gone from the saved file`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { DesktopApp(DesktopNavigator(pdf)) }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("All pages").performClick()
            onNodeWithText("Redact").performClick()
            onNode(hasSetTextAction()).performTextInput("page   2")
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("1 match on 1 page")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Apply").assertIsEnabled().performClick()
            onNodeWithText("1 edit").assertExists()

            onNodeWithText("Save as…").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
            val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
            PdfOps.load(saved).use { document ->
                fun text(page: Int) = PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                }.getText(document)
                assertFalse(text(2).contains("Page 2"))
                assertTrue("pages without a match keep their text", text(1).contains("Page 1"))
            }
        }

    @Test
    fun `a bookmark is added for the page on screen, renamed and saved`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(pdf)) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithContentDescription("Next page").performClick()
        onNodeWithText("Bookmarks").performClick()
        onNodeWithText("No bookmarks yet. Add one for the page you're on.").assertExists()
        onNodeWithText("Add for this page").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Results")
        onNodeWithText("Apply").performClick()
        onNodeWithText("1 edit").assertExists()

        onNodeWithText("Save as…").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
        val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
        PdfOps.load(saved).use { document ->
            val item = document.documentCatalog.documentOutline.firstChild
            assertEquals("Results", item.title)
            assertEquals(1, document.pages.indexOf(item.findDestinationPage(document)))
        }
    }

    @Test
    fun `a Bates number typed into the footer is stamped on every page`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(pdf)) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Header & footer").performClick()
        // The six places, top row then bottom, then the start number: the sixth is bottom right.
        onAllNodes(hasSetTextAction())[5].performTextInput("ACME-")
        onNodeWithText("Bates number").performClick()
        onNodeWithText("Apply").performClick()
        onNodeWithText("1 edit").assertExists()

        onNodeWithText("Save as…").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
        val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
        PdfOps.load(saved).use { document ->
            for (page in 1..3) {
                val text = PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                }.getText(document)
                assertTrue(text, text.contains("ACME-00000$page"))
            }
        }
    }

    /** Three pages: words and a square markup on the first, the second blank, words on the third. */
    private fun markedUpPdf(): File {
        val file = File(scratch, "marked up.pdf")
        PDDocument().use { document ->
            repeat(3) { document.addPage(PDPage(PDRectangle.A4)) }
            for (index in listOf(0, 2)) {
                PDPageContentStream(document, document.getPage(index)).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(70f, 750f)
                    stream.showText("Words on page ${index + 1}")
                    stream.endText()
                }
            }
            val look = PDAppearanceStream(document).apply { bBox = PDRectangle(120f, 80f) }
            PDPageContentStream(document, look).use { stream ->
                stream.setNonStrokingColor(0.9f, 0.1f, 0.1f)
                stream.addRect(0f, 0f, 120f, 80f)
                stream.fill()
            }
            document.getPage(0).annotations = listOf(
                PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
                    rectangle = PDRectangle(100f, 500f, 120f, 80f)
                    appearance = PDAppearanceDictionary().apply { setNormalAppearance(look) }
                },
            )
            document.save(file)
        }
        return file
    }

    @Test
    fun `clean up removes the markups and deletes the blank page`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(markedUpPdf())) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Clean up").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasText("This document has 1 comment, highlight or other markup.")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Find blank pages").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("1 blank page: 2")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Delete them").performClick()
        onNodeWithText("Remove").performClick()
        onNodeWithText("Apply").performClick()
        onNodeWithText("2 edits").assertExists()

        onNodeWithText("Save as…").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
        val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
        PdfOps.load(saved).use { document ->
            assertEquals(2, document.numberOfPages)
            assertTrue(document.getPage(0).annotations.isEmpty())
        }
    }

    @Test
    fun `a page is duplicated, a blank one added and the whole file turned grey`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { DesktopApp(DesktopNavigator(pdf)) }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Duplicate").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 2 / 4")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Blank page").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 3 / 5")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Greyscale").performClick()
            onNodeWithText("3 edits").assertExists()

            // Level text has no lean to correct, and says so.
            onNodeWithText("Straighten").performClick()
            onNodeWithText("Straighten automatically").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("No lines of text to go by on these pages.")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Cancel").performClick()

            onNodeWithText("Save as…").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
            val saved = File(scratch, "out").listFiles { file -> file.name.endsWith("-edited.pdf") }!!.single()
            PdfOps.load(saved).use { document ->
                assertEquals(5, document.numberOfPages)
                fun text(page: Int) = PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                }.getText(document).trim()
                assertEquals("Page 1", text(1))
                assertEquals("the copy follows its page", "Page 1", text(2))
                assertEquals("then the blank page", "", text(3))
                assertEquals("Page 2", text(4))
            }
        }

    private fun onePagePdf(name: String, text: String): File {
        val file = File(scratch, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 16f)
                stream.newLineAtOffset(70f, 700f)
                stream.showText(text)
                stream.endText()
            }
            document.save(file)
        }
        return file
    }

    @Test
    fun `two versions are compared and the report says what changed`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val navigator = DesktopNavigator()
        setContent { DesktopApp(navigator) }
        // In the sidebar; the start page also has a card for it.
        onNode(hasText("Compare PDFs") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        waitForIdle()
        runOnIdle {
            navigator.drop(
                listOf(
                    onePagePdf("contract v1.pdf", "The fee is 500 dollars per month."),
                    onePagePdf("contract v2.pdf", "The fee is 650 dollars per month."),
                ),
            )
        }
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasText("contract v2.pdf", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val run = hasText("Compare PDFs") and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and isEnabled()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(run).fetchSemanticsNodes().isNotEmpty() }
        onNode(run).performScrollTo().performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasText("1 word added and 1 removed", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val report = File(scratch, "out").listFiles { file -> file.name.startsWith("comparison") }!!.single()
        PdfOps.load(report).use { assertEquals(1, it.numberOfPages) }
    }

    @Test
    fun `closing a document returns to the start page`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        setContent { DesktopApp(DesktopNavigator(pdf)) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithContentDescription("Back").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasText("What would you like to do?")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a tool works on the edited document, and coming back keeps the edits`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { DesktopApp(DesktopNavigator(pdf)) }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

            // Delete page 1, then hand the result to Extract text.
            onNodeWithText("Delete").performClick()
            onNodeWithText("1 edit").assertExists()
            onNodeWithText("Get text").performClick()
            // The tool has the edited copy: two pages, not three.
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("2 pages", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }

            onNode(hasText("Extract text") and isButton).performScrollTo().performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }
            val text = File(scratch, "out").listFiles { file -> file.name.endsWith("-text.txt") }!!.single()
                .readLines()
                .filterNot { it.startsWith("--- Page") } // the extractor's own page markers
                .joinToString("\n")
            assertFalse("the deleted page is gone", "Page 1" in text)
            assertTrue("the kept pages are there", "Page 2" in text && "Page 3" in text)

            onNodeWithContentDescription("Back").performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("1 edit")).fetchSemanticsNodes().isNotEmpty() }
        }

    @Test
    fun `a drawn signature is stamped onto the page`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val navigator = DesktopNavigator()
        navigator.show(Pane.Tool(ToolId.SIGN, pdf))
        setContent { DesktopApp(navigator) }
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasContentDescription("Signature pad")).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("Signature pad").performTouchInput {
            swipe(start = centerLeft + Offset(40f, 0f), end = center + Offset(0f, -20f), durationMillis = 300)
        }
        onNode(hasText("Sign and save") and isButton).performScrollTo().assertIsEnabled().performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Done")).fetchSemanticsNodes().isNotEmpty() }

        val signed = File(scratch, "out").listFiles { file -> file.name.endsWith("-signed.pdf") }!!.single()
        PdfOps.load(signed).use { document ->
            assertEquals(3, document.numberOfPages)
            assertTrue("page 1 carries the signature", document.getPage(0).resources.xObjectNames.any())
            assertFalse("page 2 does not", document.getPage(1).resources.xObjectNames.any())
        }
    }

    // Dropping: an OS drag can't be faked in a test, so these start where the
    // window's drop target hands over, with the dropped files.

    @Test
    fun `dropped files are sorted into PDFs and pictures, and the rest left out`() {
        val picture = picture("photo.JPG")
        val notes = File(scratch, "notes.txt").apply { writeText("not a PDF") }
        val folder = File(scratch, "folder.pdf").apply { mkdir() }

        val dropped = sortDropped(listOf(pdf, picture, notes, folder))
        assertEquals(listOf(pdf), dropped.pdfs)
        assertEquals(listOf(picture), dropped.images)
        assertFalse("nothing usable is refused", DesktopNavigator().drop(listOf(notes, folder)))
    }

    @Test
    fun `a PDF dropped on the start page opens in the editor`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val navigator = DesktopNavigator()
        setContent { DesktopApp(navigator) }
        onNodeWithText("What would you like to do?").assertExists()

        runOnIdle { assertTrue(navigator.drop(listOf(pdf))) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a PDF dropped on an open document is added through Merge, not swapped in`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val navigator = DesktopNavigator(pdf)
            setContent { DesktopApp(navigator) }
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

            runOnIdle { navigator.drop(listOf(pdf.copyTo(File(scratch, "more.pdf")))) }
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("3 pages to insert")).fetchSemanticsNodes().isNotEmpty()
            }
            onNode(hasText("Insert") and isButton).performClick()
            waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("1 edit")).fetchSemanticsNodes().isNotEmpty() }
        }

    @Test
    fun `several PDFs dropped on the start page go to Merge`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val navigator = DesktopNavigator()
        setContent { DesktopApp(navigator) }

        runOnIdle { navigator.drop(listOf(pdf, pdf.copyTo(File(scratch, "more.pdf")))) }
        waitUntil(timeoutMillis = TIMEOUT_MS) {
            onAllNodes(hasText("more.pdf", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodes(hasText("three pages.pdf", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(Pane.Tool(ToolId.MERGE), navigator.pane)
    }

    @Test
    fun `pictures dropped on the start page go to Images to PDF`() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val navigator = DesktopNavigator()
        setContent { DesktopApp(navigator) }

        runOnIdle { navigator.drop(listOf(picture("one.png"), picture("two.png"))) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Image 2")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Pane.Tool(ToolId.IMAGES_TO_PDF), navigator.pane)
    }

    @Test
    fun `a tool takes a dropped PDF, and says so when handed the wrong kind of file`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val navigator = DesktopNavigator()
            navigator.show(Pane.Tool(ToolId.COMPRESS))
            setContent { DesktopApp(navigator) }

            runOnIdle { navigator.drop(listOf(picture("photo.png"))) }
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("Only a PDF can be used here.")).fetchSemanticsNodes().isNotEmpty()
            }

            runOnIdle { navigator.drop(listOf(pdf)) }
            waitUntil(timeoutMillis = TIMEOUT_MS) {
                onAllNodes(hasText("3 pages", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(Pane.Tool(ToolId.COMPRESS), navigator.pane)
        }

    private fun picture(name: String): File = File(scratch, name).also {
        ImageIO.write(BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", it)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val WIDTH = 1440
        const val HEIGHT = 960

        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    }
}
