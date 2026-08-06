package com.anurag9000.forestrun

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.InputHandler
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class ForestRunAccessibilityDelegateTest {
    @Test
    fun nodeExposesAllStableRootActions() {
        val host = View(ApplicationProvider.getApplicationContext())
        val delegate = ForestRunAccessibilityDelegate(InputHandler())
        val info = AccessibilityNodeInfo.obtain()

        delegate.onInitializeAccessibilityNodeInfo(host, info)

        val actionIds = info.actionList.map { it.id }.toSet()
        assertTrue(AccessibilityNodeInfo.ACTION_CLICK in actionIds)
        assertTrue(ForestRunAccessibilityActions.CONTINUE_OR_RESTART in actionIds)
        assertTrue(ForestRunAccessibilityActions.OPEN_GARDEN in actionIds)
        assertTrue(ForestRunAccessibilityActions.RETURN_HOME in actionIds)
        assertTrue(ForestRunAccessibilityActions.TAP_JUMP in actionIds)
        assertTrue(ForestRunAccessibilityActions.HOLD_JUMP in actionIds)
        assertTrue(ForestRunAccessibilityActions.DUCK in actionIds)
        assertTrue(info.isFocusable)
        assertTrue(info.isClickable)
        info.recycle()
    }

    @Test
    fun tapActionsUseTheExistingTouchPath() {
        val host = View(ApplicationProvider.getApplicationContext())
        host.layout(0, 0, 1_000, 500)
        val events = mutableListOf<Triple<Int, Float, Float>>()
        host.setOnTouchListener { _, event ->
            events += Triple(event.actionMasked, event.x, event.y)
            true
        }
        attachForestRunAccessibility(host, InputHandler())

        assertTrue(
            host.performAccessibilityAction(
                ForestRunAccessibilityActions.OPEN_GARDEN,
                null
            )
        )

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), events.map { it.first })
        assertEquals(160f, events.first().second, 0.001f)
        assertEquals(460f, events.first().third, 0.001f)
    }

    @Test
    fun tapAndHoldJumpPreserveCallbackOrderAndDurations() {
        val host = View(ApplicationProvider.getApplicationContext())
        val input = InputHandler()
        val trace = mutableListOf<String>()
        input.onJumpPressed = { trace += "press" }
        input.onJumpHeld = { trace += "hold:$it" }
        input.onJumpReleased = { trace += "release:$it" }
        attachForestRunAccessibility(host, input)

        assertTrue(
            host.performAccessibilityAction(
                ForestRunAccessibilityActions.TAP_JUMP,
                null
            )
        )
        assertEquals(listOf("press", "release:0.0"), trace)

        trace.clear()
        assertTrue(
            host.performAccessibilityAction(
                ForestRunAccessibilityActions.HOLD_JUMP,
                null
            )
        )
        assertEquals(listOf("press", "hold:0.36", "release:0.36"), trace)
    }

    @Test
    fun duckUsesBoundedPressThenRelease() {
        val host = View(ApplicationProvider.getApplicationContext())
        val input = InputHandler()
        val trace = mutableListOf<String>()
        input.onDuckPressed = { trace += "press" }
        input.onDuckReleased = { trace += "release" }
        attachForestRunAccessibility(host, input)

        assertTrue(
            host.performAccessibilityAction(
                ForestRunAccessibilityActions.DUCK,
                null
            )
        )
        assertEquals(listOf("press"), trace)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertEquals(listOf("press", "release"), trace)
    }

    @Test
    fun actionsFailClosedWhenSurfaceOrCallbacksAreUnavailable() {
        val host = View(ApplicationProvider.getApplicationContext())
        val delegate = ForestRunAccessibilityDelegate(InputHandler())

        assertFalse(
            delegate.performAccessibilityAction(
                host,
                ForestRunAccessibilityActions.CONTINUE_OR_RESTART,
                null
            )
        )
        assertFalse(
            delegate.performAccessibilityAction(
                host,
                ForestRunAccessibilityActions.TAP_JUMP,
                null
            )
        )
        assertFalse(
            delegate.performAccessibilityAction(
                host,
                ForestRunAccessibilityActions.DUCK,
                null
            )
        )
    }
}
