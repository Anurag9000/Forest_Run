#!/usr/bin/env python3
"""One-shot exact migration for debug top-level session state publication."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
WORKFLOW = ROOT / ".github/workflows/debug-session-state-migration.yml"
SELF = Path(__file__)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    source = GAME_VIEW.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '''            director.selectScenario(scenario)
            appState = AppGameState.PLAYING
            runState = RunState.PLAYING
            prepareEncounterScenario()
''',
        '''            director.selectScenario(scenario)
            check(applyRunSessionEvent(RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED)) {
                "Debug scenario state publication was rejected"
            }
            prepareEncounterScenario()
''',
        "debug scenario publication",
    )
    source = replace_once(
        source,
        '''            debugScenarioScript.clear()
            prepareFreshRun()
            appState = AppGameState.PLAYING
            runState = RunState.PLAYING
''',
        '''            debugScenarioScript.clear()
            prepareFreshRun()
            check(applyRunSessionEvent(RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED)) {
                "Debug auto-start state publication was rejected"
            }
''',
        "debug auto-start publication",
    )

    direct_app = source.count("appState = result.transition.after.appState")
    direct_run = source.count("runState = result.transition.after.runState")
    if direct_app != 1 or direct_run != 1:
        raise SystemExit(
            f"expected one authoritative state assignment pair, got app={direct_app}, run={direct_run}"
        )
    if "appState = AppGameState.PLAYING\n            runState = RunState.PLAYING" in source:
        raise SystemExit("direct debug state assignment survived")
    if source.count("RunSessionEvent.DEBUG_PLAYING_STATE_REQUESTED") != 2:
        raise SystemExit("both debug launch paths must route through the session event")

    GAME_VIEW.write_text(source, encoding="utf-8")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
