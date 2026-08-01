package com.anurag9000.forestrun.engine

/** Pure score model for the authored relationship familiarity warmth tiers. */
internal object FamiliarityWarmthScoring {
    const val PERSONAL_THRESHOLD = 5
    const val BONDED_THRESHOLD = 7

    fun score(
        stage: RelationshipStage,
        passCount: Int,
        sparedCount: Int,
        kindnessStreak: Int,
        encounters: Int
    ): Int {
        val safePasses = passCount.coerceAtLeast(0)
        val safeSpares = sparedCount.coerceAtLeast(0)
        val safeKindness = kindnessStreak.coerceAtLeast(0)
        val safeEncounters = encounters.coerceAtLeast(0)

        return stageBase(stage) +
            bonus(safePasses >= 3) +
            bonus(safePasses >= 5) +
            bonus(safeSpares >= 2) +
            bonus(safeKindness >= 3) +
            bonus(safeEncounters >= 5)
    }

    fun tierOrdinal(score: Int): Int = when {
        score >= BONDED_THRESHOLD -> 3
        score >= PERSONAL_THRESHOLD -> 2
        else -> 1
    }

    private fun stageBase(stage: RelationshipStage): Int = when (stage) {
        RelationshipStage.FIRST_IMPRESSION -> 0
        RelationshipStage.RECOGNITION -> 1
        RelationshipStage.TRUST -> 2
        RelationshipStage.MILESTONE -> 3
    }

    private fun bonus(condition: Boolean): Int = if (condition) 1 else 0
}
