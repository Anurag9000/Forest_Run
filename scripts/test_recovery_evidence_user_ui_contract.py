from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ACTIVITY = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
).read_text(encoding="utf-8")
DIALOG = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/RecoveryEvidenceDialogCoordinator.kt"
).read_text(encoding="utf-8")
CONTROLLER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RecoveryEvidenceUserController.kt"
).read_text(encoding="utf-8")


class RecoveryEvidenceUserUiContractTest(unittest.TestCase):
    def test_activity_attaches_once_and_dismisses_during_destroy(self) -> None:
        self.assertEqual(
            1,
            ACTIVITY.count(
                "recoveryEvidenceDialog = RecoveryEvidenceDialogCoordinator(this)"
            ),
        )
        self.assertEqual(
            1,
            ACTIVITY.count("gameView.post(recoveryEvidenceDialog::showIfNeeded)"),
        )
        self.assertIn(
            "if (::recoveryEvidenceDialog.isInitialized) "
            "recoveryEvidenceDialog.dismiss()",
            ACTIVITY,
        )

    def test_dialog_uses_application_facade_and_privacy_safe_model(self) -> None:
        self.assertIn("ApplicationPersistenceFacade.android(activity)", DIALOG)
        self.assertIn("RecoveryEvidenceUiRow", DIALOG)
        self.assertNotIn("supportSummary", DIALOG)
        self.assertNotIn("journalPayload", DIALOG)
        self.assertNotIn("ghostFrames", DIALOG)
        self.assertNotIn("printStackTrace", DIALOG)
        self.assertNotIn("exception.message", DIALOG)

    def test_inspection_never_mutates_and_discard_requires_confirmation(self) -> None:
        show_block = DIALOG[DIALOG.index("fun showIfNeeded()") : DIALOG.index("fun dismiss()")]
        self.assertIn("controller::refresh", show_block)
        self.assertNotIn("controller.perform", show_block)

        self.assertIn("confirmDiscard(row.domain, action, row.title)", DIALOG)
        self.assertIn(
            "controller.perform(domain, action, confirmed = true)",
            DIALOG,
        )
        self.assertIn("val RecoveryUiAction.isDestructive", CONTROLLER)
        self.assertIn("CONFIRMATION_REQUIRED", CONTROLLER)

    def test_safe_retry_and_both_destructive_domains_are_explicit(self) -> None:
        for action in (
            "RecoveryUiAction.SAFE_RETRY",
            "RecoveryUiAction.DISCARD_CORRUPT",
            "RecoveryUiAction.DISCARD_UNRESOLVED_PENDING",
        ):
            self.assertIn(action, DIALOG)
        self.assertIn("Your existing data was left unchanged", DIALOG)


if __name__ == "__main__":
    unittest.main()
