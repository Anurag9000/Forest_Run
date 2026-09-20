from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RESET = ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/InstrumentationStateReset.kt"
GHOST_PERSISTENCE = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceManager.kt"

class InstrumentationStateResetContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = RESET.read_text(encoding="utf-8")
        cls.ghost_persistence = GHOST_PERSISTENCE.read_text(encoding="utf-8")

    def test_save_namespaces_follow_canonical_schema_constants(self) -> None:
        self.assertIn("SaveManager.PREFS_NAME", self.source)
        self.assertIn("SaveIntegrityManager.CURRENT_SCHEMA_VERSION", self.source)
        self.assertNotIn('"forest_run_prefs_compat_v1"', self.source)

    def test_every_save_namespace_gets_a_recovery_journal_reset(self) -> None:
        self.assertIn('saveNamespaces.forEach { namespace ->', self.source)
        self.assertIn('add("forest_run_outcome_recovery_${namespace}")', self.source)

    def test_process_save_namespace_returns_to_primary_before_preloads(self) -> None:
        clear_start = self.source.index("    fun clear(context: Context) {")
        clear_region = self.source[clear_start:]
        primary = clear_region.index("SaveManager.usePrimaryPreferences()")
        ghost = clear_region.index("GhostPersistenceManager.clearMemoryForTests()")
        self.assertLess(primary, ghost)

    def test_ghost_reset_fails_closed_if_async_writes_do_not_quiesce(self) -> None:
        start = self.ghost_persistence.index("    internal fun clearMemoryForTests()")
        end = self.ghost_persistence.index("    private fun artifactStore(", start)
        region = self.ghost_persistence[start:end]
        wait = region.index("check(awaitPendingWrites(TEST_RESET_QUIESCENCE_TIMEOUT_MS))")
        clear = region.index("pendingWrites.clear()", wait)
        self.assertLess(wait, clear)
        self.assertIn("TEST_RESET_QUIESCENCE_TIMEOUT_MS = 30_000L", self.ghost_persistence)
        self.assertIn("could not quiesce pending persistence work", region)

    def test_feedback_and_ghost_state_are_also_cleared(self) -> None:
        self.assertIn("FeedbackSettings.PREFS_NAME", self.source)
        self.assertIn("GhostPersistenceManager.clearMemoryForTests()", self.source)
        self.assertIn('it.name.startsWith("ghost_run")', self.source)

if __name__ == "__main__":
    unittest.main()
