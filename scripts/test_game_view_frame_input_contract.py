from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"


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


class GameViewFrameInputContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SOURCE.read_text(encoding="utf-8")
        self.public_body = extract_function_body(
            self.source,
            "fun update(deltaTime: Float)",
        )
        self.bounded_body = extract_function_body(
            self.source,
            "private fun updateBounded(deltaTime: Float)",
        )

    def test_public_boundary_only_admits_and_dispatches(self) -> None:
        statements = [
            line.strip()
            for line in self.public_body.splitlines()
            if line.strip() and not line.lstrip().startswith("//")
        ]
        self.assertEqual(
            [
                "if (!FrameInputAdmission.acceptsDelta(deltaTime)) return",
                "updateBounded(FrameInputAdmission.boundedDeltaSeconds(deltaTime))",
            ],
            statements,
        )

    def test_mutation_starts_only_inside_bounded_body(self) -> None:
        self.assertNotIn("debugFrameCounter", self.public_body)
        self.assertIn("debugFrameCounter++", self.bounded_body)
        self.assertLess(
            self.bounded_body.index("debugFrameCounter++"),
            self.bounded_body.index("CameraSystem.update(deltaTime)"),
        )

    def test_there_is_one_public_and_one_private_frame_owner(self) -> None:
        self.assertEqual(1, self.source.count("fun update(deltaTime: Float)"))
        self.assertEqual(
            1,
            self.source.count("private fun updateBounded(deltaTime: Float)"),
        )


if __name__ == "__main__":
    unittest.main()
