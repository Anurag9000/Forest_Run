from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COORDINATOR = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GardenPurchaseInteractionCoordinator.kt"
MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GardenPurchaseManager.kt"
FACADE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/ApplicationPersistenceFacade.kt"
TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/engine/GardenPurchaseInteractionCoordinatorTest.kt"


class GardenPurchaseFeedbackContractTest(unittest.TestCase):
    def test_feedback_policy_is_owned_above_persistence(self) -> None:
        coordinator = COORDINATOR.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        facade = FACADE.read_text(encoding="utf-8")

        self.assertIn("class GardenPurchaseInteractionCoordinator", coordinator)
        self.assertIn("val result = purchaseAction(requestedIndex)", coordinator)
        self.assertIn("if (result.purchased)", coordinator)
        self.assertIn("gardenGrowthFeedbackAction()", coordinator)

        for persistence_source in (manager, facade):
            self.assertNotIn("HapticManager", persistence_source)
            self.assertNotIn("gardenGrowth()", persistence_source)
            self.assertNotIn("GardenPurchaseInteractionCoordinator", persistence_source)

    def test_every_non_purchase_status_is_regression_covered(self) -> None:
        test = TEST.read_text(encoding="utf-8")

        self.assertIn("GardenPurchaseStatus.entries.filterNot", test)
        self.assertIn("GardenPurchaseStatus.PURCHASED", test)
        self.assertIn("assertEquals(1, growthCues)", test)
        self.assertIn('assertEquals("status=$status", 0, growthCues)', test)
        self.assertIn('assertEquals("status=$status", 1, purchaseCalls)', test)


if __name__ == "__main__":
    unittest.main()
