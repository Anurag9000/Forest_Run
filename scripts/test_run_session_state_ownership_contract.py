#!/usr/bin/env python3
"""Permanent source contract for top-level app/run state publication ownership."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
GAME_VIEW = (ENGINE / "GameView.kt").read_text(encoding="utf-8")
PLANNER = (ENGINE / "RunSessionTransitionPlanner.kt").read_text(encoding="utf-8")


class RunSessionStateOwnershipContractTest(unittest.TestCase):
    def test_game_view_has_one_authoritative_field_publication_pair(self) -> None:
        self.assertEqual(
            1,
            GAME_VIEW.count("appState = result.transition.after.appState"),
        )
        self.assertEqual(
            1,
            GAME_VIEW.count("runState = result.transition.after.runState"),
        )
        self.assertNotIn("appState = AppGameState.", GAME_VIEW)
        self.assertNotIn("runState = RunState.", GAME_VIEW)

    def test_debug_launches_route_through_explicit_session_event(self) -> None:
        self.assertEqual(
            2,
            GAME_VIEW.count("RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED"),
        )
        self.assertIn(
            "event == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED",
            PLANNER,
        )
        self.assertIn("appState = AppGameState.PLAYING", PLANNER)
        self.assertIn("runState = RunState.PLAYING", PLANNER)

    def test_debug_state_event_has_no_live_effects(self) -> None:
        start = PLANNER.index("event == RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED")
        end = PLANNER.index(
            "current == RunSessionSnapshot(AppGameState.MENU, RunState.PLAYING)",
            start,
        )
        block = PLANNER[start:end]
        self.assertNotIn("effects =", block)
        self.assertIn("appState = AppGameState.PLAYING", block)
        self.assertIn("runState = RunState.PLAYING", block)

    def test_temporary_debug_state_migration_is_absent(self) -> None:
        self.assertFalse((ROOT / "scripts/migrate_debug_session_state_ownership.py").exists())
        self.assertFalse((ROOT / ".github/workflows/debug-session-state-migration.yml").exists())


if __name__ == "__main__":
    unittest.main()
