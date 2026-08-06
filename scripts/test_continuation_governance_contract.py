from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class ContinuationGovernanceContractTest(unittest.TestCase):
    def test_stable_evidence_wrapper_remains_the_documented_entrypoint(self) -> None:
        script = (ROOT / "scripts/build_stable_release_evidence_index.py").read_text(
            encoding="utf-8"
        )
        document = (ROOT / "docs/RELEASE_EVIDENCE_INDEX.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("import build_release_evidence_index as builder", script)
        self.assertIn("import verify_release_evidence_index as verifier", script)
        self.assertIn("os.O_NOFOLLOW", script)
        self.assertIn("_confirm_unchanged", script)
        self.assertIn("real_output.unlink(missing_ok=True)", script)
        self.assertIn("build_stable_release_evidence_index.py", document)
        self.assertIn("descriptor-backed snapshot", document)
        self.assertIn("independent reviewer", document)

    def test_declared_inventory_cannot_be_misrepresented_as_an_sbom(self) -> None:
        script = (ROOT / "scripts/build_declared_dependency_inventory.py").read_text(
            encoding="utf-8"
        )
        document = (ROOT / "docs/DEPENDENCY_PROVENANCE.md").read_text(
            encoding="utf-8"
        )
        self.assertIn('"declared-direct-dependencies-only"', script)
        self.assertIn("not a resolved transitive dependency graph", script)
        self.assertIn("not an SBOM", script)
        self.assertIn("resolved transitive", document)
        self.assertIn("vulnerability review", document)
        self.assertIn("Fail-closed release rule", document)

    def test_accessibility_source_and_honest_remaining_boundary_are_documented(self) -> None:
        activity = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
        ).read_text(encoding="utf-8")
        root_delegate = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/ForestRunAccessibilityDelegate.kt"
        ).read_text(encoding="utf-8")
        semantics = (
            ROOT
            / "app/src/main/java/com/anurag9000/forestrun/engine/"
            "GameAccessibilitySemantics.kt"
        ).read_text(encoding="utf-8")
        document = (ROOT / "docs/ACCESSIBILITY.md").read_text(encoding="utf-8")
        self.assertIn("attachForestRunAccessibility", activity)
        self.assertIn("dispatchTouchEvent", root_delegate)
        self.assertIn("AccessibilityNodeIds", semantics)
        self.assertIn("stable IDs", document)
        self.assertIn("not complete TalkBack support", document)
        self.assertIn("Physical acceptance", document)

    def test_security_policy_is_not_claimed_before_private_reporting_exists(self) -> None:
        governance = (
            ROOT / "docs/SECURITY_AND_LICENSING_GOVERNANCE.md"
        ).read_text(encoding="utf-8")
        self.assertFalse((ROOT / "SECURITY.md").exists())
        self.assertIn("must be enabled before", governance)
        self.assertIn("No software or asset licence is selected automatically", governance)
        self.assertIn("Release-blocking rule", governance)

    def test_unreleased_changelog_cannot_claim_an_accepted_candidate(self) -> None:
        changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
        self.assertIn("## Unreleased", changelog)
        self.assertIn("must not be added until the accepted candidate is frozen", changelog)
        self.assertNotIn("## 1.0.0", changelog)


if __name__ == "__main__":
    unittest.main()
