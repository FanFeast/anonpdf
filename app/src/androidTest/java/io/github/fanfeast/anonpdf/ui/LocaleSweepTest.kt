package io.github.fanfeast.anonpdf.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.fanfeast.anonpdf.MainActivity
import io.github.fanfeast.anonpdf.i18n.L10n
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.ui.about.AboutScreen
import io.github.fanfeast.anonpdf.ui.browse.BrowseScreen
import io.github.fanfeast.anonpdf.ui.home.HomeScreen
import io.github.fanfeast.anonpdf.ui.settings.SettingsScreen
import io.github.fanfeast.anonpdf.ui.theme.AnonPdfTheme
import io.github.fanfeast.anonpdf.ui.viewer.ViewerScreen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The Android-only screens — home, browse, viewer, settings, about — in every
 * language, at the size of the phone the test runs on. Fails on any text that
 * is cut off; saves a screenshot of each to the app's external files directory
 * (locale-sweep/) for a person to look over.
 */
@RunWith(AndroidJUnit4::class)
class LocaleSweepTest {

    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val savedLocale = Locale.getDefault()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shots = File(context.getExternalFilesDir(null), "locale-sweep").apply { mkdirs() }
    private val overflows = mutableListOf<String>()
    private lateinit var pdf: Uri
    // Named, not inline: D8 merges the empty lambdas of these test-only composable
    // blocks into shared singletons of the wrong function type.
    private val noop: () -> Unit = {}
    private val noopUri: (Uri) -> Unit = {}
    private val noopTool: (io.github.fanfeast.anonpdf.ui.tools.ToolId) -> Unit = {}

    @Before fun setUp() {
        val file = File(context.cacheDir, "sweep.pdf")
        PDDocument().use { document ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 24f)
                    stream.newLineAtOffset(64f, 700f)
                    stream.showText("Page ${index + 1}")
                    stream.endText()
                }
            }
            document.save(file)
        }
        pdf = Uri.fromFile(file)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After fun tearDown() {
        Locale.setDefault(savedLocale)
        scenario.close()
    }

    private fun show(name: String, waitFor: String, content: @Composable () -> Unit) {
        scenario.onActivity { it.setContent { AnonPdfTheme { content() } } }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(hasText(waitFor, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        check(name)
    }

    private fun check(name: String) {
        val tag = Locale.getDefault().toLanguageTag()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                val layout = results.firstOrNull() ?: return@forEach
                val text = layout.layoutInput.text.text
                val last = layout.lineCount - 1
                val cut = (0..last).any { layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f } ||
                    layout.isLineEllipsized(last) ||
                    layout.multiParagraph.didExceedMaxLines
                if (cut && !text.contains(".pdf")) overflows += "$tag · $name · \"$text\""
            }
        val image = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(shots, "$tag-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyAndroidScreenFitsInEveryLanguage() {
        for ((tag, _) in L10n.languages) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            show("home", S.openAPdf) {
                HomeScreen(
                    onOpenDocument = noopUri, onEditDocument = noop, onBrowse = noop,
                    onOpenTool = noopTool, onOpenAbout = noop, onOpenSettings = noop,
                )
            }
            show("browse", S.browse) { BrowseScreen(onOpen = noopUri, onBack = noop) }
            show("viewer", "1 / 2") { ViewerScreen(uri = pdf, onBack = noop, onEdit = noop) }
            show("settings", S.rememberRecent) { SettingsScreen(onBack = noop) }
            show("about", S.aboutTagline) { AboutScreen(onBack = noop) }
        }
        assertTrue("Text cut off:\n" + overflows.joinToString("\n"), overflows.isEmpty())
    }
}
