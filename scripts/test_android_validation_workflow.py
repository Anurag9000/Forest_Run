from pathlib import Path
import unittest


class AndroidValidationWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = (
            Path(__file__).parents[1]
            / ".github"
            / "workflows"
            / "android-validation.yml"
        ).read_text(encoding="utf-8")

    def test_workflow_validates_main_and_manual_candidates_only(self) -> None:
        self.assertIn("push:\n    branches:\n      - main", self.workflow)
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertNotIn("pull_request:", self.workflow)
        self.assertNotIn("github.event.pull_request", self.workflow)

    def test_workflow_is_read_only_and_checks_out_exact_sha(self) -> None:
        self.assertIn("permissions:\n  contents: read", self.workflow)
        self.assertNotIn("contents: write", self.workflow)
        self.assertNotIn("git push", self.workflow)
        self.assertEqual(2, self.workflow.count("ref: ${{ github.sha }}"))
        self.assertEqual(2, self.workflow.count("persist-credentials: false"))

    def test_connected_job_uses_hardened_readiness_runner(self) -> None:
        for required_contract in (
            "Enable KVM group permissions",
            "emulator-boot-timeout: 900",
            "disable-spellchecker: true",
            "script: bash scripts/run_connected_validation.sh",
        ):
            self.assertIn(required_contract, self.workflow)
        self.assertNotIn(
            "script: ./gradlew connectedDebugAndroidTest",
            self.workflow,
        )

    def test_host_job_runs_complete_release_gate(self) -> None:
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
            self.assertIn(task, self.workflow)


if __name__ == "__main__":
    unittest.main()
