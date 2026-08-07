package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType

internal enum class EncounterFamilyGroup {
    FLORA,
    TREE,
    BIRD,
    ANIMAL
}

/** Source owner for one mutable encounter-authoring dimension. */
internal enum class EncounterAuthoringOwner {
    ENTITY_IMPLEMENTATION,
    SPRITE_MANAGER,
    RUN_FLAVOR_PRESENTATION,
    PERSISTENT_MEMORY_MANAGER,
    GAME_STATE_MANAGER
}

internal data class EncounterAuthoringOwnership(
    val movementAndLane: EncounterAuthoringOwner,
    val collisionAndMercy: EncounterAuthoringOwner,
    val fairnessCues: EncounterAuthoringOwner,
    val assets: EncounterAuthoringOwner,
    val flavorCopy: EncounterAuthoringOwner,
    val relationships: EncounterAuthoringOwner,
    val routeContribution: EncounterAuthoringOwner
)

internal data class EncounterFamilyDescriptor(
    val type: EntityType,
    val group: EncounterFamilyGroup,
    val implementationClass: String,
    val sourcePath: String
)

/**
 * Derived capabilities for one structural encounter descriptor.
 *
 * These fields are rebuilt from their existing runtime authorities; they do not
 * duplicate biome pools, scenario steps/variants, or relationship thresholds.
 */
internal data class EncounterFamilyProfile(
    val descriptor: EncounterFamilyDescriptor,
    val preferredBiomes: Set<Biome>,
    val scenarioCoverage: Set<EncounterScenario>,
    val focusedFairnessScenarios: Set<EncounterScenario>,
    val variants: Set<EncounterVariant>,
    val relationshipTracked: Boolean
) {
    init {
        require(preferredBiomes.isNotEmpty()) {
            "${descriptor.type} must remain reachable from ordinary biome rotation"
        }
        require(scenarioCoverage.isNotEmpty()) {
            "${descriptor.type} must remain covered by deterministic scenarios"
        }
        require(focusedFairnessScenarios.isNotEmpty()) {
            "${descriptor.type} must retain a focused fairness/readability scenario"
        }
        require(EncounterVariant.DEFAULT in variants) {
            "${descriptor.type} must retain its default variant"
        }
    }

    val type: EntityType
        get() = descriptor.type

    val group: EncounterFamilyGroup
        get() = descriptor.group

    val hasMultipleAuthoredVariants: Boolean
        get() = variants.size > 1

    val fairnessReads: List<String>
        get() = focusedFairnessScenarios
            .sortedBy(EncounterScenario::ordinal)
            .map(EncounterScenario::summary)
}

/**
 * Exhaustive structural catalogue for the nineteen authored encounter families.
 *
 * Dynamic values such as hitboxes, speed, lanes, mercy radii, cue timing, and
 * animation frames deliberately remain in each implementation and SpriteManager.
 * Structural implementation identity is explicit here; biome reachability,
 * deterministic fairness/readability coverage, authored variants, and
 * relationship capability are derived from their authoritative systems.
 */
internal object EncounterFamilyCatalogue {
    val authoringOwnership = EncounterAuthoringOwnership(
        movementAndLane = EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
        collisionAndMercy = EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
        fairnessCues = EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
        assets = EncounterAuthoringOwner.SPRITE_MANAGER,
        flavorCopy = EncounterAuthoringOwner.RUN_FLAVOR_PRESENTATION,
        relationships = EncounterAuthoringOwner.PERSISTENT_MEMORY_MANAGER,
        routeContribution = EncounterAuthoringOwner.GAME_STATE_MANAGER
    )

    val entries: List<EncounterFamilyDescriptor> = listOf(
        descriptor(EntityType.CACTUS, EncounterFamilyGroup.FLORA, "Cactus", "flora"),
        descriptor(EntityType.LILY_OF_VALLEY, EncounterFamilyGroup.FLORA, "LilyOfValley", "flora"),
        descriptor(EntityType.HYACINTH, EncounterFamilyGroup.FLORA, "Hyacinth", "flora"),
        descriptor(EntityType.EUCALYPTUS, EncounterFamilyGroup.FLORA, "Eucalyptus", "flora"),
        descriptor(EntityType.VANILLA_ORCHID, EncounterFamilyGroup.FLORA, "VanillaOrchid", "flora"),
        descriptor(EntityType.WEEPING_WILLOW, EncounterFamilyGroup.TREE, "WeepingWillow", "trees"),
        descriptor(EntityType.JACARANDA, EncounterFamilyGroup.TREE, "Jacaranda", "trees"),
        descriptor(EntityType.BAMBOO, EncounterFamilyGroup.TREE, "Bamboo", "trees"),
        descriptor(EntityType.CHERRY_BLOSSOM, EncounterFamilyGroup.TREE, "CherryBlossom", "trees"),
        descriptor(EntityType.DUCK, EncounterFamilyGroup.BIRD, "Duck", "birds"),
        descriptor(EntityType.TIT, EncounterFamilyGroup.BIRD, "TitGroup", "birds"),
        descriptor(EntityType.CHICKADEE, EncounterFamilyGroup.BIRD, "ChickadeeGroup", "birds"),
        descriptor(EntityType.OWL, EncounterFamilyGroup.BIRD, "Owl", "birds"),
        descriptor(EntityType.EAGLE, EncounterFamilyGroup.BIRD, "Eagle", "birds"),
        descriptor(EntityType.CAT, EncounterFamilyGroup.ANIMAL, "Cat", "animals"),
        descriptor(EntityType.WOLF, EncounterFamilyGroup.ANIMAL, "Wolf", "animals"),
        descriptor(EntityType.FOX, EncounterFamilyGroup.ANIMAL, "Fox", "animals"),
        descriptor(EntityType.HEDGEHOG, EncounterFamilyGroup.ANIMAL, "Hedgehog", "animals"),
        descriptor(EntityType.DOG, EncounterFamilyGroup.ANIMAL, "Dog", "animals")
    )

    private val byType = entries.associateBy(EncounterFamilyDescriptor::type)

    val profiles: List<EncounterFamilyProfile> by lazy {
        entries.map(::buildProfile).also(::validateProfiles)
    }

    init {
        require(entries.size == EntityType.entries.size) {
            "Encounter catalogue must contain every EntityType exactly once"
        }
        require(byType.size == entries.size) {
            "Encounter catalogue contains a duplicate EntityType"
        }
        require(EntityType.entries.all(byType::containsKey)) {
            "Encounter catalogue is missing an EntityType"
        }
        require(entries.map(EncounterFamilyDescriptor::type) == EntityType.entries.toList()) {
            "Encounter catalogue order must remain EntityType ordinal order"
        }
        require(entries.map(EncounterFamilyDescriptor::sourcePath).toSet().size == entries.size) {
            "Encounter catalogue source paths must be unique"
        }
    }

    fun descriptor(type: EntityType): EncounterFamilyDescriptor = byType.getValue(type)

    fun profile(type: EntityType): EncounterFamilyProfile = profiles[type.ordinal].also {
        check(it.type == type) { "Encounter profile ordinal drift for $type" }
    }

    fun inGroup(group: EncounterFamilyGroup): List<EncounterFamilyDescriptor> =
        entries.filter { it.group == group }

    private fun buildProfile(descriptor: EncounterFamilyDescriptor): EncounterFamilyProfile {
        val type = descriptor.type
        val coverage = EncounterScenario.entries
            .filterTo(linkedSetOf()) { scenario ->
                scenario.steps.any { it.type == type }
            }
        val focused = coverage
            .filterTo(linkedSetOf()) { scenario ->
                scenario.steps.all { it.type == type }
            }
        val variants = linkedSetOf(EncounterVariant.DEFAULT).apply {
            coverage.forEach { scenario ->
                scenario.steps.forEach { step ->
                    if (step.type == type) add(step.variant)
                }
            }
        }
        val biomes = Biome.entries
            .filterTo(linkedSetOf()) { biome -> type in biome.preferredPool }

        return EncounterFamilyProfile(
            descriptor = descriptor,
            preferredBiomes = biomes,
            scenarioCoverage = coverage,
            focusedFairnessScenarios = focused,
            variants = variants,
            relationshipTracked = RelationshipArcSystem.isTracked(type)
        )
    }

    private fun validateProfiles(profiles: List<EncounterFamilyProfile>) {
        check(profiles.map(EncounterFamilyProfile::type) == EntityType.entries.toList()) {
            "Encounter profile coverage/order drifted"
        }
        check(profiles.flatMap { it.preferredBiomes }.toSet() == Biome.entries.toSet()) {
            "Encounter profiles must span every ordinary biome"
        }
        check(profiles.flatMap { it.scenarioCoverage }.toSet() == EncounterScenario.entries.toSet()) {
            "Every deterministic scenario must remain represented by encounter profiles"
        }
    }

    private fun descriptor(
        type: EntityType,
        group: EncounterFamilyGroup,
        implementationClass: String,
        packageName: String
    ): EncounterFamilyDescriptor = EncounterFamilyDescriptor(
        type = type,
        group = group,
        implementationClass = implementationClass,
        sourcePath = "com/anurag9000/forestrun/entities/$packageName/$implementationClass.kt"
    )
}
