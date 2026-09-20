from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from training_control.forest_no_trainable_authority import audit


class TrainingControlNotApplicableTest(unittest.TestCase):
    def test_live_repository_is_certified_as_non_trainable(self) -> None:
        result = audit(ROOT)
        self.assertTrue(result.complete)
        self.assertEqual((), result.findings)

    def test_generic_application_vocabulary_does_not_create_false_training_surface(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "scripts" / "ordinary_release.py"
            source.parent.mkdir(parents=True)
            source.write_text(
                "model = 'x86_64'\n"
                "task = 'google_apis'\n"
                "metrics = {'p95_frame_ms': 16, 'crashes': 0}\n"
                "scenario = {'stage': 'release', 'strategy': 'safe'}\n",
                encoding="utf-8",
            )
            self.assertTrue(audit(root).complete)

    def test_real_framework_marker_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "app" / "trainer.py"
            source.parent.mkdir(parents=True)
            marker = "to" + "rch"
            source.write_text(f"import {marker}\n", encoding="utf-8")
            result = audit(root)
            self.assertFalse(result.complete)
            self.assertEqual("py" + "to" + "rch", result.findings[0].category)

    def test_root_command_emits_truthful_n_a_certificate(self) -> None:
        result = subprocess.run(
            [sys.executable, str(ROOT / "run_all_training.py"), "--training-control-audit"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        payload = json.loads(
            (ROOT / ".training_control/coverage_report.json").read_text(encoding="utf-8")
        )
        self.assertFalse(payload["ml_training_applicable"])
        self.assertEqual("no_retained_trainable_surface", payload["classification"])
        self.assertEqual("pass", payload["status"])
        self.assertFalse(payload["ordinary_application_registries_are_training_surfaces"])


if __name__ == "__main__":
    unittest.main()
