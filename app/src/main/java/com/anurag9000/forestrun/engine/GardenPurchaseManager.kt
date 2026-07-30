package com.anurag9000.forestrun.engine

import android.content.Context

/** Result of attempting to unlock exactly the next Garden plant. */
data class GardenPurchaseResult(
    val status: GardenPurchaseStatus,
    val unlockedCount: Int,
    val remainingSeeds: Int
) {
    val purchased: Boolean
        get() = status == GardenPurchaseStatus.PURCHASED
}

enum class GardenPurchaseStatus {
    PURCHASED,
    INVALID_REQUEST,
    NOT_NEXT_UNLOCK,
    CATALOGUE_COMPLETE,
    INSUFFICIENT_SEEDS,
    WRITE_FAILED
}

/**
 * Serializes Garden progression and Seed spending through one SharedPreferences
 * commit. The active compatibility namespace selected by [SaveIntegrityManager]
 * is respected, and cached UI state is never trusted as the source of truth.
 */
object GardenPurchaseManager {
    private const val KEY_GARDEN_UNLOCKED = "garden_unlocked"
    private const val KEY_LIFETIME_SEEDS = "lifetime_seeds"

    @Synchronized
    fun purchaseNext(
        context: Context,
        requestedIndex: Int,
        seedCost: Int,
        catalogueSize: Int
    ): GardenPurchaseResult {
        if (catalogueSize <= 0 || requestedIndex < 0 || seedCost < 0) {
            return GardenPurchaseResult(
                status = GardenPurchaseStatus.INVALID_REQUEST,
                unlockedCount = 1,
                remainingSeeds = 0
            )
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(
            SaveManager.activePrefsNameForTests,
            Context.MODE_PRIVATE
        )
        val unlocked = prefs.getInt(KEY_GARDEN_UNLOCKED, 1)
            .coerceIn(1, catalogueSize)
        val seeds = prefs.getInt(KEY_LIFETIME_SEEDS, 0)
            .coerceAtLeast(0)

        val rejection = when {
            unlocked >= catalogueSize -> GardenPurchaseStatus.CATALOGUE_COMPLETE
            requestedIndex != unlocked -> GardenPurchaseStatus.NOT_NEXT_UNLOCK
            seeds < seedCost -> GardenPurchaseStatus.INSUFFICIENT_SEEDS
            else -> null
        }
        if (rejection != null) {
            return GardenPurchaseResult(rejection, unlocked, seeds)
        }

        val nextUnlocked = unlocked + 1
        val remainingSeeds = seeds - seedCost
        val committed = prefs.edit()
            .putInt(KEY_GARDEN_UNLOCKED, nextUnlocked)
            .putInt(KEY_LIFETIME_SEEDS, remainingSeeds)
            .commit()

        return if (committed) {
            GardenPurchaseResult(
                status = GardenPurchaseStatus.PURCHASED,
                unlockedCount = nextUnlocked,
                remainingSeeds = remainingSeeds
            )
        } else {
            GardenPurchaseResult(
                status = GardenPurchaseStatus.WRITE_FAILED,
                unlockedCount = unlocked,
                remainingSeeds = seeds
            )
        }
    }
}
