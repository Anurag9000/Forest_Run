package com.yourname.forest_run.engine

import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.FlavorTextManager

/**
 * Orchestrates the DYING → GAME_OVER → RESTARTING transition.
 *
 * GameView owns one instance. On HIT:
 *   runResetManager.triggerDeath(gameState, player, entityManager)
 *
 * Each frame in GameView.update():
 *   val newState = runResetManager.update(deltaTime, currentState)
 *   // newState drives what to draw and whether physics run
 */
class RunResetManager {

    companion object {
        /** Seconds the player sits in REST state before GameOverScreen appears. */
        const val DYING_DURATION_S  = 1.2f
        /** Seconds of fade-out before handing control back to the Garden. */
        const val RESTART_FADE_S    = 0.5f
    }

    private var timer = 0f

    // Fade alpha for the restart fade-out (0 = transparent, 255 = black)
    var restartFadeAlpha: Int = 0
        private set

    // ── Death trigger ─────────────────────────────────────────────────────

    /**
     * Call this the moment a HIT collision is detected.
     * Saves high-score immediately to survive process kill.
     */
    fun triggerDeath(gameState: GameStateManager) {
        timer = 0f
        restartFadeAlpha = 0
        gameState.save()    // Persist high score immediately
    }

    // ── Frame update ─────────────────────────────────────────────────────

    /**
     * Advance the state machine. Returns the new [RunState].
     *
     * Call from GameView.update() only when [currentState] is DYING, GAME_OVER, or RESTARTING.
     */
    fun update(deltaTime: Float, currentState: RunState): RunState {
        timer += deltaTime

        return when (currentState) {
            RunState.DYING -> {
                if (timer >= DYING_DURATION_S) {
                    timer = 0f
                    RunState.GAME_OVER
                } else {
                    RunState.DYING
                }
            }

            RunState.RESTARTING -> {
                // Drive the fade-to-black alpha
                val t = (timer / RESTART_FADE_S).coerceIn(0f, 1f)
                restartFadeAlpha = (t * 255f).toInt()

                if (timer >= RESTART_FADE_S) {
                    // Full fade reached — reset happens in GameView after this returns
                    RunState.PLAYING  // GameView checks alpha==255 to actually execute reset
                } else {
                    RunState.RESTARTING
                }
            }

            // PLAYING and GAME_OVER are handled entirely by GameView
            else -> currentState
        }
    }

    /**
     * Signal that a tap was registered on the rest screen.
     * Begins the fade back to the Garden.
     */
    fun beginRestart(): RunState {
        timer = 0f
        restartFadeAlpha = 0
        return RunState.RESTARTING
    }

    /**
     * Full reset of all live systems. Call once fade completes (restartFadeAlpha >= 255).
     *
     * GameView must also:
     * GameView decides where to route next (Garden in the canonical flow).
     */
    fun executeReset(
        gameState:     GameStateManager,
        entityManager: EntityManager,
        player:        com.yourname.forest_run.entities.Player
    ) {
        timer            = 0f
        restartFadeAlpha = 0

        gameState.resetRun()
        entityManager.reset()
        player.reset()

        FlavorTextManager.clear()
        ParticleManager.clear()
        CameraSystem.reset()
    }
}
