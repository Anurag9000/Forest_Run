package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostPromotionRecoveryCoordinatorTest {

    @Test
    fun `promotion persists strong receipt ghost manifest distance and clear in order`() {
        val events = mutableListOf<String>()
        val receiptStore = MemoryReceiptStore(events = events)
        val artifactStore = MemoryArtifactStore(events = events, bestDistanceM = 120f)
        val manifestStore = MemoryManifestStore(events = events)
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)
        val frames = frames()

        val result = coordinator.persist(frames, distanceM = 480f)

        assertTrue(result.complete)
        assertTrue(result.manifestDurable)
        assertEquals(frames, artifactStore.ghost)
        assertEquals(480f, artifactStore.bestDistanceM, 0f)
        assertEquals(manifest(frames, 480f), manifestStore.manifest)
        assertNotNull(manifestStore.manifest?.sha256Hex)
        assertEquals(null, receiptStore.receipt)
        assertEquals(
            listOf(
                "receipt:save:480.0:2",
                "ghost:save:2",
                "manifest:save:480.0:2",
                "distance:load",
                "distance:save:480.0",
                "receipt:clear"
            ),
            events
        )
    }

    @Test
    fun `matching durable ghost repairs manifest and missing best distance`() {
        val frames = frames()
        val receipt = receipt(frames, distanceM = 700f)
        val receiptStore = MemoryReceiptStore(receipt = receipt)
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 200f)
        val manifestStore = MemoryManifestStore()
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(700f, artifactStore.bestDistanceM, 0f)
        assertEquals(manifest(frames, 700f), manifestStore.manifest)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `legacy receipt recovery upgrades the durable manifest before distance`() {
        val frames = frames()
        val receiptStore = MemoryReceiptStore(
            receipt = legacyReceipt(frames, distanceM = 710f)
        )
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 100f)
        val manifestStore = MemoryManifestStore()
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(manifest(frames, 710f), manifestStore.manifest)
        assertNotNull(manifestStore.manifest?.sha256Hex)
        assertEquals(710f, artifactStore.bestDistanceM, 0f)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `already applied promotion only ensures manifest and clears receipt`() {
        val frames = frames()
        val expectedManifest = manifest(frames, 500f)
        val receiptStore = MemoryReceiptStore(receipt = receipt(frames, distanceM = 500f))
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 900f)
        val manifestStore = MemoryManifestStore(manifest = expectedManifest)
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ALREADY_APPLIED, disposition)
        assertEquals(900f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(0, manifestStore.saveCount)
        assertEquals(expectedManifest, manifestStore.manifest)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `receipt for ghost that never became durable preserves old valid manifest`() {
        val candidate = frames()
        val oldGhost = candidate.mapIndexed { index, frame ->
            if (index == 0) frame.copy(x = frame.x + 1f) else frame
        }
        val oldManifest = manifest(oldGhost, distanceM = 300f)
        val receiptStore = MemoryReceiptStore(receipt = receipt(candidate, distanceM = 800f))
        val artifactStore = MemoryArtifactStore(ghost = oldGhost, bestDistanceM = 300f)
        val manifestStore = MemoryManifestStore(manifest = oldManifest)
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST, disposition)
        assertEquals(300f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(oldManifest, manifestStore.manifest)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `tampered receipt digest cannot identify the durable ghost`() {
        val frames = frames()
        val strong = receipt(frames, 820f)
        val tampered = strong.copy(sha256Hex = flipFirstHex(requireNotNull(strong.sha256Hex)))
        val receiptStore = MemoryReceiptStore(receipt = tampered)
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 200f)
        val coordinator = coordinator(receiptStore, artifactStore, MemoryManifestStore())

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST, disposition)
        assertEquals(200f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
    }

    @Test
    fun `tampered receipt distance cannot identify the durable ghost`() {
        val frames = frames()
        val strong = receipt(frames, 820f)
        val tampered = strong.copy(distanceM = 821f)
        val receiptStore = MemoryReceiptStore(receipt = tampered)
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 200f)
        val coordinator = coordinator(receiptStore, artifactStore, MemoryManifestStore())

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST, disposition)
        assertEquals(200f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
    }

    @Test
    fun `failed ghost write leaves receipt and never writes manifest or distance`() {
        val receiptStore = MemoryReceiptStore()
        val artifactStore = MemoryArtifactStore(
            bestDistanceM = 100f,
            ghostSaveSucceeds = false
        )
        val manifestStore = MemoryManifestStore()
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)

        val result = coordinator.persist(frames(), distanceM = 600f)

        assertTrue(result.receiptDurable)
        assertFalse(result.ghostDurable)
        assertFalse(result.manifestDurable)
        assertFalse(result.distanceDurable)
        assertFalse(result.receiptCleared)
        assertEquals(0, manifestStore.saveCount)
        assertEquals(100f, artifactStore.bestDistanceM, 0f)
        assertNotNull(receiptStore.receipt?.sha256Hex)
    }

    @Test
    fun `failed manifest write leaves matching receipt and never advances distance`() {
        val receiptStore = MemoryReceiptStore()
        val artifactStore = MemoryArtifactStore(bestDistanceM = 100f)
        val manifestStore = MemoryManifestStore(saveSucceeds = false)
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)
        val frames = frames()

        val result = coordinator.persist(frames, distanceM = 600f)

        assertTrue(result.receiptDurable)
        assertTrue(result.ghostDurable)
        assertFalse(result.manifestDurable)
        assertFalse(result.distanceDurable)
        assertFalse(result.receiptCleared)
        assertEquals(frames, artifactStore.ghost)
        assertEquals(100f, artifactStore.bestDistanceM, 0f)
        assertNotNull(receiptStore.receipt?.sha256Hex)
    }

    @Test
    fun `failed distance write leaves matching manifest and receipt for later repair`() {
        val receiptStore = MemoryReceiptStore()
        val artifactStore = MemoryArtifactStore(
            bestDistanceM = 100f,
            distanceSaveSucceeds = false
        )
        val manifestStore = MemoryManifestStore()
        val coordinator = coordinator(receiptStore, artifactStore, manifestStore)
        val frames = frames()

        val result = coordinator.persist(frames, distanceM = 600f)

        assertTrue(result.ghostDurable)
        assertTrue(result.manifestDurable)
        assertFalse(result.distanceDurable)
        assertNotNull(receiptStore.receipt?.sha256Hex)
        assertEquals(manifest(frames, 600f), manifestStore.manifest)
        assertEquals(frames, artifactStore.ghost)

        artifactStore.distanceSaveSucceeds = true
        val recovered = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, recovered)
        assertEquals(600f, artifactStore.bestDistanceM, 0f)
        assertEquals(null, receiptStore.receipt)
    }

    @Test
    fun `manifest repairs distance after receipt has already cleared`() {
        val frames = frames()
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 120f)
        val manifestStore = MemoryManifestStore(manifest = manifest(frames, 880f))
        val coordinator = coordinator(MemoryReceiptStore(), artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(880f, artifactStore.bestDistanceM, 0f)
        assertEquals(1, artifactStore.distanceSaveCount)
        assertEquals(1, artifactStore.ghostLoadCount)
    }

    @Test
    fun `legacy manifest is upgraded before receipt free distance repair`() {
        val frames = frames()
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 120f)
        val manifestStore = MemoryManifestStore(
            manifest = legacyManifest(frames, distanceM = 890f)
        )
        val coordinator = coordinator(MemoryReceiptStore(), artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE, disposition)
        assertEquals(manifest(frames, 890f), manifestStore.manifest)
        assertEquals(1, manifestStore.saveCount)
        assertEquals(890f, artifactStore.bestDistanceM, 0f)
    }

    @Test
    fun `matching manifest with current distance skips ghost load`() {
        val frames = frames()
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 900f)
        val coordinator = coordinator(
            MemoryReceiptStore(),
            artifactStore,
            MemoryManifestStore(manifest = manifest(frames, 700f))
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ALREADY_APPLIED, disposition)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(0, artifactStore.ghostLoadCount)
        assertTrue(disposition.allowsNewPromotion)
    }

    @Test
    fun `already applied legacy manifest remains lazy until validation is required`() {
        val frames = frames()
        val legacy = legacyManifest(frames, 700f)
        val manifestStore = MemoryManifestStore(manifest = legacy)
        val artifactStore = MemoryArtifactStore(ghost = frames, bestDistanceM = 900f)
        val coordinator = coordinator(MemoryReceiptStore(), artifactStore, manifestStore)

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ALREADY_APPLIED, disposition)
        assertEquals(0, artifactStore.ghostLoadCount)
        assertEquals(0, manifestStore.saveCount)
        assertEquals(legacy, manifestStore.manifest)
    }

    @Test
    fun `corrupt receipt blocks a new promotion`() {
        val receiptStore = MemoryReceiptStore(
            loadOverride = GhostPromotionReceiptLoadResult.Corrupt
        )
        val coordinator = coordinator(
            receiptStore,
            MemoryArtifactStore(),
            MemoryManifestStore()
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_RECEIPT, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertEquals(0, receiptStore.clearCount)
    }

    @Test
    fun `corrupt manifest blocks a new promotion when no receipt remains`() {
        val coordinator = coordinator(
            MemoryReceiptStore(),
            MemoryArtifactStore(),
            MemoryManifestStore(loadOverride = GhostArtifactManifestLoadResult.Corrupt)
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertFalse(disposition.allowsNewPromotion)
    }

    @Test
    fun `manifest fingerprint digest or distance mismatch blocks distance repair`() {
        val durableGhost = frames()
        val unrelated = durableGhost.mapIndexed { index, frame ->
            if (index == 1) frame.copy(y = frame.y + 3f) else frame
        }
        val artifactStore = MemoryArtifactStore(ghost = durableGhost, bestDistanceM = 100f)
        val unrelatedManifest = manifest(unrelated, 700f)
        val coordinator = coordinator(
            MemoryReceiptStore(),
            artifactStore,
            MemoryManifestStore(manifest = unrelatedManifest)
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(1, artifactStore.ghostLoadCount)
    }

    @Test
    fun `matching receipt replaces stale manifest with candidate identity`() {
        val frames = frames()
        val stale = manifest(frames.map { frame -> frame.copy(x = frame.x + 10f) }, 200f)
        val manifestStore = MemoryManifestStore(manifest = stale)
        val coordinator = coordinator(
            MemoryReceiptStore(receipt = receipt(frames, 700f)),
            MemoryArtifactStore(ghost = frames, bestDistanceM = 700f),
            manifestStore
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.ALREADY_APPLIED, disposition)
        assertEquals(manifest(frames, 700f), manifestStore.manifest)
        assertEquals(1, manifestStore.saveCount)
    }

    @Test
    fun `strong identity changes for every persisted frame component and distance`() {
        val base = frames()
        val distanceM = 500f
        val baseIdentity = GhostRunIdentity.calculate(base, distanceM)
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
            val identity = GhostRunIdentity.calculate(variant, distanceM)
            assertFalse(baseIdentity.fingerprint == identity.fingerprint)
            assertFalse(baseIdentity.sha256Hex == identity.sha256Hex)
        }
        val distanceVariant = GhostRunIdentity.calculate(base, distanceM + 1f)
        assertEquals(baseIdentity.fingerprint, distanceVariant.fingerprint)
        assertFalse(baseIdentity.sha256Hex == distanceVariant.sha256Hex)
        assertEquals(baseIdentity, GhostRunIdentity.calculate(base.toList(), distanceM))
        assertEquals(64, baseIdentity.sha256Hex.length)
    }

    private fun coordinator(
        receiptStore: GhostPromotionReceiptStore,
        artifactStore: GhostPromotionArtifactStore,
        manifestStore: GhostArtifactManifestStore
    ): GhostPromotionRecoveryCoordinator = GhostPromotionRecoveryCoordinator(
        receiptStore = receiptStore,
        artifactStore = artifactStore,
        manifestStore = manifestStore
    )

    private fun receipt(frames: List<GhostFrame>, distanceM: Float): GhostPromotionReceipt {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun legacyReceipt(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionReceipt = GhostPromotionReceipt(
        distanceM = distanceM,
        frameCount = frames.size,
        fingerprint = GhostRunFingerprint.calculate(frames),
        sha256Hex = null
    )

    private fun manifest(frames: List<GhostFrame>, distanceM: Float): GhostArtifactManifest {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun legacyManifest(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostArtifactManifest = GhostArtifactManifest(
        distanceM = distanceM,
        frameCount = frames.size,
        fingerprint = GhostRunFingerprint.calculate(frames),
        sha256Hex = null
    )

    private fun flipFirstHex(value: String): String =
        (if (value.first() == '0') '1' else '0') + value.drop(1)

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

    private class MemoryManifestStore(
        var manifest: GhostArtifactManifest? = null,
        private val loadOverride: GhostArtifactManifestLoadResult? = null,
        private val saveSucceeds: Boolean = true,
        private val clearSucceeds: Boolean = true,
        private val events: MutableList<String> = mutableListOf()
    ) : GhostArtifactManifestStore {
        var saveCount = 0
            private set

        override fun load(): GhostArtifactManifestLoadResult =
            loadOverride ?: manifest?.let(GhostArtifactManifestLoadResult::Present)
            ?: GhostArtifactManifestLoadResult.Empty

        override fun save(manifest: GhostArtifactManifest): Boolean {
            saveCount++
            events += "manifest:save:${manifest.distanceM}:${manifest.frameCount}"
            if (saveSucceeds) this.manifest = manifest
            return saveSucceeds
        }

        override fun clear(): Boolean {
            if (clearSucceeds) manifest = null
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
        var ghostLoadCount = 0
            private set

        override fun loadGhost(): List<GhostFrame> {
            ghostLoadCount++
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
