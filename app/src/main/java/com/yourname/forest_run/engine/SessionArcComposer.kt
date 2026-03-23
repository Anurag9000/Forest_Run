package com.yourname.forest_run.engine

import android.content.Context

data class MenuSceneCopy(
    val atmosphereLine: String,
    val secondaryAtmosphereLine: String,
    val homeSignLabel: String,
    val idlePrompt: String,
    val idleSupportLine: String,
    val readyPrompt: String,
    val readySupportLine: String
)

data class RestSceneCopy(
    val subtitle: String,
    val carryHomeLine: String,
    val promptLine: String
)

object SessionArcComposer {

    fun menuCopy(context: Context): MenuSceneCopy {
        val appContext = context.applicationContext
        val summary = SaveManager.loadLastRunSummary(appContext)
        val sanctuary = GardenSanctuaryPlanner.build(appContext, summary)
        val repeatFriend = RelationshipArcSystem.featuredRepeatFriend(appContext)

        val atmosphereLine = when {
            summary == null -> "The willow has kept the first path quiet for you."
            sanctuary.featuredPeaceLine.isNotBlank() &&
                summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal ->
                sanctuary.featuredPeaceLine
            summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL && summary.bloomConversions >= 2 ->
                "Bloom left the branches bright, but the garden is still carrying the hush of that peaceful run."
            summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL ->
                "The garden is still holding the hush of the last peaceful run."
            summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL && repeatFriend != null ->
                "Something familiar is still answering your last merciful return more softly."
            summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL ->
                "Mercy left the path quieter than usual."
            summary.forestMood == ForestMood.FEARFUL || summary.hitsTaken >= 2 ->
                "Home keeps its voice low until the roughness leaves your hands."
            sanctuary.featuredPresenceLine.isNotBlank() ->
                sanctuary.featuredPresenceLine
            repeatFriend != null ->
                sanctuary.carryHomeLine.ifBlank { RelationshipArcSystem.repeatFriendLine(appContext, repeatFriend) }
            summary.forestMood == ForestMood.GENTLE && summary.sparedCount > 0 ->
                "The garden still holds the gentler shape of your last return."
            summary.bloomConversions >= 3 ->
                "A little of Bloom is still hanging in the branches."
            sanctuary.carryHomeLine.isNotBlank() ->
                sanctuary.carryHomeLine
            else ->
                "The path remembers enough to feel familiar, not crowded."
        }

        val secondaryAtmosphereLine = when {
            summary == null ->
                "Nothing here needs to hurry before the first step becomes real."
            sanctuary.featuredPeaceLabel.isNotBlank() ->
                "${sanctuary.featuredPeaceLabel} is still visible in the way the garden is holding itself."
            sanctuary.featuredPresenceLabel.isNotBlank() ->
                "${sanctuary.featuredPresenceLabel} is still part of the air before the run starts."
            repeatFriend != null ->
                "Something familiar already belongs to the quiet before the run."
            summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL && summary.bloomConversions >= 2 ->
                "Even the brighter branches are holding still around what you brought back."
            summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal ->
                "The garden is staying soft enough for that return to be heard twice."
            summary.forestMood == ForestMood.FEARFUL ->
                "The rougher edges are being allowed to settle before anything asks for speed."
            summary.bloomConversions >= 2 ->
                "A little of Bloom is still caught in the willow and the mist."
            else ->
                "The garden is already behaving like your return changed the air."
        }

        val homeSignLabel = when {
            summary == null -> "Quiet Start"
            sanctuary.featuredPeaceLabel.isNotBlank() -> sanctuary.featuredPeaceLabel
            sanctuary.featuredPresenceLabel.isNotBlank() -> sanctuary.featuredPresenceLabel
            sanctuary.arrivalBadge.isNotBlank() -> sanctuary.arrivalBadge
            repeatFriend != null -> "Familiar Return"
            summary.forestMood == ForestMood.FEARFUL -> "Soft Landing"
            summary.pacifistRouteTier != PacifistRouteTier.NONE -> summary.pacifistRouteTier.displayName
            summary.bloomConversions >= 2 -> "Bloom Lingers"
            else -> "Homecoming"
        }

        val idlePrompt = when {
            summary?.forestMood == ForestMood.FEARFUL -> "tap when you're ready"
            else -> "tap to rise"
        }

        val idleSupportLine = when {
            summary == null -> "the willow kept your place"
            sanctuary.featuredPeaceLabel.isNotBlank() -> "${sanctuary.featuredPeaceLabel.lowercase()} is still showing at home"
            summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal ->
                "the garden still sounds softer after that run"
            sanctuary.featuredPresenceLabel.isNotBlank() ->
                "${sanctuary.featuredPresenceLabel.lowercase()} is still waiting at home"
            repeatFriend != null -> "someone familiar is already part of the way home sounds"
            summary.forestMood == ForestMood.FEARFUL -> "the path can wait for steadier hands"
            summary.forestMood == ForestMood.GENTLE -> "the garden remembers the softer version of that run"
            summary.bloomConversions >= 2 -> "there is still light left in the branches"
            else -> "home kept that run without asking anything more from it"
        }

        val readyPrompt = when {
            summary?.forestMood == ForestMood.FEARFUL -> "tap to begin softly"
            else -> "tap to run"
        }

        val readySupportLine = when {
            summary == null -> "the first steps are enough"
            sanctuary.featuredPeaceLabel.isNotBlank() -> "carry that calmer sign back into the path"
            summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL -> "carry the quiet you earned back into the path"
            sanctuary.featuredPresenceLabel.isNotBlank() -> "carry that familiar homeward sign back into the path"
            repeatFriend != null -> "carry that familiar warmth back into the path"
            summary.forestMood == ForestMood.GENTLE -> "carry the gentler pace with you"
            summary.forestMood == ForestMood.FEARFUL -> "nothing is asking for speed first"
            summary.bloomConversions >= 2 -> "the path still remembers Bloom"
            else -> "the forest is ready for another answer"
        }

        return MenuSceneCopy(
            atmosphereLine = atmosphereLine,
            secondaryAtmosphereLine = secondaryAtmosphereLine,
            homeSignLabel = homeSignLabel,
            idlePrompt = idlePrompt,
            idleSupportLine = idleSupportLine,
            readyPrompt = readyPrompt,
            readySupportLine = readySupportLine
        )
    }

    fun restCopy(context: Context, summary: RunSummary): RestSceneCopy {
        val appContext = context.applicationContext
        val sanctuary = GardenSanctuaryPlanner.build(appContext, summary)
        val previewMoment = ReturnMomentsSystem.previewGardenMoment(appContext, summary)

        val subtitle = when {
            summary.pacifistRouteTier == PacifistRouteTier.PEACEFUL && summary.bloomConversions >= 2 ->
                "Bloom came down softly with you."
            summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal ->
                summary.pacifistRouteTier.restLine
            summary.hitsTaken == 0 && summary.cleanPasses >= 8 ->
                "The run comes down gently."
            summary.forestMood == ForestMood.FEARFUL ->
                "Nothing is asking you to recover too quickly."
            summary.forestMood == ForestMood.GENTLE ->
                "The forest lets the softer parts of the run return first."
            summary.forestMood == ForestMood.RECKLESS ->
                "Even hurried runs are allowed to come down softly."
            else ->
                "The path is already making room for another calm start."
        }

        val carryHomeLine = previewMoment?.line?.ifBlank { null }
            ?: sanctuary.carryHomeLine.ifBlank {
                if (sanctuary.featuredPeaceLine.isNotBlank()) {
                    sanctuary.featuredPeaceLine
                } else
                if (sanctuary.featuredPresenceLine.isNotBlank()) {
                    sanctuary.featuredPresenceLine
                } else {
                when (summary.forestMood) {
                    ForestMood.GENTLE -> if (summary.pacifistRouteTier == PacifistRouteTier.MERCIFUL && summary.sparedCount > 0) {
                        "Mercy is already changing the way home sounds."
                    } else {
                        "The gentler parts of the run are already finding their way home."
                    }
                    ForestMood.FEARFUL -> "Home is still making a quieter place for this run."
                    ForestMood.RECKLESS -> "Even a stirred-up run is allowed to settle into home."
                    ForestMood.STEADY -> "Home keeps a little of the steadier shape that run left behind."
                }
                }
            }

        val promptLine = if (previewMoment != null) {
            "tap to return home"
        } else {
            "tap to carry this home"
        }

        return RestSceneCopy(
            subtitle = subtitle,
            carryHomeLine = carryHomeLine,
            promptLine = promptLine
        )
    }

    fun gardenArrivalLine(
        summary: RunSummary?,
        returnMoment: ReturnMoment?,
        sanctuaryState: GardenSanctuaryState
    ): String {
        if (returnMoment != null) return ""
        if (sanctuaryState.carryHomeLine.isNotBlank()) {
            return sanctuaryState.carryHomeLine
        }
        if (sanctuaryState.featuredPeaceLine.isNotBlank()) {
            return sanctuaryState.featuredPeaceLine
        }
        if (sanctuaryState.featuredPresenceLine.isNotBlank()) {
            return sanctuaryState.featuredPresenceLine
        }
        if (summary == null) {
            return "The garden kept a quiet place open for you."
        }
        return when (summary.forestMood) {
            ForestMood.FEARFUL -> "Nothing here is asking you to hurry back out."
            ForestMood.GENTLE -> if (summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal) {
                summary.pacifistRouteTier.gardenLine
            } else {
                "The gentler shape of that run is still resting here."
            }
            ForestMood.RECKLESS -> "Even stirred-up air can settle once it reaches home."
            ForestMood.STEADY -> if (summary.pacifistRouteTier.ordinal >= PacifistRouteTier.MERCIFUL.ordinal) {
                summary.pacifistRouteTier.gardenLine
            } else {
                "The garden kept the calmer part of that run."
            }
        }
    }
}
