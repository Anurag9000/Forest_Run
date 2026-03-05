package com.yourname.forest_run.engine

import android.content.Context
import com.yourname.forest_run.systems.GhostFrame
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Persists game data that spans across multiple runs:
 *  - High score (Int) in SharedPreferences.
 *  - Lifetime seeds (Int) in SharedPreferences.
 *  - Ghost run frames (binary file in filesDir).
 *
 * All disk I/O is synchronous but cheap — called only on run end / run start.
 * High score is also written immediately on HIT (in RunResetManager.triggerDeath).
 */
object SaveManager {

    private const val PREFS_NAME     = "forest_run_prefs"
    private const val KEY_HIGH_SCORE = "high_score"
    private const val KEY_LIFETIME_SEEDS = "lifetime_seeds"
    private const val KEY_BEST_DIST  = "best_distance"
    private const val GHOST_FILENAME = "ghost_run.bin"

    // ── High score ────────────────────────────────────────────────────────

    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    fun loadHighScore(context: Context): Int =
        prefs(context).getInt(KEY_HIGH_SCORE, 0)

    // ── Best distance ─────────────────────────────────────────────────────

    fun saveBestDistance(context: Context, distanceM: Float) {
        prefs(context).edit().putFloat(KEY_BEST_DIST, distanceM).apply()
    }

    fun loadBestDistance(context: Context): Float =
        prefs(context).getFloat(KEY_BEST_DIST, 0f)

    // ── Lifetime seeds ────────────────────────────────────────────────────

    fun saveLifetimeSeeds(context: Context, seeds: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_SEEDS, seeds).apply()
    }

    fun loadLifetimeSeeds(context: Context): Int =
        prefs(context).getInt(KEY_LIFETIME_SEEDS, 0)

    // ── Ghost run ─────────────────────────────────────────────────────────

    /**
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ghostFile(context: Context) = File(context.filesDir, GHOST_FILENAME)
}
