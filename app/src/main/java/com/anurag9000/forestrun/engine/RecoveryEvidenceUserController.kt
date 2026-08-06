package com.anurag9000.forestrun.engine

internal enum class RecoveryUserActionDisposition {
    REFRESHED,
    COMPLETED,
    CONFIRMATION_REQUIRED,
    NOT_AVAILABLE,
    ACTION_FAILED
}

internal data class RecoveryUserActionResult(
    val disposition: RecoveryUserActionDisposition,
    val model: RecoveryEvidenceUiModel,
    val domain: RecoveryEvidenceDomain? = null,
    val action: RecoveryUiAction? = null
)

/**
 * Privacy-safe user action boundary for recovery maintenance.
 *
 * Destructive actions require explicit confirmation and are offered only when
 * the current evidence state permits that exact operation. The controller never
 * exposes recovery payloads; it returns only [RecoveryEvidenceUiModel].
 */
internal class RecoveryEvidenceUserController(
    private val inspectEvidence: () -> RecoveryEvidenceReport,
    private val recoverSafely: () -> RecoveryEvidenceReport,
    private val discardCorrupt: (RecoveryEvidenceDomain) -> RecoveryDiscardResult,
    private val discardUnresolvedPending: (RecoveryEvidenceDomain) -> RecoveryDiscardResult
) {
    fun refresh(): RecoveryUserActionResult {
        val model = RecoveryEvidencePresentation.present(inspectEvidence())
        return RecoveryUserActionResult(
            disposition = RecoveryUserActionDisposition.REFRESHED,
            model = model
        )
    }

    fun perform(
        domain: RecoveryEvidenceDomain,
        action: RecoveryUiAction,
        confirmed: Boolean = false
    ): RecoveryUserActionResult {
        val current = RecoveryEvidencePresentation.present(inspectEvidence())
        val row = current.rows.first { it.domain == domain }
        if (action !in row.actions) {
            return RecoveryUserActionResult(
                disposition = RecoveryUserActionDisposition.NOT_AVAILABLE,
                model = current,
                domain = domain,
                action = action
            )
        }
        if (action.isDestructive && !confirmed) {
            return RecoveryUserActionResult(
                disposition = RecoveryUserActionDisposition.CONFIRMATION_REQUIRED,
                model = current,
                domain = domain,
                action = action
            )
        }

        return try {
            val refreshed = when (action) {
                RecoveryUiAction.SAFE_RETRY -> recoverSafely()
                RecoveryUiAction.DISCARD_CORRUPT -> {
                    discardCorrupt(domain)
                    inspectEvidence()
                }
                RecoveryUiAction.DISCARD_UNRESOLVED_PENDING -> {
                    discardUnresolvedPending(domain)
                    inspectEvidence()
                }
            }
            RecoveryUserActionResult(
                disposition = RecoveryUserActionDisposition.COMPLETED,
                model = RecoveryEvidencePresentation.present(refreshed),
                domain = domain,
                action = action
            )
        } catch (_: RuntimeException) {
            RecoveryUserActionResult(
                disposition = RecoveryUserActionDisposition.ACTION_FAILED,
                model = current,
                domain = domain,
                action = action
            )
        }
    }

    companion object {
        fun from(facade: ApplicationPersistenceFacade): RecoveryEvidenceUserController =
            RecoveryEvidenceUserController(
                inspectEvidence = facade::inspectRecoveryEvidence,
                recoverSafely = facade::recoverSafely,
                discardCorrupt = facade::discardCorruptRecoveryEvidence,
                discardUnresolvedPending =
                    facade::discardUnresolvedPendingRecoveryEvidence
            )
    }

    private val RecoveryUiAction.isDestructive: Boolean
        get() = this == RecoveryUiAction.DISCARD_CORRUPT ||
            this == RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
}
