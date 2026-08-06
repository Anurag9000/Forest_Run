package com.anurag9000.forestrun.engine

internal enum class RecoveryUiSeverity {
    OK,
    INFO,
    WARNING,
    ERROR
}

internal enum class RecoveryUiAction {
    SAFE_RETRY,
    DISCARD_CORRUPT,
    DISCARD_UNRESOLVED_PENDING
}

internal data class RecoveryEvidenceUiRow(
    val domain: RecoveryEvidenceDomain,
    val title: String,
    val stateLabel: String,
    val detail: String,
    val severity: RecoveryUiSeverity,
    val actions: List<RecoveryUiAction>
)

internal data class RecoveryEvidenceUiModel(
    val rows: List<RecoveryEvidenceUiRow>
) {
    val hasActionableIssue: Boolean
        get() = rows.any { it.actions.isNotEmpty() }
}

/**
 * Converts recovery evidence into stable user-facing copy without exposing run
 * summaries, ghost frames, file paths, hashes, or unknown raw detail strings.
 */
internal object RecoveryEvidencePresentation {
    fun present(report: RecoveryEvidenceReport): RecoveryEvidenceUiModel =
        RecoveryEvidenceUiModel(
            rows = RecoveryEvidenceDomain.entries.map { domain ->
                present(report.forDomain(domain))
            }
        )

    private fun present(snapshot: RecoveryEvidenceSnapshot): RecoveryEvidenceUiRow {
        val statePresentation = when (snapshot.state) {
            RecoveryEvidenceState.CLEAN -> StatePresentation(
                label = "Healthy",
                severity = RecoveryUiSeverity.OK,
                actions = emptyList()
            )
            RecoveryEvidenceState.PENDING -> StatePresentation(
                label = "Recovery pending",
                severity = RecoveryUiSeverity.INFO,
                actions = listOf(
                    RecoveryUiAction.SAFE_RETRY,
                    RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
                )
            )
            RecoveryEvidenceState.BLOCKED -> StatePresentation(
                label = "Recovery blocked",
                severity = RecoveryUiSeverity.WARNING,
                actions = listOf(
                    RecoveryUiAction.SAFE_RETRY,
                    RecoveryUiAction.DISCARD_UNRESOLVED_PENDING
                )
            )
            RecoveryEvidenceState.CORRUPT -> StatePresentation(
                label = "Recovery data damaged",
                severity = RecoveryUiSeverity.ERROR,
                actions = listOf(RecoveryUiAction.DISCARD_CORRUPT)
            )
            RecoveryEvidenceState.IO_FAILURE -> StatePresentation(
                label = "Storage unavailable",
                severity = RecoveryUiSeverity.ERROR,
                actions = listOf(RecoveryUiAction.SAFE_RETRY)
            )
        }
        return RecoveryEvidenceUiRow(
            domain = snapshot.domain,
            title = when (snapshot.domain) {
                RecoveryEvidenceDomain.RUN_OUTCOME -> "Run progress"
                RecoveryEvidenceDomain.GHOST_PROMOTION -> "Best ghost run"
            },
            stateLabel = statePresentation.label,
            detail = safeDetail(snapshot.state, snapshot.detail),
            severity = statePresentation.severity,
            actions = statePresentation.actions
        )
    }

    private fun safeDetail(
        state: RecoveryEvidenceState,
        detail: String
    ): String = when (detail) {
        "no_journal", "no_evidence", "already_applied" ->
            "No recovery action is needed."
        "recovered", "distance_repaired" ->
            "Saved progress was repaired successfully."
        "valid_journal", "valid_receipt" ->
            "Saved progress is waiting for a safe retry."
        "journal_conflict_or_write_failure" ->
            "Saved progress could not be completed safely."
        "invalid_journal", "invalid_receipt", "invalid_manifest",
        "invalid_manifest_or_artifact", "manifest_artifact_mismatch" ->
            "Damaged recovery data cannot be applied safely."
        "journal_read_failed", "journal_recovery_failed",
        "ghost_evidence_read_failed", "ghost_recovery_failed" ->
            "Storage could not be read or updated."
        "unwritten_candidate_abandoned" ->
            "An incomplete ghost update was safely abandoned."
        else -> when (state) {
            RecoveryEvidenceState.CLEAN -> "No recovery action is needed."
            RecoveryEvidenceState.PENDING -> "Saved progress is waiting for a safe retry."
            RecoveryEvidenceState.BLOCKED -> "Saved progress could not be completed safely."
            RecoveryEvidenceState.CORRUPT -> "Damaged recovery data cannot be applied safely."
            RecoveryEvidenceState.IO_FAILURE -> "Storage could not be read or updated."
        }
    }

    private data class StatePresentation(
        val label: String,
        val severity: RecoveryUiSeverity,
        val actions: List<RecoveryUiAction>
    )
}
