package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostPromotionRecoveryCoordinatorTest {

    @Test
    fun `promotion persists receipt ghost distance and clear in order`() {
        val events = mutableListOf<String>()
        val receiptStore = MemoryReceiptStore(events = events)
        val artifactStore = MemoryArtifactStore(events = events, bestDistanceM = 120f)
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)
        val frames = frames()

        val result = coordinator.persist(frames, distanceM = 480f)

        assertTrue(result.complete)
        assertEquals(frames, artifactStore.ghost)
        assertEquals(480f, artifactStore.bestDistanceM, 0f)
        assertEquals(null, receiptStore.receipt)
        assertEquals(
            listOf(
                "receipt:save:480.0:2",
                "ghost:save:2",
                "distance:load",
                "distance:save:480.0",
                "receipt:clear"
            ),
            events
        )
    }

    @Test
    fun `matching durable ghost repairs missing best distance`() {
        val frames = frames()
        val receipt = receipt(frames, distanceM = 700f)
        val receiptStore = MemoryReceiptStore(receipt = receipt)
        val artifactStore = MemoryArtifactStore(
            ghost = frames,
            bestDistanceM = 200f
        )
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(700f, artifactStore.bestDistanceM, 0f)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `already applied promotion only clears its receipt`() {
        val frames = frames()
        val receiptStore = MemoryReceiptStore(receipt = receipt(frames, distanceM = 500f))
        val artifactStore = MemoryArtifactStore(
            ghost = frames,
            bestDistanceM = 900f
        )
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ALREADY_APPLIED, disposition)
        assertEquals(900f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `receipt for ghost that never became durable is abandoned safely`() {
        val candidate = frames()
        val oldGhost = candidate.mapIndexed { index, frame ->
            if (index == 0) frame.copy(x = frame.x + 1f) else frame
        }
        val receiptStore = MemoryReceiptStore(receipt = receipt(candidate, distanceM = 800f))
        val artifactStore = MemoryArtifactStore(
            ghost = oldGhost,
            bestDistanceM = 300f
        )
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)

        val disposition = coordinator.recover()

        assertEquals(
            GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST,
            disposition
        )
        assertEquals(300f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `failed ghost write leaves receipt and never advances distance`() {
        val receiptStore = MemoryReceiptStore()
        val artifactStore = MemoryArtifactStore(
            bestDistanceM = 100f,
            ghostSaveSucceeds = false
        )
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)

        val result = coordinator.persist(frames(), distanceM = 600f)

        assertTrue(result.receiptDurable)
        assertFalse(result.ghostDurable)
        assertFalse(result.distanceDurable)
        assertFalse(result.receiptCleared)
        assertEquals(100f, artifactStore.bestDistanceM, 0f)
        assertTrue(receiptStore.receipt != null)
    }

    @Test
    fun `failed distance write leaves matching receipt for later repair`() {
        val receiptStore = MemoryReceiptStore()
        val artifactStore = MemoryArtifactStore(
            bestDistanceM = 100f,
            distanceSaveSucceeds = false
        )
        val coordinator = GhostPromotionRecoveryCoordinator(receiptStore, artifactStore)
        val frames = frames()

        val result = coordinator.persist(frames, distanceM = 600f)

        assertTrue(result.ghostDurable)
        assertFalse(result.distanceDurable)
        assertTrue(receiptStore.receipt != null)
        assertEquals(frames, artifactStore.ghost)

        artifactStore.distanceSaveSucceeds = true
        val recovered = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, recovered)
        assertEquals(600f, artifactStore.bestDistanceM, 0f)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `corrupt receipt blocks a new promotion`() {
        val receiptStore = MemoryReceiptStore(
            loadOverride = GhostPromotionReceiptLoadResult.Corrupt
        )
        val coordinator = GhostPromotionRecoveryCoordinator(
            receiptStore,
            MemoryArtifactStore()
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_RECEIPT, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertEquals(0, receiptStore.clearCount)
    }

    @Test
    fun `fingerprint changes for every persisted frame component`() {
        val base = frames()
        val baseFingerprint = GhostRunFingerprint.calculate(base)
        val variants = listOf(
            base.toMutableList().apply { this[0] = this[0].copy(t = 0.01f) },
            base.toMutableList().apply { this[0] = this[0].copy(x = 101f) },
            base.toMutableList().apply { this[0] = this[0].copy(y = 201f) },
            base.toMutableList().apply { this[0] = this[0].copy(stateOrdinal = 1) },
            base.toMutableList().apply { this[0] = this[0].copy(scaleX = 0.9f) },
            base.toMutableList().apply { this[0] = this[0].copy(scaleY = 1.1f) },
            base + GhostFrame(0.08f, 108f, 192f, 0, 1f, 1f)
        )

        variants.forEach { variant ->
            assertFalse(baseFingerprint == GhostRunFingerprint.calculate(variant))
        }
        assertEquals(baseFingerprint, GhostRunFingerprint.calculate(base.toList()))
    }

    private fun receipt(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionReceipt = GhostPromotionReceipt(
        distanceM = distanceM,
        frameCount = frames.size,
        fingerprint = GhostRunFingerprint.calculate(frames)
    )

    private fun frames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )

    private class MemoryReceiptStore(
        var receipt: GhostPromotionReceipt? = null,
        private val loadOverride: GhostPromotionReceiptLoadResult? = null,
        private val saveSucceeds: Boolean = true,
        private val clearSucceeds: Boolean = true,
        private val events: MutableList<String> = mutableListOf()
    ) : GhostPromotionReceiptStore {
        var clearCount = 0
            private set

        override fun load(): GhostPromotionReceiptLoadResult =
            loadOverride ?: receipt?.let(GhostPromotionReceiptLoadResult::Pending)
            ?: GhostPromotionReceiptLoadResult.Empty

        override fun save(receipt: GhostPromotionReceipt): Boolean {
            events += "receipt:save:${receipt.distanceM}:${receipt.frameCount}"
            if (saveSucceeds) this.receipt = receipt
            return saveSucceeds
        }

        override fun clear(): Boolean {
            clearCount++
            events += "receipt:clear"
            if (clearSucceeds) receipt = null
            return clearSucceeds
        }
    }

    private class MemoryArtifactStore(
        var ghost: List<GhostFrame> = emptyList(),
        var bestDistanceM: Float = 0f,
        private val ghostSaveSucceeds: Boolean = true,
        var distanceSaveSucceeds: Boolean = true,
        private val events: MutableList<String> = mutableListOf()
    ) : GhostPromotionArtifactStore {
        var distanceSaveCount = 0
            private set

        override fun loadGhost(): List<GhostFrame> {
            events += "ghost:load"
            return ghost
        }

        override fun saveGhost(frames: List<GhostFrame>): Boolean {
            events += "ghost:save:${frames.size}"
            if (ghostSaveSucceeds) ghost = frames.toList()
            return ghostSaveSucceeds
        }

        override fun loadBestDistanceM(): Float {
            events += "distance:load"
            return bestDistanceM
        }

        override fun saveBestDistanceM(distanceM: Float): Boolean {
            distanceSaveCount++
            events += "distance:save:$distanceM"
            if (distanceSaveSucceeds) bestDistanceM = distanceM
            return distanceSaveSucceeds
        }
    }
}
