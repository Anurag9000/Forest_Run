#!/usr/bin/env python3
"""Source contracts for the durable terminal-outcome recovery journal."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STORE = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomeRecoveryStore.kt"
)
SNAPSHOT = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomeSummarySnapshotStore.kt"
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


def extract_between(source: str, start_signature: str, end_signature: str) -> str:
    start = source.index(start_signature)
    end = source.index(end_signature, start)
    return source[start:end]


class RunOutcomeRecoveryStoreContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = STORE.read_text(encoding="utf-8")
        cls.snapshot = SNAPSHOT.read_text(encoding="utf-8")

    def test_store_is_scoped_to_sanitized_persistence_namespace(self) -> None:
        constructor = extract_braced_block(
            self.source,
            "internal class SharedPreferencesRunOutcomeRecoveryStore(",
        )
        self.assertIn(
            '"forest_run_outcome_recovery_${safeNamespace(persistenceNamespace)}"',
            constructor,
        )
        safe = extract_braced_block(self.source, "fun safeNamespace(namespace: String)")
        self.assertIn("char.isLetterOrDigit()", safe)
        self.assertIn("take(96)", safe)
        self.assertIn('safe.ifBlank { "default" }', safe)

    def test_load_distinguishes_empty_corrupt_and_pending_schema_two(self) -> None:
        load = extract_braced_block(
            self.source,
            "override fun load(): RunOutcomeRecoveryLoadResult",
        )
        self.assertIn("RunOutcomeRecoveryLoadResult.Empty", load)
        self.assertIn("schema != SCHEMA_VERSION", load)
        self.assertIn("RunOutcomeRecoveryLoadResult.Corrupt", load)
        self.assertIn("RunOutcomeRecoveryLoadResult.Pending(", load)
        self.assertIn("catch (_: ClassCastException)", load)
        self.assertIn("const val SCHEMA_VERSION = 2", self.source)

    def test_record_validation_precedes_destructive_replacement(self) -> None:
        save = extract_braced_block(
            self.source,
            "override fun save(record: RunOutcomeRecoveryRecord)",
        )
        validate = save.index("if (!isValid(record)) return false")
        clear = save.index("prefs.edit().clear()")
        commit = save.index("editor.commit()")
        self.assertLess(validate, clear)
        self.assertLess(clear, commit)

    def test_journal_writes_and_clear_are_synchronous(self) -> None:
        self.assertEqual(2, self.source.count(".commit()"))
        self.assertNotIn(".apply()", self.source)
        self.assertIn("override fun clear(): Boolean = prefs.edit().clear().commit()", self.source)

    def test_all_summary_identity_fields_are_serialized(self) -> None:
        write = extract_braced_block(self.source, "private fun writeSummary(")
        read = extract_braced_block(self.source, "private fun readSummary()")
        fields = (
            "score",
            "distanceM",
            "isNewHighScore",
            "highScore",
            "mercyHearts",
            "mercyMisses",
            "kindnessChain",
            "cleanPasses",
            "sparedCount",
            "hitsTaken",
            "seedsCollected",
            "bloomConversions",
            "lastKiller",
            "restQuote",
            "forestMood",
            "pacifistRouteTier",
        )
        for field in fields:
            self.assertIn(f"summary.{field}", write, field)
        self.assertIn("RunSummary(", read)
        self.assertIn("lastKiller.value", read)

    def test_before_and_after_progression_snapshots_are_stored(self) -> None:
        save = extract_braced_block(
            self.source,
            "override fun save(record: RunOutcomeRecoveryRecord)",
        )
        for item in (
            "writeMood(editor, PREVIOUS_MOOD, record.previousMood)",
            "writeMood(editor, NEXT_MOOD, record.nextMood)",
            "writeReturn(editor, PREVIOUS_RETURN, record.previousReturn)",
            "writeReturn(editor, NEXT_RETURN, record.nextReturn)",
            ".putInt(PREVIOUS_ROUTE_TIER_COUNT, record.previousRouteTierCount)",
            ".putInt(NEXT_ROUTE_TIER_COUNT, record.nextRouteTierCount)",
        ):
            self.assertIn(item, save)
        self.assertIn("previousRouteTierCount = previousRouteTierCount", self.source)
        self.assertIn("nextRouteTierCount = nextRouteTierCount", self.source)

    def test_forest_mood_transition_saturates_each_counter(self) -> None:
        transition = extract_braced_block(
            self.source,
            "fun nextForestMood(previous: ForestMoodState, summary: RunSummary)",
        )
        self.assertIn("saturatingIncrement(previous.moodStreak)", transition)
        self.assertIn("saturatingIncrement(previous.totalRuns)", transition)
        for counter in ("gentleRuns", "recklessRuns", "fearfulRuns", "steadyRuns"):
            self.assertIn(f"saturatingIncrement(previous.{counter})", transition)

    def test_return_transition_preserves_canonical_rough_run_formula(self) -> None:
        transition = extract_braced_block(
            self.source,
            "fun nextReturnMoment(",
        )
        required = (
            "summary.forestMood == ForestMood.FEARFUL",
            "summary.hitsTaken >= 2 && summary.distanceM < 650f",
            "summary.hitsTaken > 0 && summary.kindnessChain == 0 && summary.seedsCollected < 4",
            "lastActiveAtMs = nowMs",
            "SafeProgressionArithmetic.saturatingIncrement(previous.roughRunStreak)",
        )
        for item in required:
            self.assertIn(item, transition)

    def test_route_transition_preserves_none_and_saturates_real_tiers(self) -> None:
        transition = extract_between(
            self.source,
            "fun nextRouteTierCount(",
            "fun persistedSummary(",
        )
        self.assertIn("tier == PacifistRouteTier.NONE", transition)
        self.assertIn("previous.coerceAtLeast(0)", transition)
        self.assertIn("saturatingIncrement(previous)", transition)

    def test_persisted_summary_matches_save_manager_sanitization(self) -> None:
        transition = extract_between(
            self.source,
            "fun persistedSummary(",
            "private fun saturatingIncrement(",
        )
        self.assertIn("summary.score.coerceAtLeast(0)", transition)
        self.assertIn("summary.distanceM.takeIf { it.isFinite() }", transition)
        for field in (
            "highScore",
            "mercyHearts",
            "mercyMisses",
            "kindnessChain",
            "cleanPasses",
            "sparedCount",
            "hitsTaken",
            "seedsCollected",
            "bloomConversions",
        ):
            self.assertIn(f"summary.{field}.coerceAtLeast(0)", transition)

    def test_atomic_snapshot_writes_summary_and_route_in_one_commit(self) -> None:
        save = extract_braced_block(
            self.snapshot,
            "override fun save(summary: RunSummary, routeTierCount: Int)",
        )
        self.assertIn("RunOutcomeRecoveryTransitions.persistedSummary(summary)", save)
        self.assertIn("routeTierKey(persisted.pacifistRouteTier)", save)
        self.assertIn("editor.putInt(key, routeTierCount.coerceAtLeast(0))", save)
        self.assertEqual(1, save.count("editor.commit()"))
        self.assertNotIn(".apply()", self.snapshot)
        self.assertNotIn("SaveManager.saveLastRunSummary", self.snapshot)

    def test_snapshot_maps_only_persistent_route_tiers(self) -> None:
        route = extract_braced_block(self.snapshot, "private fun routeTierKey(")
        self.assertIn("PacifistRouteTier.NONE -> null", route)
        self.assertIn("PacifistRouteTier.KIND -> KEY_ROUTE_KIND_RUNS", route)
        self.assertIn("PacifistRouteTier.MERCIFUL -> KEY_ROUTE_MERCIFUL_RUNS", route)
        self.assertIn("PacifistRouteTier.PEACEFUL -> KEY_ROUTE_PEACEFUL_RUNS", route)


if __name__ == "__main__":
    unittest.main()
