from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = (ROOT / ".github/workflows/android-validation.yml").read_text(
    encoding="utf-8"
)


class AndroidValidationActionVersionsTest(unittest.TestCase):
    def test_maintained_node_runtime_actions_are_used(self) -> None:
        self.assertEqual(2, WORKFLOW.count("uses: actions/checkout@v6"))
        self.assertEqual(2, WORKFLOW.count("uses: actions/setup-java@v5"))
        self.assertEqual(2, WORKFLOW.count("uses: gradle/actions/setup-gradle@v6"))
        self.assertEqual(1, WORKFLOW.count("uses: gradle/actions/wrapper-validation@v6"))
        for obsolete in (
            "actions/checkout@v4",
            "actions/checkout@v5",
            "actions/setup-java@v4",
            "gradle/actions/setup-gradle@v4",
            "gradle/actions/setup-gradle@v5",
            "gradle/actions/wrapper-validation@v4",
            "gradle/actions/wrapper-validation@v5",
        ):
            self.assertNotIn(obsolete, WORKFLOW)

    def test_gradle_cache_is_single_owner_and_open_source(self) -> None:
        self.assertEqual(2, WORKFLOW.count("cache-provider: basic"))
        self.assertNotRegex(WORKFLOW, re.compile(r"^\s+cache:\s+gradle\s*$", re.MULTILINE))

    def test_validation_remains_read_only_and_exact_sha(self) -> None:
        self.assertIn("permissions:\n  contents: read", WORKFLOW)
        self.assertNotIn("contents: write", WORKFLOW)
        self.assertEqual(2, WORKFLOW.count("ref: ${{ github.sha }}"))
        self.assertEqual(2, WORKFLOW.count("persist-credentials: false"))
        self.assertNotRegex(WORKFLOW, re.compile(r"\bgit\s+push\b"))

    def test_android_and_emulator_owners_remain_explicit(self) -> None:
        self.assertEqual(2, WORKFLOW.count("uses: android-actions/setup-android@v3"))
        self.assertEqual(
            1,
            WORKFLOW.count("uses: reactivecircus/android-emulator-runner@v2"),
        )
        self.assertIn("api-level: 35", WORKFLOW)
        self.assertEqual(2, WORKFLOW.count("platforms;android-36"))


if __name__ == "__main__":
    unittest.main()
