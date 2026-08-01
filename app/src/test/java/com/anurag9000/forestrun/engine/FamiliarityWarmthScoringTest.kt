package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FamiliarityWarmthScoringTest {

    @Test
    fun `every authored modifier accumulates independently`() {
        assertEquals(
            8,
            FamiliarityWarmthScoring.score(
                stage = RelationshipStage.MILESTONE,
                passCount = 5,
                sparedCount = 2,
                kindnessStreak = 3,
                encounters = 5
            )
        )
    }

    @Test
    fun `each modifier contributes exactly one point`() {
        val baseline = FamiliarityWarmthScoring.score(
            stage = RelationshipStage.RECOGNITION,
            passCount = 0,
            sparedCount = 0,
            kindnessStreak = 0,
            encounters = 0
        )
        assertEquals(1, baseline)
        assertEquals(
            baseline + 1,
            FamiliarityWarmthScoring.score(
                RelationshipStage.RECOGNITION,
                passCount = 3,
                sparedCount = 0,
                kindnessStreak = 0,
                encounters = 0
            )
        )
        assertEquals(
            baseline + 2,
            FamiliarityWarmthScoring.score(
                RelationshipStage.RECOGNITION,
                passCount = 5,
                sparedCount = 0,
                kindnessStreak = 0,
                encounters = 0
            )
        )
        assertEquals(
            baseline + 1,
            FamiliarityWarmthScoring.score(
                RelationshipStage.RECOGNITION,
                passCount = 0,
                sparedCount = 2,
                kindnessStreak = 0,
                encounters = 0
            )
        )
        assertEquals(
            baseline + 1,
            FamiliarityWarmthScoring.score(
                RelationshipStage.RECOGNITION,
                passCount = 0,
                sparedCount = 0,
                kindnessStreak = 3,
                encounters = 0
            )
        )
        assertEquals(
            baseline + 1,
            FamiliarityWarmthScoring.score(
                RelationshipStage.RECOGNITION,
                passCount = 0,
                sparedCount = 0,
                kindnessStreak = 0,
                encounters = 5
            )
        )
    }

    @Test
    fun `negative restored counters cannot create warmth`() {
        assertEquals(
            2,
            FamiliarityWarmthScoring.score(
                stage = RelationshipStage.TRUST,
                passCount = Int.MIN_VALUE,
                sparedCount = Int.MIN_VALUE,
                kindnessStreak = Int.MIN_VALUE,
                encounters = Int.MIN_VALUE
            )
        )
    }

    @Test
    fun `personal and bonded thresholds match authored tiers`() {
        assertEquals(1, FamiliarityWarmthScoring.tierOrdinal(4))
        assertEquals(2, FamiliarityWarmthScoring.tierOrdinal(5))
        assertEquals(2, FamiliarityWarmthScoring.tierOrdinal(6))
        assertEquals(3, FamiliarityWarmthScoring.tierOrdinal(7))
        assertEquals(3, FamiliarityWarmthScoring.tierOrdinal(Int.MAX_VALUE))
    }
}
