#!/usr/bin/env python3
"""Source contract for independently accumulated relationship warmth modifiers."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
RELATIONSHIP = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RelationshipArcSystem.kt"
)
SCORING = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/FamiliarityWarmthScoring.kt"
)
INTEGRATION = (
    ROOT
    / "app/src/test/java/com/anurag9000/forestrun/engine/FamiliarityWarmthIntegrationTest.kt"
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


class FamiliarityWarmthContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.relationship = RELATIONSHIP.read_text(encoding="utf-8")
        cls.scoring = SCORING.read_text(encoding="utf-8")
        cls.integration = INTEGRATION.read_text(encoding="utf-8")
        cls.warmth = extract_braced_block(
            cls.relationship,
            "private fun familiarityWarmth("
        )
        cls.score = extract_braced_block(cls.scoring, "fun score(")

    def test_relationship_owner_delegates_all_inputs_to_pure_scorer(self) -> None:
        self.assertEqual(1, self.warmth.count("FamiliarityWarmthScoring.score("))
        for argument in (
            "stage = stage",
            "passCount = passCount",
            "sparedCount = sparedCount",
            "kindnessStreak = kindnessStreak",
            "encounters = encounters",
        ):
            self.assertEqual(1, self.warmth.count(argument), argument)
        self.assertNotIn("val score = stage.ordinal +", self.warmth)
        self.assertNotIn("+ if (", self.warmth)

    def test_scorer_accumulates_every_authored_modifier_independently(self) -> None:
        required = (
            "stageBase(stage) +",
            "bonus(safePasses >= 3) +",
            "bonus(safePasses >= 5) +",
            "bonus(safeSpares >= 2) +",
            "bonus(safeKindness >= 3) +",
            "bonus(safeEncounters >= 5)",
        )
        positions = [self.score.index(item) for item in required]
        self.assertEqual(sorted(positions), positions)
        self.assertNotIn("+ if (", self.score)
        self.assertNotIn("else 0 +", self.score)

    def test_restored_counters_are_normalized_before_thresholds(self) -> None:
        for normalized in (
            "passCount.coerceAtLeast(0)",
            "sparedCount.coerceAtLeast(0)",
            "kindnessStreak.coerceAtLeast(0)",
            "encounters.coerceAtLeast(0)",
        ):
            self.assertEqual(1, self.score.count(normalized), normalized)

    def test_authored_tier_thresholds_remain_stable(self) -> None:
        self.assertIn("const val PERSONAL_THRESHOLD = 5", self.scoring)
        self.assertIn("const val BONDED_THRESHOLD = 7", self.scoring)
        tier = extract_braced_block(self.scoring, "fun tierOrdinal(")
        bonded = tier.index("score >= BONDED_THRESHOLD -> 3")
        personal = tier.index("score >= PERSONAL_THRESHOLD -> 2")
        gentle = tier.index("else -> 1")
        self.assertLess(bonded, personal)
        self.assertLess(personal, gentle)

    def test_public_integration_locks_accumulated_bonded_copy(self) -> None:
        required = (
            "repeat(5) { PersistentMemoryManager.recordEncounter",
            "repeat(2) { PersistentMemoryManager.recordSpare",
            "repeat(5) { PersistentMemoryManager.recordPass",
            '"You came back to our quiet."',
            "RelationshipArcSystem.Event.PASS",
        )
        for item in required:
            self.assertIn(item, self.integration)


if __name__ == "__main__":
    unittest.main()
