package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctuaryAtmosphereTest {
    @Test
    fun `all firefly modifiers remain independent when repeat friend is present`() {
        val atmosphere = buildSanctuaryAtmosphere(
            signals(
                mood = ForestMood.STEADY,
                moodStreak = 4,
                warmBondCount = 2,
                milestoneRewardCount = 2,
                kindnessStreak = 4,
                peacefulBiomeCount = 2,
                hasRepeatFriend = true,
                hasRepeatedHarm = true,
                hasFeaturedReward = true,
                routeTier = PacifistRouteTier.PEACEFUL
            )
        )

        assertEquals(17, atmosphere.fireflyCount)
    }

    @Test
    fun `petal bloom lantern and glow modifiers all accumulate`() {
        val atmosphere = buildSanctuaryAtmosphere(
            signals(
                mood = ForestMood.GENTLE,
                warmBondCount = 2,
                milestoneRewardCount = 2,
                kindnessStreak = 3,
                peacefulBiomeCount = 2,
                hasRepeatFriend = true,
                hasFeaturedReward = true,
                routeTier = PacifistRouteTier.PEACEFUL,
                sparedCount = 1,
                hasRepeatedKindness = true,
                hasFeaturedPeaceBiome = true,
                hasFeaturedCostume = true,
                bloomConversions = 2,
                hasCactusBloom = true
            )
        )

        assertEquals(9, atmosphere.petalCount)
        assertEquals(11, atmosphere.bloomPatchCount)
        assertEquals(11, atmosphere.lanternGlowCount)
        assertEquals(158, atmosphere.groundGlowAlpha)
    }

    @Test
    fun `mist and canopy subtractions apply without swallowing additions`() {
        val atmosphere = buildSanctuaryAtmosphere(
            signals(
                mood = ForestMood.FEARFUL,
                hasRepeatedHarm = true,
                hasFeaturedPeaceBiome = true,
                routeTier = PacifistRouteTier.PEACEFUL,
                memoryPageCount = 4
            )
        )

        assertEquals(3, atmosphere.mistBandCount)
        assertEquals(64, atmosphere.canopyShadeAlpha)
    }

    @Test
    fun `negative restored counters fail closed`() {
        val atmosphere = buildSanctuaryAtmosphere(
            signals(
                mood = ForestMood.RECKLESS,
                moodStreak = Int.MIN_VALUE,
                warmBondCount = Int.MIN_VALUE,
                milestoneRewardCount = Int.MIN_VALUE,
                kindnessStreak = Int.MIN_VALUE,
                peacefulBiomeCount = Int.MIN_VALUE,
                sparedCount = Int.MIN_VALUE,
                bloomConversions = Int.MIN_VALUE,
                memoryPageCount = Int.MIN_VALUE
            )
        )

        assertEquals(1, atmosphere.fireflyCount)
        assertEquals(5, atmosphere.petalCount)
        assertEquals(0, atmosphere.bloomPatchCount)
        assertEquals(0, atmosphere.mistBandCount)
        assertEquals(0, atmosphere.lanternGlowCount)
        assertEquals(36, atmosphere.groundGlowAlpha)
        assertEquals(22, atmosphere.canopyShadeAlpha)
    }

    @Test
    fun `all published visual values remain bounded`() {
        val atmosphere = buildSanctuaryAtmosphere(
            signals(
                mood = ForestMood.GENTLE,
                moodStreak = Int.MAX_VALUE,
                warmBondCount = Int.MAX_VALUE,
                milestoneRewardCount = Int.MAX_VALUE,
                kindnessStreak = Int.MAX_VALUE,
                peacefulBiomeCount = Int.MAX_VALUE,
                hasRepeatFriend = true,
                hasFeaturedReward = true,
                routeTier = PacifistRouteTier.PEACEFUL,
                sparedCount = Int.MAX_VALUE,
                hasRepeatedKindness = true,
                hasFeaturedPeaceBiome = true,
                hasFeaturedCostume = true,
                bloomConversions = Int.MAX_VALUE,
                hasCactusBloom = true,
                memoryPageCount = Int.MAX_VALUE
            )
        )

        assertTrue(atmosphere.fireflyCount >= 0)
        assertTrue(atmosphere.petalCount >= 0)
        assertTrue(atmosphere.bloomPatchCount >= 0)
        assertTrue(atmosphere.mistBandCount >= 0)
        assertTrue(atmosphere.lanternGlowCount >= 0)
        assertTrue(atmosphere.groundGlowAlpha in 0..180)
        assertTrue(atmosphere.canopyShadeAlpha in 0..255)
    }

    private fun signals(
        mood: ForestMood,
        moodStreak: Int = 0,
        warmBondCount: Int = 0,
        milestoneRewardCount: Int = 0,
        kindnessStreak: Int = 0,
        peacefulBiomeCount: Int = 0,
        hasRepeatFriend: Boolean = false,
        hasRepeatedHarm: Boolean = false,
        hasFeaturedReward: Boolean = false,
        routeTier: PacifistRouteTier = PacifistRouteTier.NONE,
        sparedCount: Int = 0,
        hasRepeatedKindness: Boolean = false,
        hasFeaturedPeaceBiome: Boolean = false,
        hasFeaturedCostume: Boolean = false,
        bloomConversions: Int = 0,
        hasCactusBloom: Boolean = false,
        memoryPageCount: Int = 0
    ): SanctuaryAtmosphereSignals = SanctuaryAtmosphereSignals(
        mood = mood,
        moodStreak = moodStreak,
        warmBondCount = warmBondCount,
        milestoneRewardCount = milestoneRewardCount,
        kindnessStreak = kindnessStreak,
        peacefulBiomeCount = peacefulBiomeCount,
        hasRepeatFriend = hasRepeatFriend,
        hasRepeatedHarm = hasRepeatedHarm,
        hasFeaturedReward = hasFeaturedReward,
        routeTier = routeTier,
        sparedCount = sparedCount,
        hasRepeatedKindness = hasRepeatedKindness,
        hasFeaturedPeaceBiome = hasFeaturedPeaceBiome,
        hasFeaturedCostume = hasFeaturedCostume,
        bloomConversions = bloomConversions,
        hasCactusBloom = hasCactusBloom,
        memoryPageCount = memoryPageCount
    )
}
