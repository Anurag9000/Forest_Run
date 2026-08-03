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

/**
 * Immutable identity for one primary or compatibility persistence namespace.
 *
 * Capturing both names together prevents a queued ghost transaction from
 * reading one preference namespace while writing another namespace's ghost
 * artifact after test/support compatibility mode changes.
 */
internal data class GhostPersistenceNamespace(
    val prefsName: String,
    val ghostFilename: String
) {
    init {
        require(prefsName.isNotBlank()) { "Ghost preference namespace must not be blank." }
        require(isSafeFilename(ghostFilename)) { "Ghost filename must be a plain local filename." }
    }

    companion object {
        fun capture(): GhostPersistenceNamespace = GhostPersistenceNamespace(
            prefsName = SaveManager.activePrefsNameForTests,
            ghostFilename = SaveManager.activeGhostFilenameForTests
        )

        private fun isSafeFilename(value: String): Boolean =
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                '/' !in value &&
                '\\' !in value &&
                '\u0000' !in value
    }
}

/**
 * Ghost-plus-distance adapter permanently bound to one captured namespace.
 *
 * The codec intentionally mirrors SaveManager's bounded version-2 writer and
 * legacy/versioned reader, but never consults mutable active namespace state
 * after construction.
 */
internal class NamespaceBoundGhostPromotionArtifactStore(
    context: Context,
    private val namespace: GhostPersistenceNamespace
) : GhostPromotionArtifactStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(namespace.prefsName, Context.MODE_PRIVATE)
    private val atomicFile = AtomicFile(File(appContext.filesDir, namespace.ghostFilename))

    override fun loadGhost(): List<GhostFrame> {
        if (!hasRecoverableGhostFile()) return emptyList()

        return try {
            val input = atomicFile.openRead()
            val fileSize = input.channel.size()
            if (fileSize !in LEGACY_GHOST_HEADER_BYTES..MAX_GHOST_FILE_BYTES) {
                input.close()
                return emptyList()
            }

            DataInputStream(BufferedInputStream(input)).use { data ->
                val firstWord = data.readInt()
                val isVersioned = firstWord == SaveManager.GHOST_FILE_MAGIC
                val headerBytes: Long
                val count: Int
                if (isVersioned) {
                    if (data.readInt() != SaveManager.GHOST_FILE_VERSION) return emptyList()
                    count = data.readInt()
                    headerBytes = VERSIONED_GHOST_HEADER_BYTES
                } else {
                    count = firstWord
                    headerBytes = LEGACY_GHOST_HEADER_BYTES
                }
                if (count !in 1..GhostRecorder.MAX_FRAMES) return emptyList()
                if (fileSize != headerBytes + count.toLong() * GHOST_FRAME_BYTES) {
                    return emptyList()
                }

                val frames = ArrayList<GhostFrame>(count)
                repeat(count) {
                    val storedState: Int
                    val t = data.readFloat()
                    val x = data.readFloat()
                    val y = data.readFloat()
                    storedState = data.readInt()
                    val stateOrdinal = if (isVersioned) {
                        GhostStateCodec.decodeToOrdinal(storedState) ?: return emptyList()
                    } else {
                        storedState
                    }
                    frames += GhostFrame(
                        t = t,
                        x = x,
                        y = y,
                        stateOrdinal = stateOrdinal,
                        scaleX = data.readFloat(),
                        scaleY = data.readFloat()
                    )
                }
                frames.takeIf(GhostRunValidator::isValid) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveGhost(frames: List<GhostFrame>): Boolean {
        if (!GhostRunValidator.isValid(frames)) return false

        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(SaveManager.GHOST_FILE_MAGIC)
            output.writeInt(SaveManager.GHOST_FILE_VERSION)
            output.writeInt(frames.size)
            frames.forEach { frame ->
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(requireNotNull(GhostStateCodec.encodeOrdinal(frame.stateOrdinal)))
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
            output.flush()
            atomicFile.finishWrite(stream)
            stream = null
            true
        } catch (_: Exception) {
            stream?.let(atomicFile::failWrite)
            false
        }
    }

    override fun loadBestDistanceM(): Float =
        prefs.getFloat(KEY_BEST_DISTANCE, 0f)

    override fun saveBestDistanceM(distanceM: Float): Boolean {
        val safeDistance = distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        return prefs.edit().putFloat(KEY_BEST_DISTANCE, safeDistance).commit()
    }

    private fun hasRecoverableGhostFile(): Boolean =
        atomicFile.baseFile.exists() || File(atomicFile.baseFile.path + ".bak").exists()

    private companion object {
        const val KEY_BEST_DISTANCE = "best_distance"
        const val LEGACY_GHOST_HEADER_BYTES = 4L
        const val VERSIONED_GHOST_HEADER_BYTES = 12L
        const val GHOST_FRAME_BYTES = 24L
        val MAX_GHOST_FILE_BYTES =
            VERSIONED_GHOST_HEADER_BYTES + GhostRecorder.MAX_FRAMES.toLong() * GHOST_FRAME_BYTES
    }
}
