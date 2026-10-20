package io.github.fanfeast.anonpdf.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

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

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val WIDTH = 1440
        const val HEIGHT = 960

        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    }
}
