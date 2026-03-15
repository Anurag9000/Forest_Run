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
        SaveManager.incrementEncounterCount(context.applicationContext, type)
    }

    fun recordSpare(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementSparedCount(appContext, type)
    }

    fun recordHit(context: Context, type: EntityType) {
        val appContext = context.applicationContext
        SaveManager.incrementHitCount(appContext, type)
        SaveManager.saveLastKiller(appContext, type)
    }

    fun getEncounterCount(context: Context, type: EntityType): Int =
        SaveManager.loadEncounterCount(context.applicationContext, type)

    fun getSparedCount(context: Context, type: EntityType): Int =
        SaveManager.loadSparedCount(context.applicationContext, type)

    fun getHitCount(context: Context, type: EntityType): Int =
        SaveManager.loadHitCount(context.applicationContext, type)

    fun getLastKiller(context: Context): EntityType? =
        SaveManager.loadLastKiller(context.applicationContext)
}
