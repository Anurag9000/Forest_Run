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
 * The newest accepted run is published in memory before disk work begins, so
 * an immediate restart can use it while the recoverable worker transaction is
 * pending. A single daemon worker preserves promotion order without extending
 * app shutdown.
 */
object GhostPersistenceManager {
    private data class PublishedGhost(
        val frames: List<GhostFrame>,
        val distanceM: Float,
        val fingerprint: Long,
        val sha256Hex: String
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "forest-run-ghost-io").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    @Volatile
    private var latestPublication: PublishedGhost? = null

    @Volatile
    private var pendingWrite: Future<*>? = null

    /**
     * Compatibility overload used by direct ghost tests and legacy callers.
     * Production terminal persistence supplies the completed distance explicitly.
     */
    fun saveBestRunAsync(context: Context, frames: List<GhostFrame>): Boolean =
        saveBestRunAsync(
            context = context,
            frames = frames,
            distanceM = bestDistanceFloor(context.applicationContext)
        )

    /**
     * Publishes a validated candidate immediately and schedules its recoverable
     * ghost-plus-distance promotion on the single I/O worker.
     */
    @Synchronized
    fun saveBestRunAsync(
        context: Context,
        frames: List<GhostFrame>,
        distanceM: Float
    ): Boolean {
        if (!GhostRunValidator.isValid(frames)) return false
        if (!distanceM.isFinite() || distanceM < 0f) return false

        val appContext = context.applicationContext
        val activeTask = pendingWrite
        if (activeTask == null || activeTask.isDone) {
            val recovery = recoveryCoordinator(appContext).recover()
            if (!recovery.allowsNewPromotion) return false
        }
        if (distanceM < bestDistanceFloor(appContext)) return false

        val snapshot = frames.toList()
        val identity = GhostRunIdentity.calculate(snapshot)
        val publication = PublishedGhost(
            frames = snapshot,
            distanceM = distanceM,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
        latestPublication = publication
        GhostIoTelemetry.recordWriteStarted(snapshot.size)

        return try {
            pendingWrite = executor.submit {
                val startedAtNs = System.nanoTime()
                var ghostDurable = false
                val succeeded = try {
                    val coordinator = recoveryCoordinator(appContext)
                    val recovery = coordinator.recover()
                    if (!recovery.allowsNewPromotion) {
                        false
                    } else {
                        val result = coordinator.persist(snapshot, distanceM)
                        ghostDurable = result.ghostDurable
                        result.complete
                    }
                } catch (_: Exception) {
                    false
                }

                if (!succeeded && !ghostDurable) {
                    clearPublicationIfCurrent(publication)
                }
                GhostIoTelemetry.recordWriteCompleted(
                    durationNs = System.nanoTime() - startedAtNs,
                    succeeded = succeeded
                )
            }
            true
        } catch (_: RuntimeException) {
            clearPublicationIfCurrent(publication)
            GhostIoTelemetry.recordWriteCompleted(durationNs = 0L, succeeded = false)
            false
        }
    }

    /**
     * Includes an accepted in-memory promotion in comparisons even while its
     * single-worker durable transaction is pending.
     */
    fun bestDistanceFloor(context: Context): Float {
        val diskDistance = SaveManager.loadBestDistance(context.applicationContext)
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0f)
            ?: 0f
        val publishedDistance = latestPublication?.distanceM ?: 0f
        return maxOf(diskDistance, publishedDistance)
    }

    /** Retry any receipt or durable manifest left by a previous process. */
    @Synchronized
    internal fun recoverPendingPromotion(
        context: Context
    ): GhostPromotionRecoveryDisposition {
        val activeTask = pendingWrite
        if (activeTask != null && !activeTask.isDone) {
            return GhostPromotionRecoveryDisposition.IO_FAILURE
        }
        return recoveryCoordinator(context.applicationContext).recover()
    }

    /** Returns the latest in-memory run, falling back to recovered disk state. */
    fun loadLatest(context: Context): List<GhostFrame> {
        latestPublication?.let { return it.frames }

        val appContext = context.applicationContext
        recoverPendingPromotion(appContext)
        val loaded = SaveManager.loadGhostRun(appContext)
        if (loaded.isEmpty()) return emptyList()

        val identity = GhostRunIdentity.calculate(loaded)
        val publication = PublishedGhost(
            frames = loaded,
            distanceM = SaveManager.loadBestDistance(appContext)
                .takeIf { it.isFinite() }
                ?.coerceAtLeast(0f)
                ?: 0f,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
        synchronized(this) {
            if (latestPublication == null) latestPublication = publication
            return latestPublication?.frames ?: loaded
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

    internal fun clearPromotionEvidenceForTests(context: Context): Boolean {
        val appContext = context.applicationContext
        val ghostFilename = SaveManager.activeGhostFilenameForTests
        val receiptCleared = AtomicFileGhostPromotionReceiptStore(
            context = appContext,
            ghostFilename = ghostFilename
        ).clear()
        val manifestCleared = AtomicFileGhostArtifactManifestStore(
            context = appContext,
            ghostFilename = ghostFilename
        ).clear()
        return receiptCleared && manifestCleared
    }

    internal fun clearMemoryForTests() {
        awaitPendingWrites()
        synchronized(this) {
            latestPublication = null
            pendingWrite = null
        }
        GhostIoTelemetry.reset()
    }

    private fun recoveryCoordinator(context: Context): GhostPromotionRecoveryCoordinator {
        val ghostFilename = SaveManager.activeGhostFilenameForTests
        return GhostPromotionRecoveryCoordinator(
            receiptStore = AtomicFileGhostPromotionReceiptStore(
                context = context,
                ghostFilename = ghostFilename
            ),
            artifactStore = AndroidGhostPromotionArtifactStore(context),
            manifestStore = AtomicFileGhostArtifactManifestStore(
                context = context,
                ghostFilename = ghostFilename
            )
        )
    }

    @Synchronized
    private fun clearPublicationIfCurrent(publication: PublishedGhost) {
        val current = latestPublication ?: return
        if (current.distanceM == publication.distanceM &&
            current.fingerprint == publication.fingerprint &&
            current.sha256Hex == publication.sha256Hex
        ) {
            latestPublication = null
        }
    }
}
