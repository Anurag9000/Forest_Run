from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
)

OLD_RUN_OWNER = """    @Volatile
    private var runState: RunState = RunState.PLAYING
    private val runResetManager    = RunResetManager()
"""

NEW_RUN_OWNER = """    @Volatile
    private var runState: RunState = RunState.PLAYING
    private val runResetManager = RunResetManager()
    private val runSessionTransitions = RunSessionTransitionCoordinator(
        effects = LiveRunSessionEffects(
            prepareFreshRunAction = {
                check(::entityManager.isInitialized)
                check(::player.isInitialized)
                check(::gameState.isInitialized)
                prepareFreshRun()
            },
            resetMenuRitualAction = {
                check(::mainMenuScreen.isInitialized)
                mainMenuScreen.resetRitual()
            },
            refreshMenuCopyAction = {
                check(::mainMenuScreen.isInitialized)
                mainMenuScreen.refreshCopy()
            },
            triggerDeathAction = {
                check(::gameState.isInitialized)
                runResetManager.triggerDeath(gameState)
            },
            beginRestartAction = { runResetManager.beginRestart() },
            executeRunResetAction = {
                check(::entityManager.isInitialized)
                check(::player.isInitialized)
                check(::gameState.isInitialized)
                runResetManager.executeReset(gameState, entityManager, player)
            },
            resetGhostRecorderAction = { ghostRecorder.reset() },
            reloadGhostAction = { reloadGhost() },
            refreshGardenAction = {
                check(::gardenScreen.isInitialized)
                gardenScreen.refresh()
            },
            playMenuMusicAction = {
                LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
            }
        )
    )
"""

REPLACEMENTS = (
    (
        """                        runState == RunState.GAME_OVER ->
                            runState = runResetManager.beginRestart()
""",
        """                        runState == RunState.GAME_OVER ->
                            applyRunSessionEvent(RunSessionEvent.REST_TAPPED)
""",
    ),
    (
        """            mainMenuScreen.onGardenTap = {
                if (::gardenScreen.isInitialized) gardenScreen.refresh()
                appState = AppGameState.GARDEN
            }
""",
        """            mainMenuScreen.onGardenTap = {
                applyRunSessionEvent(RunSessionEvent.MENU_GARDEN_REQUESTED)
            }
""",
    ),
    (
        """            gardenScreen.onBack = {
                if (::mainMenuScreen.isInitialized) {
                    mainMenuScreen.resetRitual()
                    mainMenuScreen.refreshCopy()
                }
                appState = AppGameState.MENU
            }
            gardenScreen.onRun = {
                prepareFreshRun()
                appState = AppGameState.PLAYING
            }
""",
        """            gardenScreen.onBack = {
                applyRunSessionEvent(RunSessionEvent.GARDEN_BACK_REQUESTED)
            }
            gardenScreen.onRun = {
                applyRunSessionEvent(RunSessionEvent.GARDEN_RUN_REQUESTED)
            }
""",
    ),
    (
        """                if (mainMenuScreen.consumeStartRunRequest()) {
                    prepareFreshRun()
                    appState = AppGameState.PLAYING
                }
""",
        """                if (mainMenuScreen.consumeStartRunRequest()) {
                    applyRunSessionEvent(RunSessionEvent.MENU_RUN_REQUESTED)
                }
""",
    ),
    (
        """                if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER
""",
        """                if (next == RunState.GAME_OVER) {
                    applyRunSessionEvent(RunSessionEvent.DYING_DURATION_COMPLETED)
                }
""",
    ),
    (
        """                if (next == RunState.PLAYING && runResetManager.restartFadeAlpha >= 255) {
                    if (::entityManager.isInitialized && ::player.isInitialized &&
                        ::gameState.isInitialized) {
                        runResetManager.executeReset(gameState, entityManager, player)
                        ghostRecorder.reset()
                        reloadGhost()
                    }
                    if (::gardenScreen.isInitialized) gardenScreen.refresh()
                    appState = AppGameState.GARDEN
                    LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
                    runState = RunState.PLAYING
                }
""",
        """                if (next == RunState.PLAYING && runResetManager.restartFadeAlpha >= 255) {
                    applyRunSessionEvent(RunSessionEvent.RESTART_FADE_COMPLETED)
                }
""",
    ),
    (
        """                    // Transition to DYING
                    if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)
                    runState = RunState.DYING
""",
        """                    // Transition to DYING through the authoritative session table.
                    applyRunSessionEvent(
                        RunSessionEvent.TERMINAL_COLLISION_COMPLETED
                    )
""",
    ),
)

HELPER_ANCHOR = """    private fun acceptsGameplayInput(): Boolean =
        appState == AppGameState.PLAYING &&
            runState == RunState.PLAYING &&
            ::player.isInitialized

"""

HELPER = """    private fun acceptsGameplayInput(): Boolean =
        appState == AppGameState.PLAYING &&
            runState == RunState.PLAYING &&
            ::player.isInitialized

    private fun applyRunSessionEvent(event: RunSessionEvent): Boolean {
        val result = runSessionTransitions.execute(
            current = RunSessionSnapshot(appState, runState),
            event = event
        )
        if (!result.mayAdoptAfterState) return false
        appState = result.transition.after.appState
        runState = result.transition.after.runState
        return true
    }

"""


def verify_adopted(text: str) -> None:
    assert text.count("private val runSessionTransitions =") == 1
    assert text.count("private fun applyRunSessionEvent(") == 1
    for event in (
        "MENU_RUN_REQUESTED",
        "MENU_GARDEN_REQUESTED",
        "GARDEN_RUN_REQUESTED",
        "GARDEN_BACK_REQUESTED",
        "TERMINAL_COLLISION_COMPLETED",
        "DYING_DURATION_COMPLETED",
        "REST_TAPPED",
        "RESTART_FADE_COMPLETED",
    ):
        assert text.count(f"RunSessionEvent.{event}") >= 1, event
    assert "runState = runResetManager.beginRestart()" not in text
    assert "if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER" not in text
    assert "if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)" not in text
    assert text.count("appState = result.transition.after.appState") == 1
    assert text.count("runState = result.transition.after.runState") == 1


def apply_migration(text: str) -> str:
    assert "private val liveCollisionEffects = LiveCollisionEffects(" in text, (
        "collision adapter adoption must land first"
    )
    assert text.count(OLD_RUN_OWNER) == 1
    assert "private val runSessionTransitions" not in text
    migrated = text.replace(OLD_RUN_OWNER, NEW_RUN_OWNER, 1)

    for old, new in REPLACEMENTS:
        assert migrated.count(old) == 1, old.splitlines()[0]
        migrated = migrated.replace(old, new, 1)

    assert migrated.count(HELPER_ANCHOR) == 1
    migrated = migrated.replace(HELPER_ANCHOR, HELPER, 1)
    verify_adopted(migrated)
    return migrated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    current = GAME_VIEW.read_text(encoding="utf-8")
    if args.check:
        verify_adopted(current)
        return
    GAME_VIEW.write_text(apply_migration(current), encoding="utf-8")


if __name__ == "__main__":
    main()
