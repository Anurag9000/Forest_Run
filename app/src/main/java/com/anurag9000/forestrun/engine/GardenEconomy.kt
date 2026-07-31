package com.anurag9000.forestrun.engine

/** Canonical Garden catalogue progression shared by persistence and UI transactions. */
object GardenEconomy {
    private val seedCosts = intArrayOf(15, 20, 25, 30, 40, 50, 60, 75, 100)

    val catalogueSize: Int
        get() = seedCosts.size

    /** Cost of unlocking the plant at [index], or null when the index is invalid. */
    fun seedCostForIndex(index: Int): Int? = seedCosts.getOrNull(index)
}
