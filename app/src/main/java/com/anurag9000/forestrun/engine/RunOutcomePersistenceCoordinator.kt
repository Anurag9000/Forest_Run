package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager

internal enum class RunOutcomeCommitDisposition {
    COMMITTED,
    NON_PERSISTENT_RUN,
    ALREADY_COMMITTED
}

internal data class RunOutcomeCommitResult(
    val disposition: RunOutcomeCommitDisposition,
    val ghostPromoted: Boolean
) {
    val committed: Boolean
        get() = disposition == RunOutcomeCommitDisposition.COMMITTED
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

/** Production adapter for the terminal-run persistence surface. */
internal class AndroidRunOutcomePersistenceSink(context: Context) : RunOutcomePersistenceSink {
    private val appContext = context.applicationContext

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
}

/**
 * Exactly-once owner for persistence caused by one terminal run outcome.
 *
 * The committed flag is claimed before the first sink call. This fail-closed
 * ordering prevents re-entrant or repeated collision delivery from duplicating
 * counters and summaries. [resetForNewRun] is the only operation that reopens
 * the coordinator.
 */
internal class RunOutcomePersistenceCoordinator(
    private val sink: RunOutcomePersistenceSink
) {
    private var terminalOutcomeCommitted = false

    @Synchronized
    fun resetForNewRun() {
        terminalOutcomeCommitted = false
    }

    @Synchronized
    fun commit(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult {
        if (!persistProgress) {
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.NON_PERSISTENT_RUN,
                ghostPromoted = false
            )
        }
        if (terminalOutcomeCommitted) {
            return RunOutcomeCommitResult(
                disposition = RunOutcomeCommitDisposition.ALREADY_COMMITTED,
                ghostPromoted = false
            )
        }

        terminalOutcomeCommitted = true

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

        sink.recordForestMood(summary)
        sink.recordReturnMoment(summary)
        sink.saveLastRunSummary(summary)

        return RunOutcomeCommitResult(
            disposition = RunOutcomeCommitDisposition.COMMITTED,
            ghostPromoted = ghostPromoted
        )
    }
}
