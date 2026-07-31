package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameOverScreenTimeBoundaryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `invalid frame deltas leave the pulse clock unchanged`() {
        val screen = GameOverScreen(context, 1920, 1080)

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach(screen::update)

        assertEquals(0f, pulseTimer(screen), 0f)
    }

    @Test
    fun `large lifecycle delta is capped to one render-frame budget`() {
        val screen = GameOverScreen(context, 1920, 1080)

        screen.update(10f)

        assertEquals(0.05f, pulseTimer(screen), 0f)
    }

    @Test
    fun `ordinary frame delta remains exact`() {
        val screen = GameOverScreen(context, 1920, 1080)

        screen.update(0.016f)

        assertEquals(0.016f, pulseTimer(screen), 0f)
    }

    private fun pulseTimer(screen: GameOverScreen): Float {
        val field = GameOverScreen::class.java.getDeclaredField("pulseTimer")
        field.isAccessible = true
        return field.getFloat(screen)
    }
}
