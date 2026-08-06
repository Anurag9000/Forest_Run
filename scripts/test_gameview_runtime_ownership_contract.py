from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW_PATH = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
)
COLLISION_MIGRATION = ROOT / "scripts/apply_gameview_collision_adapter.py"
SESSION_MIGRATION = ROOT / "scripts/apply_gameview_run_session_coordinator.py"
WRITE_WORKFLOW = ROOT / ".github/workflows/apply-gameview-collision-adapter.yml"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def adopted_source() -> str:
    source = GAME_VIEW_PATH.read_text(encoding="utf-8")
    if COLLISION_MIGRATION.exists():
        collision = load_module("runtime_collision_contract", COLLISION_MIGRATION)
        if "private val liveCollisionEffects = LiveCollisionEffects(" not in source:
            source = collision.apply_migration(source)
        collision.verify_adopted(source)
    if SESSION_MIGRATION.exists():
        session = load_module("runtime_session_contract", SESSION_MIGRATION)
        if "private val runSessionTransitions =" not in source:
            source = session.apply_migration(source)
        session.verify_adopted(source)
    return source


class GameViewRuntimeOwnershipContractTest(unittest.TestCase):
    def test_collision_effect_ownership_is_shared_and_private_adapters_are_gone(self) -> None:
        source = adopted_source()
        self.assertEqual(
            1,
            source.count("private val liveCollisionEffects = LiveCollisionEffects("),
        )
        self.assertEqual(2, source.count("effects = liveCollisionEffects"))
        self.assertNotIn("GameViewTerminalHitImpactEffects", source)
        self.assertNotIn("GameViewNonTerminalCollisionEffects", source)
        self.assertEqual(1, source.count("collisionOutcomeDispatcher.dispatch("))

    def test_ordinary_session_routes_have_one_state_publication_boundary(self) -> None:
        source = adopted_source()
        self.assertEqual(1, source.count("private val runSessionTransitions ="))
        self.assertEqual(1, source.count("private fun applyRunSessionEvent("))
        self.assertEqual(1, source.count("appState = result.transition.after.appState"))
        self.assertEqual(1, source.count("runState = result.transition.after.runState"))
        for event in (
            "MENU_RUN_REQUESTED",
            "MENU_GARDEN_REQUESTED",
            "GARDEN_RUN_REQUESTED",
            "GARDEN_BACK_REQUESTED",
            "TERMINAL_COLLISION_COMPLETED",
            "DYING_DURATION_COMPLETED",
            "REST_TAPPED",
            "RESTART_FADE_COMPLETED",
        ):
            self.assertIn(f"RunSessionEvent.{event}", source)

    def test_old_direct_ordinary_transition_fragments_cannot_return(self) -> None:
        source = adopted_source()
        for forbidden in (
            "runState = runResetManager.beginRestart()",
            "if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER",
            "if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)",
            "mainMenuScreen.resetRitual()\n                    mainMenuScreen.refreshCopy()",
        ):
            self.assertNotIn(forbidden, source)

    def test_migration_capability_is_temporary_and_self_removing(self) -> None:
        if not WRITE_WORKFLOW.exists():
            self.assertFalse(COLLISION_MIGRATION.exists())
            self.assertFalse(SESSION_MIGRATION.exists())
            return

        workflow = WRITE_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("rm scripts/apply_gameview_collision_adapter.py", workflow)
        self.assertIn("rm scripts/apply_gameview_run_session_coordinator.py", workflow)
        self.assertIn("rm .github/workflows/apply-gameview-collision-adapter.yml", workflow)
        self.assertIn("git pull --rebase origin main", workflow)
        self.assertNotIn("--force", workflow)
        self.assertNotIn("force-with-lease", workflow)


if __name__ == "__main__":
    unittest.main()
