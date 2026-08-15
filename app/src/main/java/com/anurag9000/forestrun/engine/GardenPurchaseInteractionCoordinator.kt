package com.anurag9000.forestrun.engine

/** Shared success-to-feedback rule for every Garden purchase presentation path. */
internal object GardenPurchaseFeedbackPolicy {
    fun emitIfPurchased(
        purchased: Boolean,
        gardenGrowthFeedbackAction: () -> Unit
    ) {
        if (purchased) {
            gardenGrowthFeedbackAction()
        }
    }
}

/**
 * Presentation-layer Garden purchase coordinator for result-bearing callers.
 *
 * Persistence remains the sole authority for whether a purchase succeeds. This
 * coordinator translates only a successful committed purchase into one semantic
 * growth-feedback cue; rejected or failed writes never masquerade as growth.
 */
internal class GardenPurchaseInteractionCoordinator(
    private val purchaseAction: (Int) -> GardenPurchaseResult,
    private val gardenGrowthFeedbackAction: () -> Unit
) {
    fun purchase(requestedIndex: Int): GardenPurchaseResult {
        val result = purchaseAction(requestedIndex)
        GardenPurchaseFeedbackPolicy.emitIfPurchased(
            purchased = result.purchased,
            gardenGrowthFeedbackAction = gardenGrowthFeedbackAction
        )
        return result
    }
}
