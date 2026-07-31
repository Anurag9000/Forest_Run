package com.anurag9000.forestrun.systems

import android.graphics.Canvas
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

    class VisibilityContext(
        var livePlayerX: Float,
        var livePlayerY: Float,
        var livePlayerWidth: Float,
        var livePlayerHeight: Float,
        var nearbyHazardCount: Int,
        var nearestHazardDistancePx: Float
    ) {
        fun set(
            livePlayerX: Float,
            livePlayerY: Float,
            livePlayerWidth: Float,
            livePlayerHeight: Float,
            nearbyHazardCount: Int,
            nearestHazardDistancePx: Float
        ): VisibilityContext {
            this.livePlayerX = livePlayerX
            this.livePlayerY = livePlayerY
            this.livePlayerWidth = livePlayerWidth
            this.livePlayerHeight = livePlayerHeight
            this.nearbyHazardCount = nearbyHazardCount
            this.nearestHazardDistancePx = nearestHazardDistancePx
            return this
        }
    }

    companion object {
        const val GHOST_ALPHA = 102 // 40% of 255
        const val WAVE_DURATION = 0.8f // seconds for wave + fade-out
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
                0.8f, 0f, 0f, 0f, 20f, // R
                0f, 0.8f, 0f, 0f, 30f, // G
                0f, 0f, 1.1f, 0f, 60f, // B
                0f, 0f, 0f, 1f, 0f // A (paint alpha controls final opacity)
            )
            ColorMatrixColorFilter(ColorMatrix(tint))
        }
    }

    // ── State ─────────────────────────────────────────────────────────────
    private var frames: List<GhostFrame> = emptyList()
    private var elapsed: Float = 0f
    private var frameIdx: Int = 0
    private var isWaving: Boolean = false
    private var waveTimer: Float = 0f
    private var isActive: Boolean = false
    private var suppressedFor: Float = 0f
    private var denseSuppressedFor: Float = 0f
    private var visibilityAlpha: Float = 0f
    private var revealImmediately: Boolean = false

    // ── Paints ────────────────────────────────────────────────────────────
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = GHOST_ALPHA
        colorFilter = GHOST_FILTER
    }

    // Reusable draw geometry
    private val drawRect = RectF()
    private val spriteRect = RectF()
    private var drawPivotX = 0f

    // ── API ───────────────────────────────────────────────────────────────

    /** Load a validated, detached recording from a previous run. */
    fun load(recordedFrames: List<GhostFrame>, revealImmediately: Boolean = false) {
        reset()
        if (!GhostRunValidator.isValid(recordedFrames)) return

        frames = recordedFrames.toList()
        this.revealImmediately = revealImmediately
        isActive = true
    }

    fun reset() {
        frames = emptyList()
        elapsed = 0f
        frameIdx = 0
        isWaving = false
        waveTimer = 0f
        isActive = false
        suppressedFor = 0f
        denseSuppressedFor = 0f
        visibilityAlpha = 0f
        revealImmediately = false
        drawPivotX = 0f
        lastX = 0f
        lastY = 0f
    }

    val hasGhost: Boolean get() = isActive

    /** Advance ghost playback. Call every PLAYING frame. */
    fun update(deltaTime: Float, visibilityContext: VisibilityContext? = null) {
        if (!isActive) return

        val safeDelta = deltaTime.takeIf { it.isFinite() && it > 0f } ?: 0f
        advanceElapsed(safeDelta)
        suppressedFor = decrementTimer(suppressedFor, safeDelta)
        denseSuppressedFor = decrementTimer(denseSuppressedFor, safeDelta)

        if (isWaving) {
            waveTimer = addClamped(waveTimer, safeDelta, WAVE_DURATION)
            if (waveTimer >= WAVE_DURATION) {
                isActive = false // Sparkle burst is emitted by GameView at the last frame position.
            }
            return
        }

        frameIdx = frameIndexAt(elapsed)

        // Ghost has run out of recording.
        if (frameIdx >= frames.lastIndex && elapsed > frames.last().t) {
            isWaving = true
            waveTimer = 0f
        }

        if (!isWaving && visibilityContext != null && shouldSuppressForDensePlay(visibilityContext)) {
            denseSuppressedFor = maxOf(denseSuppressedFor, DENSE_SUPPRESSION_DURATION)
        }

        val targetAlpha = visibilityTargetFor(visibilityContext)
        val speed = if (targetAlpha < visibilityAlpha) FADE_OUT_SPEED else FADE_IN_SPEED
        visibilityAlpha = approach(visibilityAlpha, targetAlpha, speed * safeDelta)
    }

    fun suppress(durationSec: Float) {
        if (!durationSec.isFinite() || durationSec <= 0f) return
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

        // Fade out during wave.
        var alphaMulti = if (isWaving) {
            (1f - (waveTimer / WAVE_DURATION)).coerceIn(0f, 1f)
        } else {
            1f
        }
        alphaMulti *= visibilityAlpha
        if (!alphaMulti.isFinite() || alphaMulti <= 0.02f) return
        if (!prepareFiniteDrawGeometry(frame)) return

        ghostPaint.alpha = (GHOST_ALPHA * alphaMulti).toInt().coerceIn(0, GHOST_ALPHA)

        // Pick sprite that matches the ghost's recorded state.
        val sprite = spriteForState(frame.stateOrdinal, spriteManager)
        val saveCount = canvas.save()
        try {
            canvas.scale(frame.scaleX, frame.scaleY, drawPivotX, drawRect.bottom)
            sprite.draw(canvas, spriteRect, ghostPaint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    // ── Ghost's last known world position (for GameView sparkle on finish) ──
    var lastX: Float = 0f
        private set
    var lastY: Float = 0f
        private set

    internal val visibilityAlphaForTest: Float get() = visibilityAlpha
    internal val denseSuppressionRemainingForTest: Float get() = denseSuppressedFor
    internal val suppressionRemainingForTest: Float get() = suppressedFor
    internal val frameIndexForTest: Int get() = frameIdx
    internal val isWavingForTest: Boolean get() = isWaving
    internal val elapsedForTest: Float get() = elapsed

    private fun spriteForState(ordinal: Int, sm: SpriteManager): SpriteSheet {
        val state = PlayerState.entries.getOrElse(ordinal) { PlayerState.RUNNING }
        return when (state) {
            PlayerState.JUMP_START -> sm.playerJumpStart
            PlayerState.JUMPING -> sm.playerJumping
            PlayerState.APEX -> sm.playerApex
            PlayerState.FALLING -> sm.playerFalling
            PlayerState.LANDING -> sm.playerLanding
            PlayerState.DUCKING -> sm.playerDuck
            else -> sm.playerRun
        }
    }

    private fun visibilityTargetFor(visibilityContext: VisibilityContext?): Float {
        if (suppressedFor > 0f || denseSuppressedFor > 0f) return 0f

        val revealDelay = if (revealImmediately) 0f else START_DELAY
        val revealProgress = ((elapsed - revealDelay) / FADE_IN_DURATION).coerceIn(0f, 1f)
        if (revealProgress <= 0f) return 0f
        if (visibilityContext == null || frames.isEmpty()) return revealProgress
        if (!isValidVisibilityContext(visibilityContext)) return 0f

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
        if (!isValidVisibilityContext(visibilityContext)) return false
        return visibilityContext.nearbyHazardCount >= 2 &&
            visibilityContext.nearestHazardDistancePx <= visibilityContext.livePlayerWidth * 1.65f
    }

    private fun isValidVisibilityContext(context: VisibilityContext): Boolean =
        context.livePlayerX.isFinite() &&
            context.livePlayerY.isFinite() &&
            context.livePlayerWidth.isFinite() &&
            context.livePlayerWidth > 0f &&
            context.livePlayerHeight.isFinite() &&
            context.livePlayerHeight > 0f &&
            context.nearbyHazardCount >= 0 &&
            !context.nearestHazardDistancePx.isNaN() &&
            context.nearestHazardDistancePx >= 0f

    private fun approach(current: Float, target: Float, delta: Float): Float {
        val safeCurrent = current.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val safeTarget = target.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val safeDelta = delta.takeIf { it.isFinite() && it > 0f } ?: 0f
        return when {
            safeCurrent < safeTarget -> minOf(safeTarget, safeCurrent + safeDelta)
            safeCurrent > safeTarget -> maxOf(safeTarget, safeCurrent - safeDelta)
            else -> safeCurrent
        }
    }

    private fun advanceElapsed(deltaTime: Float) {
        if (deltaTime <= 0f) return
        val maximum = frames.last().t.toDouble() + WAVE_DURATION.toDouble()
        elapsed = (elapsed.toDouble() + deltaTime.toDouble())
            .coerceAtMost(maximum)
            .toFloat()
    }

    private fun decrementTimer(timer: Float, deltaTime: Float): Float {
        val safeTimer = timer.takeIf { it.isFinite() && it > 0f } ?: 0f
        return (safeTimer - deltaTime).coerceAtLeast(0f)
    }

    private fun addClamped(current: Float, delta: Float, maximum: Float): Float =
        (current.toDouble() + delta.toDouble())
            .coerceIn(0.0, maximum.toDouble())
            .toFloat()

    private fun frameIndexAt(time: Float): Int {
        var low = 0
        var high = frames.lastIndex
        var result = 0
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (frames[middle].t <= time) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    private fun prepareFiniteDrawGeometry(frame: GhostFrame): Boolean {
        val drawRight = frame.x.toDouble() + Player.BASE_WIDTH.toDouble() * frame.scaleX.toDouble()
        val drawBottom = frame.y.toDouble() + Player.BASE_HEIGHT.toDouble() * frame.scaleY.toDouble()
        val spriteRight = frame.x.toDouble() + Player.BASE_WIDTH.toDouble()
        val spriteBottom = frame.y.toDouble() + Player.BASE_HEIGHT.toDouble()
        val pivotX = (frame.x.toDouble() + drawRight) * 0.5
        if (!isRepresentableFloat(drawRight) ||
            !isRepresentableFloat(drawBottom) ||
            !isRepresentableFloat(spriteRight) ||
            !isRepresentableFloat(spriteBottom) ||
            !isRepresentableFloat(pivotX)
        ) {
            return false
        }

        drawRect.set(frame.x, frame.y, drawRight.toFloat(), drawBottom.toFloat())
        spriteRect.set(frame.x, frame.y, spriteRight.toFloat(), spriteBottom.toFloat())
        drawPivotX = pivotX.toFloat()
        return true
    }

    private fun isRepresentableFloat(value: Double): Boolean =
        value.isFinite() && value >= -Float.MAX_VALUE.toDouble() && value <= Float.MAX_VALUE.toDouble()
}
