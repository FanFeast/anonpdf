package io.github.fanfeast.anonpdf.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import io.github.fanfeast.anonpdf.i18n.L10n
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.PdfForms
import io.github.fanfeast.anonpdf.ui.tools.ToolCatalog
import io.github.fanfeast.anonpdf.ui.tools.ToolId
import io.github.fanfeast.anonpdf.ui.tools.ToolInput
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Every screen, panel and tool of the desktop app, in every language, at a
 * desktop width and at roughly a phone's width.
 *
 * Two things are checked in each language: that no text is cut off, and that
 * the app still works when driven by its translated labels — a form is filled
 * and saved, and a tool runs to the end. Screenshots of everything land in
 * desktop/build/locale-sweep for a person to look over.
 */
@OptIn(ExperimentalTestApi::class)
class LocaleSweepTest {

    private lateinit var scratch: File
    private lateinit var formPdf: File
    private lateinit var lockedPdf: File
    private val savedLocale = Locale.getDefault()
    private val shots = File("build/locale-sweep").apply { mkdirs() }
    private val overflows = mutableListOf<String>()

    @Before
    fun setUp() {
        scratch = Files.createTempDirectory("locale-sweep").toFile()
        System.setProperty("java.io.tmpdir", scratch.absolutePath)
        formPdf = File(scratch, "form.pdf")
        PDDocument().use { document ->
            repeat(3) { document.addPage(PDPage(PDRectangle.A4)) }
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
                page = document.getPage(0)
                document.getPage(0).annotations.add(this)
            }
            form.fields.add(name)
            // So the save card has hidden information to offer to remove.
            document.documentInformation.apply {
                author = "Jane Doe"
                creator = "Some Word Processor"
                creationDate = Calendar.getInstance()
            }
            document.save(formPdf)
        }
        lockedPdf = File(scratch, "locked.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.protect(StandardProtectionPolicy("owner", "secret", AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(lockedPdf)
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    private fun eachLanguage(block: (String) -> Unit) {
        // -Dsweep.langs=pt,ru runs just those, for chasing one language's failure.
        val only = System.getProperty("sweep.langs")?.split(',')?.toSet()
        L10n.languages.filter { only == null || it.first in only }.forEach { (tag, _) ->
            Locale.setDefault(Locale.forLanguageTag(tag))
            block(tag)
        }
        Locale.setDefault(savedLocale)
    }

    /** Records any text that does not fit where it is drawn, and saves a picture of the screen. */
    private fun ComposeUiTest.check(name: String) {
        waitForIdle()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                val layout = results.firstOrNull() ?: return@forEach
                val text = layout.layoutInput.text.text
                // File names are the user's own and may be long; they ellipsize by design.
                // A line running past the text's own box, an ellipsis, or lines
                // dropped by a line limit. (didOverflowWidth is no use here: it
                // compares against the widest the text was allowed, not what it got.)
                val last = layout.lineCount - 1
                val cut = (0..last).any { layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f } ||
                    layout.isLineEllipsized(last) ||
                    layout.multiParagraph.didExceedMaxLines
                if (cut && !text.contains(".pdf") && !text.contains(".txt")) {
                    overflows += "${Locale.getDefault().toLanguageTag()} · $name · \"$text\""
                }
            }
        // A dialog is a second root; save each.
        val roots = onAllNodes(isRoot())
        roots.fetchSemanticsNodes().indices.forEach { i ->
            val suffix = if (i == 0) "" else "-$i"
            ImageIO.write(roots[i].captureToImage().toAwtImage(), "png", File(shots, "${Locale.getDefault().toLanguageTag()}-$name$suffix.png"))
        }
    }

    private fun ComposeUiTest.exists(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitFor(text: String) {
        try {
            waitUntil(timeoutMillis = TIMEOUT_MS) { exists(text) }
        } catch (e: Throwable) {
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(shots, "${Locale.getDefault().toLanguageTag()}-TIMEOUT.png"))
            throw AssertionError("${Locale.getDefault().toLanguageTag()}: never saw \"$text\"", e)
        }
    }

    /**
     * Presses the control labelled [text] through its accessibility click action,
     * as TalkBack would. A mouse click needs the control on screen, and in the
     * short phone-height drawer that depends on scroll position, which is not
     * what this test is about. (Reaching the second row by scrolling was checked
     * separately: the drawer scrolls far enough to show it whole.)
     */
    private fun ComposeUiTest.click(text: String) {
        val clickable = onAllNodes(hasText(text) and hasClickAction())
        if (clickable.fetchSemanticsNodes().isNotEmpty()) {
            clickable[0].performSemanticsAction(SemanticsActions.OnClick)
        } else {
            onAllNodes(hasText(text), useUnmergedTree = true)[0].performClick()
        }
        waitForIdle()
    }

    /** Opens an editor tool, swiping through the tool pages on a narrow window to reach it. */
    private fun ComposeUiTest.openTool(label: String) {
        repeat(4) {
            if (exists(label)) return click(label)
            // The pager is the lowest horizontally scrolling area in the drawer.
            val scrollers = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            val lowest = scrollers.fetchSemanticsNodes().withIndex().maxByOrNull { it.value.boundsInRoot.top }
                ?: error("no tool pager")
            scrollers[lowest.index].performTouchInput { swipeLeft() }
            waitForIdle()
        }
        error("tool \"$label\" not found")
    }

    @Test
    fun `the start page and every tool screen fit in every language`() = eachLanguage { _ ->
        for ((width, tag) in listOf(WIDE to "wide", NARROW to "narrow")) {
            runDesktopComposeUiTest(width, HEIGHT) {
                setContent { DesktopApp(DesktopNavigator()) }
                waitFor(S.whatWouldYouLikeToDo)
                check("start-$tag")
            }
        }
        ToolId.entries.forEach { id ->
            val spec = ToolCatalog.spec(id)
            runDesktopComposeUiTest(WIDE, HEIGHT) {
                val navigator = DesktopNavigator()
                navigator.show(Pane.Tool(id, formPdf.takeIf { spec.input != ToolInput.IMAGES }))
                setContent { DesktopApp(navigator) }
                waitForIdle()
                Thread.sleep(400)
                check("tool-${id.name.lowercase()}")
            }
        }
    }

    @Test
    fun `a protected file asks for its password in every language`() = eachLanguage { _ ->
        runDesktopComposeUiTest(WIDE, HEIGHT) {
            setContent { DesktopApp(DesktopNavigator(lockedPdf)) }
            waitFor(S.passwordNeeded)
            check("password")
        }
    }

    @Test
    fun `every editor panel fits, and a form is filled and saved, in every language`() = eachLanguage { lang ->
        for ((width, tag) in listOf(WIDE to "wide", NARROW to "narrow")) {
            runDesktopComposeUiTest(width, HEIGHT) {
                setContent { DesktopApp(DesktopNavigator(formPdf)) }
                waitFor(S.hasFormPrompt)
                check("editor-$tag")

                val panels = listOf(
                    S.toolCrop to "crop",
                    S.toolResize to "resize",
                    S.toolWatermark to "watermark",
                    S.toolNumbers to "numbers",
                    S.toolText to "text",
                    S.toolWhiteout to "whiteout",
                    S.toolHighlight to "highlight",
                    S.toolDraw to "draw",
                )
                for ((label, name) in panels) {
                    openTool(label)
                    check("panel-$name-$tag")
                    if (name == "watermark") {
                        // Text the built-in fonts cannot draw is flagged as it is typed.
                        onAllNodes(hasSetTextAction())[0].performTextInput("Тест 日本")
                        waitFor(S.charactersNotDrawable)
                        check("panel-watermark-undrawable-$tag")
                    }
                    if (name == "numbers") {
                        // Only a preset the fonts can stamp is offered.
                        assertEquals(isDrawable(S.numberPresetPage), exists(S.numberPresetPage))
                    }
                    if (name == "resize") {
                        click(S.sizePercent)
                        check("panel-resize-percent-$tag")
                    }
                    click(S.cancel)
                }

                // Fill the form through the translated panel, then save.
                click(S.hasFormPrompt)
                check("panel-form-$tag")
                onAllNodes(hasSetTextAction())[0].performTextInput("Jane $lang")
                click(S.apply)
                waitFor(S.edits(1))
                click(S.edits(1))
                check("pending-$tag")
                click(S.close)

                click(S.saveAs)
                waitFor(S.done)
                check("saved-$tag")
                click(S.removeHiddenInfo)
                check("saved-hidden-$tag")

                val saved = File(scratch, "out").listFiles { f -> f.name.endsWith("-edited.pdf") }!!.single()
                assertEquals("Jane $lang", PdfForms.read(saved).fields.single { it.name == "full_name" }.value)
                saved.delete()
            }
        }
    }

    @Test
    fun `a tool runs to the end in every language`() = eachLanguage { _ ->
        runDesktopComposeUiTest(WIDE, HEIGHT) {
            val navigator = DesktopNavigator()
            navigator.show(Pane.Tool(ToolId.EXTRACT_TEXT, formPdf))
            setContent { DesktopApp(navigator) }
            waitFor(S.extractTextTitle)
            onNode(hasText(S.extractTextTitle) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performScrollTo()
                .performClick()
            waitFor(S.done)
            check("tool-extract-text-done")
        }
    }

    @After
    fun reportOverflow() {
        assertTrue("Text cut off:\n" + overflows.joinToString("\n"), overflows.isEmpty())
    }

    private fun isDrawable(text: String) = io.github.fanfeast.anonpdf.pdf.FieldFormat.drawable(text) == text

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val WIDE = 1440
        /** The desktop sidebar plus about a phone's width for the editor. */
        const val NARROW = 660
        const val HEIGHT = 900
    }
}
