from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"


class DebugLaunchReleaseGuardContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.main = MAIN_ACTIVITY.read_text(encoding="utf-8")
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        start = cls.main.index("    private fun applyDebugLaunchWhenReady(")
        end = cls.main.index("    private fun configureSafeAreaInsets()", start)
        cls.launch = cls.main[start:end]

    def test_activity_rejects_debug_launch_before_readiness_or_surface_wait(self) -> None:
        no_request = self.launch.index("if (scenarioName.isNullOrBlank() && !autoStart) return")
        release_guard = self.launch.index("if (!isDebuggableRuntime())")
        not_debuggable = self.launch.index("reason=not_debuggable")
        scenario_parse = self.launch.index("val scenario = scenarioName?.let")
        surface_wait = self.launch.index("val surfaceReady =")
        game_view_dispatch = self.launch.index("gameView.applyDebugLaunchIntent(launchIntent)")
        ready_marker = self.launch.index("$DEBUG_SCENARIO_READY_PREFIX")

        self.assertLess(no_request, release_guard)
        self.assertLess(release_guard, not_debuggable)
        self.assertLess(not_debuggable, scenario_parse)
        self.assertLess(release_guard, surface_wait)
        self.assertLess(release_guard, game_view_dispatch)
        self.assertLess(release_guard, ready_marker)

    def test_non_debuggable_rejection_has_no_ready_fallthrough(self) -> None:
        guard_start = self.launch.index("if (!isDebuggableRuntime())")
        scenario_parse = self.launch.index("val scenario = scenarioName?.let", guard_start)
        guard = self.launch[guard_start:scenario_parse]
        self.assertIn("FOREST_RUN_SCENARIO_REJECTED", guard)
        self.assertIn("reason=not_debuggable", guard)
        self.assertIn("return", guard)
        self.assertNotIn("DEBUG_SCENARIO_READY_PREFIX", guard)

    def test_ready_marker_requires_exact_applied_state_match(self) -> None:
        dispatch = self.launch.index("gameView.applyDebugLaunchIntent(launchIntent)")
        post_dispatch_match = self.launch.index(
            "if (!gameView.matchesDebugLaunch(scenario, effectiveMode))",
            dispatch + 1,
        )
        apply_timeout = self.launch.index("reason=apply_timeout", post_dispatch_match)
        ready_marker = self.launch.index("$DEBUG_SCENARIO_READY_PREFIX", apply_timeout)
        self.assertLess(dispatch, post_dispatch_match)
        self.assertLess(post_dispatch_match, apply_timeout)
        self.assertLess(apply_timeout, ready_marker)

    def test_game_view_match_is_locked_and_exact(self) -> None:
        start = self.game_view.index("    internal fun matchesDebugLaunch(")
        end = self.game_view.index("    private fun stopThread()", start)
        match = self.game_view[start:end]
        self.assertIn("synchronized(runtimeStateLock)", match)
        self.assertIn("appState != AppGameState.PLAYING", match)
        self.assertIn("runState != RunState.PLAYING", match)
        self.assertIn("runMode != mode", match)
        self.assertIn("encounterDirector?.activeScenario == scenario", match)

    def test_new_single_task_intent_cancels_any_older_deferred_scenario(self) -> None:
        start = self.main.index("    override fun onNewIntent(intent: Intent)")
        end = self.main.index("    override fun onWindowFocusChanged", start)
        region = self.main[start:end]
        token = region.index("debugLaunchGate.begin()")
        cancel = region.index("gameView.cancelPendingDebugLaunchIntent()", token)
        post = region.index("gameView.post { applyDebugLaunchWhenReady", cancel)
        self.assertLess(token, cancel)
        self.assertLess(cancel, post)

        view_start = self.game_view.index("    internal fun cancelPendingDebugLaunchIntent()")
        view_end = self.game_view.index("    fun applyDebugLaunchIntent", view_start)
        clear = self.game_view[view_start:view_end]
        self.assertIn("synchronized(runtimeStateLock)", clear)
        self.assertIn("pendingDebugLaunchIntent = null", clear)

    def test_game_view_has_one_latest_pending_launch_owner(self) -> None:
        start = self.game_view.index("    fun applyDebugLaunchIntent(intent: Intent?)")
        end = self.game_view.index("    internal fun matchesDebugLaunch(", start)
        dispatch = self.game_view[start:end]
        deferred = dispatch.index("pendingDebugLaunchIntent = Intent(intent)")
        deferred_return = dispatch.index("return", deferred)
        clear = dispatch.index("pendingDebugLaunchIntent = null", deferred_return)
        scenario = dispatch.index("if (!scenarioName.isNullOrBlank())", clear)
        self.assertLess(deferred, deferred_return)
        self.assertLess(deferred_return, clear)
        self.assertLess(clear, scenario)
        self.assertNotIn("postDelayed({ applyDebugLaunchIntent(intent)", dispatch)

    def test_game_view_keeps_second_defense_at_dispatch_boundary(self) -> None:
        start = self.game_view.index("    fun applyDebugLaunchIntent(intent: Intent?)")
        end = self.game_view.index("    private fun prepareEncounterScenario()", start)
        dispatch = self.game_view[start:end]
        self.assertIn("if (!debugToolsEnabled || intent == null) return", dispatch)


if __name__ == "__main__":
    unittest.main()
