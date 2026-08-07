package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGameAccessibilityActionsTest {
    @Test
    fun stableNodesDispatchOnlyTheirTypedRuntimeCommands() {
        val calls = mutableListOf<String>()
        val handler = handler(calls)

        assertTrue(handler.perform(AccessibilityNodeIds.MENU_CONTINUE, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.MENU_GARDEN, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.MENU_SETTINGS, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.SETTINGS_REDUCED_MOTION, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.SETTINGS_AUDIO, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.SETTINGS_HAPTICS, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.SETTINGS_CLOSE, AccessibilitySemanticAction.DISMISS))
        assertTrue(handler.perform(AccessibilityNodeIds.RUN_JUMP, AccessibilitySemanticAction.JUMP))
        assertTrue(handler.perform(AccessibilityNodeIds.RUN_LONG_JUMP, AccessibilitySemanticAction.LONG_JUMP))
        assertTrue(handler.perform(AccessibilityNodeIds.RUN_DUCK, AccessibilitySemanticAction.DUCK))
        assertTrue(handler.perform(AccessibilityNodeIds.GARDEN_RUN, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.GARDEN_HOME, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.REST_CONTINUE, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(handler.perform(AccessibilityNodeIds.GARDEN_FIRST_PLANT + 3, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(
            handler.perform(
                AccessibilityNodeIds.GARDEN_FIRST_COSTUME + CostumeStyle.MOON_CAPE.ordinal,
                AccessibilitySemanticAction.ACTIVATE
            )
        )

        assertEquals(
            listOf(
                "menu-primary",
                "session:${RunSessionEvent.MENU_GARDEN_REQUESTED}",
                "settings-open",
                "motion",
                "audio",
                "haptics",
                "settings-close",
                "jump",
                "long-jump",
                "duck",
                "session:${RunSessionEvent.GARDEN_RUN_REQUESTED}",
                "session:${RunSessionEvent.GARDEN_BACK_REQUESTED}",
                "session:${RunSessionEvent.REST_TAPPED}",
                "plant:3",
                "costume:${CostumeStyle.MOON_CAPE}"
            ),
            calls
        )
    }

    @Test
    fun mismatchedActionsAndInformationalNodesFailClosed() {
        val calls = mutableListOf<String>()
        val handler = handler(calls)

        assertFalse(handler.perform(AccessibilityNodeIds.RUN_JUMP, AccessibilitySemanticAction.ACTIVATE))
        assertFalse(handler.perform(AccessibilityNodeIds.SETTINGS_CLOSE, AccessibilitySemanticAction.ACTIVATE))
        assertFalse(handler.perform(AccessibilityNodeIds.RUN_STATUS, AccessibilitySemanticAction.ACTIVATE))
        assertFalse(handler.perform(AccessibilityNodeIds.GARDEN_WARDROBE, AccessibilitySemanticAction.ACTIVATE))
        assertFalse(handler.perform(9999, AccessibilitySemanticAction.ACTIVATE))
        assertTrue(calls.isEmpty())
    }

    private fun handler(calls: MutableList<String>): LiveGameAccessibilityActions =
        LiveGameAccessibilityActions(
            menuPrimaryAction = { calls += "menu-primary"; true },
            sessionEventAction = { event -> calls += "session:$event"; true },
            openSettingsAction = { calls += "settings-open"; true },
            closeSettingsAction = { calls += "settings-close"; true },
            toggleReducedMotionAction = { calls += "motion"; true },
            toggleAudioAction = { calls += "audio"; true },
            toggleHapticsAction = { calls += "haptics"; true },
            jumpAction = { calls += "jump"; true },
            longJumpAction = { calls += "long-jump"; true },
            duckAction = { calls += "duck"; true },
            purchasePlantAction = { index -> calls += "plant:$index"; true },
            equipCostumeAction = { style -> calls += "costume:$style"; true }
        )
}
