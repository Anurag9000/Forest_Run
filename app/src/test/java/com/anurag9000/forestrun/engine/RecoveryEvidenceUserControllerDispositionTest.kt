package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEvidenceUserControllerDispositionTest {
    @Test
    fun ioFailureNeverReportsDestructiveCompletion() {
        val current = report(RecoveryEvidenceState.CORRUPT)
        val controller = controller(
            inspect = { current },
            discard = {
                result(
                    disposition = RecoveryDiscardDisposition.IO_FAILURE,
                    beforeState = RecoveryEvidenceState.CORRUPT,
                    afterState = RecoveryEvidenceState.CORRUPT
                )
            }
        )

        val action = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_CORRUPT,
            confirmed = true
        )

        assertEquals(
            RecoveryUserActionDisposition.ACTION_FAILED,
            action.disposition
        )
        assertTrue(action.model.hasActionableIssue)
    }

    @Test
    fun maintenanceRaceMapsNotApplicableToUnavailable() {
        val current = report(RecoveryEvidenceState.CORRUPT)
        val controller = controller(
            inspect = { current },
            discard = {
                result(
                    disposition = RecoveryDiscardDisposition.NOT_APPLICABLE,
                    beforeState = RecoveryEvidenceState.CLEAN,
                    afterState = RecoveryEvidenceState.CLEAN
                )
            }
        )

        val action = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_CORRUPT,
            confirmed = true
        )

        assertEquals(
            RecoveryUserActionDisposition.NOT_AVAILABLE,
            action.disposition
        )
    }

    @Test
    fun recoveredInsteadIsACompletedNonDestructiveOutcome() {
        var current = report(RecoveryEvidenceState.PENDING)
        val controller = controller(
            inspect = { current },
            discard = {
                current = report(RecoveryEvidenceState.CLEAN)
                result(
                    disposition = RecoveryDiscardDisposition.RECOVERED_INSTEAD,
                    beforeState = RecoveryEvidenceState.PENDING,
                    afterState = RecoveryEvidenceState.CLEAN
                )
            }
        )

        val action = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_UNRESOLVED_PENDING,
            confirmed = true
        )

        assertEquals(RecoveryUserActionDisposition.COMPLETED, action.disposition)
        assertFalse(action.model.hasActionableIssue)
    }

    @Test
    fun successfulDiscardRefreshesBothIndependentDomains() {
        var current = report(
            runState = RecoveryEvidenceState.CORRUPT,
            ghostState = RecoveryEvidenceState.PENDING
        )
        val controller = controller(
            inspect = { current },
            discard = {
                current = report(
                    runState = RecoveryEvidenceState.CLEAN,
                    ghostState = RecoveryEvidenceState.PENDING
                )
                result(
                    disposition = RecoveryDiscardDisposition.DISCARDED,
                    beforeState = RecoveryEvidenceState.CORRUPT,
                    afterState = RecoveryEvidenceState.CLEAN
                )
            }
        )

        val action = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_CORRUPT,
            confirmed = true
        )

        assertEquals(RecoveryUserActionDisposition.COMPLETED, action.disposition)
        assertTrue(action.model.hasActionableIssue)
        assertEquals(
            RecoveryEvidenceStateLabel.RECOVERY_PENDING,
            stateLabel(
                action.model.rows.first {
                    it.domain == RecoveryEvidenceDomain.GHOST_PROMOTION
                }.stateLabel
            )
        )
    }

    private fun controller(
        inspect: () -> RecoveryEvidenceReport,
        discard: (RecoveryEvidenceDomain) -> RecoveryDiscardResult
    ): RecoveryEvidenceUserController = RecoveryEvidenceUserController(
        inspectEvidence = inspect,
        recoverSafely = inspect,
        discardCorrupt = discard,
        discardUnresolvedPending = discard
    )

    private fun report(
        runState: RecoveryEvidenceState,
        ghostState: RecoveryEvidenceState = RecoveryEvidenceState.CLEAN
    ): RecoveryEvidenceReport = RecoveryEvidenceReport(
        runOutcome = snapshot(RecoveryEvidenceDomain.RUN_OUTCOME, runState),
        ghostPromotion = snapshot(RecoveryEvidenceDomain.GHOST_PROMOTION, ghostState)
    )

    private fun snapshot(
        domain: RecoveryEvidenceDomain,
        state: RecoveryEvidenceState
    ): RecoveryEvidenceSnapshot = RecoveryEvidenceSnapshot(
        domain = domain,
        state = state,
        detail = when (state) {
            RecoveryEvidenceState.CLEAN -> "no_evidence"
            RecoveryEvidenceState.PENDING -> "valid_journal"
            RecoveryEvidenceState.CORRUPT -> "invalid_journal"
            RecoveryEvidenceState.BLOCKED -> "journal_conflict_or_write_failure"
            RecoveryEvidenceState.IO_FAILURE -> "journal_read_failed"
        }
    )

    private fun result(
        disposition: RecoveryDiscardDisposition,
        beforeState: RecoveryEvidenceState,
        afterState: RecoveryEvidenceState
    ): RecoveryDiscardResult = RecoveryDiscardResult(
        domain = RecoveryEvidenceDomain.RUN_OUTCOME,
        disposition = disposition,
        before = snapshot(RecoveryEvidenceDomain.RUN_OUTCOME, beforeState),
        after = snapshot(RecoveryEvidenceDomain.RUN_OUTCOME, afterState)
    )

    private fun stateLabel(label: String): RecoveryEvidenceStateLabel = when (label) {
        "Recovery pending" -> RecoveryEvidenceStateLabel.RECOVERY_PENDING
        else -> RecoveryEvidenceStateLabel.OTHER
    }

    private enum class RecoveryEvidenceStateLabel {
        RECOVERY_PENDING,
        OTHER
    }
}
