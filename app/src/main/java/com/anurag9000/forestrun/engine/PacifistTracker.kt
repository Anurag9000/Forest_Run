package com.anurag9000.forestrun.engine

data class PacifistReward(
    val kind: PacifistRewardKind,
    val message: String,
    val points: Int,
    val seeds: Int,
    val friendBiome: Biome? = null,
    val routeTier: PacifistRouteTier? = null
)

/** Tracks clean-play and mercy-oriented progress within a run. */
class PacifistTracker {

    companion object {
        private const val MAX_PENDING_REWARDS = 16

        fun routeTierFor(
            mercyHearts: Int,
            kindnessChain: Int,
            cleanPasses: Int,
            sparedCount: Int,
            hitsTaken: Int
        ): PacifistRouteTier = when {
            hitsTaken == 0 && mercyHearts >= 5 && sparedCount >= 2 && cleanPasses >= 10 ->
                PacifistRouteTier.PEACEFUL
            hitsTaken == 0 &&
                (sparedCount >= 2 ||
                    (mercyHearts >= 3 && kindnessChain >= 7 && cleanPasses >= 6)) ->
                PacifistRouteTier.MERCIFUL
            hitsTaken <= 1 &&
                (mercyHearts >= 2 ||
                    sparedCount >= 1 ||
                    (kindnessChain >= 4 && cleanPasses >= 4)) ->
                PacifistRouteTier.KIND
            else -> PacifistRouteTier.NONE
        }

        private fun saturatingIncrement(value: Int): Int =
            if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value.coerceAtLeast(0) + 1
    }

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
    private val pendingRewards = ArrayDeque<PacifistReward>(MAX_PENDING_REWARDS)
    private var highestRewardedRouteTier: PacifistRouteTier = PacifistRouteTier.NONE

    fun reset() {
        cleanPassesThisRun = 0
        sparedThisRun = 0
        hitsThisRun = 0
        currentBiome = null
        cleanPassesThisBiome = 0
        sparedThisBiome = 0
        wasHitThisBiome = false
        pendingRewards.clear()
        highestRewardedRouteTier = PacifistRouteTier.NONE
    }

    fun updateBiome(biome: Biome) {
        val previousBiome = currentBiome
        if (previousBiome == null) {
            currentBiome = biome
            return
        }

        if (previousBiome != biome) {
            if (!wasHitThisBiome && cleanPassesThisBiome >= 3) {
                val biomeName = previousBiome.name.lowercase().replace('_', ' ')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                enqueueReward(
                    PacifistReward(
                        kind = PacifistRewardKind.BIOME_FRIENDSHIP,
                        message = "$biomeName at peace",
                        points = 380,
                        seeds = 2,
                        friendBiome = previousBiome,
                        routeTier = currentRouteTier(0, 0)
                    )
                )
            }

            currentBiome = biome
            cleanPassesThisBiome = 0
            sparedThisBiome = 0
            wasHitThisBiome = false
        }
    }

    fun recordCleanPass() {
        cleanPassesThisRun = saturatingIncrement(cleanPassesThisRun)
        cleanPassesThisBiome = saturatingIncrement(cleanPassesThisBiome)
        if (cleanPassesThisRun % 5 == 0) {
            enqueueReward(
                PacifistReward(
                    kind = PacifistRewardKind.CLEAN_STREAK,
                    message = "Kindness carries",
                    points = 150,
                    seeds = 1
                )
            )
        }
    }

    fun recordSpare() {
        sparedThisRun = saturatingIncrement(sparedThisRun)
        sparedThisBiome = saturatingIncrement(sparedThisBiome)
        if (sparedThisRun % 2 == 0) {
            enqueueReward(
                PacifistReward(
                    kind = PacifistRewardKind.SPARE_STREAK,
                    message = "Mercy kept",
                    points = 240,
                    seeds = 1
                )
            )
        }
    }

    fun recordHit() {
        hitsThisRun = saturatingIncrement(hitsThisRun)
        wasHitThisBiome = true
    }

    fun updateRouteReward(mercyHearts: Int, kindnessChain: Int) {
        val tier = currentRouteTier(mercyHearts, kindnessChain)
        if (tier == PacifistRouteTier.NONE || tier.ordinal <= highestRewardedRouteTier.ordinal) {
            return
        }

        val reward = when (tier) {
            PacifistRouteTier.KIND -> PacifistReward(
                kind = PacifistRewardKind.ROUTE_KIND,
                message = "Mercy noticed",
                points = 180,
                seeds = 1,
                routeTier = tier
            )
            PacifistRouteTier.MERCIFUL -> PacifistReward(
                kind = PacifistRewardKind.ROUTE_MERCIFUL,
                message = "Merciful route",
                points = 320,
                seeds = 2,
                routeTier = tier
            )
            PacifistRouteTier.PEACEFUL -> PacifistReward(
                kind = PacifistRewardKind.ROUTE_PEACEFUL,
                message = "Forest at peace",
                points = 520,
                seeds = 3,
                routeTier = tier
            )
            PacifistRouteTier.NONE -> return
        }
        if (enqueueReward(reward)) {
            highestRewardedRouteTier = tier
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
        if (pendingRewards.isEmpty()) null else pendingRewards.removeFirst()

    private fun enqueueReward(reward: PacifistReward): Boolean {
        if (pendingRewards.size >= MAX_PENDING_REWARDS) return false
        pendingRewards.addLast(reward)
        return true
    }
}
