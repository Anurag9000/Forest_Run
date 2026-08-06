package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.systems.GhostFrame

/** Resettable exactly-once terminal-outcome capability exposed to the app. */
internal interface ApplicationRunOutcomePort : RunOutcomeCommitter {
    fun resetForNewRun()
}

private class CoordinatorRunOutcomePort(
    private val coordinator: RunOutcomePersistenceCoordinator
) : ApplicationRunOutcomePort {
    override fun resetForNewRun() = coordinator.resetForNewRun()

    override fun commit(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult = coordinator.commit(
        summary = summary,
        completedGhost = completedGhost,
        persistProgress = persistProgress
    )
}

/** Independent recovery-maintenance capability exposed through the app facade. */
internal interface ApplicationRecoveryMaintenance {
    fun inspect(): RecoveryEvidenceReport
    fun recoverSafely(): RecoveryEvidenceReport
    fun discardCorrupt(domain: RecoveryEvidenceDomain): RecoveryDiscardResult
    fun discardUnresolvedPending(domain: RecoveryEvidenceDomain): RecoveryDiscardResult
}

private class AndroidApplicationRecoveryMaintenance(context: Context) :
    ApplicationRecoveryMaintenance {
    private val delegate = AndroidRecoveryEvidenceMaintenance(context)

    override fun inspect(): RecoveryEvidenceReport = delegate.inspect()
    override fun recoverSafely(): RecoveryEvidenceReport = delegate.recoverSafely()
    override fun discardCorrupt(domain: RecoveryEvidenceDomain): RecoveryDiscardResult =
        delegate.discardCorrupt(domain)

    override fun discardUnresolvedPending(
        domain: RecoveryEvidenceDomain
    ): RecoveryDiscardResult = delegate.discardUnresolvedPending(domain)
}

/**
 * Application-facing persistence boundary.
 *
 * The facade centralizes discovery and policy without pretending that
 * SharedPreferences, AtomicFile ghost artifacts, and recovery journals form one
 * ACID transaction. Every method represents one independently recoverable
 * durability domain and returns that domain's authoritative result.
 */
internal class ApplicationPersistenceFacade(
    private val runOutcomes: ApplicationRunOutcomePort,
    private val purchaseGardenPlant: (Int) -> GardenPurchaseResult,
    private val feedbackWriter: (FeedbackPreferences) -> FeedbackPreferences,
    private val wardrobeWriter: (CostumeStyle) -> Boolean,
    private val recoveryMaintenance: ApplicationRecoveryMaintenance
) {
    fun resetForNewRun() = runOutcomes.resetForNewRun()

    fun commitTerminalOutcome(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult = runOutcomes.commit(
        summary = summary,
        completedGhost = completedGhost,
        persistProgress = persistProgress
    )

    fun purchaseNextGardenPlant(requestedIndex: Int): GardenPurchaseResult =
        purchaseGardenPlant(requestedIndex)

    fun saveFeedbackPreferences(
        preferences: FeedbackPreferences
    ): FeedbackPreferences = feedbackWriter(preferences)

    fun equipCostume(style: CostumeStyle): Boolean = wardrobeWriter(style)

    fun inspectRecoveryEvidence(): RecoveryEvidenceReport =
        recoveryMaintenance.inspect()

    fun recoverSafely(): RecoveryEvidenceReport =
        recoveryMaintenance.recoverSafely()

    fun discardCorruptRecoveryEvidence(
        domain: RecoveryEvidenceDomain
    ): RecoveryDiscardResult = recoveryMaintenance.discardCorrupt(domain)

    fun discardUnresolvedPendingRecoveryEvidence(
        domain: RecoveryEvidenceDomain
    ): RecoveryDiscardResult = recoveryMaintenance.discardUnresolvedPending(domain)

    companion object {
        fun android(context: Context): ApplicationPersistenceFacade {
            val appContext = context.applicationContext
            return ApplicationPersistenceFacade(
                runOutcomes = CoordinatorRunOutcomePort(
                    RunOutcomePersistenceCoordinator(
                        AndroidRunOutcomePersistenceSink(appContext)
                    )
                ),
                purchaseGardenPlant = { requestedIndex ->
                    GardenPurchaseManager.purchaseNext(appContext, requestedIndex)
                },
                feedbackWriter = { preferences ->
                    FeedbackSettings.setReducedMotion(
                        appContext,
                        preferences.reducedMotion
                    )
                    FeedbackSettings.setAudioEnabled(
                        appContext,
                        preferences.audioEnabled
                    )
                    FeedbackSettings.setHapticsEnabled(
                        appContext,
                        preferences.hapticsEnabled
                    )
                    FeedbackSettings.snapshot()
                },
                wardrobeWriter = { style ->
                    CostumeManager.equip(appContext, style)
                },
                recoveryMaintenance = AndroidApplicationRecoveryMaintenance(appContext)
            )
        }
    }
}
