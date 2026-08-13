from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
DOCS = ROOT / "docs"


class CandidateEvidenceLayersContractTest(unittest.TestCase):
    def test_permanent_source_surface_exists(self) -> None:
        for relative in (
            "scripts/collect_performance_profiles.sh",
            "scripts/collect_installed_candidate_identity.py",
            "scripts/validate_installed_candidate_identity.py",
            "scripts/test_installed_candidate_identity.py",
            "scripts/compile_installed_identity_matrix.py",
            "scripts/validate_installed_identity_matrix.py",
            "scripts/test_installed_identity_matrix.py",
            "scripts/compile_play_delivery_evidence.py",
            "scripts/validate_play_delivery_evidence.py",
            "scripts/test_play_delivery_evidence.py",
            "scripts/compile_human_acceptance.py",
            "scripts/validate_human_acceptance.py",
            "scripts/test_validate_human_acceptance.py",
            "scripts/compile_release_governance.py",
            "scripts/validate_release_governance.py",
            "scripts/test_validate_release_governance.py",
            "scripts/validate_release_readiness.py",
            "scripts/test_validate_release_readiness.py",
            "docs/INSTALLED_CANDIDATE_IDENTITY.md",
            "docs/HUMAN_ACCEPTANCE.md",
            "docs/RELEASE_GOVERNANCE_EVIDENCE.md",
            "docs/RELEASE_READINESS.md",
        ):
            self.assertTrue((ROOT / relative).is_file(), relative)

    def test_no_temporary_write_migration_survives(self) -> None:
        for relative in (
            "scripts/migrate_candidate_evidence_layers.py",
            "scripts/finalize_candidate_evidence_reconciliation.py",
            "scripts/migrate_device_acceptance_path_integrity.py",
            "scripts/migrate_play_delivery_release_integration.py",
            "scripts/migrate_final_release_evidence_docs.py",
            ".github/workflows/candidate-evidence-reconciliation.yml",
            ".github/workflows/candidate-evidence-reconciliation-v2.yml",
            ".github/workflows/candidate-evidence-reconciliation-v3.yml",
            ".github/workflows/candidate-evidence-reconciliation-v4.yml",
            ".github/workflows/device-acceptance-path-integrity.yml",
            ".github/workflows/play-delivery-release-integration.yml",
            ".github/workflows/final-release-evidence-docs.yml",
        ):
            self.assertFalse((ROOT / relative).exists(), relative)

    def test_physical_acceptance_rejects_weak_json_and_path_aliasing(self) -> None:
        source = (SCRIPTS / "validate_device_acceptance.py").read_text(encoding="utf-8")
        for token in (
            "strict_json.loads(",
            "must not traverse a symbolic link",
            "acceptance manifest must not be a symbolic link",
            "path.lstat()",
            "candidate.artifact_path",
        ):
            self.assertIn(token, source)
        tests = (SCRIPTS / "test_validate_device_acceptance.py").read_text(encoding="utf-8")
        for test_name in (
            "test_load_rejects_duplicate_json_keys",
            "test_manifest_symlink_is_rejected",
            "test_artifact_symlink_component_is_rejected",
            "test_scenario_evidence_symlink_component_is_rejected",
        ):
            self.assertIn(test_name, tests)

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

    def test_installed_identity_and_play_delivery_are_candidate_bound(self) -> None:
        installed = (SCRIPTS / "validate_installed_identity_matrix.py").read_text(
            encoding="utf-8"
        )
        play = (SCRIPTS / "validate_play_delivery_evidence.py").read_text(encoding="utf-8")
        for token in (
            "device_acceptance_sha256",
            "artifact_sha256",
            "upload_certificate_sha256",
            "app_signing_certificate_sha256",
            "candidate_sha",
        ):
            self.assertIn(token, installed)
        for token in (
            "installed_identity_matrix_sha256",
            "artifact_sha256",
            "upload_certificate_sha256",
            "app_signing_certificate_sha256",
            "candidate_sha",
            '"internal"',
        ):
            self.assertIn(token, play)

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
            "installed_matrix.load_and_validate",
            "play_delivery.load_and_validate",
            "installed_identity_matrix",
            "play_delivery_record",
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

    def test_readiness_revalidates_layers_and_binds_the_signed_bundle(self) -> None:
        source = (SCRIPTS / "validate_release_readiness.py").read_text(encoding="utf-8")
        for token in (
            "device_acceptance.load_and_validate",
            "installed_matrix.load_and_validate",
            "play_delivery.load_and_validate",
            "human_acceptance.load_and_validate",
            "governance.load_and_validate",
            "evidence_index.verify_index",
            'kind="device_acceptance"',
            'kind="installed_identity_matrix"',
            'kind="play_delivery"',
            'kind="human_acceptance"',
            'kind="release_governance"',
            'entries["signed_bundle"]',
            "indexed signed_bundle digest does not match the accepted signed artifact",
        ):
            self.assertIn(token, source)

    def test_canonical_docs_describe_all_layers_and_index_requires_them(self) -> None:
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        performance = (DOCS / "PERFORMANCE.md").read_text(encoding="utf-8")
        device = (DOCS / "DEVICE_ACCEPTANCE.md").read_text(encoding="utf-8")
        index = (DOCS / "RELEASE_EVIDENCE_INDEX.md").read_text(encoding="utf-8")
        governance = (DOCS / "RELEASE_GOVERNANCE_EVIDENCE.md").read_text(encoding="utf-8")
        security = (DOCS / "SECURITY_AND_LICENSING_GOVERNANCE.md").read_text(encoding="utf-8")
        readiness_doc = (DOCS / "RELEASE_READINESS.md").read_text(encoding="utf-8")

        self.assertIn("docs/INSTALLED_CANDIDATE_IDENTITY.md", readme)
        self.assertIn("docs/HUMAN_ACCEPTANCE.md", readme)
        self.assertIn("docs/RELEASE_GOVERNANCE_EVIDENCE.md", readme)
        self.assertIn("FOREST_RUN_CAPTURE_PERFETTO", performance)
        self.assertIn("HUMAN_ACCEPTANCE.md", device)
        self.assertEqual(2, index.count("--require-bound-kind installed_identity_matrix"))
        self.assertEqual(2, index.count("--require-bound-kind play_delivery"))
        self.assertEqual(2, index.count("--require-bound-kind human_acceptance"))
        self.assertEqual(2, index.count("--require-bound-kind release_governance"))
        self.assertNotIn("--require-bound-kind policy_approval", index)
        self.assertNotIn("--entry policy_approval=", index)
        self.assertIn("installed-identity-matrix", governance)
        self.assertIn("play-delivery", governance)
        self.assertIn("validate_release_readiness.py", index)
        self.assertIn("compile_release_governance.py", security)
        self.assertIn("validate_release_governance.py", security)
        self.assertIn("--installed-identity-matrix", readiness_doc)
        self.assertIn("--play-delivery", readiness_doc)
        self.assertIn("validate_release_readiness.py", readiness_doc)


if __name__ == "__main__":
    unittest.main()
