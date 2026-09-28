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
 * The preference name is one volatile value. Its canonical ghost filename is
 * derived from that single read, so a queued transaction cannot observe a
 * mixed pair during SaveManager's brief two-field namespace transition.
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
        fun capture(): GhostPersistenceNamespace {
            val prefsName = SaveManager.activePrefsNameForTests
            val ghostFilename = expectedGhostFilename(prefsName)
                ?: throw IllegalStateException("Unsupported save namespace: $prefsName")
            return GhostPersistenceNamespace(
                prefsName = prefsName,
                ghostFilename = ghostFilename
            )
        }

        private fun expectedGhostFilename(prefsName: String): String? {
            if (prefsName == SaveManager.PREFS_NAME) return PRIMARY_GHOST_FILENAME
            if (!prefsName.startsWith(COMPAT_PREFS_PREFIX)) return null
            val version = prefsName.removePrefix(COMPAT_PREFS_PREFIX)
            if (version.isEmpty() || version.any { !it.isDigit() }) return null
            return "$COMPAT_GHOST_PREFIX$version.bin"
        }

        private fun isSafeFilename(value: String): Boolean =
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                '/' !in value &&
                '\\' !in value &&
                '\u0000' !in value

        private const val PRIMARY_GHOST_FILENAME = "ghost_run.bin"
        private const val COMPAT_PREFS_PREFIX = "forest_run_prefs_compat_v"
        private const val COMPAT_GHOST_PREFIX = "ghost_run_compat_v"
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
    namespace: GhostPersistenceNamespace
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
                    val t = data.readFloat()
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val storedState = data.readInt()
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
