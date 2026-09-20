from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/MainActivityInstrumentedTest.kt"

class InstrumentationRuntimeMutationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = TEST.read_text(encoding="utf-8")
        helper_start = cls.source.index("    private fun mutateStopped(")
        helper_end = cls.source.index("    private fun tapLogical(", helper_start)
        cls.helper = cls.source[helper_start:helper_end]

    def test_quiescence_helper_requires_successful_stop_and_always_resumes(self) -> None:
        self.assertIn("gameView.pause()", self.helper)
        self.assertIn("assertTrue(", self.helper)
        self.assertIn("try {", self.helper)
        self.assertIn("finally {", self.helper)
        self.assertIn("gameView.resume()", self.helper)

    def test_live_owner_mutations_are_not_direct_activity_callbacks(self) -> None:
        self.assertNotIn(
            'scenario.onActivity {\n                val gameState = getPrivateField(gameView, "gameState")',
            self.source,
        )
        self.assertNotIn(
            'scenario.onActivity {\n                val entityManager = getPrivateField(gameView, "entityManager")',
            self.source,
        )

    def test_each_high_risk_mutation_is_inside_a_quiesced_block(self) -> None:
        markers = (
            "gameState.collectSeed()",
            "entityManager.debugSpawnAt(",
            "entityManager.reset()",
            'setPrivateField(gameState, "distanceMetres"',
        )
        for marker in markers:
            search_from = 0
            while True:
                index = self.source.find(marker, search_from)
                if index < 0:
                    break
                block_start = self.source.rfind("mutateStopped(gameView) {", 0, index)
                next_close = self.source.find("\n            }", block_start)
                self.assertGreaterEqual(block_start, 0, marker)
                self.assertGreater(next_close, index, marker)
                search_from = index + len(marker)

if __name__ == "__main__":
    unittest.main()
