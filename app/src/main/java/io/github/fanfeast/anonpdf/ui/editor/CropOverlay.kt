package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import io.github.fanfeast.anonpdf.pdf.CropInsets
import kotlin.math.abs

/** Which part of the crop rectangle a touch grabbed. */
private enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    LEFT, TOP, RIGHT, BOTTOM,
    MOVE,
}

/** Never let the crop shrink below this fraction of the page in either axis. */
private const val MIN_SPAN = 0.12f

/** How close, in pixels, a touch has to be to grab an edge or corner. */
private const val GRAB_RADIUS = 56f

/**
 * A draggable crop rectangle drawn over a page preview.
 *
 * Works in fractions of the page rather than pixels, so the rectangle survives
 * rotation, a resized preview, and the trip into [CropInsets] unchanged.
 */
@Composable
fun CropOverlay(
    insets: CropInsets,
    onChange: (CropInsets) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture handler is created once, so it must read the live value rather
    // than the one captured when it was set up.
    val current by rememberUpdatedState(insets)
    val onChangeNow by rememberUpdatedState(onChange)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val handle = handleAt(down.position, size, current) ?: return@awaitEachGesture
                down.consume()
                var previous = down.position

                drag(down.id) { change ->
                    val delta = change.position - previous
                    previous = change.position
                    onChangeNow(
                        resize(
                            insets = current,
                            handle = handle,
                            position = change.position,
                            delta = delta,
                            size = size,
                        ),
                    )
                    change.consume()
                }
            }
        },
    ) {
        val rect = Rect(
            left = insets.left * size.width,
            top = insets.top * size.height,
            right = size.width - insets.right * size.width,
            bottom = size.height - insets.bottom * size.height,
        )

        // Dim what is being cut away, in four bands around the keep-rectangle.
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, size = Size(size.width, rect.top))
        drawRect(
            shade,
            topLeft = Offset(0f, rect.bottom),
            size = Size(size.width, size.height - rect.bottom),
        )
        drawRect(
            shade,
            topLeft = Offset(0f, rect.top),
            size = Size(rect.left, rect.height),
        )
        drawRect(
            shade,
            topLeft = Offset(rect.right, rect.top),
            size = Size(size.width - rect.right, rect.height),
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 2f),
        )

        // Rule-of-thirds guides, the usual cue that this is a crop tool.
        val guide = Color.White.copy(alpha = 0.35f)
        for (step in 1..2) {
            val x = rect.left + rect.width * step / 3f
            val y = rect.top + rect.height * step / 3f
            drawLine(guide, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = 1f)
            drawLine(guide, Offset(rect.left, y), Offset(rect.right, y), strokeWidth = 1f)
        }

        // Corner grips.
        val grip = 22f
        val thickness = 5f
        listOf(
            Offset(rect.left, rect.top) to Pair(1f, 1f),
            Offset(rect.right, rect.top) to Pair(-1f, 1f),
            Offset(rect.left, rect.bottom) to Pair(1f, -1f),
            Offset(rect.right, rect.bottom) to Pair(-1f, -1f),
        ).forEach { (corner, direction) ->
            drawLine(
                Color.White,
                corner,
                Offset(corner.x + grip * direction.first, corner.y),
                strokeWidth = thickness,
            )
            drawLine(
                Color.White,
                corner,
                Offset(corner.x, corner.y + grip * direction.second),
                strokeWidth = thickness,
            )
        }
    }
}

private fun handleAt(
    position: Offset,
    size: IntSize,
    insets: CropInsets,
): CropHandle? {
    if (size.width == 0 || size.height == 0) return null
    val left = insets.left * size.width
    val top = insets.top * size.height
    val right = size.width - insets.right * size.width
    val bottom = size.height - insets.bottom * size.height

    val nearLeft = abs(position.x - left) < GRAB_RADIUS
    val nearRight = abs(position.x - right) < GRAB_RADIUS
    val nearTop = abs(position.y - top) < GRAB_RADIUS
    val nearBottom = abs(position.y - bottom) < GRAB_RADIUS

    // Corners win over edges, which win over the interior.
    return when {
        nearLeft && nearTop -> CropHandle.TOP_LEFT
        nearRight && nearTop -> CropHandle.TOP_RIGHT
        nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
        nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
        nearLeft && position.y in top..bottom -> CropHandle.LEFT
        nearRight && position.y in top..bottom -> CropHandle.RIGHT
        nearTop && position.x in left..right -> CropHandle.TOP
        nearBottom && position.x in left..right -> CropHandle.BOTTOM
        position.x in left..right && position.y in top..bottom -> CropHandle.MOVE
        else -> null
    }
}

private fun resize(
    insets: CropInsets,
    handle: CropHandle,
    position: Offset,
    delta: Offset,
    size: IntSize,
): CropInsets {
    if (size.width == 0 || size.height == 0) return insets
    val fractionX = (position.x / size.width).coerceIn(0f, 1f)
    val fractionY = (position.y / size.height).coerceIn(0f, 1f)

    fun withLeft(value: Float) =
        insets.copy(left = value.coerceIn(0f, 1f - insets.right - MIN_SPAN))

    fun withRight(value: Float) =
        insets.copy(right = value.coerceIn(0f, 1f - insets.left - MIN_SPAN))

    fun withTop(value: Float) =
        insets.copy(top = value.coerceIn(0f, 1f - insets.bottom - MIN_SPAN))

    fun withBottom(value: Float) =
        insets.copy(bottom = value.coerceIn(0f, 1f - insets.top - MIN_SPAN))

    return when (handle) {
        CropHandle.LEFT -> withLeft(fractionX)
        CropHandle.RIGHT -> withRight(1f - fractionX)
        CropHandle.TOP -> withTop(fractionY)
        CropHandle.BOTTOM -> withBottom(1f - fractionY)

        CropHandle.TOP_LEFT -> withLeft(fractionX).let { stepped ->
            stepped.copy(top = fractionY.coerceIn(0f, 1f - stepped.bottom - MIN_SPAN))
        }
        CropHandle.TOP_RIGHT -> withRight(1f - fractionX).let { stepped ->
            stepped.copy(top = fractionY.coerceIn(0f, 1f - stepped.bottom - MIN_SPAN))
        }
        CropHandle.BOTTOM_LEFT -> withLeft(fractionX).let { stepped ->
            stepped.copy(bottom = (1f - fractionY).coerceIn(0f, 1f - stepped.top - MIN_SPAN))
        }
        CropHandle.BOTTOM_RIGHT -> withRight(1f - fractionX).let { stepped ->
            stepped.copy(bottom = (1f - fractionY).coerceIn(0f, 1f - stepped.top - MIN_SPAN))
        }

        // Slide the whole rectangle, keeping its size, stopping at the edges.
        CropHandle.MOVE -> {
            val shiftX = (delta.x / size.width)
                .coerceIn(-insets.left, insets.right)
            val shiftY = (delta.y / size.height)
                .coerceIn(-insets.top, insets.bottom)
            insets.copy(
                left = insets.left + shiftX,
                right = insets.right - shiftX,
                top = insets.top + shiftY,
                bottom = insets.bottom - shiftY,
            )
        }
    }
}
