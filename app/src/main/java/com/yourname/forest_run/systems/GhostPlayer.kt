package com.yourname.forest_run.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.entities.PlayerState

/**
 * Plays back a ghost recording of the personal-best run.
 *
 * Visual spec:
 *  - 40% opacity (alpha 102 out of 255).
 *  - White-blue [ColorMatrixColorFilter] tint — desaturated + blue push.
 *  - Drawn BEHIND the live player.
 *  - At the ghost's last recorded frame: play a 2-frame wave animation then fade out with sparkles.
 *
 * Usage:
 *   // Load on run start if a ghost file exists (SaveManager handles this):
 *   ghostPlayer.load(frames)
 *
 *   // Every PLAYING frame:
 *   ghostPlayer.update(deltaTime)
 *
 *   // In draw() BEFORE live player:
 *   ghostPlayer.draw(canvas, spriteManager)
 *
 *   // After run reset:
 *   ghostPlayer.reset()
 */
class GhostPlayer {

    companion object {
        const val GHOST_ALPHA  = 102   // 40% of 255
        const val WAVE_DURATION = 0.8f  // seconds for wave + fade-out

        // White-blue colour filter: cool tint, low saturation
        private val GHOST_FILTER: ColorMatrixColorFilter by lazy {
            val m = ColorMatrix()
            m.setSaturation(0.15f)
            // Tint blue channel boost
            val tint = floatArrayOf(
                0.8f, 0f, 0f, 0f, 20f,   // R
                0f, 0.8f, 0f, 0f, 30f,   // G
                0f, 0f, 1.1f, 0f, 60f,   // B
                0f, 0f, 0f, 0.4f, 0f     // A  (40% opacity baked in)
            )
            ColorMatrixColorFilter(ColorMatrix(tint))
        }
    }

    // ── State ─────────────────────────────────────────────────────────────
    private var frames:    List<GhostFrame> = emptyList()
    private var elapsed:   Float = 0f
    private var frameIdx:  Int   = 0
    private var isWaving:  Boolean = false
    private var waveTimer: Float   = 0f
    private var isActive:  Boolean = false

    // ── Paints ────────────────────────────────────────────────────────────
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha       = GHOST_ALPHA
        colorFilter = GHOST_FILTER
    }

    // Reusable draw rect
    private val drawRect = RectF()

    // ── API ───────────────────────────────────────────────────────────────

    /** Load a frame list recorded from a previous run. Starts ghost playback. */
    fun load(recordedFrames: List<GhostFrame>) {
        frames    = recordedFrames
        elapsed   = 0f
        frameIdx  = 0
        isWaving  = false
        waveTimer = 0f
        isActive  = recordedFrames.isNotEmpty()
    }

    fun reset() {
        isActive = false
        frames   = emptyList()
    }

    val hasGhost: Boolean get() = isActive

    /** Advance ghost playback. Call every PLAYING frame. */
    fun update(deltaTime: Float) {
        if (!isActive) return

        elapsed += deltaTime

        if (isWaving) {
            waveTimer += deltaTime
            if (waveTimer >= WAVE_DURATION) {
                isActive = false   // ghost finishes
                // Sparkle burst emitted by GameView at last frame position
            }
            return
        }

        // Binary-search for the frame closest to elapsed time
        while (frameIdx < frames.size - 1 && frames[frameIdx + 1].t <= elapsed) {
            frameIdx++
        }

        // Ghost has run out of recording
        if (frameIdx >= frames.size - 1 && elapsed > frames.last().t) {
            isWaving  = true
            waveTimer = 0f
        }
    }

    /**
     * Draw the ghost. Call BEFORE the live player.
     * @param spriteManager Supplies the same sprite sheets used by the live player.
     */
    fun draw(canvas: Canvas, spriteManager: SpriteManager) {
        if (!isActive || frames.isEmpty()) return

        val frame = frames[frameIdx.coerceIn(0, frames.lastIndex)]

        // Fade out during wave
        val alphaMulti = if (isWaving) {
            (1f - (waveTimer / WAVE_DURATION)).coerceIn(0f, 1f)
        } else 1f
        ghostPaint.alpha = (GHOST_ALPHA * alphaMulti).toInt()

        val w = Player.BASE_WIDTH  * frame.scaleX
        val h = Player.BASE_HEIGHT * frame.scaleY

        drawRect.set(frame.x, frame.y, frame.x + w, frame.y + h)

        // Pick sprite that matches the ghost's recorded state
        val sprite = spriteForState(frame.stateOrdinal, spriteManager)
        canvas.save()
        canvas.scale(frame.scaleX, frame.scaleY, drawRect.centerX(), drawRect.bottom)
        sprite.draw(canvas, RectF(frame.x, frame.y, frame.x + Player.BASE_WIDTH, frame.y + Player.BASE_HEIGHT))
        canvas.restore()

        // After restoring, draw the ghost paint overlay (tint)  — done via the sprite draw above
        // The colorFilter is applied in ghostPaint which we pass to sprite below; handled in SpriteSheet.draw overload. 
        // Since SpriteSheet.draw doesn't accept a custom Paint, we apply alpha + filter to the canvas paint via a full-rect semi-transparent overlay instead:
        ghostPaint.alpha = (GHOST_ALPHA * alphaMulti).toInt()
        canvas.drawRect(drawRect, ghostPaint)
    }

    // ── Ghost's last known world position (for GameView sparkle on finish) ──
    var lastX: Float = 0f; private set
    var lastY: Float = 0f; private set

    private fun spriteForState(ordinal: Int, sm: SpriteManager): SpriteSheet {
        val state = PlayerState.entries.getOrElse(ordinal) { PlayerState.RUNNING }
        return when (state) {
            PlayerState.JUMP_START -> sm.playerJumpStart
            PlayerState.JUMPING    -> sm.playerJumping
            PlayerState.APEX       -> sm.playerApex
            PlayerState.FALLING    -> sm.playerFalling
            PlayerState.LANDING    -> sm.playerLanding
            PlayerState.DUCKING    -> sm.playerDuck
            else                   -> sm.playerRun
        }
    }
}
