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
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
        deletePromotionFiles()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
        deletePromotionFiles()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SaveManager.usePrimaryPreferences()
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
    fun `new best ghost is visible immediately then ghost and distance become durable`() {
        val frames = sampleFrames()

        assertTrue(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = frames,
                distanceM = 640f
            )
        )
        assertEquals(frames, GhostPersistenceManager.loadLatest(context))
        assertEquals(640f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        assertEquals(640f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertEquals(
            GhostPromotionRecoveryDisposition.EMPTY,
            GhostPersistenceManager.recoverPendingPromotion(context)
        )

        GhostPersistenceManager.clearMemoryForTests()
        assertEquals(frames, GhostPersistenceManager.loadLatest(context))
    }

    @Test
    fun `startup recovery repairs distance when durable ghost matches receipt`() {
        val frames = sampleFrames()
        val receipt = GhostPromotionReceipt(
            distanceM = 900f,
            frameCount = frames.size,
            fingerprint = GhostRunFingerprint.calculate(frames)
        )
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(receiptStore().save(receipt))

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(900f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
    }

    @Test
    fun `startup recovery abandons receipt when candidate ghost never landed`() {
        val candidate = sampleFrames()
        val oldGhost = candidate.mapIndexed { index, frame ->
            if (index == 0) frame.copy(x = frame.x + 10f) else frame
        }
        assertTrue(SaveManager.saveGhostRun(context, oldGhost))
        assertTrue(
            receiptStore().save(
                GhostPromotionReceipt(
                    distanceM = 1_100f,
                    frameCount = candidate.size,
                    fingerprint = GhostRunFingerprint.calculate(candidate)
                )
            )
        )

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(
            GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST,
            disposition
        )
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(oldGhost, SaveManager.loadGhostRun(context))
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
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

    private fun receiptStore(): AtomicFileGhostPromotionReceiptStore =
        AtomicFileGhostPromotionReceiptStore(
            context = context,
            ghostFilename = SaveManager.activeGhostFilenameForTests
        )

    private fun ghostFile(): File =
        File(context.filesDir, SaveManager.activeGhostFilenameForTests)

    private fun promotionFile(): File =
        File(context.filesDir, "${SaveManager.activeGhostFilenameForTests}.promotion")

    private fun deleteGhostFiles() {
        val base = ghostFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private fun deletePromotionFiles() {
        val base = promotionFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }
}
