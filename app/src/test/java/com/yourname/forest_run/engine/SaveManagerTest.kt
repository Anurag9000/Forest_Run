package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "ghost_run.bin").delete()
    }

    @Test
    fun `best distance persists across reloads`() {
        SaveManager.saveBestDistance(context, 123.5f)

        assertEquals(123.5f, SaveManager.loadBestDistance(context), 0.0001f)
    }

    @Test
    fun `ghost frames round trip through binary persistence`() {
        val frames = listOf(
            GhostFrame(0.016f, 100f, 200f, 0, 1f, 1f),
            GhostFrame(0.032f, 100f, 180f, 1, 0.9f, 1.1f)
        )

        SaveManager.saveGhostRun(context, frames)
        val reloaded = SaveManager.loadGhostRun(context)

        assertTrue(SaveManager.hasGhostRun(context))
        assertEquals(frames, reloaded)
    }
}
