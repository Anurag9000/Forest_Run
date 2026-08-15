package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType

/**
 * Read-only projection of long-horizon progression already owned by gameplay,
 * relationship, Garden, wardrobe, route, and story persistence.
 *
 * This module must never create a second achievement/progression store. Every
 * value is derived from the same authorities that change the run and Garden.
 */
internal data class ForestCollectionTrack(
    val id: String,
    val label: String,
    val completed: Int,
    val total: Int,
    val detail: String
) {
    init {
        require(id.isNotBlank()) { "Collection track id must not be blank" }
        require(label.isNotBlank()) { "Collection track label must not be blank" }
        require(total > 0) { "Collection track total must be positive" }
        require(completed in 0..total) { "Collection progress must stay within its total" }
        require(detail.isNotBlank()) { "Collection track detail must not be blank" }
    }

    val isComplete: Boolean
        get() = completed == total

    val progressLabel: String
        get() = "$completed/$total"
}

internal data class ForestLegacyMilestone(
    val id: String,
    val title: String,
    val line: String,
    val achieved: Boolean,
    val progress: String
) {
    init {
        require(id.isNotBlank()) { "Milestone id must not be blank" }
        require(title.isNotBlank()) { "Milestone title must not be blank" }
        require(line.isNotBlank()) { "Milestone line must not be blank" }
        require(progress.isNotBlank()) { "Milestone progress must not be blank" }
    }
}

internal data class ForestRelationshipMemory(
    val displayName: String,
    val stage: RelationshipStage,
    val toneLabel: String,
    val toneLine: String,
    val milestoneTitle: String?,
    val milestoneLine: String?,
    val ritualTitle: String?,
    val ritualLine: String?,
    val costumeMemory: String?
) {
    init {
        require(displayName.isNotBlank()) { "Relationship memory must name its family" }
        require(toneLabel.isNotBlank()) { "Relationship memory must describe its tone" }
        require(toneLine.isNotBlank()) { "Relationship memory must describe its current history" }
        require((milestoneTitle == null) == (milestoneLine == null)) {
            "Relationship milestone title and line must appear together"
        }
        require((ritualTitle == null) == (ritualLine == null)) {
            "Relationship ritual title and line must appear together"
        }
    }
}

internal data class ForestWardrobeMemory(
    val displayName: String,
    val unlockHint: String,
    val available: Boolean,
    val active: Boolean
) {
    init {
        require(displayName.isNotBlank()) { "Wardrobe memory must name its style" }
        require(unlockHint.isNotBlank()) { "Wardrobe memory must retain its unlock story" }
        require(!active || available) { "Active wardrobe style must be available" }
    }
}

internal data class ForestMemoryPagePresentation(
    val id: String,
    val title: String,
    val category: String,
    val line: String
) {
    init {
        require(id.isNotBlank()) { "Memory page id must not be blank" }
        require(title.isNotBlank()) { "Memory page title must not be blank" }
        require(category.isNotBlank()) { "Memory page category must not be blank" }
        require(line.isNotBlank()) { "Memory page line must not be blank" }
    }
}

internal data class ForestCollectionSnapshot(
    val tracks: List<ForestCollectionTrack>,
    val milestones: List<ForestLegacyMilestone>,
    val relationships: List<ForestRelationshipMemory>,
    val wardrobe: List<ForestWardrobeMemory>,
    val memoryPages: List<ForestMemoryPagePresentation>,
    val kindRuns: Int,
    val mercifulRuns: Int,
    val peacefulRuns: Int
) {
    init {
        require(tracks.map(ForestCollectionTrack::id).distinct().size == tracks.size) {
            "Collection track ids must be unique"
        }
        require(milestones.map(ForestLegacyMilestone::id).distinct().size == milestones.size) {
            "Milestone ids must be unique"
        }
        require(relationships.map(ForestRelationshipMemory::displayName).distinct().size == relationships.size) {
            "Relationship memories must be unique per family"
        }
        require(wardrobe.map(ForestWardrobeMemory::displayName).distinct().size == wardrobe.size) {
            "Wardrobe memories must be unique per style"
        }
        require(wardrobe.count(ForestWardrobeMemory::active) <= 1) {
            "Only one wardrobe style can be active"
        }
        require(memoryPages.map(ForestMemoryPagePresentation::id).distinct().size == memoryPages.size) {
            "Memory page ids must be unique"
        }
        require(kindRuns >= 0 && mercifulRuns >= 0 && peacefulRuns >= 0) {
            "Route history counters must be non-negative"
        }
    }
}

private enum class JournalRelationshipTone {
    WARM,
    STRAINED,
    NEUTRAL
}

/**
 * Canonical collection view used by the Forest Journal.
 *
 * Reading the Journal is intentionally side-effect free: it does not refresh
 * unlocks, award Seeds, mutate relationships, or persist milestone flags.
 */
internal object ForestCollectionProgressComposer {
    fun snapshot(
        context: Context,
        journal: ForestJournalSnapshot
    ): ForestCollectionSnapshot {
        val appContext = context.applicationContext
        val trackedRelationships = journal.entries.count { it.relationshipStage != null }
        require(trackedRelationships > 0) {
            "Forest Journal must retain at least one persistent relationship family"
        }
        val bondedRelationships = journal.entries
            .count { it.relationshipStage == RelationshipStage.MILESTONE }
            .coerceIn(0, trackedRelationships)
        val gardenPlants = SaveManager.loadGardenProgress(appContext)
            .coerceIn(1, GardenEconomy.catalogueSize)
        val availableCostumes = CostumeManager.availableCostumes(appContext)
            .distinct()
        val activeCostume = CostumeManager.activeCostume(appContext)
        val wardrobeStyles = availableCostumes.size.coerceIn(1, CostumeStyle.entries.size)
        val peacefulBiomeCount = journal.peacefulBiomes
            .map { it.biome }
            .distinct()
            .size
            .coerceIn(0, Biome.entries.size)
        val kindRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.KIND)
            .coerceAtLeast(0)
        val mercifulRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.MERCIFUL)
            .coerceAtLeast(0)
        val peacefulRuns = SaveManager.loadRouteTierCount(appContext, PacifistRouteTier.PEACEFUL)
            .coerceAtLeast(0)

        val tracks = listOf(
            ForestCollectionTrack(
                id = "families",
                label = "Forest Families",
                completed = journal.discoveredFamilies,
                total = journal.totalFamilies,
                detail = "Meet every flora, tree, bird, and animal family remembered by the forest."
            ),
            ForestCollectionTrack(
                id = "bonds",
                label = "Living Bonds",
                completed = bondedRelationships,
                total = trackedRelationships,
                detail = "Grow every persistent creature relationship from first meeting to a lasting Bond."
            ),
            ForestCollectionTrack(
                id = "garden",
                label = "Garden",
                completed = gardenPlants,
                total = GardenEconomy.catalogueSize,
                detail = "Grow the full sanctuary catalogue with Seeds carried home from runs."
            ),
            ForestCollectionTrack(
                id = "wardrobe",
                label = "Wardrobe",
                completed = wardrobeStyles,
                total = CostumeStyle.entries.size,
                detail = "Collect every wearable memory, including the always-available Classic style."
            ),
            ForestCollectionTrack(
                id = "biome_peace",
                label = "Peace in Every Biome",
                completed = peacefulBiomeCount,
                total = Biome.entries.size,
                detail = "Leave enough gentle history for every biome to carry friendship back into the Garden."
            )
        )

        val milestones = listOf(
            ForestLegacyMilestone(
                id = "first_family",
                title = "First Footprint",
                line = "The Journal stopped being empty the first time the forest remembered who you met.",
                achieved = journal.discoveredFamilies > 0,
                progress = "${journal.discoveredFamilies}/${journal.totalFamilies} families"
            ),
            ForestLegacyMilestone(
                id = "all_families",
                title = "Every Path Has a Name",
                line = "Every encounter family has found a place in your remembered forest.",
                achieved = journal.discoveredFamilies == journal.totalFamilies,
                progress = "${journal.discoveredFamilies}/${journal.totalFamilies} families"
            ),
            ForestLegacyMilestone(
                id = "first_bond",
                title = "Known by the Wild",
                line = "One wary acquaintance has become a relationship the forest carries between runs.",
                achieved = bondedRelationships > 0,
                progress = "$bondedRelationships/$trackedRelationships Bonds"
            ),
            ForestLegacyMilestone(
                id = "all_bonds",
                title = "Every Quiet Promise",
                line = "Every persistent creature relationship has reached its lasting Bond.",
                achieved = bondedRelationships == trackedRelationships,
                progress = "$bondedRelationships/$trackedRelationships Bonds"
            ),
            ForestLegacyMilestone(
                id = "full_garden",
                title = "Garden in Full",
                line = "Every place in the sanctuary catalogue has been grown into something that can remain.",
                achieved = gardenPlants == GardenEconomy.catalogueSize,
                progress = "$gardenPlants/${GardenEconomy.catalogueSize} plants"
            ),
            ForestLegacyMilestone(
                id = "full_wardrobe",
                title = "Dressed by Memory",
                line = "Every current wearable memory has found its way into the wardrobe.",
                achieved = wardrobeStyles == CostumeStyle.entries.size,
                progress = "$wardrobeStyles/${CostumeStyle.entries.size} styles"
            ),
            ForestLegacyMilestone(
                id = "all_biomes_peaceful",
                title = "Peace in Every Biome",
                line = "Every biome has learned to carry a gentler history back toward home.",
                achieved = peacefulBiomeCount == Biome.entries.size,
                progress = "$peacefulBiomeCount/${Biome.entries.size} biomes"
            ),
            ForestLegacyMilestone(
                id = "peaceful_route",
                title = "Peace Carried Home",
                line = "A complete peaceful route reached the willow without losing the kindness that shaped it.",
                achieved = peacefulRuns > 0,
                progress = "$peacefulRuns peaceful runs"
            )
        )

        val relationships = journal.entries
            .asSequence()
            .filter { it.discovered && it.relationshipStage != null }
            .map { entry -> relationshipMemory(appContext, entry) }
            .toList()

        val wardrobe = CostumeStyle.entries.map { style ->
            val available = style == CostumeStyle.NONE || style in availableCostumes
            ForestWardrobeMemory(
                displayName = style.displayName,
                unlockHint = style.unlockLabel,
                available = available,
                active = style == activeCostume
            )
        }

        val memoryPages = StoryFragmentSystem.unlockedMemoryPages(appContext)
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .map(ForestMemoryPagePresenter::present)
            .toList()

        return ForestCollectionSnapshot(
            tracks = tracks,
            milestones = milestones,
            relationships = relationships,
            wardrobe = wardrobe,
            memoryPages = memoryPages,
            kindRuns = kindRuns,
            mercifulRuns = mercifulRuns,
            peacefulRuns = peacefulRuns
        )
    }

    private fun relationshipMemory(
        context: Context,
        entry: ForestJournalEntry
    ): ForestRelationshipMemory {
        val type = entry.type
        val stage = requireNotNull(entry.relationshipStage)
        val tone = relationshipTone(context, type, stage)
        val reward = RelationshipArcSystem.milestoneRewardFor(context, type)
        val warm = tone == JournalRelationshipTone.WARM
        val strained = tone == JournalRelationshipTone.STRAINED
        return ForestRelationshipMemory(
            displayName = entry.displayName,
            stage = stage,
            toneLabel = when {
                strained -> "Strained"
                warm -> "Warm"
                stage == RelationshipStage.MILESTONE -> "Bonded"
                stage == RelationshipStage.TRUST -> "Trusting"
                stage == RelationshipStage.RECOGNITION -> "Recognizing"
                else -> "First impression"
            },
            toneLine = when {
                strained -> "Familiarity remains, but repeated harm has made this bond hold itself more carefully."
                warm && stage == RelationshipStage.MILESTONE ->
                    "Repeated gentle outcomes have turned recognition into a warm relationship that now changes home and return moments."
                warm -> "Gentle outcomes are becoming a recognizable pattern instead of isolated good luck."
                stage == RelationshipStage.MILESTONE ->
                    "This relationship reached its Bond, though its current tone still reflects the history that followed."
                stage == RelationshipStage.TRUST ->
                    "The forest now treats this creature as someone who knows your habits, not merely someone you have met."
                stage == RelationshipStage.RECOGNITION ->
                    "This creature has begun to recognize the pattern of your crossings."
                else -> "The relationship is still learning what your choices usually mean."
            },
            milestoneTitle = reward?.label,
            milestoneLine = reward?.summary,
            ritualTitle = reward?.bondRitualLabel,
            ritualLine = reward?.bondRitualLine,
            costumeMemory = reward?.costumeReward?.displayName
        )
    }

    /** Mirrors RelationshipArcSystem tone semantics using read-only counters only. */
    private fun relationshipTone(
        context: Context,
        type: EntityType,
        stage: RelationshipStage
    ): JournalRelationshipTone {
        val spared = SaveManager.loadSparedCount(context, type)
        val hits = SaveManager.loadHitCount(context, type)
        val kindnessStreak = SaveManager.loadKindnessStreak(context, type)
        val tenderStreak = SaveManager.loadTenderStreak(context, type)
        return when {
            kindnessStreak >= 2 || (spared > hits && spared >= 1) -> JournalRelationshipTone.WARM
            stage.ordinal >= RelationshipStage.RECOGNITION.ordinal &&
                (tenderStreak >= 2 || (hits > spared && hits >= 2)) -> JournalRelationshipTone.STRAINED
            else -> JournalRelationshipTone.NEUTRAL
        }
    }
}

/** Converts internal memory-page keys into stable player-facing book copy. */
internal object ForestMemoryPagePresenter {
    fun present(pageId: String): ForestMemoryPagePresentation {
        val raw = pageId.removePrefix("page_")
        val category = categoryFor(raw)
        return ForestMemoryPagePresentation(
            id = pageId,
            title = titleFor(raw),
            category = category,
            line = lineFor(category, raw)
        )
    }

    private fun categoryFor(raw: String): String = when {
        raw.startsWith("thought_") -> "CREATURE MEMORY"
        raw.startsWith("weather_") -> "FOREST WEATHER"
        raw.startsWith("garden_") -> "GARDEN MEMORY"
        raw.startsWith("rest_") -> "REST MEMORY"
        raw.startsWith("return_") -> "RETURN MEMORY"
        "route_" in raw -> "PATH MEMORY"
        "bloom" in raw -> "BLOOM MEMORY"
        else -> "FOREST MEMORY"
    }

    private fun titleFor(raw: String): String = when {
        raw.startsWith("thought_learned_") ->
            "${humanize(raw.removePrefix("thought_learned_"))} — Familiar Lesson"
        raw.startsWith("thought_caution_") ->
            "${humanize(raw.removePrefix("thought_caution_"))} — Caution Remembered"
        raw.startsWith("thought_") ->
            "${humanize(raw.removePrefix("thought_"))} — A Quiet Thought"
        raw.startsWith("weather_route_") ->
            "${humanize(raw.removePrefix("weather_route_"))} Path — Weather Memory"
        raw.startsWith("weather_biome_") ->
            "${humanize(raw.removePrefix("weather_biome_"))} — Weather Memory"
        raw.startsWith("weather_repeat_") ->
            "${humanize(raw.removePrefix("weather_repeat_"))} — Familiar Weather"
        raw == "weather_bloom" -> "Bloom in the Evening Air"
        raw.startsWith("weather_") -> "${humanize(raw.removePrefix("weather_"))} Weather"
        raw.startsWith("rest_route_") -> "${humanize(raw.removePrefix("rest_route_"))} Rest"
        raw.startsWith("rest_") -> "${humanize(raw.removePrefix("rest_"))} — Rest"
        raw.startsWith("garden_") -> "${humanize(raw.removePrefix("garden_"))} — Garden"
        raw.startsWith("return_") -> "${humanize(raw.removePrefix("return_"))} — Return"
        else -> humanize(raw)
    }

    private fun lineFor(category: String, raw: String): String = when (category) {
        "CREATURE MEMORY" ->
            "A creature-specific thought unlocked by the history of how you have met, passed, spared, or hurt one another."
        "FOREST WEATHER" ->
            "The Garden weather kept an echo of a route, biome, relationship, mood, or Bloom moment from earlier runs."
        "GARDEN MEMORY" ->
            "A Garden reflection became durable enough to remain as a page instead of disappearing with one visit."
        "REST MEMORY" ->
            "A Rest beneath the willow left a line worth carrying beyond the run that first created it."
        "RETURN MEMORY" ->
            "A return after time away changed how home or a familiar relationship greeted you."
        "PATH MEMORY" ->
            "The shape of a gentle route became part of the forest's longer memory."
        "BLOOM MEMORY" ->
            "Bloom changed more than the immediate run; some of its light remained in the story the forest kept."
        else -> when {
            "relationship" in raw || "bond" in raw ->
                "A relationship crossed a threshold the forest considered worth remembering between runs."
            "biome" in raw ->
                "A biome-specific history became distinct enough to earn its own remembered page."
            else ->
                "A persistent story fragment unlocked through the choices and histories already recorded by the game."
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
        .ifBlank { "Untitled Memory" }
}
