package com.anurag9000.forestrun

import androidx.appcompat.app.AlertDialog
import android.os.Looper
import com.anurag9000.forestrun.engine.RecoveryDiscardDisposition
import com.anurag9000.forestrun.engine.RecoveryDiscardResult
import com.anurag9000.forestrun.engine.RecoveryEvidenceDomain
import com.anurag9000.forestrun.engine.RecoveryEvidenceReport
import com.anurag9000.forestrun.engine.RecoveryEvidenceSnapshot
import com.anurag9000.forestrun.engine.RecoveryEvidenceState
import com.anurag9000.forestrun.engine.RecoveryEvidenceUserController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RecoveryEvidenceDialogCoordinatorTest {
    @Test
    fun `safe retry action is dispatched once and dialog closes after recovery`() {
        val calls = mutableListOf<String>()
        var current = report(runState = RecoveryEvidenceState.PENDING)
        val controller = controller(
            inspect = { current },
            recover = {
                calls += "retry"
                current = report()
                current
            }
        )
        val (activity, coordinator) = coordinator(controller)

        coordinator.showIfNeeded()
        val dialog = activeDialog(coordinator)
        assertNotNull(dialog)
        assertTrue(
            dialog!!.findViewById<android.widget.TextView>(android.R.id.message)
                ?.text.toString().contains("Recovery pending")
        )
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString().contains("Retry"))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("retry"), calls)
        assertNull(activeDialog(coordinator))
        activity.finish()
    }

    @Test
    fun `discard action requires a second dialog confirmation before storage mutation`() {
        val calls = mutableListOf<String>()
        var current = report(runState = RecoveryEvidenceState.CORRUPT)
        val controller = controller(
            inspect = { current },
            discardCorrupt = { domain ->
                calls += "discard:${domain.name}"
                current = report()
                discarded(domain)
            }
        )
        val (activity, coordinator) = coordinator(controller)

        coordinator.showIfNeeded()
        val issueDialog = activeDialog(coordinator)
        assertNotNull(issueDialog)
        assertEquals("Discard damaged data", issueDialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).text)

        issueDialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val confirmation = activeDialog(coordinator)
        assertNotNull(confirmation)
        assertNotSame(issueDialog, confirmation)
        assertEquals(emptyList<String>(), calls)
        assertTrue(confirmation!!.getButton(AlertDialog.BUTTON_POSITIVE).text.toString().contains("Discard"))

        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("discard:RUN_OUTCOME"), calls)
        assertNull(activeDialog(coordinator))
        activity.finish()
    }

    @Test
    fun `dialog omits raw evidence details and dismiss resets active ownership`() {
        val secret = "/data/user/0/private/ghost.frames"
        val controller = controller(
            inspect = { report(ghostState = RecoveryEvidenceState.PENDING, ghostDetail = secret) }
        )
        val (activity, coordinator) = coordinator(controller)

        coordinator.showIfNeeded()
        val dialog = activeDialog(coordinator)
        assertNotNull(dialog)
        val message = dialog!!.findViewById<android.widget.TextView>(android.R.id.message)?.text.toString()
        assertFalse(message.contains(secret))

        coordinator.dismiss()

        assertNull(activeDialog(coordinator))
        activity.finish()
    }

    @Test
    fun `clean evidence does not open a dialog`() {
        val (activity, coordinator) = coordinator(controller(inspect = { report() }))

        coordinator.showIfNeeded()

        assertNull(activeDialog(coordinator))
        activity.finish()
    }

    private fun coordinator(
        controller: RecoveryEvidenceUserController
    ): Pair<MainActivity, RecoveryEvidenceDialogCoordinator> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        return activity to RecoveryEvidenceDialogCoordinator(activity, controller)
    }

    private fun activeDialog(coordinator: RecoveryEvidenceDialogCoordinator): AlertDialog? {
        val field = RecoveryEvidenceDialogCoordinator::class.java.getDeclaredField("activeDialog")
        field.isAccessible = true
        return field.get(coordinator) as AlertDialog?
    }

    private fun controller(
        inspect: () -> RecoveryEvidenceReport,
        recover: () -> RecoveryEvidenceReport = { report() },
        discardCorrupt: (RecoveryEvidenceDomain) -> RecoveryDiscardResult = { discarded(it) }
    ) = RecoveryEvidenceUserController(
        inspectEvidence = inspect,
        recoverSafely = recover,
        discardCorrupt = discardCorrupt,
        discardUnresolvedPending = { discarded(it) }
    )

    private fun report(
        runState: RecoveryEvidenceState = RecoveryEvidenceState.CLEAN,
        runDetail: String = "no_journal",
        ghostState: RecoveryEvidenceState = RecoveryEvidenceState.CLEAN,
        ghostDetail: String = "no_evidence"
    ) = RecoveryEvidenceReport(
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

    private fun discarded(domain: RecoveryEvidenceDomain) = RecoveryDiscardResult(
        domain = domain,
        disposition = RecoveryDiscardDisposition.DISCARDED,
        before = RecoveryEvidenceSnapshot(domain, RecoveryEvidenceState.CORRUPT, "invalid"),
        after = RecoveryEvidenceSnapshot(domain, RecoveryEvidenceState.CLEAN, "no_evidence")
    )
}
