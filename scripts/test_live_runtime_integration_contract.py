from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
).read_text(encoding="utf-8")
DISPATCHER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt"
).read_text(encoding="utf-8")


class LiveRuntimeIntegrationContractTest(unittest.TestCase):
    def test_game_view_routes_all_collision_outcomes_through_dispatcher(self) -> None:
        self.assertIn(
            "private val collisionOutcomeDispatcher = CollisionOutcomeDispatcher(",
            GAME_VIEW,
        )
        self.assertEqual(1, GAME_VIEW.count("collisionOutcomeDispatcher.dispatch("))
        self.assertNotIn("when (collision.result)", GAME_VIEW)
        self.assertIn("captureTerminalImpact = {", GAME_VIEW)
        self.assertIn("buildTerminalSummaryPreview = { killerType ->", GAME_VIEW)
        self.assertIn("buildStumbleInput = {", GAME_VIEW)
        self.assertIn("deactivateStumbleEntity = {", GAME_VIEW)
        self.assertIn("buildMercyMissInput = {", GAME_VIEW)
        self.assertIn(
            "dispatchResult is CollisionOutcomeDispatchResult.Terminal",
            GAME_VIEW,
        )

    def test_dispatcher_keeps_branch_inputs_lazy_and_exhaustive(self) -> None:
        self.assertIn("captureTerminalImpact: () ->", DISPATCHER)
        self.assertIn("buildStumbleInput: () ->", DISPATCHER)
        self.assertIn("buildMercyMissInput: () ->", DISPATCHER)
        for outcome in ("HIT", "STUMBLE", "MERCY_MISS", "NONE"):
            self.assertIn(f"CollisionResult.{outcome}", DISPATCHER)
        self.assertIn(
            "val impact = terminalHitImpact.apply(captureTerminalImpact)",
            DISPATCHER,
        )
        self.assertIn(
            "deactivateEntity = deactivateStumbleEntity",
            DISPATCHER,
        )


if __name__ == "__main__":
    unittest.main()
