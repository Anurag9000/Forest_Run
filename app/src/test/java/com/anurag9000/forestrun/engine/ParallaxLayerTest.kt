package com.anurag9000.forestrun.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParallaxLayerTest {

    @Test
    fun `ordinary movement wraps exactly at bitmap seam`() {
        val layer = layer(width = 100, speedFraction = 1f)

        layer.update(deltaTime = 1f, gameScrollSpeed = 50f)
        assertEquals(-50f, layer.x, 0.0001f)

        layer.update(deltaTime = 1f, gameScrollSpeed = 50f)
        assertEquals(0f, layer.x, 0.0001f)
    }

    @Test
    fun `huge finite movement wraps in one bounded step`() {
        val layer = layer(width = 128, speedFraction = 1.5f)

        layer.update(deltaTime = Float.MAX_VALUE, gameScrollSpeed = Float.MAX_VALUE)

        assertTrue(layer.x.isFinite())
        assertTrue(layer.x > -128f)
        assertTrue(layer.x <= 0f)
    }

    @Test
    fun `invalid or reversing inputs are no ops`() {
        val layer = layer(width = 100, speedFraction = 1f)
        layer.update(0.5f, 40f)
        val before = layer.x

        layer.update(Float.NaN, 40f)
        layer.update(Float.POSITIVE_INFINITY, 40f)
        layer.update(-1f, 40f)
        layer.update(1f, Float.NaN)
        layer.update(1f, Float.POSITIVE_INFINITY)
        layer.update(1f, -40f)

        assertEquals(before, layer.x, 0f)
    }

    @Test
    fun `non finite external position assignment is ignored`() {
        val layer = layer(width = 100, speedFraction = 1f)
        layer.x = -25f

        layer.x = Float.NaN
        layer.x = Float.POSITIVE_INFINITY

        assertEquals(-25f, layer.x, 0f)
    }

    @Test
    fun `draw remains safe after modular movement`() {
        val layer = layer(width = 32, speedFraction = 1f)
        layer.update(Float.MAX_VALUE, Float.MAX_VALUE)
        val destination = Bitmap.createBitmap(64, 16, Bitmap.Config.ARGB_8888)

        layer.draw(Canvas(destination))

        assertTrue(layer.x.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative speed fraction is rejected`() {
        layer(width = 16, speedFraction = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite speed fraction is rejected`() {
        layer(width = 16, speedFraction = Float.NaN)
    }

    private fun layer(width: Int, speedFraction: Float): ParallaxLayer =
        ParallaxLayer(
            bitmap = Bitmap.createBitmap(width, 16, Bitmap.Config.ARGB_8888),
            speedFraction = speedFraction
        )
}
