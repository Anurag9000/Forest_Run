package com.anurag9000.forestrun

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.anurag9000.forestrun.engine.ApplicationPersistenceFacade
import com.anurag9000.forestrun.engine.RecoveryEvidenceDomain
import com.anurag9000.forestrun.engine.RecoveryEvidenceUiRow
import com.anurag9000.forestrun.engine.RecoveryEvidenceUserController
import com.anurag9000.forestrun.engine.RecoveryUiAction
import com.anurag9000.forestrun.engine.RecoveryUserActionDisposition
import com.anurag9000.forestrun.engine.RecoveryUserActionResult

/**
 * User-facing recovery entry point for actionable persistence evidence.
 *
 * The dialog consumes only the privacy-safe presentation model. It never renders
 * journal payloads, ghost frames, paths, hashes, or exception text. Destructive
 * maintenance always requires a second explicit confirmation.
 */
internal class RecoveryEvidenceDialogCoordinator(
    private val activity: AppCompatActivity,
    private val controller: RecoveryEvidenceUserController =
        RecoveryEvidenceUserController.from(
            ApplicationPersistenceFacade.android(activity)
        )
) {
    private var activeDialog: AlertDialog? = null

    fun showIfNeeded() {
        if (activity.isFinishing || activity.isDestroyed || activeDialog != null) return
        val refreshed = runCatching(controller::refresh).getOrNull() ?: return
        val row = refreshed.model.rows.firstOrNull { it.actions.isNotEmpty() } ?: return
        showRow(row)
    }

    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun showRow(row: RecoveryEvidenceUiRow) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(row.title)
            .setMessage("${row.stateLabel}\n\n${row.detail}")
            .setNegativeButton("Not now") { _, _ -> activeDialog = null }
            .setOnCancelListener { activeDialog = null }

        if (RecoveryUiAction.SAFE_RETRY in row.actions) {
            builder.setPositiveButton("Retry safely") { _, _ ->
                activeDialog = null
                handleResult(
                    controller.perform(row.domain, RecoveryUiAction.SAFE_RETRY)
                )
            }
        }
        destructiveAction(row)?.let { action ->
            builder.setNeutralButton("Discard damaged data") { _, _ ->
                activeDialog = null
                confirmDiscard(row.domain, action, row.title)
            }
        }
        activeDialog = builder.create().also(AlertDialog::show)
    }

    private fun confirmDiscard(
        domain: RecoveryEvidenceDomain,
        action: RecoveryUiAction,
        title: String
    ) {
        if (activity.isFinishing || activity.isDestroyed || activeDialog != null) return
        activeDialog = AlertDialog.Builder(activity)
            .setTitle("Discard $title recovery data?")
            .setMessage(
                "Only the unresolved or damaged recovery evidence for this item " +
                    "will be removed. This cannot be undone."
            )
            .setNegativeButton("Keep data") { _, _ -> activeDialog = null }
            .setPositiveButton("Discard") { _, _ ->
                activeDialog = null
                handleResult(controller.perform(domain, action, confirmed = true))
            }
            .setOnCancelListener { activeDialog = null }
            .create()
            .also(AlertDialog::show)
    }

    private fun handleResult(result: RecoveryUserActionResult) {
        when (result.disposition) {
            RecoveryUserActionDisposition.COMPLETED,
            RecoveryUserActionDisposition.REFRESHED -> {
                val next = result.model.rows.firstOrNull { it.actions.isNotEmpty() }
                if (next != null) showRow(next)
            }
            RecoveryUserActionDisposition.CONFIRMATION_REQUIRED -> {
                val domain = result.domain ?: return
                val action = result.action ?: return
                val row = result.model.rows.firstOrNull { it.domain == domain } ?: return
                confirmDiscard(domain, action, row.title)
            }
            RecoveryUserActionDisposition.NOT_AVAILABLE,
            RecoveryUserActionDisposition.ACTION_FAILED -> showFailure()
        }
    }

    private fun showFailure() {
        if (activity.isFinishing || activity.isDestroyed || activeDialog != null) return
        activeDialog = AlertDialog.Builder(activity)
            .setTitle("Recovery could not finish")
            .setMessage(
                "Your existing data was left unchanged. You can retry after " +
                    "restarting the app or checking available storage."
            )
            .setPositiveButton("OK") { _, _ -> activeDialog = null }
            .setOnCancelListener { activeDialog = null }
            .create()
            .also(AlertDialog::show)
    }

    private fun destructiveAction(row: RecoveryEvidenceUiRow): RecoveryUiAction? =
        when {
            RecoveryUiAction.DISCARD_CORRUPT in row.actions ->
                RecoveryUiAction.DISCARD_CORRUPT
            RecoveryUiAction.DISCARD_UNRESOLVED_PENDING in row.actions ->
                RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
            else -> null
        }
}
