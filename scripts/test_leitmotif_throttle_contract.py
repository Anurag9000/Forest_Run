from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt"


class LeitmotifThrottleContractTest(unittest.TestCase):
    def test_evaluation_throttle_is_the_only_parameter_interval_gate(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")

        self.assertIn(
            "private val tempoEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)",
            source,
        )
        self.assertIn(
            "private val bloomEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)",
            source,
        )
        self.assertIn("tempoEvaluationThrottle.tryAcquire(nowNs)", source)
        self.assertIn(
            "bloomEvaluationThrottle.tryAcquire(nowNs, force = conversionChanged)",
            source,
        )
        self.assertNotIn("lastParameterUpdateNs", source)
        self.assertNotIn("nowNs -", source)
        self.assertNotIn("System.nanoTime() -", source)


if __name__ == "__main__":
    unittest.main()
