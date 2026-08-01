package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningReadabilityGuideTest {

    @Test
    fun `opening guidance walks through tap hold duck prompts`() {
        val tapCue = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = 1.5f,
            inputState = OpeningInputState(),
            routeTier = PacifistRouteTier.NONE,
            mercyHearts = 0,
            kindnessChain = 0
        )
        assertEquals("Find The Stride", tapCue?.title)

        val holdCue = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = 5f,
            inputState = OpeningInputState(jumpSeen = true),
            routeTier = PacifistRouteTier.NONE,
            mercyHearts = 0,
            kindnessChain = 0
        )
        assertEquals("Hold For Height", holdCue?.title)

        val duckCue = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = 12f,
            inputState = OpeningInputState(jumpSeen = true, holdSeen = true),
            routeTier = PacifistRouteTier.NONE,
            mercyHearts = 0,
            kindnessChain = 0
        )
        assertEquals("Duck The Low Lane", duckCue?.title)
    }

    @Test
    fun `opening guidance becomes route-facing after the inputs are learned`() {
        val cue = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = 18f,
            inputState = OpeningInputState(jumpSeen = true, holdSeen = true, duckSeen = true),
            routeTier = PacifistRouteTier.PEACEFUL,
            mercyHearts = 2,
            kindnessChain = 3
        )

        assertNotNull(cue)
        assertEquals("Keep It Peaceful", cue?.title)
        assertTrue(cue!!.chips.all { it.isComplete })
    }

    @Test
    fun `opening guidance ends after the guided window`() {
        val cue = OpeningReadabilityGuide.cueFor(
            runTimeSeconds = 28f,
            inputState = OpeningInputState(jumpSeen = true, holdSeen = true, duckSeen = true),
            routeTier = PacifistRouteTier.NONE,
            mercyHearts = 0,
            kindnessChain = 0
        )

        assertNull(cue)
    }

    @Test
    fun `opening pacing locks then slows then releases random spawns`() {
        assertTrue(OpeningReadabilityGuide.isRandomSpawnLocked(3f))
        assertFalse(OpeningReadabilityGuide.isRandomSpawnLocked(8f))
        assertEquals(1.95f, OpeningReadabilityGuide.adjustedSpawnInterval(7f, 1.2f), 0.0001f)
        assertEquals(1.58f, OpeningReadabilityGuide.adjustedSpawnInterval(24f, 1.2f), 0.0001f)
        assertEquals(1.2f, OpeningReadabilityGuide.adjustedSpawnInterval(40f, 1.2f), 0.0001f)
    }

    @Test
    fun `opening pool stays curated during the guided window`() {
        val guidedPool = OpeningReadabilityGuide.spawnPoolFor(
            runTimeSeconds = 10f,
            defaultPool = listOf(EntityType.WOLF, EntityType.BAMBOO)
        )
        assertTrue(EntityType.DUCK in guidedPool)
        assertTrue(EntityType.CACTUS in guidedPool)
        assertFalse(EntityType.WOLF in guidedPool)

        val defaultPool = OpeningReadabilityGuide.spawnPoolFor(
            runTimeSeconds = 40f,
            defaultPool = listOf(EntityType.WOLF, EntityType.BAMBOO)
        )
        assertEquals(listOf(EntityType.WOLF, EntityType.BAMBOO), defaultPool)
    }

    @Test
    fun `invalid time behaves as the safe beginning of the opening`() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertTrue(OpeningReadabilityGuide.isRandomSpawnLocked(invalid))
            assertEquals(
                listOf(
                    EntityType.DUCK,
                    EntityType.LILY_OF_VALLEY,
                    EntityType.CAT,
                    EntityType.TIT,
                    EntityType.CACTUS
                ),
                OpeningReadabilityGuide.spawnPoolFor(invalid, listOf(EntityType.WOLF))
            )
            assertEquals(
                "Find The Stride",
                OpeningReadabilityGuide.cueFor(
                    runTimeSeconds = invalid,
                    inputState = OpeningInputState(),
                    routeTier = PacifistRouteTier.NONE,
                    mercyHearts = 0,
                    kindnessChain = 0
                )?.title
            )
        }
    }

    @Test
    fun `invalid or zero intervals always resolve to a conservative positive cadence`() {
        listOf(
            -1f,
            0f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { invalid ->
            listOf(1f, 8f, 40f).forEach { time ->
                val adjusted = OpeningReadabilityGuide.adjustedSpawnInterval(time, invalid)
                assertTrue(adjusted.isFinite())
                assertEquals(1.95f, adjusted, 0.0001f)
                assertTrue(adjusted > 0f)
            }
        }
    }
}
