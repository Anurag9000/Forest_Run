
package com.yourname.forest_run.engine

import android.view.MotionEvent
import android.view.View
import com.yourname.forest_run.utils.MathUtils

/**
 * Converts raw touch input into mutually-exclusive jump or duck gestures.
 *
 * A touch is deliberately kept pending until either it crosses the downward
 * swipe threshold or the short decision window expires. This prevents the
 * old failure mode where ACTION_DOWN started a jump before a swipe could be
 * recognised as a duck.
 */
class InputHandler : View.OnTouchListener {

    var onJumpPressed: (() -> Unit)? = null
    var onJumpHeld: ((holdSeconds: Float) -> Unit)? = null
    var onJumpReleased: ((holdSeconds: Float) -> Unit)? = null
    var onDuckPressed: (() -> Unit)? = null
    var onDuckReleased: (() -> Unit)? = null

    var isDucking: Boolean = false
        private set
    var isChargingJump: Boolean = false
        private set
    var holdDuration: Float = 0f
        private set
    var lastGestureLabel: String = "none"
        private set

    private var primaryPointerId = INVALID_POINTER
    private var touchStartY = 0f
    private var jumpCommitted = false

    companion object {
        private const val INVALID_POINTER = -1
        internal const val SWIPE_DOWN_THRESHOLD_PX = 80f
        internal const val GESTURE_DECISION_WINDOW_S = 0.10f
        private const val HOLD_FIRE_THRESHOLD_S = 0.05f
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> handleDown(event)
        MotionEvent.ACTION_POINTER_DOWN -> true
        MotionEvent.ACTION_MOVE -> handleMove(event)
        MotionEvent.ACTION_UP -> {
            v.performClick()
            handleUp(event)
        }
        MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
        MotionEvent.ACTION_CANCEL -> {
            cancelActiveGesture()
            true
        }
        else -> false
    }

    fun tick(deltaTime: Float) {
        if (primaryPointerId == INVALID_POINTER || isDucking) return
        holdDuration = (holdDuration + deltaTime.coerceAtLeast(0f)).coerceAtMost(PlayerHoldLimit.SECONDS)

        if (!jumpCommitted && holdDuration >= GESTURE_DECISION_WINDOW_S) {
            commitJumpStart()
        }
        if (jumpCommitted && holdDuration >= HOLD_FIRE_THRESHOLD_S) {
            onJumpHeld?.invoke(holdDuration)
        }
    }

    /** Clears a partially recognised gesture without emitting gameplay input. */
    fun cancelActiveGesture() {
        if (isDucking) onDuckReleased?.invoke()
        primaryPointerId = INVALID_POINTER
        isDucking = false
        isChargingJump = false
        jumpCommitted = false
        holdDuration = 0f
        lastGestureLabel = "CANCEL"
    }

    private fun handleDown(event: MotionEvent): Boolean {
        if (primaryPointerId != INVALID_POINTER) return true
        val index = event.actionIndex
        primaryPointerId = event.getPointerId(index)
        touchStartY = event.getY(index)
        holdDuration = 0f
        isDucking = false
        isChargingJump = true
        jumpCommitted = false
        lastGestureLabel = "PENDING"
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val index = event.findPointerIndex(primaryPointerId)
        if (index < 0) return false
        val dy = event.getY(index) - touchStartY
        if (!jumpCommitted && !isDucking && dy > SWIPE_DOWN_THRESHOLD_PX) {
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
        if (event.getPointerId(event.actionIndex) != primaryPointerId) return true
        commitRelease()
        return true
    }

    private fun commitJumpStart() {
        if (jumpCommitted || isDucking) return
        jumpCommitted = true
        isChargingJump = true
        lastGestureLabel = "JUMP:PRESS"
        onJumpPressed?.invoke()
    }

    private fun commitRelease() {
        val wasDucking = isDucking
        val finalHold = MathUtils.clamp(holdDuration, 0f, PlayerHoldLimit.SECONDS)

        if (!wasDucking && !jumpCommitted) {
            // Quick taps still receive a real jump press before release.
            commitJumpStart()
        }

        primaryPointerId = INVALID_POINTER
        isDucking = false
        isChargingJump = false
        jumpCommitted = false
        holdDuration = 0f

        if (wasDucking) {
            lastGestureLabel = "DUCK_END"
            onDuckReleased?.invoke()
        } else {
            lastGestureLabel = if (finalHold < 0.12f) {
                "JUMP:TAP"
            } else {
                "JUMP:HOLD(${String.format("%.2f", finalHold)}s)"
            }
            onJumpReleased?.invoke(finalHold)
        }
    }

    private object PlayerHoldLimit {
        const val SECONDS = 0.60f
    }
}
