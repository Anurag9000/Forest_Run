package com.anurag9000.forestrun.systems

import android.content.Context
import com.anurag9000.forestrun.engine.GhostIoTelemetry
import com.anurag9000.forestrun.engine.SaveManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns ghost persistence away from the render thread.
 *
 * The newest accepted run is published in memory before disk work begins, so
 * an immediate restart can use it while the recoverable worker transaction is
 * pending. Work remains FIFO inside each immutable namespace, while primary and
 * compatibility namespaces may use separate daemon workers concurrently.
 */
object GhostPersistenceManager {
    private const val MAX_CONCURRENT_NAMESPACE_WRITES = 2

    private data class PublishedGhost(
        val namespace: GhostPersistenceNamespace,
        val frames: List<GhostFrame>,
        val distanceM: Float,
        val fingerprint: Long,
        val sha256Hex: String
    )

    private val workerOrdinal = AtomicInteger(0)
    private val scheduler = GhostNamespaceSerialScheduler(
        Executors.newFixedThreadPool(MAX_CONCURRENT_NAMESPACE_WRITES) { runnable ->
            Thread(
                runnable,
                "forest-run-ghost-io-${workerOrdinal.incrementAndGet()}"
            ).apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    )

    private val latestPublications =
        ConcurrentHashMap<GhostPersistenceNamespace, PublishedGhost>()
    private val pendingWrites = GhostNamespacePendingWriteRegistry()

    /**
     * Compatibility overload used by direct ghost tests and legacy callers.
     * Production terminal persistence supplies the completed distance explicitly.
     */
    fun saveBestRunAsync(context: Context, frames: List<GhostFrame>): Boolean {
        val appContext = context.applicationContext
        val namespace = GhostPersistenceNamespace.capture()
        return saveBestRunAsync(
            context = appContext,
            frames = frames,
            distanceM = bestDistanceFloor(appContext, namespace),
            namespace = namespace
        )
    }

    /**
     * Publishes a validated candidate immediately and schedules its recoverable
     * ghost-plus-distance promotion on the namespace-serial I/O scheduler.
     */
    fun saveBestRunAsync(
        context: Context,
        frames: List<GhostFrame>,
        distanceM: Float
    ): Boolean = saveBestRunAsync(
        context = context.applicationContext,
        frames = frames,
        distanceM = distanceM,
        namespace = GhostPersistenceNamespace.capture()
    )

    @Synchronized
    private fun saveBestRunAsync(
        context: Context,
        frames: List<GhostFrame>,
        distanceM: Float,
        namespace: GhostPersistenceNamespace
    ): Boolean {
        if (!GhostRunValidator.isValid(frames)) return false
        if (!distanceM.isFinite() || distanceM < 0f) return false

        if (!pendingWrites.isActive(namespace)) {
            val recovery = recoveryCoordinator(context, namespace).recover()
            if (!recovery.allowsNewPromotion) return false
        }
        if (distanceM < bestDistanceFloor(context, namespace)) return false

        val snapshot = frames.toList()
        val identity = GhostRunIdentity.calculate(snapshot, distanceM)
        val publication = PublishedGhost(
            namespace = namespace,
            frames = snapshot,
            distanceM = distanceM,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
        latestPublications[namespace] = publication
        GhostIoTelemetry.recordWriteStarted(snapshot.size)

        return try {
            val task = scheduler.submit(namespace) {
                val startedAtNs = System.nanoTime()
                var ghostDurable = false
                val succeeded = try {
                    val coordinator = recoveryCoordinator(context, namespace)
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
            pendingWrites.track(namespace, task)
            true
        } catch (_: RuntimeException) {
            clearPublicationIfCurrent(publication)
            GhostIoTelemetry.recordWriteCompleted(durationNs = 0L, succeeded = false)
            false
        }
    }

    /**
     * Includes an accepted in-memory promotion in comparisons even while its
     * namespace-serial durable transaction is pending.
     */
    fun bestDistanceFloor(context: Context): Float =
        bestDistanceFloor(
            context = context.applicationContext,
            namespace = GhostPersistenceNamespace.capture()
        )

    private fun bestDistanceFloor(
        context: Context,
        namespace: GhostPersistenceNamespace
    ): Float {
        val diskDistance = artifactStore(context, namespace).loadBestDistanceM()
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0f)
            ?: 0f
        val publishedDistance = latestPublications[namespace]?.distanceM ?: 0f
        return maxOf(diskDistance, publishedDistance)
    }

    /** Retry any receipt or durable manifest left by a previous process. */
    internal fun recoverPendingPromotion(
        context: Context
    ): GhostPromotionRecoveryDisposition = recoverPendingPromotion(
        context = context.applicationContext,
        namespace = GhostPersistenceNamespace.capture()
    )

    @Synchronized
    private fun recoverPendingPromotion(
        context: Context,
        namespace: GhostPersistenceNamespace
    ): GhostPromotionRecoveryDisposition {
        if (pendingWrites.isActive(namespace)) {
            return GhostPromotionRecoveryDisposition.IO_FAILURE
        }
        return recoveryCoordinator(context, namespace).recover()
    }

    /** Returns the latest in-memory run, falling back to recovered disk state. */
    fun loadLatest(context: Context): List<GhostFrame> {
        val appContext = context.applicationContext
        val namespace = GhostPersistenceNamespace.capture()
        latestPublications[namespace]?.let { return it.frames }

        recoverPendingPromotion(appContext, namespace)
        val store = artifactStore(appContext, namespace)
        val loaded = store.loadGhost()
        if (loaded.isEmpty()) return emptyList()

        val loadedDistance = store.loadBestDistanceM()
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0f)
            ?: 0f
        val identity = GhostRunIdentity.calculate(loaded, loadedDistance)
        val publication = PublishedGhost(
            namespace = namespace,
            frames = loaded,
            distanceM = loadedDistance,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
        val current = latestPublications.putIfAbsent(namespace, publication) ?: publication
        return current.frames
    }

    internal fun awaitPendingWrites(timeoutMs: Long = 5_000L): Boolean =
        pendingWrites.awaitAll(timeoutMs)

    internal fun clearPromotionEvidenceForTests(context: Context): Boolean {
        val appContext = context.applicationContext
        val namespace = GhostPersistenceNamespace.capture()
        val receiptCleared = AtomicFileGhostPromotionReceiptStore(
            context = appContext,
            ghostFilename = namespace.ghostFilename
        ).clear()
        val manifestCleared = AtomicFileGhostArtifactManifestStore(
            context = appContext,
            ghostFilename = namespace.ghostFilename
        ).clear()
        return receiptCleared && manifestCleared
    }

    internal fun clearMemoryForTests() {
        awaitPendingWrites()
        synchronized(this) {
            latestPublications.clear()
            pendingWrites.clear()
        }
        GhostIoTelemetry.reset()
    }

    private fun artifactStore(
        context: Context,
        namespace: GhostPersistenceNamespace
    ): GhostPromotionArtifactStore =
        NamespaceBoundGhostPromotionArtifactStore(context, namespace)

    private fun recoveryCoordinator(
        context: Context,
        namespace: GhostPersistenceNamespace
    ): GhostPromotionRecoveryCoordinator = GhostPromotionRecoveryCoordinator(
        receiptStore = AtomicFileGhostPromotionReceiptStore(
            context = context,
            ghostFilename = namespace.ghostFilename
        ),
        artifactStore = artifactStore(context, namespace),
        manifestStore = AtomicFileGhostArtifactManifestStore(
            context = context,
            ghostFilename = namespace.ghostFilename
        )
    )

    private fun clearPublicationIfCurrent(publication: PublishedGhost) {
        val current = latestPublications[publication.namespace] ?: return
        if (current.distanceM == publication.distanceM &&
            current.fingerprint == publication.fingerprint &&
            current.sha256Hex == publication.sha256Hex
        ) {
            latestPublications.remove(publication.namespace, current)
        }
    }
}
