#!/usr/bin/env python3
"""Source ownership and ordering contract for immediate terminal HIT impact."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
GAME_VIEW = ENGINE / "GameView.kt"
DISPATCHER = ENGINE / "CollisionOutcomeDispatcher.kt"
IMPACT = ENGINE / "TerminalHitImpactCoordinator.kt"
SESSION_PLANNER = ENGINE / "RunSessionTransitionPlanner.kt"
SESSION_EFFECTS = ENGINE / "LiveRunSessionEffects.kt"


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
        cls.session_planner = SESSION_PLANNER.read_text(encoding="utf-8")
        cls.session_effects = SESSION_EFFECTS.read_text(encoding="utf-8")
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
        effects_start = cls.game_view.index(
            "private val liveCollisionEffects = LiveCollisionEffects("
        )
        effects_end = cls.game_view.index(
            "private val terminalHitImpact = TerminalHitImpactCoordinator(",
            effects_start,
        )
        cls.live_effects = cls.game_view[effects_start:effects_end]
        session_start = cls.game_view.index(
            "private val runSessionTransitions = RunSessionTransitionCoordinator("
        )
        session_end = cls.game_view.index(
            "@Volatile\n    internal var runMode", session_start
        )
        cls.live_session = cls.game_view[session_start:session_end]
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
            "effects.terminalImpactHaptic()",
            "return captureAfterImpact()",
        )
        positions = [apply.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            "const val GHOST_SUPPRESSION_SECONDS = 1.35f",
            self.impact,
        )
        self.assertIn("fun terminalImpactHaptic()", self.impact)
        self.assertNotIn("fun longPulse()", self.impact)

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

    def test_completion_then_typed_session_event_preserves_death_order(self) -> None:
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
            "RunSessionEvent.TERMINAL_COLLISION_COMPLETED",
        )
        positions = [
            self.live_terminal_transition.index(item) for item in order
        ]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("applyRunSessionEvent(", self.live_terminal_transition)
        self.assertNotIn("runState = RunState.DYING", self.live_terminal_transition)
        self.assertIn(
            "gameState.buildRunSummary(lastKiller = killerType)",
            self.live_inputs,
        )
        self.assertIn(
            "event == RunSessionEvent.TERMINAL_COLLISION_COMPLETED",
            self.session_planner,
        )
        self.assertIn("runState = RunState.DYING", self.session_planner)
        self.assertIn("effects = listOf(RunSessionEffect.TRIGGER_DEATH)", self.session_planner)
        self.assertIn(
            "RunSessionEffect.TRIGGER_DEATH -> triggerDeathAction()",
            self.session_effects,
        )
        self.assertIn("triggerDeathAction = {", self.live_session)
        self.assertEqual(
            1,
            self.live_session.count("runResetManager.triggerDeath(gameState)"),
        )
        self.assertLess(
            self.live_session.index("check(::gameState.isInitialized)"),
            self.live_session.index("runResetManager.triggerDeath(gameState)"),
        )

    def test_shared_adapter_maps_terminal_effects_to_original_owners(self) -> None:
        required = (
            "recordRunHitAction = { gameState.recordHit() }",
            "suppressGhostAction = { seconds -> ghostPlayer.suppress(seconds) }",
            "triggerPlayerRestAction = { player.triggerRest() }",
            "shakeHitAction = { CameraSystem.shakeHit() }",
            "playHitAction = { SfxManager.playHit() }",
            "playRestAction = { LeitmotifManager.playRest() }",
            "longPulseAction = { HapticManager.longPulse() }",
        )
        for item in required:
            self.assertEqual(1, self.live_effects.count(item), item)
        self.assertEqual(2, self.game_view.count("effects = liveCollisionEffects"))
        self.assertNotIn("GameViewTerminalHitImpactEffects", self.game_view)

    def test_capture_rejects_killer_identity_drift(self) -> None:
        capture = extract_braced_block(
            self.impact,
            "internal data class TerminalHitImpactCapture(",
        )
        self.assertIn("require(presentation.killerType == killerType)", capture)


if __name__ == "__main__":
    unittest.main()
