#!/usr/bin/env python3
"""Move deterministic input script ownership out of GameView."""

from pathlib import Path

GAME_VIEW = Path("app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt")
ENTITY_MANAGER = Path("app/src/main/java/com/anurag9000/forestrun/engine/EntityManager.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_game_view() -> None:
    text = GAME_VIEW.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private enum class DebugScriptAction {
        TAP_JUMP,
        HOLD_JUMP_START,
        HOLD_JUMP_END,
        DUCK_START,
        DUCK_END
    }

    private data class DebugScriptStep(
        val atSeconds: Float,
        val action: DebugScriptAction
    )
    @Volatile
''',
        '''class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    @Volatile
''',
        "remove nested debug script types",
    )
    text = replace_once(
        text,
        '''    private var debugScenarioVisualsEnabled = true
    private var debugScenarioScript: List<DebugScriptStep> = emptyList()
    private var debugScenarioScriptIndex = 0
''',
        '''    private var debugScenarioVisualsEnabled = true
    private val debugScenarioScript = DebugScenarioScript()
''',
        "replace script fields",
    )
    text = replace_once(
        text,
        '''            debugScenarioVisualsEnabled = false
            debugScenarioScript = debugScriptForScenario(scenario)
            debugScenarioScriptIndex = 0
''',
        '''            debugScenarioVisualsEnabled = false
            debugScenarioScript.prepare(scenario)
''',
        "prepare launch script",
    )
    text = replace_once(
        text,
        '''            runMode = RunMode.NORMAL
            debugScenarioVisualsEnabled = false
            debugScenarioScript = emptyList()
            debugScenarioScriptIndex = 0
''',
        '''            runMode = RunMode.NORMAL
            debugScenarioVisualsEnabled = false
            debugScenarioScript.clear()
''',
        "clear ordinary autostart script",
    )
    text = replace_once(
        text,
        '''        runMode = RunMode.NORMAL
        encounterDirector?.stopScenario()
        debugScenarioScript = emptyList()
        debugScenarioScriptIndex = 0
''',
        '''        runMode = RunMode.NORMAL
        encounterDirector?.stopScenario()
        debugScenarioScript.clear()
''',
        "clear fresh-run script",
    )
    text = replace_once(
        text,
        '''        if (runMode == RunMode.NORMAL) runMode = RunMode.DEBUG_SCENARIO
        val scenario = director.selectedScenario
        debugScenarioScriptIndex = 0
''',
        '''        if (runMode == RunMode.NORMAL) runMode = RunMode.DEBUG_SCENARIO
        val scenario = director.selectedScenario
        debugScenarioScript.prepare(scenario)
''',
        "prepare overlay-selected scenario script",
    )

    start = text.find("    private fun runDebugScenarioScript() {")
    end = text.find("    private fun shouldDrawGhostPlayback(): Boolean {", start)
    if start < 0 or end < 0:
        raise RuntimeError("Could not locate debug script methods")
    replacement = '''    private fun runDebugScenarioScript() {
        if (!debugToolsEnabled ||
            !::gameState.isInitialized ||
            !::player.isInitialized ||
            appState != AppGameState.PLAYING ||
            runState != RunState.PLAYING
        ) return

        debugScenarioScript.advance(gameState.runTimeSeconds) { action ->
            when (action) {
                DebugScenarioAction.TAP_JUMP -> {
                    player.onJumpPressed()
                    player.onJumpReleased(0f)
                }
                DebugScenarioAction.HOLD_JUMP_START -> player.onJumpPressed()
                DebugScenarioAction.HOLD_JUMP_END -> player.onJumpReleased(0.35f)
                DebugScenarioAction.DUCK_START -> player.onDuckPressed()
                DebugScenarioAction.DUCK_END -> player.onDuckReleased()
            }
        }
    }

'''
    text = text[:start] + replacement + text[end:]

    GAME_VIEW.write_text(text, encoding="utf-8")


def patch_entity_manager_default() -> None:
    text = ENTITY_MANAGER.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "runMode: RunMode = RunMode.NORMAL\n    ) {",
        '''runMode: RunMode = if (encounterDirector == null) {
            RunMode.NORMAL
        } else {
            RunMode.DEBUG_SCENARIO
        }
    ) {''',
        "director-aware compatibility default",
    )
    ENTITY_MANAGER.write_text(text, encoding="utf-8")


def verify() -> None:
    game_view = GAME_VIEW.read_text(encoding="utf-8")
    entity_manager = ENTITY_MANAGER.read_text(encoding="utf-8")
    forbidden = (
        "DebugScriptAction",
        "DebugScriptStep",
        "debugScenarioScriptIndex",
        "debugScriptForScenario",
        "private var debugScenarioScript: List",
    )
    remaining = [marker for marker in forbidden if marker in game_view]
    if remaining:
        raise RuntimeError(f"GameView still owns script internals: {remaining}")
    required = (
        "private val debugScenarioScript = DebugScenarioScript()",
        "debugScenarioScript.prepare(scenario)",
        "debugScenarioScript.advance(gameState.runTimeSeconds)",
        "runMode: RunMode = if (encounterDirector == null)",
    )
    combined = game_view + entity_manager
    missing = [marker for marker in required if marker not in combined]
    if missing:
        raise RuntimeError(f"Missing extracted-script integrations: {missing}")


def main() -> None:
    patch_game_view()
    patch_entity_manager_default()
    verify()
    print("Extracted debug scenario scripts from GameView")


if __name__ == "__main__":
    main()
