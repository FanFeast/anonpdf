package io.github.fanfeast.anonpdf.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.fanfeast.anonpdf.pdf.PdfOps
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The shared editor screen, driven through the desktop app with real files: the
 * same screen the Android app shows, on the desktop engine.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopEditorTest {

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
    fun `an edit can be undone, and a saved file carries it`() = runComposeUiTest {
        setContent { DesktopApp(pdf) }
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
    fun `leaving a document returns to an empty editor`() = runComposeUiTest {
        setContent { DesktopApp(pdf) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("page 1 / 3")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithContentDescription("Back").performClick()
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodes(hasText("Choose a PDF")).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
