package com.anurag9000.forestrun.engine

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import kotlin.random.Random
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
    fun `hold duration saturates at gameplay maximum`() {
        var held = -1f
        var released = -1f
        handler.onJumpHeld = { held = it }
        handler.onJumpReleased = { released = it }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(Float.MAX_VALUE)
        dispatch(MotionEvent.ACTION_UP, 100f, 100f)

        assertEquals(0.6f, held, 0f)
        assertEquals(0.6f, released, 0f)
        assertTrue(handler.lastGestureLabel.startsWith("JUMP:HOLD"))
    }

    @Test
    fun `invalid tick deltas cannot poison a pending tap`() {
        var released = -1f
        handler.onJumpReleased = { released = it }

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        handler.tick(Float.NaN)
        handler.tick(Float.POSITIVE_INFINITY)
        handler.tick(-1f)
        dispatch(MotionEvent.ACTION_UP, 100f, 100f)

        assertEquals(0f, released, 0f)
        assertEquals("JUMP:TAP", handler.lastGestureLabel)
        assertFalse(handler.isChargingJump)
    }

    @Test
    fun `malformed initial coordinates are rejected without creating gesture state`() {
        assertFalse(dispatch(MotionEvent.ACTION_DOWN, Float.NaN, 100f))
        assertEquals("INVALID", handler.lastGestureLabel)
        assertFalse(handler.isChargingJump)
        assertFalse(handler.isDucking)
        assertEquals(0f, handler.holdDuration, 0f)

        assertFalse(dispatch(MotionEvent.ACTION_DOWN, 100f, Float.POSITIVE_INFINITY))
        assertFalse(handler.isChargingJump)
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

    @Test
    fun `swipe threshold is strict and stable across the arbitration boundary`() {
        val offsets = listOf(79.999f, 80f, 80.001f, 120f)
        for (offset in offsets) {
            handler = InputHandler()
            var jumpPresses = 0
            var jumpReleases = 0
            var duckPresses = 0
            var duckReleases = 0
            handler.onJumpPressed = { jumpPresses++ }
            handler.onJumpReleased = { jumpReleases++ }
            handler.onDuckPressed = { duckPresses++ }
            handler.onDuckReleased = { duckReleases++ }

            dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
            handler.tick(0.074f)
            dispatch(MotionEvent.ACTION_MOVE, 100f, 100f + offset)
            dispatch(MotionEvent.ACTION_UP, 100f, 100f + offset)

            if (offset > 80f) {
                assertEquals("offset=$offset jump presses", 0, jumpPresses)
                assertEquals("offset=$offset jump releases", 0, jumpReleases)
                assertEquals("offset=$offset duck presses", 1, duckPresses)
                assertEquals("offset=$offset duck releases", 1, duckReleases)
            } else {
                assertEquals("offset=$offset jump presses", 1, jumpPresses)
                assertEquals("offset=$offset jump releases", 1, jumpReleases)
                assertEquals("offset=$offset duck presses", 0, duckPresses)
                assertEquals("offset=$offset duck releases", 0, duckReleases)
            }
            assertFalse(handler.isChargingJump)
            assertFalse(handler.isDucking)
        }
    }

    @Test
    fun `seeded predecision gesture traces always resolve to exactly one action family`() {
        val random = Random(0xF0E57)
        repeat(1_024) { trace ->
            handler = InputHandler()
            var jumpPresses = 0
            var jumpReleases = 0
            var duckPresses = 0
            var duckReleases = 0
            handler.onJumpPressed = { jumpPresses++ }
            handler.onJumpReleased = { jumpReleases++ }
            handler.onDuckPressed = { duckPresses++ }
            handler.onDuckReleased = { duckReleases++ }

            val startX = random.nextInt(0, 1_920).toFloat()
            val startY = random.nextInt(0, 1_080).toFloat()
            val tick = random.nextInt(0, 75).toFloat() / 1_000f
            val dy = random.nextInt(-240, 241).toFloat()
            val expectedDuck = dy > 80f

            assertTrue("trace=$trace down", dispatch(MotionEvent.ACTION_DOWN, startX, startY))
            handler.tick(tick)
            assertTrue(
                "trace=$trace move",
                dispatch(MotionEvent.ACTION_MOVE, startX + random.nextInt(-80, 81), startY + dy)
            )
            assertTrue("trace=$trace up", dispatch(MotionEvent.ACTION_UP, startX, startY + dy))

            if (expectedDuck) {
                assertEquals("trace=$trace jump presses", 0, jumpPresses)
                assertEquals("trace=$trace jump releases", 0, jumpReleases)
                assertEquals("trace=$trace duck presses", 1, duckPresses)
                assertEquals("trace=$trace duck releases", 1, duckReleases)
            } else {
                assertEquals("trace=$trace jump presses", 1, jumpPresses)
                assertEquals("trace=$trace jump releases", 1, jumpReleases)
                assertEquals("trace=$trace duck presses", 0, duckPresses)
                assertEquals("trace=$trace duck releases", 0, duckReleases)
            }
            assertFalse("trace=$trace charging", handler.isChargingJump)
            assertFalse("trace=$trace ducking", handler.isDucking)
            assertEquals("trace=$trace reset hold", 0f, handler.holdDuration, 0f)
        }
    }

    @Test
    fun `once hold decision starts later downward motion cannot double classify`() {
        val downwardOffsets = listOf(81f, 120f, 500f, 10_000f)
        for (offset in downwardOffsets) {
            handler = InputHandler()
            var jumpPresses = 0
            var jumpReleases = 0
            var duckPresses = 0
            var duckReleases = 0
            handler.onJumpPressed = { jumpPresses++ }
            handler.onJumpReleased = { jumpReleases++ }
            handler.onDuckPressed = { duckPresses++ }
            handler.onDuckReleased = { duckReleases++ }

            dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
            handler.tick(0.075f)
            dispatch(MotionEvent.ACTION_MOVE, 100f, 100f + offset)
            dispatch(MotionEvent.ACTION_UP, 100f, 100f + offset)

            assertEquals("offset=$offset jump presses", 1, jumpPresses)
            assertEquals("offset=$offset jump releases", 1, jumpReleases)
            assertEquals("offset=$offset duck presses", 0, duckPresses)
            assertEquals("offset=$offset duck releases", 0, duckReleases)
        }
    }

    private fun dispatch(action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(
            0L,
            16L,
            action,
            x,
            y,
            0
        )
        return try {
            handler.onTouch(view, event)
        } finally {
            event.recycle()
        }
    }
}
