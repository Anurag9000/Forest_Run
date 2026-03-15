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
    val fireflyCount: Int = 0,
    val petalCount: Int = 0,
    val bloomPatchCount: Int = 0,
    val canopyShadeAlpha: Int = 0,
    val traces: List<SanctuaryTrace> = emptyList()
)

object GardenSanctuaryPlanner {

    fun build(context: Context, summary: RunSummary?): GardenSanctuaryState {
        val appContext = context.applicationContext
        val moodState = ForestMoodSystem.currentState(appContext)
        val bonds = RelationshipArcSystem.relationshipsAtOrAbove(appContext, RelationshipStage.TRUST)
        val warmBonds = bonds.filter { RelationshipArcSystem.isWarmBond(appContext, it.first) }
        val memoryPages = StoryFragmentSystem.memoryPageCount(appContext)
        val mood = moodState.currentMood

        val fireflies = when (mood) {
            ForestMood.GENTLE -> 4 + moodState.moodStreak.coerceAtMost(4)
            ForestMood.RECKLESS -> 1
            ForestMood.FEARFUL -> 2
            ForestMood.STEADY -> 3 + (moodState.moodStreak / 2).coerceAtMost(2)
        } + warmBonds.size.coerceAtMost(2)

        val petals = when (mood) {
            ForestMood.GENTLE -> 3
            ForestMood.RECKLESS -> 5
            ForestMood.FEARFUL -> 2
            ForestMood.STEADY -> 3
        } + if ((summary?.sparedCount ?: 0) > 0) 1 else 0

        val bloomPatches = when (mood) {
            ForestMood.GENTLE -> 2
            ForestMood.RECKLESS -> 0
            ForestMood.FEARFUL -> 1
            ForestMood.STEADY -> 1
        } + warmBonds.size.coerceAtMost(2) / 2 + if ((summary?.bloomConversions ?: 0) >= 2) 1 else 0

        val canopyShadeAlpha = when (mood) {
            ForestMood.GENTLE -> 26
            ForestMood.RECKLESS -> 22
            ForestMood.FEARFUL -> 54
            ForestMood.STEADY -> 18
        } + if (memoryPages >= 4) 6 else 0

        val traces = bonds.take(3).map { (type, _) ->
            when (type) {
                EntityType.CAT -> SanctuaryTrace(type, "Warm Grass", Color.rgb(255, 226, 240))
                EntityType.FOX -> SanctuaryTrace(type, "Bright Trail", Color.rgb(255, 208, 142))
                EntityType.WOLF -> SanctuaryTrace(type, "Quiet Watch", Color.rgb(198, 212, 236))
                EntityType.DOG -> SanctuaryTrace(type, "Happy Paws", Color.rgb(255, 230, 154))
                EntityType.OWL -> SanctuaryTrace(type, "Lantern Branch", Color.rgb(212, 214, 255))
                EntityType.EAGLE -> SanctuaryTrace(type, "Sky Hush", Color.rgb(210, 228, 255))
                else -> SanctuaryTrace(type, "Kind Trace", Color.rgb(232, 246, 212))
            }
        }

        val sanctuaryLine = when (mood) {
            ForestMood.GENTLE -> if (warmBonds.isNotEmpty()) {
                "The sanctuary opens faster when you keep coming home gently."
            } else {
                "The sanctuary keeps the softer shape of your footsteps."
            }
            ForestMood.RECKLESS -> "Even stirred-up air can settle once it reaches home."
            ForestMood.FEARFUL -> "The sanctuary lowers its voice until your breathing does too."
            ForestMood.STEADY -> if (traces.isNotEmpty()) {
                "Steady returns have started leaving visible traces here."
            } else {
                "The sanctuary keeps a calm shape for ordinary returns."
            }
        }

        val strongestBond = bonds.firstOrNull()?.first
        val carryHomeLine = when {
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
            fireflyCount = fireflies,
            petalCount = petals,
            bloomPatchCount = bloomPatches,
            canopyShadeAlpha = canopyShadeAlpha,
            traces = traces
        )
    }

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
