from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
SHUTDOWN_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/engine/GameThreadShutdownTest.kt"


class GameViewThreadHandoffContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = GAME_VIEW.read_text(encoding="utf-8")
        cls.shutdown_test = SHUTDOWN_TEST.read_text(encoding="utf-8")

    def test_existing_shutdown_test_proves_timeout_can_leave_old_owner_alive(self) -> None:
        self.assertIn("uncooperative update cannot make shutdown wait past its bound", self.shutdown_test)
        self.assertIn("assertFalse(thread.requestStopAndAwait(timeoutMs = 40L))", self.shutdown_test)
        self.assertIn("assertTrue(thread.isAlive)", self.shutdown_test)

    def test_pause_cancels_deferred_restart_before_stopping_owner(self) -> None:
        start = self.source.index("    fun pause() {")
        end = self.source.index("    fun resume() {", start)
        pause = self.source[start:end]
        self.assertLess(pause.index("gameThreadRestartGate.cancel()"), pause.index("stopThread()"))

    def test_resume_uses_owned_deferred_handoff_instead_of_immediate_replacement(self) -> None:
        start = self.source.index("    fun resume() {")
        end = self.source.index("    fun applyDebugLaunchIntent", start)
        resume_region = self.source[start:end]
        resume_only = resume_region[:resume_region.index("    private fun resumeGameThreadWhenStopped")]
        self.assertIn("val restartToken = gameThreadRestartGate.begin()", resume_only)
        self.assertIn("resumeGameThreadWhenStopped(restartToken)", resume_only)
        self.assertNotIn("gameThread = GameThread(holder, this)", resume_only)

    def test_replacement_occurs_only_after_live_owner_branch_returns(self) -> None:
        start = self.source.index("    private fun resumeGameThreadWhenStopped")
        end = self.source.index("    fun applyDebugLaunchIntent", start)
        handoff = self.source[start:end]
        ownership = handoff.index("if (!gameThreadRestartGate.isCurrent(restartToken)) return")
        alive = handoff.index("if (gameThread.isAlive)")
        stop = handoff.index("gameThread.requestStop()", alive)
        retry = handoff.index("postDelayed(", stop)
        branch_return = handoff.index("return", retry)
        replacement = handoff.index("gameThread = GameThread(holder, this)")
        start_thread = handoff.index("gameThread.start()", replacement)
        self.assertLess(ownership, alive)
        self.assertLess(alive, stop)
        self.assertLess(stop, retry)
        self.assertLess(retry, branch_return)
        self.assertLess(branch_return, replacement)
        self.assertLess(replacement, start_thread)

    def test_retry_is_bounded_by_latest_resume_ownership(self) -> None:
        self.assertIn("private val gameThreadRestartGate = LatestRequestGate()", self.source)
        self.assertIn("GAME_THREAD_RESTART_RETRY_MS = 16L", self.source)


if __name__ == "__main__":
    unittest.main()
