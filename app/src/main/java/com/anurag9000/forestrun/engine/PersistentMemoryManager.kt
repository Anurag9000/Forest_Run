package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType

/**
 * Cross-run memory wrapper for encounter counts, spared counts, and repeated killers.
 *
 * This intentionally starts small and data-oriented so gameplay systems can
 * depend on one canonical API while the broader pacifist/memory layer expands.
 */
object PersistentMemoryManager {

    data class HistoryUnlockMark(
        val id: String,
        val label: String,
        val line: String
    )

    data class BiomeFriendshipMark(
        val biome: Biome,
        val friendshipCount: Int
    )

    data class CleanPassMark(
        val type: EntityType,
        val passCount: Int,
        val hitCount: Int
    )

    data class RepeatedHistorySnapshot(
        val featuredWarmCreature: EntityType?,
        val featuredWarmStreak: Int,
        val featuredTenderCreature: EntityType?,
        val featuredTenderStreak: Int,
        val featuredRepeatKiller: EntityType?,
        val featuredRepeatKillerHits: Int,
        val featuredCleanPass: CleanPassMark?,
        val featuredPeaceBiome: BiomeFriendshipMark?,
        val unlockedMarks: Set<String>,
        val featuredUnlock: HistoryUnlockMark?
    )

    private data class RepeatKillerCandidate(
        val type: EntityType,
        val hits: Int,
        val severity: Long
    )

    fun recordEncounter(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementEncounterCount(appContext, type)
        refreshHistoryUnlockState(appContext)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }

    fun recordSpare(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementSparedCount(appContext, type)
        SaveManager.incrementKindnessStreak(appContext, type)
        SaveManager.resetTenderStreak(appContext, type)
        refreshHistoryUnlockState(appContext)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }

    fun recordHit(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementHitCount(appContext, type)
        SaveManager.incrementTenderStreak(appContext, type)
        SaveManager.resetKindnessStreak(appContext, type)
        SaveManager.saveLastKiller(appContext, type)
        refreshHistoryUnlockState(appContext)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }

    fun recordPass(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementCleanPassCount(appContext, type)
        refreshHistoryUnlockState(appContext)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }

    fun getEncounterCount(context: Context, type: EntityType): Int =
        SaveManager.loadEncounterCount(context.applicationContext, type)

    fun getSparedCount(context: Context, type: EntityType): Int =
        SaveManager.loadSparedCount(context.applicationContext, type)

    fun getHitCount(context: Context, type: EntityType): Int =
        SaveManager.loadHitCount(context.applicationContext, type)

    fun getPassCount(context: Context, type: EntityType): Int =
        SaveManager.loadCleanPassCount(context.applicationContext, type)

    fun getKindnessStreak(context: Context, type: EntityType): Int =
        SaveManager.loadKindnessStreak(context.applicationContext, type)

    fun getTenderStreak(context: Context, type: EntityType): Int =
        SaveManager.loadTenderStreak(context.applicationContext, type)

    fun getLastKiller(context: Context): EntityType? =
        SaveManager.loadLastKiller(context.applicationContext)

    fun recordBiomeFriendship(context: Context, biome: Biome) {
        val appContext = context.applicationContext
        SaveManager.incrementBiomeFriendship(appContext, biome)
        refreshHistoryUnlockState(appContext)
    }

    fun getBiomeFriendship(context: Context, biome: Biome): Int =
        SaveManager.loadBiomeFriendship(context.applicationContext, biome)

    fun peacefulBiomes(context: Context, minimumFriendship: Int = 1): List<BiomeFriendshipMark> {
        val safeMinimum = minimumFriendship.coerceAtLeast(1)
        return Biome.entries
            .map { biome -> BiomeFriendshipMark(biome, getBiomeFriendship(context, biome)) }
            .filter { it.friendshipCount >= safeMinimum }
            .sortedWith(
                compareByDescending<BiomeFriendshipMark> { it.friendshipCount }
                    .thenBy { Biome.entries.indexOf(it.biome) }
            )
    }

    fun featuredPeaceBiome(context: Context, minimumFriendship: Int = 1): BiomeFriendshipMark? =
        peacefulBiomes(context, minimumFriendship).firstOrNull()

    fun getRelationshipStage(context: Context, type: EntityType): RelationshipStage =
        RelationshipArcSystem.stageFor(context.applicationContext, type)

    fun featuredWarmCreature(context: Context, minimumStreak: Int = 2): EntityType? {
        val safeMinimum = minimumStreak.coerceAtLeast(1)
        return EntityType.entries
            .asSequence()
            .map { type ->
                Triple(
                    type,
                    getKindnessStreak(context, type),
                    getSparedCount(context, type) - getHitCount(context, type)
                )
            }
            .filter { (_, streak, warmthMargin) -> streak >= safeMinimum || warmthMargin >= 2 }
            .maxWithOrNull(
                compareBy<Triple<EntityType, Int, Int>> { it.second }
                    .thenBy { it.third }
                    .thenBy { it.first.ordinal }
            )
            ?.first
    }

    fun featuredTenderCreature(context: Context, minimumStreak: Int = 2): EntityType? {
        val safeMinimum = minimumStreak.coerceAtLeast(1)
        return EntityType.entries
            .asSequence()
            .map { type ->
                Triple(
                    type,
                    getTenderStreak(context, type),
                    getHitCount(context, type) - getSparedCount(context, type)
                )
            }
            .filter { (_, streak, tensionMargin) -> streak >= safeMinimum || tensionMargin >= 2 }
            .maxWithOrNull(
                compareBy<Triple<EntityType, Int, Int>> { it.second }
                    .thenBy { it.third }
                    .thenBy { it.first.ordinal }
            )
            ?.first
    }

    fun featuredRepeatKiller(context: Context, minimumHits: Int = 3): EntityType? {
        val safeMinimum = minimumHits.coerceAtLeast(1)
        return EntityType.entries
            .asSequence()
            .map { type ->
                val hits = getHitCount(context, type)
                val severity = getTenderStreak(context, type).toLong() +
                    hits.toLong() -
                    getSparedCount(context, type).toLong()
                RepeatKillerCandidate(type, hits, severity)
            }
            .filter { candidate -> candidate.hits >= safeMinimum }
            .maxWithOrNull(
                compareBy<RepeatKillerCandidate> { it.hits }
                    .thenBy { it.severity }
                    .thenBy { it.type.ordinal }
            )
            ?.type
    }

    fun featuredCleanPass(
        context: Context,
        candidates: Set<EntityType> = EntityType.entries.toSet(),
        minimumPasses: Int = 3
    ): CleanPassMark? {
        val safeMinimum = minimumPasses.coerceAtLeast(1)
        return candidates
            .asSequence()
            .map { type ->
                CleanPassMark(
                    type = type,
                    passCount = getPassCount(context, type),
                    hitCount = getHitCount(context, type)
                )
            }
            .filter { it.passCount >= safeMinimum && it.passCount > it.hitCount }
            .maxWithOrNull(
                compareBy<CleanPassMark> { it.passCount - it.hitCount }
                    .thenBy { it.passCount }
                    .thenBy { it.type.ordinal }
            )
    }

    fun unlockedHistoryMarks(context: Context): Set<String> =
        SaveManager.loadUnlockedHistoryMarks(context.applicationContext)

    fun historyUnlocks(context: Context): List<HistoryUnlockMark> =
        unlockedHistoryMarks(context)
            .mapNotNull { id -> unlockMarkForId(context.applicationContext, id) }
            .sortedWith(compareBy<HistoryUnlockMark> { historyPriority(it.id) }.thenBy { it.label })

    fun featuredHistoryUnlock(context: Context): HistoryUnlockMark? =
        historyUnlocks(context).firstOrNull()

    fun repeatedHistorySnapshot(context: Context): RepeatedHistorySnapshot {
        val appContext = context.applicationContext
        val featuredWarmCreature = featuredWarmCreature(appContext)
        val featuredTenderCreature = featuredTenderCreature(appContext)
        val featuredRepeatKiller = featuredRepeatKiller(appContext)
        val featuredCleanPass = featuredCleanPass(appContext)
        val featuredPeaceBiome = featuredPeaceBiome(appContext, minimumFriendship = 2)
        return RepeatedHistorySnapshot(
            featuredWarmCreature = featuredWarmCreature,
            featuredWarmStreak = featuredWarmCreature?.let { getKindnessStreak(appContext, it) } ?: 0,
            featuredTenderCreature = featuredTenderCreature,
            featuredTenderStreak = featuredTenderCreature?.let { getTenderStreak(appContext, it) } ?: 0,
            featuredRepeatKiller = featuredRepeatKiller,
            featuredRepeatKillerHits = featuredRepeatKiller?.let { getHitCount(appContext, it) } ?: 0,
            featuredCleanPass = featuredCleanPass,
            featuredPeaceBiome = featuredPeaceBiome,
            unlockedMarks = unlockedHistoryMarks(appContext),
            featuredUnlock = featuredHistoryUnlock(appContext)
        )
    }

    private fun refreshHistoryUnlockState(context: Context) {
        val unlocked = SaveManager.loadUnlockedHistoryMarks(context).toMutableSet()
        var changed = false

        EntityType.entries.forEach { type ->
            val warmthMargin = getSparedCount(context, type) - getHitCount(context, type)
            if (getKindnessStreak(context, type) >= 2 || warmthMargin >= 2) {
                changed = unlocked.add("history_kindness_${type.name.lowercase()}") || changed
            }

            val tensionMargin = getHitCount(context, type) - getSparedCount(context, type)
            if (getTenderStreak(context, type) >= 2 || tensionMargin >= 2) {
                changed = unlocked.add("history_tender_${type.name.lowercase()}") || changed
            }

            if (getHitCount(context, type) >= 3) {
                changed = unlocked.add("history_repeat_killer_${type.name.lowercase()}") || changed
            }

            val cleanPass = CleanPassMark(
                type = type,
                passCount = getPassCount(context, type),
                hitCount = getHitCount(context, type)
            )
            if (cleanPass.passCount >= 3 && cleanPass.passCount > cleanPass.hitCount) {
                changed = unlocked.add("history_clean_pass_${type.name.lowercase()}") || changed
            }
        }

        Biome.entries.forEach { biome ->
            if (getBiomeFriendship(context, biome) >= 2) {
                changed = unlocked.add("history_peace_${biome.name.lowercase()}") || changed
            }
        }

        if (changed) {
            SaveManager.saveUnlockedHistoryMarks(context, unlocked)
        }
    }

    private fun unlockMarkForId(context: Context, id: String): HistoryUnlockMark? = when {
        id.startsWith("history_kindness_") -> entityFromId(id.removePrefix("history_kindness_"))?.let { type ->
            HistoryUnlockMark(
                id = id,
                label = "Trust Kept",
                line = "${formatName(type)} keeps showing up in memory as something you answered gently often enough for it to stay."
            )
        }
        id.startsWith("history_tender_") -> entityFromId(id.removePrefix("history_tender_"))?.let { type ->
            HistoryUnlockMark(
                id = id,
                label = "Watchful Memory",
                line = "${formatName(type)} still sits in memory as the place where caution keeps gathering first."
            )
        }
        id.startsWith("history_repeat_killer_") -> entityFromId(id.removePrefix("history_repeat_killer_"))?.let { type ->
            HistoryUnlockMark(
                id = id,
                label = "Same Shadow",
                line = "${formatName(type)} has become part of the darker pattern the forest remembers most quickly."
            )
        }
        id.startsWith("history_clean_pass_") -> entityFromId(id.removePrefix("history_clean_pass_"))?.let { type ->
            HistoryUnlockMark(
                id = id,
                label = if (type == EntityType.CACTUS) "Needle Bloom" else "Clean Read",
                line = if (type == EntityType.CACTUS) {
                    "Cactus crossings have started living in memory as a place you learned to pass cleanly instead of fear."
                } else {
                    "${formatName(type)} has become part of memory as something you now read cleanly more often than not."
                }
            )
        }
        id.startsWith("history_peace_") -> biomeFromId(id.removePrefix("history_peace_"))?.let { biome ->
            HistoryUnlockMark(
                id = id,
                label = "${biome.displayName} Softened",
                line = "${biome.displayName} has appeared often enough in gentler runs to leave a quieter mark in memory."
            )
        }
        else -> null
    }

    private fun entityFromId(raw: String): EntityType? =
        runCatching { EntityType.valueOf(raw.uppercase()) }.getOrNull()

    private fun biomeFromId(raw: String): Biome? =
        runCatching { Biome.valueOf(raw.uppercase()) }.getOrNull()

    private fun historyPriority(id: String): Int = when {
        id.startsWith("history_repeat_killer_") -> 0
        id.startsWith("history_tender_") -> 1
        id.startsWith("history_kindness_") -> 2
        id.startsWith("history_clean_pass_") -> 3
        id.startsWith("history_peace_") -> 4
        else -> 5
    }

    private fun formatName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
