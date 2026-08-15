from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARCHITECTURE = ROOT / "docs/ARCHITECTURE.md"
GAME_DESIGN = ROOT / "docs/GAME_DESIGN.md"
README = ROOT / "README.md"


class CurrentArchitectureContractTest(unittest.TestCase):
    def test_orientation_is_a_source_decision_not_an_unresolved_product_choice(self) -> None:
        documents = [
            ARCHITECTURE.read_text(encoding="utf-8"),
            GAME_DESIGN.read_text(encoding="utf-8"),
            README.read_text(encoding="utf-8"),
        ]
        for text in documents:
            self.assertNotIn("fixed landscape, pending final product/device acceptance", text)
        self.assertIn("Orientation is **fixed landscape by product/source design**", documents[0])
        self.assertIn("Current orientation:** fixed landscape by product/source design", documents[1])
        self.assertIn("orientation: fixed landscape", documents[2])

    def test_architecture_names_current_long_horizon_owners(self) -> None:
        text = ARCHITECTURE.read_text(encoding="utf-8")
        for token in (
            "GardenEconomy",
            "GardenPurchaseManager",
            "ForestJournalActivity",
            "ForestCollectionProgressComposer",
            "ForestGardenHistoryComposer",
            "ForestPathHistoryComposer",
            "ForestRunLegacyComposer",
            "ForestCompletionCapstoneComposer",
            "ApplicationPersistenceFacade",
            "RelationshipArcSystem",
            "StoryFragmentSystem",
        ):
            self.assertIn(token, text)

    def test_architecture_uses_semantic_collision_haptic_language(self) -> None:
        text = ARCHITECTURE.read_text(encoding="utf-8")
        for token in (
            "terminal-impact haptic",
            "stumble-impact haptic",
            "mercy-acknowledgement haptic",
            "terminalImpactHaptic",
            "lightTick()",
            "gardenGrowth()",
            "bloomSurge()",
        ):
            self.assertIn(token, text)
        self.assertNotIn("→ long haptic", text)

    def test_architecture_retains_external_evidence_boundary(self) -> None:
        text = ARCHITECTURE.read_text(encoding="utf-8")
        for token in (
            "docs/DEVICE_ACCEPTANCE.md",
            "docs/INSTALLED_CANDIDATE_IDENTITY.md",
            "docs/HUMAN_ACCEPTANCE.md",
            "docs/RELEASE_GOVERNANCE_EVIDENCE.md",
            "docs/RELEASE_EVIDENCE_INDEX.md",
            "docs/RELEASE_READINESS.md",
            "physical-device",
            "production signing",
            "Play Console",
        ):
            self.assertIn(token, text)


if __name__ == "__main__":
    unittest.main()
