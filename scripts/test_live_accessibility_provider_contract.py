#!/usr/bin/env python3
"""Permanent source contract for live Canvas accessibility ownership."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
APP = ROOT / "app/src/main/java/com/anurag9000/forestrun"
TESTS = ROOT / "app/src/test/java/com/anurag9000/forestrun"
GAME_VIEW = ENGINE / "GameView.kt"
MAIN_ACTIVITY = APP / "MainActivity.kt"
MAIN_MENU = APP / "ui/MainMenuScreen.kt"
PROVIDER = ENGINE / "GameAccessibilityNodeProvider.kt"
SEMANTICS = ENGINE / "GameAccessibilitySemantics.kt"
ACTIONS = ENGINE / "LiveGameAccessibilityActions.kt"
GEOMETRY = ENGINE / "GameAccessibilityGeometry.kt"
ANNOUNCEMENTS = ENGINE / "AccessibilityAnnouncementPolicy.kt"


class LiveAccessibilityProviderContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
        cls.menu = MAIN_MENU.read_text(encoding="utf-8")
        cls.provider = PROVIDER.read_text(encoding="utf-8")
        cls.semantics = SEMANTICS.read_text(encoding="utf-8")
        cls.actions = ACTIONS.read_text(encoding="utf-8")
        cls.geometry = GEOMETRY.read_text(encoding="utf-8")
        cls.announcements = ANNOUNCEMENTS.read_text(encoding="utf-8")

    def test_legacy_root_delegate_and_one_shot_migrations_are_absent(self) -> None:
        forbidden_paths = (
            APP / "ForestRunAccessibilityDelegate.kt",
            TESTS / "ForestRunAccessibilityDelegateTest.kt",
            ROOT / "scripts/migrate_live_accessibility_provider.py",
            ROOT / ".github/workflows/live-accessibility-migration.yml",
            ROOT / "scripts/migrate_live_accessibility_announcements.py",
            ROOT / ".github/workflows/live-accessibility-announcement-migration.yml",
        )
        for path in forbidden_paths:
            self.assertFalse(path.exists(), str(path))
        self.assertNotIn("attachForestRunAccessibility(", self.activity)

    def test_live_host_exposes_virtual_node_provider_directly(self) -> None:
        required = (
            "override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider",
            "GameAccessibilityNodeProvider(",
            "GameAccessibilityActionRouter(",
            "LiveGameAccessibilityActions(",
            "importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES",
            "isFocusable = true",
        )
        for token in required:
            self.assertIn(token, self.game_view, token)

    def test_accessibility_authority_never_synthesizes_touch_coordinates(self) -> None:
        combined = self.activity + self.game_view + self.menu + self.provider + self.actions
        for token in (
            "MotionEvent.obtain(",
            "dispatchTouchEvent(",
            "xFraction",
            "yFraction",
        ):
            self.assertNotIn(token, combined, token)

    def test_live_snapshot_uses_canonical_runtime_and_persistence_facts(self) -> None:
        required = (
            "FeedbackSettings.snapshot()",
            "SaveManager.loadGardenProgress(context)",
            "SaveManager.loadLifetimeSeeds(context)",
            "GardenEconomy.seedCostForIndex(gardenUnlocked)",
            "CostumeManager.availableCostumes(context)",
            "CostumeManager.activeCostume(context)",
            "summary?.seedsCollected",
            "restContinueEnabled = runState == RunState.GAME_OVER",
        )
        for token in required:
            self.assertIn(token, self.game_view, token)
        self.assertIn("AccessibilitySurface.SETTINGS", self.game_view)
        self.assertIn("AccessibilitySurface.GARDEN", self.game_view)
        self.assertIn("AccessibilitySurface.REST", self.game_view)

    def test_live_actions_route_to_real_owners_and_shared_persistence_facade(self) -> None:
        required = (
            "sessionEventAction = ::applyRunSessionEvent",
            "mainMenuScreen.performAccessibilityPrimaryAction()",
            "applicationPersistence.saveFeedbackPreferences(",
            "applicationPersistence.purchaseNextGardenPlant(index)",
            "applicationPersistence.equipCostume(style)",
            "inputHandler.onJumpPressed",
            "inputHandler.onJumpReleased",
            "inputHandler.onDuckPressed",
            "inputHandler.onDuckReleased",
        )
        for token in required:
            self.assertIn(token, self.game_view, token)
        for forbidden in (
            "GardenPurchaseManager.purchaseNext(context, index)",
            "CostumeManager.equip(context, style)",
            "FeedbackSettings.setReducedMotion(context",
            "FeedbackSettings.setAudioEnabled(context",
            "FeedbackSettings.setHapticsEnabled(context",
        ):
            self.assertNotIn(forbidden, self.game_view, forbidden)
        self.assertIn("RunSessionEvent.REST_TAPPED", self.actions)
        self.assertIn("RunSessionEvent.GARDEN_RUN_REQUESTED", self.actions)
        self.assertIn("RunSessionEvent.GARDEN_BACK_REQUESTED", self.actions)

    def test_menu_primary_action_preserves_willow_ritual_without_fake_tap(self) -> None:
        start = self.menu.index("fun performAccessibilityPrimaryAction(): Boolean")
        end = self.menu.index("fun update(deltaTime: Float)", start)
        block = self.menu[start:end]
        self.assertIn("Phase.IDLE", block)
        self.assertIn("phase = Phase.STANDING_UP", block)
        self.assertIn("Phase.STANDING_UP -> false", block)
        self.assertIn("Phase.READY", block)
        self.assertIn("startRunRequested = true", block)
        self.assertNotIn("onTap(", block)

    def test_semantics_publish_truthful_wardrobe_and_rest_states(self) -> None:
        self.assertIn("GARDEN_FIRST_COSTUME", self.semantics)
        self.assertIn("style.unlockLabel", self.semantics)
        self.assertIn("active -> \"Equipped\"", self.semantics)
        self.assertIn("unlocked -> \"Available\"", self.semantics)
        self.assertIn("if (snapshot.restContinueEnabled)", self.semantics)
        self.assertIn("\"Recovering before Garden\"", self.semantics)
        self.assertIn("it.wardrobeCards[index]", self.geometry)

    def test_state_mutation_owners_publish_semantic_tree_changes(self) -> None:
        self.assertIn("notifyAccessibilityTreeChanged()", self.game_view)
        self.assertIn("if (!manager.isEnabled) return", self.game_view)
        self.assertNotIn("notifySemanticTreeChanged()\n        return true", self.provider)

    def test_live_announcements_are_touch_exploration_only_sampled_and_coalesced(self) -> None:
        required_live = (
            "AccessibilityAnnouncementPolicy()",
            "ACCESSIBILITY_ANNOUNCEMENT_POLL_FRAMES = 30L",
            "manager.isTouchExplorationEnabled",
            "debugFrameCounter % ACCESSIBILITY_ANNOUNCEMENT_POLL_FRAMES != 0L",
            "SystemClock.uptimeMillis()",
            "announceAccessibilitySnapshot(buildAccessibilitySnapshot())",
            "?.let(::announceForAccessibility)",
        )
        for token in required_live:
            self.assertIn(token, self.game_view, token)
        for token in (
            "RUN_DISTANCE_BUCKET_METRES = 120",
            "RUN_MIN_INTERVAL_MS = 8_000L",
            "GARDEN_MIN_INTERVAL_MS = 2_500L",
        ):
            self.assertIn(token, self.announcements, token)
        self.assertNotIn("announceForAccessibility", self.provider)


if __name__ == "__main__":
    unittest.main()
