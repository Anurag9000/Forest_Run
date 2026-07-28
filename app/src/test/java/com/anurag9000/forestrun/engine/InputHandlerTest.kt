package com.anurag9000.forestrun.engine

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputHandlerTest {
    private lateinit var view: View
    private lateinit var handler: InputHandler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        view = View(context)
        handler = InputHandler()
    }

    @Test
    fun `quick tap starts and releases one jump`() {
        var presses = 0
        var releases = 0
        var releasedHold = -1f
        handler.onJumpPressed = { presses++ }
        handler.onJumpReleased = {
            releases++
            releasedHold = it
        }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(0.02f)
        dispatch(MotionEvent.ACTION_UP, 100f, 100f)

        assertEquals(1, presses)
        assertEquals(1, releases)
        assertEquals(0.02f, releasedHold, 0.0001f)
        assertFalse(handler.isChargingJump)
    }

    @Test
    fun `swipe down is classified before any jump starts`() {
        var jumpPresses = 0
        var jumpReleases = 0
        var duckPresses = 0
        var duckReleases = 0
        handler.onJumpPressed = { jumpPresses++ }
        handler.onJumpReleased = { jumpReleases++ }
        handler.onDuckPressed = { duckPresses++ }
        handler.onDuckReleased = { duckReleases++ }

        dispatch(MotionEvent.ACTION_DOWN, 120f, 100f)
        handler.tick(0.03f)
        dispatch(MotionEvent.ACTION_MOVE, 122f, 205f)
        dispatch(MotionEvent.ACTION_UP, 122f, 205f)

        assertEquals(0, jumpPresses)
        assertEquals(0, jumpReleases)
        assertEquals(1, duckPresses)
        assertEquals(1, duckReleases)
        assertFalse(handler.isDucking)
    }

    @Test
    fun `hold starts after arbitration window and releases normally`() {
        var presses = 0
        var heldCallbacks = 0
        var releases = 0
        handler.onJumpPressed = { presses++ }
        handler.onJumpHeld = { heldCallbacks++ }
        handler.onJumpReleased = { releases++ }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(0.08f)

        assertEquals(1, presses)
        assertTrue(heldCallbacks > 0)
        assertTrue(handler.isChargingJump)

        dispatch(MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(1, releases)
    }

    @Test
    fun `cancel before gesture decision does not create a jump`() {
        var presses = 0
        var releases = 0
        handler.onJumpPressed = { presses++ }
        handler.onJumpReleased = { releases++ }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(0.02f)
        dispatch(MotionEvent.ACTION_CANCEL, 100f, 100f)

        assertEquals(0, presses)
        assertEquals(0, releases)
        assertEquals("CANCEL", handler.lastGestureLabel)
    }

    @Test
    fun `silent reset clears a started jump without synthesizing release`() {
        var presses = 0
        var releases = 0
        handler.onJumpPressed = { presses++ }
        handler.onJumpReleased = { releases++ }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(0.08f)
        handler.cancelActiveGesture()
        handler.tick(0.5f)
        dispatch(MotionEvent.ACTION_UP, 100f, 100f)

        assertEquals(1, presses)
        assertEquals(0, releases)
        assertFalse(handler.isChargingJump)
        assertFalse(handler.isDucking)
        assertEquals(0f, handler.holdDuration, 0f)
        assertEquals("RESET", handler.lastGestureLabel)
    }

    @Test
    fun `silent reset can release an active duck when explicitly requested`() {
        var duckReleases = 0
        handler.onDuckReleased = { duckReleases++ }

        dispatch(MotionEvent.ACTION_DOWN, 120f, 100f)
        dispatch(MotionEvent.ACTION_MOVE, 122f, 205f)
        handler.cancelActiveGesture(notifyDuckRelease = true)

        assertEquals(1, duckReleases)
        assertFalse(handler.isDucking)
        assertEquals("RESET", handler.lastGestureLabel)
    }

    private fun dispatch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(
            0L,
            16L,
            action,
            x,
            y,
            0
        )
        try {
            handler.onTouch(view, event)
        } finally {
            event.recycle()
        }
    }
}
