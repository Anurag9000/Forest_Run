from pathlib import Path
import subprocess
import unittest


class ConnectedValidationRunnerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.path = Path(__file__).with_name("run_connected_validation.sh")
        cls.script = cls.path.read_text(encoding="utf-8")
        cls.hardware_profile = (
            cls.path.parent.parent
            / "app"
            / "src"
            / "androidTest"
            / "java"
            / "com"
            / "anurag9000"
            / "forestrun"
            / "HardwarePerformanceProfileTest.kt"
        ).read_text(encoding="utf-8")

    def test_runner_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(self.path)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runner_is_strict_and_bounded(self) -> None:
        self.assertIn("set -euo pipefail", self.script)
        self.assertIn("READINESS_TIMEOUT_SECONDS", self.script)
        self.assertIn("while (( SECONDS < deadline ))", self.script)
        self.assertIn(
            'timeout "${READINESS_TIMEOUT_SECONDS}s" adb -s "${SERIAL}" wait-for-device',
            self.script,
        )
        self.assertIn("return 1", self.script)

    def test_runner_validates_timeout_and_external_commands_before_trap(self) -> None:
        validation_index = self.script.index(
            "FOREST_RUN_EMULATOR_READINESS_TIMEOUT_SECONDS must be a positive integer"
        )
        prerequisite_index = self.script.index("for required_command in adb timeout git")
        trap_index = self.script.index("trap dump_emulator_diagnostics EXIT")

        self.assertLess(validation_index, trap_index)
        self.assertLess(prerequisite_index, trap_index)
        self.assertIn("exit 2", self.script[:trap_index])

    def test_runner_waits_for_framework_and_provider_readiness(self) -> None:
        for required_probe in (
            "getprop sys.boot_completed",
            "pm path android",
            "cmd package list packages",
            "settings get global device_provisioned",
        ):
            self.assertIn(required_probe, self.script)

    def test_runner_parses_and_enforces_candidate_binding_flags(self) -> None:
        self.assertIn("--skip-origin-main-check)", self.script)
        self.assertIn("--candidate-sha)", self.script)
        self.assertIn('git rev-parse --verify HEAD', self.script)
        self.assertIn(
            'if [[ "${LOCAL_CANDIDATE_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]',
            self.script,
        )
        self.assertIn('bash scripts/verify_origin_main.sh "${ROOT_DIR}"', self.script)
        self.assertIn("Unknown connected-validation argument", self.script)

    def test_runner_rejects_dirty_source_before_and_after_connected_execution(self) -> None:
        initial_status = self.script.index(
            'git status --porcelain=v1 --untracked-files=all'
        )
        gradle = self.script.index("./gradlew connectedDebugAndroidTest")
        final_status = self.script.index(
            'git status --porcelain=v1 --untracked-files=all',
            initial_status + 1,
        )
        final_head = self.script.index(
            'FINAL_LOCAL_CANDIDATE_SHA="$(git rev-parse --verify HEAD)"'
        )
        self.assertLess(initial_status, gradle)
        self.assertLess(gradle, final_head)
        self.assertLess(final_head, final_status)
        self.assertIn("Connected validation requires a clean candidate worktree.", self.script)
        self.assertIn(
            "Connected validation changed or introduced non-ignored candidate files.",
            self.script,
        )

    def test_local_origin_identity_is_rechecked_after_connected_execution(self) -> None:
        gradle = self.script.index("./gradlew connectedDebugAndroidTest")
        final_origin = self.script.index("FINAL_ORIGIN_MAIN_SHA", gradle)
        self.assertLess(gradle, final_origin)
        self.assertIn("Canonical origin/main changed during connected validation.", self.script)

    def test_ci_skip_does_not_skip_local_candidate_identity(self) -> None:
        local_check = self.script.index(
            'if [[ "${LOCAL_CANDIDATE_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]'
        )
        origin_gate = self.script.index('if [[ "${SKIP_ORIGIN_MAIN_CHECK}" -eq 0 ]]')
        self.assertLess(local_check, origin_gate)

    def test_runner_preserves_connected_test_failures(self) -> None:
        gradle_command = "./gradlew connectedDebugAndroidTest"
        self.assertEqual(1, self.script.count(gradle_command))
        command_tail = self.script.split(gradle_command, maxsplit=1)[1]
        self.assertNotIn("|| true", command_tail)
        self.assertIn("trap dump_emulator_diagnostics EXIT", self.script)

    def test_emulator_gate_excludes_only_physical_device_profiles(self) -> None:
        annotation = "androidx.test.filters.LargeTest"
        self.assertIn(f'PHYSICAL_PROFILE_ANNOTATION="{annotation}"', self.script)
        self.assertIn(
            '-Pandroid.testInstrumentationRunnerArguments.notAnnotation="${PHYSICAL_PROFILE_ANNOTATION}"',
            self.script,
        )
        self.assertIn("@LargeTest", self.hardware_profile)
        self.assertIn("class HardwarePerformanceProfileTest", self.hardware_profile)
        self.assertIn("profiling window contains sustained samples", self.hardware_profile)

    def test_runner_targets_the_action_emulator_port(self) -> None:
        self.assertIn("emulator-${EMULATOR_PORT:-5554}", self.script)
        self.assertIn('export ANDROID_SERIAL="${SERIAL}"', self.script)

    def test_serial_presence_probe_uses_exact_awk_field_match(self) -> None:
        self.assertIn('$1 == serial', self.script)
        self.assertNotIn('grep -q "^${SERIAL}', self.script)


if __name__ == "__main__":
    unittest.main()
