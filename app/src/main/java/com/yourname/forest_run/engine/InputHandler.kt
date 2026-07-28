package com.yourname.forest_run.engine

import android.view.MotionEvent
import android.view.View
import com.yourname.forest_run.utils.MathUtils

/**
 * Translates touch events into jump and duck callbacks.
 *
 * A touch is kept undecided for a very short gesture-arbitration window. A
 * downward swipe during that window becomes a duck without ever starting a
 * jump. A hold starts the jump after the window; a quick tap starts and
 * releases the jump together on finger-up.
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
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var jumpStarted = false

    companion object {
        private const val INVALID_POINTER = -1
        private const val SWIPE_DOWN_THRESHOLD_PX = 80f

        /**
         * Small delay used only to distinguish a swipe from a hold. Quick taps
         * are still committed immediately on ACTION_UP.
         */
        private const val JUMP_DECISION_DELAY_S = 0.075f
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> false
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> {
                v.performClick()
                handleUp(event)
            }
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
            else -> false
        }
    }

    /** Called once per game frame while a primary pointer is active. */
    fun tick(deltaTime: Float) {
        if (primaryPointerId == INVALID_POINTER || !isChargingJump || isDucking) return

        holdDuration += deltaTime.coerceAtLeast(0f)
        if (!jumpStarted && holdDuration >= JUMP_DECISION_DELAY_S) {
            startJump()
        }
        if (jumpStarted) {
            onJumpHeld?.invoke(holdDuration)
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        if (primaryPointerId != INVALID_POINTER) return false

        val index = event.actionIndex
        primaryPointerId = event.getPointerId(index)
        touchStartX = event.getX(index)
        touchStartY = event.getY(index)
        holdDuration = 0f
        isDucking = false
        isChargingJump = true
        jumpStarted = false
        lastGestureLabel = "PRESS"
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val index = event.findPointerIndex(primaryPointerId)
        if (index < 0) return false

        val dy = event.getY(index) - touchStartY
        if (!isDucking && !jumpStarted && dy > SWIPE_DOWN_THRESHOLD_PX) {
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
        commitRelease(cancelled = false)
        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        if (event.getPointerId(event.actionIndex) != primaryPointerId) return false
        commitRelease(cancelled = false)
        return true
    }

    private fun handleCancel(): Boolean {
        commitRelease(cancelled = true)
        return true
    }

    private fun startJump() {
        if (jumpStarted || isDucking || !isChargingJump) return
        jumpStarted = true
        onJumpPressed?.invoke()
    }

    private fun commitRelease(cancelled: Boolean) {
        if (primaryPointerId == INVALID_POINTER) return

        val wasDucking = isDucking
        val wasCharging = isChargingJump
        val hadStartedJump = jumpStarted
        val finalHold = MathUtils.clamp(holdDuration, 0f, 0.6f)

        primaryPointerId = INVALID_POINTER
        isDucking = false
        isChargingJump = false
        jumpStarted = false
        holdDuration = 0f
        touchStartX = 0f
        touchStartY = 0f

        when {
            wasDucking -> {
                lastGestureLabel = if (cancelled) "DUCK_CANCEL" else "DUCK_END"
                onDuckReleased?.invoke()
            }

            wasCharging && cancelled -> {
                lastGestureLabel = "CANCEL"
                // Release only a jump that actually began. Never turn an
                // Android cancellation into a surprise tap jump.
                if (hadStartedJump) onJumpReleased?.invoke(finalHold)
            }

            wasCharging -> {
                if (!hadStartedJump) onJumpPressed?.invoke()
                lastGestureLabel = if (finalHold < 0.12f) {
                    "JUMP:TAP"
                } else {
                    "JUMP:HOLD(${String.format("%.2f", finalHold)}s)"
                }
                onJumpReleased?.invoke(finalHold)
            }
        }
    }
}
