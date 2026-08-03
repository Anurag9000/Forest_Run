#!/usr/bin/env python3
"""Source contracts for the terminal HIT impact-to-completion boundary."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
TERMINAL = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/TerminalHitOutcomeCoordinator.kt"
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
        cls.terminal = TERMINAL.read_text(encoding="utf-8")
        start = cls.game_view.index("CollisionResult.HIT ->")
        end = cls.game_view.index("CollisionResult.STUMBLE ->", start)
        cls.hit_block = cls.game_view[start:end]
        cls.complete = extract_braced_block(
            cls.terminal,
            "fun complete(\n        killerType: EntityType?,",
        )

    def test_game_view_delegates_impact_and_completion_once(self) -> None:
        self.assertEqual(1, self.hit_block.count("terminalHitImpact.apply"))
        self.assertEqual(1, self.hit_block.count("terminalHitOutcome.complete("))
        self.assertLess(
            self.hit_block.index("terminalHitImpact.apply"),
            self.hit_block.index("terminalHitOutcome.complete("),
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
            self.assertNotIn(call, self.hit_block)

    def test_post_impact_capture_precedes_completion_and_death(self) -> None:
        order = (
            "terminalHitImpact.apply",
            "ghostRecorder.detachSnapshot()",
            "entityManager.entityTypeOf(collision.entity)",
            "TerminalHitImpactCapture(",
            "terminalHitOutcome.complete(",
            "currentRestQuote = completedHit.summary.restQuote",
            "currentRunSummary = completedHit.summary",
            "runResetManager.triggerDeath(gameState)",
            "runState = RunState.DYING",
        )
        positions = [self.hit_block.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

    def test_game_view_passes_captured_identity_and_accepts_completed_summary(self) -> None:
        required_once = (
            "killerType = impact.killerType",
            "biome = impact.biome",
            "presentation = impact.presentation",
            "completedGhost = impact.completedGhost",
            "persistEncounter = persistEncounter",
            "gameState.buildRunSummary(lastKiller = impact.killerType)",
            "currentRestQuote = completedHit.summary.restQuote",
            "currentRunSummary = completedHit.summary",
        )
        for item in required_once:
            self.assertEqual(1, self.hit_block.count(item), item)

        capture_inputs = (
            "biome = entityManager.biomeManager.currentBiome",
            "routeTier = gameState.pacifistRouteTier",
            "playerX = player.x",
            "playerY = player.y",
            "completedGhost = completedGhost",
        )
        for item in capture_inputs:
            self.assertEqual(1, self.hit_block.count(item), item)

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
