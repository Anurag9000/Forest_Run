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
class CostumeOverlayTest {

    @Test
    fun `invalid time does not poison later costume animation`() {
        val overlay = CostumeOverlay()

        overlay.update(Float.NaN)
        overlay.update(Float.POSITIVE_INFINITY)
        overlay.update(-1f)
        assertEquals(0f, overlay.elapsedForTest, 0f)

        overlay.update(0.025f)
        assertEquals(0.025f, overlay.elapsedForTest, 0.0001f)
    }

    @Test
    fun `extreme finite time is capped to one presentation frame`() {
        val overlay = CostumeOverlay()

        overlay.update(Float.MAX_VALUE)

        assertEquals(0.05f, overlay.elapsedForTest, 0.0001f)
    }

    @Test
    fun `valid frame repairs a poisoned elapsed phase`() {
        val overlay = CostumeOverlay()
        setPrivateFloat(overlay, "elapsed", Float.NaN)

        overlay.update(10f)

        assertEquals(0.05f, overlay.elapsedForTest, 0.0001f)
    }

    @Test
    fun `invalid body rectangles are ignored`() {
        val overlay = CostumeOverlay()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val motion = PlayerSecondaryMotionState(0f, 0f, 0f, 0f, 0f)

        listOf(
            RectF(Float.NaN, 0f, 80f, 100f),
            RectF(20f, 20f, 20f, 100f),
            RectF(80f, 20f, 20f, 100f),
            RectF(20f, Float.NEGATIVE_INFINITY, 80f, 100f)
        ).forEach { invalid ->
            overlay.draw(
                canvas = canvas,
                bodyRect = invalid,
                style = CostumeStyle.MOON_CAPE,
                state = PlayerState.RUNNING,
                isInvincible = false,
                motion = motion
            )
        }

        assertTrue(bitmapContainsOnlyTransparentPixels(bitmap))
    }

    @Test
    fun `every costume style accepts malformed secondary motion safely`() {
        val overlay = CostumeOverlay()
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val malformedMotion = PlayerSecondaryMotionState(
            bodyTiltDegrees = Float.NaN,
            bodyLiftPx = Float.POSITIVE_INFINITY,
            costumeSwingPx = Float.NEGATIVE_INFINITY,
            costumeTrailLiftPx = Float.NaN,
            headOffsetPx = Float.POSITIVE_INFINITY
        )
        val body = RectF(72f, 40f, 184f, 224f)

        CostumeStyle.entries.forEach { style ->
            PlayerState.entries.forEach { state ->
                overlay.draw(
                    canvas = canvas,
                    bodyRect = body,
                    style = style,
                    state = state,
                    isInvincible = true,
                    motion = malformedMotion
                )
            }
        }

        assertTrue(overlay.elapsedForTest.isFinite())
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
