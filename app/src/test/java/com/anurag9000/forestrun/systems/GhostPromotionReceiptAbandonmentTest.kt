package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GhostPromotionReceiptAbandonmentTest {

    @Test
    fun `mismatched strong receipt validates older strong manifest even when distance is applied`() {
        val durableGhost = frames(xOffset = 0f)
        val candidateGhost = frames(xOffset = 40f)
        val unrelatedManifestGhost = frames(xOffset = 80f)
        val receiptStore = ReceiptStore(receipt(candidateGhost, distanceM = 900f))
        val artifactStore = ArtifactStore(ghost = durableGhost, bestDistanceM = 700f)
        val manifestStore = ManifestStore(manifest(unrelatedManifestGhost, distanceM = 700f))
        val coordinator = GhostPromotionRecoveryCoordinator(
            receiptStore = receiptStore,
            artifactStore = artifactStore,
            manifestStore = manifestStore
        )

        val disposition = coordinator.recover()

        assertEquals(GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST, disposition)
        assertFalse(disposition.allowsNewPromotion)
        assertNull(receiptStore.receipt)
        assertEquals(700f, artifactStore.bestDistanceM, 0f)
        assertEquals(0, artifactStore.distanceSaveCount)
        assertEquals(1, artifactStore.ghostLoadCount)
    }

    @Test
    fun `mismatched strong receipt upgrades matching legacy manifest before abandonment`() {
        val durableGhost = frames(xOffset = 0f)
        val candidateGhost = frames(xOffset = 40f)
        val receiptStore = ReceiptStore(receipt(candidateGhost, distanceM = 900f))
        val artifactStore = ArtifactStore(ghost = durableGhost, bestDistanceM = 700f)
        val legacy = GhostArtifactManifest(
            distanceM = 700f,
            frameCount = durableGhost.size,
            fingerprint = GhostRunFingerprint.calculate(durableGhost),
            sha256Hex = null
        )
        val manifestStore = ManifestStore(legacy)
        val coordinator = GhostPromotionRecoveryCoordinator(
            receiptStore = receiptStore,
            artifactStore = artifactStore,
            manifestStore = manifestStore
        )

        val disposition = coordinator.recover()

        assertEquals(
            GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST,
            disposition
        )
        assertNull(receiptStore.receipt)
        assertEquals(manifest(durableGhost, 700f), manifestStore.manifest)
        assertEquals(1, manifestStore.saveCount)
        assertEquals(0, artifactStore.distanceSaveCount)
    }

    private fun receipt(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionReceipt {
        val identity = GhostRunIdentity.calculate(frames)
        return GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun manifest(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostArtifactManifest {
        val identity = GhostRunIdentity.calculate(frames)
        return GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun frames(xOffset: Float): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f + xOffset, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f + xOffset, 196f, 1, 0.98f, 1.02f)
    )

    private class ReceiptStore(
        var receipt: GhostPromotionReceipt?
    ) : GhostPromotionReceiptStore {
        override fun load(): GhostPromotionReceiptLoadResult =
            receipt?.let(GhostPromotionReceiptLoadResult::Pending)
                ?: GhostPromotionReceiptLoadResult.Empty

        override fun save(receipt: GhostPromotionReceipt): Boolean {
            this.receipt = receipt
            return true
        }

        override fun clear(): Boolean {
            receipt = null
            return true
        }
    }

    private class ManifestStore(
        var manifest: GhostArtifactManifest?
    ) : GhostArtifactManifestStore {
        var saveCount = 0
            private set

        override fun load(): GhostArtifactManifestLoadResult =
            manifest?.let(GhostArtifactManifestLoadResult::Present)
                ?: GhostArtifactManifestLoadResult.Empty

        override fun save(manifest: GhostArtifactManifest): Boolean {
            saveCount++
            this.manifest = manifest
            return true
        }

        override fun clear(): Boolean {
            manifest = null
            return true
        }
    }

    private class ArtifactStore(
        private val ghost: List<GhostFrame>,
        var bestDistanceM: Float
    ) : GhostPromotionArtifactStore {
        var ghostLoadCount = 0
            private set
        var distanceSaveCount = 0
            private set

        override fun loadGhost(): List<GhostFrame> {
            ghostLoadCount++
            return ghost
        }

        override fun saveGhost(frames: List<GhostFrame>): Boolean = true

        override fun loadBestDistanceM(): Float = bestDistanceM

        override fun saveBestDistanceM(distanceM: Float): Boolean {
            distanceSaveCount++
            bestDistanceM = distanceM
            return true
        }
    }
}
