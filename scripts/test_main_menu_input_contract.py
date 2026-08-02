from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"


class MainMenuInputContractTest(unittest.TestCase):
    def test_finite_guard_is_first_executable_statement(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        match = re.search(
            r"fun\s+onTap\s*\(\s*tapX:\s*Float\s*=\s*0f,\s*"
            r"tapY:\s*Float\s*=\s*0f\s*\)\s*\{(?P<body>.*?)\n\s*\}",
            source,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(match, "MainMenuScreen.onTap signature was not found")
        body = match.group("body")
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
        self.assertLess(
            body.index("FiniteCoordinateAdmission.accepts"),
            body.index("feedbackSettingsPanel.onTap"),
        )
        self.assertLess(
            body.index("FiniteCoordinateAdmission.accepts"),
            body.index("onGardenTap?.invoke"),
        )
        self.assertLess(
            body.index("FiniteCoordinateAdmission.accepts"),
            body.index("when (phase)"),
        )

    def test_guard_uses_both_coordinates(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(
            1,
            source.count("FiniteCoordinateAdmission.accepts(tapX, tapY)"),
        )


if __name__ == "__main__":
    unittest.main()
