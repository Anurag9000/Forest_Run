
package com.yourname.forest_run.engine

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputHandlerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val view = View(context)

    @Test
    fun `quick tap commits one jump and no duck`() {
        val handler = InputHandler()
        var presses = 0
        var releases = 0
        var ducks = 0
        handler.onJumpPressed = { presses++ }
        handler.onJumpReleased = { releases++ }
        handler.onDuckPressed = { ducks++ }

        handler.onTouch(view, event(MotionEvent.ACTION_DOWN, 0L, 100f))
        handler.onTouch(view, event(MotionEvent.ACTION_UP, 20L, 100f))

        assertEquals(1, presses)
        assertEquals(1, releases)
        assertEquals(0, ducks)
    }

    @Test
    fun `down swipe commits duck without a jump`() {
        val handler = InputHandler()
        var presses = 0
        var ducks = 0
        handler.onJumpPressed = { presses++ }
        handler.onDuckPressed = { ducks++ }

        handler.onTouch(view, event(MotionEvent.ACTION_DOWN, 0L, 100f))
        handler.onTouch(view, event(MotionEvent.ACTION_MOVE, 20L, 220f))
        handler.onTouch(view, event(MotionEvent.ACTION_UP, 40L, 220f))

        assertEquals(0, presses)
        assertEquals(1, ducks)
        assertFalse(handler.isDucking)
        assertTrue(handler.lastGestureLabel == "DUCK_END")
    }

    private fun event(action: Int, timeMs: Long, y: Float): MotionEvent =
        MotionEvent.obtain(0L, timeMs, action, 300f, y, 0)
}
