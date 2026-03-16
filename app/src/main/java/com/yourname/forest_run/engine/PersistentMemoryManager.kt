package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.entities.EntityType

/**
 * Cross-run memory wrapper for encounter counts, spared counts, and repeated killers.
 *
 * This intentionally starts small and data-oriented so gameplay systems can
 * depend on one canonical API while the broader pacifist/memory layer expands.
 */
object PersistentMemoryManager {

    fun recordEncounter(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementEncounterCount(appContext, type)
        if (RelationshipArcSystem.isTracked(type)) {
            RelationshipArcSystem.refreshStage(appContext, type)
        }
    }

    fun recordSpare(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementSparedCount(appContext, type)
        SaveManager.incrementKindnessStreak(appContext, type)
        SaveManager.resetTenderStreak(appContext, type)
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

    fun getKindnessStreak(context: Context, type: EntityType): Int =
        SaveManager.loadKindnessStreak(context.applicationContext, type)

    fun getTenderStreak(context: Context, type: EntityType): Int =
        SaveManager.loadTenderStreak(context.applicationContext, type)

    fun getLastKiller(context: Context): EntityType? =
        SaveManager.loadLastKiller(context.applicationContext)

    fun recordBiomeFriendship(context: Context, biome: Biome) {
        SaveManager.incrementBiomeFriendship(context.applicationContext, biome)
    }

    fun getBiomeFriendship(context: Context, biome: Biome): Int =
        SaveManager.loadBiomeFriendship(context.applicationContext, biome)

    fun getRelationshipStage(context: Context, type: EntityType): RelationshipStage =
        RelationshipArcSystem.stageFor(context.applicationContext, type)

    fun featuredWarmCreature(context: Context, minimumStreak: Int = 2): EntityType? =
        EntityType.entries
            .asSequence()
            .map { type ->
                Triple(
                    type,
                    getKindnessStreak(context, type),
                    getSparedCount(context, type) - getHitCount(context, type)
                )
            }
            .filter { (_, streak, warmthMargin) -> streak >= minimumStreak || warmthMargin >= 2 }
            .maxWithOrNull(compareBy<Triple<EntityType, Int, Int>> { it.second }.thenBy { it.third })
            ?.first

    fun featuredTenderCreature(context: Context, minimumStreak: Int = 2): EntityType? =
        EntityType.entries
            .asSequence()
            .map { type ->
                Triple(
                    type,
                    getTenderStreak(context, type),
                    getHitCount(context, type) - getSparedCount(context, type)
                )
            }
            .filter { (_, streak, tensionMargin) -> streak >= minimumStreak || tensionMargin >= 2 }
            .maxWithOrNull(compareBy<Triple<EntityType, Int, Int>> { it.second }.thenBy { it.third })
            ?.first
}
