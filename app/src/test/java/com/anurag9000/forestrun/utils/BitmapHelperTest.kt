package com.anurag9000.forestrun.utils

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BitmapHelperTest {
    @Test
    fun `valid placeholder uses exact strip geometry`() {
        val bitmap = BitmapHelper.buildPlaceholderStrip(
            frameW = 32,
            frameH = 24,
            frameCount = 4,
            baseColor = Color.GREEN
        )

        assertEquals(128, bitmap.width)
        assertEquals(24, bitmap.height)
    }

    @Test
    fun `minimum drawable frame geometry remains accepted`() {
        val bitmap = BitmapHelper.buildPlaceholderStrip(
            frameW = 17,
            frameH = 13,
            frameCount = 1,
            baseColor = Color.BLUE
        )

        assertEquals(17, bitmap.width)
        assertEquals(13, bitmap.height)
    }

    @Test
    fun `empty and inverted body geometry is rejected`() {
        listOf(
            Triple(16, 24, 1),
            Triple(32, 12, 1),
            Triple(32, 24, 0),
            Triple(32, 24, -1)
        ).forEach { (width, height, count) ->
            assertFailsWith<IllegalArgumentException> {
                BitmapHelper.buildPlaceholderStrip(width, height, count, Color.RED)
            }
        }
    }

    @Test
    fun `strip width multiplication cannot overflow`() {
        assertFailsWith<IllegalArgumentException> {
            BitmapHelper.buildPlaceholderStrip(
                frameW = Int.MAX_VALUE,
                frameH = 13,
                frameCount = Int.MAX_VALUE,
                baseColor = Color.RED
            )
        }
    }

    @Test
    fun `extreme but integer representable allocation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BitmapHelper.buildPlaceholderStrip(
                frameW = 8_192,
                frameH = 8_192,
                frameCount = 1,
                baseColor = Color.RED
            )
        }
    }
}
