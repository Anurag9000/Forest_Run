package com.yourname.forest_run.engine

data class PacifistReward(
    val message: String,
    val points: Int,
    val seeds: Int
)

/**
 * Tracks clean-play and mercy-oriented progress within a run.
 */
class PacifistTracker {

    var cleanPassesThisRun: Int = 0
        private set
    var sparedThisRun: Int = 0
        private set
    var hitsThisRun: Int = 0
        private set

    private var currentBiome: Biome? = null
    private var cleanPassesThisBiome: Int = 0
    private var sparedThisBiome: Int = 0
    private var wasHitThisBiome: Boolean = false
    private var pendingReward: PacifistReward? = null

    fun reset() {
        cleanPassesThisRun = 0
        sparedThisRun = 0
        hitsThisRun = 0
        currentBiome = null
        cleanPassesThisBiome = 0
        sparedThisBiome = 0
        wasHitThisBiome = false
        pendingReward = null
    }

    fun updateBiome(biome: Biome) {
        if (currentBiome == null) {
            currentBiome = biome
            return
        }

        if (currentBiome != biome) {
            if (!wasHitThisBiome && cleanPassesThisBiome >= 3) {
                val biomeName = currentBiome!!.name.lowercase().replace('_', ' ')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                pendingReward = PacifistReward(
                    message = "$biomeName befriended",
                    points = 350,
                    seeds = 2
                )
            }

            currentBiome = biome
            cleanPassesThisBiome = 0
            sparedThisBiome = 0
            wasHitThisBiome = false
        }
    }

    fun recordCleanPass() {
        cleanPassesThisRun++
        cleanPassesThisBiome++
        if (cleanPassesThisRun % 5 == 0) {
            pendingReward = PacifistReward(
                message = "Kindness streak +",
                points = 150,
                seeds = 1
            )
        }
    }

    fun recordSpare() {
        sparedThisRun++
        sparedThisBiome++
        if (sparedThisRun % 2 == 0) {
            pendingReward = PacifistReward(
                message = "Spare bonus",
                points = 220,
                seeds = 1
            )
        }
    }

    fun recordHit() {
        hitsThisRun++
        wasHitThisBiome = true
    }

    fun consumeReward(): PacifistReward? =
        pendingReward.also { pendingReward = null }
}
