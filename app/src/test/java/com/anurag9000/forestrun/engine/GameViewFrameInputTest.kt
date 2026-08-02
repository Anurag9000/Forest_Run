package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
