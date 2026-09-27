package io.github.fanfeast.anonpdf.ui.editor

import android.graphics.Bitmap
import android.util.SizeF
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fanfeast.anonpdf.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorZoomTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val mode = mutableStateOf<EditorMode>(EditorMode.Inking(emptyList(), 0xff000000.toInt(), 0.005f))
    private val page = mutableStateOf(0)
    private var renderWidth = 0

    @Before fun openPreview() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setContent {
                MaterialTheme {
                    PreviewArea(
                        bitmap = bitmap, pageKey = page.value, busy = false, empty = false,
                        shownSize = SizeF(300f, 300f), referenceSize = SizeF(300f, 300f),
                        mode = mode.value, onModeChange = { mode.value = it },
                        onWidthChange = { renderWidth = it }, stepper = null,
                        modifier = Modifier.size(300.dp, 400.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun closePreview() { scenario.close() }

    @Test fun zoomAndPanKeepInkInPageCoordinates() {
        val originalPage = compose.onNodeWithContentDescription("Page preview").fetchSemanticsNode().boundsInRoot
        val originalRenderWidth = renderWidth
        repeat(2) { compose.onNodeWithContentDescription("Zoom in").performClick() }
        compose.onNodeWithText("200%").assertExists()
        compose.runOnIdle { assertTrue(renderWidth > originalRenderWidth) }
        compose.onNodeWithText("Move page").performClick()
        val viewport = compose.onNodeWithTag("Editor viewport")
        viewport.performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 0f), 100)
            up()
        }
        compose.runOnIdle { assertTrue((mode.value as EditorMode.Inking).strokes.isEmpty()) }
        compose.onNodeWithText("Resume editing").performClick()
        viewport.performTouchInput { swipe(center, center + Offset(40f, 30f), 500) }
        compose.runOnIdle {
            val stroke = (mode.value as EditorMode.Inking).strokes.single()
            // The viewport moved 60 screen pixels right at 2x magnification.
            assertEquals(0.5f - 60f / (originalPage.width * 2f), stroke.first().first, 0.015f)
            assertEquals(0.5f, stroke.first().second, 0.015f)
            assertEquals(40f / (originalPage.width * 2f), stroke.last().first - stroke.first().first, 0.015f)
            assertEquals(30f / (originalPage.height * 2f), stroke.last().second - stroke.first().second, 0.015f)
        }
        compose.onNodeWithText("200%").performClick()
        compose.onNodeWithText("100%").assertExists()
        compose.runOnIdle { assertEquals(1, (mode.value as EditorMode.Inking).strokes.size) }
    }

    @Test fun pinchDoesNotDrawAndNewPageResetsView() {
        compose.onNodeWithText("Move page").performClick()
        compose.onNodeWithTag("Editor viewport").performTouchInput {
            down(0, center - Offset(30f, 0f))
            down(1, center + Offset(30f, 0f))
            repeat(10) { index ->
                val distance = 30f + (index + 1) * 6f
                moveTo(0, center - Offset(distance, 0f))
                moveTo(1, center + Offset(distance, 0f))
            }
            up(0)
            up(1)
        }
        compose.onNodeWithText("100%").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue((mode.value as EditorMode.Inking).strokes.isEmpty())
            page.value = 1
        }
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithText("Move page").assertExists()
    }
}
