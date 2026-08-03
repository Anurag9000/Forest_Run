#!/usr/bin/env python3
"""Source contracts for overflow- and rollback-safe return-moment arithmetic."""

from __future__ import annotations

import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
RETURN_MOMENTS = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/ReturnMomentsSystem.kt"
)
ARITHMETIC = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/SafeProgressionArithmetic.kt"
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


class ReturnMomentArithmeticContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.return_moments = RETURN_MOMENTS.read_text(encoding="utf-8")
        cls.arithmetic = ARITHMETIC.read_text(encoding="utf-8")
        cls.record = extract_braced_block(
            cls.return_moments,
            "fun recordRunOutcome(",
        )
        cls.build = extract_braced_block(
            cls.return_moments,
            "private fun buildGardenMoment(",
        )
        cls.increment = extract_braced_block(
            cls.arithmetic,
            "fun saturatingIncrement(",
        )
        cls.elapsed = extract_braced_block(
            cls.arithmetic,
            "fun elapsedAtLeast(",
        )

    def test_rough_run_streak_uses_shared_saturating_increment(self) -> None:
        expected = "SafeProgressionArithmetic.saturatingIncrement(previous.roughRunStreak)"
        self.assertEqual(1, self.record.count(expected))
        self.assertNotRegex(
            self.record,
            re.compile(r"(?:previous\.)?roughRunStreak\s*\+\s*1"),
        )
        self.assertNotIn("Math.addExact", self.record)

    def test_long_absence_uses_rollback_safe_elapsed_predicate(self) -> None:
        expected = (
            "SafeProgressionArithmetic.elapsedAtLeast(\n"
            "                nowMs = nowMs,\n"
            "                earlierMs = previous.lastActiveAtMs,\n"
            "                thresholdMs = LONG_ABSENCE_MS\n"
            "            )"
        )
        self.assertEqual(1, self.build.count(expected))
        self.assertNotRegex(
            self.build,
            re.compile(r"nowMs\s*-\s*previous\.lastActiveAtMs"),
        )
        self.assertNotRegex(
            self.build,
            re.compile(r"previous\.lastActiveAtMs\s*-\s*nowMs"),
        )
        self.assertNotIn("kotlin.math.abs", self.build)
        self.assertNotIn("Math.abs", self.build)

    def test_saturating_increment_normalizes_before_adding(self) -> None:
        required_order = (
            "require(maximum >= 0)",
            "value.coerceIn(0, maximum)",
            "normalized >= maximum",
            "normalized + 1",
        )
        positions = [self.increment.index(item) for item in required_order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("const val DEFAULT_COUNTER_MAX = Int.MAX_VALUE / 16", self.arithmetic)

    def test_elapsed_predicate_guards_all_invalid_ordering_before_subtraction(self) -> None:
        guard = (
            "nowMs < 0L || earlierMs < 0L || thresholdMs < 0L || nowMs < earlierMs"
        )
        self.assertEqual(1, self.elapsed.count(guard))
        guard_position = self.elapsed.index(guard)
        subtraction_position = self.elapsed.index("nowMs - earlierMs")
        self.assertLess(guard_position, subtraction_position)
        self.assertNotIn("Math.subtractExact", self.elapsed)

    def test_return_moment_threshold_is_exactly_thirty_six_hours(self) -> None:
        self.assertIn(
            "private const val LONG_ABSENCE_MS = 36L * 60L * 60L * 1_000L",
            self.return_moments,
        )


if __name__ == "__main__":
    unittest.main()
