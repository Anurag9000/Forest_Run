package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Color
import com.anurag9000.forestrun.entities.EntityType

data class SanctuaryTrace(
    val type: EntityType,
    val label: String,
    val color: Int
)

data class HomecomingConsequence(
    val label: String,
    val line: String
)

data class GardenSanctuaryState(
    val sanctuaryLine: String = "",
    val carryHomeLine: String = "",
    val arrivalBadge: String = "",
    val worldOpinionLabel: String = "",
    val worldOpinionLine: String = "",
    val homeCharacterLabel: String = "",
    val homeCharacterLine: String = "",
    val homecomingConsequences: List<HomecomingConsequence> = emptyList(),
    val featuredRewardLine: String = "",
    val featuredPeaceBiome: Biome? = null,
    val featuredPeaceLabel: String = "",
    val featuredPeaceLine: String = "",
    val routeWorldLabel: String = "",
    val routeWorldLine: String = "",
    val featuredCostumeLabel: String = "",
    val featuredCostumeLine: String = "",
    val activeCostumeLabel: String = "",
    val activeCostumeLine: String = "",
    val featuredPresenceLabel: String = "",
    val featuredPresenceLine: String = "",
    val featuredRitualLabel: String = "",
    val featuredRitualLine: String = "",
    val featuredVisitor: EntityType? = null,
    val featuredVisitorTitle: String = "",
    val featuredVisitorLine: String = "",
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
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(appContext, RelationshipStage.TRUST)
        val memoryPages = StoryFragmentSystem.memoryPageCount(appContext)
        val mood = moodState.currentMood
        val milestoneRewards = RelationshipArcSystem.unlockedMilestoneTypes(appContext)
            .mapNotNull { RelationshipArcSystem.milestoneRewardFor(appContext, it) }
        val featuredReward = RelationshipArcSystem.featuredMilestoneReward(appContext)
        val historySnapshot = PersistentMemoryManager.repeatedHistorySnapshot(appContext)
        val historyUnlock = historySnapshot.featuredUnlock
        val repeatedKillerCreature = historySnapshot.featuredRepeatKiller
        val repeatedHarmCreature = historySnapshot.featuredTenderCreature
            ?: (summary?.lastKiller ?: PersistentMemoryManager.getLastKiller(appContext))?.takeIf {
                PersistentMemoryManager.getHitCount(appContext, it) >= 2
            }
        val repeatedKindnessCreature = historySnapshot.featuredWarmCreature
        val kindnessStreak = historySnapshot.featuredWarmStreak
        val routeTier = summary?.pacifistRouteTier ?: PacifistRouteTier.NONE
        val routeWorldState = PacifistPresentation.routeWorldState(appContext, routeTier)
        val worldOpinion = WorldOpinionPresentation.current(appContext, summary = summary, routeTierOverride = routeTier)
        val featuredCostume = CostumeManager.featuredPresentation(appContext)
        val activeCostume = CostumeManager.activePresentation(appContext)
        val peacefulBiomes = PersistentMemoryManager.peacefulBiomes(appContext)
        val featuredPeaceBiome = historySnapshot.featuredPeaceBiome ?: peacefulBiomes.firstOrNull()
        val cactusBloom = historySnapshot.featuredCleanPass?.takeIf { it.type == EntityType.CACTUS }
        val featuredRewardLine = featuredReward?.let { reward ->
            reward.costumeReward?.let { costume ->
                "${reward.summary} ${costume.displayName} is waiting in the wardrobe."
            } ?: reward.summary
        }.orEmpty()
        val featuredPeaceLabel = featuredPeaceBiome?.let { peace ->
            when (routeTier) {
                PacifistRouteTier.PEACEFUL -> "${peace.biome.displayName} At Peace"
                PacifistRouteTier.MERCIFUL -> "${peace.biome.displayName} Softened"
                PacifistRouteTier.KIND -> "${peace.biome.displayName} Answered Kindly"
                PacifistRouteTier.NONE -> if (peace.friendshipCount >= 2) "${peace.biome.displayName} Remembers" else ""
            }
        }.orEmpty()
        val featuredPeaceLine = featuredPeaceBiome?.let { peace ->
            when (routeTier) {
                PacifistRouteTier.PEACEFUL ->
                    "${peace.biome.displayName} still feels at peace with the way you crossed it."
                PacifistRouteTier.MERCIFUL ->
                    "${peace.biome.displayName} sounds less guarded after the mercy you left there."
                PacifistRouteTier.KIND ->
                    "${peace.biome.displayName} kept a softer opinion of your return."
                PacifistRouteTier.NONE ->
                    if (peace.friendshipCount >= 2) {
                        "${peace.biome.displayName} still lingers in the garden like a place that learned your gentler steps."
                    } else {
                        ""
                    }
            }
        }.orEmpty()
        val featuredCostumeLabel = featuredCostume?.signLabel.orEmpty()
        val featuredCostumeLine = featuredCostume?.signLine.orEmpty()
        val activeCostumeLabel = activeCostume?.activeLabel.orEmpty()
        val activeCostumeLine = activeCostume?.activeLine.orEmpty()
        val featuredPresenceLabel = featuredReward?.homePresenceLabel.orEmpty()
        val featuredPresenceLine = featuredReward?.homePresenceLine.orEmpty()
        val featuredRitualLabel = featuredReward?.bondRitualLabel.orEmpty()
        val featuredRitualLine = featuredReward?.bondRitualLine.orEmpty()
        val featuredVisitor = featuredReward?.type
        val featuredVisitorTitle = featuredReward?.gardenReactionTitle.orEmpty()
        val featuredVisitorLine = featuredReward?.gardenReactionLine.orEmpty()
        val atmosphere = buildSanctuaryAtmosphere(
            SanctuaryAtmosphereSignals(
                mood = mood,
                moodStreak = moodState.moodStreak,
                warmBondCount = warmBonds.size,
                milestoneRewardCount = milestoneRewards.size,
                kindnessStreak = kindnessStreak,
                peacefulBiomeCount = peacefulBiomes.size,
                hasRepeatFriend = repeatFriend != null,
                hasRepeatedHarm = repeatedHarmCreature != null,
                hasFeaturedReward = featuredReward != null,
                routeTier = routeTier,
                sparedCount = summary?.sparedCount ?: 0,
                hasRepeatedKindness = repeatedKindnessCreature != null,
                hasFeaturedPeaceBiome = featuredPeaceBiome != null,
                hasFeaturedCostume = featuredCostume != null,
                bloomConversions = summary?.bloomConversions ?: 0,
                hasCactusBloom = cactusBloom != null,
                memoryPageCount = memoryPages
            )
        )

        val traces = buildList {
            if (strainedBond != null) {
                add(
                    SanctuaryTrace(
                        strainedBond,
                        "Watchful Distance",
                        Color.rgb(214, 210, 236)
                    )
                )
            } else if (repeatedHarmCreature != null) {
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
            if (cactusBloom != null) {
                add(
                    SanctuaryTrace(
                        EntityType.CACTUS,
                        "Needle Bloom",
                        Color.rgb(214, 244, 164)
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
            strainedBond != null -> "Held At A Distance"
            repeatedHarmCreature != null -> "Tender Return"
            routeTier == PacifistRouteTier.PEACEFUL && featuredPeaceBiome != null -> "Peace Carried"
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
                if (strainedBond != null) {
                    "The sanctuary keeps extra quiet around the bond that has gone watchful."
                } else {
                    "The sanctuary keeps extra quiet around what still feels tender."
                }
            } else {
                "The sanctuary lowers its voice until your breathing does too."
            }
            ForestMood.GENTLE -> if (routeTier == PacifistRouteTier.PEACEFUL && featuredPeaceLine.isNotBlank()) {
                "The sanctuary has started keeping ${featuredPeaceBiome!!.biome.displayName.lowercase()} in the same soft state you left it."
            } else if (routeTier == PacifistRouteTier.PEACEFUL) {
                "The sanctuary has started keeping the whole shape of your peaceful runs."
            } else if (routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal && featuredPeaceLine.isNotBlank()) {
                "The sanctuary is letting ${featuredPeaceBiome!!.biome.displayName.lowercase()} answer back through the way home feels tonight."
            } else if (featuredRitualLine.isNotBlank()) {
                "The sanctuary has started keeping ${featuredRitualLabel.lowercase()} alive between returns instead of treating the bond like a finished reward."
            } else if (featuredReward != null) {
                "The sanctuary has started holding onto ${featuredReward.homePresenceLabel.lowercase()} instead of letting it fade between returns."
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
        val homeCharacterLabel = when {
            repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature ->
                "Shadowed Home"
            strainedBond != null ->
                "Watchful Home"
            featuredPeaceBiome != null && routeTier == PacifistRouteTier.PEACEFUL ->
                "${featuredPeaceBiome.biome.displayName} Quiet"
            featuredPeaceBiome != null && routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal ->
                "${featuredPeaceBiome.biome.displayName} Sign"
            routeWorldState != null ->
                routeWorldState.label
            featuredCostumeLabel.isNotBlank() ->
                featuredCostumeLabel
            featuredPresenceLabel.isNotBlank() ->
                featuredPresenceLabel
            repeatFriend != null ->
                "Familiar Hearth"
            repeatedKindnessCreature != null && kindnessStreak >= 2 ->
                "Trusting Home"
            milestoneRewards.size >= 2 || warmBonds.size >= 2 ->
                "Lantern Home"
            memoryPages >= 4 ->
                "Remembering Home"
            (summary?.forestMood ?: mood) == ForestMood.FEARFUL ->
                "Sheltering Home"
            (summary?.forestMood ?: mood) == ForestMood.GENTLE ->
                "Soft Home"
            (summary?.forestMood ?: mood) == ForestMood.RECKLESS ->
                "Settling Home"
            else ->
                "Kept Home"
        }
        val homeCharacterLine = when {
            repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature ->
                "Home has learned the darker outline your trouble keeps returning with, and it answers by dimming everything around it."
            strainedBond != null ->
                "Home has become more watchful than fearful, keeping room for distance without pretending it is not there."
            featuredPeaceLine.isNotBlank() && routeTier == PacifistRouteTier.PEACEFUL ->
                "Home has started keeping ${featuredPeaceBiome!!.biome.displayName.lowercase()} in its quieter state instead of treating that peace like a one-run accident."
            featuredPeaceLine.isNotBlank() ->
                "Home now carries a visible sign from ${featuredPeaceBiome!!.biome.displayName}, so the calmer answer you left there does not vanish between runs."
            routeWorldState != null ->
                routeWorldState.line
            featuredRitualLine.isNotBlank() ->
                featuredRitualLine
            featuredCostumeLine.isNotBlank() ->
                featuredCostumeLine
            featuredPresenceLine.isNotBlank() ->
                featuredPresenceLine
            repeatFriend != null ->
                "${formatEntityName(repeatFriend)} has turned home into a place that sounds familiar before the run even begins."
            repeatedKindnessCreature != null && kindnessStreak >= 2 ->
                "${formatEntityName(repeatedKindnessCreature)} has made home feel more trusting, not just more forgiving."
            milestoneRewards.size >= 2 || warmBonds.size >= 2 ->
                "Several bonds are bright enough now that home feels lit by what keeps returning kindly."
            memoryPages >= 4 ->
                "Home has become a place that keeps your quieter pages open without forcing them into explanation."
            (summary?.forestMood ?: mood) == ForestMood.FEARFUL ->
                "Home has learned to shelter first and explain later."
            (summary?.forestMood ?: mood) == ForestMood.GENTLE ->
                "Home has taken on the softer shape of the way you keep coming back."
            (summary?.forestMood ?: mood) == ForestMood.RECKLESS ->
                "Home now feels like the place where stirred-up air is allowed to settle on purpose."
            else ->
                "Home has started keeping a recognizable shape between runs instead of resetting to neutral."
        }
        val carryHomeLine = when {
            repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature ->
                "${formatEntityName(repeatedKillerCreature)} has started to feel like the shape your trouble keeps taking."
            strainedBond != null ->
                RelationshipArcSystem.strainedBondLine(appContext, strainedBond)
            repeatedHarmCreature != null ->
                "${formatEntityName(repeatedHarmCreature)} still lingers in the way the garden holds itself tonight."
            featuredReward != null ->
                featuredRitualLine.ifBlank { featuredPresenceLine.ifBlank { featuredRewardLine } }
            featuredCostumeLine.isNotBlank() ->
                featuredCostumeLine
            repeatFriend != null ->
                "${formatEntityName(repeatFriend)} has started to feel less like a visit and more like a familiar part of home."
            repeatedKindnessCreature != null && kindnessStreak >= 2 ->
                "${formatEntityName(repeatedKindnessCreature)} has started leaving trust behind instead of only memory."
            cactusBloom != null ->
                "The cactus bed has started flowering because you keep reading the sharp line cleanly."
            featuredPeaceLine.isNotBlank() ->
                featuredPeaceLine
            routeWorldState != null ->
                routeWorldState.line
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
            historyUnlock != null ->
                historyUnlock.line
            (summary?.forestMood ?: mood) == ForestMood.FEARFUL ->
                "Nothing here asks you to hurry before you are ready."
            else ->
                "The garden keeps a little of the run instead of sending all of it away."
        }

        val homecomingConsequences = buildList {
            if (worldOpinion != null) {
                add(
                    HomecomingConsequence(
                        label = "Opinion: ${worldOpinion.label.take(18)}",
                        line = worldOpinion.line
                    )
                )
            }

            when (routeTier) {
                PacifistRouteTier.PEACEFUL -> add(
                    HomecomingConsequence(
                        label = "Route: Peaceful",
                        line = if ((summary?.bloomConversions ?: 0) >= 2) {
                            "Peace and Bloom both made it home without turning harsh."
                        } else {
                            "The whole route still feels quieter because you kept peace all the way through."
                        }
                    )
                )
                PacifistRouteTier.MERCIFUL -> add(
                    HomecomingConsequence(
                        label = "Route: Merciful",
                        line = "Mercy is still the clearest thing home remembers about that run."
                    )
                )
                PacifistRouteTier.KIND -> add(
                    HomecomingConsequence(
                        label = "Route: Kind",
                        line = "Kindness is still shaping the way home answers you."
                    )
                )
                PacifistRouteTier.NONE -> Unit
            }

            if (featuredPeaceLabel.isNotBlank() && featuredPeaceLine.isNotBlank()) {
                add(
                    HomecomingConsequence(
                        label = "World: ${featuredPeaceLabel.take(22)}",
                        line = featuredPeaceLine
                    )
                )
            }

            if (routeWorldState != null) {
                add(
                    HomecomingConsequence(
                        label = "World: ${routeWorldState.label.take(20)}",
                        line = routeWorldState.line
                    )
                )
            }

            if (historyUnlock != null) {
                add(
                    HomecomingConsequence(
                        label = "Memory: ${historyUnlock.label.take(20)}",
                        line = historyUnlock.line
                    )
                )
            }

            when {
                strainedBond != null -> add(
                    HomecomingConsequence(
                        label = "Bond: Watchful",
                        line = RelationshipArcSystem.strainedBondLine(appContext, strainedBond)
                    )
                )
                featuredRitualLabel.isNotBlank() && featuredRitualLine.isNotBlank() -> add(
                    HomecomingConsequence(
                        label = "Ritual: ${featuredRitualLabel.take(18)}",
                        line = featuredRitualLine
                    )
                )
                featuredPresenceLabel.isNotBlank() && featuredPresenceLine.isNotBlank() -> add(
                    HomecomingConsequence(
                        label = "Bond: ${featuredPresenceLabel.take(20)}",
                        line = featuredPresenceLine
                    )
                )
                repeatFriend != null -> add(
                    HomecomingConsequence(
                        label = "Bond: Familiar",
                        line = "${formatEntityName(repeatFriend)} is starting to feel expected here, not merely welcomed."
                    )
                )
                strongestBond != null && RelationshipArcSystem.isWarmBond(appContext, strongestBond) -> add(
                    HomecomingConsequence(
                        label = "Bond: ${formatEntityName(strongestBond)}",
                        line = "${formatEntityName(strongestBond)} is part of the way home holds itself now."
                    )
                )
            }

            when {
                featuredCostumeLabel.isNotBlank() && activeCostumeLine.isNotBlank() -> add(
                    HomecomingConsequence(
                        label = "Dress: ${featuredCostumeLabel.take(18)}",
                        line = activeCostumeLine
                    )
                )
                featuredCostumeLabel.isNotBlank() && featuredCostumeLine.isNotBlank() -> add(
                    HomecomingConsequence(
                        label = "Dress: ${featuredCostumeLabel.take(18)}",
                        line = featuredCostumeLine
                    )
                )
                activeCostumeLabel.isNotBlank() && activeCostumeLine.isNotBlank() -> add(
                    HomecomingConsequence(
                        label = "Dress: ${activeCostumeLabel.take(18)}",
                        line = activeCostumeLine
                    )
                )
            }

            when {
                repeatedKillerCreature != null && repeatedKillerCreature == repeatedHarmCreature -> add(
                    HomecomingConsequence(
                        label = "History: Same Shadow",
                        line = "${formatEntityName(repeatedKillerCreature)} still defines the darker edge of the return."
                    )
                )
                repeatedHarmCreature != null -> add(
                    HomecomingConsequence(
                        label = "History: Tender Return",
                        line = "${formatEntityName(repeatedHarmCreature)} still leaves the return more careful than usual."
                    )
                )
                repeatedKindnessCreature != null && kindnessStreak >= 2 -> add(
                    HomecomingConsequence(
                        label = "History: Trust Kept",
                        line = "${formatEntityName(repeatedKindnessCreature)} has left trust behind instead of only memory."
                    )
                )
                cactusBloom != null -> add(
                    HomecomingConsequence(
                        label = "Flora: Needle Bloom",
                        line = "Repeated clean cactus reads have started leaving a small bloom sign behind at home."
                    )
                )
            }

            add(
                HomecomingConsequence(
                    label = "Mood: ${mood.displayName}",
                    line = when (mood) {
                        ForestMood.GENTLE -> "Home kept the softer shape of the run instead of flattening it back to neutral."
                        ForestMood.RECKLESS -> "Home is still letting the stirred-up part of the run come down."
                        ForestMood.FEARFUL -> "Home is still sheltering the shaken edges of the run."
                        ForestMood.STEADY -> "Home is holding onto the calmer shape of the run."
                    }
                )
            )
        }.distinctBy { it.label }.take(5)

        return GardenSanctuaryState(
            sanctuaryLine = sanctuaryLine,
            carryHomeLine = carryHomeLine,
            arrivalBadge = arrivalBadge,
            worldOpinionLabel = worldOpinion?.label.orEmpty(),
            worldOpinionLine = worldOpinion?.line.orEmpty(),
            homeCharacterLabel = homeCharacterLabel,
            homeCharacterLine = homeCharacterLine,
            homecomingConsequences = homecomingConsequences,
            featuredRewardLine = featuredRewardLine,
            featuredPeaceBiome = featuredPeaceBiome?.biome,
            featuredPeaceLabel = featuredPeaceLabel,
            featuredPeaceLine = featuredPeaceLine,
            routeWorldLabel = routeWorldState?.label.orEmpty(),
            routeWorldLine = routeWorldState?.line.orEmpty(),
            featuredCostumeLabel = featuredCostumeLabel,
            featuredCostumeLine = featuredCostumeLine,
            activeCostumeLabel = activeCostumeLabel,
            activeCostumeLine = activeCostumeLine,
            featuredPresenceLabel = featuredPresenceLabel,
            featuredPresenceLine = featuredPresenceLine,
            featuredRitualLabel = featuredRitualLabel,
            featuredRitualLine = featuredRitualLine,
            featuredVisitor = featuredVisitor,
            featuredVisitorTitle = featuredVisitorTitle,
            featuredVisitorLine = featuredVisitorLine,
            fireflyCount = atmosphere.fireflyCount,
            petalCount = atmosphere.petalCount,
            bloomPatchCount = atmosphere.bloomPatchCount,
            mistBandCount = atmosphere.mistBandCount,
            lanternGlowCount = atmosphere.lanternGlowCount,
            groundGlowAlpha = atmosphere.groundGlowAlpha,
            canopyShadeAlpha = atmosphere.canopyShadeAlpha,
            traces = traces
        )
    }

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
