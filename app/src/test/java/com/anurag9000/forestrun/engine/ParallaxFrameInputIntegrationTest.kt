package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParallaxFrameInputIntegrationTest {

    @Test
    fun `malformed frame inputs cannot mutate coordinator or layers`() {
        val background = ParallaxBackground(160, 90)
        background.setBloomState(isActive = true, activationLevel = 1f)
        val before = snapshot(background)

        val malformed = listOf(
            Float.NaN to GameConstants.BASE_SCROLL_SPEED,
            Float.POSITIVE_INFINITY to GameConstants.BASE_SCROLL_SPEED,
            Float.NEGATIVE_INFINITY to GameConstants.BASE_SCROLL_SPEED,
            0f to GameConstants.BASE_SCROLL_SPEED,
            -0.001f to GameConstants.BASE_SCROLL_SPEED,
            0.016f to Float.NaN,
            0.016f to Float.POSITIVE_INFINITY,
            0.016f to Float.NEGATIVE_INFINITY,
            0.016f to -0.001f
        )

        malformed.forEach { (delta, speed) ->
            background.update(delta, speed)
            assertEquals(before, snapshot(background))
        }
    }

    @Test
    fun `oversized finite frame is capped consistently across coordinator and layers`() {
        val background = ParallaxBackground(160, 90)
        background.setBloomState(isActive = true, activationLevel = 1f)

        background.update(Float.MAX_VALUE, Float.MAX_VALUE)

        assertEquals(
            FrameInputAdmission.MAX_DELTA_SECONDS,
            floatField(background, "ambienceTime"),
            0f
        )
        assertEquals(
            GameConstants.MAX_SCROLL_SPEED,
            floatField(background, "currentScrollSpeed"),
            0f
        )
        assertEquals(0.225f, floatField(background, "bloomLevel"), 0.0001f)
        assertEquals(0.17f, floatField(background, "bloomPulse"), 0.0001f)

        layers(background).forEach { layer ->
            assertTrue(layer.x.isFinite())
            assertTrue(layer.x <= 0f)
            assertTrue(layer.x > -layer.bitmap.width.toFloat())
        }
    }

    @Test
    fun `admitted frame repairs inherited nonfinite accumulators`() {
        val background = ParallaxBackground(160, 90)
        background.setBloomState(isActive = true, activationLevel = 1f)
        setFloatField(background, "ambienceTime", Float.NaN)
        setFloatField(background, "bloomLevel", Float.NaN)
        setFloatField(background, "bloomPulse", Float.POSITIVE_INFINITY)

        background.update(0.016f, GameConstants.BASE_SCROLL_SPEED)

        assertEquals(0.016f, floatField(background, "ambienceTime"), 0f)
        assertEquals(0.072f, floatField(background, "bloomLevel"), 0.0001f)
        assertEquals(0.0544f, floatField(background, "bloomPulse"), 0.0001f)
    }

    private data class FrameSnapshot(
        val ambienceTime: Float,
        val currentScrollSpeed: Float,
        val bloomLevel: Float,
        val bloomPulse: Float,
        val layerPositions: List<Float>
    )

    private fun snapshot(background: ParallaxBackground): FrameSnapshot = FrameSnapshot(
        ambienceTime = floatField(background, "ambienceTime"),
        currentScrollSpeed = floatField(background, "currentScrollSpeed"),
        bloomLevel = floatField(background, "bloomLevel"),
        bloomPulse = floatField(background, "bloomPulse"),
        layerPositions = layers(background).map { it.x }
    )

    @Suppress("UNCHECKED_CAST")
    private fun layers(background: ParallaxBackground): Array<ParallaxLayer> {
        val field = ParallaxBackground::class.java.getDeclaredField("layers")
        field.isAccessible = true
        return field.get(background) as Array<ParallaxLayer>
    }

    private fun floatField(background: ParallaxBackground, name: String): Float {
        val field = ParallaxBackground::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(background)
    }

    private fun setFloatField(background: ParallaxBackground, name: String, value: Float) {
        val field = ParallaxBackground::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(background, value)
    }
}
