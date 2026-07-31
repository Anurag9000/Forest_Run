package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
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
    fun `invalid deltas cannot rewind or poison active scenario`() {
        val director = EncounterDirector()
        director.startSelectedScenario()

        assertTrue(director.advance(Float.NaN).isEmpty())
        assertTrue(director.advance(-10f).isEmpty())
        assertTrue(director.advance(Float.POSITIVE_INFINITY).isEmpty())
        assertEquals(4, director.remainingSteps)

        val firstWave = director.advance(0.15f)
        assertEquals(EntityType.DUCK, firstWave.single().type)
        assertEquals(3, director.remainingSteps)
    }

    @Test
    fun `large finite delta emits every remaining step exactly once`() {
        val director = EncounterDirector()
        director.startSelectedScenario()

        val all = director.advance(Float.MAX_VALUE)
        val repeated = director.advance(1f)

        assertEquals(EncounterScenario.OPENING_READABILITY.steps.size, all.size)
        assertEquals(
            EncounterScenario.OPENING_READABILITY.steps.map { it.type },
            all.map { it.type }
        )
        assertTrue(repeated.isEmpty())
        assertEquals(0, director.remainingSteps)
    }

    @Test
    fun `restarting selected scenario resets elapsed time and cursor`() {
        val director = EncounterDirector()
        director.startSelectedScenario()
        director.advance(10f)
        assertEquals(0, director.remainingSteps)

        director.startSelectedScenario()

        assertEquals(EncounterScenario.OPENING_READABILITY.steps.size, director.remainingSteps)
        assertTrue(director.advance(0.10f).isEmpty())
        assertEquals(EntityType.DUCK, director.advance(0.05f).single().type)
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
        assertTrue(director.advance(100f).isEmpty())
    }

    @Test
    fun `every authored scenario has finite ordered steps and metadata`() {
        EncounterScenario.entries.forEach { scenario ->
            assertTrue(scenario.title.isNotBlank())
            assertTrue(scenario.summary.isNotBlank())
            assertTrue(scenario.steps.isNotEmpty())
            assertTrue(scenario.steps.all { it.atSeconds.isFinite() && it.atSeconds >= 0f })
            assertTrue(scenario.steps.all { it.xOffset.isFinite() })
            assertEquals(
                scenario.steps.sortedBy { it.atSeconds },
                scenario.steps
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encounter step rejects negative time`() {
        EncounterStep(-1f, EntityType.CACTUS, 100f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encounter step rejects non finite offset`() {
        EncounterStep(1f, EntityType.CACTUS, Float.NaN)
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
