#!/usr/bin/env python3
"""Source ownership and ordering contract for immediate terminal HIT impact."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
DISPATCHER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt"
)
IMPACT = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/TerminalHitImpactCoordinator.kt"
)


def extract_braced_block(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    in_string = False
    escaped = False
    line_comment = False
    block_comment = False
    index = brace

    while index < len(source):
        character = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if line_comment:
            if character == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if character == "*" and following == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
            index += 1
            continue
        if character == "/" and following == "/":
            line_comment = True
            index += 2
            continue
        if character == "/" and following == "*":
            block_comment = True
            index += 2
            continue
        if character == '"':
            in_string = True
            index += 1
            continue
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1

    raise AssertionError(f"Unbalanced Kotlin block for {signature!r}")


class TerminalHitImpactContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.dispatcher = DISPATCHER.read_text(encoding="utf-8")
        cls.impact = IMPACT.read_text(encoding="utf-8")
        dispatch_start = cls.game_view.index(
            "val dispatchResult = collisionOutcomeDispatcher.dispatch("
        )
        transition_start = cls.game_view.index(
            "if (dispatchResult is CollisionOutcomeDispatchResult.Terminal)",
            dispatch_start,
        )
        cls.live_inputs = cls.game_view[dispatch_start:transition_start]
        cls.live_terminal_transition = cls.game_view[
            transition_start : cls.game_view.index(
                "// Tick down mercy flash", transition_start
            )
        ]
        cls.dispatch = extract_braced_block(
            cls.dispatcher,
            "fun dispatch(\n        result: CollisionResult,",
        )

    def test_coordinator_owns_exact_impact_order_before_capture(self) -> None:
        apply = extract_braced_block(self.impact, "fun apply(")
        order = (
            "effects.recordRunHit()",
            "effects.suppressGhost(GHOST_SUPPRESSION_SECONDS)",
            "effects.triggerPlayerRest()",
            "effects.shakeHit()",
            "effects.playHit()",
            "effects.playRest()",
            "effects.longPulse()",
            "return captureAfterImpact()",
        )
        positions = [apply.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            "const val GHOST_SUPPRESSION_SECONDS = 1.35f",
            self.impact,
        )

    def test_dispatcher_delegates_impact_once_without_direct_effect_calls(self) -> None:
        self.assertEqual(
            1,
            self.dispatch.count(
                "terminalHitImpact.apply(captureTerminalImpact)"
            ),
        )
        for forbidden in (
            "gameState.recordHit()",
            "ghostPlayer.suppress(",
            "player.triggerRest()",
            "CameraSystem.shakeHit()",
            "SfxManager.playHit()",
            "LeitmotifManager.playRest()",
            "HapticManager.longPulse()",
        ):
            self.assertNotIn(forbidden, self.dispatch)
            self.assertNotIn(forbidden, self.live_inputs)

    def test_post_impact_capture_preserves_original_snapshot_order(self) -> None:
        start = self.live_inputs.index("captureTerminalImpact = {")
        end = self.live_inputs.index("buildTerminalSummaryPreview", start)
        capture = self.live_inputs[start:end]
        order = (
            "ghostRecorder.detachSnapshot()",
            "entityManager.entityTypeOf(collision.entity)",
            "biome = entityManager.biomeManager.currentBiome",
            "routeTier = gameState.pacifistRouteTier",
            "playerX = player.x",
            "playerY = player.y",
            "completedGhost = completedGhost",
        )
        positions = [capture.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            "terminalHitImpact.apply(captureTerminalImpact)",
            self.dispatch,
        )

    def test_completion_and_death_transition_have_single_explicit_owners(self) -> None:
        impact = self.dispatch.index(
            "terminalHitImpact.apply(captureTerminalImpact)"
        )
        complete = self.dispatch.index("terminalHitOutcome.complete(")
        terminal_result = self.dispatch.index(
            "CollisionOutcomeDispatchResult.Terminal(completion)"
        )
        self.assertLess(impact, complete)
        self.assertLess(complete, terminal_result)

        order = (
            "val completedHit = dispatchResult.completion",
            "currentRestQuote = completedHit.summary.restQuote",
            "currentRunSummary = completedHit.summary",
            "runResetManager.triggerDeath(gameState)",
            "runState = RunState.DYING",
        )
        positions = [
            self.live_terminal_transition.index(item) for item in order
        ]
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            "gameState.buildRunSummary(lastKiller = killerType)",
            self.live_inputs,
        )

    def test_private_adapter_maps_each_effect_to_original_owner(self) -> None:
        adapter = extract_braced_block(
            self.game_view,
            "private inner class GameViewTerminalHitImpactEffects",
        )
        required = (
            "gameState.recordHit()",
            "ghostPlayer.suppress(seconds)",
            "player.triggerRest()",
            "CameraSystem.shakeHit()",
            "SfxManager.playHit()",
            "LeitmotifManager.playRest()",
            "HapticManager.longPulse()",
        )
        for item in required:
            self.assertEqual(1, adapter.count(item), item)

    def test_capture_rejects_killer_identity_drift(self) -> None:
        capture = extract_braced_block(
            self.impact,
            "internal data class TerminalHitImpactCapture(",
        )
        self.assertIn("require(presentation.killerType == killerType)", capture)


if __name__ == "__main__":
    unittest.main()
