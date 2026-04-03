package com.yourname.forest_run.engine

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
}
