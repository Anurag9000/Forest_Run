from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"

class GameViewRuntimeOwnershipContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = GAME_VIEW.read_text(encoding="utf-8")

    def region(self, start: str, end: str) -> str:
        begin = self.source.index(start)
        finish = self.source.index(end, begin)
        return self.source[begin:finish]

    def test_one_monitor_is_the_runtime_mutation_boundary(self) -> None:
        self.assertIn("private val runtimeStateLock = Any()", self.source)

    def test_update_and_render_are_serialized(self) -> None:
        update = self.region("    fun update(deltaTime: Float) {", "    private fun updateBounded")
        draw = self.region("    override fun draw(canvas: Canvas) {", "    private fun prepareFreshRun")
        self.assertIn("synchronized(runtimeStateLock)", update)
        self.assertIn("synchronized(runtimeStateLock)", draw)

    def test_surface_initialization_joins_runtime_owner_after_quiescence(self) -> None:
        surface = self.region(
            "    private fun initializeSurfaceWhenThreadStopped(",
            "    override fun surfaceChanged("
        )
        old_owner = surface.index("if (gameThread.isAlive && !gameThread.isRunning)")
        retry = surface.index("postDelayed(", old_owner)
        branch_return = surface.index("return", retry)
        lock = surface.index("synchronized(runtimeStateLock)", branch_return)
        self.assertLess(old_owner, retry)
        self.assertLess(retry, branch_return)
        self.assertLess(branch_return, lock)
        self.assertIn("screenWidth  = width", surface[lock:])
        self.assertIn("resumeGameThreadWhenStopped(restartToken)", surface[lock:])

    def test_touch_and_debug_reset_share_the_same_owner(self) -> None:
        init = self.region("    init {", "    override fun surfaceCreated")
        debug = self.region("    fun applyDebugLaunchIntent", "    private fun stopThread")
        self.assertIn("setOnTouchListener", init)
        self.assertIn("synchronized(runtimeStateLock)", init)
        self.assertIn("synchronized(runtimeStateLock)", debug)
        self.assertIn("prepareEncounterScenario()", debug)

    def test_accessibility_queries_and_mutations_share_runtime_owner(self) -> None:
        router = self.region("    private val accessibilityActionRouter", "    private val gameAccessibilityNodeProvider")
        snapshot = self.region("    private fun buildAccessibilitySnapshot", "    private fun accessibilityBoundsFor")
        self.assertIn("AccessibilitySemanticActionHandler", router)
        self.assertIn("synchronized(runtimeStateLock)", router)
        self.assertIn("synchronized(runtimeStateLock)", snapshot)

    def test_delayed_duck_release_reenters_runtime_owner(self) -> None:
        duck = self.region("    private fun performAccessibilityDuck()", "    private fun performAccessibilityPlantPurchase")
        post = duck.index("postDelayed(")
        lock = duck.index("synchronized(runtimeStateLock)", post)
        release = duck.index("released.invoke()", lock)
        self.assertLess(post, lock)
        self.assertLess(lock, release)

    def test_lifecycle_join_never_holds_runtime_monitor(self) -> None:
        pause = self.region("    fun pause(): Boolean {", "    fun resume() {")
        stop = self.region("    private fun stopThread(): Boolean {", "    // -----------------------------------------------------------------------\n    // Input callback wiring")
        self.assertNotIn("synchronized(runtimeStateLock)", pause)
        self.assertNotIn("synchronized(runtimeStateLock)", stop)
        self.assertIn("requestStopAndAwait()", stop)

if __name__ == "__main__":
    unittest.main()
