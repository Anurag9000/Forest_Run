package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Color
import com.yourname.forest_run.entities.EntityType

data class SanctuaryTrace(
    val type: EntityType,
    val label: String,
    val color: Int
)

data class GardenSanctuaryState(
    val sanctuaryLine: String = "",
    val carryHomeLine: String = "",
    val arrivalBadge: String = "",
    val featuredRewardLine: String = "",
    val fireflyCount: Int = 0,
    val petalCount: Int = 0,
    val bloomPatchCount: Int = 0,
    val mistBandCount: Int = 0,
    val lanternGlowCount: Int = 0,
    val groundGlowAlpha: Int = 0,
    val canopyShadeAlpha: Int = 0,
    val traces: List<SanctuaryTrace> = emptyList()
)

object GardenSanctuaryPlanner {

    fun build(context: Context, summary: RunSummary?): GardenSanctuaryState {
        val appContext = context.applicationContext
        val moodState = ForestMoodSystem.currentState(appContext)
        val bonds = RelationshipArcSystem.relationshipsAtOrAbove(appContext, RelationshipStage.TRUST)
        val warmBonds = bonds.filter { RelationshipArcSystem.isWarmBond(appContext, it.first) }
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val memoryPages = StoryFragmentSystem.memoryPageCount(appContext)
        val mood = moodState.currentMood
        val milestoneRewards = RelationshipArcSystem.unlockedMilestoneTypes(appContext)
            .mapNotNull { RelationshipArcSystem.milestoneRewardFor(appContext, it) }
        val featuredReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val repeatedKillerCreature = PersistentMemoryManager.featuredRepeatKiller(appContext)
        val repeatedHarmCreature = PersistentMemoryManager.featuredTenderCreature(appContext)
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
                PersistentMemoryManager.getHitCount(appContext, it) >= 2
            }
        val repeatedKindnessCreature = PersistentMemoryManager.featuredWarmCreature(appContext)
        val kindnessStreak = repeatedKindnessCreature?.let { PersistentMemoryManager.getKindnessStreak(appContext, it) } ?: 0
        val routeTier = summary?.pacifistRouteTier ?: PacifistRouteTier.NONE
        val featuredRewardLine = featuredReward?.let { reward ->
            reward.costumeReward?.let { costume ->
                "${reward.summary} ${costume.displayName} is waiting in the wardrobe."
            } ?: reward.summary
        }.orEmpty()

        val fireflies = when (mood) {
            ForestMood.GENTLE -> 4 + moodState.moodStreak.coerceAtMost(4)
            ForestMood.RECKLESS -> 1
            ForestMood.FEARFUL -> 2
            ForestMood.STEADY -> 3 + (moodState.moodStreak / 2).coerceAtMost(2)
        } + warmBonds.size.coerceAtMost(2) + milestoneRewards.size.coerceAtMost(2) + (kindnessStreak / 2).coerceAtMost(2) + if (repeatFriend != null) 1 else 0 - if (repeatedHarmCreature != null) 1 else 0 +
            when (routeTier) {
                PacifistRouteTier.NONE -> 0
                PacifistRouteTier.KIND -> 1
                PacifistRouteTier.MERCIFUL -> 2
                PacifistRouteTier.PEACEFUL -> 3
            }

        val petals = when (mood) {
            ForestMood.GENTLE -> 3
            ForestMood.RECKLESS -> 5
            ForestMood.FEARFUL -> 2
            ForestMood.STEADY -> 3
        } + if ((summary?.sparedCount ?: 0) > 0) 1 else 0 + if (repeatedKindnessCreature != null) 1 else 0 +
            if (routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal) 1 else 0

        val bloomPatches = when (mood) {
            ForestMood.GENTLE -> 2
            ForestMood.RECKLESS -> 0
            ForestMood.FEARFUL -> 1
            ForestMood.STEADY -> 1
        } + warmBonds.size.coerceAtMost(2) / 2 + milestoneRewards.size.coerceAtMost(2) + if ((summary?.bloomConversions ?: 0) >= 2) 1 else 0 + (kindnessStreak / 3).coerceAtMost(1) +
            if (routeTier == PacifistRouteTier.PEACEFUL) 1 else 0

        val mistBands = when (mood) {
            ForestMood.GENTLE -> 1
            ForestMood.RECKLESS -> 0
            ForestMood.FEARFUL -> 3
            ForestMood.STEADY -> 2
        } + if (repeatedHarmCreature != null) 1 else 0

        val lanternGlows = warmBonds.size.coerceAtMost(3) +
            milestoneRewards.size.coerceAtMost(2) +
            if (repeatFriend != null) 1 else 0 +
            when (routeTier) {
                PacifistRouteTier.NONE -> 0
                PacifistRouteTier.KIND -> 1
                PacifistRouteTier.MERCIFUL -> 2
                PacifistRouteTier.PEACEFUL -> 3
            } +
            if (repeatedKindnessCreature != null && kindnessStreak >= 2) 1 else 0

        val groundGlowAlpha = when (mood) {
            ForestMood.GENTLE -> 92
            ForestMood.RECKLESS -> 36
            ForestMood.FEARFUL -> 54
            ForestMood.STEADY -> 68
        } + if ((summary?.bloomConversions ?: 0) >= 2) 12 else 0 +
            if (routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal) 16 else 0 +
            if (repeatedKindnessCreature != null) 10 else 0

        val canopyShadeAlpha = when (mood) {
            ForestMood.GENTLE -> 26
            ForestMood.RECKLESS -> 22
            ForestMood.FEARFUL -> 54
            ForestMood.STEADY -> 18
        } + if (memoryPages >= 4) 6 else 0 + if (repeatedHarmCreature != null) 10 else 0

        val traces = buildList {
            if (repeatedHarmCreature != null) {
                add(
                    SanctuaryTrace(
                        repeatedHarmCreature,
                        "Cautious Path",
                        Color.rgb(206, 214, 238)
                    )
                )
            }
            if (repeatedKindnessCreature != null &&
                repeatedKindnessCreature != repeatFriend &&
                milestoneRewards.none { it.type == repeatedKindnessCreature }
            ) {
                add(
                    SanctuaryTrace(
                        repeatedKindnessCreature,
                        "Trust Path",
                        Color.rgb(238, 248, 202)
                    )
                )
            }
            if (repeatFriend != null && milestoneRewards.none { it.type == repeatFriend }) {
                add(
                    SanctuaryTrace(
                        repeatFriend,
                        "Shared Path",
                        Color.rgb(248, 236, 198)
                    )
                )
            }
            milestoneRewards.take(2).forEach { reward ->
                add(
                    SanctuaryTrace(
                        reward.type,
                        reward.traceLabel,
                        when (reward.type) {
                            EntityType.CAT -> Color.rgb(255, 230, 239)
                            EntityType.FOX -> Color.rgb(255, 214, 152)
                            EntityType.WOLF -> Color.rgb(202, 216, 240)
                            EntityType.DOG -> Color.rgb(255, 236, 168)
                            EntityType.OWL -> Color.rgb(218, 220, 255)
                            EntityType.EAGLE -> Color.rgb(214, 232, 255)
                            else -> Color.rgb(232, 246, 212)
                        }
                    )
                )
            }
            bonds.take(3).forEach { (type, _) ->
                add(when (type) {
                EntityType.CAT -> SanctuaryTrace(type, "Warm Grass", Color.rgb(255, 226, 240))
                EntityType.FOX -> SanctuaryTrace(type, "Bright Trail", Color.rgb(255, 208, 142))
                EntityType.WOLF -> SanctuaryTrace(type, "Quiet Watch", Color.rgb(198, 212, 236))
                EntityType.DOG -> SanctuaryTrace(type, "Happy Paws", Color.rgb(255, 230, 154))
                EntityType.OWL -> SanctuaryTrace(type, "Lantern Branch", Color.rgb(212, 214, 255))
                EntityType.EAGLE -> SanctuaryTrace(type, "Sky Hush", Color.rgb(210, 228, 255))
                else -> SanctuaryTrace(type, "Kind Trace", Color.rgb(232, 246, 212))
                })
            }
        }

        val arrivalBadge = when {
            repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature -> "Same Shadow"
            repeatedHarmCreature != null -> "Tender Return"
            routeTier == PacifistRouteTier.PEACEFUL -> "Peace Kept"
            routeTier == PacifistRouteTier.MERCIFUL -> "Mercy Stayed"
            routeTier == PacifistRouteTier.KIND -> "Kindness Stayed"
            featuredReward != null -> featuredReward.label
            repeatFriend != null -> "Familiar Return"
            repeatedKindnessCreature != null && kindnessStreak >= 2 -> "Trust Kept"
            warmBonds.isNotEmpty() -> "Known Footsteps"
            mood == ForestMood.FEARFUL -> "Soft Landing"
            mood == ForestMood.GENTLE -> "Quiet Home"
            mood == ForestMood.RECKLESS -> "Settling Air"
            else -> "Homecoming"
        }

        val sanctuaryLine = when (mood) {
            ForestMood.FEARFUL -> if (repeatedHarmCreature != null) {
                "The sanctuary keeps extra quiet around what still feels tender."
            } else {
                "The sanctuary lowers its voice until your breathing does too."
            }
            ForestMood.GENTLE -> if (routeTier == PacifistRouteTier.PEACEFUL) {
                "The sanctuary has started keeping the whole shape of your peaceful runs."
            } else if (repeatFriend != null) {
                "The sanctuary has started behaving like some bonds expect your return, not just welcome it."
            } else if (warmBonds.isNotEmpty()) {
                "The sanctuary opens faster when you keep coming home gently."
            } else if (repeatedKindnessCreature != null) {
                "The sanctuary has started trusting the gentler habits you keep repeating."
            } else {
                "The sanctuary keeps the softer shape of your footsteps."
            }
            ForestMood.RECKLESS -> "Even stirred-up air can settle once it reaches home."
            ForestMood.STEADY -> if (traces.isNotEmpty()) {
                "Steady returns have started leaving visible traces here."
            } else {
                "The sanctuary keeps a calm shape for ordinary returns."
            }
        }

        val strongestBond = bonds.firstOrNull()?.first
        val carryHomeLine = when {
            repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature ->
                "${formatEntityName(repeatedKillerCreature)} has started to feel like the shape your trouble keeps taking."
            repeatedHarmCreature != null ->
                "${formatEntityName(repeatedHarmCreature)} still lingers in the way the garden holds itself tonight."
            featuredReward != null ->
                featuredRewardLine
            repeatFriend != null ->
                "${formatEntityName(repeatFriend)} has started to feel less like a visit and more like a familiar part of home."
            repeatedKindnessCreature != null && kindnessStreak >= 2 ->
                "${formatEntityName(repeatedKindnessCreature)} has started leaving trust behind instead of only memory."
            routeTier == PacifistRouteTier.KIND ->
                "The garden kept the kinder shape of that run instead of letting it vanish immediately."
            routeTier == PacifistRouteTier.PEACEFUL ->
                "The garden kept the quiet of that peaceful run instead of letting it disappear."
            routeTier == PacifistRouteTier.MERCIFUL ->
                "Mercy stayed in the garden long enough to change how it holds itself tonight."
            strongestBond != null && (summary?.sparedCount ?: 0) > 0 ->
                "${formatEntityName(strongestBond)} stayed in the garden's mood after that run."
            strongestBond != null && (summary?.bloomConversions ?: 0) >= 2 ->
                "${formatEntityName(strongestBond)} still lingers in the afterglow you carried back."
            strongestBond != null && RelationshipArcSystem.isWarmBond(appContext, strongestBond) ->
                "${formatEntityName(strongestBond)} has started to feel like part of home."
            (summary?.forestMood ?: mood) == ForestMood.FEARFUL ->
                "Nothing here asks you to hurry before you are ready."
            else ->
                "The garden keeps a little of the run instead of sending all of it away."
        }

        return GardenSanctuaryState(
            sanctuaryLine = sanctuaryLine,
            carryHomeLine = carryHomeLine,
            arrivalBadge = arrivalBadge,
            featuredRewardLine = featuredRewardLine,
            fireflyCount = fireflies,
            petalCount = petals,
            bloomPatchCount = bloomPatches,
            mistBandCount = mistBands,
            lanternGlowCount = lanternGlows,
            groundGlowAlpha = groundGlowAlpha.coerceAtMost(180),
            canopyShadeAlpha = canopyShadeAlpha,
            traces = traces
        )
    }

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
