package com.anurag9000.forestrun.engine

import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager

/**
 * Orchestrates the DYING → GAME_OVER → RESTARTING transition.
 *
 * GameView owns one instance. On HIT:
 *   runResetManager.triggerDeath(gameState)
 *
 * Each frame in GameView.update():
 *   val newState = runResetManager.update(deltaTime, currentState)
 *   // newState drives what to draw and whether physics run
 */
class RunResetManager {

    companion object {
        /** Seconds the player sits in REST state before GameOverScreen appears. */
        const val DYING_DURATION_S = 1.2f
        /** Seconds of fade-out before handing control back to the Garden. */
        const val RESTART_FADE_S = 0.5f
    }

    private var timer = 0f

    // Fade alpha for the restart fade-out (0 = transparent, 255 = black)
    var restartFadeAlpha: Int = 0
        private set

    val dyingFraction: Float
        get() = (timer / DYING_DURATION_S).takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f

    /** Call this the moment a HIT collision is detected. */
    fun triggerDeath(gameState: GameStateManager) {
        timer = 0f
        restartFadeAlpha = 0
        gameState.save()
    }

    /** Advance only the timed DYING and RESTARTING states. */
    fun update(deltaTime: Float, currentState: RunState): RunState {
        if (currentState != RunState.DYING && currentState != RunState.RESTARTING) {
            return currentState
        }
        if (!deltaTime.isFinite() || deltaTime <= 0f) return currentState

        timer = finiteSaturatingAdd(timer, deltaTime)
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
                val t = (timer / RESTART_FADE_S).coerceIn(0f, 1f)
                restartFadeAlpha = (t * 255f).toInt().coerceIn(0, 255)
                if (timer >= RESTART_FADE_S) RunState.PLAYING else RunState.RESTARTING
            }

            else -> currentState
        }
    }

    /** Begin the fade back to the Garden. */
    fun beginRestart(): RunState {
        timer = 0f
        restartFadeAlpha = 0
        return RunState.RESTARTING
    }

    /** Full reset of all live systems after the fade completes. */
    fun executeReset(
        gameState: GameStateManager,
        entityManager: EntityManager,
        player: com.anurag9000.forestrun.entities.Player
    ) {
        timer = 0f
        restartFadeAlpha = 0

        gameState.resetRun()
        entityManager.reset()
        player.reset()

        FlavorTextManager.clear()
        DialogueBubbleManager.clear()
        ParticleManager.clear()
        CameraSystem.reset()
    }

    private fun finiteSaturatingAdd(value: Float, delta: Float): Float {
        if (!value.isFinite() || value < 0f) return 0f
        val sum = value.toDouble() + delta.toDouble()
        return sum.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }
}
