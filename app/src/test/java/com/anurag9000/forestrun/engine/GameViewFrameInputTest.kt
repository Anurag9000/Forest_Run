package com.anurag9000.forestrun.engine

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameViewFrameInputTest {
    private lateinit var context: Context
    private lateinit var view: GameView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        view = GameView(context)
    }

    @Test
    fun `rejected deltas do not count a frame`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0.001f)
            .forEach { delta ->
                view.update(delta)
                assertEquals(0L, view.debugFrameCounter)
            }
    }

    @Test
    fun `ordinary delta counts one frame`() {
        view.update(0.016f)
        assertEquals(1L, view.debugFrameCounter)
    }

    @Test
    fun `large finite delta counts one bounded frame`() {
        view.update(Float.MAX_VALUE)
        assertEquals(1L, view.debugFrameCounter)
    }

    @Test
    fun `pause discards undecided touch before it can become a delayed jump`() {
        var jumpPresses = 0
        view.inputHandler.onJumpPressed = { jumpPresses++ }
        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)

        view.pause()
        view.inputHandler.tick(0.2f)

        assertEquals(0, jumpPresses)
        assertFalse(view.inputHandler.isChargingJump)
    }

    @Test
    fun `pause stops hold callbacks from an already started jump gesture`() {
        var jumpPresses = 0
        var heldCallbacks = 0
        view.inputHandler.onJumpPressed = { jumpPresses++ }
        view.inputHandler.onJumpHeld = { heldCallbacks++ }
        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        view.inputHandler.tick(0.08f)
        assertEquals(1, jumpPresses)
        assertEquals(1, heldCallbacks)

        view.pause()
        view.inputHandler.tick(0.2f)

        assertEquals(1, jumpPresses)
        assertEquals(1, heldCallbacks)
        assertFalse(view.inputHandler.isChargingJump)
    }

    @Test
    fun `pause releases active touch duck and clears gesture ownership`() {
        var duckPresses = 0
        var duckReleases = 0
        view.inputHandler.onDuckPressed = { duckPresses++ }
        view.inputHandler.onDuckReleased = { duckReleases++ }
        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        dispatch(MotionEvent.ACTION_MOVE, 102f, 205f)

        view.pause()

        assertEquals(1, duckPresses)
        assertEquals(1, duckReleases)
        assertFalse(view.inputHandler.isDucking)
        assertFalse(view.inputHandler.isChargingJump)
    }

    @Test
    fun `pause cancels accessibility duck release and restores Player stance`() {
        val player = testPlayer()
        player.onDuckPressed()
        attachPlayer(player)

        val gateField = GameView::class.java.getDeclaredField("accessibilityDuckReleaseGate")
        gateField.isAccessible = true
        val gate = gateField.get(view) as LatestRequestGate
        val releaseToken = gate.begin()

        view.pause()

        assertEquals(PlayerState.RUNNING, player.state)
        assertFalse(gate.isCurrent(releaseToken))
    }

    @Test
    fun `surface destruction releases Player duck and blocks surface input`() {
        val player = testPlayer()
        player.onDuckPressed()
        attachPlayer(player)

        view.surfaceDestroyed(view.holder)

        assertEquals(PlayerState.RUNNING, player.state)
        val surfaceInputField = GameView::class.java.getDeclaredField("surfaceInputAvailable")
        surfaceInputField.isAccessible = true
        assertFalse(surfaceInputField.getBoolean(view))
    }

    @Test
    fun `resume rejects stale surface readiness until a surface is reinitialized`() {
        attachPlayer(testPlayer())
        setPrivateField("appState", AppGameState.PLAYING)
        setPrivateField("runState", RunState.PLAYING)
        setPrivateField("surfaceInputAvailable", true)
        var jumpPresses = 0
        view.inputHandler.onJumpPressed = { jumpPresses++ }

        view.pause()
        dispatchToView(MotionEvent.ACTION_DOWN, 100f, 100f)
        assertFalse(view.inputHandler.isChargingJump)
        assertEquals(0, jumpPresses)

        assertFalse(view.holder.surface?.isValid == true)
        view.resume()
        dispatchToView(MotionEvent.ACTION_DOWN, 100f, 100f)
        assertFalse(view.inputHandler.isChargingJump)
        assertEquals(0, jumpPresses)

        // Model the successful readiness publication made by surface
        // initialization after a newly created, valid Surface is available.
        setPrivateField("surfaceInputAvailable", true)
        dispatchToView(MotionEvent.ACTION_DOWN, 100f, 100f)
        assertEquals(true, view.inputHandler.isChargingJump)
        dispatchToView(MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(1, jumpPresses)
    }

    private fun testPlayer(): Player =
        Player(1080, 1920, SpriteManager(context), 1500f)

    private fun attachPlayer(player: Player) {
        val field = GameView::class.java.getDeclaredField("player")
        field.isAccessible = true
        field.set(view, player)
    }

    private fun setPrivateField(name: String, value: Any) {
        val field = GameView::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(view, value)
    }

    private fun dispatch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            view.inputHandler.onTouch(view, event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatchToView(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
