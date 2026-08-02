from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"
SIGNATURE = "fun onTap(tapX: Float = 0f, tapY: Float = 0f)"


def extract_function_body(source: str, signature: str) -> str:
    signature_index = source.find(signature)
    if signature_index < 0:
        raise AssertionError(f"function signature was not found: {signature}")
    opening = source.find("{", signature_index + len(signature))
    if opening < 0:
        raise AssertionError(f"function body was not found: {signature}")

    depth = 0
    for index in range(opening, len(source)):
        character = source[index]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"function body is unterminated: {signature}")


class MainMenuInputContractTest(unittest.TestCase):
    def test_finite_guard_is_first_executable_statement(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        body = extract_function_body(source, SIGNATURE)
        statements = [
            line.strip()
            for line in body.splitlines()
            if line.strip() and not line.lstrip().startswith("//")
        ]
        self.assertGreaterEqual(len(statements), 2)
        self.assertEqual(
            "if (!FiniteCoordinateAdmission.accepts(tapX, tapY)) return",
            statements[0],
        )
        guard_index = body.index("FiniteCoordinateAdmission.accepts")
        self.assertLess(guard_index, body.index("feedbackSettingsPanel.onTap"))
        self.assertLess(guard_index, body.index("onGardenTap?.invoke"))
        self.assertLess(guard_index, body.index("when (phase)"))

    def test_guard_uses_both_coordinates_exactly_once(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        body = extract_function_body(source, SIGNATURE)
        self.assertEqual(
            1,
            body.count("FiniteCoordinateAdmission.accepts(tapX, tapY)"),
        )

    def test_balanced_extractor_includes_nested_branches(self) -> None:
        sample = "fun onTap(tapX: Float = 0f, tapY: Float = 0f) { if (true) { x() }; y() }"
        body = extract_function_body(sample, SIGNATURE)
        self.assertIn("x()", body)
        self.assertIn("y()", body)


if __name__ == "__main__":
    unittest.main()
