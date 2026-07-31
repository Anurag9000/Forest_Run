package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunResetManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `dying fraction tracks the recovery settle window`() {
        val manager = RunResetManager()
        val state = GameStateManager(context)

        manager.triggerDeath(state)
        manager.update(RunResetManager.DYING_DURATION_S / 2f, RunState.DYING)

        assertEquals(0.5f, manager.dyingFraction, 0.0001f)
    }

    @Test
    fun `begin restart resets dying fraction`() {
        val manager = RunResetManager()
        val state = GameStateManager(context)

        manager.triggerDeath(state)
        manager.update(RunResetManager.DYING_DURATION_S, RunState.DYING)
        manager.beginRestart()

        assertEquals(0f, manager.dyingFraction, 0.0001f)
    }

    @Test
    fun `game over and playing do not advance transition clocks`() {
        val manager = RunResetManager()
        val state = GameStateManager(context)
        manager.triggerDeath(state)

        assertEquals(RunState.GAME_OVER, manager.update(1_000f, RunState.GAME_OVER))
        assertEquals(0f, manager.dyingFraction, 0f)
        assertEquals(RunState.PLAYING, manager.update(1_000f, RunState.PLAYING))
        assertEquals(0, manager.restartFadeAlpha)
    }

    @Test
    fun `invalid deltas are no ops in timed states`() {
        val manager = RunResetManager()
        val state = GameStateManager(context)
        manager.triggerDeath(state)

        assertEquals(RunState.DYING, manager.update(Float.NaN, RunState.DYING))
        assertEquals(RunState.DYING, manager.update(-1f, RunState.DYING))
        assertEquals(RunState.DYING, manager.update(Float.POSITIVE_INFINITY, RunState.DYING))
        assertEquals(0f, manager.dyingFraction, 0f)

        manager.beginRestart()
        assertEquals(RunState.RESTARTING, manager.update(Float.NaN, RunState.RESTARTING))
        assertEquals(0, manager.restartFadeAlpha)
    }

    @Test
    fun `restart fade reaches an exact bounded terminal alpha`() {
        val manager = RunResetManager()
        manager.beginRestart()

        val result = manager.update(RunResetManager.RESTART_FADE_S, RunState.RESTARTING)

        assertEquals(RunState.PLAYING, result)
        assertEquals(255, manager.restartFadeAlpha)
    }
}
