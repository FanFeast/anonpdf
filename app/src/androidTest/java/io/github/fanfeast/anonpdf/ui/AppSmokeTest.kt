package io.github.fanfeast.anonpdf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.fanfeast.anonpdf.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real app the way a person would: launch it, tap through the main
 * screens, and open a PDF handed over by "another app".
 *
 * These are smoke tests. They do not check what each tool produces (that is
 * PdfEngineTest and PdfEditorTest); they check that the screens are wired
 * together and a document actually reaches the viewer and the editor.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var fixtureDir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Outside work/ and out/, so the launch-time workspace clear leaves it alone.
        fixtureDir = File(context.cacheDir, "uitest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        fixtureDir.deleteRecursively()
    }

    private fun launchHome() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Open a PDF").assertIsDisplayed()
    }

    @Test
    fun launchesToHome() {
        launchHome()
        compose.onNodeWithText("My files").assertIsDisplayed()
        compose.onNodeWithText("Edit pages").assertIsDisplayed()
    }

    @Test
    fun settingsOpensAndReturns() {
        launchHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Clear recent files").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open a PDF").assertIsDisplayed()
    }

    @Test
    fun aboutOpensAndReturns() {
        launchHome()
        compose.onNodeWithContentDescription("About").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open a PDF").assertIsDisplayed()
    }

    @Test
    fun browseOpens() {
        launchHome()
        compose.onNodeWithText("My files").performClick()
        compose.onNodeWithText("Browse").assertIsDisplayed()
    }

    @Test
    fun editorWithoutDocumentAsksForOne() {
        launchHome()
        compose.onNodeWithText("Edit pages").performClick()
        compose.onNodeWithText("Choose a PDF").assertIsDisplayed()
    }

    @Test
    fun pdfFromAnotherAppOpensInViewerThenEditor() {
        val pdf = samplePdf(pages = 3)
        // The platform refuses file:// URIs in intents by default. A real sender
        // would pass a content:// URI; for the test, lift the check in this process.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(Uri.fromFile(pdf), "application/pdf")
        scenario = ActivityScenario.launch(intent)

        // The page counter only appears once the renderer has opened the file.
        compose.waitFor { onAllNodesWithText("1 / 3") }
        compose.waitFor { onAllNodesWithContentDescription("Page 1") }

        compose.onNodeWithContentDescription("Edit").performClick()
        compose.waitFor { onAllNodesWithContentDescription("Undo") }
    }

    private fun ComposeTestRule.waitFor(
        nodes: ComposeTestRule.() -> SemanticsNodeInteractionCollection,
    ) = waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }

    private fun samplePdf(pages: Int): File {
        val file = File(fixtureDir, "uitest-sample.pdf")
        PDDocument().use { document ->
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 22f)
                    stream.newLineAtOffset(64f, 700f)
                    stream.showText("Smoke test page ${index + 1}")
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
