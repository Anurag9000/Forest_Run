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
    val fingerprint: Long,
    val sha256Hex: String? = null
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
 * Version-1 sidecars remain readable through their legacy FNV identity. New
 * sidecars use SHA-256, and any legacy evidence that must be replayed is upgraded
 * to a strong version-2 manifest before best distance can advance.
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

        val identity = GhostRunIdentity.calculate(frames)
        val receipt = GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
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

        if (!manifestStore.save(receipt.toManifest(identity))) {
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
        val durableIdentity = matchingIdentity(
            frames = durableGhost,
            frameCount = receipt.frameCount,
            fingerprint = receipt.fingerprint,
            sha256Hex = receipt.sha256Hex
        )
        if (durableIdentity == null) {
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

        val expectedManifest = receipt.toManifest(durableIdentity)
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
                val knownIdentity = knownGhost?.let { frames ->
                    matchingIdentity(
                        frames = frames,
                        frameCount = manifest.frameCount,
                        fingerprint = manifest.fingerprint,
                        sha256Hex = manifest.sha256Hex
                    )
                }
                when {
                    knownGhost != null && knownIdentity == null ->
                        GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST
                    knownIdentity != null && !ensureStrongManifest(manifest, knownIdentity) ->
                        GhostPromotionRecoveryDisposition.IO_FAILURE
                    currentBest >= manifest.distanceM ->
                        GhostPromotionRecoveryDisposition.ALREADY_APPLIED
                    else -> {
                        val durableGhost = knownGhost ?: artifactStore.loadGhost()
                        val identity = knownIdentity ?: matchingIdentity(
                            frames = durableGhost,
                            frameCount = manifest.frameCount,
                            fingerprint = manifest.fingerprint,
                            sha256Hex = manifest.sha256Hex
                        )
                        if (identity == null) {
                            GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST
                        } else if (!ensureStrongManifest(manifest, identity)) {
                            GhostPromotionRecoveryDisposition.IO_FAILURE
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

    private fun ensureStrongManifest(
        manifest: GhostArtifactManifest,
        identity: GhostRunIdentityValue
    ): Boolean {
        if (manifest.sha256Hex != null) return true
        return manifestStore.save(
            manifest.copy(
                fingerprint = identity.fingerprint,
                sha256Hex = identity.sha256Hex
            )
        )
    }

    /** Returns whether a write occurred, or null when the write failed. */
    private fun repairDistanceIfNeeded(distanceM: Float): Boolean? {
        val currentBest = normalizedDistance(artifactStore.loadBestDistanceM())
        if (currentBest >= distanceM) return false
        return if (artifactStore.saveBestDistanceM(distanceM)) true else null
    }

    private fun matchingIdentity(
        frames: List<GhostFrame>,
        frameCount: Int,
        fingerprint: Long,
        sha256Hex: String?
    ): GhostRunIdentityValue? {
        if (frames.size != frameCount || !GhostRunValidator.isValid(frames)) return null
        val identity = GhostRunIdentity.calculate(frames)
        val matches = if (sha256Hex == null) {
            identity.fingerprint == fingerprint
        } else {
            GhostRunIdentity.isCanonicalSha256(sha256Hex) &&
                identity.fingerprint == fingerprint &&
                identity.sha256Hex == sha256Hex
        }
        return identity.takeIf { matches }
    }

    private fun GhostPromotionReceipt.toManifest(
        identity: GhostRunIdentityValue
    ): GhostArtifactManifest = GhostArtifactManifest(
        distanceM = distanceM,
        frameCount = frameCount,
        fingerprint = identity.fingerprint,
        sha256Hex = identity.sha256Hex
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
            val fileSize = input.channel.size()
            if (fileSize != LEGACY_RECORD_BYTES && fileSize != RECORD_BYTES) {
                input.close()
                return GhostPromotionReceiptLoadResult.Corrupt
            }
            DataInputStream(BufferedInputStream(input)).use { data ->
                if (data.readInt() != MAGIC) return GhostPromotionReceiptLoadResult.Corrupt
                val version = data.readInt()
                val receipt = when (version) {
                    LEGACY_VERSION -> {
                        if (fileSize != LEGACY_RECORD_BYTES) {
                            return GhostPromotionReceiptLoadResult.Corrupt
                        }
                        GhostPromotionReceipt(
                            distanceM = data.readFloat(),
                            frameCount = data.readInt(),
                            fingerprint = data.readLong(),
                            sha256Hex = null
                        )
                    }
                    VERSION -> {
                        if (fileSize != RECORD_BYTES) {
                            return GhostPromotionReceiptLoadResult.Corrupt
                        }
                        GhostPromotionReceipt(
                            distanceM = data.readFloat(),
                            frameCount = data.readInt(),
                            fingerprint = data.readLong(),
                            sha256Hex = GhostRunIdentity.encodeHex(
                                ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT).also { bytes ->
                                    data.readFully(bytes)
                                }
                            )
                        )
                    }
                    else -> return GhostPromotionReceiptLoadResult.Corrupt
                }
                if (isValidForLoad(receipt)) {
                    GhostPromotionReceiptLoadResult.Pending(receipt)
                } else {
                    GhostPromotionReceiptLoadResult.Corrupt
                }
            }
        } catch (_: Exception) {
            GhostPromotionReceiptLoadResult.Corrupt
        }
    }

    override fun save(receipt: GhostPromotionReceipt): Boolean {
        if (!isValidForSave(receipt)) return false
        val digest = GhostRunIdentity.decodeSha256(requireNotNull(receipt.sha256Hex))
            ?: return false

        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(receipt.distanceM)
            output.writeInt(receipt.frameCount)
            output.writeLong(receipt.fingerprint)
            output.write(digest)
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

    private fun isValidForLoad(receipt: GhostPromotionReceipt): Boolean =
        hasValidCommonFields(receipt) &&
            (receipt.sha256Hex == null ||
                GhostRunIdentity.isCanonicalSha256(receipt.sha256Hex))

    private fun isValidForSave(receipt: GhostPromotionReceipt): Boolean =
        hasValidCommonFields(receipt) &&
            receipt.sha256Hex?.let(GhostRunIdentity::isCanonicalSha256) == true

    private fun hasValidCommonFields(receipt: GhostPromotionReceipt): Boolean =
        receipt.distanceM.isFinite() &&
            receipt.distanceM >= 0f &&
            receipt.frameCount in 1..GhostRecorder.MAX_FRAMES

    private companion object {
        const val MAGIC = 0x46524750 // "FRGP"
        const val LEGACY_VERSION = 1
        const val VERSION = 2
        const val LEGACY_RECORD_BYTES = 24L
        const val RECORD_BYTES = 56L
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
