package com.anurag9000.forestrun.systems

import android.content.Context
import android.util.AtomicFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Durable identity for a completed ghost artifact after its transient promotion
 * receipt has been cleared.
 *
 * Version 1 used only a 64-bit FNV fingerprint. Version 2 retains that field for
 * diagnostics and adds a canonical SHA-256 digest used for new artifact identity.
 */
internal data class GhostArtifactManifest(
    val distanceM: Float,
    val frameCount: Int,
    val fingerprint: Long,
    val sha256Hex: String? = null
)

internal sealed interface GhostArtifactManifestLoadResult {
    data object Empty : GhostArtifactManifestLoadResult
    data object Corrupt : GhostArtifactManifestLoadResult
    data class Present(val manifest: GhostArtifactManifest) :
        GhostArtifactManifestLoadResult
}

internal interface GhostArtifactManifestStore {
    fun load(): GhostArtifactManifestLoadResult
    fun save(manifest: GhostArtifactManifest): Boolean
    fun clear(): Boolean
}

/** Versioned AtomicFile manifest scoped to the active ghost artifact name. */
internal class AtomicFileGhostArtifactManifestStore(
    context: Context,
    ghostFilename: String
) : GhostArtifactManifestStore {
    private val baseFile = File(
        context.applicationContext.filesDir,
        "$ghostFilename.manifest"
    )
    private val atomicFile = AtomicFile(baseFile)

    override fun load(): GhostArtifactManifestLoadResult {
        if (!hasRecoverableFile()) return GhostArtifactManifestLoadResult.Empty

        return try {
            val input = atomicFile.openRead()
            val fileSize = input.channel.size()
            if (fileSize != LEGACY_RECORD_BYTES && fileSize != RECORD_BYTES) {
                input.close()
                return GhostArtifactManifestLoadResult.Corrupt
            }
            DataInputStream(BufferedInputStream(input)).use { data ->
                if (data.readInt() != MAGIC) return GhostArtifactManifestLoadResult.Corrupt
                val version = data.readInt()
                val manifest = when (version) {
                    LEGACY_VERSION -> {
                        if (fileSize != LEGACY_RECORD_BYTES) {
                            return GhostArtifactManifestLoadResult.Corrupt
                        }
                        GhostArtifactManifest(
                            distanceM = data.readFloat(),
                            frameCount = data.readInt(),
                            fingerprint = data.readLong(),
                            sha256Hex = null
                        )
                    }
                    VERSION -> {
                        if (fileSize != RECORD_BYTES) {
                            return GhostArtifactManifestLoadResult.Corrupt
                        }
                        GhostArtifactManifest(
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
                    else -> return GhostArtifactManifestLoadResult.Corrupt
                }
                if (isValidForLoad(manifest)) {
                    GhostArtifactManifestLoadResult.Present(manifest)
                } else {
                    GhostArtifactManifestLoadResult.Corrupt
                }
            }
        } catch (_: Exception) {
            GhostArtifactManifestLoadResult.Corrupt
        }
    }

    override fun save(manifest: GhostArtifactManifest): Boolean {
        if (!isValidForSave(manifest)) return false
        val digest = GhostRunIdentity.decodeSha256(requireNotNull(manifest.sha256Hex))
            ?: return false

        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(manifest.distanceM)
            output.writeInt(manifest.frameCount)
            output.writeLong(manifest.fingerprint)
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
        return success && files.none { file -> file.exists() }
    }

    private fun hasRecoverableFile(): Boolean =
        baseFile.exists() || File(baseFile.path + ".bak").exists()

    private fun isValidForLoad(manifest: GhostArtifactManifest): Boolean =
        hasValidCommonFields(manifest) &&
            (manifest.sha256Hex == null ||
                GhostRunIdentity.isCanonicalSha256(manifest.sha256Hex))

    private fun isValidForSave(manifest: GhostArtifactManifest): Boolean =
        hasValidCommonFields(manifest) &&
            manifest.sha256Hex?.let(GhostRunIdentity::isCanonicalSha256) == true

    private fun hasValidCommonFields(manifest: GhostArtifactManifest): Boolean =
        manifest.distanceM.isFinite() &&
            manifest.distanceM >= 0f &&
            manifest.frameCount in 1..GhostRecorder.MAX_FRAMES

    private companion object {
        const val MAGIC = 0x4652474D // "FRGM"
        const val LEGACY_VERSION = 1
        const val VERSION = 2
        const val LEGACY_RECORD_BYTES = 24L
        const val RECORD_BYTES = 56L
    }
}
