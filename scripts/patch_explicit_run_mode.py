#!/usr/bin/env python3
"""Route deterministic side-effect policy through RunMode."""

from pathlib import Path

GAME_VIEW = Path("app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt")
ENTITY_MANAGER = Path("app/src/main/java/com/anurag9000/forestrun/engine/EntityManager.kt")
HARDWARE_CAPTURE = Path("app/src/androidTest/java/com/anurag9000/forestrun/HardwareCoreFlowCaptureTest.kt")
HARDWARE_PROFILE = Path("app/src/androidTest/java/com/anurag9000/forestrun/HardwarePerformanceProfileTest.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_game_view() -> None:
    text = GAME_VIEW.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "    /** Top-level lifecycle state — MENU, GARDEN, PLAYING, BLOOM, REST. */\n",
        "    /** Active screen state; Bloom and rest/death have separate owners. */\n",
        "app-state comment",
    )
    text = replace_once(
        text,
        '''    private var runState: RunState = RunState.PLAYING
    private val runResetManager    = RunResetManager()
''',
        '''    private var runState: RunState = RunState.PLAYING
    private val runResetManager    = RunResetManager()

    @Volatile
    internal var runMode: RunMode = RunMode.NORMAL
        private set
''',
        "run-mode field",
    )
    text = replace_once(
        text,
        "if (::gameState.isInitialized && encounterDirector?.isScenarioActive != true) {",
        "if (::gameState.isInitialized && runMode.persistsProgress) {",
        "pause persistence gate",
    )
    text = replace_once(
        text,
        '''        val scenarioName = intent.getStringExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_SCENARIO)
        val autoStart = intent.getBooleanExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_AUTOSTART, false)
''',
        '''        val scenarioName = intent.getStringExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_SCENARIO)
        val requestedRunMode = intent.getStringExtra(com.anurag9000.forestrun.MainActivity.EXTRA_RUN_MODE)
        val autoStart = intent.getBooleanExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_AUTOSTART, false)
''',
        "intent mode parsing",
    )
    text = replace_once(
        text,
        '''            val director = encounterDirector ?: return
            val scenario = EncounterScenario.entries.firstOrNull { it.name == scenarioName } ?: return
            debugScenarioVisualsEnabled = false
''',
        '''            val director = encounterDirector ?: return
            val scenario = EncounterScenario.entries.firstOrNull { it.name == scenarioName } ?: return
            runMode = RunMode.forScenario(requestedRunMode)
            debugScenarioVisualsEnabled = false
''',
        "scenario run-mode assignment",
    )
    text = replace_once(
        text,
        '''        if (autoStart) {
            debugScenarioVisualsEnabled = false
''',
        '''        if (autoStart) {
            runMode = RunMode.NORMAL
            debugScenarioVisualsEnabled = false
''',
        "ordinary autostart mode",
    )
    text = replace_once(
        text,
        "entityManager.update(deltaTime, gameState, player, encounterDirector)",
        "entityManager.update(deltaTime, gameState, player, encounterDirector, runMode)",
        "entity update run mode",
    )
    text = replace_once(
        text,
        '''                val persistEncounter = collision.entity.shouldRecordPersistence &&
                    encounterDirector?.isScenarioActive != true
''',
        '''                val persistEncounter = collision.entity.shouldRecordPersistence &&
                    runMode.persistsProgress
''',
        "collision persistence gate",
    )
    text = replace_once(
        text,
        "if (::player.isInitialized && encounterDirector?.isScenarioActive != true) {",
        "if (::player.isInitialized && runMode.recordsGhost) {",
        "ghost recording gate",
    )
    text = replace_once(
        text,
        "if (encounterDirector?.isScenarioActive != true) {\n                    reward.friendBiome",
        "if (runMode.persistsProgress) {\n                    reward.friendBiome",
        "friendship persistence gate",
    )
    text = replace_once(
        text,
        '''    private fun prepareFreshRun() {
        encounterDirector?.stopScenario()
''',
        '''    private fun prepareFreshRun() {
        runMode = RunMode.NORMAL
        encounterDirector?.stopScenario()
''',
        "fresh-run normal mode",
    )
    text = replace_once(
        text,
        "if (encounterDirector?.isScenarioActive == true) return\n\n        val mercyTier",
        "if (!runMode.allowsOrdinaryProgressCues) return\n\n        val mercyTier",
        "ordinary cue mode gate",
    )
    text = replace_once(
        text,
        '''        val director = encounterDirector ?: return
        if (!::entityManager.isInitialized || !::player.isInitialized || !::gameState.isInitialized) return
        val scenario = director.selectedScenario
''',
        '''        val director = encounterDirector ?: return
        if (!::entityManager.isInitialized || !::player.isInitialized || !::gameState.isInitialized) return
        if (runMode == RunMode.NORMAL) runMode = RunMode.DEBUG_SCENARIO
        val scenario = director.selectedScenario
''',
        "interactive scenario mode fallback",
    )
    text = replace_once(
        text,
        '''    private fun shouldDrawGhostPlayback(): Boolean {
        val director = encounterDirector
        if (director?.isScenarioActive == true) {
            return director.activeScenario?.allowGhostPlayback == true
        }
        return true
    }
''',
        '''    private fun shouldDrawGhostPlayback(): Boolean {
        if (!runMode.isDeterministic) return runMode.allowsDefaultGhostPlayback
        return encounterDirector?.activeScenario?.allowGhostPlayback == true
    }
''',
        "ghost playback policy",
    )

    GAME_VIEW.write_text(text, encoding="utf-8")


def patch_entity_manager() -> None:
    text = ENTITY_MANAGER.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        player: Player,
        encounterDirector: EncounterDirector? = null
    ) {
''',
        '''        player: Player,
        encounterDirector: EncounterDirector? = null,
        runMode: RunMode = RunMode.NORMAL
    ) {
''',
        "entity update signature",
    )
    text = replace_once(
        text,
        "if (encounterDirector?.isScenarioActive != true) {",
        "if (runMode.allowsRandomSpawns) {",
        "random spawn policy",
    )
    ENTITY_MANAGER.write_text(text, encoding="utf-8")


def patch_hardware_capture() -> None:
    text = HARDWARE_CAPTURE.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.RunState\n",
        "import com.anurag9000.forestrun.engine.RunMode\nimport com.anurag9000.forestrun.engine.RunState\n",
        "capture RunMode import",
    )
    text = replace_once(
        text,
        '''        instrumentation.runOnMainSync {
            val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
''',
        '''        instrumentation.runOnMainSync {
            setPrivateField(gameView, "runMode", RunMode.SCREENSHOT_CAPTURE)
            val director = getPrivateField(gameView, "encounterDirector") as EncounterDirector
''',
        "capture mode assignment",
    )
    HARDWARE_CAPTURE.write_text(text, encoding="utf-8")


def patch_hardware_profile() -> None:
    text = HARDWARE_PROFILE.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.GameView\n",
        "import com.anurag9000.forestrun.engine.GameView\nimport com.anurag9000.forestrun.engine.RunMode\n",
        "profile RunMode import",
    )
    text = replace_once(
        text,
        '''            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, scenario.name)
            putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
''',
        '''            putExtra(MainActivity.EXTRA_DEBUG_SCENARIO, scenario.name)
            putExtra(MainActivity.EXTRA_RUN_MODE, RunMode.PERFORMANCE_PROFILE.name)
            putExtra(MainActivity.EXTRA_DEBUG_AUTOSTART, true)
''',
        "profile mode intent",
    )
    HARDWARE_PROFILE.write_text(text, encoding="utf-8")


def verify() -> None:
    game_view = GAME_VIEW.read_text(encoding="utf-8")
    entity_manager = ENTITY_MANAGER.read_text(encoding="utf-8")
    if "encounterDirector?.isScenarioActive != true" in game_view:
        raise RuntimeError("GameView still contains indirect side-effect gates")
    if "encounterDirector?.isScenarioActive != true" in entity_manager:
        raise RuntimeError("EntityManager still contains indirect random-spawn gate")
    required = (
        "runMode.persistsProgress",
        "runMode.recordsGhost",
        "runMode.allowsOrdinaryProgressCues",
        "RunMode.forScenario(requestedRunMode)",
        "runMode: RunMode = RunMode.NORMAL",
        "runMode.allowsRandomSpawns",
    )
    combined = game_view + entity_manager
    missing = [marker for marker in required if marker not in combined]
    if missing:
        raise RuntimeError(f"Missing run-mode integrations: {missing}")


def main() -> None:
    patch_game_view()
    patch_entity_manager()
    patch_hardware_capture()
    patch_hardware_profile()
    verify()
    print("Integrated explicit run-mode policy")


if __name__ == "__main__":
    main()
