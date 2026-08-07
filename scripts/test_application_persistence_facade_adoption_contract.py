#!/usr/bin/env python3
"""Permanent source contract for the application persistence facade."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
UI = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui"


class ApplicationPersistenceFacadeAdoptionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.facade = (ENGINE / "ApplicationPersistenceFacade.kt").read_text(encoding="utf-8")
        cls.game_view = (ENGINE / "GameView.kt").read_text(encoding="utf-8")
        cls.entity_manager = (ENGINE / "EntityManager.kt").read_text(encoding="utf-8")
        cls.garden = (UI / "GardenScreen.kt").read_text(encoding="utf-8")
        cls.menu = (UI / "MainMenuScreen.kt").read_text(encoding="utf-8")
        cls.feedback = (UI / "FeedbackSettingsPanel.kt").read_text(encoding="utf-8")

    def test_facade_exposes_independent_live_write_ports_without_fake_global_transaction(self) -> None:
        required = (
            "ApplicationRunOutcomePort",
            "ApplicationEncounterPersistence",
            "TerminalHitRelationshipRecorder",
            "NonTerminalCollisionRelationshipRecorder",
            "purchaseNextGardenPlant",
            "saveFeedbackPreferences",
            "equipCostume",
            "inspectRecoveryEvidence",
            "recoverSafely",
            "recoverable durability domain",
            "without pretending that",
            "form one",
            "ACID transaction",
        )
        for token in required:
            self.assertIn(token, self.facade, token)
        self.assertIn("RunOutcomePersistenceCoordinator(", self.facade)
        self.assertIn("AndroidApplicationEncounterPersistence(appContext)", self.facade)

    def test_game_view_shares_one_facade_across_run_collision_ui_and_accessibility_writes(self) -> None:
        required = (
            "private val applicationPersistence = ApplicationPersistenceFacade.android(context)",
            "private val runOutcomePersistence: ApplicationRunOutcomePort = applicationPersistence",
            "relationshipRecorder = applicationPersistence",
            "encounterPersistence = applicationPersistence",
            "applicationPersistence.saveFeedbackPreferences(",
            "applicationPersistence.purchaseNextGardenPlant(index)",
            "applicationPersistence.equipCostume(style)",
            "applicationPersistence",
        )
        for token in required:
            self.assertIn(token, self.game_view, token)
        self.assertEqual(2, self.game_view.count("relationshipRecorder = applicationPersistence"))

    def test_entity_manager_has_no_direct_resolved_encounter_memory_write(self) -> None:
        self.assertIn("encounterPersistence.recordPass(type)", self.entity_manager)
        self.assertIn("encounterPersistence.recordEncounter(type)", self.entity_manager)
        self.assertNotIn("PersistentMemoryManager.recordPass(context, type)", self.entity_manager)
        self.assertNotIn("PersistentMemoryManager.recordEncounter(context, type)", self.entity_manager)

    def test_touch_ui_writes_use_facade(self) -> None:
        self.assertIn("persistenceFacade.purchaseNextGardenPlant(i)", self.garden)
        self.assertIn("persistenceFacade.equipCostume(style)", self.garden)
        self.assertNotIn("GardenPurchaseManager.purchaseNext(", self.garden)
        self.assertNotIn("CostumeManager.equip(context, style)", self.garden)
        self.assertIn("persistenceFacade.saveFeedbackPreferences(", self.feedback)
        self.assertNotIn("FeedbackSettings.setReducedMotion(", self.feedback)
        self.assertNotIn("FeedbackSettings.setAudioEnabled(", self.feedback)
        self.assertNotIn("FeedbackSettings.setHapticsEnabled(", self.feedback)
        self.assertIn("FeedbackSettingsPanel(", self.menu)
        self.assertIn("persistenceFacade", self.menu)

    def test_one_shot_migration_artifacts_are_absent(self) -> None:
        self.assertFalse((ROOT / "scripts/migrate_application_persistence_facade_adoption.py").exists())
        self.assertFalse((ROOT / ".github/workflows/application-persistence-facade-migration.yml").exists())


if __name__ == "__main__":
    unittest.main()
