package io.github.fanfeast.anonpdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** What the shared engine tests (shared-engine/src/deviceTest) need from Android. */
object TestPlatform {
    const val WHITE = 0xFFFFFFFF.toInt()

    fun scratchDir(name: String): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)

    /** An image from packed ARGB [pixels], row by row. */
    fun imageOf(width: Int, height: Int, pixels: IntArray, hasAlpha: Boolean): PageImage =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
            .apply { setHasAlpha(hasAlpha) }

    fun readImage(file: File): PageImage? = BitmapFactory.decodeFile(file.absolutePath)
}

/** The pixel at [x], [y] as packed ARGB. */
fun PageImage.argbAt(x: Int, y: Int): Int = getPixel(x, y)
