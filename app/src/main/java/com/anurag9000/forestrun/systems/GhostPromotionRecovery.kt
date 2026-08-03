package com.anurag9000.forestrun.systems

import android.content.Context
import android.util.AtomicFile
import com.anurag9000.forestrun.engine.SaveManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

internal data class GhostPromotionReceipt(
    val distanceM: Float,
    val frameCount: Int,
    val fingerprint: Long
)

internal sealed interface GhostPromotionReceiptLoadResult {
    data object Empty : GhostPromotionReceiptLoadResult
    data object Corrupt : GhostPromotionReceiptLoadResult
    data class Pending(val receipt: GhostPromotionReceipt) : GhostPromotionReceiptLoadResult
}

internal interface GhostPromotionReceiptStore {
    fun load(): GhostPromotionReceiptLoadResult
    fun save(receipt: GhostPromotionReceipt): Boolean
    fun clear(): Boolean
}

internal interface GhostPromotionArtifactStore {
    fun loadGhost(): List<GhostFrame>
    fun saveGhost(frames: List<GhostFrame>): Boolean
    fun loadBestDistanceM(): Float
    fun saveBestDistanceM(distanceM: Float): Boolean
}

internal enum class GhostPromotionRecoveryDisposition {
    EMPTY,
    REPAIRED_DISTANCE,
    ALREADY_APPLIED,
    ABANDONED_UNWRITTEN_GHOST,
    CORRUPT_RECEIPT,
    CORRUPT_MANIFEST,
    IO_FAILURE;

    val allowsNewPromotion: Boolean
        get() = when (this) {
            EMPTY,
            REPAIRED_DISTANCE,
            ALREADY_APPLIED,
            ABANDONED_UNWRITTEN_GHOST -> true
            CORRUPT_RECEIPT,
            CORRUPT_MANIFEST,
            IO_FAILURE -> false
        }
}

internal data class GhostPromotionPersistenceResult(
    val receiptDurable: Boolean,
    val ghostDurable: Boolean,
    val distanceDurable: Boolean,
    val receiptCleared: Boolean,
    val manifestDurable: Boolean = true
) {
    val complete: Boolean
        get() = receiptDurable &&
            ghostDurable &&
            manifestDurable &&
            distanceDurable &&
            receiptCleared
}

/**
 * Makes asynchronous best-ghost publication recoverable across process death.
 *
 * The transient receipt is durable before the ghost write. The persistent
 * manifest is durable after the ghost write and before best distance, binding
 * the surviving artifact to its distance after the receipt has been cleared.
 */
internal class GhostPromotionRecoveryCoordinator(
    private val receiptStore: GhostPromotionReceiptStore,
    private val artifactStore: GhostPromotionArtifactStore,
    private val manifestStore: GhostArtifactManifestStore
) {
    fun persist(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionPersistenceResult {
        if (!GhostRunValidator.isValid(frames) || !isValidDistance(distanceM)) {
            return failedPersistence()
        }

        val receipt = GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = GhostRunFingerprint.calculate(frames)
        )
        if (!receiptStore.save(receipt)) {
            return failedPersistence()
        }
        if (!artifactStore.saveGhost(frames)) {
            return GhostPromotionPersistenceResult(
                receiptDurable = true,
                ghostDurable = false,
                manifestDurable = false,
                distanceDurable = false,
                receiptCleared = false
            )
        }

        if (!manifestStore.save(receipt.toManifest())) {
            return GhostPromotionPersistenceResult(
                receiptDurable = true,
                ghostDurable = true,
                manifestDurable = false,
                distanceDurable = false,
                receiptCleared = false
            )
        }

        val currentBest = normalizedDistance(artifactStore.loadBestDistanceM())
        val targetBest = max(currentBest, distanceM)
        if (!artifactStore.saveBestDistanceM(targetBest)) {
            return GhostPromotionPersistenceResult(
                receiptDurable = true,
                ghostDurable = true,
                manifestDurable = true,
                distanceDurable = false,
                receiptCleared = false
            )
        }

        val cleared = receiptStore.clear()
        return GhostPromotionPersistenceResult(
            receiptDurable = true,
            ghostDurable = true,
            manifestDurable = true,
            distanceDurable = true,
            receiptCleared = cleared
        )
    }

    fun recover(): GhostPromotionRecoveryDisposition {
        return try {
            when (val loaded = receiptStore.load()) {
                GhostPromotionReceiptLoadResult.Empty -> recoverManifest()
                GhostPromotionReceiptLoadResult.Corrupt ->
                    GhostPromotionRecoveryDisposition.CORRUPT_RECEIPT
                is GhostPromotionReceiptLoadResult.Pending -> recoverReceipt(loaded.receipt)
            }
        } catch (_: Exception) {
            GhostPromotionRecoveryDisposition.IO_FAILURE
        }
    }

    private fun recoverReceipt(
        receipt: GhostPromotionReceipt
    ): GhostPromotionRecoveryDisposition {
        val durableGhost = artifactStore.loadGhost()
        if (!matches(durableGhost, receipt.frameCount, receipt.fingerprint)) {
            if (!receiptStore.clear()) {
                return GhostPromotionRecoveryDisposition.IO_FAILURE
            }
            return when (val manifestRecovery = recoverManifest(durableGhost)) {
                GhostPromotionRecoveryDisposition.EMPTY,
                GhostPromotionRecoveryDisposition.ALREADY_APPLIED ->
                    GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST
                else -> manifestRecovery
            }
        }

        val expectedManifest = receipt.toManifest()
        if (!ensureManifest(expectedManifest)) {
            return GhostPromotionRecoveryDisposition.IO_FAILURE
        }

        val repaired = repairDistanceIfNeeded(receipt.distanceM)
            ?: return GhostPromotionRecoveryDisposition.IO_FAILURE
        if (!receiptStore.clear()) {
            return GhostPromotionRecoveryDisposition.IO_FAILURE
        }
        return if (repaired) {
            GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE
        } else {
            GhostPromotionRecoveryDisposition.ALREADY_APPLIED
        }
    }

    private fun recoverManifest(
        knownGhost: List<GhostFrame>? = null
    ): GhostPromotionRecoveryDisposition {
        return when (val loaded = manifestStore.load()) {
            GhostArtifactManifestLoadResult.Empty ->
                GhostPromotionRecoveryDisposition.EMPTY
            GhostArtifactManifestLoadResult.Corrupt ->
                GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST
            is GhostArtifactManifestLoadResult.Present -> {
                val manifest = loaded.manifest
                val currentBest = normalizedDistance(artifactStore.loadBestDistanceM())
                when {
                    knownGhost != null && !matches(
                        frames = knownGhost,
                        frameCount = manifest.frameCount,
                        fingerprint = manifest.fingerprint
                    ) -> GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST
                    currentBest >= manifest.distanceM ->
                        GhostPromotionRecoveryDisposition.ALREADY_APPLIED
                    else -> {
                        val durableGhost = knownGhost ?: artifactStore.loadGhost()
                        if (!matches(
                                frames = durableGhost,
                                frameCount = manifest.frameCount,
                                fingerprint = manifest.fingerprint
                            )
                        ) {
                            GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST
                        } else if (artifactStore.saveBestDistanceM(manifest.distanceM)) {
                            GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE
                        } else {
                            GhostPromotionRecoveryDisposition.IO_FAILURE
                        }
                    }
                }
            }
        }
    }

    private fun ensureManifest(expected: GhostArtifactManifest): Boolean {
        return when (val loaded = manifestStore.load()) {
            is GhostArtifactManifestLoadResult.Present ->
                loaded.manifest == expected || manifestStore.save(expected)
            GhostArtifactManifestLoadResult.Empty,
            GhostArtifactManifestLoadResult.Corrupt -> manifestStore.save(expected)
        }
    }

    /** Returns whether a write occurred, or null when the write failed. */
    private fun repairDistanceIfNeeded(distanceM: Float): Boolean? {
        val currentBest = normalizedDistance(artifactStore.loadBestDistanceM())
        if (currentBest >= distanceM) return false
        return if (artifactStore.saveBestDistanceM(distanceM)) true else null
    }

    private fun matches(
        frames: List<GhostFrame>,
        frameCount: Int,
        fingerprint: Long
    ): Boolean =
        frames.size == frameCount &&
            GhostRunValidator.isValid(frames) &&
            GhostRunFingerprint.calculate(frames) == fingerprint

    private fun GhostPromotionReceipt.toManifest(): GhostArtifactManifest =
        GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frameCount,
            fingerprint = fingerprint
        )

    private fun failedPersistence(): GhostPromotionPersistenceResult =
        GhostPromotionPersistenceResult(
            receiptDurable = false,
            ghostDurable = false,
            manifestDurable = false,
            distanceDurable = false,
            receiptCleared = false
        )

    private fun isValidDistance(distanceM: Float): Boolean =
        distanceM.isFinite() && distanceM >= 0f

    private fun normalizedDistance(distanceM: Float): Float =
        distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
}

/** Versioned AtomicFile receipt scoped to the active ghost artifact name. */
internal class AtomicFileGhostPromotionReceiptStore(
    context: Context,
    ghostFilename: String
) : GhostPromotionReceiptStore {
    private val baseFile = File(
        context.applicationContext.filesDir,
        "$ghostFilename.promotion"
    )
    private val atomicFile = AtomicFile(baseFile)

    override fun load(): GhostPromotionReceiptLoadResult {
        if (!hasRecoverableFile()) return GhostPromotionReceiptLoadResult.Empty

        return try {
            val input = atomicFile.openRead()
            if (input.channel.size() != RECORD_BYTES) {
                input.close()
                return GhostPromotionReceiptLoadResult.Corrupt
            }
            DataInputStream(BufferedInputStream(input)).use { data ->
                if (data.readInt() != MAGIC) return GhostPromotionReceiptLoadResult.Corrupt
                if (data.readInt() != VERSION) return GhostPromotionReceiptLoadResult.Corrupt
                val receipt = GhostPromotionReceipt(
                    distanceM = data.readFloat(),
                    frameCount = data.readInt(),
                    fingerprint = data.readLong()
                )
                if (!isValid(receipt)) {
                    GhostPromotionReceiptLoadResult.Corrupt
                } else {
                    GhostPromotionReceiptLoadResult.Pending(receipt)
                }
            }
        } catch (_: Exception) {
            GhostPromotionReceiptLoadResult.Corrupt
        }
    }

    override fun save(receipt: GhostPromotionReceipt): Boolean {
        if (!isValid(receipt)) return false

        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(receipt.distanceM)
            output.writeInt(receipt.frameCount)
            output.writeLong(receipt.fingerprint)
            output.flush()
            atomicFile.finishWrite(stream)
            stream = null
            true
        } catch (_: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            false
        }
    }

    override fun clear(): Boolean {
        val files = arrayOf(
            baseFile,
            File(baseFile.path + ".bak"),
            File(baseFile.path + ".new")
        )
        var success = true
        files.forEach { file ->
            if (file.exists() && !file.delete()) success = false
        }
        return success && files.none(File::exists)
    }

    private fun hasRecoverableFile(): Boolean =
        baseFile.exists() || File(baseFile.path + ".bak").exists()

    private fun isValid(receipt: GhostPromotionReceipt): Boolean =
        receipt.distanceM.isFinite() &&
            receipt.distanceM >= 0f &&
            receipt.frameCount in 1..GhostRecorder.MAX_FRAMES

    private companion object {
        const val MAGIC = 0x46524750 // "FRGP"
        const val VERSION = 1
        const val RECORD_BYTES = 24L
    }
}

/** Production artifact adapter for the active SaveManager namespace. */
internal class AndroidGhostPromotionArtifactStore(context: Context) :
    GhostPromotionArtifactStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SaveManager.activePrefsNameForTests,
        Context.MODE_PRIVATE
    )

    override fun loadGhost(): List<GhostFrame> = SaveManager.loadGhostRun(appContext)

    override fun saveGhost(frames: List<GhostFrame>): Boolean =
        SaveManager.saveGhostRun(appContext, frames)

    override fun loadBestDistanceM(): Float =
        prefs.getFloat(KEY_BEST_DISTANCE, 0f)

    override fun saveBestDistanceM(distanceM: Float): Boolean {
        val safeDistance = distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        return prefs.edit().putFloat(KEY_BEST_DISTANCE, safeDistance).commit()
    }

    private companion object {
        const val KEY_BEST_DISTANCE = "best_distance"
    }
}

/** Stable raw-bit fingerprint for one validated ghost artifact. */
internal object GhostRunFingerprint {
    fun calculate(frames: List<GhostFrame>): Long {
        var hash = FNV_OFFSET_BASIS
        hash = mixInt(hash, frames.size)
        frames.forEach { frame ->
            hash = mixInt(hash, frame.t.toRawBits())
            hash = mixInt(hash, frame.x.toRawBits())
            hash = mixInt(hash, frame.y.toRawBits())
            hash = mixInt(hash, frame.stateOrdinal)
            hash = mixInt(hash, frame.scaleX.toRawBits())
            hash = mixInt(hash, frame.scaleY.toRawBits())
        }
        return hash
    }

    private fun mixInt(initial: Long, value: Int): Long {
        var hash = initial
        repeat(Int.SIZE_BYTES) { index ->
            val byte = (value ushr (index * Byte.SIZE_BITS)) and 0xff
            hash = (hash xor byte.toLong()) * FNV_PRIME
        }
        return hash
    }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
