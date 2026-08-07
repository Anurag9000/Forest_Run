package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType

/** Stable high-level content families matching the authored 19-type roster. */
internal enum class EncounterContentFamily {
    FLORA,
    TREE,
    BIRD,
    ANIMAL
}

/**
 * Queryable, drift-resistant view of one authored encounter family member.
 *
 * Biome membership, debug/fairness coverage, variants, and relationship support
 * are derived from their existing runtime authorities rather than copied here.
 */
internal data class EncounterContentProfile(
    val type: EntityType,
    val family: EncounterContentFamily,
    val displayName: String,
    val preferredBiomes: Set<Biome>,
    val scenarioCoverage: Set<EncounterScenario>,
    val focusedFairnessScenarios: Set<EncounterScenario>,
    val variants: Set<EncounterVariant>,
    val relationshipTracked: Boolean
) {
    init {
        require(displayName.isNotBlank())
        require(preferredBiomes.isNotEmpty()) {
            "$type must remain reachable from ordinary biome rotation"
        }
        require(scenarioCoverage.isNotEmpty()) {
            "$type must remain covered by deterministic encounter scenarios"
        }
        require(focusedFairnessScenarios.isNotEmpty()) {
            "$type must retain at least one focused deterministic fairness/readability scenario"
        }
        require(EncounterVariant.DEFAULT in variants) {
            "$type must retain a default factory/debug variant"
        }
    }

    val hasMultipleAuthoredVariants: Boolean
        get() = variants.size > 1

    /** Human-readable scenario summaries suitable for audits/debug tooling. */
    val fairnessReads: List<String>
        get() = focusedFairnessScenarios
            .sortedBy { it.ordinal }
            .map { it.summary }
}

/**
 * Canonical derived catalogue over the existing encounter authorities.
 *
 * This object intentionally does not own spawn probabilities, collision rules,
 * sprite assets, or relationship thresholds. Those remain in Biome/EntityFactory,
 * concrete entities, SpriteManager, and RelationshipArcSystem respectively.
 */
internal object EncounterContentCatalogue {
    val profiles: List<EncounterContentProfile> by lazy {
        EntityType.entries.map(::buildProfile).also(::validateCatalogue)
    }

    fun profile(type: EntityType): EncounterContentProfile = profiles[type.ordinal].also {
        check(it.type == type) { "Encounter catalogue ordinal drift for $type" }
    }

    fun familyOf(type: EntityType): EncounterContentFamily = when (type) {
        EntityType.CACTUS,
        EntityType.LILY_OF_VALLEY,
        EntityType.HYACINTH,
        EntityType.EUCALYPTUS,
        EntityType.VANILLA_ORCHID -> EncounterContentFamily.FLORA

        EntityType.WEEPING_WILLOW,
        EntityType.JACARANDA,
        EntityType.BAMBOO,
        EntityType.CHERRY_BLOSSOM -> EncounterContentFamily.TREE

        EntityType.DUCK,
        EntityType.TIT,
        EntityType.CHICKADEE,
        EntityType.OWL,
        EntityType.EAGLE -> EncounterContentFamily.BIRD

        EntityType.CAT,
        EntityType.WOLF,
        EntityType.FOX,
        EntityType.HEDGEHOG,
        EntityType.DOG -> EncounterContentFamily.ANIMAL
    }

    private fun buildProfile(type: EntityType): EncounterContentProfile {
        val coverage = EncounterScenario.entries
            .filterTo(linkedSetOf()) { scenario ->
                scenario.steps.any { it.type == type }
            }
        val focused = coverage
            .filterTo(linkedSetOf()) { scenario ->
                scenario.steps.all { it.type == type }
            }
        val authoredVariants = linkedSetOf(EncounterVariant.DEFAULT).apply {
            coverage.forEach { scenario ->
                scenario.steps.forEach { step ->
                    if (step.type == type) add(step.variant)
                }
            }
        }
        val biomes = Biome.entries
            .filterTo(linkedSetOf()) { biome -> type in biome.preferredPool }

        return EncounterContentProfile(
            type = type,
            family = familyOf(type),
            displayName = type.name
                .lowercase()
                .split('_')
                .joinToString(" ") { token ->
                    token.replaceFirstChar { character -> character.titlecase() }
                },
            preferredBiomes = biomes,
            scenarioCoverage = coverage,
            focusedFairnessScenarios = focused,
            variants = authoredVariants,
            relationshipTracked = RelationshipArcSystem.isTracked(type)
        )
    }

    private fun validateCatalogue(profiles: List<EncounterContentProfile>) {
        check(profiles.size == EntityType.entries.size) {
            "Encounter catalogue must cover every EntityType exactly once"
        }
        check(profiles.map { it.type }.toSet() == EntityType.entries.toSet()) {
            "Encounter catalogue type coverage drifted"
        }
        check(profiles.map { it.type.ordinal } == profiles.indices.toList()) {
            "Encounter catalogue ordering must remain EntityType ordinal order"
        }
        check(profiles.flatMap { it.preferredBiomes }.toSet() == Biome.entries.toSet()) {
            "Encounter catalogue must retain coverage across every biome"
        }
        check(profiles.flatMap { it.scenarioCoverage }.toSet() == EncounterScenario.entries.toSet()) {
            "Every deterministic scenario must remain represented by catalogue coverage"
        }
    }
}
