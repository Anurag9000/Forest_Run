package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpriteSheetTest {

    @Test
    fun `ordinary timing advances and preserves fractional remainder`() {
        val sheet = sheet(frameCount = 4, framesPerSec = 4f, looping = true)

        sheet.update(0.375f)

        assertEquals(1, sheet.currentFrame)
        assertEquals(0.125f, sheet.animationTimerForTest, 0.0001f)
    }

    @Test
    fun `invalid timing and fps values cannot poison or hang animation`() {
        val sheet = sheet(frameCount = 4, framesPerSec = 4f, looping = true)

        sheet.update(Float.NaN)
        sheet.update(Float.POSITIVE_INFINITY)
        sheet.update(-1f)
        assertEquals(0, sheet.currentFrame)
        assertEquals(0f, sheet.animationTimerForTest, 0f)

        sheet.framesPerSec = Float.POSITIVE_INFINITY
        sheet.update(1f)
        sheet.framesPerSec = Float.NaN
        sheet.update(1f)
        sheet.framesPerSec = -4f
        sheet.update(1f)

        assertEquals(0, sheet.currentFrame)
        assertEquals(0f, sheet.animationTimerForTest, 0f)
    }

    @Test
    fun `huge looping delta resolves in one bounded update`() {
        val sheet = sheet(frameCount = 7, framesPerSec = Float.MAX_VALUE, looping = true)

        sheet.update(Float.MAX_VALUE)

        assertTrue(sheet.currentFrame in 0 until sheet.frameCount)
        assertTrue(sheet.animationTimerForTest.isFinite())
        assertTrue(sheet.animationTimerForTest >= 0f)
    }

    @Test
    fun `huge non looping delta lands on final frame`() {
        val sheet = sheet(frameCount = 6, framesPerSec = Float.MAX_VALUE, looping = false)

        sheet.update(Float.MAX_VALUE)

        assertEquals(5, sheet.currentFrame)
        assertTrue(sheet.isFinished)
        assertEquals(0f, sheet.animationTimerForTest, 0f)
    }

    @Test
    fun `single frame sheet never advances`() {
        val sheet = sheet(frameCount = 1, framesPerSec = Float.MAX_VALUE, looping = true)

        sheet.update(Float.MAX_VALUE)

        assertEquals(0, sheet.currentFrame)
        assertFalse(sheet.isFinished)
    }

    @Test
    fun `invalid destination rectangle is ignored`() {
        val source = Bitmap.createBitmap(40, 10, Bitmap.Config.ARGB_8888)
        val sheet = SpriteSheet(source, frameCount = 4, framesPerSec = 4f)
        val destination = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destination)

        listOf(
            RectF(Float.NaN, 0f, 20f, 20f),
            RectF(10f, 10f, 10f, 30f),
            RectF(30f, 10f, 10f, 30f),
            RectF(0f, Float.NEGATIVE_INFINITY, 20f, 20f)
        ).forEach { invalid -> sheet.draw(canvas, invalid) }

        assertTrue(bitmapContainsOnlyTransparentPixels(destination))
    }

    @Test
    fun `copy shares bitmap but owns independent playback state`() {
        val original = sheet(frameCount = 4, framesPerSec = 4f, looping = true)
        val copy = original.copy()

        original.update(0.25f)

        assertTrue(original.bitmap === copy.bitmap)
        assertEquals(1, original.currentFrame)
        assertEquals(0, copy.currentFrame)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bitmap must contain at least one pixel per physical frame`() {
        SpriteSheet(
            bitmap = Bitmap.createBitmap(3, 10, Bitmap.Config.ARGB_8888),
            frameCount = 4,
            framesPerSec = 4f
        )
    }

    private fun sheet(frameCount: Int, framesPerSec: Float, looping: Boolean): SpriteSheet =
        SpriteSheet(
            bitmap = Bitmap.createBitmap(frameCount * 10, 12, Bitmap.Config.ARGB_8888),
            frameCount = frameCount,
            framesPerSec = framesPerSec,
            isLooping = looping
        )

    private fun bitmapContainsOnlyTransparentPixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.none { pixel -> pixel ushr 24 != 0 }
    }
}
