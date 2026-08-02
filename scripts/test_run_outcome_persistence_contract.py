#!/usr/bin/env python3
"""Source contracts for exactly-once terminal run persistence ownership."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
COORDINATOR = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomePersistenceCoordinator.kt"
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


class RunOutcomePersistenceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.coordinator = COORDINATOR.read_text(encoding="utf-8")

    def test_game_view_has_no_direct_terminal_persistence_writes(self) -> None:
        forbidden = (
            "ForestMoodSystem.recordRun(",
            "ReturnMomentsSystem.recordRunOutcome(",
            "SaveManager.saveLastRunSummary(",
            "GhostPersistenceManager.saveBestRunAsync(",
            "SaveManager.saveBestDistance(",
        )
        for call in forbidden:
            self.assertNotIn(call, self.game_view)
        self.assertEqual(1, self.game_view.count("runOutcomePersistence.commit("))

    def test_hit_path_detaches_then_commits_before_death_transition(self) -> None:
        start = self.game_view.index("CollisionResult.HIT ->")
        end = self.game_view.index("CollisionResult.STUMBLE ->", start)
        hit_block = self.game_view[start:end]

        detach = hit_block.index("val completedGhost = ghostRecorder.detachSnapshot()")
        commit = hit_block.index("runOutcomePersistence.commit(")
        transition = hit_block.index("runResetManager.triggerDeath(gameState)")
        self.assertLess(detach, commit)
        self.assertLess(commit, transition)
        self.assertNotIn("ghostRecorder.reset()", hit_block)

    def test_each_run_start_reopens_the_coordinator(self) -> None:
        fresh = extract_braced_block(self.game_view, "private fun prepareFreshRun()")
        scenario = extract_braced_block(
            self.game_view, "private fun prepareEncounterScenario()"
        )
        self.assertEqual(1, fresh.count("runOutcomePersistence.resetForNewRun()"))
        self.assertEqual(1, scenario.count("runOutcomePersistence.resetForNewRun()"))
        self.assertEqual(2, self.game_view.count("runOutcomePersistence.resetForNewRun()"))

    def test_commit_claims_token_before_mode_gate_and_sink_calls(self) -> None:
        commit = extract_braced_block(
            self.coordinator,
            "fun commit(\n        summary: RunSummary,",
        )
        already = commit.index("if (terminalOutcomeCommitted)")
        claim = commit.index("terminalOutcomeCommitted = true")
        mode_gate = commit.index("if (!persistProgress)")
        first_sink = commit.index("sink.loadBestDistanceM()")
        self.assertLess(already, claim)
        self.assertLess(claim, mode_gate)
        self.assertLess(mode_gate, first_sink)

    def test_coordinator_is_the_only_production_write_adapter(self) -> None:
        expected_once = (
            "GhostPersistenceManager.saveBestRunAsync(appContext, frames)",
            "SaveManager.saveBestDistance(appContext, distanceM)",
            "ForestMoodSystem.recordRun(appContext, summary)",
            "ReturnMomentsSystem.recordRunOutcome(appContext, summary)",
            "SaveManager.saveLastRunSummary(appContext, summary)",
        )
        for call in expected_once:
            self.assertEqual(1, self.coordinator.count(call))

    def test_best_distance_advances_only_after_accepted_ghost(self) -> None:
        commit = extract_braced_block(
            self.coordinator,
            "fun commit(\n        summary: RunSummary,",
        )
        promoted = commit.index("val ghostPromoted =")
        publish = commit.index("sink.publishBestGhost(completedGhost)")
        gate = commit.index("if (ghostPromoted)")
        save = commit.index("sink.saveBestDistanceM(completedDistance)")
        summary_write = commit.index("sink.recordForestMood(summary)")
        self.assertLess(promoted, publish)
        self.assertLess(publish, gate)
        self.assertLess(gate, save)
        self.assertLess(save, summary_write)


if __name__ == "__main__":
    unittest.main()
