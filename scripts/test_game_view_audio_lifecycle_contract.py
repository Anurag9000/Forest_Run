from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
AUDIO = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt"
SFX = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/SfxManager.kt"
FEEDBACK = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/FeedbackSettings.kt"


class GameViewAudioLifecycleContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.view = VIEW.read_text(encoding="utf-8")
        cls.audio = AUDIO.read_text(encoding="utf-8")
        cls.sfx = SFX.read_text(encoding="utf-8")
        cls.feedback = FEEDBACK.read_text(encoding="utf-8")
        start = cls.view.index("    private fun initializeSurfaceWhenThreadStopped(")
        end = cls.view.index("    override fun surfaceChanged(", start)
        cls.surface_initializer = cls.view[start:end]

    def test_audio_bootstrap_is_once_per_game_view(self) -> None:
        self.assertIn("private var audioBootstrapCompleted = false", self.view)
        block = self.surface_initializer
        gate = block.index("if (!audioBootstrapCompleted) {")
        initialize = block.index("LeitmotifManager.init(context)", gate)
        sfx = block.index("SfxManager.init(context)", initialize)
        selected = block.index("LeitmotifManager.playRunStart()", sfx)
        completed = block.index("audioBootstrapCompleted = true", selected)
        self.assertLess(gate, initialize)
        self.assertLess(initialize, sfx)
        self.assertLess(sfx, selected)
        self.assertLess(selected, completed)
        self.assertEqual(self.view.count("LeitmotifManager.init(context)"), 1)
        self.assertEqual(self.view.count("SfxManager.init(context)"), 1)

    def test_cold_start_selects_current_state_instead_of_always_run_one(self) -> None:
        block = self.surface_initializer
        self.assertIn("appState == AppGameState.MENU", block)
        self.assertIn("runState == RunState.GAME_OVER", block)
        self.assertIn("gameState.isBloomActive", block)
        self.assertIn("LeitmotifManager.playRest()", block)
        self.assertIn("LeitmotifManager.playBloom()", block)
        self.assertIn("LeitmotifManager.playRunStart()", block)

    def test_resume_only_resumes_music_and_does_not_reselect_it(self) -> None:
        start = self.view.index("    fun resume() {")
        end = self.view.index("    private fun resumeGameThreadWhenStopped(", start)
        resume = self.view[start:end]
        self.assertIn("LeitmotifManager.resume()", resume)
        self.assertIn("initializeSurfaceWhenThreadStopped(holder, restartToken)", resume)
        self.assertNotIn("LeitmotifManager.init(", resume)
        self.assertNotIn("LeitmotifManager.playRunStart()", resume)
        self.assertNotIn("LeitmotifManager.transitionTo(", resume)


    def test_pause_stops_transient_sfx_and_haptics_with_music(self) -> None:
        start = self.view.index("    fun pause(): Boolean {")
        end = self.view.index("    fun resume() {", start)
        pause = self.view[start:end]
        music = pause.index("LeitmotifManager.pause()")
        sfx = pause.index("SfxManager.stopActivePlayback()", music)
        haptic = pause.index("HapticManager.cancel()", sfx)
        self.assertLess(music, sfx)
        self.assertLess(sfx, haptic)

    def test_resume_does_not_revive_old_transient_feedback(self) -> None:
        start = self.view.index("    fun resume() {")
        end = self.view.index("    private fun resumeGameThreadWhenStopped(", start)
        resume = self.view[start:end]
        self.assertIn("LeitmotifManager.resume()", resume)
        self.assertNotIn("SfxManager.", resume)
        self.assertNotIn("HapticManager.", resume)

    def test_audio_disable_immediately_stops_existing_sfx(self) -> None:
        start = self.feedback.index("    fun setAudioEnabled(")
        end = self.feedback.index("    @Synchronized\n    fun setHapticsEnabled(", start)
        setter = self.feedback[start:end]
        self.assertIn("if (!enabled) SfxManager.stopActivePlayback()", setter)
        self.assertIn("LeitmotifManager.setAudioEnabled(enabled)", setter)

    def test_sfx_stop_boundary_drains_bounded_stream_ledger(self) -> None:
        self.assertIn("private val activeStreams = SoundStreamLedger(MAX_STREAMS * 4)", self.sfx)
        start = self.sfx.index("    fun stopActivePlayback() {")
        end = self.sfx.index("    fun playJump()", start)
        stop = self.sfx[start:end]
        self.assertIn("activeStreams.drain().forEach", stop)
        self.assertIn("activePool?.stop(streamId)", stop)
        self.assertNotIn("autoResume", self.sfx)

    def test_repeated_manager_init_would_request_menu_music(self) -> None:
        start = self.audio.index("    fun init(context: Context) {")
        end = self.audio.index("    fun setAudioEnabled(", start)
        init = self.audio[start:end]
        self.assertIn("if (enabled) transitionTo(MusicState.MENU)", init)


if __name__ == "__main__":
    unittest.main()
