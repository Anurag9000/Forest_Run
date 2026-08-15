from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JOURNAL = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/ForestJournal.kt"
COLLECTION = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/ForestCollectionProgress.kt"
COSTUMES = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/CostumeManager.kt"
STORY = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/StoryFragmentSystem.kt"
DOC = ROOT / "docs/FOREST_JOURNAL.md"


class ForestJournalReadOnlyContractTest(unittest.TestCase):
    def test_journal_never_calls_relationship_refresh_paths(self) -> None:
        journal = JOURNAL.read_text(encoding="utf-8")
        collection = COLLECTION.read_text(encoding="utf-8")
        combined = journal + "\n" + collection

        for forbidden in (
            "PersistentMemoryManager.getRelationshipStage(",
            "RelationshipArcSystem.stageFor(",
            "RelationshipArcSystem.refreshStage(",
            "RelationshipArcSystem.relationshipsAtOrAbove(",
            "RelationshipArcSystem.isStrainedBond(",
        ):
            self.assertNotIn(forbidden, combined)

        self.assertIn("SaveManager.loadRelationshipStage", journal)
        self.assertIn("RelationshipStage.FIRST_IMPRESSION", journal)
        self.assertIn("relationshipTone", collection)

    def test_journal_uses_only_read_side_costume_and_story_apis(self) -> None:
        collection = COLLECTION.read_text(encoding="utf-8")
        costumes = COSTUMES.read_text(encoding="utf-8")
        story = STORY.read_text(encoding="utf-8")

        self.assertIn("CostumeManager.availableCostumes", collection)
        self.assertIn("CostumeManager.activeCostume", collection)
        self.assertNotIn("CostumeManager.refreshUnlocks", collection)
        self.assertNotIn("CostumeManager.equip", collection)
        self.assertIn("fun availableCostumes", costumes)

        self.assertIn("StoryFragmentSystem.unlockedMemoryPages", collection)
        for forbidden in (
            "StoryFragmentSystem.restQuote(",
            "StoryFragmentSystem.gardenReflection(",
            "StoryFragmentSystem.creatureThought(",
            "StoryFragmentSystem.weatherThought(",
        ):
            self.assertNotIn(forbidden, collection)
        self.assertIn("fun unlockedMemoryPages", story)

    def test_contract_document_states_the_observational_rule(self) -> None:
        text = DOC.read_text(encoding="utf-8")
        self.assertIn("observational projection", text)
        self.assertIn("award or spend Seeds", text)
        self.assertIn("mutate or materialize relationship stage", text)
        self.assertIn("create story pages", text)
        self.assertIn("refresh or fabricate costume unlocks", text)
        self.assertIn("must never call that path directly or indirectly", text)


if __name__ == "__main__":
    unittest.main()
