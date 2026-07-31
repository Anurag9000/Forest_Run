package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainMenuScreenTimeBoundaryTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `invalid frame deltas leave menu state unchanged`() {
        val screen = menu()

        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach(screen::update)

        assertEquals(MainMenuScreen.Phase.IDLE, screen.phase)
        assertEquals(0f, field(screen, "elapsedT"), 0f)
        assertEquals(0f, field(screen, "standTimer"), 0f)
    }

    @Test
    fun `large lifecycle delta is capped to one render-frame budget`() {
        val screen = menu()

        screen.update(10f)

        assertEquals(0.05f, field(screen, "elapsedT"), 0f)
    }

    @Test
    fun `valid frame repairs poisoned menu clocks`() {
        val screen = menu()
        screen.onTap(960f, 540f)
        setField(screen, "elapsedT", Float.NaN)
        setField(screen, "standTimer", Float.NaN)

        screen.update(0.016f)

        assertEquals(MainMenuScreen.Phase.STANDING_UP, screen.phase)
        assertEquals(0.016f, field(screen, "elapsedT"), 0f)
        assertEquals(0.016f, field(screen, "standTimer"), 0f)
    }

    @Test
    fun `bounded updates still complete the stand ritual`() {
        val screen = menu()
        screen.onTap(960f, 540f)

        repeat(40) { screen.update(0.05f) }

        assertEquals(MainMenuScreen.Phase.READY, screen.phase)
    }

    private fun menu(): MainMenuScreen =
        MainMenuScreen(context, spriteManager, 1920, 1080)

    private fun field(screen: MainMenuScreen, name: String): Float {
        val field = MainMenuScreen::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(screen)
    }

    private fun setField(screen: MainMenuScreen, name: String, value: Float) {
        val field = MainMenuScreen::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(screen, value)
    }
}
