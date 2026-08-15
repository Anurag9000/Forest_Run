package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReducedMotionAccessibilityInvariantTest {
    @Test
    fun `reduced motion cannot change non-settings semantic information or actions`() {
        val snapshots = listOf(
            AccessibilitySemanticSnapshot(surface = AccessibilitySurface.MENU),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.PLAYING,
                distanceM = 731,
                score = 12_400,
                seeds = 9,
                bloomReady = true
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.GARDEN,
                seeds = 41,
                gardenUnlockedPlants = 4,
                nextPlantCost = 30,
                wardrobeUnlocked = false
            ),
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.REST,
                distanceM = 731,
                score = 12_400,
                seeds = 9,
                restQuote = "The willow remembers.",
                restSummary = "A quiet run came home.",
                restContinueEnabled = true
            )
        )

        snapshots.forEach { snapshot ->
            val fullMotion = GameAccessibilitySemantics.build(
                snapshot.copy(reducedMotion = false)
            )
            val reducedMotion = GameAccessibilitySemantics.build(
                snapshot.copy(reducedMotion = true)
            )

            assertEquals(
                "reduced motion changed semantic tree for ${snapshot.surface}",
                fullMotion,
                reducedMotion
            )
        }
    }

    @Test
    fun `settings reports reduced motion without changing sibling semantics`() {
        val fullMotion = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.SETTINGS,
                reducedMotion = false,
                audioEnabled = false,
                hapticsEnabled = true
            )
        )
        val reducedMotion = GameAccessibilitySemantics.build(
            AccessibilitySemanticSnapshot(
                surface = AccessibilitySurface.SETTINGS,
                reducedMotion = true,
                audioEnabled = false,
                hapticsEnabled = true
            )
        )

        val reducedMotionId = AccessibilityNodeIds.SETTINGS_REDUCED_MOTION
        val fullMotionToggle = fullMotion.single { it.id == reducedMotionId }
        val reducedMotionToggle = reducedMotion.single { it.id == reducedMotionId }

        assertEquals("Off", fullMotionToggle.stateDescription)
        assertEquals("On", reducedMotionToggle.stateDescription)
        assertEquals(
            fullMotionToggle.copy(stateDescription = null),
            reducedMotionToggle.copy(stateDescription = null)
        )
        assertEquals(
            fullMotion.filterNot { it.id == reducedMotionId },
            reducedMotion.filterNot { it.id == reducedMotionId }
        )
    }
}
