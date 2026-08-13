package com.anurag9000.forestrun.engine

import android.view.MotionEvent
import android.view.View

/**
 * Translates touch events into jump and duck callbacks.
 *
 * A touch is kept undecided for a very short gesture-arbitration window. A
 * downward swipe during that window becomes a duck without ever starting a
 * jump. A hold starts the jump after the window; a quick tap starts and
 * releases the jump together on finger-up.
 */
class InputHandler : View.OnTouchListener {
    var onGestureClassified: ((InputGestureKind) -> Unit)? = null
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
        private const val MAX_REPORTED_HOLD_S = 0.6f

        /**
         * Small delay used only to distinguish a swipe from a hold. Quick taps
         * are still committed immediately on ACTION_UP.
         */
        private const val JUMP_DECISION_DELAY_S = 0.075f
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> handleDown(event)
        // Keep ownership of the sequence while deliberately ignoring extra fingers.
        MotionEvent.ACTION_POINTER_DOWN -> primaryPointerId != INVALID_POINTER
        MotionEvent.ACTION_MOVE -> handleMove(event)
        MotionEvent.ACTION_UP -> {
            v.performClick()
            handleUp(event)
        }
        MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
        MotionEvent.ACTION_CANCEL -> handleCancel()
        else -> false
    }

    /** Called once per game frame while a primary pointer is active. */
    fun tick(deltaTime: Float) {
        if (primaryPointerId == INVALID_POINTER || !isChargingJump || isDucking) return
        if (!deltaTime.isFinite() || deltaTime <= 0f) return

        holdDuration = (holdDuration.toDouble() + deltaTime.toDouble())
            .coerceIn(0.0, MAX_REPORTED_HOLD_S.toDouble())
            .toFloat()
        if (!jumpStarted && holdDuration >= JUMP_DECISION_DELAY_S) {
            startJump()
        }
        if (jumpStarted) {
            onJumpHeld?.invoke(holdDuration)
        }
    }

    /**
     * Clears an in-flight gesture without synthesizing a gameplay action.
     *
     * This is used when the top-level screen changes while a pointer is still
     * down. Menu, Garden, death, pause, and restart transitions must not turn a
     * stale press into a delayed jump or duck release on the next frame.
     */
    fun cancelActiveGesture(notifyDuckRelease: Boolean = false) {
        if (primaryPointerId == INVALID_POINTER) return

        val wasDucking = isDucking
        clearGestureState()
        lastGestureLabel = "RESET"

        if (notifyDuckRelease && wasDucking) {
            onDuckReleased?.invoke()
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        if (primaryPointerId != INVALID_POINTER) return false

        val index = event.actionIndex
        if (index !in 0 until event.pointerCount) return false
        val pointerId = event.getPointerId(index)
        val startX = event.getX(index)
        val startY = event.getY(index)
        if (pointerId == INVALID_POINTER || !startX.isFinite() || !startY.isFinite()) {
            lastGestureLabel = "INVALID"
            return false
        }

        primaryPointerId = pointerId
        touchStartX = startX
        touchStartY = startY
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

        val currentY = event.getY(index)
        if (!currentY.isFinite()) return true
        val dy = currentY - touchStartY
        if (!isDucking && !jumpStarted && dy.isFinite() && dy > SWIPE_DOWN_THRESHOLD_PX) {
            isDucking = true
            isChargingJump = false
            holdDuration = 0f
            lastGestureLabel = "DUCK"
            onGestureClassified?.invoke(InputGestureKind.DUCK)
            onDuckPressed?.invoke()
        }
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        val index = event.actionIndex
        if (index !in 0 until event.pointerCount ||
            event.getPointerId(index) != primaryPointerId
        ) return false
        commitRelease(cancelled = false)
        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        val index = event.actionIndex
        if (index !in 0 until event.pointerCount) return false
        if (event.getPointerId(index) != primaryPointerId) return true
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
        onGestureClassified?.invoke(InputGestureKind.JUMP)
        onJumpPressed?.invoke()
    }

    private fun commitRelease(cancelled: Boolean) {
        if (primaryPointerId == INVALID_POINTER) return

        val wasDucking = isDucking
        val wasCharging = isChargingJump
        val hadStartedJump = jumpStarted
        val finalHold = holdDuration.takeIf { it.isFinite() }
            ?.coerceIn(0f, MAX_REPORTED_HOLD_S)
            ?: 0f

        clearGestureState()

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
                if (!hadStartedJump) {
                    onGestureClassified?.invoke(InputGestureKind.JUMP)
                    onJumpPressed?.invoke()
                }
                lastGestureLabel = if (finalHold < 0.12f) {
                    "JUMP:TAP"
                } else {
                    "JUMP:HOLD(${String.format("%.2f", finalHold)}s)"
                }
                onJumpReleased?.invoke(finalHold)
            }
        }
    }

    private fun clearGestureState() {
        primaryPointerId = INVALID_POINTER
        isDucking = false
        isChargingJump = false
        jumpStarted = false
        holdDuration = 0f
        touchStartX = 0f
        touchStartY = 0f
    }
}
