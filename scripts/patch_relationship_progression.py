#!/usr/bin/env python3
"""Make Trust and Bond depend on positive outcomes rather than raw familiarity."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    relationship = Path(
        "app/src/main/java/com/anurag9000/forestrun/engine/RelationshipArcSystem.kt"
    )
    replace_once(
        relationship,
        '''        val stage = computeStage(
            type = type,
            encounters = SaveManager.loadEncounterCount(context.applicationContext, type),
            spared = SaveManager.loadSparedCount(context.applicationContext, type),
            hits = SaveManager.loadHitCount(context.applicationContext, type)
        )
''',
        '''        val stage = computeStage(
            type = type,
            encounters = SaveManager.loadEncounterCount(context.applicationContext, type),
            cleanPasses = SaveManager.loadCleanPassCount(context.applicationContext, type),
            spared = SaveManager.loadSparedCount(context.applicationContext, type),
            hits = SaveManager.loadHitCount(context.applicationContext, type)
        )
''',
        "stage inputs",
    )
    replace_once(
        relationship,
        '''    private fun computeStage(type: EntityType, encounters: Int, spared: Int, hits: Int): RelationshipStage {
        val config = thresholds.getValue(type)
        val score = encounters + spared * 2 + maxOf(0, spared - hits)
        return when {
            score >= config.milestoneScore -> RelationshipStage.MILESTONE
            score >= config.trustScore -> RelationshipStage.TRUST
            score >= config.recognitionScore -> RelationshipStage.RECOGNITION
            else -> RelationshipStage.FIRST_IMPRESSION
        }
    }

    private fun affinityScore(context: Context, type: EntityType): Int {
        val appContext = context.applicationContext
        val encounters = SaveManager.loadEncounterCount(appContext, type)
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return encounters + spared * 3 - hits
    }
''',
        '''    private fun computeStage(
        type: EntityType,
        encounters: Int,
        cleanPasses: Int,
        spared: Int,
        hits: Int
    ): RelationshipStage {
        val config = thresholds.getValue(type)
        if (encounters < config.recognitionScore) {
            return RelationshipStage.FIRST_IMPRESSION
        }

        // Familiarity alone is capped at Recognition. Trust and Bond must be
        // earned through positive outcomes; repeated hits delay progression.
        // A deliberate spare is rarer and more meaningful than a clean pass.
        val familiarity = minOf(encounters, config.recognitionScore)
        val positiveOutcomes = cleanPasses.coerceAtLeast(0) + spared.coerceAtLeast(0)
        val earnedScore = familiarity +
            cleanPasses.coerceAtLeast(0) +
            spared.coerceAtLeast(0) * 4 -
            hits.coerceAtLeast(0)

        return when {
            positiveOutcomes >= 3 && earnedScore >= config.milestoneScore -> RelationshipStage.MILESTONE
            positiveOutcomes > 0 && earnedScore >= config.trustScore -> RelationshipStage.TRUST
            else -> RelationshipStage.RECOGNITION
        }
    }

    private fun affinityScore(context: Context, type: EntityType): Int {
        val appContext = context.applicationContext
        val config = thresholds.getValue(type)
        // Familiarity can distinguish two already-earned bonds, but is bounded
        // and can never advance the relationship stage by itself.
        val familiarity = minOf(
            SaveManager.loadEncounterCount(appContext, type),
            config.recognitionScore + 3
        )
        val cleanPasses = SaveManager.loadCleanPassCount(appContext, type)
        val spared = SaveManager.loadSparedCount(appContext, type)
        val hits = SaveManager.loadHitCount(appContext, type)
        return familiarity + cleanPasses * 2 + spared * 4 - hits * 2
    }
''',
        "outcome-earned stage computation",
    )

    persistence = Path(
        "app/src/main/java/com/anurag9000/forestrun/engine/PersistentMemoryManager.kt"
    )
    replace_once(
        persistence,
        '''    fun recordPass(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementCleanPassCount(appContext, type)
        refreshHistoryUnlockState(appContext)
    }
''',
        '''    fun recordPass(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementCleanPassCount(appContext, type)
        refreshHistoryUnlockState(appContext)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }
''',
        "immediate pass-stage refresh",
    )

    return_moments_test = Path(
        "app/src/test/java/com/anurag9000/forestrun/engine/ReturnMomentsSystemTest.kt"
    )
    replace_once(
        return_moments_test,
        '''    fun `gentle high kindness milestone run can return stronger bonded moment`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }

        val summary = RunSummary(
''',
        '''    fun `gentle high kindness milestone run can return stronger bonded moment`() {
        repeat(3) { PersistentMemoryManager.recordEncounter(context, EntityType.CAT) }
        repeat(2) { PersistentMemoryManager.recordSpare(context, EntityType.CAT) }
        PersistentMemoryManager.recordPass(context, EntityType.CAT)

        val summary = RunSummary(
''',
        "milestone fixture includes third positive outcome",
    )


if __name__ == "__main__":
    main()
