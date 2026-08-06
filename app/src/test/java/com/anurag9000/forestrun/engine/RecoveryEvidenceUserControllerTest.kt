package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEvidenceUserControllerTest {
    @Test
    fun presentationOffersOnlyStateAppropriateActions() {
        val states = listOf(
            RecoveryEvidenceState.CLEAN to emptyList(),
            RecoveryEvidenceState.PENDING to listOf(
                RecoveryUiAction.SAFE_RETRY,
                RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
            ),
            RecoveryEvidenceState.BLOCKED to listOf(
                RecoveryUiAction.SAFE_RETRY,
                RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
            ),
            RecoveryEvidenceState.CORRUPT to listOf(
                RecoveryUiAction.DISCARD_CORRUPT
            ),
            RecoveryEvidenceState.IO_FAILURE to listOf(
                RecoveryUiAction.SAFE_RETRY
            )
        )

        states.forEach { (state, actions) ->
            val model = RecoveryEvidencePresentation.present(
                report(runState = state, runDetail = "unknown-private-detail")
            )
            val row = model.rows.first {
                it.domain == RecoveryEvidenceDomain.RUN_OUTCOME
            }
            assertEquals(actions, row.actions)
            assertEquals(actions.isNotEmpty(), model.hasActionableIssue)
            assertFalse(row.detail.contains("unknown-private-detail"))
        }
    }

    @Test
    fun pendingValidManifestUsesPendingCopyRatherThanHealthyCopy() {
        val model = RecoveryEvidencePresentation.present(
            report(
                ghostState = RecoveryEvidenceState.PENDING,
                ghostDetail = "valid_manifest"
            )
        )
        val row = model.rows.first {
            it.domain == RecoveryEvidenceDomain.GHOST_PROMOTION
        }

        assertEquals("Recovery pending", row.stateLabel)
        assertEquals("Saved progress is waiting for a safe retry.", row.detail)
        assertTrue(RecoveryUiAction.SAFE_RETRY in row.actions)
    }

    @Test
    fun destructiveActionRequiresConfirmationAndDoesNotCallStorageEarly() {
        val calls = mutableListOf<String>()
        var current = report(runState = RecoveryEvidenceState.CORRUPT)
        val controller = controller(
            inspect = { current },
            recover = { calls += "recover"; current },
            discardCorrupt = { domain ->
                calls += "discardCorrupt:${domain.name}"
                current = report()
                discarded(domain)
            },
            discardPending = { domain ->
                calls += "discardPending:${domain.name}"
                discarded(domain)
            }
        )

        val confirmation = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_CORRUPT
        )
        assertEquals(
            RecoveryUserActionDisposition.CONFIRMATION_REQUIRED,
            confirmation.disposition
        )
        assertTrue(calls.isEmpty())

        val completed = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_CORRUPT,
            confirmed = true
        )
        assertEquals(RecoveryUserActionDisposition.COMPLETED, completed.disposition)
        assertEquals(listOf("discardCorrupt:RUN_OUTCOME"), calls)
        assertFalse(completed.model.hasActionableIssue)
    }

    @Test
    fun safeRetryRoutesOnceWithoutDestructiveConfirmation() {
        val calls = mutableListOf<String>()
        var current = report(ghostState = RecoveryEvidenceState.PENDING)
        val controller = controller(
            inspect = { current },
            recover = {
                calls += "recover"
                current = report()
                current
            },
            discardCorrupt = { error("must not discard corrupt") },
            discardPending = { error("must not discard pending") }
        )

        val result = controller.perform(
            RecoveryEvidenceDomain.GHOST_PROMOTION,
            RecoveryUiAction.SAFE_RETRY
        )

        assertEquals(RecoveryUserActionDisposition.COMPLETED, result.disposition)
        assertEquals(listOf("recover"), calls)
        assertFalse(result.model.hasActionableIssue)
    }

    @Test
    fun unavailableActionNeverCallsAnotherDomain() {
        val calls = mutableListOf<String>()
        val controller = controller(
            inspect = { report() },
            recover = { calls += "recover"; report() },
            discardCorrupt = { calls += "corrupt:${it.name}"; discarded(it) },
            discardPending = { calls += "pending:${it.name}"; discarded(it) }
        )

        val result = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.DISCARD_UNRESOLVED_PENDING,
            confirmed = true
        )

        assertEquals(RecoveryUserActionDisposition.NOT_AVAILABLE, result.disposition)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun operationFailureReturnsOnlyExistingSafeModel() {
        val secret = "/data/user/0/private/ghost.frames"
        val controller = controller(
            inspect = {
                report(
                    runState = RecoveryEvidenceState.PENDING,
                    runDetail = secret
                )
            },
            recover = { throw IllegalStateException(secret) },
            discardCorrupt = { throw IllegalStateException(secret) },
            discardPending = { throw IllegalStateException(secret) }
        )

        val result = controller.perform(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            RecoveryUiAction.SAFE_RETRY
        )

        assertEquals(RecoveryUserActionDisposition.ACTION_FAILED, result.disposition)
        assertFalse(result.model.rows.any { it.detail.contains(secret) })
    }

    private fun controller(
        inspect: () -> RecoveryEvidenceReport,
        recover: () -> RecoveryEvidenceReport,
        discardCorrupt: (RecoveryEvidenceDomain) -> RecoveryDiscardResult,
        discardPending: (RecoveryEvidenceDomain) -> RecoveryDiscardResult
    ): RecoveryEvidenceUserController = RecoveryEvidenceUserController(
        inspectEvidence = inspect,
        recoverSafely = recover,
        discardCorrupt = discardCorrupt,
        discardUnresolvedPending = discardPending
    )

    private fun report(
        runState: RecoveryEvidenceState = RecoveryEvidenceState.CLEAN,
        runDetail: String = "no_journal",
        ghostState: RecoveryEvidenceState = RecoveryEvidenceState.CLEAN,
        ghostDetail: String = "no_evidence"
    ): RecoveryEvidenceReport = RecoveryEvidenceReport(
        runOutcome = RecoveryEvidenceSnapshot(
            RecoveryEvidenceDomain.RUN_OUTCOME,
            runState,
            runDetail
        ),
        ghostPromotion = RecoveryEvidenceSnapshot(
            RecoveryEvidenceDomain.GHOST_PROMOTION,
            ghostState,
            ghostDetail
        )
    )

    private fun discarded(domain: RecoveryEvidenceDomain): RecoveryDiscardResult {
        val before = RecoveryEvidenceSnapshot(
            domain,
            RecoveryEvidenceState.CORRUPT,
            "invalid"
        )
        return RecoveryDiscardResult(
            domain = domain,
            disposition = RecoveryDiscardDisposition.DISCARDED,
            before = before,
            after = RecoveryEvidenceSnapshot(
                domain,
                RecoveryEvidenceState.CLEAN,
                "no_evidence"
            )
        )
    }
}
