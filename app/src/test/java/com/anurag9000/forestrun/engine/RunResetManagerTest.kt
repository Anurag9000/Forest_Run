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
}
