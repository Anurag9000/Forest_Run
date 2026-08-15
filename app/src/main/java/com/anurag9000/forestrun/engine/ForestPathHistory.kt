package com.anurag9000.forestrun.engine

import android.content.Context

internal data class ForestPathMemory(
    val tier: PacifistRouteTier,
    val label: String,
    val runCount: Int,
    val line: String
) {
    init {
        require(tier != PacifistRouteTier.NONE) { "Path history must describe a real route tier" }
        require(label.isNotBlank()) { "Path history label must not be blank" }
        require(runCount >= 0) { "Path history count must be non-negative" }
        require(line.isNotBlank()) { "Path history line must not be blank" }
    }

    val discovered: Boolean
        get() = runCount > 0
}

internal data class ForestPathHistorySnapshot(
    val paths: List<ForestPathMemory>
) {
    init {
        require(paths.isNotEmpty()) { "Path history must contain the gentle route tiers" }
        require(paths.map(ForestPathMemory::tier).distinct().size == paths.size) {
            "Path history tiers must be unique"
        }
        require(paths.none { it.tier == PacifistRouteTier.NONE }) {
            "NONE is not a collectible path history"
        }
    }

    val discoveredTiers: Int
        get() = paths.count(ForestPathMemory::discovered)

    val totalTiers: Int
        get() = paths.size

    val allGentleShapesSeen: Boolean
        get() = discoveredTiers == totalTiers
}

/** Read-only route-history projection; route counters remain owned by SaveManager. */
internal object ForestPathHistoryComposer {
    private val orderedTiers = listOf(
        PacifistRouteTier.KIND,
        PacifistRouteTier.MERCIFUL,
        PacifistRouteTier.PEACEFUL
    )

    fun snapshot(context: Context): ForestPathHistorySnapshot {
        val appContext = context.applicationContext
        return ForestPathHistorySnapshot(
            paths = orderedTiers.map { tier ->
                ForestPathMemory(
                    tier = tier,
                    label = labelFor(tier),
                    runCount = SaveManager.loadRouteTierCount(appContext, tier).coerceAtLeast(0),
                    line = lineFor(tier)
                )
            }
        )
    }

    private fun labelFor(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.KIND -> "Kind Path"
        PacifistRouteTier.MERCIFUL -> "Merciful Path"
        PacifistRouteTier.PEACEFUL -> "Peaceful Path"
        PacifistRouteTier.NONE -> error("NONE has no Journal path label")
    }

    private fun lineFor(tier: PacifistRouteTier): String = when (tier) {
        PacifistRouteTier.KIND ->
            "A run where gentler choices became a pattern the forest could notice, even if the whole path was not untouched."
        PacifistRouteTier.MERCIFUL ->
            "A stronger mercy pattern: restraint held often enough that the route returned home with a distinct remembered shape."
        PacifistRouteTier.PEACEFUL ->
            "The gentlest complete route tier, where the run carried its restraint all the way back to the willow."
        PacifistRouteTier.NONE -> error("NONE has no Journal path description")
    }
}
