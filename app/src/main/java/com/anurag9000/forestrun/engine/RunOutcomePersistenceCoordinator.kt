package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager

internal enum class RunOutcomeCommitDisposition {
    COMMITTED,
    NON_PERSISTENT_RUN,
    ALREADY_COMMITTED,
    RECOVERY_PENDING,
    RECOVERY_BLOCKED
}

internal data class RunOutcomeCommitResult(
    val disposition: RunOutcomeCommitDisposition,
    val ghostPromoted: Boolean
) {
    val committed: Boolean
        get() = disposition == RunOutcomeCommitDisposition.COMMITTED
}

/** Exactly-once terminal persistence seam used by higher-level outcome owners. */
internal interface RunOutcomeCommitter {
    fun commit(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult
}

/** Side-effect seam used by [RunOutcomePersistenceCoordinator]. */
internal interface RunOutcomePersistenceSink {
    fun loadBestDistanceM(): Float
    fun publishBestGhost(frames: List<GhostFrame>): Boolean
    fun saveBestDistanceM(distanceM: Float)
    fun recordForestMood(summary: RunSummary)
    fun recordReturnMoment(summary: RunSummary)
    fun saveLastRunSummary(summary: RunSummary)
}

/** Optional capability that makes the non-ghost bundle crash recoverable. */
internal interface RecoverableRunOutcomePersistenceSink : RunOutcomePersistenceSink {
    val recoveryStore: RunOutcomeRecoveryStore
    val summarySnapshotStore: RunOutcomeSummarySnapshotStore
    fun loadForestMoodState(): ForestMoodState
    fun saveForestMoodState(state: ForestMoodState)
    fun loadReturnMomentState(): ReturnMomentState
    fun saveReturnMomentState(state: ReturnMomentState)
    fun loadLastRunSummary(): RunSummary?
    fun loadRouteTierCount(tier: PacifistRouteTier): Int
}

/** Production adapter for the terminal-run persistence surface. */
internal class AndroidRunOutcomePersistenceSink(context: Context) :
    RecoverableRunOutcomePersistenceSink {
    private val appContext = context.applicationContext
    private val persistenceNamespace = SaveManager.activePrefsNameForTests

    override val recoveryStore: RunOutcomeRecoveryStore =
        SharedPreferencesRunOutcomeRecoveryStore(
            context = appContext,
            persistenceNamespace = persistenceNamespace
        )

    override val summarySnapshotStore: RunOutcomeSummarySnapshotStore =
        SharedPreferencesRunOutcomeSummarySnapshotStore(
            context = appContext,
            persistenceNamespace = persistenceNamespace
        )

    override fun loadBestDistanceM(): Float = SaveManager.loadBestDistance(appContext)

    override fun publishBestGhost(frames: List<GhostFrame>): Boolean =
        GhostPersistenceManager.saveBestRunAsync(appContext, frames)

    override fun saveBestDistanceM(distanceM: Float) {
        SaveManager.saveBestDistance(appContext, distanceM)
    }

    override fun recordForestMood(summary: RunSummary) {
        ForestMoodSystem.recordRun(appContext, summary)
    }

    override fun recordReturnMoment(summary: RunSummary) {
        ReturnMomentsSystem.recordRunOutcome(appContext, summary)
    }

    override fun saveLastRunSummary(summary: RunSummary) {
        SaveManager.saveLastRunSummary(appContext, summary)
    }

    override fun loadForestMoodState(): ForestMoodState =
        SaveManager.loadForestMoodState(appContext)

    override fun saveForestMoodState(state: ForestMoodState) {
        SaveManager.saveForestMoodState(appContext, state)
    }

    override fun loadReturnMomentState(): ReturnMomentState =
        SaveManager.loadReturnMomentState(appContext)

    override fun saveReturnMomentState(state: ReturnMomentState) {
        SaveManager.saveReturnMomentState(appContext, state)
    }

    override fun loadLastRunSummary(): RunSummary? =
        SaveManager.loadLastRunSummary(appContext)

    override fun loadRouteTierCount(tier: PacifistRouteTier): Int =
        SaveManager.loadRouteTierCount(appContext, tier)
}

/**
 * Exactly-once owner for persistence caused by one terminal run outcome.
 *
 * The committed flag is claimed before the first sink call. This fail-closed
 * ordering prevents re-entrant or repeated collision delivery from duplicating
 * counters and summaries. Non-persistent terminal outcomes also consume the
 * token so a later mode change cannot retroactively write the same run.
 * [resetForNewRun] is the only operation that reopens the coordinator.
 *
 * Production sinks synchronously journal the summary and the before/after
 * progression states before any write. Recovery compares actual state with both
 * snapshots, allowing a write that completed before its checkpoint to be
 * recognized without incrementing the same run twice.
 */
internal class RunOutcomePersistenceCoordinator(
    private val sink: RunOutcomePersistenceSink,
    private val clock: () -> Long = System::currentTimeMillis
) : RunOutcomeCommitter {
    private val recoverableSink = sink as? RecoverableRunOutcomePersistenceSink
    private var terminalOutcomeCommitted = false
    private var recoveryBlocked = !recoverPendingOutcome()

    @Synchronized
    fun resetForNewRun() {
        terminalOutcomeCommitted = false
        recoveryBlocked = !recoverPendingOutcome()
    }

    @Synchronized
    override fun commit(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult {
        if (terminalOutcomeCommitted) {
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.ALREADY_COMMITTED,
                ghostPromoted = false
            )
        }

        terminalOutcomeCommitted = true

        if (!persistProgress) {
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.NON_PERSISTENT_RUN,
                ghostPromoted = false
            )
        }

        if (recoveryBlocked) {
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.RECOVERY_BLOCKED,
                ghostPromoted = false
            )
        }

        val recoveryRecord = recoverableSink?.let { recoverable ->
            prepareRecoveryRecord(recoverable, summary)
        }
        if (recoverableSink != null && recoveryRecord == null) {
            recoveryBlocked = true
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.RECOVERY_BLOCKED,
                ghostPromoted = false
            )
        }

        val completedDistance = summary.distanceM
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0f)
            ?: 0f
        val previousBestDistance = sink.loadBestDistanceM()
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0f)
            ?: 0f

        val ghostPromoted =
            completedDistance > previousBestDistance &&
                completedGhost.isNotEmpty() &&
                sink.publishBestGhost(completedGhost)
        if (ghostPromoted) {
            sink.saveBestDistanceM(completedDistance)
        }

        if (recoveryRecord != null && recoverableSink != null) {
            return commitRecoveryProtectedBundle(
                recoverable = recoverableSink,
                initialRecord = recoveryRecord,
                ghostPromoted = ghostPromoted
            )
        }

        sink.recordForestMood(summary)
        sink.recordReturnMoment(summary)
        sink.saveLastRunSummary(summary)

        return RunOutcomeCommitResult(
            disposition = RunOutcomeCommitDisposition.COMMITTED,
            ghostPromoted = ghostPromoted
        )
    }

    private fun prepareRecoveryRecord(
        recoverable: RecoverableRunOutcomePersistenceSink,
        summary: RunSummary
    ): RunOutcomeRecoveryRecord? = try {
        val previousMood = recoverable.loadForestMoodState()
        val previousReturn = recoverable.loadReturnMomentState()
        val previousRouteTierCount = recoverable.loadRouteTierCount(summary.pacifistRouteTier)
            .coerceAtLeast(0)
        val record = RunOutcomeRecoveryRecord(
            phase = RunOutcomeRecoveryPhase.PREPARED,
            summary = summary,
            previousMood = previousMood,
            nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary),
            previousReturn = previousReturn,
            nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
                previous = previousReturn,
                summary = summary,
                nowMs = clock()
            ),
            previousRouteTierCount = previousRouteTierCount,
            nextRouteTierCount = RunOutcomeRecoveryTransitions.nextRouteTierCount(
                previous = previousRouteTierCount,
                tier = summary.pacifistRouteTier
            )
        )
        record.takeIf { recoverable.recoveryStore.save(it) }
    } catch (_: Exception) {
        null
    }

    private fun commitRecoveryProtectedBundle(
        recoverable: RecoverableRunOutcomePersistenceSink,
        initialRecord: RunOutcomeRecoveryRecord,
        ghostPromoted: Boolean
    ): RunOutcomeCommitResult {
        return try {
            var record = initialRecord
            if (!ensureMoodState(recoverable, record)) {
                recoveryBlocked = true
                return recoveryPending(ghostPromoted)
            }
            record = record.copy(phase = RunOutcomeRecoveryPhase.MOOD_APPLIED)
            recoverable.recoveryStore.save(record)

            if (!ensureReturnState(recoverable, record)) {
                recoveryBlocked = true
                return recoveryPending(ghostPromoted)
            }
            record = record.copy(phase = RunOutcomeRecoveryPhase.RETURN_APPLIED)
            recoverable.recoveryStore.save(record)

            if (!ensureSummaryState(recoverable, record)) {
                recoveryBlocked = true
                return recoveryPending(ghostPromoted)
            }
            record = record.copy(phase = RunOutcomeRecoveryPhase.SUMMARY_APPLIED)
            recoverable.recoveryStore.save(record)

            if (!recoverable.recoveryStore.clear()) {
                recoveryBlocked = true
                return recoveryPending(ghostPromoted)
            }

            RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.COMMITTED,
                ghostPromoted = ghostPromoted
            )
        } catch (_: Exception) {
            recoveryBlocked = true
            recoveryPending(ghostPromoted)
        }
    }

    private fun recoverPendingOutcome(): Boolean {
        val recoverable = recoverableSink ?: return true
        return try {
            when (val loaded = recoverable.recoveryStore.load()) {
                RunOutcomeRecoveryLoadResult.Empty -> true
                RunOutcomeRecoveryLoadResult.Corrupt -> false
                is RunOutcomeRecoveryLoadResult.Pending -> {
                    var record = loaded.record
                    if (!ensureMoodState(recoverable, record)) return false
                    record = record.copy(phase = RunOutcomeRecoveryPhase.MOOD_APPLIED)
                    recoverable.recoveryStore.save(record)

                    if (!ensureReturnState(recoverable, record)) return false
                    record = record.copy(phase = RunOutcomeRecoveryPhase.RETURN_APPLIED)
                    recoverable.recoveryStore.save(record)

                    if (!ensureSummaryState(recoverable, record)) return false
                    record = record.copy(phase = RunOutcomeRecoveryPhase.SUMMARY_APPLIED)
                    recoverable.recoveryStore.save(record)
                    recoverable.recoveryStore.clear()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureMoodState(
        recoverable: RecoverableRunOutcomePersistenceSink,
        record: RunOutcomeRecoveryRecord
    ): Boolean {
        val actual = recoverable.loadForestMoodState()
        if (actual == record.nextMood) return true
        if (actual != record.previousMood) return false
        recoverable.saveForestMoodState(record.nextMood)
        return recoverable.loadForestMoodState() == record.nextMood
    }

    private fun ensureReturnState(
        recoverable: RecoverableRunOutcomePersistenceSink,
        record: RunOutcomeRecoveryRecord
    ): Boolean {
        val actual = recoverable.loadReturnMomentState()
        if (actual == record.nextReturn) return true
        if (actual != record.previousReturn) return false
        recoverable.saveReturnMomentState(record.nextReturn)
        return recoverable.loadReturnMomentState() == record.nextReturn
    }

    private fun ensureSummaryState(
        recoverable: RecoverableRunOutcomePersistenceSink,
        record: RunOutcomeRecoveryRecord
    ): Boolean {
        val expectedSummary = RunOutcomeRecoveryTransitions.persistedSummary(record.summary)
        val routeTier = expectedSummary.pacifistRouteTier
        val actualSummary = recoverable.loadLastRunSummary()
        val actualRouteTierCount = recoverable.loadRouteTierCount(routeTier).coerceAtLeast(0)

        if (actualSummary == expectedSummary &&
            actualRouteTierCount == record.nextRouteTierCount
        ) return true
        if (actualRouteTierCount != record.previousRouteTierCount) return false
        if (!recoverable.summarySnapshotStore.save(
                summary = expectedSummary,
                routeTierCount = record.nextRouteTierCount
            )
        ) return false

        return recoverable.loadLastRunSummary() == expectedSummary &&
            recoverable.loadRouteTierCount(routeTier).coerceAtLeast(0) ==
            record.nextRouteTierCount
    }

    private fun recoveryPending(ghostPromoted: Boolean): RunOutcomeCommitResult =
        RunOutcomeCommitResult(
            disposition = RunOutcomeCommitDisposition.RECOVERY_PENDING,
            ghostPromoted = ghostPromoted
        )
}
