package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import java.io.DataOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostPersistenceManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
    }

    @Test
    fun `detached recorder snapshot remains stable while next run records`() {
        val recorder = GhostRecorder()
        val player = Player(1_920, 1_080, SpriteManager(context))

        recorder.record(1f / 30f, player)
        val completed = recorder.detachSnapshot()

        assertEquals(1, completed.size)
        assertTrue(recorder.frames.isEmpty())
        assertEquals(0f, recorder.runDuration, 0f)

        player.onJumpPressed()
        recorder.record(1f / 30f, player)

        assertEquals(1, completed.size)
        assertEquals(1, recorder.frames.size)
        assertFalse(completed === recorder.frames)
    }

    @Test
    fun `new best ghost is available before asynchronous disk write completes`() {
        val frames = sampleFrames()

        assertTrue(GhostPersistenceManager.saveBestRunAsync(context, frames))
        assertEquals(frames, GhostPersistenceManager.loadLatest(context))
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        GhostPersistenceManager.clearMemoryForTests()
        assertEquals(frames, GhostPersistenceManager.loadLatest(context))
    }

    @Test
    fun `atomic ghost round trip preserves every frame`() {
        val frames = sampleFrames()

        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `truncated or trailing ghost payload is rejected`() {
        val file = ghostFile()
        file.writeBytes(byteArrayOf(0, 0, 0, 1, 0, 0, 0))
        assertTrue(SaveManager.loadGhostRun(context).isEmpty())

        assertTrue(SaveManager.saveGhostRun(context, sampleFrames()))
        file.appendBytes(byteArrayOf(7))
        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
    }

    @Test
    fun `oversized frame count is rejected before allocation`() {
        DataOutputStream(ghostFile().outputStream()).use { output ->
            output.writeInt(GhostRecorder.MAX_FRAMES + 1)
        }

        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
    }

    @Test
    fun `non finite or invalid state data is rejected`() {
        val invalid = listOf(
            GhostFrame(
                t = Float.NaN,
                x = 100f,
                y = 200f,
                stateOrdinal = PlayerState.RUNNING.ordinal,
                scaleX = 1f,
                scaleY = 1f
            )
        )

        assertFalse(SaveManager.saveGhostRun(context, invalid))
        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
    }

    private fun sampleFrames(): List<GhostFrame> = listOf(
        GhostFrame(
            t = 0.033f,
            x = 480f,
            y = 740f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        ),
        GhostFrame(
            t = 0.066f,
            x = 480f,
            y = 705f,
            stateOrdinal = PlayerState.JUMPING.ordinal,
            scaleX = 0.85f,
            scaleY = 1.2f
        )
    )

    private fun ghostFile(): File = File(context.filesDir, "ghost_run.bin")

    private fun deleteGhostFiles() {
        ghostFile().delete()
        File(context.filesDir, "ghost_run.bin.bak").delete()
        File(context.filesDir, "ghost_run.bin.new").delete()
    }
}
