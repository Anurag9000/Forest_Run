package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HudTimeBoundaryTest {
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
    fun `invalid deltas leave HUD animation state unchanged`() {
        val state = GameStateManager(context)
        state.collectSeed()
        val hud = HUD(context, 1920, 1080)

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { hud.update(it, state) }

        assertEquals(0f, field(hud, "displayedFill"), 0f)
        assertEquals(0f, field(hud, "bloomPulse"), 0f)
        assertEquals(0f, field(hud, "newBadge"), 0f)
    }

    @Test
    fun `large lifecycle delta is capped to one render-frame budget`() {
        val state = GameStateManager(context)
        state.collectSeed()
        val hud = HUD(context, 1920, 1080)

        hud.update(10f, state)

        assertEquals(0.4f, field(hud, "displayedFill"), 0.0001f)
    }

    @Test
    fun `valid update repairs poisoned animation fields`() {
        val state = GameStateManager(context)
        state.debugActivateBloom()
        state.addBonus(points = 1)
        val hud = HUD(context, 1920, 1080)
        setField(hud, "displayedFill", Float.NaN)
        setField(hud, "bloomPulse", Float.NaN)
        setField(hud, "newBadge", Float.NaN)

        hud.update(0.016f, state)

        assertTrue(field(hud, "displayedFill").isFinite())
        assertTrue(field(hud, "bloomPulse").isFinite())
        assertTrue(field(hud, "newBadge").isFinite())
    }

    @Test
    fun `inactive presentation pulses reset deterministically`() {
        val state = GameStateManager(context)
        val hud = HUD(context, 1920, 1080)
        setField(hud, "bloomPulse", 2f)
        setField(hud, "newBadge", 3f)

        hud.update(0.016f, state)

        assertEquals(0f, field(hud, "bloomPulse"), 0f)
        assertEquals(0f, field(hud, "newBadge"), 0f)
    }

    private fun field(hud: HUD, name: String): Float {
        val field = HUD::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(hud)
    }

    private fun setField(hud: HUD, name: String, value: Float) {
        val field = HUD::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(hud, value)
    }
}
