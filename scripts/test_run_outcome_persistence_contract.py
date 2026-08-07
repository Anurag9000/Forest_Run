#!/usr/bin/env python3
"""Source contracts for exactly-once terminal run persistence ownership."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
GAME_VIEW = ENGINE / "GameView.kt"
DISPATCHER = ENGINE / "CollisionOutcomeDispatcher.kt"
COORDINATOR = ENGINE / "RunOutcomePersistenceCoordinator.kt"
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


class RunOutcomePersistenceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.dispatcher = DISPATCHER.read_text(encoding="utf-8")
        cls.coordinator = COORDINATOR.read_text(encoding="utf-8")
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

    def test_game_view_has_no_direct_terminal_persistence_writes(self) -> None:
        forbidden = (
            "ForestMoodSystem.recordRun(",
            "ReturnMomentsSystem.recordRunOutcome(",
            "SaveManager.saveLastRunSummary(",
            "GhostPersistenceManager.saveBestRunAsync(",
            "SaveManager.saveBestDistance(",
            "runOutcomePersistence.commit(",
            "terminalHitOutcome.complete(",
        )
        for call in forbidden:
            self.assertNotIn(call, self.game_view)
        self.assertEqual(1, self.game_view.count("collisionOutcomeDispatcher.dispatch("))
        self.assertEqual(1, self.dispatcher.count("terminalHitOutcome.complete("))

    def test_hit_path_keeps_lazy_capture_completion_and_session_handoff_order(self) -> None:
        self.assertEqual(
            1,
            self.live_collision.count(
                "val completedGhost = ghostRecorder.detachSnapshot()"
            ),
        )
        self.assertEqual(1, self.live_collision.count("captureTerminalImpact = {"))
        self.assertEqual(
            1,
            self.live_collision.count("buildTerminalSummaryPreview = { killerType ->"),
        )
        self.assertEqual(
            1,
            self.live_collision.count(
                "dispatchResult is CollisionOutcomeDispatchResult.Terminal"
            ),
        )
        detach = self.live_collision.index("ghostRecorder.detachSnapshot()")
        summary_builder = self.live_collision.index("buildTerminalSummaryPreview")
        transition = self.live_collision.index(
            "applyRunSessionEvent(RunSessionEvent.TERMINAL_COLLISION_COMPLETED)"
        )
        self.assertLess(detach, summary_builder)
        self.assertLess(summary_builder, transition)
        self.assertNotIn("ghostRecorder.reset()", self.live_collision)
        self.assertNotIn("runResetManager.triggerDeath(gameState)", self.live_collision)

        impact = self.dispatch.index(
            "terminalHitImpact.apply(captureTerminalImpact)"
        )
        complete = self.dispatch.index("terminalHitOutcome.complete(")
        self.assertLess(impact, complete)
        self.assertIn(
            "event == RunSessionEvent.TERMINAL_COLLISION_COMPLETED",
            self.session_planner,
        )
        self.assertIn("effects = listOf(RunSessionEffect.TRIGGER_DEATH)", self.session_planner)

    def test_each_run_start_reopens_and_retries_recovery(self) -> None:
        fresh = extract_braced_block(self.game_view, "private fun prepareFreshRun()")
        scenario = extract_braced_block(
            self.game_view, "private fun prepareEncounterScenario()"
        )
        self.assertEqual(1, fresh.count("runOutcomePersistence.resetForNewRun()"))
        self.assertEqual(1, scenario.count("runOutcomePersistence.resetForNewRun()"))
        self.assertEqual(2, self.game_view.count("runOutcomePersistence.resetForNewRun()"))

        reset = extract_braced_block(self.coordinator, "fun resetForNewRun()")
        self.assertLess(
            reset.index("terminalOutcomeCommitted = false"),
            reset.index("recoveryBlocked = !recoverPendingOutcome()"),
        )

    def test_commit_claims_token_before_mode_and_recovery_gates(self) -> None:
        commit = extract_braced_block(
            self.coordinator,
            "override fun commit(\n        summary: RunSummary,",
        )
        already = commit.index("if (terminalOutcomeCommitted)")
        claim = commit.index("terminalOutcomeCommitted = true")
        mode_gate = commit.index("if (!persistProgress)")
        recovery_gate = commit.index("if (recoveryBlocked)")
        prepare = commit.index("prepareRecoveryRecord(recoverable, summary)")
        first_ghost_sink = commit.index("sink.loadBestDistanceM()")
        self.assertLess(already, claim)
        self.assertLess(claim, mode_gate)
        self.assertLess(mode_gate, recovery_gate)
        self.assertLess(recovery_gate, prepare)
        self.assertLess(prepare, first_ghost_sink)

    def test_production_adapter_owns_storage_and_recovery_surfaces(self) -> None:
        expected_once = (
            "GhostPersistenceManager.recoverPendingPromotion(appContext)",
            "GhostPersistenceManager.bestDistanceFloor(appContext)",
            "GhostPersistenceManager.saveBestRunAsync(",
            "ForestMoodSystem.recordRun(appContext, summary)",
            "ReturnMomentsSystem.recordRunOutcome(appContext, summary)",
            "SaveManager.saveLastRunSummary(appContext, summary)",
            "SaveManager.loadForestMoodState(appContext)",
            "SaveManager.saveForestMoodState(appContext, state)",
            "SaveManager.loadReturnMomentState(appContext)",
            "SaveManager.saveReturnMomentState(appContext, state)",
            "SaveManager.loadLastRunSummary(appContext)",
            "SaveManager.loadRouteTierCount(appContext, tier)",
        )
        for call in expected_once:
            self.assertEqual(1, self.coordinator.count(call), call)
        self.assertNotIn("SaveManager.saveBestDistance(", self.coordinator)
        self.assertNotIn("fun saveBestDistanceM(", self.coordinator)
        self.assertEqual(1, self.coordinator.count("SharedPreferencesRunOutcomeRecoveryStore("))
        self.assertEqual(
            1,
            self.coordinator.count("SharedPreferencesRunOutcomeSummarySnapshotStore("),
        )
        self.assertEqual(1, self.coordinator.count("private val persistenceNamespace ="))

    def test_recovery_record_is_durable_before_ghost_evaluation(self) -> None:
        prepare = extract_braced_block(
            self.coordinator,
            "private fun prepareRecoveryRecord(",
        )
        order = (
            "recoverable.loadForestMoodState()",
            "recoverable.loadReturnMomentState()",
            "recoverable.loadRouteTierCount(summary.pacifistRouteTier)",
            "RunOutcomeRecoveryTransitions.nextForestMood(",
            "RunOutcomeRecoveryTransitions.nextReturnMoment(",
            "RunOutcomeRecoveryTransitions.nextRouteTierCount(",
            "recoverable.recoveryStore.save(it)",
        )
        positions = [prepare.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

        commit = extract_braced_block(
            self.coordinator,
            "override fun commit(\n        summary: RunSummary,",
        )
        self.assertLess(
            commit.index("prepareRecoveryRecord(recoverable, summary)"),
            commit.index("sink.loadBestDistanceM()"),
        )

    def test_ghost_candidate_carries_distance_without_direct_threshold_write(self) -> None:
        commit = extract_braced_block(
            self.coordinator,
            "override fun commit(\n        summary: RunSummary,",
        )
        promoted = commit.index("val ghostPromoted =")
        publish = commit.index("sink.publishBestGhost(")
        distance = commit.index("distanceM = completedDistance", publish)
        bundle = commit.index("commitRecoveryProtectedBundle(")
        self.assertLess(promoted, publish)
        self.assertLess(publish, distance)
        self.assertLess(distance, bundle)
        self.assertNotIn("saveBestDistance", commit)
        self.assertIn(
            "fun publishBestGhost(frames: List<GhostFrame>, distanceM: Float): Boolean",
            self.coordinator,
        )

    def test_recovery_bundle_orders_states_atomic_snapshot_and_clear(self) -> None:
        bundle = extract_braced_block(
            self.coordinator,
            "private fun commitRecoveryProtectedBundle(",
        )
        order = (
            "ensureMoodState(recoverable, record)",
            "RunOutcomeRecoveryPhase.MOOD_APPLIED",
            "ensureReturnState(recoverable, record)",
            "RunOutcomeRecoveryPhase.RETURN_APPLIED",
            "ensureSummaryState(recoverable, record)",
            "RunOutcomeRecoveryPhase.SUMMARY_APPLIED",
            "recoverable.recoveryStore.clear()",
        )
        positions = [bundle.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertNotIn("sink.saveLastRunSummary(record.summary)", bundle)

    def test_recovery_recognizes_applied_states_before_reapplying(self) -> None:
        mood = extract_braced_block(self.coordinator, "private fun ensureMoodState(")
        return_state = extract_braced_block(
            self.coordinator,
            "private fun ensureReturnState(",
        )
        for block, next_state, previous_state, save_call in (
            (
                mood,
                "actual == record.nextMood",
                "actual != record.previousMood",
                "recoverable.saveForestMoodState(record.nextMood)",
            ),
            (
                return_state,
                "actual == record.nextReturn",
                "actual != record.previousReturn",
                "recoverable.saveReturnMomentState(record.nextReturn)",
            ),
        ):
            self.assertLess(block.index(next_state), block.index(previous_state))
            self.assertLess(block.index(previous_state), block.index(save_call))

    def test_summary_recovery_compares_summary_and_route_before_atomic_save(self) -> None:
        summary = extract_braced_block(self.coordinator, "private fun ensureSummaryState(")
        order = (
            "RunOutcomeRecoveryTransitions.persistedSummary(record.summary)",
            "recoverable.loadLastRunSummary()",
            "recoverable.loadRouteTierCount(routeTier)",
            "actualSummary == expectedSummary",
            "actualRouteTierCount != record.previousRouteTierCount",
            "recoverable.summarySnapshotStore.save(",
            "recoverable.loadLastRunSummary() == expectedSummary",
        )
        positions = [summary.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("routeTierCount = record.nextRouteTierCount", summary)

    def test_corrupt_or_conflicting_recovery_fails_closed(self) -> None:
        recover = extract_braced_block(
            self.coordinator,
            "private fun recoverPendingOutcome()",
        )
        self.assertIn("RunOutcomeRecoveryLoadResult.Corrupt -> false", recover)
        self.assertEqual(6, self.coordinator.count("recoveryBlocked = true"))
        self.assertIn("RunOutcomeCommitDisposition.RECOVERY_BLOCKED", self.coordinator)
        self.assertIn("RunOutcomeCommitDisposition.RECOVERY_PENDING", self.coordinator)


if __name__ == "__main__":
    unittest.main()
