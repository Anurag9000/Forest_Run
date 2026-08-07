package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAccessibilityGeometryTest {
    @Test
    fun everyPublishedNodeGetsFiniteContainedBounds() {
        val width = 1920f
        val height = 1080f
        AccessibilitySurface.entries.forEach { surface ->
            val nodes = GameAccessibilitySemantics.build(
                AccessibilitySemanticSnapshot(
                    surface = surface,
                    gardenUnlockedPlants = 1,
                    gardenTotalPlants = GardenEconomy.catalogueSize
                )
            )
            nodes.forEach { node ->
                val bounds = GameAccessibilityGeometry.boundsFor(node.id, width, height)
                assertTrue(bounds.left >= 0f)
                assertTrue(bounds.top >= 0f)
                assertTrue(bounds.right <= width)
                assertTrue(bounds.bottom <= height)
                assertTrue(bounds.left < bounds.right)
                assertTrue(bounds.top < bounds.bottom)
            }
        }
    }

    @Test
    fun gardenPlantBoundsFollowCanonicalCatalogueOrder() {
        val bounds = (0 until GardenEconomy.catalogueSize).map { index ->
            GameAccessibilityGeometry.boundsFor(
                AccessibilityNodeIds.GARDEN_FIRST_PLANT + index,
                width = 1920f,
                height = 1080f
            )
        }

        assertEquals(GardenEconomy.catalogueSize, bounds.size)
        assertTrue(bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
    }

    @Test
    fun settingsNodesMatchVisibleComfortRows() {
        val motion = GameAccessibilityGeometry.boundsFor(
            AccessibilityNodeIds.SETTINGS_REDUCED_MOTION,
            1920f,
            1080f
        )
        val audio = GameAccessibilityGeometry.boundsFor(
            AccessibilityNodeIds.SETTINGS_AUDIO,
            1920f,
            1080f
        )
        val haptics = GameAccessibilityGeometry.boundsFor(
            AccessibilityNodeIds.SETTINGS_HAPTICS,
            1920f,
            1080f
        )

        assertTrue(motion.bottom < audio.bottom)
        assertTrue(audio.bottom < haptics.bottom)
        assertEquals(motion.left, audio.left, 0.001f)
        assertEquals(audio.left, haptics.left, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveViewportFailsClosed() {
        GameAccessibilityGeometry.boundsFor(
            AccessibilityNodeIds.MENU_CONTINUE,
            width = 0f,
            height = 1080f
        )
    }
}
