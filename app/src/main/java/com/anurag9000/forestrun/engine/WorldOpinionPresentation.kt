package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType

data class WorldOpinionState(
    val label: String,
    val line: String,
    val runBubbleText: String,
    val runFlavorText: String
)

object WorldOpinionPresentation {

    fun current(
        context: Context,
        summary: RunSummary? = null,
        routeTierOverride: PacifistRouteTier? = null
    ): WorldOpinionState? {
        val appContext = context.applicationContext
        val moodState = ForestMoodSystem.currentState(appContext)
        val history = PersistentMemoryManager.repeatedHistorySnapshot(appContext)
        val routeTier = routeTierOverride
            ?: summary?.pacifistRouteTier
            ?: SaveManager.loadLastRunSummary(appContext)?.pacifistRouteTier
            ?: PacifistRouteTier.NONE
        val routeWorld = PacifistPresentation.routeWorldState(appContext, routeTier)
        val strainedBond = RelationshipArcSystem.featuredStrainedBond(appContext, RelationshipStage.TRUST)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)
        val strongestBond = RelationshipArcSystem.strongestRelationship(appContext)?.first
            ?.takeIf { RelationshipArcSystem.isWarmBond(appContext, it) }
        val repeatedWarm = history.featuredWarmCreature
        val repeatedTender = history.featuredTenderCreature
        val repeatedKiller = history.featuredRepeatKiller
        val featuredPeaceBiome = history.featuredPeaceBiome

        return when {
            repeatedKiller != null && repeatedKiller == repeatedTender -> WorldOpinionState(
                label = "Shadowed",
                line = "${formatEntityName(repeatedKiller)} has become the same shadow the world keeps expecting from your returns.",
                runBubbleText = "World wary",
                runFlavorText = "The path still expects the same shadow."
            )
            strainedBond != null -> WorldOpinionState(
                label = "Watchful",
                line = "The world has become careful around ${formatEntityName(strainedBond)}, answering distance before it answers trust.",
                runBubbleText = "World watchful",
                runFlavorText = "${formatEntityName(strainedBond)} still changes how the path answers you."
            )
            featuredPeaceBiome != null && routeTier == PacifistRouteTier.PEACEFUL -> WorldOpinionState(
                label = "Softened",
                line = "${featuredPeaceBiome.biome.displayName} stayed soft enough that the whole world now answers your return more gently.",
                runBubbleText = "World softens",
                runFlavorText = "Peace is no longer staying in one biome."
            )
            routeWorld != null && routeTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal -> WorldOpinionState(
                label = routeWorld.label,
                line = routeWorld.line,
                runBubbleText = when (routeTier) {
                    PacifistRouteTier.PEACEFUL -> "Peace kept"
                    PacifistRouteTier.MERCIFUL -> "Mercy known"
                    else -> "World answers"
                },
                runFlavorText = routeWorld.line
            )
            repeatedWarm != null && history.featuredWarmStreak >= 2 -> WorldOpinionState(
                label = "Trusting",
                line = "${formatEntityName(repeatedWarm)} has started teaching the world to expect gentler hands from you.",
                runBubbleText = "Trust held",
                runFlavorText = "The forest is starting to trust your return."
            )
            repeatFriend != null -> WorldOpinionState(
                label = "Familiar",
                line = "${formatEntityName(repeatFriend)} has made the world answer like your return belongs here, not like it surprises it.",
                runBubbleText = "Known return",
                runFlavorText = "The path is starting to sound familiar to itself."
            )
            strongestBond != null -> WorldOpinionState(
                label = "Warmed",
                line = "${formatEntityName(strongestBond)} has brightened the world enough that home no longer resets to neutral between runs.",
                runBubbleText = "Warmth held",
                runFlavorText = "The forest kept part of that bond."
            )
            moodState.currentMood == ForestMood.GENTLE && moodState.moodStreak >= 2 -> WorldOpinionState(
                label = "Soft Home",
                line = "Gentler runs have made the world answer you more softly before you even move.",
                runBubbleText = "World softens",
                runFlavorText = "The forest is already answering gently."
            )
            moodState.currentMood == ForestMood.FEARFUL && moodState.moodStreak >= 2 -> WorldOpinionState(
                label = "Sheltering",
                line = "The world has learned to shelter first, because it remembers the shakier edges of your recent returns.",
                runBubbleText = "World shelters",
                runFlavorText = "The path is making room for steadier hands."
            )
            moodState.currentMood == ForestMood.RECKLESS && moodState.moodStreak >= 2 -> WorldOpinionState(
                label = "Stirred",
                line = "The world still answers like it remembers your rush, even while it tries to settle.",
                runBubbleText = "World stirred",
                runFlavorText = "The branches still remember the rush."
            )
            moodState.currentMood == ForestMood.STEADY && moodState.moodStreak >= 2 -> WorldOpinionState(
                label = "Steady",
                line = "The world has started expecting a steadier kind of return from you.",
                runBubbleText = "World steady",
                runFlavorText = "The forest is keeping your steadier pace."
            )
            else -> null
        }
    }

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
