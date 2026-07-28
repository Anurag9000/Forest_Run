package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PacifistPresentationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

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

    @Test
    fun `route world state reflects cumulative route history`() {
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 300,
                distanceM = 220f,
                isNewHighScore = false,
                highScore = 500,
                mercyHearts = 3,
                mercyMisses = 3,
                kindnessChain = 6,
                cleanPasses = 7,
                sparedCount = 2,
                hitsTaken = 0,
                seedsCollected = 3,
                bloomConversions = 0,
                lastKiller = null,
                restQuote = "Mercy.",
                forestMood = ForestMood.GENTLE,
                pacifistRouteTier = PacifistRouteTier.MERCIFUL
            )
        )
        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = 520,
                distanceM = 410f,
                isNewHighScore = false,
                highScore = 600,
                mercyHearts = 5,
                mercyMisses = 5,
                kindnessChain = 8,
                cleanPasses = 10,
                sparedCount = 2,
                hitsTaken = 0,
                seedsCollected = 5,
                bloomConversions = 1,
                lastKiller = null,
                restQuote = "Peace.",
                forestMood = ForestMood.GENTLE,
                pacifistRouteTier = PacifistRouteTier.PEACEFUL
            )
        )

        val state = PacifistPresentation.routeWorldState(context, PacifistRouteTier.PEACEFUL)

        assertEquals("Peace Remembered", state?.label)
        assertTrue(state?.line?.contains("expects", ignoreCase = true) == true)
    }
}
