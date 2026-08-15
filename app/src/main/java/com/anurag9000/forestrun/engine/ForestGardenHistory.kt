package com.anurag9000.forestrun.engine

import android.content.Context

internal enum class ForestGardenPlantState {
    GROWN,
    NEXT,
    LOCKED
}

internal data class ForestGardenPlantMemory(
    val index: Int,
    val displayName: String,
    val seedCost: Int,
    val state: ForestGardenPlantState,
    val affordableNow: Boolean
) {
    init {
        require(index >= 0) { "Garden memory index must be non-negative" }
        require(displayName.isNotBlank()) { "Garden memory name must not be blank" }
        require(seedCost > 0) { "Garden memory cost must be positive" }
        require(!affordableNow || state == ForestGardenPlantState.NEXT) {
            "Only the next Garden plant can be immediately affordable"
        }
    }
}

internal data class ForestGardenHistorySnapshot(
    val plants: List<ForestGardenPlantMemory>,
    val unlockedCount: Int,
    val availableSeeds: Int
) {
    init {
        require(plants.isNotEmpty()) { "Garden history must expose the canonical catalogue" }
        require(unlockedCount in 1..plants.size) { "Garden history unlocked count is invalid" }
        require(availableSeeds >= 0) { "Garden history Seed balance must be non-negative" }
        require(plants.map(ForestGardenPlantMemory::index) == plants.indices.toList()) {
            "Garden history indices must remain canonical"
        }
        require(plants.count { it.state == ForestGardenPlantState.GROWN } == unlockedCount) {
            "Garden history grown entries must match persisted progress"
        }
        require(plants.count { it.state == ForestGardenPlantState.NEXT } <= 1) {
            "Garden history can expose at most one next purchase"
        }
    }

    val complete: Boolean
        get() = unlockedCount == plants.size

    val nextPlant: ForestGardenPlantMemory?
        get() = plants.firstOrNull { it.state == ForestGardenPlantState.NEXT }
}

/** Read-only Garden projection for the Journal; purchases remain GardenPurchaseManager-owned. */
internal object ForestGardenHistoryComposer {
    fun snapshot(context: Context): ForestGardenHistorySnapshot {
        val appContext = context.applicationContext
        val unlocked = SaveManager.loadGardenProgress(appContext)
            .coerceIn(1, GardenEconomy.catalogueSize)
        val seeds = SaveManager.loadLifetimeSeeds(appContext).coerceAtLeast(0)
        val plants = GardenEconomy.entries.map { plant ->
            val state = when {
                plant.index < unlocked -> ForestGardenPlantState.GROWN
                plant.index == unlocked && unlocked < GardenEconomy.catalogueSize ->
                    ForestGardenPlantState.NEXT
                else -> ForestGardenPlantState.LOCKED
            }
            ForestGardenPlantMemory(
                index = plant.index,
                displayName = plant.displayName,
                seedCost = plant.seedCost,
                state = state,
                affordableNow = state == ForestGardenPlantState.NEXT && seeds >= plant.seedCost
            )
        }
        return ForestGardenHistorySnapshot(
            plants = plants,
            unlockedCount = unlocked,
            availableSeeds = seeds
        )
    }
}
