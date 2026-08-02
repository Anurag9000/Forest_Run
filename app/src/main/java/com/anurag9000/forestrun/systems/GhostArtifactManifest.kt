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
 * The manifest does not duplicate frame payloads. It binds one validated ghost
 * file to the distance that produced it through frame count and raw-bit
 * fingerprint identity.
 */
internal data class GhostArtifactManifest(
    val distanceM: Float,
    val frameCount: Int,
    val fingerprint: Long
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
            if (input.channel.size() != RECORD_BYTES) {
                input.close()
                return GhostArtifactManifestLoadResult.Corrupt
            }
            DataInputStream(BufferedInputStream(input)).use { data ->
                if (data.readInt() != MAGIC) return GhostArtifactManifestLoadResult.Corrupt
                if (data.readInt() != VERSION) return GhostArtifactManifestLoadResult.Corrupt
                val manifest = GhostArtifactManifest(
                    distanceM = data.readFloat(),
                    frameCount = data.readInt(),
                    fingerprint = data.readLong()
                )
                if (isValid(manifest)) {
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
        if (!isValid(manifest)) return false

        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(manifest.distanceM)
            output.writeInt(manifest.frameCount)
            output.writeLong(manifest.fingerprint)
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

    private fun isValid(manifest: GhostArtifactManifest): Boolean =
        manifest.distanceM.isFinite() &&
            manifest.distanceM >= 0f &&
            manifest.frameCount in 1..GhostRecorder.MAX_FRAMES

    private companion object {
        const val MAGIC = 0x4652474D // "FRGM"
        const val VERSION = 1
        const val RECORD_BYTES = 24L
    }
}
