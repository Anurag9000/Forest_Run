package com.anurag9000.forestrun.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import kotlin.math.abs

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

    data class VisibilityContext(
        val livePlayerX: Float,
        val livePlayerY: Float,
        val livePlayerWidth: Float,
        val livePlayerHeight: Float,
        val nearbyHazardCount: Int,
        val nearestHazardDistancePx: Float
    )

    companion object {
        const val GHOST_ALPHA  = 102   // 40% of 255
        const val WAVE_DURATION = 0.8f  // seconds for wave + fade-out
        private const val START_DELAY = 1.15f
        private const val FADE_IN_DURATION = 0.65f
        private const val DENSE_SUPPRESSION_DURATION = 0.32f
        private const val FADE_OUT_SPEED = 8.5f
        private const val FADE_IN_SPEED = 3.1f

        // White-blue colour filter: cool tint, low saturation
        private val GHOST_FILTER: ColorMatrixColorFilter by lazy {
            val m = ColorMatrix()
            m.setSaturation(0.15f)
            // Tint blue channel boost
            val tint = floatArrayOf(
                0.8f, 0f, 0f, 0f, 20f,   // R
                0f, 0.8f, 0f, 0f, 30f,   // G
                0f, 0f, 1.1f, 0f, 60f,   // B
                0f, 0f, 0f, 1f, 0f       // A  (paint alpha controls final opacity)
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
    private var suppressedFor: Float = 0f
    private var denseSuppressedFor: Float = 0f
    private var visibilityAlpha: Float = 0f
    private var revealImmediately: Boolean = false

    // ── Paints ────────────────────────────────────────────────────────────
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha       = GHOST_ALPHA
        colorFilter = GHOST_FILTER
    }

    // Reusable draw rect
    private val drawRect = RectF()
    private val spriteRect = RectF()

    // ── API ───────────────────────────────────────────────────────────────

    /** Load a frame list recorded from a previous run. Starts ghost playback. */
    fun load(recordedFrames: List<GhostFrame>, revealImmediately: Boolean = false) {
        frames    = recordedFrames
        elapsed   = 0f
        frameIdx  = 0
        isWaving  = false
        waveTimer = 0f
        suppressedFor = 0f
        denseSuppressedFor = 0f
        visibilityAlpha = 0f
        this.revealImmediately = revealImmediately
        isActive  = recordedFrames.isNotEmpty()
    }

    fun reset() {
        isActive = false
        frames   = emptyList()
        revealImmediately = false
    }

    val hasGhost: Boolean get() = isActive

    /** Advance ghost playback. Call every PLAYING frame. */
    fun update(deltaTime: Float, visibilityContext: VisibilityContext? = null) {
        if (!isActive) return

        elapsed += deltaTime
        if (suppressedFor > 0f) {
            suppressedFor = (suppressedFor - deltaTime).coerceAtLeast(0f)
        }
        if (denseSuppressedFor > 0f) {
            denseSuppressedFor = (denseSuppressedFor - deltaTime).coerceAtLeast(0f)
        }

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

        if (!isWaving) {
            visibilityContext?.let { context ->
                if (shouldSuppressForDensePlay(context)) {
                    denseSuppressedFor = maxOf(denseSuppressedFor, DENSE_SUPPRESSION_DURATION)
                }
            }
        }

        val targetAlpha = visibilityTargetFor(visibilityContext)
        val speed = if (targetAlpha < visibilityAlpha) FADE_OUT_SPEED else FADE_IN_SPEED
        visibilityAlpha = approach(visibilityAlpha, targetAlpha, speed * deltaTime)
    }

    fun suppress(durationSec: Float) {
        suppressedFor = maxOf(suppressedFor, durationSec)
    }

    /**
     * Draw the ghost. Call BEFORE the live player.
     * @param spriteManager Supplies the same sprite sheets used by the live player.
     */
    fun draw(canvas: Canvas, spriteManager: SpriteManager) {
        if (!isActive || frames.isEmpty()) return

        val frame = frames[frameIdx.coerceIn(0, frames.lastIndex)]
        lastX = frame.x
        lastY = frame.y

        // Fade out during wave
        var alphaMulti = if (isWaving) {
            (1f - (waveTimer / WAVE_DURATION)).coerceIn(0f, 1f)
        } else 1f

        alphaMulti *= visibilityAlpha
        if (alphaMulti <= 0.02f) return

        ghostPaint.alpha = (GHOST_ALPHA * alphaMulti).toInt()

        val w = Player.BASE_WIDTH  * frame.scaleX
        val h = Player.BASE_HEIGHT * frame.scaleY

        drawRect.set(frame.x, frame.y, frame.x + w, frame.y + h)

        // Pick sprite that matches the ghost's recorded state
        val sprite = spriteForState(frame.stateOrdinal, spriteManager)
        canvas.save()
        canvas.scale(frame.scaleX, frame.scaleY, drawRect.centerX(), drawRect.bottom)
        spriteRect.set(frame.x, frame.y, frame.x + Player.BASE_WIDTH, frame.y + Player.BASE_HEIGHT)
        sprite.draw(canvas, spriteRect, ghostPaint)
        canvas.restore()
    }

    // ── Ghost's last known world position (for GameView sparkle on finish) ──
    var lastX: Float = 0f; private set
    var lastY: Float = 0f; private set
    internal val visibilityAlphaForTest: Float get() = visibilityAlpha
    internal val denseSuppressionRemainingForTest: Float get() = denseSuppressedFor

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

    private fun visibilityTargetFor(visibilityContext: VisibilityContext?): Float {
        if (suppressedFor > 0f || denseSuppressedFor > 0f) return 0f

        val revealDelay = if (revealImmediately) 0f else START_DELAY
        val revealProgress = ((elapsed - revealDelay) / FADE_IN_DURATION).coerceIn(0f, 1f)
        if (revealProgress <= 0f) return 0f
        if (visibilityContext == null || frames.isEmpty()) return revealProgress

        val frame = frames[frameIdx.coerceIn(0, frames.lastIndex)]
        val ghostWidth = Player.BASE_WIDTH * frame.scaleX
        val ghostHeight = Player.BASE_HEIGHT * frame.scaleY
        val ghostCenterX = frame.x + ghostWidth * 0.5f
        val ghostCenterY = frame.y + ghostHeight * 0.5f
        val liveCenterX = visibilityContext.livePlayerX + visibilityContext.livePlayerWidth * 0.5f
        val liveCenterY = visibilityContext.livePlayerY + visibilityContext.livePlayerHeight * 0.5f

        val horizontalRatio = abs(liveCenterX - ghostCenterX) /
            maxOf(visibilityContext.livePlayerWidth, ghostWidth)
        val verticalRatio = abs(liveCenterY - ghostCenterY) /
            maxOf(visibilityContext.livePlayerHeight * 0.9f, ghostHeight * 0.9f)

        val horizontalFade = when {
            horizontalRatio <= 0.18f -> 0f
            horizontalRatio >= 1.05f -> 1f
            else -> (horizontalRatio - 0.18f) / 0.87f
        }
        val laneFade = when {
            horizontalRatio >= 0.70f -> 1f
            verticalRatio <= 0.12f -> 0.18f
            verticalRatio >= 0.92f -> 1f
            else -> 0.18f + ((verticalRatio - 0.12f) / 0.80f) * 0.82f
        }
        val overlapFade = horizontalFade * laneFade

        val nearestHazardDistance = visibilityContext.nearestHazardDistancePx
        val hazardFade = when {
            nearestHazardDistance <= visibilityContext.livePlayerWidth * 0.95f -> 0.30f
            nearestHazardDistance >= visibilityContext.livePlayerWidth * 3.8f -> 1f
            else -> 0.30f + (
                (nearestHazardDistance - visibilityContext.livePlayerWidth * 0.95f) /
                    (visibilityContext.livePlayerWidth * 2.85f)
                ) * 0.70f
        }
        val crowdFade = when {
            visibilityContext.nearbyHazardCount >= 3 -> 0.35f
            visibilityContext.nearbyHazardCount == 2 -> 0.58f
            visibilityContext.nearbyHazardCount == 1 -> 0.82f
            else -> 1f
        }

        return (revealProgress * overlapFade * hazardFade * crowdFade).coerceIn(0f, 1f)
    }

    private fun shouldSuppressForDensePlay(visibilityContext: VisibilityContext): Boolean {
        return visibilityContext.nearbyHazardCount >= 2 &&
            visibilityContext.nearestHazardDistancePx <= visibilityContext.livePlayerWidth * 1.65f
    }

    private fun approach(current: Float, target: Float, delta: Float): Float {
        return when {
            current < target -> minOf(target, current + delta)
            current > target -> maxOf(target, current - delta)
            else -> current
        }
    }
}
