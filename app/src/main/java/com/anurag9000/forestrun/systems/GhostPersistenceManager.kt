package com.anurag9000.forestrun.systems

import android.content.Context
import com.anurag9000.forestrun.engine.GhostIoTelemetry
import com.anurag9000.forestrun.engine.SaveManager
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Owns ghost persistence away from the render thread.
 *
 * The newest completed run is published in memory before disk work begins, so
 * an immediate restart can use it even while the atomic file write is pending.
 * A single daemon worker preserves save ordering without extending app shutdown.
 */
object GhostPersistenceManager {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "forest-run-ghost-io").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    @Volatile
    private var latestFrames: List<GhostFrame>? = null

    @Volatile
    private var pendingWrite: Future<*>? = null

    /** Publishes [frames] immediately and schedules their atomic disk write. */
    @Synchronized
    fun saveBestRunAsync(context: Context, frames: List<GhostFrame>): Boolean {
        if (frames.isEmpty()) return false

        val appContext = context.applicationContext
        latestFrames = frames
        GhostIoTelemetry.recordWriteStarted(frames.size)
        return try {
            pendingWrite = executor.submit {
                val startedAtNs = System.nanoTime()
                val succeeded = try {
                    SaveManager.saveGhostRun(appContext, frames)
                } catch (_: Exception) {
                    false
                }
                GhostIoTelemetry.recordWriteCompleted(
                    durationNs = System.nanoTime() - startedAtNs,
                    succeeded = succeeded
                )
            }
            true
        } catch (_: RuntimeException) {
            GhostIoTelemetry.recordWriteCompleted(durationNs = 0L, succeeded = false)
            false
        }
    }

    /** Returns the latest in-memory run, falling back to validated disk state. */
    fun loadLatest(context: Context): List<GhostFrame> {
        latestFrames?.let { return it }

        val loaded = SaveManager.loadGhostRun(context.applicationContext)
        if (loaded.isEmpty()) return emptyList()

        synchronized(this) {
            if (latestFrames == null) latestFrames = loaded
            return latestFrames ?: loaded
        }
    }

    internal fun awaitPendingWrites(timeoutMs: Long = 5_000L): Boolean {
        val task = pendingWrite ?: return true
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    internal fun clearMemoryForTests() {
        awaitPendingWrites()
        synchronized(this) {
            latestFrames = null
            pendingWrite = null
        }
        GhostIoTelemetry.reset()
    }
}
