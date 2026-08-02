#!/usr/bin/env python3
"""Source contracts for recovery-record completeness and consistency."""

from __future__ import annotations

import pathlib
import unittest

SOURCE_PATH = (
    pathlib.Path(__file__).resolve().parents[1]
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomeRecoveryStore.kt"
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


class RunOutcomeRecoveryRecordValidationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE_PATH.read_text(encoding="utf-8")

    def test_summary_reader_requires_every_serialized_key(self) -> None:
        read = extract_braced_block(self.source, "private fun readSummary()")
        self.assertIn("REQUIRED_SUMMARY_KEYS.any", read)
        self.assertIn("!prefs.contains(key)", read)
        required = extract_between(
            self.source,
            "val REQUIRED_SUMMARY_KEYS = arrayOf(",
            "const val PREVIOUS_MOOD",
        )
        for key in (
            "SUMMARY_SCORE",
            "SUMMARY_DISTANCE",
            "SUMMARY_NEW_HIGH",
            "SUMMARY_HIGH_SCORE",
            "SUMMARY_MERCY_HEARTS",
            "SUMMARY_MERCY_MISSES",
            "SUMMARY_KINDNESS_CHAIN",
            "SUMMARY_CLEAN_PASSES",
            "SUMMARY_SPARED",
            "SUMMARY_HITS",
            "SUMMARY_SEEDS",
            "SUMMARY_BLOOM",
            "SUMMARY_LAST_KILLER",
            "SUMMARY_REST_QUOTE",
            "SUMMARY_FOREST_MOOD",
            "SUMMARY_ROUTE_TIER",
        ):
            self.assertIn(key, required)

    def test_raw_summary_validation_only_bounds_payload_size(self) -> None:
        valid = extract_between(
            self.source,
            "private fun isValidSummary(",
            "private fun isValidMood(",
        )
        self.assertIn("summary.restQuote.length <= MAX_QUOTE_LENGTH", valid)
        self.assertNotIn("summary.score >= 0", valid)
        self.assertNotIn("summary.distanceM.isFinite", valid)

    def test_record_after_states_must_match_canonical_transitions(self) -> None:
        valid = extract_between(
            self.source,
            "private fun isValid(record:",
            "private fun isValidSummary(",
        )
        expected = (
            "record.previousRouteTierCount in 0..MAX_RECOVERABLE_ROUTE_TIER_COUNT",
            "record.nextMood == RunOutcomeRecoveryTransitions.nextForestMood(",
            "record.nextReturn == RunOutcomeRecoveryTransitions.nextReturnMoment(",
            "nowMs = record.nextReturn.lastActiveAtMs",
            "record.nextRouteTierCount == RunOutcomeRecoveryTransitions.nextRouteTierCount(",
            "tier = record.summary.pacifistRouteTier",
        )
        for item in expected:
            self.assertIn(item, valid)

    def test_return_snapshots_match_save_manager_bounds(self) -> None:
        valid = extract_between(
            self.source,
            "private fun isValidReturn(",
            "private data class NullableEnum",
        )
        self.assertIn("state.lastActiveAtMs >= 0L", valid)
        self.assertIn("state.lastGardenGreetingDay >= -1L", valid)
        self.assertIn("state.roughRunStreak >= 0", valid)

    def test_loaded_record_is_validated_before_pending_result(self) -> None:
        load = extract_braced_block(
            self.source,
            "override fun load(): RunOutcomeRecoveryLoadResult",
        )
        construct = load.index("val record = RunOutcomeRecoveryRecord(")
        validate = load.index("if (!isValid(record))")
        pending = load.index("RunOutcomeRecoveryLoadResult.Pending(record)")
        self.assertLess(construct, validate)
        self.assertLess(validate, pending)


if __name__ == "__main__":
    unittest.main()
