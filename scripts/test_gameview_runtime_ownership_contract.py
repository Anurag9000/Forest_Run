from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW_PATH = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
)
PLANNER_PATH = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunSessionTransitionPlanner.kt"
)
TEMPORARY_MIGRATION_PATHS = (
    ROOT / "scripts/apply_gameview_collision_adapter.py",
    ROOT / "scripts/apply_gameview_run_session_coordinator.py",
    ROOT / "scripts/test_gameview_runtime_adoption_migrations.py",
    ROOT / ".github/workflows/apply-gameview-collision-adapter.yml",
    ROOT / ".github/workflows/apply-gameview-runtime-ownership.yml",
    ROOT / "scripts/migrate_debug_session_state_ownership.py",
    ROOT / ".github/workflows/debug-session-state-migration.yml",
)


class GameViewRuntimeOwnershipContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = GAME_VIEW_PATH.read_text(encoding="utf-8")
        cls.planner = PLANNER_PATH.read_text(encoding="utf-8")

    def test_collision_effect_ownership_is_shared_and_private_adapters_are_gone(self) -> None:
        self.assertEqual(
            1,
            self.source.count("private val liveCollisionEffects = LiveCollisionEffects("),
        )
        self.assertEqual(2, self.source.count("effects = liveCollisionEffects"))
        self.assertNotIn("GameViewTerminalHitImpactEffects", self.source)
        self.assertNotIn("GameViewNonTerminalCollisionEffects", self.source)
        self.assertEqual(1, self.source.count("collisionOutcomeDispatcher.dispatch("))

    def test_session_routes_have_one_state_publication_boundary(self) -> None:
        self.assertEqual(1, self.source.count("private val runSessionTransitions ="))
        self.assertEqual(1, self.source.count("private fun applyRunSessionEvent("))
        self.assertEqual(1, self.source.count("appState = result.transition.after.appState"))
        self.assertEqual(1, self.source.count("runState = result.transition.after.runState"))
        self.assertIn("if (!result.mayAdoptAfterState) return false", self.source)

        for event in (
            "MENU_RUN_REQUESTED",
            "MENU_GARDEN_REQUESTED",
            "GARDEN_RUN_REQUESTED",
            "GARDEN_BACK_REQUESTED",
            "TERMINAL_COLLISION_COMPLETED",
            "DYING_DURATION_COMPLETED",
            "REST_TAPPED",
            "RESTART_FADE_COMPLETED",
            "DEBUG_PLAYING_STATE_REQUESTED",
        ):
            self.assertIn(f"RunSessionEvent.{event}", self.source)
            self.assertIn(event, self.planner)

    def test_old_direct_transition_fragments_cannot_return(self) -> None:
        for forbidden in (
            "runState = runResetManager.beginRestart()",
            "if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER",
            "if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)",
            "mainMenuScreen.resetRitual()\n                    mainMenuScreen.refreshCopy()",
            "prepareFreshRun()\n                    appState = AppGameState.PLAYING",
            "appState = AppGameState.PLAYING",
            "runState = RunState.PLAYING",
        ):
            self.assertNotIn(forbidden, self.source)

    def test_debug_launches_use_the_explicit_state_agnostic_session_event(self) -> None:
        self.assertIn("fun applyDebugLaunchIntent(intent: Intent?)", self.source)
        self.assertEqual(
            2,
            self.source.count("RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED"),
        )
        self.assertIn(
            "event == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED",
            self.planner,
        )

    def test_temporary_write_capability_and_migration_scripts_are_gone(self) -> None:
        for path in TEMPORARY_MIGRATION_PATHS:
            self.assertFalse(path.exists(), str(path))


if __name__ == "__main__":
    unittest.main()
