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
        start = self.source.index("    fun pause(): Boolean {")
        end = self.source.index("    fun resume() {", start)
        pause = self.source[start:end]
        self.assertLess(pause.index("lifecyclePaused = true"), pause.index("gameThreadRestartGate.cancel()"))
        self.assertLess(pause.index("gameThreadRestartGate.cancel()"), pause.index("stopThread()"))

    def test_resume_uses_owned_deferred_handoff_instead_of_immediate_replacement(self) -> None:
        start = self.source.index("    fun resume() {")
        end = self.source.index("    fun applyDebugLaunchIntent", start)
        resume_region = self.source[start:end]
        resume_only = resume_region[:resume_region.index("    private fun resumeGameThreadWhenStopped")]
        self.assertIn("lifecyclePaused = false", resume_only)
        self.assertIn("val restartToken = gameThreadRestartGate.begin()", resume_only)
        self.assertIn("resumeGameThreadWhenStopped(restartToken)", resume_only)
        self.assertNotIn("gameThread = GameThread(holder, this)", resume_only)

    def test_replacement_occurs_only_after_live_owner_branch_returns(self) -> None:
        start = self.source.index("    private fun resumeGameThreadWhenStopped")
        end = self.source.index("    fun applyDebugLaunchIntent", start)
        handoff = self.source[start:end]
        ownership = handoff.index("if (!gameThreadRestartGate.isCurrent(restartToken) || lifecyclePaused) return")
        alive = handoff.index("if (gameThread.isAlive)")
        healthy_return = handoff.index("if (gameThread.isRunning) return", alive)
        stop = handoff.index("gameThread.requestStop()", healthy_return)
        retry = handoff.index("postDelayed(", stop)
        branch_return = handoff.index("return", retry)
        replacement = handoff.index("gameThread = GameThread(holder, this)")
        start_thread = handoff.index("gameThread.start()", replacement)
        self.assertLess(ownership, alive)
        self.assertLess(alive, healthy_return)
        self.assertLess(healthy_return, stop)
        self.assertLess(stop, retry)
        self.assertLess(retry, branch_return)
        self.assertLess(branch_return, replacement)
        self.assertLess(replacement, start_thread)

    def test_live_healthy_owner_is_idempotent_under_duplicate_resume_or_surface_callback(self) -> None:
        start = self.source.index("    private fun resumeGameThreadWhenStopped")
        end = self.source.index("    fun applyDebugLaunchIntent", start)
        handoff = self.source[start:end]
        alive = handoff.index("if (gameThread.isAlive)")
        healthy = handoff.index("if (gameThread.isRunning) return", alive)
        stop = handoff.index("gameThread.requestStop()", healthy)
        self.assertLess(alive, healthy)
        self.assertLess(healthy, stop)

    def test_pause_reports_quiescence_for_fail_closed_profile_reset(self) -> None:
        start = self.source.index("    fun pause(): Boolean {")
        end = self.source.index("    fun resume() {", start)
        pause = self.source[start:end]
        self.assertIn("val threadStopped = stopThread()", pause)
        self.assertIn("return threadStopped", pause)

        profile = (ROOT / "app/src/androidTest/java/com/anurag9000/forestrun/HardwarePerformanceProfileTest.kt").read_text(encoding="utf-8")
        pause_index = profile.index("gameView.pause()")
        reset_index = profile.index("FramePerformanceTelemetry.resetStoppedSession()")
        self.assertLess(pause_index, reset_index)
        surrounding = profile[max(0, pause_index - 160):reset_index]
        self.assertIn("assertTrue(", surrounding)
        self.assertIn("warmup render producer must stop before telemetry reset", surrounding)

    def test_pause_never_snapshots_mutable_game_state_after_shutdown_timeout(self) -> None:
        start = self.source.index("    fun pause(): Boolean {")
        end = self.source.index("    fun resume() {", start)
        pause = self.source[start:end]
        stop = pause.index("val threadStopped = stopThread()")
        guarded_save = pause.index("if (threadStopped && ::gameState.isInitialized && runMode.persistsProgress)", stop)
        save = pause.index("gameState.save()", guarded_save)
        timeout_branch = pause.index("else if (!threadStopped", save)
        self.assertLess(stop, guarded_save)
        self.assertLess(guarded_save, save)
        self.assertLess(save, timeout_branch)
        self.assertIn("Skipping pause persistence while GameThread is still active", pause)

    def test_surface_recreation_never_reenables_a_stopping_live_thread(self) -> None:
        created_start = self.source.index("    override fun surfaceCreated(")
        init_start = self.source.index("    private fun initializeSurfaceWhenThreadStopped(", created_start)
        changed_start = self.source.index("    override fun surfaceChanged(", init_start)
        created = self.source[created_start:init_start]
        initializer = self.source[init_start:changed_start]

        self.assertIn("gameThreadRestartGate.begin()", created)
        self.assertIn("initializeSurfaceWhenThreadStopped(holder, restartToken)", created)

        ownership = initializer.index("if (!gameThreadRestartGate.isCurrent(restartToken) || lifecyclePaused) return")
        old_owner = initializer.index("if (gameThread.isAlive && !gameThread.isRunning)")
        retry = initializer.index("postDelayed(", old_owner)
        old_owner_return = initializer.index("return", retry)
        runtime_lock = initializer.index("synchronized(runtimeStateLock)", old_owner_return)
        initialize_dimensions = initializer.index("screenWidth  = width", runtime_lock)
        resume = initializer.index("resumeGameThreadWhenStopped(restartToken)", initialize_dimensions)

        self.assertLess(ownership, old_owner)
        self.assertLess(old_owner, retry)
        self.assertLess(retry, old_owner_return)
        self.assertLess(old_owner_return, runtime_lock)
        self.assertLess(runtime_lock, initialize_dimensions)
        self.assertLess(initialize_dimensions, resume)
        self.assertNotIn("gameThread.isRunning = true", initializer)

        destroyed_start = self.source.index("    override fun surfaceDestroyed(")
        pause_start = self.source.index("    fun pause(): Boolean {", destroyed_start)
        destroyed = self.source[destroyed_start:pause_start]
        self.assertLess(destroyed.index("gameThreadRestartGate.cancel()"), destroyed.index("stopThread()"))

    def test_retry_is_bounded_by_latest_resume_ownership(self) -> None:
        self.assertIn("private val gameThreadRestartGate = LatestRequestGate()", self.source)
        self.assertIn("GAME_THREAD_RESTART_RETRY_MS = 16L", self.source)


if __name__ == "__main__":
    unittest.main()
