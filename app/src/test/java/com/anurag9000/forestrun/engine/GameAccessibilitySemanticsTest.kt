package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAccessibilitySemanticsTest {
    @Test
    fun everySurfaceHasUniqueStableIdsAndStrictFocusOrder() {
        AccessibilitySurface.entries.forEach { surface ->
            val nodes = GameAccessibilitySemantics.build(
                AccessibilitySemanticSnapshot(surface = surface)
            )

            assertTrue(nodes.isNotEmpty())
            assertEquals(nodes.size, nodes.map { it.id }.distinct().size)
            assertEquals(nodes.size, nodes.map { it.focusOrder }.distinct().size)
            assertEquals(nodes.sortedBy { it.focusOrder }, nodes)
            assertTrue(nodes.all { it.label.isNotBlank() })
        }
    }

    @Test
    fun playingStatusIsLiveAndExposesOnlyBoundedGameActions() {
        val nodes = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.PLAYING,
                distanceM = 123,
                score = 900,
                seeds = 7,
                bloomReady = true
            )
        )

        val status = nodes.first()
        assertEquals(AccessibilityNodeIds.RUN_STATUS, status.id)
        assertTrue(status.liveRegion)
        assertEquals(
            "123 metres, score 900, 7 Seeds, Bloom ready",
            status.stateDescription
        )
        assertEquals(
            listOf(
                AccessibilitySemanticAction.JUMP,
                AccessibilitySemanticAction.LONG_JUMP,
                AccessibilitySemanticAction.DUCK
            ),
            nodes.drop(1).map { it.actions.single() }
        )
    }

    @Test
    fun settingsDescribeCurrentBooleanState() {
        val nodes = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.SETTINGS,
                reducedMotion = true,
                audioEnabled = false,
                hapticsEnabled = true
            )
        )

        assertEquals(
            listOf("On", "Off", "On", null),
            nodes.map { it.stateDescription }
        )
        assertEquals(
            AccessibilitySemanticAction.DISMISS,
            nodes.last().actions.single()
        )
    }

    @Test
    fun gardenExposesExactlyOneAffordableNextPlant() {
        val nodes = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                seeds = 10,
                gardenUnlockedPlants = 3,
                gardenTotalPlants = 9,
                nextPlantCost = 8
            )
        )
        val plantNodes = nodes.filter {
            it.id in AccessibilityNodeIds.GARDEN_FIRST_PLANT until
                AccessibilityNodeIds.GARDEN_FIRST_PLANT + 9
        }

        assertEquals(9, plantNodes.size)
        assertEquals("Grown", plantNodes[2].stateDescription)
        assertEquals("Next plant, costs 8 Seeds", plantNodes[3].stateDescription)
        assertEquals(
            setOf(AccessibilitySemanticAction.ACTIVATE),
            plantNodes[3].actions
        )
        assertTrue(plantNodes.drop(4).all { it.actions.isEmpty() && !it.enabled })

        val wardrobe = nodes.single { it.id == AccessibilityNodeIds.GARDEN_WARDROBE }
        assertTrue(wardrobe.enabled)
        assertTrue(wardrobe.actions.isEmpty())
        assertEquals("Classic style only", wardrobe.stateDescription)
    }

    @Test
    fun wardrobePublishesEachStyleAndOnlyUnlockedStylesAreActionable() {
        val unlocked = setOf(CostumeStyle.NONE, CostumeStyle.FLOWER_CROWN)
        val nodes = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                gardenUnlockedPlants = 1,
                wardrobeUnlocked = true,
                wardrobeUnlockedCostumes = unlocked,
                activeCostume = CostumeStyle.FLOWER_CROWN
            )
        )
        val costumes = nodes.filter {
            it.id in AccessibilityNodeIds.GARDEN_FIRST_COSTUME until
                AccessibilityNodeIds.GARDEN_FIRST_COSTUME + CostumeStyle.entries.size
        }

        assertEquals(CostumeStyle.entries.size, costumes.size)
        assertEquals("Available", costumes[CostumeStyle.NONE.ordinal].stateDescription)
        assertEquals("Equipped", costumes[CostumeStyle.FLOWER_CROWN.ordinal].stateDescription)
        assertEquals(
            setOf(AccessibilitySemanticAction.ACTIVATE),
            costumes[CostumeStyle.FLOWER_CROWN.ordinal].actions
        )
        val locked = costumes[CostumeStyle.MOON_CAPE.ordinal]
        assertEquals(CostumeStyle.MOON_CAPE.unlockLabel, locked.stateDescription)
        assertTrue(locked.enabled)
        assertTrue(locked.actions.isEmpty())
    }

    @Test
    fun unaffordableNextPlantRemainsDescribedButNotActionable() {
        val plant = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                seeds = 2,
                gardenUnlockedPlants = 1,
                nextPlantCost = 8
            )
        ).single { it.id == AccessibilityNodeIds.GARDEN_FIRST_PLANT + 1 }

        assertEquals("Next plant, costs 8 Seeds", plant.stateDescription)
        assertTrue(plant.enabled)
        assertTrue(plant.actions.isEmpty())
    }

    @Test
    fun restUsesAuthoredQuoteAndSummaryWithDeterministicFallbacks() {
        val authored = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.REST,
                restQuote = "The willow remembers your kindness.",
                restSummary = "Four creatures spared."
            )
        ).first()
        assertEquals("The willow remembers your kindness.", authored.label)
        assertEquals("Four creatures spared.", authored.stateDescription)

        val fallback = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.REST,
                score = 20,
                distanceM = 42,
                seeds = 3
            )
        ).first()
        assertEquals("Rest beneath the willow", fallback.label)
        assertEquals("Score 20, 42 metres, 3 Seeds", fallback.stateDescription)
    }

    @Test
    fun dyingRestDoesNotExposeContinueActionEarly() {
        val continueNode = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.REST,
                restContinueEnabled = false
            )
        ).single { it.id == AccessibilityNodeIds.REST_CONTINUE }

        assertFalse(continueNode.enabled)
        assertTrue(continueNode.actions.isEmpty())
        assertEquals("Recovering before Garden", continueNode.label)
    }

    @Test
    fun malformedStateFailsClosed() {
        val invalid = listOf(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.PLAYING,
                distanceM = -1
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                gardenUnlockedPlants = 10
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                nextPlantCost = -1
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                wardrobeUnlockedCostumes = setOf(CostumeStyle.FLOWER_CROWN)
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                activeCostume = CostumeStyle.FLOWER_CROWN
            )
        )

        invalid.forEach { snapshot ->
            try {
                GameAccessibilitySemantics.build(snapshot)
                error("expected invalid semantic snapshot")
            } catch (_: IllegalArgumentException) {
                // Expected fail-closed boundary.
            }
        }
    }
}
