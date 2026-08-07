package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterFamilyCatalogueTest {
    @Test
    fun coversEveryEntityTypeExactlyOnceInOrdinalOrder() {
        assertEquals(19, EncounterFamilyCatalogue.entries.size)
        assertEquals(
            EntityType.entries.toList(),
            EncounterFamilyCatalogue.entries.map { it.type }
        )
        assertEquals(
            EncounterFamilyCatalogue.entries.size,
            EncounterFamilyCatalogue.entries.map { it.sourcePath }.toSet().size
        )
        assertEquals(EntityType.entries.toList(), EncounterFamilyCatalogue.profiles.map { it.type })
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

    @Test
    fun everyTypeIsOrdinaryBiomeReachableAndHasFocusedDeterministicRead() {
        EncounterFamilyCatalogue.profiles.forEach { profile ->
            assertTrue(profile.preferredBiomes.isNotEmpty())
            assertTrue(profile.scenarioCoverage.isNotEmpty())
            assertTrue(profile.focusedFairnessScenarios.isNotEmpty())
            assertTrue(
                profile.focusedFairnessScenarios.all { scenario ->
                    scenario.steps.isNotEmpty() && scenario.steps.all { it.type == profile.type }
                }
            )
            assertTrue(profile.fairnessReads.all(String::isNotBlank))
        }
    }

    @Test
    fun derivedProfilesSpanEveryBiomeAndEveryScenario() {
        assertEquals(
            Biome.entries.toSet(),
            EncounterFamilyCatalogue.profiles.flatMap { it.preferredBiomes }.toSet()
        )
        assertEquals(
            EncounterScenario.entries.toSet(),
            EncounterFamilyCatalogue.profiles.flatMap { it.scenarioCoverage }.toSet()
        )
    }

    @Test
    fun relationshipCapabilityTracksRelationshipAuthorityExactly() {
        val tracked = EncounterFamilyCatalogue.profiles
            .filter { it.relationshipTracked }
            .map { it.type }
            .toSet()
        assertEquals(
            setOf(
                EntityType.CAT,
                EntityType.FOX,
                EntityType.WOLF,
                EntityType.DOG,
                EntityType.OWL,
                EntityType.EAGLE
            ),
            tracked
        )
        EntityType.entries.forEach { type ->
            assertEquals(
                RelationshipArcSystem.isTracked(type),
                EncounterFamilyCatalogue.profile(type).relationshipTracked
            )
        }
    }

    @Test
    fun dogVariantsAreDerivedWithoutDuplicatingFactoryRules() {
        val dog = EncounterFamilyCatalogue.profile(EntityType.DOG)
        assertTrue(dog.hasMultipleAuthoredVariants)
        assertEquals(
            setOf(
                EncounterVariant.DEFAULT,
                EncounterVariant.DOG_HAZARD,
                EncounterVariant.DOG_BUDDY
            ),
            dog.variants
        )
        EncounterFamilyCatalogue.profiles
            .filter { it.type != EntityType.DOG }
            .forEach { profile ->
                assertFalse(profile.hasMultipleAuthoredVariants)
                assertEquals(setOf(EncounterVariant.DEFAULT), profile.variants)
            }
    }
}
