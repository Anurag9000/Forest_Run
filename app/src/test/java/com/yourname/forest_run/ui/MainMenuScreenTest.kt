package com.yourname.forest_run.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainMenuScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `garden hotspot triggers callback from idle screen`() {
        val screen = MainMenuScreen(context, 1_920, 1_080)
        var gardenTapped = false
        screen.onGardenTap = { gardenTapped = true }

        screen.onTap(50f, 1_050f)

        assertTrue(gardenTapped)
        assertEquals(MainMenuScreen.Phase.IDLE, screen.phase)
    }

    @Test
    fun `starting a run requires stand then ready tap`() {
        val screen = MainMenuScreen(context, 1_920, 1_080)

        screen.onTap(960f, 540f)
        assertEquals(MainMenuScreen.Phase.STANDING_UP, screen.phase)

        screen.update(2.0f)
        assertEquals(MainMenuScreen.Phase.READY, screen.phase)

        screen.onTap(960f, 540f)
        assertTrue(screen.shouldStartRun)
    }
}
