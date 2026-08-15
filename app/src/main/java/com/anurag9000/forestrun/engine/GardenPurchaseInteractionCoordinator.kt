package com.anurag9000.forestrun.engine

/**
 * Presentation-layer Garden purchase policy shared by touch and accessibility.
 *
 * Persistence remains the sole authority for whether a purchase succeeds. This
 * coordinator only translates a successful committed purchase into one semantic
 * growth-feedback cue; rejected or failed writes never masquerade as growth.
 */
internal class GardenPurchaseInteractionCoordinator(
    private val purchaseAction: (Int) -> GardenPurchaseResult,
    private val gardenGrowthFeedbackAction: () -> Unit
) {
    fun purchase(requestedIndex: Int): GardenPurchaseResult {
        val result = purchaseAction(requestedIndex)
        if (result.purchased) {
            gardenGrowthFeedbackAction()
        }
        return result
    }
}
