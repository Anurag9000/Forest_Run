package com.anurag9000.forestrun.engine

/** Primitive inputs that determine Garden ambient density and lighting. */
internal data class SanctuaryAtmosphereSignals(
    val mood: ForestMood,
    val moodStreak: Int,
    val warmBondCount: Int,
    val milestoneRewardCount: Int,
    val kindnessStreak: Int,
    val peacefulBiomeCount: Int,
    val hasRepeatFriend: Boolean,
    val hasRepeatedHarm: Boolean,
    val hasFeaturedReward: Boolean,
    val routeTier: PacifistRouteTier,
    val sparedCount: Int,
    val hasRepeatedKindness: Boolean,
    val hasFeaturedPeaceBiome: Boolean,
    val hasFeaturedCostume: Boolean,
    val bloomConversions: Int,
    val hasCactusBloom: Boolean,
    val memoryPageCount: Int
)

internal data class SanctuaryAtmosphere(
    val fireflyCount: Int,
    val petalCount: Int,
    val bloomPatchCount: Int,
    val mistBandCount: Int,
    val lanternGlowCount: Int,
    val groundGlowAlpha: Int,
    val canopyShadeAlpha: Int
)

/**
 * Compose every independent sanctuary atmosphere modifier explicitly.
 *
 * Kotlin's `if` is an expression with low precedence. Chaining raw arithmetic
 * such as `base + if (a) 1 else 0 + if (b) 1 else 0` makes later terms part of
 * an earlier `else` branch. Keeping the model here prevents that silent control
 * flow bug and gives the visual density contract a pure test surface.
 */
internal fun buildSanctuaryAtmosphere(
    signals: SanctuaryAtmosphereSignals
): SanctuaryAtmosphere {
    val moodStreak = signals.moodStreak.coerceAtLeast(0)
    val warmBonds = signals.warmBondCount.coerceAtLeast(0)
    val milestoneRewards = signals.milestoneRewardCount.coerceAtLeast(0)
    val kindnessStreak = signals.kindnessStreak.coerceAtLeast(0)
    val peacefulBiomes = signals.peacefulBiomeCount.coerceAtLeast(0)
    val sparedCount = signals.sparedCount.coerceAtLeast(0)
    val bloomConversions = signals.bloomConversions.coerceAtLeast(0)
    val memoryPages = signals.memoryPageCount.coerceAtLeast(0)
    val mercifulOrBetter =
        signals.routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal

    val routeLift = when (signals.routeTier) {
        PacifistRouteTier.NONE -> 0
        PacifistRouteTier.KIND -> 1
        PacifistRouteTier.MERCIFUL -> 2
        PacifistRouteTier.PEACEFUL -> 3
    }

    val fireflyBase = when (signals.mood) {
        ForestMood.GENTLE -> 4 + moodStreak.coerceAtMost(4)
        ForestMood.RECKLESS -> 1
        ForestMood.FEARFUL -> 2
        ForestMood.STEADY -> 3 + (moodStreak / 2).coerceAtMost(2)
    }
    val fireflies = fireflyBase +
        warmBonds.coerceAtMost(2) +
        milestoneRewards.coerceAtMost(2) +
        (kindnessStreak / 2).coerceAtMost(2) +
        peacefulBiomes.coerceAtMost(2) +
        flag(signals.hasRepeatFriend) -
        flag(signals.hasRepeatedHarm) +
        flag(signals.hasFeaturedReward) +
        routeLift

    val petalBase = when (signals.mood) {
        ForestMood.GENTLE -> 3
        ForestMood.RECKLESS -> 5
        ForestMood.FEARFUL -> 2
        ForestMood.STEADY -> 3
    }
    val petals = petalBase +
        flag(sparedCount > 0) +
        flag(signals.hasRepeatedKindness) +
        flag(signals.hasFeaturedPeaceBiome) +
        flag(signals.hasFeaturedReward && signals.mood != ForestMood.FEARFUL) +
        flag(signals.hasFeaturedCostume) +
        flag(mercifulOrBetter)

    val bloomBase = when (signals.mood) {
        ForestMood.GENTLE -> 2
        ForestMood.RECKLESS -> 0
        ForestMood.FEARFUL -> 1
        ForestMood.STEADY -> 1
    }
    val bloomPatches = bloomBase +
        warmBonds.coerceAtMost(2) / 2 +
        milestoneRewards.coerceAtMost(2) +
        flag(bloomConversions >= 2) +
        (kindnessStreak / 3).coerceAtMost(1) +
        flag(signals.hasFeaturedPeaceBiome) +
        flag(signals.hasFeaturedReward) +
        flag(signals.hasCactusBloom) +
        flag(signals.routeTier == PacifistRouteTier.PEACEFUL)

    val mistBase = when (signals.mood) {
        ForestMood.GENTLE -> 1
        ForestMood.RECKLESS -> 0
        ForestMood.FEARFUL -> 3
        ForestMood.STEADY -> 2
    }
    val mistBands = mistBase +
        flag(signals.hasRepeatedHarm) -
        flag(signals.hasFeaturedPeaceBiome && mercifulOrBetter)

    val lanternGlows = warmBonds.coerceAtMost(3) +
        milestoneRewards.coerceAtMost(2) +
        peacefulBiomes.coerceAtMost(2) +
        flag(signals.hasRepeatFriend) +
        routeLift +
        flag(signals.hasRepeatedKindness && kindnessStreak >= 2)

    val groundGlowBase = when (signals.mood) {
        ForestMood.GENTLE -> 92
        ForestMood.RECKLESS -> 36
        ForestMood.FEARFUL -> 54
        ForestMood.STEADY -> 68
    }
    val groundGlowAlpha = groundGlowBase +
        flag(bloomConversions >= 2, 12) +
        flag(signals.hasFeaturedReward, 8) +
        flag(signals.hasFeaturedCostume, 6) +
        flag(signals.hasFeaturedPeaceBiome, 8) +
        flag(signals.hasCactusBloom, 6) +
        flag(mercifulOrBetter, 16) +
        flag(signals.hasRepeatedKindness, 10)

    val canopyShadeBase = when (signals.mood) {
        ForestMood.GENTLE -> 26
        ForestMood.RECKLESS -> 22
        ForestMood.FEARFUL -> 54
        ForestMood.STEADY -> 18
    }
    val canopyShadeAlpha = canopyShadeBase +
        flag(memoryPages >= 4, 6) +
        flag(signals.hasRepeatedHarm, 10) -
        flag(
            signals.hasFeaturedPeaceBiome &&
                signals.routeTier == PacifistRouteTier.PEACEFUL,
            6
        )

    return SanctuaryAtmosphere(
        fireflyCount = fireflies.coerceAtLeast(0),
        petalCount = petals.coerceAtLeast(0),
        bloomPatchCount = bloomPatches.coerceAtLeast(0),
        mistBandCount = mistBands.coerceAtLeast(0),
        lanternGlowCount = lanternGlows.coerceAtLeast(0),
        groundGlowAlpha = groundGlowAlpha.coerceIn(0, 180),
        canopyShadeAlpha = canopyShadeAlpha.coerceIn(0, 255)
    )
}

private fun flag(condition: Boolean, amount: Int = 1): Int =
    if (condition) amount.coerceAtLeast(0) else 0
