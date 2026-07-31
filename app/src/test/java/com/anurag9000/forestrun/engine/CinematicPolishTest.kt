package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicPolishTest {

    @Test
    fun `run polish stays lighter than sanctuary scenes until bloom rises`() {
        val run = buildCinematicPolishProfile(CinematicScene.RUN)
        val menu = buildCinematicPolishProfile(CinematicScene.MENU)
        val garden = buildCinematicPolishProfile(CinematicScene.GARDEN)
        val rest = buildCinematicPolishProfile(CinematicScene.REST)

        assertTrue(run.vignetteAlpha < menu.vignetteAlpha)
        assertTrue(run.vignetteAlpha < garden.vignetteAlpha)
        assertTrue(run.vignetteAlpha < rest.vignetteAlpha)
        assertTrue(run.letterboxHeightFraction < rest.letterboxHeightFraction)
    }

    @Test
    fun `bloom emphasis intensifies the run cinematic profile`() {
        val calmRun = buildCinematicPolishProfile(CinematicScene.RUN, emphasis = 0.2f, bloomStrength = 0f)
        val bloomingRun = buildCinematicPolishProfile(CinematicScene.RUN, emphasis = 0.6f, bloomStrength = 1f)

        assertTrue(bloomingRun.vignetteAlpha > calmRun.vignetteAlpha)
        assertTrue(bloomingRun.edgeGlowAlpha > calmRun.edgeGlowAlpha)
        assertTrue(bloomingRun.centerLiftAlpha > calmRun.centerLiftAlpha)
        assertTrue(bloomingRun.shimmerStrength > calmRun.shimmerStrength)
    }

    @Test
    fun `sanctuary scenes keep strong cinematic framing without breaking clamps`() {
        val profile = buildCinematicPolishProfile(CinematicScene.GARDEN, emphasis = 1f)

        assertTrue(profile.vignetteAlpha in 0..140)
        assertTrue(profile.edgeGlowAlpha in 0..120)
        assertTrue(profile.letterboxAlpha in 0..120)
        assertTrue(profile.letterboxHeightFraction in 0f..0.1f)
        assertTrue(profile.centerLiftAlpha in 0..90)
        assertTrue(profile.shimmerStrength in 0f..1f)
    }

    @Test
    fun `non finite profile inputs resolve to calm scene baseline`() {
        CinematicScene.entries.forEach { scene ->
            val baseline = buildCinematicPolishProfile(scene, emphasis = 0f, bloomStrength = 0f)
            listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
                assertEquals(
                    baseline,
                    buildCinematicPolishProfile(scene, emphasis = invalid, bloomStrength = invalid)
                )
            }
        }
    }

    @Test
    fun `extreme finite profile inputs clamp to authored endpoints`() {
        CinematicScene.entries.forEach { scene ->
            assertEquals(
                buildCinematicPolishProfile(scene, emphasis = 0f, bloomStrength = 0f),
                buildCinematicPolishProfile(
                    scene,
                    emphasis = -Float.MAX_VALUE,
                    bloomStrength = -Float.MAX_VALUE
                )
            )
            val maximum = buildCinematicPolishProfile(
                scene,
                emphasis = Float.MAX_VALUE,
                bloomStrength = Float.MAX_VALUE
            )
            assertTrue(maximum.letterboxHeightFraction.isFinite())
            assertTrue(maximum.shimmerStrength.isFinite())
            assertTrue(maximum.shimmerStrength in 0f..1f)
        }
    }
}
