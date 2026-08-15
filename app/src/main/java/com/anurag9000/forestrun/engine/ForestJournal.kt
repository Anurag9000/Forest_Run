package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.EntityType

/**
 * Player-facing projection of Forest Run's existing encounter, relationship,
 * biome, and history persistence. The journal deliberately derives from the
 * canonical runtime authorities instead of adding a second progression store.
 */
internal data class ForestJournalEntry(
    val type: EntityType,
    val displayName: String,
    val group: EncounterFamilyGroup,
    val discovered: Boolean,
    val encounterCount: Int,
    val cleanPassCount: Int,
    val sparedCount: Int,
    val hitCount: Int,
    val relationshipStage: RelationshipStage?,
    val preferredBiomes: List<String>,
    val authoredVariantCount: Int,
    val temperament: String,
    val fieldNote: String
)

internal data class ForestJournalSnapshot(
    val entries: List<ForestJournalEntry>,
    val discoveredFamilies: Int,
    val totalFamilies: Int,
    val totalEncounters: Int,
    val totalCleanPasses: Int,
    val totalSpares: Int,
    val totalHits: Int,
    val memoryPageCount: Int,
    val historyMarks: List<PersistentMemoryManager.HistoryUnlockMark>,
    val strongestRelationship: String?,
    val peacefulBiomes: List<PersistentMemoryManager.BiomeFriendshipMark>
) {
    init {
        require(totalFamilies == entries.size) { "Journal family count must match entries" }
        require(discoveredFamilies in 0..totalFamilies) { "Journal discovery count is invalid" }
        require(totalEncounters >= 0 && totalCleanPasses >= 0 && totalSpares >= 0 && totalHits >= 0) {
            "Journal aggregate counters must be non-negative"
        }
        require(memoryPageCount >= 0) { "Journal memory-page count must be non-negative" }
    }
}

/** Canonical authored identity used only for player-facing journal copy. */
internal data class ForestJournalLore(
    val temperament: String,
    val fieldNote: String
)

internal object ForestJournalComposer {
    fun snapshot(context: Context): ForestJournalSnapshot {
        val appContext = context.applicationContext
        val entries = EncounterFamilyCatalogue.profiles.map { profile ->
            val type = profile.type
            val encounters = PersistentMemoryManager.getEncounterCount(appContext, type)
            val passes = PersistentMemoryManager.getPassCount(appContext, type)
            val spared = PersistentMemoryManager.getSparedCount(appContext, type)
            val hits = PersistentMemoryManager.getHitCount(appContext, type)
            val lore = loreByType.getValue(type)
            ForestJournalEntry(
                type = type,
                displayName = displayName(type),
                group = profile.group,
                discovered = encounters > 0 || passes > 0 || spared > 0 || hits > 0,
                encounterCount = encounters,
                cleanPassCount = passes,
                sparedCount = spared,
                hitCount = hits,
                relationshipStage = if (profile.relationshipTracked) {
                    PersistentMemoryManager.getRelationshipStage(appContext, type)
                } else {
                    null
                },
                preferredBiomes = profile.preferredBiomes
                    .sortedBy(Biome::ordinal)
                    .map(Biome::displayName),
                authoredVariantCount = profile.variants.size,
                temperament = lore.temperament,
                fieldNote = lore.fieldNote
            )
        }

        return ForestJournalSnapshot(
            entries = entries,
            discoveredFamilies = entries.count(ForestJournalEntry::discovered),
            totalFamilies = entries.size,
            totalEncounters = entries.sumOf(ForestJournalEntry::encounterCount),
            totalCleanPasses = entries.sumOf(ForestJournalEntry::cleanPassCount),
            totalSpares = entries.sumOf(ForestJournalEntry::sparedCount),
            totalHits = entries.sumOf(ForestJournalEntry::hitCount),
            memoryPageCount = SaveManager.loadUnlockedMemoryPages(appContext).size,
            historyMarks = PersistentMemoryManager.historyUnlocks(appContext),
            strongestRelationship = RelationshipArcSystem.strongestRelationshipLabel(appContext),
            peacefulBiomes = PersistentMemoryManager.peacefulBiomes(appContext)
        )
    }

    private fun displayName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }

    private val loreByType: Map<EntityType, ForestJournalLore> = mapOf(
        EntityType.CACTUS to ForestJournalLore(
            "Patient, prickly, readable",
            "A still obstacle that rewards a clean read. Repeated safe crossings turn fear into a small remembered bloom."
        ),
        EntityType.LILY_OF_VALLEY to ForestJournalLore(
            "Delicate, low, inviting",
            "Tiny bells gather close to the ground; the safest response is attentive movement rather than haste."
        ),
        EntityType.HYACINTH to ForestJournalLore(
            "Bright, clustered, buoyant",
            "Dense colour makes this flower easy to notice, but its shape asks for a measured approach."
        ),
        EntityType.EUCALYPTUS to ForestJournalLore(
            "Tall, fragrant, sheltering",
            "Its long silhouette changes the rhythm of the lane and makes the surrounding air feel briefly cooler."
        ),
        EntityType.VANILLA_ORCHID to ForestJournalLore(
            "Rare, curling, gentle",
            "A softer forest sign whose presence is more invitation than threat when you give it enough room."
        ),
        EntityType.WEEPING_WILLOW to ForestJournalLore(
            "Ancient, protective, watchful",
            "The willow is both threshold and witness: the shape that sends you out and receives you when the run is over."
        ),
        EntityType.JACARANDA to ForestJournalLore(
            "Vivid, drifting, ceremonial",
            "Purple canopy and falling petals turn a traversal cue into a moment that feels deliberately placed."
        ),
        EntityType.BAMBOO to ForestJournalLore(
            "Quick, vertical, whispering",
            "A narrow rhythm of stems that teaches spacing and timing more than brute reaction."
        ),
        EntityType.CHERRY_BLOSSOM to ForestJournalLore(
            "Soft, seasonal, fleeting",
            "Petals make the encounter feel generous, but the path still asks you to stay present."
        ),
        EntityType.DUCK to ForestJournalLore(
            "Grounded, social, unhurried",
            "A familiar little traveller whose movement is easiest to understand when you stop treating every body as a hazard."
        ),
        EntityType.TIT to ForestJournalLore(
            "Restless, light, curious",
            "Small and quick in the air, it rewards reading the whole flight rather than reacting to one frame."
        ),
        EntityType.CHICKADEE to ForestJournalLore(
            "Friendly, flickering, close",
            "Its flight keeps the upper lane alive and makes a clean pass feel like sharing a path."
        ),
        EntityType.OWL to ForestJournalLore(
            "Quiet, observant, deliberate",
            "The owl remembers restraint. With enough gentle outcomes, its watchfulness becomes recognition."
        ),
        EntityType.EAGLE to ForestJournalLore(
            "Distant, decisive, proud",
            "A strong aerial silhouette whose attention can become a bond only after repeated respectful crossings."
        ),
        EntityType.CAT to ForestJournalLore(
            "Independent, curious, warm on its terms",
            "The cat notices consistency. Trust grows through repeated gentle meetings rather than simple appearances."
        ),
        EntityType.WOLF to ForestJournalLore(
            "Guarded, intense, loyal once known",
            "The wolf begins as pressure and becomes one of the clearest measures of whether the forest believes your restraint."
        ),
        EntityType.FOX to ForestJournalLore(
            "Clever, playful, cautious",
            "The fox reads your habits quickly; clean passes and mercy turn surprise into familiarity."
        ),
        EntityType.HEDGEHOG to ForestJournalLore(
            "Small, defensive, earnest",
            "Its shape asks for space. The kinder read is often simply to let a nervous creature keep its own line."
        ),
        EntityType.DOG to ForestJournalLore(
            "Open, energetic, companionable",
            "The dog's multi-part greetings can become a recurring friendship ritual when you answer without panic."
        )
    ).also { lore ->
        require(lore.keys == EntityType.entries.toSet()) {
            "Forest journal lore must cover all nineteen encounter families"
        }
    }
}
