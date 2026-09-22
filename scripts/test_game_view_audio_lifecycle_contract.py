from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
AUDIO = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt"


class GameViewAudioLifecycleContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.view = VIEW.read_text(encoding="utf-8")
        cls.audio = AUDIO.read_text(encoding="utf-8")
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

    def test_repeated_manager_init_would_request_menu_music(self) -> None:
        start = self.audio.index("    fun init(context: Context) {")
        end = self.audio.index("    fun setAudioEnabled(", start)
        init = self.audio[start:end]
        self.assertIn("if (enabled) transitionTo(MusicState.MENU)", init)


if __name__ == "__main__":
    unittest.main()
