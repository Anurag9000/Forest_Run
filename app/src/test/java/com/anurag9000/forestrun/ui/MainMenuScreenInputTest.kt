package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainMenuScreenInputTest {

    private lateinit var context: Context
    private lateinit var screen: MainMenuScreen
    private var gardenTaps = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        screen = MainMenuScreen(
            context = context,
            spriteManager = SpriteManager(context),
            screenW = 1_920,
            screenH = 1_080
        )
        gardenTaps = 0
        screen.onGardenTap = { gardenTaps += 1 }
    }

    @Test
    fun `nonfinite taps cannot mutate menu state`() {
        val nonfinite = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        for (value in nonfinite) {
            screen.onTap(value, 1_000f)
            screen.onTap(100f, value)
        }

        assertEquals(MainMenuScreen.Phase.IDLE, screen.phase)
        assertEquals(0, gardenTaps)
        assertFalse(screen.shouldStartRun)
    }

    @Test
    fun `finite garden tap retains garden behavior`() {
        screen.onTap(100f, 1_000f)

        assertEquals(MainMenuScreen.Phase.IDLE, screen.phase)
        assertEquals(1, gardenTaps)
        assertFalse(screen.shouldStartRun)
    }

    @Test
    fun `finite ordinary tap retains ritual behavior`() {
        screen.onTap(1_000f, 500f)

        assertEquals(MainMenuScreen.Phase.STANDING_UP, screen.phase)
        assertEquals(0, gardenTaps)
        assertFalse(screen.shouldStartRun)
    }
}
