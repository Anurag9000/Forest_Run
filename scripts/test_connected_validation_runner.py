from pathlib import Path
import unittest


class ConnectedValidationRunnerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.script = (
            Path(__file__).with_name("run_connected_validation.sh")
            .read_text(encoding="utf-8")
        )

    def test_runner_is_strict_and_bounded(self) -> None:
        self.assertIn("set -euo pipefail", self.script)
        self.assertIn("READINESS_TIMEOUT_SECONDS", self.script)
        self.assertIn("while (( SECONDS < deadline ))", self.script)
        self.assertIn("return 1", self.script)

    def test_runner_waits_for_framework_and_provider_readiness(self) -> None:
        for required_probe in (
            "getprop sys.boot_completed",
            "pm path android",
            "cmd package list packages",
            "settings get global device_provisioned",
        ):
            self.assertIn(required_probe, self.script)

    def test_runner_preserves_connected_test_failures(self) -> None:
        gradle_command = "./gradlew connectedDebugAndroidTest"
        self.assertEqual(1, self.script.count(gradle_command))
        command_tail = self.script.split(gradle_command, maxsplit=1)[1]
        self.assertNotIn("|| true", command_tail)
        self.assertIn("trap dump_emulator_diagnostics EXIT", self.script)

    def test_runner_targets_the_action_emulator_port(self) -> None:
        self.assertIn("emulator-${EMULATOR_PORT:-5554}", self.script)
        self.assertIn('export ANDROID_SERIAL="${SERIAL}"', self.script)


if __name__ == "__main__":
    unittest.main()
