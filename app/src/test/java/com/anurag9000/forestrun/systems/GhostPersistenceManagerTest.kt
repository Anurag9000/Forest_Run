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
import org.junit.Assert.assertNotNull
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
        deleteManifestFiles()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deleteGhostFiles()
        deletePromotionFiles()
        deleteManifestFiles()
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
    fun `new best ghost is visible immediately then strong sidecars and distance become durable`() {
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
            GhostArtifactManifestLoadResult.Present(strongManifest(frames, 640f)),
            manifestStore().load()
        )
        assertEquals(56L, manifestFile().length())
        assertFalse(promotionFile().exists())
        assertEquals(
            GhostPromotionRecoveryDisposition.ALREADY_APPLIED,
            GhostPersistenceManager.recoverPendingPromotion(context)
        )

        GhostPersistenceManager.clearMemoryForTests()
        assertEquals(frames, GhostPersistenceManager.loadLatest(context))
    }

    @Test
    fun `startup recovery repairs strong manifest and distance when durable ghost matches receipt`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(receiptStore().save(strongReceipt(frames, 900f)))

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(900f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
        assertEquals(
            GhostArtifactManifestLoadResult.Present(strongManifest(frames, 900f)),
            manifestStore().load()
        )
    }

    @Test
    fun `startup recovery upgrades legacy receipt to strong manifest`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        writeLegacyReceipt(frames, distanceM = 930f)

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(930f, SaveManager.loadBestDistance(context), 0f)
        val loaded = manifestStore().load()
        assertTrue(loaded is GhostArtifactManifestLoadResult.Present)
        assertNotNull((loaded as GhostArtifactManifestLoadResult.Present).manifest.sha256Hex)
        assertEquals(56L, manifestFile().length())
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
    }

    @Test
    fun `startup recovery repairs distance from strong manifest after receipt is gone`() {
        val frames = sampleFrames()
        val manifest = strongManifest(frames, distanceM = 1_050f)
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore().save(manifest))
        SaveManager.saveBestDistance(context, 200f)

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(1_050f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(GhostArtifactManifestLoadResult.Present(manifest), manifestStore().load())
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
    }

    @Test
    fun `startup recovery upgrades legacy manifest before distance repair`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        writeLegacyManifest(frames, distanceM = 1_075f)
        SaveManager.saveBestDistance(context, 200f)

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(1_075f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(
            GhostArtifactManifestLoadResult.Present(strongManifest(frames, 1_075f)),
            manifestStore().load()
        )
        assertEquals(56L, manifestFile().length())
    }

    @Test
    fun `startup recovery abandons strong receipt when candidate ghost never landed`() {
        val candidate = sampleFrames()
        val oldGhost = candidate.mapIndexed { index, frame ->
            if (index == 0) frame.copy(x = frame.x + 10f) else frame
        }
        assertTrue(SaveManager.saveGhostRun(context, oldGhost))
        assertTrue(receiptStore().save(strongReceipt(candidate, 1_100f)))

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST, disposition)
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(oldGhost, SaveManager.loadGhostRun(context))
        assertEquals(GhostPromotionReceiptLoadResult.Empty, receiptStore().load())
        assertEquals(GhostArtifactManifestLoadResult.Empty, manifestStore().load())
    }

    @Test
    fun `tampered strong manifest digest blocks distance repair without deleting ghost`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        val valid = strongManifest(frames, 1_200f)
        val tampered = valid.copy(
            sha256Hex = flipFirstHex(requireNotNull(valid.sha256Hex))
        )
        assertTrue(manifestStore().save(tampered))
        SaveManager.saveBestDistance(context, 100f)

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertEquals(100f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertTrue(manifestFile().exists())
    }

    @Test
    fun `tampered strong manifest distance blocks repair without deleting ghost`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        val valid = strongManifest(frames, 1_200f)
        assertTrue(manifestStore().save(valid.copy(distanceM = 1_201f)))
        SaveManager.saveBestDistance(context, 100f)

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertEquals(100f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `corrupt manifest blocks new promotion without deleting ghost`() {
        val frames = sampleFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        manifestFile().writeBytes(byteArrayOf(1, 2, 3, 4))

        val disposition = GhostPersistenceManager.recoverPendingPromotion(context)

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertTrue(manifestFile().exists())
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

    private fun strongReceipt(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionReceipt {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun strongManifest(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostArtifactManifest {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun writeLegacyReceipt(frames: List<GhostFrame>, distanceM: Float) {
        DataOutputStream(promotionFile().outputStream()).use { output ->
            output.writeInt(0x46524750)
            output.writeInt(1)
            output.writeFloat(distanceM)
            output.writeInt(frames.size)
            output.writeLong(GhostRunFingerprint.calculate(frames))
        }
    }

    private fun writeLegacyManifest(frames: List<GhostFrame>, distanceM: Float) {
        DataOutputStream(manifestFile().outputStream()).use { output ->
            output.writeInt(0x4652474D)
            output.writeInt(1)
            output.writeFloat(distanceM)
            output.writeInt(frames.size)
            output.writeLong(GhostRunFingerprint.calculate(frames))
        }
    }

    private fun flipFirstHex(value: String): String =
        (if (value.first() == '0') '1' else '0') + value.drop(1)

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

    private fun manifestStore(): AtomicFileGhostArtifactManifestStore =
        AtomicFileGhostArtifactManifestStore(
            context = context,
            ghostFilename = SaveManager.activeGhostFilenameForTests
        )

    private fun ghostFile(): File =
        File(context.filesDir, SaveManager.activeGhostFilenameForTests)

    private fun promotionFile(): File =
        File(context.filesDir, "${SaveManager.activeGhostFilenameForTests}.promotion")

    private fun manifestFile(): File =
        File(context.filesDir, "${SaveManager.activeGhostFilenameForTests}.manifest")

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

    private fun deleteManifestFiles() {
        val base = manifestFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }
}
