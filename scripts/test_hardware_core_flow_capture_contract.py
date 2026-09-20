from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CAPTURE = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/HardwareCoreFlowCaptureTest.kt"

class HardwareCoreFlowCaptureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = CAPTURE.read_text(encoding="utf-8")
        start = cls.source.index("    private fun prepareScenario(")
        end = cls.source.index("    private fun captureAtOffsets(", start)
        cls.prepare = cls.source[start:end]

    def test_capture_scenario_uses_production_locked_debug_boundary(self) -> None:
        self.assertIn("gameView.applyDebugLaunchIntent(launchIntent)", self.prepare)
        self.assertIn("RunMode.SCREENSHOT_CAPTURE.name", self.prepare)
        self.assertIn("MainActivity.EXTRA_DEBUG_SCENARIO", self.prepare)
        self.assertIn("MainActivity.EXTRA_RUN_MODE", self.prepare)

    def test_capture_never_reflectively_mutates_runtime_scenario_owners(self) -> None:
        forbidden = (
            'setPrivateField(gameView, "runMode"',
            'setPrivateField(director, "selectedIndex"',
            'invokePrivate(gameView, "prepareEncounterScenario")',
        )
        for marker in forbidden:
            self.assertNotIn(marker, self.source)

    def test_capture_waits_for_exact_deterministic_mode(self) -> None:
        self.assertIn(
            'getPrivateField(gameView, "runMode") == RunMode.SCREENSHOT_CAPTURE',
            self.prepare,
        )
        self.assertIn(
            'getPrivateField(gameView, "runState") == RunState.PLAYING',
            self.prepare,
        )

if __name__ == "__main__":
    unittest.main()
