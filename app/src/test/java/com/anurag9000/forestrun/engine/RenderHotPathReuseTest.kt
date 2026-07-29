package com.anurag9000.forestrun.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RenderHotPathReuseTest {

    @Test
    fun `mutable presentation resolvers reuse caller-owned state`() {
        val lighting = RunLightingIdentity()
        val cinematic = CinematicPolishProfile()
        val bloomPower = BloomPowerPresentationState()
        val bloomHud = BloomHudPresentation()

        assertSame(
            lighting,
            resolveRunLightingIdentity(lighting, nightFactor = 0.6f, bloomStrength = 0.8f)
        )
        assertSame(
            cinematic,
            resolveCinematicPolishProfile(
                cinematic,
                scene = CinematicScene.RUN,
                emphasis = 0.4f,
                bloomStrength = 0.7f
            )
        )
        assertSame(
            bloomPower,
            BloomPowerPresentation.resolveInto(
                bloomPower,
                secondsRemaining = 4.5f,
                conversionsInBurst = 3,
                recentSurgeFraction = 0.5f
            )
        )
        assertSame(
            bloomHud,
            BloomPresentation.resolveInto(
                bloomHud,
                bloomMeter = 0,
                seedTarget = 8,
                isActive = true,
                secondsRemaining = 4.34f,
                totalConversions = 2,
                burstConversions = 2,
                recentAfterglow = 0f
            )
        )

        assertTrue(lighting.horizonGlowColor != 0)
        assertTrue(cinematic.vignetteAlpha > 0)
        assertEquals(2, bloomPower.tier)
        assertEquals(BloomPresentationMode.ACTIVE, bloomHud.mode)
    }

    @Test
    fun `bloom HUD preserves status string within the same displayed tenth`() {
        val target = BloomHudPresentation()
        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.31f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        val cached = target.statusText

        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.34f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        assertSame(cached, target.statusText)

        BloomPresentation.resolveInto(
            target,
            bloomMeter = 0,
            seedTarget = 8,
            isActive = true,
            secondsRemaining = 4.21f,
            totalConversions = 2,
            burstConversions = 2,
            recentAfterglow = 0f
        )
        assertNotSame(cached, target.statusText)
        assertTrue(target.statusText.contains("4.2s"))
    }

    @Test
    fun `parallax shaders stay stable across unchanged frames`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)
        val brightTop = Color.rgb(150, 200, 245)
        val brightBottom = Color.rgb(220, 238, 252)

        background.refreshDynamicShadersForTest(
            skyTop = brightTop,
            skyBottom = brightBottom,
            nightFactor = 0.15f,
            bloomStrength = 0f
        )
        val firstCount = background.dynamicShaderRebuildCountForTest
        assertTrue(firstCount > 0)

        repeat(12) {
            background.refreshDynamicShadersForTest(
                skyTop = brightTop,
                skyBottom = brightBottom,
                nightFactor = 0.15f,
                bloomStrength = 0f
            )
        }
        assertEquals(firstCount, background.dynamicShaderRebuildCountForTest)

        background.refreshDynamicShadersForTest(
            skyTop = Color.rgb(30, 42, 72),
            skyBottom = Color.rgb(68, 76, 106),
            nightFactor = 0.75f,
            bloomStrength = 0.8f
        )
        assertTrue(background.dynamicShaderRebuildCountForTest > firstCount)
    }
}
