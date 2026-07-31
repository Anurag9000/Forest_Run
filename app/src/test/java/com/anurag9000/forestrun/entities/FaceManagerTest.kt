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

        face.update(0.025f)
        assertEquals(0.025f, face.blinkTimerForTest, 0.0001f)
    }

    @Test
    fun `extreme finite time is capped to one presentation frame`() {
        val face = FaceManager()

        face.update(Float.MAX_VALUE)

        assertEquals(0.05f, face.blinkTimerForTest, 0.0001f)
    }

    @Test
    fun `valid frame repairs a poisoned blink phase`() {
        val face = FaceManager()
        setPrivateFloat(face, "blinkTimer", Float.NaN)

        face.update(10f)

        assertEquals(0.05f, face.blinkTimerForTest, 0.0001f)
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
    fun `non finite velocity and head offset draw without poisoning animation state`() {
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

        face.update(0.025f)
        face.draw(
            canvas = canvas,
            bodyRect = RectF(24f, 14f, 104f, 124f),
            state = PlayerState.RUNNING,
            velocityY = Float.POSITIVE_INFINITY,
            isInvincible = false,
            motion = malformedMotion
        )

        assertEquals(0.025f, face.blinkTimerForTest, 0.0001f)
        assertTrue(face.blinkTimerForTest.isFinite())
    }

    private fun setPrivateFloat(target: Any, name: String, value: Float) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(target, value)
    }

    private fun bitmapContainsOnlyTransparentPixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.none { pixel -> pixel ushr 24 != 0 }
    }
}
