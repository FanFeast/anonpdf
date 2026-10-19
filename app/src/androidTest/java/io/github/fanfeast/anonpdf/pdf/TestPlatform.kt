package io.github.fanfeast.anonpdf.pdf

import androidx.core.graphics.scale
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** What the shared engine tests (shared-engine/src/deviceTest) need from Android. */
object TestPlatform {
    const val WHITE = 0xFFFFFFFF.toInt()

    fun scratchDir(name: String): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
}

/** The pixel at [x], [y] as packed ARGB. */
fun PageImage.argbAt(x: Int, y: Int): Int = getPixel(x, y)

fun PageImage.scaledTo(width: Int, height: Int): PageImage = scale(width, height)
