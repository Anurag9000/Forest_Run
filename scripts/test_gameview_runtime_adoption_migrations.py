from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
)


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class GameViewRuntimeAdoptionMigrationTest(unittest.TestCase):
    def test_collision_and_session_migrations_compose_on_real_source(self) -> None:
        collision = load_module(
            "collision_migration",
            ROOT / "scripts/apply_gameview_collision_adapter.py",
        )
        session = load_module(
            "session_migration",
            ROOT / "scripts/apply_gameview_run_session_coordinator.py",
        )
        original = GAME_VIEW.read_text(encoding="utf-8")

        if "private val liveCollisionEffects = LiveCollisionEffects(" in original:
            collision.verify_adopted(original)
            collision_adopted = original
        else:
            collision_adopted = collision.apply_migration(original)
            collision.verify_adopted(collision_adopted)

        if "private val runSessionTransitions =" in collision_adopted:
            session.verify_adopted(collision_adopted)
            fully_adopted = collision_adopted
        else:
            fully_adopted = session.apply_migration(collision_adopted)
            session.verify_adopted(fully_adopted)

        self.assertIn("private val collisionOutcomeDispatcher", fully_adopted)
        self.assertIn("collisionOutcomeDispatcher.dispatch(", fully_adopted)
        self.assertIn("private fun prepareFreshRun()", fully_adopted)
        self.assertIn("private fun prepareEncounterScenario()", fully_adopted)
        self.assertIn("fun applyDebugLaunchIntent(intent: Intent?)", fully_adopted)
        self.assertEqual(1, fully_adopted.count("class GameView(context: Context)"))
        self.assertEqual(1, fully_adopted.count("private fun updateBounded("))
        self.assertEqual(1, fully_adopted.count("override fun draw(canvas: Canvas)"))

    def test_migrations_are_deterministic_and_refuse_second_application(self) -> None:
        collision = load_module(
            "collision_migration_repeat",
            ROOT / "scripts/apply_gameview_collision_adapter.py",
        )
        session = load_module(
            "session_migration_repeat",
            ROOT / "scripts/apply_gameview_run_session_coordinator.py",
        )
        original = GAME_VIEW.read_text(encoding="utf-8")

        collision_adopted = (
            original
            if "private val liveCollisionEffects = LiveCollisionEffects(" in original
            else collision.apply_migration(original)
        )
        fully_adopted = (
            collision_adopted
            if "private val runSessionTransitions =" in collision_adopted
            else session.apply_migration(collision_adopted)
        )

        collision.verify_adopted(fully_adopted)
        session.verify_adopted(fully_adopted)
        with self.assertRaises(AssertionError):
            collision.apply_migration(fully_adopted)
        with self.assertRaises(AssertionError):
            session.apply_migration(fully_adopted)


if __name__ == "__main__":
    unittest.main()
