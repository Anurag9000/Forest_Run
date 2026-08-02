package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEvidenceMaintenanceCoordinatorTest {

    @Test
    fun `constructor requires exactly one handler per domain`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.CLEAN
        )

        var failed = false
        try {
            RecoveryEvidenceMaintenanceCoordinator(listOf(run))
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }

    @Test
    fun `inspection reports both independent domains without payload data`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.PENDING,
            detail = "valid_journal"
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CORRUPT,
            detail = "invalid_receipt"
        )
        val coordinator = coordinator(run, ghost)

        val report = coordinator.inspect()

        assertEquals(RecoveryEvidenceState.PENDING, report.runOutcome.state)
        assertEquals(RecoveryEvidenceState.CORRUPT, report.ghostPromotion.state)
        assertEquals(
            "run_outcome=PENDING(valid_journal); " +
                "ghost_promotion=CORRUPT(invalid_receipt)",
            report.supportSummary()
        )
        assertFalse(report.supportSummary().contains("score", ignoreCase = true))
        assertFalse(report.supportSummary().contains("frame", ignoreCase = true))
    }

    @Test
    fun `safe recovery skips clean and corrupt evidence`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CORRUPT
        )
        val coordinator = coordinator(run, ghost)

        val report = coordinator.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals(RecoveryEvidenceState.CORRUPT, report.ghostPromotion.state)
        assertEquals(0, run.recoverCalls)
        assertEquals(0, ghost.recoverCalls)
        assertEquals(0, run.clearCalls)
        assertEquals(0, ghost.clearCalls)
    }

    @Test
    fun `safe recovery retries pending and io failed domains independently`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.PENDING,
            recoveredState = RecoveryEvidenceState.CLEAN
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.IO_FAILURE,
            recoveredState = RecoveryEvidenceState.CORRUPT
        )
        val coordinator = coordinator(run, ghost)

        val report = coordinator.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals(RecoveryEvidenceState.CORRUPT, report.ghostPromotion.state)
        assertEquals(1, run.recoverCalls)
        assertEquals(1, ghost.recoverCalls)
        assertEquals(0, run.clearCalls)
        assertEquals(0, ghost.clearCalls)
    }

    @Test
    fun `corrupt discard clears only the selected corrupt domain`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.CORRUPT
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.PENDING
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardCorrupt(RecoveryEvidenceDomain.RUN_OUTCOME)

        assertEquals(RecoveryDiscardDisposition.DISCARDED, result.disposition)
        assertEquals(RecoveryEvidenceState.CORRUPT, result.before.state)
        assertEquals(RecoveryEvidenceState.CLEAN, result.after.state)
        assertEquals(1, run.clearCalls)
        assertEquals(0, ghost.clearCalls)
    }

    @Test
    fun `corrupt discard refuses valid pending evidence`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.PENDING
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardCorrupt(RecoveryEvidenceDomain.RUN_OUTCOME)

        assertEquals(RecoveryDiscardDisposition.NOT_APPLICABLE, result.disposition)
        assertEquals(0, run.clearCalls)
    }

    @Test
    fun `pending discard recovers before erasing when state becomes valid`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.PENDING,
            recoveredState = RecoveryEvidenceState.CLEAN
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardUnresolvedPending(
            RecoveryEvidenceDomain.RUN_OUTCOME
        )

        assertEquals(RecoveryDiscardDisposition.RECOVERED_INSTEAD, result.disposition)
        assertEquals(1, run.recoverCalls)
        assertEquals(0, run.clearCalls)
        assertEquals(RecoveryEvidenceState.CLEAN, result.after.state)
    }

    @Test
    fun `confirmed unresolved pending evidence can be deliberately discarded`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.PENDING,
            recoveredState = RecoveryEvidenceState.BLOCKED
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardUnresolvedPending(
            RecoveryEvidenceDomain.RUN_OUTCOME
        )

        assertEquals(RecoveryDiscardDisposition.DISCARDED, result.disposition)
        assertEquals(1, run.recoverCalls)
        assertEquals(1, run.clearCalls)
        assertEquals(RecoveryEvidenceState.CLEAN, result.after.state)
    }

    @Test
    fun `read failure never authorizes destructive pending discard`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.IO_FAILURE,
            recoveredState = RecoveryEvidenceState.BLOCKED
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardUnresolvedPending(
            RecoveryEvidenceDomain.RUN_OUTCOME
        )

        assertEquals(RecoveryDiscardDisposition.IO_FAILURE, result.disposition)
        assertEquals(0, run.recoverCalls)
        assertEquals(0, run.clearCalls)
    }

    @Test
    fun `clear failure remains visible and evidence stays blocked`() {
        val run = FakeHandler(
            domain = RecoveryEvidenceDomain.RUN_OUTCOME,
            inspectedState = RecoveryEvidenceState.CORRUPT,
            clearSucceeds = false
        )
        val ghost = FakeHandler(
            domain = RecoveryEvidenceDomain.GHOST_PROMOTION,
            inspectedState = RecoveryEvidenceState.CLEAN
        )
        val coordinator = coordinator(run, ghost)

        val result = coordinator.discardCorrupt(RecoveryEvidenceDomain.RUN_OUTCOME)

        assertEquals(RecoveryDiscardDisposition.IO_FAILURE, result.disposition)
        assertEquals(RecoveryEvidenceState.CORRUPT, result.after.state)
        assertEquals(1, run.clearCalls)
    }

    private fun coordinator(
        run: FakeHandler,
        ghost: FakeHandler
    ): RecoveryEvidenceMaintenanceCoordinator =
        RecoveryEvidenceMaintenanceCoordinator(listOf(run, ghost))

    private class FakeHandler(
        override val domain: RecoveryEvidenceDomain,
        inspectedState: RecoveryEvidenceState,
        private val recoveredState: RecoveryEvidenceState = inspectedState,
        private val detail: String = inspectedState.name.lowercase(),
        private val clearSucceeds: Boolean = true
    ) : RecoveryEvidenceHandler {
        private var state = inspectedState
        var recoverCalls = 0
        var clearCalls = 0

        override fun inspect(): RecoveryEvidenceSnapshot = RecoveryEvidenceSnapshot(
            domain = domain,
            state = state,
            detail = detail
        )

        override fun recoverSafely(): RecoveryEvidenceSnapshot {
            recoverCalls++
            state = recoveredState
            return RecoveryEvidenceSnapshot(
                domain = domain,
                state = state,
                detail = "recovered_${state.name.lowercase()}"
            )
        }

        override fun clearEvidence(): Boolean {
            clearCalls++
            if (clearSucceeds) state = RecoveryEvidenceState.CLEAN
            return clearSucceeds
        }
    }
}
