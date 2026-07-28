#!/usr/bin/env python3
"""Move ghost serialization off the render thread and harden its binary format."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    save_manager = Path(
        "app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt"
    )
    replace_once(
        save_manager,
        '''import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
''',
        '''import android.content.Context
import android.util.AtomicFile
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostRecorder
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
''',
        "ghost persistence imports",
    )
    replace_once(
        save_manager,
        ''' * All disk I/O is synchronous but cheap — called only on run end / run start.
 * High score is also written immediately on HIT (in RunResetManager.triggerDeath).
''',
        ''' * SharedPreferences writes are lightweight. Ghost serialization is handled by
 * GhostPersistenceManager on a dedicated worker and committed through AtomicFile.
''',
        "persistence documentation",
    )
    replace_once(
        save_manager,
        '''    /**
     * Serialize [frames] to a compact binary file.
     *
     * Format per frame (28 bytes):
     *   Float t (4), Float x (4), Float y (4), Int stateOrdinal (4),
     *   Float scaleX (4), Float scaleY (4), [padding 4] → 24 bytes
     * Actually: 6 × 4 = 24 bytes per frame.
     */
    fun saveGhostRun(context: Context, frames: List<GhostFrame>) {
        if (frames.isEmpty()) return
        val file = ghostFile(context)
        try {
            DataOutputStream(file.outputStream().buffered()).use { dos ->
                dos.writeInt(frames.size)
                for (f in frames) {
                    dos.writeFloat(f.t)
                    dos.writeFloat(f.x)
                    dos.writeFloat(f.y)
                    dos.writeInt(f.stateOrdinal)
                    dos.writeFloat(f.scaleX)
                    dos.writeFloat(f.scaleY)
                }
            }
        } catch (_: Exception) { /* Silently skip on I/O error — ghost is optional */ }
    }

    /**
     * Load the persisted ghost run. Returns empty list if no file exists or on error.
     */
    fun loadGhostRun(context: Context): List<GhostFrame> {
        val file = ghostFile(context)
        if (!file.exists()) return emptyList()
        return try {
            DataInputStream(file.inputStream().buffered()).use { dis ->
                val count = dis.readInt()
                val list  = ArrayList<GhostFrame>(count)
                repeat(count) {
                    list.add(GhostFrame(
                        t            = dis.readFloat(),
                        x            = dis.readFloat(),
                        y            = dis.readFloat(),
                        stateOrdinal = dis.readInt(),
                        scaleX       = dis.readFloat(),
                        scaleY       = dis.readFloat()
                    ))
                }
                list
            }
        } catch (_: Exception) { emptyList() }
    }

    fun hasGhostRun(context: Context): Boolean = ghostFile(context).exists()
''',
        '''    private const val GHOST_HEADER_BYTES = 4L
    private const val GHOST_FRAME_BYTES = 24L
    private val MAX_GHOST_FILE_BYTES =
        GHOST_HEADER_BYTES + GhostRecorder.MAX_FRAMES.toLong() * GHOST_FRAME_BYTES

    /** Serialize [frames] through [AtomicFile] so interrupted writes preserve the old ghost. */
    fun saveGhostRun(context: Context, frames: List<GhostFrame>): Boolean {
        if (frames.isEmpty() || frames.size > GhostRecorder.MAX_FRAMES) return false

        var previousTime = Float.NEGATIVE_INFINITY
        for (frame in frames) {
            if (!isValidGhostFrame(frame, previousTime)) return false
            previousTime = frame.t
        }

        val atomicFile = AtomicFile(ghostFile(context.applicationContext))
        var stream: FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(frames.size)
            for (frame in frames) {
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(frame.stateOrdinal)
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
            output.flush()
            atomicFile.finishWrite(stream)
            stream = null
            true
        } catch (_: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            false
        }
    }

    /**
     * Load a structurally valid ghost run. Corrupt, truncated, oversized, or
     * non-finite payloads are rejected before they can allocate unbounded state.
     */
    fun loadGhostRun(context: Context): List<GhostFrame> {
        val atomicFile = AtomicFile(ghostFile(context.applicationContext))
        if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) {
            return emptyList()
        }

        return try {
            val input = atomicFile.openRead()
            val fileSize = input.channel.size()
            if (fileSize !in GHOST_HEADER_BYTES..MAX_GHOST_FILE_BYTES) {
                input.close()
                return emptyList()
            }

            DataInputStream(input.buffered()).use { data ->
                val count = data.readInt()
                if (count !in 1..GhostRecorder.MAX_FRAMES) return emptyList()

                val expectedBytes = GHOST_HEADER_BYTES + count.toLong() * GHOST_FRAME_BYTES
                if (fileSize != expectedBytes) return emptyList()

                val frames = ArrayList<GhostFrame>(count)
                var previousTime = Float.NEGATIVE_INFINITY
                repeat(count) {
                    val frame = GhostFrame(
                        t = data.readFloat(),
                        x = data.readFloat(),
                        y = data.readFloat(),
                        stateOrdinal = data.readInt(),
                        scaleX = data.readFloat(),
                        scaleY = data.readFloat()
                    )
                    if (!isValidGhostFrame(frame, previousTime)) return emptyList()
                    previousTime = frame.t
                    frames.add(frame)
                }
                frames
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasGhostRun(context: Context): Boolean = ghostFile(context).exists()

    private fun isValidGhostFrame(frame: GhostFrame, previousTime: Float): Boolean =
        frame.t.isFinite() &&
            frame.t >= 0f &&
            frame.t >= previousTime &&
            frame.t <= GhostRecorder.MAX_DURATION_S.toFloat() + GhostRecorder.SAMPLE_INTERVAL_S &&
            frame.x.isFinite() &&
            frame.y.isFinite() &&
            frame.stateOrdinal in PlayerState.entries.indices &&
            frame.scaleX.isFinite() &&
            frame.scaleY.isFinite() &&
            frame.scaleX in 0.1f..4f &&
            frame.scaleY in 0.1f..4f
''',
        "atomic validated ghost serialization",
    )

    game_view = Path(
        "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
    )
    replace_once(
        game_view,
        '''import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.GhostRecorder
''',
        '''import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.GhostRecorder
''',
        "GhostPersistenceManager import",
    )
    replace_once(
        game_view,
        '''                        if (persistEncounter &&
                            ::gameState.isInitialized &&
                            gameState.distanceMetres > SaveManager.loadBestDistance(context)
                        ) {
                            SaveManager.saveGhostRun(context, ghostRecorder.snapshot())
                            SaveManager.saveBestDistance(context, gameState.distanceMetres)
                        }
''',
        '''                        if (persistEncounter &&
                            ::gameState.isInitialized &&
                            gameState.distanceMetres > SaveManager.loadBestDistance(context)
                        ) {
                            val completedGhost = ghostRecorder.detachSnapshot()
                            GhostPersistenceManager.saveBestRunAsync(context, completedGhost)
                            SaveManager.saveBestDistance(context, gameState.distanceMetres)
                        }
''',
        "off-thread best-run ghost save",
    )
    replace_once(
        game_view,
        '''    private fun reloadGhost() {
        ghostPlayer.reset()
        val frames = SaveManager.loadGhostRun(context)
        if (frames.isNotEmpty()) ghostPlayer.load(frames)
    }
''',
        '''    private fun reloadGhost() {
        ghostPlayer.reset()
        val frames = GhostPersistenceManager.loadLatest(context)
        if (frames.isNotEmpty()) ghostPlayer.load(frames)
    }
''',
        "immediate in-memory ghost reload",
    )


if __name__ == "__main__":
    main()
