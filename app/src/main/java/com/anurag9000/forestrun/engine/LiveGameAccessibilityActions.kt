package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.entities.CostumeStyle

/**
 * Typed bridge from stable virtual-node IDs to live game owners.
 *
 * [GameAccessibilityActionRouter] has already validated that a node is current,
 * enabled, and publishes the requested semantic action. This class still checks
 * the expected action per node so direct misuse also fails closed.
 */
internal class LiveGameAccessibilityActions(
    private val menuPrimaryAction: () -> Boolean,
    private val sessionEventAction: (RunSessionEvent) -> Boolean,
    private val openSettingsAction: () -> Boolean,
    private val closeSettingsAction: () -> Boolean,
    private val toggleReducedMotionAction: () -> Boolean,
    private val toggleAudioAction: () -> Boolean,
    private val toggleHapticsAction: () -> Boolean,
    private val jumpAction: () -> Boolean,
    private val longJumpAction: () -> Boolean,
    private val duckAction: () -> Boolean,
    private val purchasePlantAction: (Int) -> Boolean,
    private val equipCostumeAction: (CostumeStyle) -> Boolean,
    private val gardenGrowthFeedbackAction: () -> Unit = HapticManager::gardenGrowth
) : AccessibilitySemanticActionHandler {
    override fun perform(nodeId: Int, action: AccessibilitySemanticAction): Boolean = when {
        nodeId == AccessibilityNodeIds.MENU_CONTINUE &&
            action == AccessibilitySemanticAction.ACTIVATE -> menuPrimaryAction()

        nodeId == AccessibilityNodeIds.MENU_GARDEN &&
            action == AccessibilitySemanticAction.ACTIVATE ->
            sessionEventAction(RunSessionEvent.MENU_GARDEN_REQUESTED)

        nodeId == AccessibilityNodeIds.MENU_SETTINGS &&
            action == AccessibilitySemanticAction.ACTIVATE -> openSettingsAction()

        nodeId == AccessibilityNodeIds.SETTINGS_REDUCED_MOTION &&
            action == AccessibilitySemanticAction.ACTIVATE -> toggleReducedMotionAction()

        nodeId == AccessibilityNodeIds.SETTINGS_AUDIO &&
            action == AccessibilitySemanticAction.ACTIVATE -> toggleAudioAction()

        nodeId == AccessibilityNodeIds.SETTINGS_HAPTICS &&
            action == AccessibilitySemanticAction.ACTIVATE -> toggleHapticsAction()

        nodeId == AccessibilityNodeIds.SETTINGS_CLOSE &&
            action == AccessibilitySemanticAction.DISMISS -> closeSettingsAction()

        nodeId == AccessibilityNodeIds.RUN_JUMP &&
            action == AccessibilitySemanticAction.JUMP -> jumpAction()

        nodeId == AccessibilityNodeIds.RUN_LONG_JUMP &&
            action == AccessibilitySemanticAction.LONG_JUMP -> longJumpAction()

        nodeId == AccessibilityNodeIds.RUN_DUCK &&
            action == AccessibilitySemanticAction.DUCK -> duckAction()

        nodeId == AccessibilityNodeIds.GARDEN_RUN &&
            action == AccessibilitySemanticAction.ACTIVATE ->
            sessionEventAction(RunSessionEvent.GARDEN_RUN_REQUESTED)

        nodeId == AccessibilityNodeIds.GARDEN_HOME &&
            action == AccessibilitySemanticAction.ACTIVATE ->
            sessionEventAction(RunSessionEvent.GARDEN_BACK_REQUESTED)

        nodeId == AccessibilityNodeIds.REST_CONTINUE &&
            action == AccessibilitySemanticAction.ACTIVATE ->
            sessionEventAction(RunSessionEvent.REST_TAPPED)

        nodeId in AccessibilityNodeIds.GARDEN_FIRST_PLANT until
            AccessibilityNodeIds.GARDEN_FIRST_PLANT + GardenEconomy.catalogueSize &&
            action == AccessibilitySemanticAction.ACTIVATE -> {
            val purchased = purchasePlantAction(
                nodeId - AccessibilityNodeIds.GARDEN_FIRST_PLANT
            )
            GardenPurchaseFeedbackPolicy.emitIfPurchased(
                purchased = purchased,
                gardenGrowthFeedbackAction = gardenGrowthFeedbackAction
            )
            purchased
        }

        nodeId in AccessibilityNodeIds.GARDEN_FIRST_COSTUME until
            AccessibilityNodeIds.GARDEN_FIRST_COSTUME + CostumeStyle.entries.size &&
            action == AccessibilitySemanticAction.ACTIVATE -> {
            val style = CostumeStyle.entries[
                nodeId - AccessibilityNodeIds.GARDEN_FIRST_COSTUME
            ]
            equipCostumeAction(style)
        }

        else -> false
    }
}
