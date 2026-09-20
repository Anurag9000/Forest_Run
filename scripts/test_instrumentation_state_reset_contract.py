from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RESET = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/InstrumentationStateReset.kt"

class InstrumentationStateResetContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = RESET.read_text(encoding="utf-8")

    def test_save_namespaces_follow_canonical_schema_constants(self) -> None:
        self.assertIn("SaveManager.PREFS_NAME", self.source)
        self.assertIn("SaveIntegrityManager.CURRENT_SCHEMA_VERSION", self.source)
        self.assertNotIn('"forest_run_prefs_compat_v1"', self.source)

    def test_every_save_namespace_gets_a_recovery_journal_reset(self) -> None:
        self.assertIn('saveNamespaces.forEach { namespace ->', self.source)
        self.assertIn('add("forest_run_outcome_recovery_${namespace}")', self.source)

    def test_feedback_and_ghost_state_are_also_cleared(self) -> None:
        self.assertIn("FeedbackSettings.PREFS_NAME", self.source)
        self.assertIn("GhostPersistenceManager.clearMemoryForTests()", self.source)
        self.assertIn('it.name.startsWith("ghost_run")', self.source)

if __name__ == "__main__":
    unittest.main()
