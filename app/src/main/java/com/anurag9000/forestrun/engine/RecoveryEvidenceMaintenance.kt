package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.systems.AndroidGhostPromotionArtifactStore
import com.anurag9000.forestrun.systems.AtomicFileGhostArtifactManifestStore
import com.anurag9000.forestrun.systems.AtomicFileGhostPromotionReceiptStore
import com.anurag9000.forestrun.systems.GhostArtifactManifest
import com.anurag9000.forestrun.systems.GhostArtifactManifestLoadResult
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPromotionReceiptLoadResult
import com.anurag9000.forestrun.systems.GhostPromotionRecoveryCoordinator
import com.anurag9000.forestrun.systems.GhostPromotionRecoveryDisposition
import com.anurag9000.forestrun.systems.GhostRunIdentity

internal enum class RecoveryEvidenceDomain {
    RUN_OUTCOME,
    GHOST_PROMOTION
}

internal enum class RecoveryEvidenceState {
    CLEAN,
    PENDING,
    CORRUPT,
    BLOCKED,
    IO_FAILURE
}

internal data class RecoveryEvidenceSnapshot(
    val domain: RecoveryEvidenceDomain,
    val state: RecoveryEvidenceState,
    val detail: String
)

internal data class RecoveryEvidenceReport(
    val runOutcome: RecoveryEvidenceSnapshot,
    val ghostPromotion: RecoveryEvidenceSnapshot
) {
    fun forDomain(domain: RecoveryEvidenceDomain): RecoveryEvidenceSnapshot = when (domain) {
        RecoveryEvidenceDomain.RUN_OUTCOME -> runOutcome
        RecoveryEvidenceDomain.GHOST_PROMOTION -> ghostPromotion
    }

    /** Stable support text that intentionally excludes run summaries and frame data. */
    fun supportSummary(): String = buildString {
        append("run_outcome=")
        append(runOutcome.state.name)
        append('(')
        append(runOutcome.detail)
        append("); ghost_promotion=")
        append(ghostPromotion.state.name)
        append('(')
        append(ghostPromotion.detail)
        append(')')
    }
}

internal enum class RecoveryDiscardDisposition {
    DISCARDED,
    NOT_APPLICABLE,
    RECOVERED_INSTEAD,
    IO_FAILURE
}

internal data class RecoveryDiscardResult(
    val domain: RecoveryEvidenceDomain,
    val disposition: RecoveryDiscardDisposition,
    val before: RecoveryEvidenceSnapshot,
    val after: RecoveryEvidenceSnapshot
)

internal interface RecoveryEvidenceHandler {
    val domain: RecoveryEvidenceDomain
    fun inspect(): RecoveryEvidenceSnapshot
    fun recoverSafely(): RecoveryEvidenceSnapshot
    fun clearEvidence(): Boolean
}

/**
 * Explicit maintenance seam for otherwise fail-closed recovery evidence.
 *
 * Safe recovery never discards corrupt evidence. Destructive operations are
 * separate methods so support/debug tooling must choose the affected domain and
 * the kind of evidence it intends to remove.
 */
internal class RecoveryEvidenceMaintenanceCoordinator(
    handlers: List<RecoveryEvidenceHandler>
) {
    private val handlersByDomain = handlers.associateBy(RecoveryEvidenceHandler::domain)

    init {
        require(handlersByDomain.size == RecoveryEvidenceDomain.entries.size) {
            "Exactly one recovery handler is required for each evidence domain"
        }
        require(RecoveryEvidenceDomain.entries.all(handlersByDomain::containsKey)) {
            "A recovery evidence domain is missing its maintenance handler"
        }
    }

    @Synchronized
    fun inspect(): RecoveryEvidenceReport = report(
        handlersByDomain.getValue(RecoveryEvidenceDomain.RUN_OUTCOME).inspect(),
        handlersByDomain.getValue(RecoveryEvidenceDomain.GHOST_PROMOTION).inspect()
    )

    @Synchronized
    fun recoverSafely(): RecoveryEvidenceReport {
        val run = recoverIfEligible(
            handlersByDomain.getValue(RecoveryEvidenceDomain.RUN_OUTCOME)
        )
        val ghost = recoverIfEligible(
            handlersByDomain.getValue(RecoveryEvidenceDomain.GHOST_PROMOTION)
        )
        return report(run, ghost)
    }

    @Synchronized
    fun discardCorrupt(domain: RecoveryEvidenceDomain): RecoveryDiscardResult {
        val handler = handlersByDomain.getValue(domain)
        val before = handler.inspect()
        if (before.state != RecoveryEvidenceState.CORRUPT) {
            return RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.NOT_APPLICABLE,
                before = before,
                after = before
            )
        }
        return clear(handler, before, RecoveryDiscardDisposition.DISCARDED)
    }

    /**
     * Attempts safe recovery once more before removing unresolved pending data.
     * Valid evidence that becomes recoverable is therefore completed, not erased.
     * Read failures are never treated as permission to delete unknown evidence.
     */
    @Synchronized
    fun discardUnresolvedPending(domain: RecoveryEvidenceDomain): RecoveryDiscardResult {
        val handler = handlersByDomain.getValue(domain)
        val before = handler.inspect()
        if (before.state == RecoveryEvidenceState.CLEAN ||
            before.state == RecoveryEvidenceState.CORRUPT
        ) {
            return RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.NOT_APPLICABLE,
                before = before,
                after = before
            )
        }
        if (before.state == RecoveryEvidenceState.IO_FAILURE) {
            return RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.IO_FAILURE,
                before = before,
                after = before
            )
        }

        val recovered = handler.recoverSafely()
        return when (recovered.state) {
            RecoveryEvidenceState.CLEAN -> RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.RECOVERED_INSTEAD,
                before = before,
                after = recovered
            )
            RecoveryEvidenceState.CORRUPT -> RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.NOT_APPLICABLE,
                before = before,
                after = recovered
            )
            RecoveryEvidenceState.IO_FAILURE -> RecoveryDiscardResult(
                domain = domain,
                disposition = RecoveryDiscardDisposition.IO_FAILURE,
                before = before,
                after = recovered
            )
            RecoveryEvidenceState.PENDING,
            RecoveryEvidenceState.BLOCKED ->
                clear(handler, before, RecoveryDiscardDisposition.DISCARDED)
        }
    }

    private fun recoverIfEligible(handler: RecoveryEvidenceHandler): RecoveryEvidenceSnapshot {
        val before = handler.inspect()
        return when (before.state) {
            RecoveryEvidenceState.CLEAN,
            RecoveryEvidenceState.CORRUPT -> before
            RecoveryEvidenceState.PENDING,
            RecoveryEvidenceState.BLOCKED,
            RecoveryEvidenceState.IO_FAILURE -> handler.recoverSafely()
        }
    }

    private fun clear(
        handler: RecoveryEvidenceHandler,
        before: RecoveryEvidenceSnapshot,
        successDisposition: RecoveryDiscardDisposition
    ): RecoveryDiscardResult {
        if (!handler.clearEvidence()) {
            return RecoveryDiscardResult(
                domain = handler.domain,
                disposition = RecoveryDiscardDisposition.IO_FAILURE,
                before = before,
                after = handler.inspect()
            )
        }
        val after = handler.inspect()
        val disposition = if (after.state == RecoveryEvidenceState.CLEAN) {
            successDisposition
        } else {
            RecoveryDiscardDisposition.IO_FAILURE
        }
        return RecoveryDiscardResult(
            domain = handler.domain,
            disposition = disposition,
            before = before,
            after = after
        )
    }

    private fun report(
        run: RecoveryEvidenceSnapshot,
        ghost: RecoveryEvidenceSnapshot
    ): RecoveryEvidenceReport = RecoveryEvidenceReport(
        runOutcome = run,
        ghostPromotion = ghost
    )
}

/** Production entrypoint for diagnostics, safe retries, and deliberate repair. */
internal class AndroidRecoveryEvidenceMaintenance(context: Context) {
    private val appContext = context.applicationContext
    private val coordinator = RecoveryEvidenceMaintenanceCoordinator(
        listOf(
            AndroidRunOutcomeEvidenceHandler(appContext),
            AndroidGhostPromotionEvidenceHandler(appContext)
        )
    )

    fun inspect(): RecoveryEvidenceReport = coordinator.inspect()

    fun recoverSafely(): RecoveryEvidenceReport = coordinator.recoverSafely()

    fun discardCorrupt(domain: RecoveryEvidenceDomain): RecoveryDiscardResult =
        coordinator.discardCorrupt(domain)

    fun discardUnresolvedPending(domain: RecoveryEvidenceDomain): RecoveryDiscardResult =
        coordinator.discardUnresolvedPending(domain)
}

private class AndroidRunOutcomeEvidenceHandler(
    private val context: Context
) : RecoveryEvidenceHandler {
    override val domain = RecoveryEvidenceDomain.RUN_OUTCOME
    private val namespace = SaveManager.activePrefsNameForTests
    private val store = SharedPreferencesRunOutcomeRecoveryStore(context, namespace)

    override fun inspect(): RecoveryEvidenceSnapshot = try {
        when (store.load()) {
            RunOutcomeRecoveryLoadResult.Empty -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "no_journal"
            )
            RunOutcomeRecoveryLoadResult.Corrupt -> snapshot(
                RecoveryEvidenceState.CORRUPT,
                "invalid_journal"
            )
            is RunOutcomeRecoveryLoadResult.Pending -> snapshot(
                RecoveryEvidenceState.PENDING,
                "valid_journal"
            )
        }
    } catch (_: Exception) {
        snapshot(RecoveryEvidenceState.IO_FAILURE, "journal_read_failed")
    }

    override fun recoverSafely(): RecoveryEvidenceSnapshot {
        val before = inspect()
        if (before.state == RecoveryEvidenceState.CLEAN ||
            before.state == RecoveryEvidenceState.CORRUPT
        ) return before

        return try {
            RunOutcomePersistenceCoordinator(
                MaintenanceRunOutcomePersistenceSink(
                    context = context,
                    recoveryStore = store,
                    namespace = namespace
                )
            )
            val after = inspect()
            when (after.state) {
                RecoveryEvidenceState.CLEAN -> after.copy(detail = "recovered")
                RecoveryEvidenceState.PENDING -> after.copy(
                    state = RecoveryEvidenceState.BLOCKED,
                    detail = "journal_conflict_or_write_failure"
                )
                else -> after
            }
        } catch (_: Exception) {
            snapshot(RecoveryEvidenceState.IO_FAILURE, "journal_recovery_failed")
        }
    }

    override fun clearEvidence(): Boolean = try {
        store.clear()
    } catch (_: Exception) {
        false
    }

    private fun snapshot(
        state: RecoveryEvidenceState,
        detail: String
    ): RecoveryEvidenceSnapshot = RecoveryEvidenceSnapshot(domain, state, detail)
}

private class MaintenanceRunOutcomePersistenceSink(
    private val context: Context,
    override val recoveryStore: RunOutcomeRecoveryStore,
    namespace: String
) : RecoverableRunOutcomePersistenceSink {
    override val summarySnapshotStore: RunOutcomeSummarySnapshotStore =
        SharedPreferencesRunOutcomeSummarySnapshotStore(context, namespace)

    override fun loadBestDistanceM(): Float = SaveManager.loadBestDistance(context)

    override fun publishBestGhost(frames: List<GhostFrame>, distanceM: Float): Boolean = false

    override fun recordForestMood(summary: RunSummary) {
        error("Maintenance recovery must use complete mood snapshots")
    }

    override fun recordReturnMoment(summary: RunSummary) {
        error("Maintenance recovery must use complete return snapshots")
    }

    override fun saveLastRunSummary(summary: RunSummary) {
        error("Maintenance recovery must use the atomic summary snapshot")
    }

    override fun loadForestMoodState(): ForestMoodState =
        SaveManager.loadForestMoodState(context)

    override fun saveForestMoodState(state: ForestMoodState) {
        SaveManager.saveForestMoodState(context, state)
    }

    override fun loadReturnMomentState(): ReturnMomentState =
        SaveManager.loadReturnMomentState(context)

    override fun saveReturnMomentState(state: ReturnMomentState) {
        SaveManager.saveReturnMomentState(context, state)
    }

    override fun loadLastRunSummary(): RunSummary? =
        SaveManager.loadLastRunSummary(context)

    override fun loadRouteTierCount(tier: PacifistRouteTier): Int =
        SaveManager.loadRouteTierCount(context, tier)
}

private class AndroidGhostPromotionEvidenceHandler(
    private val context: Context
) : RecoveryEvidenceHandler {
    override val domain = RecoveryEvidenceDomain.GHOST_PROMOTION
    private val ghostFilename = SaveManager.activeGhostFilenameForTests
    private val receiptStore = AtomicFileGhostPromotionReceiptStore(
        context = context,
        ghostFilename = ghostFilename
    )
    private val manifestStore = AtomicFileGhostArtifactManifestStore(
        context = context,
        ghostFilename = ghostFilename
    )
    private val recovery = GhostPromotionRecoveryCoordinator(
        receiptStore = receiptStore,
        artifactStore = AndroidGhostPromotionArtifactStore(context),
        manifestStore = manifestStore
    )

    override fun inspect(): RecoveryEvidenceSnapshot = try {
        when (receiptStore.load()) {
            GhostPromotionReceiptLoadResult.Corrupt -> snapshot(
                RecoveryEvidenceState.CORRUPT,
                "invalid_receipt"
            )
            is GhostPromotionReceiptLoadResult.Pending -> snapshot(
                RecoveryEvidenceState.PENDING,
                "valid_receipt"
            )
            GhostPromotionReceiptLoadResult.Empty -> inspectManifest()
        }
    } catch (_: Exception) {
        snapshot(RecoveryEvidenceState.IO_FAILURE, "ghost_evidence_read_failed")
    }

    override fun recoverSafely(): RecoveryEvidenceSnapshot = try {
        when (recovery.recover()) {
            GhostPromotionRecoveryDisposition.EMPTY -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "no_evidence"
            )
            GhostPromotionRecoveryDisposition.REPAIRED_DISTANCE -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "distance_repaired"
            )
            GhostPromotionRecoveryDisposition.ALREADY_APPLIED -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "already_applied"
            )
            GhostPromotionRecoveryDisposition.ABANDONED_UNWRITTEN_GHOST -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "unwritten_candidate_abandoned"
            )
            GhostPromotionRecoveryDisposition.CORRUPT_RECEIPT -> snapshot(
                RecoveryEvidenceState.CORRUPT,
                "invalid_receipt"
            )
            GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST -> snapshot(
                RecoveryEvidenceState.CORRUPT,
                "invalid_manifest_or_artifact"
            )
            GhostPromotionRecoveryDisposition.IO_FAILURE -> snapshot(
                RecoveryEvidenceState.IO_FAILURE,
                "ghost_recovery_failed"
            )
        }
    } catch (_: Exception) {
        snapshot(RecoveryEvidenceState.IO_FAILURE, "ghost_recovery_failed")
    }

    override fun clearEvidence(): Boolean = try {
        val receiptCleared = when (receiptStore.load()) {
            GhostPromotionReceiptLoadResult.Empty -> true
            GhostPromotionReceiptLoadResult.Corrupt,
            is GhostPromotionReceiptLoadResult.Pending -> receiptStore.clear()
        }
        val manifestCleared = when (val loaded = manifestStore.load()) {
            GhostArtifactManifestLoadResult.Empty -> true
            GhostArtifactManifestLoadResult.Corrupt -> manifestStore.clear()
            is GhostArtifactManifestLoadResult.Present -> {
                if (manifestMatches(loaded.manifest)) true else manifestStore.clear()
            }
        }
        receiptCleared && manifestCleared
    } catch (_: Exception) {
        false
    }

    private fun inspectManifest(): RecoveryEvidenceSnapshot =
        when (val loaded = manifestStore.load()) {
            GhostArtifactManifestLoadResult.Empty -> snapshot(
                RecoveryEvidenceState.CLEAN,
                "no_evidence"
            )
            GhostArtifactManifestLoadResult.Corrupt -> snapshot(
                RecoveryEvidenceState.CORRUPT,
                "invalid_manifest"
            )
            is GhostArtifactManifestLoadResult.Present -> {
                if (manifestMatches(loaded.manifest)) {
                    snapshot(RecoveryEvidenceState.CLEAN, "valid_manifest")
                } else {
                    snapshot(
                        RecoveryEvidenceState.CORRUPT,
                        "manifest_artifact_mismatch"
                    )
                }
            }
        }

    private fun manifestMatches(manifest: GhostArtifactManifest): Boolean {
        val frames = SaveManager.loadGhostRun(context)
        return GhostRunIdentity.matches(
            frames = frames,
            distanceM = manifest.distanceM,
            frameCount = manifest.frameCount,
            fingerprint = manifest.fingerprint,
            sha256Hex = manifest.sha256Hex
        )
    }

    private fun snapshot(
        state: RecoveryEvidenceState,
        detail: String
    ): RecoveryEvidenceSnapshot = RecoveryEvidenceSnapshot(domain, state, detail)
}
