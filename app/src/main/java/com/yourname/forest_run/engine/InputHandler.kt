package com.yourname.forest_run.engine

import android.view.MotionEvent
import android.view.View
import com.yourname.forest_run.utils.MathUtils

/**
 * Translates raw [MotionEvent]s into high-level game input callbacks.
 *
 * Gestures handled:
 *  - **Tap (short)** → [onJumpPressed] then immediately [onJumpReleased]
 *  - **Hold (long press)** → [onJumpPressed], repeated [onJumpHeld], then [onJumpReleased]
 *  - **Swipe Down** → [onDuckPressed]; lift → [onDuckReleased]
 *
 * Jump height is variable: [onJumpReleased] receives the total hold
 * duration in seconds so the physics engine can scale the jump force.
 *
 * Multi-touch: each pointer is tracked independently; only the *first*
 * active pointer drives game input (subsequent fingers are ignored).
 */
class InputHandler : View.OnTouchListener {

    // ------------------------------------------------------------------
    // Callbacks – set by the owner (GameView / GameStateManager)
    // ------------------------------------------------------------------

    /** Finger touched the screen – start charging the jump. */
    var onJumpPressed: (() -> Unit)? = null

    /**
     * Finger is still held down.
     * @param holdSeconds seconds the finger has been held so far.
     */
    var onJumpHeld: ((holdSeconds: Float) -> Unit)? = null

    /**
     * Finger lifted – commit the jump.
     * @param holdSeconds total hold duration in seconds (0 = quick tap).
     */
    var onJumpReleased: ((holdSeconds: Float) -> Unit)? = null

    /** Swipe-down detected while finger is still on screen. */
    var onDuckPressed: (() -> Unit)? = null

    /** Swipe-down finger lifted. */
    var onDuckReleased: (() -> Unit)? = null

    // ------------------------------------------------------------------
    // Internal state
    // ------------------------------------------------------------------

    /** True while the primary pointer is performing a duck gesture. */
    var isDucking: Boolean = false
        private set

    /** True while the primary pointer has a jump charge in progress. */
    var isChargingJump: Boolean = false
        private set

    /** Read-only current hold duration in seconds (0 when not pressing). */
    var holdDuration: Float = 0f
        private set

    /** Last raw gesture string for debug display. */
    var lastGestureLabel: String = "none"
        private set

    // Primary pointer tracking
    private var primaryPointerId: Int = INVALID_POINTER
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var touchStartTimeMs: Long = 0L

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------
    companion object {
        private const val INVALID_POINTER = -1

        /** Vertical swipe threshold in pixels. Swipes shorter than this are jumps. */
        private const val SWIPE_DOWN_THRESHOLD_PX = 80f

        /** Minimum hold time in seconds before [onJumpHeld] fires. */
        private const val HOLD_FIRE_THRESHOLD_S = 0.05f
    }

    // ------------------------------------------------------------------
    // Touch listener
    // ------------------------------------------------------------------

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN        -> handleDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> false // ignore extra fingers
            MotionEvent.ACTION_MOVE        -> handleMove(event)
            MotionEvent.ACTION_UP          -> {
                v.performClick()
                handleUp(event)
            }
            MotionEvent.ACTION_POINTER_UP  -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL      -> handleCancel()
            else                           -> false
        }
    }

    // ------------------------------------------------------------------
    // Called every game frame from GameView.update() while a finger is held
    // ------------------------------------------------------------------

    /**
     * Must be called once per game frame (with [deltaTime] in seconds) so we
     * can track the hold duration and fire [onJumpHeld] continuously.
     */
    fun tick(deltaTime: Float) {
        if (primaryPointerId != INVALID_POINTER && isChargingJump && !isDucking) {
            holdDuration += deltaTime
            if (holdDuration >= HOLD_FIRE_THRESHOLD_S) {
                onJumpHeld?.invoke(holdDuration)
            }
        }
    }

    // ------------------------------------------------------------------
    // Private event handlers
    // ------------------------------------------------------------------

    private fun handleDown(event: MotionEvent): Boolean {
        if (primaryPointerId != INVALID_POINTER) return false // already tracking

        val idx = event.actionIndex
        primaryPointerId = event.getPointerId(idx)
        touchStartX = event.getX(idx)
        touchStartY = event.getY(idx)
        touchStartTimeMs = System.currentTimeMillis()
        holdDuration = 0f
        isDucking = false
        isChargingJump = true

        lastGestureLabel = "PRESS"
        onJumpPressed?.invoke()
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val idx = event.findPointerIndex(primaryPointerId)
        if (idx < 0) return false

        val dy = event.getY(idx) - touchStartY

        if (!isDucking && dy > SWIPE_DOWN_THRESHOLD_PX) {
            // Transition: jump charge → duck
            isDucking = true
            isChargingJump = false
            holdDuration = 0f
            lastGestureLabel = "DUCK"
            onDuckPressed?.invoke()
        }
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        if (event.getPointerId(event.actionIndex) != primaryPointerId) return false
        commitRelease()
        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        if (event.getPointerId(event.actionIndex) != primaryPointerId) return false
        commitRelease()
        return true
    }

    private fun handleCancel(): Boolean {
        commitRelease()
        return true
    }

    private fun commitRelease() {
        val wasDucking    = isDucking
        val wasCharging   = isChargingJump
        val finalHold     = holdDuration

        // Reset state first so callbacks see clean state if they re-enter
        primaryPointerId  = INVALID_POINTER
        isDucking         = false
        isChargingJump    = false
        holdDuration      = 0f

        if (wasDucking) {
            lastGestureLabel = "DUCK_END"
            onDuckReleased?.invoke()
        } else if (wasCharging) {
            val label = if (finalHold < 0.12f) "JUMP:TAP" else "JUMP:HOLD(${String.format("%.2f", finalHold)}s)"
            lastGestureLabel = label
            onJumpReleased?.invoke(MathUtils.clamp(finalHold, 0f, 0.6f))
        }
    }
}
