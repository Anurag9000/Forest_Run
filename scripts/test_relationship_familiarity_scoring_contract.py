from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/RelationshipArcSystem.kt"


def extract_function_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"Function signature not found: {signature}")
    opening = source.find("{", start + len(signature))
    if opening < 0:
        raise AssertionError(f"Function body not found: {signature}")

    depth = 0
    for index in range(opening, len(source)):
        character = source[index]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"Unbalanced function body: {signature}")


class RelationshipFamiliarityScoringContractTest(unittest.TestCase):
    def setUp(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.body = extract_function_body(
            source,
            "private fun familiarityWarmth(",
        )

    def test_owner_delegates_score_to_canonical_helper(self) -> None:
        self.assertEqual(1, self.body.count("FamiliarityWarmthScoring.score("))
        for argument in (
            "stage = stage",
            "passCount = passCount",
            "sparedCount = sparedCount",
            "kindnessStreak = kindnessStreak",
            "encounters = encounters",
        ):
            self.assertIn(argument, self.body)

    def test_owner_delegates_tier_thresholds_to_canonical_helper(self) -> None:
        self.assertEqual(
            1,
            self.body.count("FamiliarityWarmthScoring.tierOrdinal(score)"),
        )
        self.assertNotIn("score >= 7", self.body)
        self.assertNotIn("score >= 5", self.body)

    def test_local_modifier_arithmetic_cannot_return(self) -> None:
        for expression in (
            "if (passCount >= 3)",
            "if (passCount >= 5)",
            "if (sparedCount >= 2)",
            "if (kindnessStreak >= 3)",
            "if (encounters >= 5)",
        ):
            self.assertNotIn(expression, self.body)


if __name__ == "__main__":
    unittest.main()
