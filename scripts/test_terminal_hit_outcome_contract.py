#!/usr/bin/env python3
"""Source contracts for the terminal HIT impact-to-completion boundary."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
GAME_VIEW = ENGINE / "GameView.kt"
DISPATCHER = ENGINE / "CollisionOutcomeDispatcher.kt"
TERMINAL = ENGINE / "TerminalHitOutcomeCoordinator.kt"
SESSION_PLANNER = ENGINE / "RunSessionTransitionPlanner.kt"


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
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""

        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and nxt == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue

        if char == "/" and nxt == "/":
            line_comment = True
            index += 2
            continue
        if char == "/" and nxt == "*":
            block_comment = True
            index += 2
            continue
        if char == '"':
            in_string = True
            index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1

    raise AssertionError(f"Unbalanced Kotlin block for {signature!r}")


class TerminalHitOutcomeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.dispatcher = DISPATCHER.read_text(encoding="utf-8")
        cls.terminal = TERMINAL.read_text(encoding="utf-8")
        cls.session_planner = SESSION_PLANNER.read_text(encoding="utf-8")
        dispatch_start = cls.game_view.index(
            "val dispatchResult = collisionOutcomeDispatcher.dispatch("
        )
        flash_start = cls.game_view.index("// Tick down mercy flash", dispatch_start)
        cls.live_collision = cls.game_view[dispatch_start:flash_start]
        cls.dispatch = extract_braced_block(
            cls.dispatcher,
            "fun dispatch(\n        result: CollisionResult,",
        )
        cls.complete = extract_braced_block(
            cls.terminal,
            "fun complete(\n        killerType: EntityType?,",
        )

    def test_dispatcher_delegates_impact_and_completion_once(self) -> None:
        self.assertEqual(
            1,
            self.dispatch.count(
                "terminalHitImpact.apply(captureTerminalImpact)"
            ),
        )
        self.assertEqual(1, self.dispatch.count("terminalHitOutcome.complete("))
        self.assertLess(
            self.dispatch.index("terminalHitImpact.apply(captureTerminalImpact)"),
            self.dispatch.index("terminalHitOutcome.complete("),
        )
        forbidden = (
            "PersistentMemoryManager.recordHit(",
            "RunFlavorPresentation.collisionCue(",
            "RestQuoteManager.quoteFor(",
            "runOutcomePersistence.commit(",
            "DialogueBubbleManager.spawn(",
            "FlavorTextManager.spawn(",
        )
        for call in forbidden:
            self.assertNotIn(call, self.dispatch)
            self.assertNotIn(call, self.live_collision)

    def test_post_impact_capture_precedes_completion_and_typed_death_handoff(self) -> None:
        capture_order = (
            "captureTerminalImpact = {",
            "ghostRecorder.detachSnapshot()",
            "entityManager.entityTypeOf(collision.entity)",
            "TerminalHitImpactCapture(",
            "buildTerminalSummaryPreview = { killerType ->",
        )
        positions = [self.live_collision.index(item) for item in capture_order]
        self.assertEqual(sorted(positions), positions)

        dispatch_order = (
            "terminalHitImpact.apply(captureTerminalImpact)",
            "terminalHitOutcome.complete(",
            "buildTerminalSummaryPreview(impact.killerType)",
            "CollisionOutcomeDispatchResult.Terminal(completion)",
        )
        positions = [self.dispatch.index(item) for item in dispatch_order]
        self.assertEqual(sorted(positions), positions)

        transition_order = (
            "val completedHit = dispatchResult.completion",
            "currentRestQuote = completedHit.summary.restQuote",
            "currentRunSummary = completedHit.summary",
            "applyRunSessionEvent(RunSessionEvent.TERMINAL_COLLISION_COMPLETED)",
        )
        positions = [
            self.live_collision.index(item) for item in transition_order
        ]
        self.assertEqual(sorted(positions), positions)
        self.assertNotIn("runResetManager.triggerDeath(gameState)", self.live_collision)
        self.assertNotIn("runState = RunState.DYING", self.live_collision)
        self.assertIn(
            "event == RunSessionEvent.TERMINAL_COLLISION_COMPLETED",
            self.session_planner,
        )
        self.assertIn("runState = RunState.DYING", self.session_planner)

    def test_game_view_passes_live_identity_and_accepts_completed_summary(self) -> None:
        required_once = (
            "persistEncounter = persistEncounter",
            "gameState.buildRunSummary(lastKiller = killerType)",
            "currentRestQuote = completedHit.summary.restQuote",
            "currentRunSummary = completedHit.summary",
            "biome = entityManager.biomeManager.currentBiome",
            "routeTier = gameState.pacifistRouteTier",
            "playerX = player.x",
            "playerY = player.y",
            "completedGhost = completedGhost",
        )
        for item in required_once:
            self.assertGreaterEqual(self.live_collision.count(item), 1, item)

        dispatcher_inputs = (
            "killerType = impact.killerType",
            "biome = impact.biome",
            "presentation = impact.presentation",
            "completedGhost = impact.completedGhost",
            "persistEncounter = persistEncounter",
        )
        for item in dispatcher_inputs:
            self.assertEqual(1, self.dispatch.count(item), item)

    def test_identity_invariant_precedes_every_completion_side_effect(self) -> None:
        invariant = self.complete.index("require(presentation.killerType == killerType)")
        first_side_effect = self.complete.index("relationshipRecorder.recordHit(killerType)")
        feedback = self.complete.index("feedbackPresenter.present(presentation)")
        self.assertLess(invariant, first_side_effect)
        self.assertLess(invariant, feedback)

    def test_terminal_coordinator_preserves_authored_completion_order(self) -> None:
        relationship = self.complete.index("relationshipRecorder.recordHit(killerType)")
        feedback = self.complete.index("feedbackPresenter.present(presentation)")
        summary = self.complete.index("val summaryPreview = buildSummaryPreview()")
        quote = self.complete.index("restQuoteResolver.resolve(")
        copy = self.complete.index("summaryPreview.copy(restQuote = restQuote)")
        persist = self.complete.index("outcomeCommitter.commit(")
        result = self.complete.index("return TerminalHitCompletionResult(")
        self.assertEqual(
            sorted((relationship, feedback, summary, quote, copy, persist, result)),
            [relationship, feedback, summary, quote, copy, persist, result],
        )

    def test_relationship_memory_is_gated_by_persistent_known_killer(self) -> None:
        gate = "if (persistEncounter && killerType != null)"
        self.assertEqual(1, self.complete.count(gate))
        gate_pos = self.complete.index(gate)
        record_pos = self.complete.index("relationshipRecorder.recordHit(killerType)")
        feedback_pos = self.complete.index("feedbackPresenter.present(presentation)")
        self.assertLess(gate_pos, record_pos)
        self.assertLess(record_pos, feedback_pos)

    def test_summary_builder_and_persistence_each_execute_once(self) -> None:
        self.assertEqual(1, self.complete.count("buildSummaryPreview()"))
        self.assertEqual(1, self.complete.count("outcomeCommitter.commit("))
        self.assertEqual(1, self.complete.count("summaryPreview.copy(restQuote = restQuote)"))

    def test_android_adapters_own_the_extracted_completion_side_effects(self) -> None:
        expected_once = (
            "PersistentMemoryManager.recordHit(appContext, type)",
            "RunFlavorPresentation.collisionCue(",
            "DialogueBubbleManager.spawn(",
            "FlavorTextManager.spawn(",
            "RestQuoteManager.quoteFor(",
        )
        for call in expected_once:
            self.assertEqual(1, self.terminal.count(call), call)


if __name__ == "__main__":
    unittest.main()
