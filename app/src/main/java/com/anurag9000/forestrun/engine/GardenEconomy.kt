package com.anurag9000.forestrun.engine

/** One stable Garden catalogue entry used by progression and player-facing memory views. */
data class GardenPlantEconomy(
    val index: Int,
    val displayName: String,
    val compactName: String,
    val seedCost: Int
) {
    init {
        require(index >= 0) { "Garden plant index must be non-negative" }
        require(displayName.isNotBlank()) { "Garden plant name must not be blank" }
        require(compactName.isNotBlank()) { "Garden compact plant name must not be blank" }
        require(seedCost > 0) { "Garden plant Seed cost must be positive" }
    }
}

/**
 * Canonical Garden catalogue progression shared by persistence and player-facing
 * collection surfaces.
 *
 * Visual sprite/color ownership remains with GardenScreen/SpriteManager; this
 * object owns stable progression order, full/compact names, and Seed costs.
 */
object GardenEconomy {
    private val catalogue = listOf(
        GardenPlantEconomy(0, "Lily", "Lily", 15),
        GardenPlantEconomy(1, "Cactus", "Cactus", 20),
        GardenPlantEconomy(2, "Hyacinth", "Hyacinth", 25),
        GardenPlantEconomy(3, "Eucalyptus", "Eucalyptus", 30),
        GardenPlantEconomy(4, "Vanilla Orchid", "Orchid", 40),
        GardenPlantEconomy(5, "Weeping Willow", "Willow", 50),
        GardenPlantEconomy(6, "Jacaranda", "Jacaranda", 60),
        GardenPlantEconomy(7, "Bamboo", "Bamboo", 75),
        GardenPlantEconomy(8, "Cherry Blossom", "Cherry", 100)
    ).also { entries ->
        require(entries.map(GardenPlantEconomy::index) == entries.indices.toList()) {
            "Garden catalogue indices must remain contiguous and ordered"
        }
        require(entries.map(GardenPlantEconomy::displayName).distinct().size == entries.size) {
            "Garden catalogue full names must remain unique"
        }
        require(entries.map(GardenPlantEconomy::compactName).distinct().size == entries.size) {
            "Garden catalogue compact names must remain unique"
        }
    }

    val entries: List<GardenPlantEconomy>
        get() = catalogue

    val catalogueSize: Int
        get() = catalogue.size

    /** Stable catalogue entry at [index], or null when the index is invalid. */
    fun plantForIndex(index: Int): GardenPlantEconomy? = catalogue.getOrNull(index)

    /** Cost of unlocking the plant at [index], or null when the index is invalid. */
    fun seedCostForIndex(index: Int): Int? = plantForIndex(index)?.seedCost
}
