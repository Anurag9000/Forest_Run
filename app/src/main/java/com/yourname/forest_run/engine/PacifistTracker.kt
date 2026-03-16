package com.yourname.forest_run.engine

data class PacifistReward(
    val message: String,
    val points: Int,
    val seeds: Int,
    val friendBiome: Biome? = null,
    val routeTier: PacifistRouteTier? = null
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
    private var highestRewardedRouteTier: PacifistRouteTier = PacifistRouteTier.NONE

    fun reset() {
        cleanPassesThisRun = 0
        sparedThisRun = 0
        hitsThisRun = 0
        currentBiome = null
        cleanPassesThisBiome = 0
        sparedThisBiome = 0
        wasHitThisBiome = false
        pendingReward = null
        highestRewardedRouteTier = PacifistRouteTier.NONE
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
                    message = "$biomeName at peace",
                    points = 380,
                    seeds = 2,
                    friendBiome = currentBiome,
                    routeTier = currentRouteTier(0, 0)
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
                message = "Kindness carries",
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
                message = "Mercy kept",
                points = 240,
                seeds = 1
            )
        }
    }

    fun recordHit() {
        hitsThisRun++
        wasHitThisBiome = true
    }

    fun updateRouteReward(mercyHearts: Int, kindnessChain: Int) {
        val tier = currentRouteTier(mercyHearts, kindnessChain)
        if (tier == PacifistRouteTier.NONE || tier.ordinal <= highestRewardedRouteTier.ordinal || pendingReward != null) {
            return
        }
        highestRewardedRouteTier = tier
        pendingReward = when (tier) {
            PacifistRouteTier.KIND -> PacifistReward(
                message = "Mercy noticed",
                points = 180,
                seeds = 1,
                routeTier = tier
            )
            PacifistRouteTier.MERCIFUL -> PacifistReward(
                message = "Merciful route",
                points = 320,
                seeds = 2,
                routeTier = tier
            )
            PacifistRouteTier.PEACEFUL -> PacifistReward(
                message = "Forest at peace",
                points = 520,
                seeds = 3,
                routeTier = tier
            )
            PacifistRouteTier.NONE -> null
        }
    }

    fun currentRouteTier(mercyHearts: Int, kindnessChain: Int): PacifistRouteTier =
        routeTierFor(
            mercyHearts = mercyHearts,
            kindnessChain = kindnessChain,
            cleanPasses = cleanPassesThisRun,
            sparedCount = sparedThisRun,
            hitsTaken = hitsThisRun
        )

    fun consumeReward(): PacifistReward? =
        pendingReward.also { pendingReward = null }

    companion object {
        fun routeTierFor(
            mercyHearts: Int,
            kindnessChain: Int,
            cleanPasses: Int,
            sparedCount: Int,
            hitsTaken: Int
        ): PacifistRouteTier = when {
            hitsTaken == 0 && mercyHearts >= 5 && sparedCount >= 2 && cleanPasses >= 10 ->
                PacifistRouteTier.PEACEFUL
            hitsTaken == 0 && (sparedCount >= 2 || (mercyHearts >= 3 && kindnessChain >= 7 && cleanPasses >= 6)) ->
                PacifistRouteTier.MERCIFUL
            hitsTaken <= 1 && (mercyHearts >= 2 || sparedCount >= 1 || (kindnessChain >= 4 && cleanPasses >= 4)) ->
                PacifistRouteTier.KIND
            else -> PacifistRouteTier.NONE
        }
    }
}
