package com.anurag9000.forestrun.ui

import android.content.Context
import com.anurag9000.forestrun.engine.Biome
import com.anurag9000.forestrun.engine.ForestMood
import com.anurag9000.forestrun.engine.PacifistRouteTier
import com.anurag9000.forestrun.engine.RunSummary
import com.anurag9000.forestrun.engine.StoryFragmentSystem
import com.anurag9000.forestrun.entities.EntityType

/**
 * Builds short reflective quotes for the post-run rest beat.
 */
object RestQuoteManager {

    fun quoteFor(context: Context, summary: RunSummary, biome: Biome, killer: EntityType?): String {
        val base = StoryFragmentSystem.restQuote(context, summary, biome, killer)
        val coda = selectCoda(summary, biome, killer)
        return "$base $coda"
    }

    private fun selectCoda(summary: RunSummary, biome: Biome, killer: EntityType?): String {
        val pool = when {
            summary.pacifistRouteTier != PacifistRouteTier.NONE -> routeCodas(summary.pacifistRouteTier, biome)
            killer != null -> killerCodas(killer, summary.forestMood)
            else -> biomeCodas(biome, summary.forestMood) + moodCodas(summary.forestMood) + universalCodas()
        }
        return pool[variantIndex(summary, biome, killer, pool.size)]
    }

    private fun routeCodas(routeTier: PacifistRouteTier, biome: Biome): List<String> = when (routeTier) {
        PacifistRouteTier.KIND -> listOf(
            "Even ${biome.displayName} feels slower to close after that.",
            "Kindness lingers longer in rest than the score ever does.",
            "The quieter version of the run is still here."
        )
        PacifistRouteTier.MERCIFUL -> listOf(
            "Mercy is still the thing the room remembers first.",
            "Rest keeps the spared part of the run closest.",
            "${biome.displayName} does not sound as guarded from here."
        )
        PacifistRouteTier.PEACEFUL -> listOf(
            "Peace made it all the way home with you.",
            "Nothing in the room seems eager to break that hush.",
            "${biome.displayName} still feels answered softly."
        )
        PacifistRouteTier.NONE -> universalCodas()
    }

    private fun killerCodas(killer: EntityType, mood: ForestMood): List<String> {
        val name = readableName(killer)
        return when (mood) {
            ForestMood.GENTLE -> listOf(
                "Even now, rest is trying not to turn $name into only fear.",
                "$name still feels nearer than the bed does.",
                "You can feel where $name is still sitting in the room."
            )
            ForestMood.RECKLESS -> listOf(
                "$name still makes the room feel faster than it is.",
                "Rest has not fully caught up to what $name took from the rhythm.",
                "The mistake still sounds a little like $name."
            )
            ForestMood.FEARFUL -> listOf(
                "The room is gentle, but it still knows the shape of $name.",
                "$name is quieter here, not gone.",
                "Rest is careful around what $name left behind."
            )
            ForestMood.STEADY -> listOf(
                "$name has not kept the whole room, only a corner of it.",
                "The memory of $name is present, but smaller now.",
                "Rest gives $name less space than the run did."
            )
        }
    }

    private fun biomeCodas(biome: Biome, mood: ForestMood): List<String> = when (biome) {
        Biome.MEADOW -> listOf(
            "The meadow still leaves the room a little open.",
            "Rest keeps a meadow softness around the edges.",
            if (mood == ForestMood.GENTLE) "The grass-light calm has not fully left you yet." else "The meadow keeps the quieter part of the run."
        )
        Biome.ORCHARD -> listOf(
            "The orchard still sounds faintly rhythmic here.",
            "Rest holds the orchard like a song after the note.",
            "Something sweet and measured is still hanging on."
        )
        Biome.ANCIENT_GROVE -> listOf(
            "The grove leaves a patient weight behind it.",
            "Rest keeps the older hush of the grove intact.",
            "Ancient Grove still asks for patience, even now."
        )
        Biome.DUSK_CANYON -> listOf(
            "Dusk is still close enough to shorten the room.",
            "The canyon leaves its edge behind longer than it should.",
            "Rest keeps a little of Dusk Canyon's narrow light."
        )
        Biome.NIGHT_FOREST -> listOf(
            "Night is still sitting quietly at the edge of the bed.",
            "The dark here feels more remembered than empty.",
            "Night Forest has not fully let go of you yet."
        )
    }

    private fun moodCodas(mood: ForestMood): List<String> = when (mood) {
        ForestMood.GENTLE -> listOf(
            "Rest does not feel defensive tonight.",
            "The room is softer than the run was.",
            "Nothing here seems eager to press the bruise."
        )
        ForestMood.RECKLESS -> listOf(
            "The room is still trying to slow your nerves down.",
            "Rest arrives later after runs like that.",
            "Your body has not fully believed the danger is over."
        )
        ForestMood.FEARFUL -> listOf(
            "The room keeps its voice low for you.",
            "Rest is careful tonight.",
            "Everything here is trying not to startle you again."
        )
        ForestMood.STEADY -> listOf(
            "Rest feels earned instead of borrowed.",
            "The room has settled into a patient pace.",
            "Nothing here is asking more of you right now."
        )
    }

    private fun universalCodas(): List<String> = listOf(
        "The room remembers more than the numbers do.",
        "What made it home is not only the score.",
        "Rest is keeping the run from disappearing too quickly."
    )

    private fun variantIndex(summary: RunSummary, biome: Biome, killer: EntityType?, size: Int): Int {
        require(size > 0) { "Rest quote variant pool must not be empty." }
        val seed = summary.score.toLong() +
            summary.cleanPasses.toLong() * 7L +
            summary.sparedCount.toLong() * 11L +
            summary.bloomConversions.toLong() * 13L +
            summary.forestMood.ordinal.toLong() * 17L +
            biome.ordinal.toLong() * 19L +
            (killer?.ordinal ?: 0).toLong() * 23L +
            summary.pacifistRouteTier.ordinal.toLong() * 29L
        return Math.floorMod(seed, size.toLong()).toInt()
    }

    private fun readableName(type: EntityType): String =
        type.name.lowercase().replace('_', ' ')
}
