package com.anurag9000.forestrun.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceManagerTest {

    @Test
    fun `invalid time does not poison later blink animation`() {
        val face = FaceManager()

        face.update(Float.NaN)
        face.update(Float.POSITIVE_INFINITY)
        face.update(-1f)
        assertEquals(0f, face.blinkTimerForTest, 0f)

        face.update(0.25f)
        assertEquals(0.25f, face.blinkTimerForTest, 0.0001f)
    }

    @Test
    fun `extreme finite time wraps to a finite phase`() {
        val face = FaceManager()

        face.update(Float.MAX_VALUE)

        assertTrue(face.blinkTimerForTest.isFinite())
        assertTrue(face.blinkTimerForTest >= 0f)
        assertTrue(face.blinkTimerForTest < 60f)
    }

    @Test
    fun `invalid body rectangle is rejected without touching canvas`() {
        val face = FaceManager()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val motion = PlayerSecondaryMotionState(0f, 0f, 0f, 0f, 0f)

        face.draw(
            canvas = canvas,
            bodyRect = RectF(Float.NaN, 0f, 50f, 100f),
            state = PlayerState.RUNNING,
            velocityY = Float.NaN,
            isInvincible = false,
            motion = motion
        )

        assertTrue(bitmapContainsOnlyTransparentPixels(bitmap))
    }

    @Test
    fun `non finite velocity and head offset fall back to stable geometry`() {
        val face = FaceManager()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val malformedMotion = PlayerSecondaryMotionState(
            bodyTiltDegrees = 0f,
            bodyLiftPx = 0f,
            costumeSwingPx = 0f,
            costumeTrailLiftPx = 0f,
            headOffsetPx = Float.NaN
        )

        face.draw(
            canvas = canvas,
            bodyRect = RectF(24f, 14f, 104f, 124f),
            state = PlayerState.RUNNING,
            velocityY = Float.POSITIVE_INFINITY,
            isInvincible = false,
            motion = malformedMotion
        )

        assertTrue(bitmapContainsVisiblePixel(bitmap))
    }

    private fun bitmapContainsOnlyTransparentPixels(bitmap: Bitmap): Boolean =
        !bitmapContainsVisiblePixel(bitmap)

    private fun bitmapContainsVisiblePixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }
}
