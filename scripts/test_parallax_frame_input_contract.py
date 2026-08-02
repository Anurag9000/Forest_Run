from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/ParallaxBackground.kt"


def extract_function_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"Function signature not found: {signature}")
    opening = source.find("{", start + len(signature))
    if opening < 0:
        raise AssertionError(f"Function body not found: {signature}")

    depth = 0
    for index in range(opening, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"Unbalanced function body: {signature}")


class ParallaxFrameInputContractTest(unittest.TestCase):
    def setUp(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.body = extract_function_body(
            source,
            "fun update(deltaTime: Float, gameScrollSpeed: Float)",
        )

    def test_admission_is_first_executable_statement(self) -> None:
        statements = [
            line.strip()
            for line in self.body.splitlines()
            if line.strip() and not line.lstrip().startswith("//")
        ]
        self.assertGreaterEqual(len(statements), 3)
        self.assertEqual(
            "if (!FrameInputAdmission.accepts(deltaTime, gameScrollSpeed)) return",
            statements[0],
        )
        self.assertEqual(
            "val dt = FrameInputAdmission.boundedDeltaSeconds(deltaTime)",
            statements[1],
        )
        self.assertEqual(
            "val scrollSpeed = FrameInputAdmission.boundedScrollSpeed(gameScrollSpeed)",
            statements[2],
        )

    def test_all_mutation_uses_bounded_values(self) -> None:
        required = (
            "ambienceTime = (safeAmbienceTime + dt).coerceAtMost(Float.MAX_VALUE)",
            "currentScrollSpeed = scrollSpeed",
            "layer.update(dt, scrollSpeed)",
            "(blendSpeed * dt).coerceAtMost(1f)",
            "safeBloomPulse + dt * 3.4f",
        )
        for expression in required:
            self.assertIn(expression, self.body)

    def test_raw_inputs_are_only_used_by_admission_and_bounding(self) -> None:
        self.assertEqual(2, self.body.count("deltaTime"))
        self.assertEqual(2, self.body.count("gameScrollSpeed"))


if __name__ == "__main__":
    unittest.main()
