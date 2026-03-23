package com.yourname.forest_run.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostRunReflectionPlannerTest {

    @Test
    fun `rest entry prefers route or world reflection over duplicate carry-home lines`() {
        val summary = RunSummary(
            score = 900,
            distanceM = 640f,
            isNewHighScore = false,
            highScore = 1_200,
            mercyHearts = 3,
            mercyMisses = 3,
            kindnessChain = 6,
            cleanPasses = 9,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 7,
            bloomConversions = 1,
            lastKiller = null,
            restQuote = "Softly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.MERCIFUL
        )
        val sanctuary = GardenSanctuaryState(
            featuredPeaceLine = "The meadow stayed gentle with you.",
            carryHomeLine = "Mercy is already changing the way home sounds."
        )

        val entry = PostRunReflectionPlanner.restEntry(
            summary = summary,
            sanctuaryState = sanctuary,
            recoveryLine = "Mercy is already changing the way home sounds.",
            carryHomeLine = "Mercy is already changing the way home sounds."
        )

        assertEquals("Route", entry?.label)
        assertTrue(entry!!.text.isNotBlank())
    }

    @Test
    fun `garden entries prioritize route world and reflection without duplicates`() {
        val summary = RunSummary(
            score = 1_220,
            distanceM = 810f,
            isNewHighScore = false,
            highScore = 1_480,
            mercyHearts = 4,
            mercyMisses = 4,
            kindnessChain = 7,
            cleanPasses = 11,
            sparedCount = 2,
            hitsTaken = 0,
            seedsCollected = 8,
            bloomConversions = 2,
            lastKiller = null,
            restQuote = "Quietly.",
            forestMood = ForestMood.GENTLE,
            pacifistRouteTier = PacifistRouteTier.PEACEFUL
        )
        val sanctuary = GardenSanctuaryState(
            featuredPeaceLine = "Orchard still feels at peace with the way you crossed it.",
            carryHomeLine = "Orchard still feels at peace with the way you crossed it."
        )

        val entries = PostRunReflectionPlanner.gardenEntries(
            summary = summary,
            sanctuaryState = sanctuary,
            restQuote = "Quietly.",
            gardenReflection = "The garden has started trusting your gentler habits as something dependable.",
            weatherThought = "The evening wind sounds like it is trying not to disturb the peace you carried home.",
            creatureThought = "",
            arrivalLine = "The garden kept the calmer part of that run."
        )

        assertEquals(3, entries.size)
        assertEquals("Route", entries[0].label)
        assertEquals("World", entries[1].label)
        assertEquals("Reflection", entries[2].label)
    }
}
