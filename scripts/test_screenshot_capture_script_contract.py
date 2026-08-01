from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("capture_store_screenshots.sh")


class ScreenshotCaptureScriptContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_script_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_capture_is_bound_to_origin_main_before_and_after_build(self) -> None:
        gradle_index = self.source.index("./gradlew clean assembleDebug")
        first_origin_index = self.source.index("verify_origin_main.sh")
        last_origin_index = self.source.rindex("verify_origin_main.sh")
        local_verifier_indices = [
            match.start()
            for match in re.finditer("verify_main_candidate.py", self.source)
        ]

        self.assertLess(first_origin_index, gradle_index)
        self.assertGreater(last_origin_index, gradle_index)
        self.assertGreaterEqual(len(local_verifier_indices), 2)
        self.assertLess(local_verifier_indices[0], gradle_index)
        self.assertGreater(local_verifier_indices[-1], gradle_index)

    def test_every_capture_uses_strict_sidecar_writer(self) -> None:
        self.assertIn("scripts/write_screenshot_capture_evidence.py", self.source)
        self.assertNotIn("struct.unpack", self.source)
        self.assertNotIn("path.write_bytes", self.source)
        capture_body = self.source.split("capture() {", maxsplit=1)[1].split("\n}", maxsplit=1)[0]
        self.assertIn("write_capture_evidence", capture_body)

    def test_activity_is_foreground_before_and_after_screencap(self) -> None:
        capture_body = self.source.split("capture() {", maxsplit=1)[1].split("\n}", maxsplit=1)[0]
        screencap_index = capture_body.index("exec-out screencap -p")
        foreground_indices = [
            match.start()
            for match in re.finditer("assert_app_foreground", capture_body)
        ]

        self.assertEqual(2, len(foreground_indices))
        self.assertLess(foreground_indices[0], screencap_index)
        self.assertGreater(foreground_indices[1], screencap_index)
        self.assertIn("mResumedActivity|topResumedActivity", self.source)

    def test_capture_count_matches_commands_and_session_is_finalized(self) -> None:
        expected_count_match = re.search(
            r"readonly CAPTURE_COUNT=([0-9]+)",
            self.source,
        )
        self.assertIsNotNone(expected_count_match)
        capture_commands = re.findall(
            r'^capture "[^"]+" "[A-Z0-9_]+" [0-9.]+$',
            self.source,
            flags=re.MULTILINE,
        )
        self.assertEqual(int(expected_count_match.group(1)), len(capture_commands))
        finalizer_index = self.source.index(
            "scripts/finalize_screenshot_capture_session.py"
        )
        self.assertGreater(finalizer_index, self.source.rindex(capture_commands[-1]))
        self.assertIn('--expected-count "${CAPTURE_COUNT}"', self.source)


if __name__ == "__main__":
    unittest.main()
