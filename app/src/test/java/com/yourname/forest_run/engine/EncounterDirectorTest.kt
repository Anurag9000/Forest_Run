package com.yourname.forest_run.engine

import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncounterDirectorTest {

    @Test
    fun `advance emits scheduled steps in deterministic order`() {
        val director = EncounterDirector()
        director.startSelectedScenario()

        val firstWave = director.advance(0.20f)
        val secondWave = director.advance(0.90f)
        val thirdWave = director.advance(1.0f)

        assertEquals(1, firstWave.size)
        assertEquals(EntityType.DUCK, firstWave.single().type)
        assertEquals(1, secondWave.size)
        assertEquals(EntityType.LILY_OF_VALLEY, secondWave.single().type)
        assertTrue(thirdWave.isNotEmpty())
        assertEquals(EntityType.CAT, thirdWave.first().type)
    }

    @Test
    fun `scenario selection wraps in both directions`() {
        val director = EncounterDirector()
        val first = director.selectedScenario

        director.previousScenario()
        assertEquals(EncounterScenario.entries.last(), director.selectedScenario)

        director.nextScenario()
        assertEquals(first, director.selectedScenario)
    }

    @Test
    fun `stopScenario clears active state`() {
        val director = EncounterDirector()
        director.startSelectedScenario()
        assertTrue(director.isScenarioActive)

        director.stopScenario()

        assertFalse(director.isScenarioActive)
        assertEquals(0, director.remainingSteps)
    }

    @Test
    fun `suite includes dedicated bloom and ghost verification scenarios`() {
        assertTrue(EncounterScenario.BLOOM_SHOWCASE.startsWithBloom)
        assertTrue(EncounterScenario.GHOST_READABILITY.allowGhostPlayback)
        assertEquals(Biome.MEADOW, EncounterScenario.GHOST_READABILITY.forcedBiome)
    }

    @Test
    fun `suite includes all major entity family showcase scenarios`() {
        assertTrue(EncounterScenario.entries.size >= 20)
        assertEquals(EntityType.CACTUS, EncounterScenario.CACTUS_READ.steps.first().type)
        assertEquals(EntityType.WEEPING_WILLOW, EncounterScenario.WILLOW_CURTAIN.steps.first().type)
        assertEquals(EntityType.OWL, EncounterScenario.OWL_DIVE.steps.first().type)
        assertEquals(EntityType.WOLF, EncounterScenario.WOLF_CHARGE.steps.first().type)
    }
}
