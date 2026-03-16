package com.yourname.forest_run.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParallaxBackgroundTest {

    @Test
    fun `night bloom profile intensifies atmosphere without breaking clamps`() {
        val profile = buildParallaxAtmosphereProfile(
            scrollSpeed = GameConstants.BASE_SCROLL_SPEED * 1.8f,
            bloomStrength = 1f,
            skyTop = Color.rgb(12, 22, 44),
            skyBottom = Color.rgb(30, 40, 68)
        )

        assertTrue(profile.worldScale in 1f..1.065f)
        assertTrue(profile.driftScale in 1f..1.65f)
        assertTrue(profile.fireflyCount >= 6)
        assertTrue(profile.ribbonCount >= 4)
        assertTrue(profile.mistBandAlpha > 30)
        assertTrue(profile.canopyShadowAlpha > 30)
        assertTrue(profile.nightFactor > 0.6f)
    }

    @Test
    fun `bright daytime profile keeps fireflies restrained and ambience calmer`() {
        val bright = buildParallaxAtmosphereProfile(
            scrollSpeed = GameConstants.BASE_SCROLL_SPEED,
            bloomStrength = 0f,
            skyTop = Color.rgb(180, 220, 255),
            skyBottom = Color.rgb(220, 245, 255)
        )
        val dark = buildParallaxAtmosphereProfile(
            scrollSpeed = GameConstants.BASE_SCROLL_SPEED,
            bloomStrength = 0f,
            skyTop = Color.rgb(22, 28, 50),
            skyBottom = Color.rgb(38, 46, 72)
        )

        assertEquals(3, bright.ribbonCount)
        assertTrue(bright.fireflyCount <= 1)
        assertTrue(dark.fireflyCount > bright.fireflyCount)
        assertTrue(dark.canopyShadowAlpha > bright.canopyShadowAlpha)
        assertTrue(dark.biomeSkyAlpha > bright.biomeSkyAlpha)
    }
}
