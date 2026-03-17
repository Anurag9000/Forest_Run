package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PacifistPresentationTest {

    @Test
    fun `mercy miss cue escalates with route state`() {
        val plainCue = PacifistPresentation.mercyMissCue(
            mercyHearts = 1,
            kindnessChain = 1,
            routeTier = PacifistRouteTier.NONE
        )
        val peacefulCue = PacifistPresentation.mercyMissCue(
            mercyHearts = 6,
            kindnessChain = 8,
            routeTier = PacifistRouteTier.PEACEFUL
        )

        assertEquals("Close, gently", plainCue.bubbleText)
        assertEquals("Peace kept", peacefulCue.bubbleText)
        assertTrue(peacefulCue.flavorText.contains("Peace"))
    }

    @Test
    fun `reward cue reflects reward kind and biome`() {
        val biomeReward = PacifistReward(
            kind = PacifistRewardKind.BIOME_FRIENDSHIP,
            message = "Meadow at peace",
            points = 380,
            seeds = 2,
            friendBiome = Biome.MEADOW
        )
        val routeReward = PacifistReward(
            kind = PacifistRewardKind.ROUTE_MERCIFUL,
            message = "Merciful route",
            points = 320,
            seeds = 2,
            routeTier = PacifistRouteTier.MERCIFUL
        )

        val biomeCue = PacifistPresentation.rewardCue(biomeReward)
        val routeCue = PacifistPresentation.rewardCue(routeReward)

        assertTrue(biomeCue.bubbleText.contains("Meadow"))
        assertEquals("Merciful route", routeCue.bubbleText)
        assertTrue(routeCue.flavorText.contains("Mercy"))
    }

    @Test
    fun `route afterglow line stays distinct by tier`() {
        assertTrue(PacifistPresentation.routeAfterglowLine(PacifistRouteTier.KIND).contains("gentler"))
        assertTrue(PacifistPresentation.routeAfterglowLine(PacifistRouteTier.MERCIFUL).contains("Mercy"))
        assertTrue(PacifistPresentation.routeAfterglowLine(PacifistRouteTier.PEACEFUL).contains("quieter"))
    }
}
