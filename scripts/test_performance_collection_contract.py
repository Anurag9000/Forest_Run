from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("collect_performance_profiles.sh")


class PerformanceCollectionContractTest(unittest.TestCase):
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

    def test_candidate_is_verified_before_any_device_build(self) -> None:
        origin_index = self.source.index("verify_origin_main.sh")
        local_index = self.source.index("verify_main_candidate.py")
        gradle_index = self.source.index("./gradlew connectedDebugAndroidTest")

        self.assertLess(origin_index, gradle_index)
        self.assertLess(local_index, gradle_index)
        self.assertIn('if [[ "$CANDIDATE_SHA" != "$ORIGIN_SHA" ]]', self.source)

    def test_candidate_and_origin_are_rechecked_after_capture(self) -> None:
        gradle_index = self.source.index("./gradlew connectedDebugAndroidTest")
        final_local_index = self.source.rindex("verify_main_candidate.py")
        final_origin_index = self.source.rindex("verify_origin_main.sh")

        self.assertGreater(final_local_index, gradle_index)
        self.assertGreater(final_origin_index, gradle_index)
        self.assertIn('--expected-sha "$CANDIDATE_SHA"', self.source)
        self.assertIn('"$FINAL_ORIGIN_SHA" != "$CANDIDATE_SHA"', self.source)

    def test_evidence_metadata_records_both_candidate_identities(self) -> None:
        self.assertIn('echo "candidate_sha=$CANDIDATE_SHA"', self.source)
        self.assertIn('echo "origin_main_sha=$ORIGIN_SHA"', self.source)

    def test_legacy_tracked_only_cleanliness_check_cannot_return(self) -> None:
        self.assertNotIn("git diff --quiet", self.source)
        self.assertNotIn("git diff --cached --quiet", self.source)


if __name__ == "__main__":
    unittest.main()
