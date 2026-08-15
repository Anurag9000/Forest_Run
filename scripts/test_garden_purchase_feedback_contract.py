from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COORDINATOR = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GardenPurchaseInteractionCoordinator.kt"
MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GardenPurchaseManager.kt"
FACADE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/ApplicationPersistenceFacade.kt"
GARDEN = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt"
ACCESSIBILITY = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/LiveGameAccessibilityActions.kt"
COORDINATOR_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/engine/GardenPurchaseInteractionCoordinatorTest.kt"
ACCESSIBILITY_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/engine/LiveGameAccessibilityGardenFeedbackTest.kt"


class GardenPurchaseFeedbackContractTest(unittest.TestCase):
    def test_shared_success_policy_is_owned_above_persistence(self) -> None:
        coordinator = COORDINATOR.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        facade = FACADE.read_text(encoding="utf-8")

        self.assertIn("object GardenPurchaseFeedbackPolicy", coordinator)
        self.assertIn("if (purchased)", coordinator)
        self.assertIn("gardenGrowthFeedbackAction()", coordinator)
        self.assertIn("val result = purchaseAction(requestedIndex)", coordinator)
        self.assertIn("purchased = result.purchased", coordinator)

        for persistence_source in (manager, facade):
            self.assertNotIn("HapticManager", persistence_source)
            self.assertNotIn("gardenGrowth()", persistence_source)
            self.assertNotIn("GardenPurchaseInteractionCoordinator", persistence_source)
            self.assertNotIn("GardenPurchaseFeedbackPolicy", persistence_source)

    def test_touch_and_accessibility_both_apply_live_growth_feedback(self) -> None:
        garden = GARDEN.read_text(encoding="utf-8")
        accessibility = ACCESSIBILITY.read_text(encoding="utf-8")

        self.assertIn("GardenPurchaseInteractionCoordinator(", garden)
        self.assertIn("persistenceFacade::purchaseNextGardenPlant", garden)
        self.assertIn("HapticManager::gardenGrowth", garden)
        self.assertIn("purchaseInteraction.purchase(i)", garden)

        self.assertIn("gardenGrowthFeedbackAction: () -> Unit = HapticManager::gardenGrowth", accessibility)
        self.assertIn("val purchased = purchasePlantAction(", accessibility)
        self.assertIn("GardenPurchaseFeedbackPolicy.emitIfPurchased(", accessibility)
        self.assertIn("purchased = purchased", accessibility)
        self.assertIn("gardenGrowthFeedbackAction = gardenGrowthFeedbackAction", accessibility)

    def test_every_result_status_and_accessibility_success_branch_are_regression_covered(self) -> None:
        coordinator_test = COORDINATOR_TEST.read_text(encoding="utf-8")
        accessibility_test = ACCESSIBILITY_TEST.read_text(encoding="utf-8")

        self.assertIn("GardenPurchaseStatus.entries.filterNot", coordinator_test)
        self.assertIn("GardenPurchaseStatus.PURCHASED", coordinator_test)
        self.assertIn("assertEquals(1, growthCues)", coordinator_test)
        self.assertIn('assertEquals("status=$status", 0, growthCues)', coordinator_test)
        self.assertIn('assertEquals("status=$status", 1, purchaseCalls)', coordinator_test)

        self.assertIn("successfulVirtualPlantPurchaseEmitsExactlyOneGrowthCue", accessibility_test)
        self.assertIn("rejectedVirtualPlantPurchaseEmitsNoGrowthCue", accessibility_test)
        self.assertIn("wrongSemanticActionDoesNotAttemptPurchaseOrFeedback", accessibility_test)


if __name__ == "__main__":
    unittest.main()
