#!/usr/bin/env python3
"""Apply the audited GameView lifecycle/input remediation exactly once."""

from pathlib import Path

TARGET = Path("app/src/main/java/com/yourname/forest_run/engine/GameView.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")

    text = replace_once(
        text,
        """        holder.addCallback(this)\n        wireInputCallbacks()\n        setOnTouchListener { view, event ->\n            val idx = event.actionIndex.coerceAtLeast(0)\n            lastTouchX = event.getX(idx)\n            lastTouchY = event.getY(idx)\n            if (debugToolsEnabled &&\n                event.actionMasked == android.view.MotionEvent.ACTION_UP &&\n                appState == AppGameState.PLAYING &&\n                runState == RunState.PLAYING\n            ) {\n                debugEncounterOverlay?.handleTap(lastTouchX, lastTouchY)?.let { action ->\n                    handleDebugOverlayAction(action)\n                    return@setOnTouchListener true\n                }\n            }\n            inputHandler.onTouch(view, event)\n        }\n""",
        """        holder.addCallback(this)\n        setOnTouchListener { view, event ->\n            val idx = event.actionIndex.coerceAtLeast(0)\n            lastTouchX = event.getX(idx)\n            lastTouchY = event.getY(idx)\n\n            if (acceptsGameplayInput()) {\n                if (debugToolsEnabled &&\n                    event.actionMasked == android.view.MotionEvent.ACTION_UP\n                ) {\n                    debugEncounterOverlay?.handleTap(lastTouchX, lastTouchY)?.let { action ->\n                        handleDebugOverlayAction(action)\n                        return@setOnTouchListener true\n                    }\n                }\n                inputHandler.onTouch(view, event)\n            } else {\n                // A screen/state transition can occur while a pointer is still\n                // down. Drop that gesture without converting it into a delayed\n                // jump or duck release on the new screen.\n                inputHandler.cancelActiveGesture()\n                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {\n                    view.performClick()\n                    when {\n                        appState == AppGameState.MENU && ::mainMenuScreen.isInitialized ->\n                            mainMenuScreen.onTap(lastTouchX, lastTouchY)\n                        appState == AppGameState.GARDEN && ::gardenScreen.isInitialized ->\n                            gardenScreen.onTap(lastTouchX, lastTouchY)\n                        runState == RunState.GAME_OVER ->\n                            runState = runResetManager.beginRestart()\n                    }\n                }\n                true\n            }\n        }\n""",
        "touch routing",
    )

    text = replace_once(
        text,
        """    private fun stopThread() {\n        gameThread.isRunning = false\n        var retry = true\n        while (retry) {\n            try { gameThread.join(); retry = false }\n            catch (e: InterruptedException) { Thread.currentThread().interrupt() }\n        }\n    }\n""",
        """    private fun stopThread() {\n        val thread = gameThread\n        thread.requestStop()\n\n        val deadlineNs = System.nanoTime() + 1_000_000_000L\n        var callerWasInterrupted = false\n        while (thread.isAlive) {\n            val remainingNs = deadlineNs - System.nanoTime()\n            if (remainingNs <= 0L) break\n\n            val waitMs = (remainingNs / 1_000_000L).coerceIn(1L, 250L)\n            try {\n                thread.join(waitMs)\n            } catch (_: InterruptedException) {\n                callerWasInterrupted = true\n                thread.requestStop()\n            }\n        }\n\n        if (thread.isAlive) {\n            Log.w(TAG, \"GameThread did not terminate within the 1 second shutdown bound\")\n        }\n        if (callerWasInterrupted) {\n            Thread.currentThread().interrupt()\n        }\n    }\n""",
        "bounded thread shutdown",
    )

    old_wiring = """    private fun wireInputCallbacks() {\n        // These callbacks run regardless of whether the player is initialized yet.\n        inputHandler.onJumpReleased = {\n            when {\n                // Menu taps drive the menu screen\n                appState == AppGameState.MENU -> {\n                    if (::mainMenuScreen.isInitialized) {\n                        mainMenuScreen.onTap(lastTouchX, lastTouchY)\n                    }\n                }\n                appState == AppGameState.GARDEN -> {\n                    if (::gardenScreen.isInitialized) {\n                        gardenScreen.onTap(lastTouchX, lastTouchY)\n                    }\n                }\n                // GAME_OVER tap begins restart\n                runState == RunState.GAME_OVER -> {\n                    runState = runResetManager.beginRestart()\n                }\n                else -> { /* handled by wirePlayerToInput when PLAYING */ }\n            }\n        }\n    }\n\n    /** Called once after [player] is initialized to attach physics callbacks. */\n    private fun wirePlayerToInput() {\n        val prev_pressed  = inputHandler.onJumpPressed\n        val prev_released = inputHandler.onJumpReleased\n        val prev_duck     = inputHandler.onDuckPressed\n        val prev_duckEnd  = inputHandler.onDuckReleased\n\n        inputHandler.onJumpPressed  = {\n            prev_pressed?.invoke()\n            if (::gameState.isInitialized) gameState.recordJumpInput()\n            player.onJumpPressed()\n        }\n        inputHandler.onJumpHeld     = { holdSec ->\n            if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)\n            player.onJumpHeld(holdSec)\n        }\n        inputHandler.onJumpReleased = { holdSec ->\n            prev_released?.invoke(holdSec)\n            if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)\n            player.onJumpReleased(holdSec)\n        }\n        inputHandler.onDuckPressed  = {\n            prev_duck?.invoke()\n            if (::gameState.isInitialized) gameState.recordDuckInput()\n            player.onDuckPressed()\n        }\n        inputHandler.onDuckReleased = { prev_duckEnd?.invoke();           player.onDuckReleased() }\n    }\n"""
    new_wiring = """    private fun acceptsGameplayInput(): Boolean =\n        appState == AppGameState.PLAYING &&\n            runState == RunState.PLAYING &&\n            ::player.isInitialized\n\n    /** Called once after [player] is initialized to attach physics callbacks. */\n    private fun wirePlayerToInput() {\n        inputHandler.onJumpPressed = {\n            if (acceptsGameplayInput()) {\n                if (::gameState.isInitialized) gameState.recordJumpInput()\n                player.onJumpPressed()\n            }\n        }\n        inputHandler.onJumpHeld = { holdSec ->\n            if (acceptsGameplayInput()) {\n                if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)\n                player.onJumpHeld(holdSec)\n            }\n        }\n        inputHandler.onJumpReleased = { holdSec ->\n            if (acceptsGameplayInput()) {\n                if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)\n                player.onJumpReleased(holdSec)\n            }\n        }\n        inputHandler.onDuckPressed = {\n            if (acceptsGameplayInput()) {\n                if (::gameState.isInitialized) gameState.recordDuckInput()\n                player.onDuckPressed()\n            }\n        }\n        inputHandler.onDuckReleased = {\n            if (acceptsGameplayInput()) player.onDuckReleased()\n        }\n    }\n"""
    text = replace_once(text, old_wiring, new_wiring, "input callback wiring")

    text = replace_once(
        text,
        """        // Input tick\n        inputHandler.tick(deltaTime)\n\n        if (!::gameState.isInitialized) return\n""",
        """        // Gesture time advances only during live gameplay. All other\n        // top-level states silently discard any in-flight pointer state.\n        if (acceptsGameplayInput()) {\n            inputHandler.tick(deltaTime)\n        } else {\n            inputHandler.cancelActiveGesture()\n        }\n\n        if (!::gameState.isInitialized) return\n""",
        "gated input tick",
    )

    TARGET.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
