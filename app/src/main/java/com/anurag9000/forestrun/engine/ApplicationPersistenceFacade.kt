package com.anurag9000.forestrun.engine

import android.content.Context
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
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

/** Resolved-encounter memory mutations used by runtime collision/pass owners. */
internal interface ApplicationEncounterPersistence {
    fun recordEncounter(type: EntityType)
    fun recordPass(type: EntityType)
    fun recordHit(type: EntityType)
}

internal class AndroidApplicationEncounterPersistence(context: Context) :
    ApplicationEncounterPersistence {
    private val appContext = context.applicationContext

    override fun recordEncounter(type: EntityType) {
        PersistentMemoryManager.recordEncounter(appContext, type)
    }

    override fun recordPass(type: EntityType) {
        PersistentMemoryManager.recordPass(appContext, type)
    }

    override fun recordHit(type: EntityType) {
        PersistentMemoryManager.recordHit(appContext, type)
    }
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
 * The facade centralizes discovery and mutation policy without pretending that
 * SharedPreferences, AtomicFile ghost artifacts, and recovery journals form one
 * ACID transaction. Every operation remains owned by one independently
 * recoverable durability domain and returns that domain's authoritative result.
 *
 * Implementing the collision relationship ports and [ApplicationRunOutcomePort]
 * lets live gameplay share this boundary without introducing adapter-specific
 * writes back into GameView.
 */
internal class ApplicationPersistenceFacade(
    private val runOutcomes: ApplicationRunOutcomePort,
    private val purchaseGardenPlant: (Int) -> GardenPurchaseResult,
    private val feedbackWriter: (FeedbackPreferences) -> FeedbackPreferences,
    private val wardrobeWriter: (CostumeStyle) -> Boolean,
    private val encounterPersistence: ApplicationEncounterPersistence,
    private val recoveryMaintenance: ApplicationRecoveryMaintenance
) : ApplicationRunOutcomePort,
    ApplicationEncounterPersistence,
    TerminalHitRelationshipRecorder,
    NonTerminalCollisionRelationshipRecorder {

    override fun resetForNewRun() = runOutcomes.resetForNewRun()

    override fun commit(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult = runOutcomes.commit(
        summary = summary,
        completedGhost = completedGhost,
        persistProgress = persistProgress
    )

    fun commitTerminalOutcome(
        summary: RunSummary,
        completedGhost: List<GhostFrame>,
        persistProgress: Boolean
    ): RunOutcomeCommitResult = commit(
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

    override fun recordEncounter(type: EntityType) =
        encounterPersistence.recordEncounter(type)

    override fun recordPass(type: EntityType) =
        encounterPersistence.recordPass(type)

    override fun recordHit(type: EntityType) =
        encounterPersistence.recordHit(type)

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
                encounterPersistence = AndroidApplicationEncounterPersistence(appContext),
                recoveryMaintenance = AndroidApplicationRecoveryMaintenance(appContext)
            )
        }
    }
}
