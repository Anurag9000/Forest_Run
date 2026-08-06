package com.anurag9000.forestrun

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import com.anurag9000.forestrun.engine.InputHandler

/** Stable custom action IDs exposed by the single Canvas game surface. */
internal object ForestRunAccessibilityActions {
    const val CONTINUE_OR_RESTART = 0x0102_0001
    const val OPEN_GARDEN = 0x0102_0002
    const val RETURN_HOME = 0x0102_0003
    const val TAP_JUMP = 0x0102_0004
    const val HOLD_JUMP = 0x0102_0005
    const val DUCK = 0x0102_0006
}

/**
 * Root accessibility bridge for Forest Run's custom Canvas surface.
 *
 * The bridge deliberately routes through the same touch and [InputHandler]
 * callbacks as ordinary interaction. GameView's existing app/run-state guards
 * therefore remain authoritative: accessibility actions cannot start physics,
 * persistence, or gameplay input in a state where touch would be rejected.
 *
 * This root action surface is useful immediately and is also the stable action
 * sink for a later virtual-node hierarchy covering individual Garden plants,
 * wardrobe cards, settings controls, and Rest-summary fields.
 */
internal class ForestRunAccessibilityDelegate(
    private val inputHandler: InputHandler
) : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = "com.anurag9000.forestrun.ForestRunGameSurface"
        info.isFocusable = true
        info.isClickable = true
        info.contentDescription =
            "Forest Run game. Use accessibility actions to continue or restart, " +
                "open the Garden, return home, jump, hold jump, or duck."
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.CONTINUE_OR_RESTART,
                "Continue or restart"
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.OPEN_GARDEN,
                "Open Garden"
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.RETURN_HOME,
                "Return home"
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.TAP_JUMP,
                "Tap jump"
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.HOLD_JUMP,
                "Hold jump"
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ForestRunAccessibilityActions.DUCK,
                "Duck"
            )
        )
    }

    override fun performAccessibilityAction(
        host: View,
        action: Int,
        args: Bundle?
    ): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_CLICK,
        ForestRunAccessibilityActions.CONTINUE_OR_RESTART ->
            dispatchTap(host, xFraction = 0.50f, yFraction = 0.50f)

        ForestRunAccessibilityActions.OPEN_GARDEN ->
            dispatchTap(host, xFraction = 0.16f, yFraction = 0.92f)

        ForestRunAccessibilityActions.RETURN_HOME ->
            dispatchTap(host, xFraction = 0.50f, yFraction = 0.94f)

        ForestRunAccessibilityActions.TAP_JUMP ->
            performJump(host, holdSeconds = 0f)

        ForestRunAccessibilityActions.HOLD_JUMP ->
            performJump(host, holdSeconds = 0.36f)

        ForestRunAccessibilityActions.DUCK ->
            performDuck(host)

        else -> super.performAccessibilityAction(host, action, args)
    }

    private fun performJump(host: View, holdSeconds: Float): Boolean {
        val press = inputHandler.onJumpPressed ?: return false
        val release = inputHandler.onJumpReleased ?: return false
        press()
        if (holdSeconds > 0f) inputHandler.onJumpHeld?.invoke(holdSeconds)
        release(holdSeconds)
        host.announceForAccessibility(
            if (holdSeconds > 0f) "Long jump" else "Jump"
        )
        return true
    }

    private fun performDuck(host: View): Boolean {
        val press = inputHandler.onDuckPressed ?: return false
        val release = inputHandler.onDuckReleased ?: return false
        press()
        host.announceForAccessibility("Duck")
        host.postDelayed({ release() }, DUCK_DURATION_MS)
        return true
    }

    private fun dispatchTap(host: View, xFraction: Float, yFraction: Float): Boolean {
        val width = host.width
        val height = host.height
        if (width <= 0 || height <= 0) return false
        val x = width * xFraction.coerceIn(0f, 1f)
        val y = height * yFraction.coerceIn(0f, 1f)
        if (!x.isFinite() || !y.isFinite()) return false

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + TAP_DURATION_MS,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )
        return try {
            val acceptedDown = host.dispatchTouchEvent(down)
            val acceptedUp = host.dispatchTouchEvent(up)
            acceptedDown || acceptedUp
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private companion object {
        const val TAP_DURATION_MS = 24L
        const val DUCK_DURATION_MS = 360L
    }
}

internal fun attachForestRunAccessibility(
    host: View,
    inputHandler: InputHandler
) {
    ViewCompat.setImportantForAccessibility(
        host,
        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES
    )
    host.isFocusable = true
    host.isFocusableInTouchMode = true
    host.accessibilityDelegate = ForestRunAccessibilityDelegate(inputHandler)
}
