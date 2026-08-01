from __future__ import annotations

import re
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parent.parent / ".github/workflows/android-validation.yml"


class AndroidValidationWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_workflow_is_main_only_and_read_only(self) -> None:
        self.assertRegex(
            self.source,
            r"on:\s*\n\s*push:\s*\n\s*branches:\s*\n\s*- main",
        )
        self.assertIn("workflow_dispatch:", self.source)
        self.assertRegex(
            self.source,
            r"permissions:\s*\n\s*contents: read",
        )
        self.assertNotRegex(self.source, r"contents:\s*write")
        self.assertNotRegex(self.source, r"\bgit\s+push\b")

    def test_both_jobs_checkout_the_exact_event_sha_without_credentials(self) -> None:
        self.assertEqual(2, self.source.count("uses: actions/checkout@v4"))
        self.assertEqual(2, self.source.count("ref: ${{ github.sha }}"))
        self.assertEqual(2, self.source.count("fetch-depth: 1"))
        self.assertEqual(2, self.source.count("persist-credentials: false"))

    def test_host_job_runs_tooling_tests_and_real_asset_preflight(self) -> None:
        self.assertIn(
            "python3 -m unittest discover -s scripts -p 'test_*.py'",
            self.source,
        )
        self.assertIn(
            "python3 scripts/verify_release_source_assets.py --root .",
            self.source,
        )
        for required_script in (
            "collect_performance_profiles.sh",
            "run_connected_validation.sh",
            "verify_main_candidate.py",
            "verify_origin_main.sh",
            "verify_release_source_assets.py",
            "release_artifact_verifier.py",
            "capture_store_screenshots.sh",
            "screenshot_capture_evidence.py",
            "verify_curated_screenshot_set.py",
        ):
            self.assertIn(f"test -f scripts/{required_script}", self.source)

    def test_host_job_compiles_tests_lints_and_packages_all_variants(self) -> None:
        for task in (
            "compileDebugKotlin",
            "compileDebugUnitTestKotlin",
            "compileReleaseKotlin",
            "compileDebugAndroidTestKotlin",
            "testDebugUnitTest",
            "lintDebug",
            "lintRelease",
            "assembleDebug",
            "assembleDebugAndroidTest",
            "bundleRelease",
        ):
            self.assertIn(task, self.source)
        self.assertIn("mapping='app/build/outputs/mapping/release/mapping.txt'", self.source)
        self.assertGreaterEqual(self.source.count("git diff --exit-code"), 2)
        self.assertGreaterEqual(self.source.count("git diff --cached --exit-code"), 2)

    def test_connected_job_uses_hardened_runner_on_api_35(self) -> None:
        self.assertIn("uses: reactivecircus/android-emulator-runner@v2", self.source)
        self.assertIn("api-level: 35", self.source)
        self.assertIn("script: bash scripts/run_connected_validation.sh", self.source)
        self.assertIn("emulator-boot-timeout: 900", self.source)
        self.assertIn("-no-snapshot", self.source)
        self.assertIn("-noaudio", self.source)

    def test_workflow_has_bounded_jobs_and_branch_concurrency(self) -> None:
        self.assertIn("cancel-in-progress: true", self.source)
        self.assertIn("timeout-minutes: 60", self.source)
        self.assertIn("timeout-minutes: 75", self.source)
        self.assertIn("${{ github.workflow }}-${{ github.ref }}", self.source)


if __name__ == "__main__":
    unittest.main()
