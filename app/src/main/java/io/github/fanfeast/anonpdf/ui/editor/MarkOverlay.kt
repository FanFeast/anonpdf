package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import io.github.fanfeast.anonpdf.pdf.MarkRect
import io.github.fanfeast.anonpdf.pdf.TextMark

/**
 * Drag out a rectangle over the page — for a white-out block or a highlight.
 *
 * Everything is in page fractions rather than pixels, so the rectangle means the
 * same thing whatever size the preview happens to be, and lands correctly on a
 * page that is later rotated or refitted.
 */
@Composable
fun RectMarkOverlay(
    rect: MarkRect?,
    colour: Color,
    opacity: Float,
    onChange: (MarkRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(rect)
    val onChangeNow by rememberUpdatedState(onChange)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val startX = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val startY = (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                val existing = current

                // Touching inside an existing rectangle moves it; anywhere else
                // starts a new one.
                val movingFrom = existing?.takeIf {
                    startX in it.left..it.right && startY in it.top..it.bottom
                }
                var lastX = startX
                var lastY = startY
                if (movingFrom == null) {
                    onChangeNow(MarkRect(startX, startY, startX, startY))
                }
                down.consume()

                drag(down.id) { change ->
                    val x = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val y = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    if (movingFrom != null) {
                        onChangeNow(
                            (current ?: movingFrom).movedBy(x - lastX, y - lastY),
                        )
                    } else {
                        onChangeNow(MarkRect(startX, startY, x, y).tidied())
                    }
                    lastX = x
                    lastY = y
                    change.consume()
                }
            }
        },
    ) {
        val tidy = rect?.tidied() ?: return@Canvas
        val topLeft = Offset(tidy.left * size.width, tidy.top * size.height)
        val boxSize = Size(tidy.width * size.width, tidy.height * size.height)
        drawRect(colour.copy(alpha = opacity), topLeft = topLeft, size = boxSize)
        drawRect(
            color = Color(0xFF2F81F7),
            topLeft = topLeft,
            size = boxSize,
            style = Stroke(width = 3f),
        )
    }
}

/**
 * Freehand ink over the page, captured in page fractions.
 *
 * The stroke being drawn is held here rather than in the caller's state, and only
 * reported once the finger lifts. That keeps the gesture handler — which is set up
 * once and cannot see later values — from having to read the growing stroke back
 * out of the caller.
 */
@Composable
fun InkOverlay(
    strokes: List<List<Pair<Float, Float>>>,
    colour: Color,
    strokeWidthRatio: Float,
    onStroke: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = remember { mutableStateListOf<Pair<Float, Float>>() }
    val report by rememberUpdatedState(onStroke)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                live.clear()
                live.add(down.position.normalisedIn(size))
                down.consume()

                drag(down.id) { change ->
                    live.add(change.position.normalisedIn(size))
                    change.consume()
                }

                // A tap is not a stroke.
                if (live.size > 1) report(live.toList())
                live.clear()
            }
        },
    ) {
        val width = strokeWidthRatio * minOf(size.width, size.height)
        (strokes + listOf(live.toList())).forEach { stroke ->
            for (index in 1 until stroke.size) {
                drawLine(
                    color = colour,
                    start = Offset(
                        stroke[index - 1].first * size.width,
                        stroke[index - 1].second * size.height,
                    ),
                    end = Offset(
                        stroke[index].first * size.width,
                        stroke[index].second * size.height,
                    ),
                    strokeWidth = width.coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Tap the page to choose where a text block goes, then drag it to nudge it.
 *
 * The text itself is drawn where it will land, at the size it will be — the point
 * size means nothing to anyone until they can see it against the page. Placement
 * and line spacing mirror the exporter: the anchor is the block's top-left corner
 * and lines advance by 1.2 times the point size.
 */
@Composable
fun TextPlacementOverlay(
    mark: TextMark,
    placed: Boolean,
    pageHeightPoints: Float,
    onPlace: (Pair<Float, Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onPlaceNow by rememberUpdatedState(onPlace)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .pointerInput("tap") {
                detectTapGestures { offset -> onPlaceNow(offset.normalisedIn(size)) }
            }
            .pointerInput("drag") {
                detectDragGestures(
                    onDrag = { change, _ -> onPlaceNow(change.position.normalisedIn(size)) },
                )
            },
    ) {
        if (!placed) return@Canvas
        val anchor = Offset(mark.left * size.width, mark.top * size.height)

        if (mark.text.isNotBlank() && pageHeightPoints > 0f) {
            val fontPx = mark.fontSize * (size.height / pageHeightPoints)
            drawText(
                textMeasurer = measurer,
                text = mark.text,
                topLeft = anchor,
                style = TextStyle(
                    color = Color(mark.color),
                    fontSize = fontPx.toSp(),
                    lineHeight = (fontPx * 1.2f).toSp(),
                    fontWeight = if (mark.bold) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }

        // A corner bracket, since this marks the top-left of the text, not its middle.
        val arm = 26f
        val guide = Color(0xFF2F81F7)
        drawLine(guide, anchor, Offset(anchor.x + arm, anchor.y), strokeWidth = 4f)
        drawLine(guide, anchor, Offset(anchor.x, anchor.y + arm), strokeWidth = 4f)
        drawCircle(guide.copy(alpha = 0.25f), radius = 16f, center = anchor)
    }
}

private fun Offset.normalisedIn(size: IntSize): Pair<Float, Float> = Pair(
    if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f,
    if (size.height > 0) (y / size.height).coerceIn(0f, 1f) else 0f,
)
