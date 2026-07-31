package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpriteSizingTest {

    @Test
    fun `valid dimensions preserve authored aspect ratio`() {
        val wide = sheet(width = 24, height = 12)
        val tall = sheet(width = 12, height = 24)

        assertEquals(200f, SpriteSizing.widthForHeight(wide, 100f), 0.0001f)
        assertEquals(200f, SpriteSizing.heightForWidth(tall, 100f), 0.0001f)
    }

    @Test
    fun `small or non positive dimensions collapse to caller minimum`() {
        val sheet = sheet(width = 20, height = 10)

        assertEquals(32f, SpriteSizing.widthForHeight(sheet, 4f, minWidth = 32f), 0f)
        assertEquals(28f, SpriteSizing.heightForWidth(sheet, 0f, minHeight = 28f), 0f)
        assertEquals(28f, SpriteSizing.heightForWidth(sheet, -10f, minHeight = 28f), 0f)
    }

    @Test
    fun `non finite dimensions cannot leak into entity geometry`() {
        val sheet = sheet(width = 20, height = 10)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(36f, SpriteSizing.widthForHeight(sheet, invalid, minWidth = 36f), 0f)
            assertEquals(44f, SpriteSizing.heightForWidth(sheet, invalid, minHeight = 44f), 0f)
        }
    }

    @Test
    fun `invalid minima use a positive finite default`() {
        val sheet = sheet(width = 20, height = 10)

        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { invalidMinimum ->
            val width = SpriteSizing.widthForHeight(sheet, Float.NaN, invalidMinimum)
            val height = SpriteSizing.heightForWidth(sheet, Float.NaN, invalidMinimum)

            assertEquals(1f, width, 0f)
            assertEquals(1f, height, 0f)
            assertTrue(width.isFinite() && width > 0f)
            assertTrue(height.isFinite() && height > 0f)
        }
    }

    @Test
    fun `overflowing aspect arithmetic falls back instead of returning infinity`() {
        val wide = sheet(width = 20, height = 10)
        val tall = sheet(width = 10, height = 20)

        assertEquals(48f, SpriteSizing.widthForHeight(wide, Float.MAX_VALUE, 48f), 0f)
        assertEquals(52f, SpriteSizing.heightForWidth(tall, Float.MAX_VALUE, 52f), 0f)
    }

    private fun sheet(width: Int, height: Int): SpriteSheet =
        SpriteSheet(
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
            frameCount = 1,
            framesPerSec = 1f
        )
}
