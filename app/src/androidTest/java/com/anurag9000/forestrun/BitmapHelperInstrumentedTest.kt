package com.anurag9000.forestrun

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anurag9000.forestrun.utils.BitmapHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapHelperInstrumentedTest {
    @Test
    fun placeholderStripPaintsEachFrameAndPulsesBrightness() {
        val base = Color.rgb(100, 120, 140)
        val bitmap = BitmapHelper.buildPlaceholderStrip(
            frameW = 32,
            frameH = 24,
            frameCount = 4,
            baseColor = base
        )

        try {
            assertEquals(128, bitmap.width)
            assertEquals(24, bitmap.height)
            val frameColors = IntArray(4) { frame ->
                bitmap.getPixel(frame * 32 + 16, 16)
            }
            assertEquals(base, frameColors[0])
            assertEquals(base, frameColors[2])
            assertNotEquals(frameColors[0], frameColors[1])
            assertNotEquals(frameColors[0], frameColors[3])
            assertNotEquals(frameColors[1], frameColors[3])
        } finally {
            bitmap.recycle()
        }
    }
}
