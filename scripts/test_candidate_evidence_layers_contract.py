from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
DOCS = ROOT / "docs"


class CandidateEvidenceLayersContractTest(unittest.TestCase):
    def test_permanent_source_surface_exists(self) -> None:
        for relative in (
            "scripts/collect_performance_profiles.sh",
            "scripts/compile_human_acceptance.py",
            "scripts/validate_human_acceptance.py",
            "scripts/test_validate_human_acceptance.py",
            "scripts/compile_release_governance.py",
            "scripts/validate_release_governance.py",
            "scripts/test_validate_release_governance.py",
            "docs/HUMAN_ACCEPTANCE.md",
            "docs/RELEASE_GOVERNANCE_EVIDENCE.md",
        ):
            self.assertTrue((ROOT / relative).is_file(), relative)

    def test_no_temporary_write_migration_survives(self) -> None:
        for relative in (
            "scripts/migrate_candidate_evidence_layers.py",
            "scripts/finalize_candidate_evidence_reconciliation.py",
            ".github/workflows/candidate-evidence-reconciliation.yml",
            ".github/workflows/candidate-evidence-reconciliation-v2.yml",
            ".github/workflows/candidate-evidence-reconciliation-v3.yml",
            ".github/workflows/candidate-evidence-reconciliation-v4.yml",
        ):
            self.assertFalse((ROOT / relative).exists(), relative)

    def test_physical_collector_retains_extended_diagnostics_and_optional_trace(self) -> None:
        source = (SCRIPTS / "collect_performance_profiles.sh").read_text(encoding="utf-8")
        for token in (
            "dumpsys battery",
            "dumpsys thermalservice",
            "dumpsys power",
            "dumpsys cpuinfo",
            "dumpsys audio",
            "dumpsys media.audio_flinger",
            'dumpsys gfxinfo "$APP_ID" framestats',
            'dumpsys meminfo "$APP_ID"',
            "dumpsys procstats --hours 3",
            "FOREST_RUN_CAPTURE_PERFETTO",
            "system-trace.perfetto-trace",
        ):
            self.assertIn(token, source)

    def test_human_contract_is_device_bound_and_detailed(self) -> None:
        source = (SCRIPTS / "validate_human_acceptance.py").read_text(encoding="utf-8")
        for token in (
            "device_acceptance.validate_bundle",
            "talkback_focus_order",
            "semantic_action_reliability",
            "all_entity_telegraphs_hitboxes_outcomes",
            "garden_wardrobe_continuity",
            "ghost_readability",
            "wolf_animation",
            "final_review.reviewers must contain at least two reviewers",
            "must not traverse a symbolic link",
        ):
            self.assertIn(token, source)

    def test_governance_contract_covers_all_external_decision_families(self) -> None:
        source = (SCRIPTS / "validate_release_governance.py").read_text(encoding="utf-8")
        for token in (
            "private_vulnerability_reporting_enabled",
            "software_licensing",
            "creative_asset_licensing",
            "dependency_vulnerability",
            "privacy_policy",
            "data_safety",
            "content_rating",
            "target_audience",
            "store_policy",
            "signed_artifact_provenance",
            "release_notes",
            "independent_reviewer",
            "must not traverse a symbolic link",
        ):
            self.assertIn(token, source)

    def test_canonical_docs_describe_new_layers_and_index_requires_them(self) -> None:
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        performance = (DOCS / "PERFORMANCE.md").read_text(encoding="utf-8")
        device = (DOCS / "DEVICE_ACCEPTANCE.md").read_text(encoding="utf-8")
        index = (DOCS / "RELEASE_EVIDENCE_INDEX.md").read_text(encoding="utf-8")
        security = (DOCS / "SECURITY_AND_LICENSING_GOVERNANCE.md").read_text(encoding="utf-8")

        self.assertIn("docs/HUMAN_ACCEPTANCE.md", readme)
        self.assertIn("docs/RELEASE_GOVERNANCE_EVIDENCE.md", readme)
        self.assertIn("FOREST_RUN_CAPTURE_PERFETTO", performance)
        self.assertIn("HUMAN_ACCEPTANCE.md", device)
        self.assertEqual(2, index.count("--require-bound-kind human_acceptance"))
        self.assertEqual(2, index.count("--require-bound-kind release_governance"))
        self.assertIn("compile_release_governance.py", security)
        self.assertIn("validate_release_governance.py", security)


if __name__ == "__main__":
    unittest.main()
