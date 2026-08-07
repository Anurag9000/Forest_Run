package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterContentCatalogueTest {
    @Test
    fun catalogueCoversAllNineteenTypesInStableOrdinalOrder() {
        val profiles = EncounterContentCatalogue.profiles

        assertEquals(19, EntityType.entries.size)
        assertEquals(EntityType.entries.toList(), profiles.map { it.type })
        assertEquals(
            mapOf(
                EncounterContentFamily.FLORA to 5,
                EncounterContentFamily.TREE to 4,
                EncounterContentFamily.BIRD to 5,
                EncounterContentFamily.ANIMAL to 5
            ),
            profiles.groupingBy { it.family }.eachCount()
        )
    }

    @Test
    fun everyTypeIsOrdinaryBiomeReachableAndHasFocusedDeterministicRead() {
        EncounterContentCatalogue.profiles.forEach { profile ->
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
    fun catalogueCoverageIncludesEveryBiomeAndEveryScenario() {
        assertEquals(
            Biome.entries.toSet(),
            EncounterContentCatalogue.profiles.flatMap { it.preferredBiomes }.toSet()
        )
        assertEquals(
            EncounterScenario.entries.toSet(),
            EncounterContentCatalogue.profiles.flatMap { it.scenarioCoverage }.toSet()
        )
    }

    @Test
    fun relationshipCapabilityIsDerivedFromRelationshipAuthority() {
        val tracked = EncounterContentCatalogue.profiles
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
            assertEquals(RelationshipArcSystem.isTracked(type), EncounterContentCatalogue.profile(type).relationshipTracked)
        }
    }

    @Test
    fun dogVariantsAreDiscoverableWithoutDuplicatingFactoryRules() {
        val dog = EncounterContentCatalogue.profile(EntityType.DOG)

        assertTrue(dog.hasMultipleAuthoredVariants)
        assertEquals(
            setOf(
                EncounterVariant.DEFAULT,
                EncounterVariant.DOG_HAZARD,
                EncounterVariant.DOG_BUDDY
            ),
            dog.variants
        )
        EncounterContentCatalogue.profiles
            .filter { it.type != EntityType.DOG }
            .forEach { profile ->
                assertFalse(profile.hasMultipleAuthoredVariants)
                assertEquals(setOf(EncounterVariant.DEFAULT), profile.variants)
            }
    }

    @Test
    fun profileLookupCannotDriftFromEnumOrdinal() {
        EntityType.entries.forEach { type ->
            assertEquals(type, EncounterContentCatalogue.profile(type).type)
        }
    }
}
