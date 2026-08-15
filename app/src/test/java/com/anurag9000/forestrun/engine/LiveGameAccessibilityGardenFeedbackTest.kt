package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGameAccessibilityGardenFeedbackTest {
    @Test
    fun successfulVirtualPlantPurchaseEmitsExactlyOneGrowthCue() {
        var requestedIndex = -1
        var purchaseCalls = 0
        var growthCues = 0
        val actions = actions(
            purchasePlantAction = { index ->
                requestedIndex = index
                purchaseCalls++
                true
            },
            gardenGrowthFeedbackAction = { growthCues++ }
        )

        val handled = actions.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT + 3,
            AccessibilitySemanticAction.ACTIVATE
        )

        assertTrue(handled)
        assertEquals(3, requestedIndex)
        assertEquals(1, purchaseCalls)
        assertEquals(1, growthCues)
    }

    @Test
    fun rejectedVirtualPlantPurchaseEmitsNoGrowthCue() {
        var purchaseCalls = 0
        var growthCues = 0
        val actions = actions(
            purchasePlantAction = {
                purchaseCalls++
                false
            },
            gardenGrowthFeedbackAction = { growthCues++ }
        )

        val handled = actions.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT,
            AccessibilitySemanticAction.ACTIVATE
        )

        assertFalse(handled)
        assertEquals(1, purchaseCalls)
        assertEquals(0, growthCues)
    }

    @Test
    fun wrongSemanticActionDoesNotAttemptPurchaseOrFeedback() {
        var purchaseCalls = 0
        var growthCues = 0
        val actions = actions(
            purchasePlantAction = {
                purchaseCalls++
                true
            },
            gardenGrowthFeedbackAction = { growthCues++ }
        )

        val handled = actions.perform(
            AccessibilityNodeIds.GARDEN_FIRST_PLANT,
            AccessibilitySemanticAction.DISMISS
        )

        assertFalse(handled)
        assertEquals(0, purchaseCalls)
        assertEquals(0, growthCues)
    }

    private fun actions(
        purchasePlantAction: (Int) -> Boolean,
        gardenGrowthFeedbackAction: () -> Unit
    ): LiveGameAccessibilityActions = LiveGameAccessibilityActions(
        menuPrimaryAction = { false },
        sessionEventAction = { false },
        openSettingsAction = { false },
        closeSettingsAction = { false },
        toggleReducedMotionAction = { false },
        toggleAudioAction = { false },
        toggleHapticsAction = { false },
        jumpAction = { false },
        longJumpAction = { false },
        duckAction = { false },
        purchasePlantAction = purchasePlantAction,
        equipCostumeAction = { _: CostumeStyle -> false },
        gardenGrowthFeedbackAction = gardenGrowthFeedbackAction
    )
}
