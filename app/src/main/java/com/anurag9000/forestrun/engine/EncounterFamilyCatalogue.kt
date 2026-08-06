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
 * Exhaustive structural catalogue for the nineteen authored encounter families.
 *
 * Dynamic values such as hitboxes, speed, lanes, mercy radii, cue timing, and
 * animation frames deliberately remain in each implementation and SpriteManager.
 * This catalogue prevents content tooling from relying on duplicated gameplay
 * constants while still providing one complete, queryable type inventory.
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
        require(entries.map(EncounterFamilyDescriptor::sourcePath).toSet().size == entries.size) {
            "Encounter catalogue source paths must be unique"
        }
    }

    fun descriptor(type: EntityType): EncounterFamilyDescriptor = byType.getValue(type)

    fun inGroup(group: EncounterFamilyGroup): List<EncounterFamilyDescriptor> =
        entries.filter { it.group == group }

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
