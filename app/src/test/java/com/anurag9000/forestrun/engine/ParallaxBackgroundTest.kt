package com.anurag9000.forestrun.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParallaxBackgroundTest {

    @Test
    fun `biome scene art profiles stay authored per biome`() {
        val meadow = buildBiomeSceneArtProfile(Biome.MEADOW)
        val orchard = buildBiomeSceneArtProfile(Biome.ORCHARD)
        val grove = buildBiomeSceneArtProfile(Biome.ANCIENT_GROVE)
        val canyon = buildBiomeSceneArtProfile(Biome.DUSK_CANYON)
        val night = buildBiomeSceneArtProfile(Biome.NIGHT_FOREST)

        assertEquals(SceneSkyFeature.SUN, meadow.skyFeature)
        assertEquals(SceneRidgeStyle.HILLS, meadow.ridgeStyle)
        assertEquals(SceneGroundAccent.FLOWERS, meadow.groundAccent)

        assertEquals(SceneSkyFeature.SUN, orchard.skyFeature)
        assertEquals(SceneRidgeStyle.ORCHARD_ROWS, orchard.ridgeStyle)
        assertEquals(SceneGroundAccent.PETALS, orchard.groundAccent)

        assertEquals(SceneSkyFeature.FILTERED_SUN, grove.skyFeature)
        assertEquals(SceneRidgeStyle.GROVE_SPIRES, grove.ridgeStyle)
        assertEquals(SceneGroundAccent.FERNS, grove.groundAccent)

        assertEquals(SceneSkyFeature.SUN, canyon.skyFeature)
        assertEquals(SceneRidgeStyle.CANYON_MESAS, canyon.ridgeStyle)
        assertEquals(SceneGroundAccent.STONES, canyon.groundAccent)

        assertEquals(SceneSkyFeature.MOON, night.skyFeature)
        assertEquals(SceneRidgeStyle.NIGHT_PINES, night.ridgeStyle)
        assertEquals(SceneGroundAccent.GLOW_MUSHROOMS, night.groundAccent)
    }

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
        assertTrue(profile.gustStrength in 0f..0.75f)
        assertTrue(profile.worldSwayAmplitude in 4f..16f)
        assertTrue(profile.fireflyCount >= 6)
        assertTrue(profile.glowMoteCount >= 6)
        assertTrue(profile.ribbonCount >= 4)
        assertTrue(profile.mistBandCount >= 4)
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
        assertTrue(dark.gustStrength > bright.gustStrength)
        assertTrue(dark.worldSwayAmplitude > bright.worldSwayAmplitude)
        assertTrue(dark.mistBandCount > bright.mistBandCount)
        assertTrue(dark.leafBackfillCount > bright.leafBackfillCount)
        assertTrue(dark.fireflyCount > bright.fireflyCount)
        assertTrue(dark.glowMoteCount > bright.glowMoteCount)
        assertTrue(dark.canopyShadowAlpha > bright.canopyShadowAlpha)
        assertTrue(dark.biomeSkyAlpha > bright.biomeSkyAlpha)
    }

    @Test
    fun `bloom lifts petal and glow density above calm daylight`() {
        val calm = buildParallaxAtmosphereProfile(
            scrollSpeed = GameConstants.BASE_SCROLL_SPEED,
            bloomStrength = 0f,
            skyTop = Color.rgb(168, 208, 248),
            skyBottom = Color.rgb(214, 238, 255)
        )
        val blooming = buildParallaxAtmosphereProfile(
            scrollSpeed = GameConstants.BASE_SCROLL_SPEED * 1.2f,
            bloomStrength = 1f,
            skyTop = Color.rgb(168, 208, 248),
            skyBottom = Color.rgb(214, 238, 255)
        )

        assertTrue(blooming.petalCount > calm.petalCount)
        assertTrue(blooming.petalTrailCount > calm.petalTrailCount)
        assertTrue(blooming.glowMoteCount > calm.glowMoteCount)
        assertTrue(blooming.gustStrength > calm.gustStrength)
        assertTrue(blooming.leafCount >= calm.leafCount)
    }
}
