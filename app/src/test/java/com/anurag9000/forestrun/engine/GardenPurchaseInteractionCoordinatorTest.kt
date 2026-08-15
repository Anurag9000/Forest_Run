package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GardenPurchaseInteractionCoordinatorTest {
    @Test
    fun purchasedResultEmitsExactlyOneGrowthCueAndReturnsAuthoritativeResult() {
        val authoritative = GardenPurchaseResult(
            status = GardenPurchaseStatus.PURCHASED,
            unlockedCount = 4,
            remainingSeeds = 17
        )
        var purchaseCalls = 0
        var growthCues = 0
        val coordinator = GardenPurchaseInteractionCoordinator(
            purchaseAction = { requestedIndex ->
                assertEquals(3, requestedIndex)
                purchaseCalls++
                authoritative
            },
            gardenGrowthFeedbackAction = { growthCues++ }
        )

        val result = coordinator.purchase(3)

        assertSame(authoritative, result)
        assertEquals(1, purchaseCalls)
        assertEquals(1, growthCues)
    }

    @Test
    fun everyRejectedOrFailedResultSuppressesGrowthFeedback() {
        val nonPurchaseStatuses = GardenPurchaseStatus.entries.filterNot {
            it == GardenPurchaseStatus.PURCHASED
        }

        nonPurchaseStatuses.forEach { status ->
            val authoritative = GardenPurchaseResult(
                status = status,
                unlockedCount = 2,
                remainingSeeds = 11
            )
            var purchaseCalls = 0
            var growthCues = 0
            val coordinator = GardenPurchaseInteractionCoordinator(
                purchaseAction = {
                    purchaseCalls++
                    authoritative
                },
                gardenGrowthFeedbackAction = { growthCues++ }
            )

            val result = coordinator.purchase(2)

            assertSame("status=$status", authoritative, result)
            assertEquals("status=$status", 1, purchaseCalls)
            assertEquals("status=$status", 0, growthCues)
        }
    }
}
