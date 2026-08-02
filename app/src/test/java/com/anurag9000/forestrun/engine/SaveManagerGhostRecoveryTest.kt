package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.systems.GhostFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveManagerGhostRecoveryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        deleteActiveGhostFiles()
    }

    @Test
    fun `ghost availability is false without base or backup`() {
        assertFalse(SaveManager.hasGhostRun(context))
        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
    }

    @Test
    fun `ghost availability recognizes recoverable primary backup`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        moveBaseToBackup()

        assertTrue(SaveManager.hasGhostRun(context))
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `ghost availability recognizes recoverable compatibility backup`() {
        SaveManager.useCompatibilityPreferences(17)
        deleteActiveGhostFiles()
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        moveBaseToBackup()

        assertTrue(SaveManager.hasGhostRun(context))
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    private fun moveBaseToBackup() {
        val base = activeBaseFile()
        val backup = File(base.path + ".bak")
        backup.delete()
        assertTrue(base.isFile)
        assertTrue(base.renameTo(backup))
        assertFalse(base.exists())
        assertTrue(backup.isFile)
    }

    private fun deleteActiveGhostFiles() {
        val base = activeBaseFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private fun activeBaseFile(): File =
        File(context.filesDir, SaveManager.activeGhostFilenameForTests)

    private fun sampleFrames(): List<GhostFrame> = listOf(
        GhostFrame(0.016f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.032f, 104f, 180f, 1, 0.9f, 1.1f)
    )
}
