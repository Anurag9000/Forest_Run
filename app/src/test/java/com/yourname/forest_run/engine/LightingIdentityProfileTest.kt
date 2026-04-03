package com.yourname.forest_run.engine

import android.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LightingIdentityProfileTest {

    @Test
    fun `run lighting warms and brightens with bloom`() {
        val calmNight = buildRunLightingIdentity(
            nightFactor = 0.8f,
            bloomStrength = 0f
        )
        val bloomingNight = buildRunLightingIdentity(
            nightFactor = 0.8f,
            bloomStrength = 1f
        )

        assertTrue(Color.red(bloomingNight.horizonGlowColor) >= Color.red(calmNight.horizonGlowColor))
        assertTrue(Color.blue(bloomingNight.glowMoteColor) > Color.blue(calmNight.glowMoteColor))
        assertTrue(Color.green(bloomingNight.canopyNearColor) >= Color.green(calmNight.canopyNearColor))
    }

    @Test
    fun `sanctuary scenes have distinct authored lighting identities`() {
        val menu = buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)
        val garden = buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)
        val rest = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)

        assertNotEquals(menu.canopyColor, garden.canopyColor)
        assertNotEquals(menu.lanternOuterColor, rest.lanternOuterColor)
        assertNotEquals(garden.groundGlowColor, rest.groundGlowColor)
    }
}
