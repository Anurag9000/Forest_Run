package com.anurag9000.forestrun.engine

/**
 * Specific prose for persisted story-page identifiers.
 *
 * Unlock ownership remains StoryFragmentSystem/SaveManager. This layer only
 * interprets durable IDs for a player-facing Journal and has no side effects.
 */
internal object ForestMemoryPageNarrative {
    fun lineFor(page: ForestMemoryPagePresentation): String {
        val raw = page.id.removePrefix("page_")
        return when {
            raw.startsWith("thought_learned_") -> {
                val who = humanize(raw.removePrefix("thought_learned_"))
                "Repeated gentle meetings with $who became familiar enough to feel learned rather than accidental."
            }
            raw.startsWith("thought_caution_") -> {
                val who = humanize(raw.removePrefix("thought_caution_"))
                "The history with $who carries caution now: familiarity remained, but harm changed the way the memory feels."
            }
            raw.startsWith("thought_") -> {
                val who = humanize(raw.removePrefix("thought_"))
                "A quiet thought about $who survived the run and became part of the forest's longer memory."
            }
            raw.startsWith("weather_route_") -> {
                val route = humanize(raw.removePrefix("weather_route_"))
                "A $route route changed the air around home strongly enough that the Garden remembered its weather."
            }
            raw.startsWith("weather_biome_") -> {
                val biome = humanize(raw.removePrefix("weather_biome_"))
                "$biome left a distinct atmospheric trace that followed the run back toward the willow."
            }
            raw.startsWith("weather_repeat_") -> {
                val who = humanize(raw.removePrefix("weather_repeat_"))
                "Seeing $who again and again became part of the rhythm of the forest, and even the Garden weather began to echo it."
            }
            raw == "weather_bloom" ->
                "Bloom lingered beyond its six-second run window: the Garden kept a softer light in the weather that followed."
            raw.startsWith("weather_") -> {
                val mood = humanize(raw.removePrefix("weather_"))
                "A $mood forest mood lasted long enough to tint the weather around home."
            }
            raw == "rest_route_peaceful" ->
                "A Peaceful Path reached Rest intact; the willow kept that complete restraint as something worth remembering."
            raw == "rest_route_merciful" ->
                "A Merciful Path reached Rest with enough restraint to leave a durable page beneath the willow."
            raw == "rest_clean_return" ->
                "A clean return made Rest feel less like failure and more like arriving home with the path still settled."
            raw.startsWith("rest_strained_") -> {
                val who = humanize(raw.removePrefix("rest_strained_"))
                "Rest carried the strain of the history with $who; recognition remained, but the relationship had learned to be careful."
            }
            raw.startsWith("rest_biome_") -> {
                val biome = humanize(raw.removePrefix("rest_biome_"))
                "$biome shaped this Rest strongly enough that the willow kept a biome-specific memory of the return."
            }
            raw.startsWith("rest_mood_") -> {
                val mood = humanize(raw.removePrefix("rest_mood_"))
                "The run returned in a $mood mood, and Rest gave that emotional pattern a place to settle."
            }
            raw == "rest_clean_pattern" ->
                "Clean passes stopped looking like isolated escapes and became a pattern the forest could recognize."
            raw == "rest_bloom_memory" ->
                "Bloom did not end when its timer did; Rest kept a trace of the conversions and light carried into the final stretch."
            raw.startsWith("rest_killer_") -> {
                val who = humanize(raw.removePrefix("rest_killer_"))
                "$who ended a run strongly enough to become part of the Rest memory instead of disappearing with the reset."
            }
            raw == "garden_cactus_bloom" ->
                "A remembered Cactus and Bloom history crossed paths at home, making the Garden feel connected to what happened out on the trail."
            raw.startsWith("garden_strained_") -> {
                val who = humanize(raw.removePrefix("garden_strained_"))
                "$who still belongs to the remembered world, but the Garden reflects a relationship made guarded by repeated harm."
            }
            raw.startsWith("garden_warm_clean_") -> {
                val who = humanize(raw.removePrefix("garden_warm_clean_"))
                "A warm history with $who met a clean recent path, turning familiarity into a noticeably gentler homecoming."
            }
            raw.startsWith("garden_warm_") -> {
                val who = humanize(raw.removePrefix("garden_warm_"))
                "The Garden now has a warm place for $who because repeated gentle outcomes changed the relationship beyond a single run."
            }
            raw.startsWith("garden_caution_") -> {
                val who = humanize(raw.removePrefix("garden_caution_"))
                "The Garden remembers that $who has reason to be cautious, even while leaving room for the relationship to change again."
            }
            raw.startsWith("garden_biome_") -> {
                val biome = humanize(raw.removePrefix("garden_biome_"))
                "Enough history accumulated in $biome that the sanctuary began carrying a recognizable piece of that place home."
            }
            raw.startsWith("garden_mood_") -> {
                val mood = humanize(raw.removePrefix("garden_mood_"))
                "A sustained $mood pattern in recent runs became visible in how the Garden greeted the player."
            }
            raw.startsWith("garden_route_") -> {
                val route = humanize(raw.removePrefix("garden_route_"))
                "The Garden recognized a $route route as more than one good moment: it became part of the sanctuary's memory."
            }
            raw.startsWith("garden_peace_") -> {
                val biome = humanize(raw.removePrefix("garden_peace_"))
                "Peaceful history in $biome grew strong enough to leave a lasting friendship trace in the Garden."
            }
            raw.startsWith("garden_warmth_") -> {
                val who = humanize(raw.removePrefix("garden_warmth_"))
                "Familiarity with $who deepened into warmth that now changes the atmosphere of home rather than only encounter dialogue."
            }
            raw.startsWith("garden_repeat_") -> {
                val who = humanize(raw.removePrefix("garden_repeat_"))
                "Repeated meetings with $who became a recurring part of homecoming instead of a sequence of unrelated encounters."
            }
            raw == "garden_bloom_memory" ->
                "The sanctuary kept a Bloom memory: light, mercy, and conversion left a trace that survived the run's end."
            "relationship" in raw || "bond" in raw ->
                "A persistent relationship crossed an important threshold and became part of the story the forest carries between sessions."
            "bloom" in raw ->
                "Bloom changed more than the immediate run; some of its light remained in the forest's persistent story."
            else -> page.line
        }
    }

    private fun humanize(raw: String): String = raw
        .split('_')
        .filter(String::isNotBlank)
        .joinToString(" ") { token ->
            token.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }
        .ifBlank { "the forest" }
}
