package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterFamilyCatalogueTest {
    @Test
    fun coversEveryEntityTypeExactlyOnce() {
        assertEquals(19, EncounterFamilyCatalogue.entries.size)
        assertEquals(
            EntityType.entries.toSet(),
            EncounterFamilyCatalogue.entries.map { it.type }.toSet()
        )
        assertEquals(
            EncounterFamilyCatalogue.entries.size,
            EncounterFamilyCatalogue.entries.map { it.sourcePath }.toSet().size
        )
    }

    @Test
    fun familyCountsMatchTheAuthoredSourcePackages() {
        assertEquals(5, EncounterFamilyCatalogue.inGroup(EncounterFamilyGroup.FLORA).size)
        assertEquals(4, EncounterFamilyCatalogue.inGroup(EncounterFamilyGroup.TREE).size)
        assertEquals(5, EncounterFamilyCatalogue.inGroup(EncounterFamilyGroup.BIRD).size)
        assertEquals(5, EncounterFamilyCatalogue.inGroup(EncounterFamilyGroup.ANIMAL).size)
    }

    @Test
    fun lookupReturnsStableImplementationIdentity() {
        assertEquals(
            "Cactus",
            EncounterFamilyCatalogue.descriptor(EntityType.CACTUS).implementationClass
        )
        assertEquals(
            "TitGroup",
            EncounterFamilyCatalogue.descriptor(EntityType.TIT).implementationClass
        )
        assertEquals(
            "ChickadeeGroup",
            EncounterFamilyCatalogue.descriptor(EntityType.CHICKADEE).implementationClass
        )
        assertEquals(
            "Dog",
            EncounterFamilyCatalogue.descriptor(EntityType.DOG).implementationClass
        )
    }

    @Test
    fun mutableGameplayValuesRemainWithTheirExistingOwners() {
        val owners = EncounterFamilyCatalogue.authoringOwnership
        assertEquals(
            EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
            owners.movementAndLane
        )
        assertEquals(
            EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
            owners.collisionAndMercy
        )
        assertEquals(
            EncounterAuthoringOwner.ENTITY_IMPLEMENTATION,
            owners.fairnessCues
        )
        assertEquals(EncounterAuthoringOwner.SPRITE_MANAGER, owners.assets)
        assertEquals(
            EncounterAuthoringOwner.RUN_FLAVOR_PRESENTATION,
            owners.flavorCopy
        )
        assertEquals(
            EncounterAuthoringOwner.PERSISTENT_MEMORY_MANAGER,
            owners.relationships
        )
        assertEquals(
            EncounterAuthoringOwner.GAME_STATE_MANAGER,
            owners.routeContribution
        )
        assertTrue(
            EncounterFamilyCatalogue.entries.none {
                it.sourcePath.contains("..") || it.sourcePath.startsWith("/")
            }
        )
    }
}
