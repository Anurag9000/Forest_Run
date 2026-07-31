package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.engine.PacifistRouteTier.NONE

data class PostRunReflectionEntry(
    val label: String,
    val text: String
)

object PostRunReflectionPlanner {

    fun restEntry(
        summary: RunSummary,
        sanctuaryState: GardenSanctuaryState,
        recoveryLine: String,
        carryHomeLine: String
    ): PostRunReflectionEntry? {
        val candidates = buildUniqueEntries(
            if (summary.pacifistRouteTier != NONE) {
                PostRunReflectionEntry("Route", PacifistPresentation.routeAfterglowLine(summary.pacifistRouteTier))
            } else null,
            sanctuaryState.featuredPeaceLine.takeIf { it.isNotBlank() && !it.equals(carryHomeLine, ignoreCase = true) }
                ?.let { PostRunReflectionEntry("World", it) },
            sanctuaryState.featuredPresenceLine.takeIf { it.isNotBlank() && !it.equals(carryHomeLine, ignoreCase = true) }
                ?.let { PostRunReflectionEntry("Home", it) },
            recoveryLine.takeIf { it.isNotBlank() && !it.equals(carryHomeLine, ignoreCase = true) }
                ?.let { PostRunReflectionEntry("After", it) }
        )
        return candidates.firstOrNull()
    }

    fun gardenEntries(
        summary: RunSummary?,
        sanctuaryState: GardenSanctuaryState,
        restQuote: String,
        gardenReflection: String,
        weatherThought: String,
        creatureThought: String,
        arrivalLine: String
    ): List<PostRunReflectionEntry> {
        val routeEntry = summary?.pacifistRouteTier
            ?.takeIf { it != NONE }
            ?.let { PostRunReflectionEntry("Route", PacifistPresentation.routeAfterglowLine(it)) }
        val worldEntry = sanctuaryState.featuredPeaceLine.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("World", it) }
        val homeEntry = sanctuaryState.featuredPresenceLine
            .takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Home", it) }
            ?: sanctuaryState.carryHomeLine.takeIf { it.isNotBlank() }
                ?.let { PostRunReflectionEntry("Home", it) }
        val reflectionEntry = gardenReflection.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Reflection", it) }
        val weatherEntry = weatherThought.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Weather", it) }
        val creatureEntry = creatureThought.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Creature", it) }
        val echoEntry = restQuote.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Echo", it) }
        val arrivalEntry = arrivalLine.takeIf { it.isNotBlank() }
            ?.let { PostRunReflectionEntry("Arrival", it) }

        val ordered = when {
            routeEntry != null -> buildUniqueEntries(
                routeEntry,
                worldEntry,
                reflectionEntry,
                creatureEntry,
                weatherEntry,
                homeEntry,
                echoEntry,
                arrivalEntry
            )
            creatureEntry != null -> buildUniqueEntries(
                creatureEntry,
                reflectionEntry,
                weatherEntry,
                worldEntry,
                homeEntry,
                echoEntry,
                arrivalEntry
            )
            else -> buildUniqueEntries(
                reflectionEntry,
                weatherEntry,
                worldEntry,
                homeEntry,
                echoEntry,
                arrivalEntry
            )
        }
        return ordered.take(3)
    }

    private fun buildUniqueEntries(vararg entries: PostRunReflectionEntry?): List<PostRunReflectionEntry> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<PostRunReflectionEntry>()
        entries.filterNotNull().forEach { entry ->
            val normalized = entry.text.trim().lowercase()
            if (normalized.isNotBlank() && seen.add(normalized)) {
                result += entry
            }
        }
        return result
    }
}
