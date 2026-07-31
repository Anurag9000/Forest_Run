package com.anurag9000.forestrun.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LightingIdentityProfileTest {

    @Test
    fun `night and bloom produce distinct run lighting identities`() {
        val calm = buildRunLightingIdentity(nightFactor = 0f, bloomStrength = 0f)
        val night = buildRunLightingIdentity(nightFactor = 1f, bloomStrength = 0f)
        val bloom = buildRunLightingIdentity(nightFactor = 0f, bloomStrength = 1f)

        assertNotEquals(calm.canopyNearColor, night.canopyNearColor)
        assertNotEquals(calm.horizonGlowColor, bloom.horizonGlowColor)
        assertNotEquals(calm.glowMoteColor, bloom.glowMoteColor)
    }

    @Test
    fun `resolve reuses caller owned run identity`() {
        val target = RunLightingIdentity()

        assertSame(target, resolveRunLightingIdentity(target, 0.4f, 0.7f))
        assertTrue(Color.red(target.horizonGlowColor) in 0..255)
        assertTrue(Color.green(target.horizonGlowColor) in 0..255)
        assertTrue(Color.blue(target.horizonGlowColor) in 0..255)
    }

    @Test
    fun `non finite run factors use calm finite identity`() {
        val calm = buildRunLightingIdentity(0f, 0f)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(calm, buildRunLightingIdentity(invalid, invalid))
        }
    }

    @Test
    fun `finite run factors clamp at authored endpoints`() {
        assertEquals(
            buildRunLightingIdentity(0f, 0f),
            buildRunLightingIdentity(-Float.MAX_VALUE, -Float.MAX_VALUE)
        )
        assertEquals(
            buildRunLightingIdentity(1f, 1f),
            buildRunLightingIdentity(Float.MAX_VALUE, Float.MAX_VALUE)
        )
    }

    @Test
    fun `sanctuary profiles remain cached and scene specific`() {
        val menu = buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU)
        val garden = buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN)
        val rest = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)

        assertSame(menu, buildSanctuaryLightingIdentity(SanctuaryLightingScene.MENU))
        assertSame(garden, buildSanctuaryLightingIdentity(SanctuaryLightingScene.GARDEN))
        assertSame(rest, buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST))
        assertNotEquals(menu.canopyColor, rest.canopyColor)
        assertNotEquals(garden.bloomPatchColor, rest.bloomPatchColor)
    }
}
