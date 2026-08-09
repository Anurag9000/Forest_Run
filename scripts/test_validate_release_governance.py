from __future__ import annotations

import copy
import json
import os
import tempfile
import unittest
from pathlib import Path

import compile_human_acceptance
import compile_release_governance as compiler
import test_validate_human_acceptance as human_fixture
import validate_release_governance as governance


VERSION_NAME = "1.0-test"


def _prepare_human(root: Path) -> tuple[dict, Path, Path]:
    human_draft = human_fixture._human_draft(root)
    human_draft_path = root / "human-acceptance-draft.json"
    human_path = root / "human-acceptance.json"
    human_draft_path.write_text(
        json.dumps(human_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_human_acceptance.compile_file(human_draft_path, human_path)
    device_path = root / "device-acceptance.json"
    return human_draft["candidate"], device_path, human_path


def _governance_draft(root: Path) -> dict:
    candidate, _, _ = _prepare_human(root)
    evidence: dict[str, str] = {}
    for kind in sorted(governance.REQUIRED_EVIDENCE_KINDS):
        suffix = ".md" if kind in {"release_notes", "changelog", "third_party_notices"} else ".txt"
        relative = f"governance-evidence/{kind}{suffix}"
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        content = f"Forest Run governance evidence: {kind}\n"
        if kind == "release_notes":
            content += (
                f"version name: {VERSION_NAME}\n"
                f"version code: {candidate['version_code']}\n"
                f"candidate commit: {candidate['commit_sha']}\n"
                f"artifact sha256: {candidate['artifact_sha256']}\n"
                f"upload certificate sha256: {candidate['upload_certificate_sha256']}\n"
                f"app signing certificate sha256: {candidate['app_signing_certificate_sha256']}\n"
            )
        path.write_text(content, encoding="utf-8")
        evidence[kind] = relative

    decisions = {}
    reviewers = ("release-owner", "independent-reviewer")
    for index, domain in enumerate(sorted(governance.DECISION_DOMAINS)):
        decisions[domain] = {
            "status": "approved",
            "reviewer": reviewers[index % 2],
            "reviewed_at_utc": "2026-08-01T14:00:00Z",
            "notes": f"Approved {domain} after reviewing the candidate-bound evidence.",
        }

    return {
        "generated_at_utc": "2026-08-01T15:00:00Z",
        "candidate": {
            **candidate,
            "version_name": VERSION_NAME,
        },
        "device_acceptance": "device-acceptance.json",
        "human_acceptance": "human-acceptance.json",
        "privacy_policy_url": "https://example.org/forest-run/privacy",
        "private_vulnerability_reporting_enabled": True,
        "decisions": decisions,
        "evidence": evidence,
        "final_decision": {
            "status": "approved",
            "release_owner": reviewers[0],
            "independent_reviewer": reviewers[1],
            "reviewed_at_utc": "2026-08-01T14:30:00Z",
            "notes": "Final go/no-go review approved after all domain decisions.",
        },
    }


class ReleaseGovernanceTest(unittest.TestCase):
    def compile_valid(self, root: Path):
        return compiler.compile_bundle(_governance_draft(root), base_dir=root)

    def test_compiler_hashes_all_references_and_validator_accepts_complete_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = self.compile_valid(root)
            self.assertEqual(20, summary.decision_count)
            self.assertEqual(len(governance.REQUIRED_EVIDENCE_KINDS), summary.evidence_file_count)
            self.assertEqual(2, summary.reviewer_count)
            self.assertRegex(compiled["device_acceptance"]["sha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(compiled["human_acceptance"]["sha256"], r"^[0-9a-f]{64}$")
            for reference in compiled["evidence"].values():
                self.assertRegex(reference["sha256"], r"^[0-9a-f]{64}$")

    def test_missing_or_extra_decision_and_evidence_kinds_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _governance_draft(root)
            draft["decisions"].pop(next(iter(governance.DECISION_DOMAINS)))
            with self.assertRaises(compiler.GovernanceCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("decisions is missing", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _governance_draft(root)
            draft["evidence"].pop(next(iter(governance.REQUIRED_EVIDENCE_KINDS)))
            with self.assertRaises(compiler.GovernanceCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("evidence is missing", str(raised.exception))

    def test_unapproved_domain_and_final_decision_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            domain = next(iter(governance.DECISION_DOMAINS))
            mutated = copy.deepcopy(compiled)
            mutated["decisions"][domain]["status"] = "pending"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("status must be approved", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["final_decision"]["status"] = "pending"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("final_decision.status must be approved", str(raised.exception))

    def test_privacy_https_and_private_vulnerability_reporting_are_hard_gates(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            for value in ("http://example.org/privacy", "https://user:pass@example.org/privacy"):
                mutated = copy.deepcopy(compiled)
                mutated["privacy_policy_url"] = value
                with self.assertRaises(governance.GovernanceError) as raised:
                    governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
                self.assertIn("public HTTPS URL", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["private_vulnerability_reporting_enabled"] = False
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must be true", str(raised.exception))

    def test_device_and_human_acceptance_must_match_exact_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["candidate"]["commit_sha"] = "f" * 40
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("device acceptance candidate does not match", str(raised.exception))

    def test_reference_and_governance_evidence_tampering_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            (root / compiled["human_acceptance"]["path"]).write_text("{}\n", encoding="utf-8")
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("human acceptance manifest digest mismatch", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            reference = compiled["evidence"]["asset_provenance"]
            (root / reference["path"]).write_text("tampered\n", encoding="utf-8")
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("governance evidence digest mismatch", str(raised.exception))

    def test_release_notes_must_bind_exact_candidate_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _governance_draft(root)
            release_notes = root / draft["evidence"]["release_notes"]
            release_notes.write_text("Forest Run release notes without candidate identity\n", encoding="utf-8")
            with self.assertRaises(compiler.GovernanceCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("release notes do not contain exact", str(raised.exception))

    def test_final_owner_and_independent_reviewer_must_be_distinct_and_accountable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["final_decision"]["independent_reviewer"] = "Release-Owner"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must be distinct", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["final_decision"]["independent_reviewer"] = "non-decision-reviewer"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must own at least one governance decision", str(raised.exception))

    def test_final_decision_cannot_precede_domain_decisions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["final_decision"]["reviewed_at_utc"] = "2026-08-01T13:30:00Z"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must not precede", str(raised.exception))

    def test_evidence_file_reuse_by_path_or_hardlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["evidence"]["resolved_sbom"] = copy.deepcopy(mutated["evidence"]["asset_provenance"])
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("evidence path is reused", str(raised.exception))

        if hasattr(os, "link"):
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                draft = _governance_draft(root)
                source = root / draft["evidence"]["asset_provenance"]
                target = root / draft["evidence"]["resolved_sbom"]
                target.unlink()
                os.link(source, target)
                with self.assertRaises(compiler.GovernanceCompilationError) as raised:
                    compiler.compile_bundle(draft, base_dir=root)
                self.assertIn("reused through a hard link", str(raised.exception))

    def test_compile_file_publishes_manifest_and_summary_without_mutating_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _governance_draft(root)
            draft_path = root / "release-governance-draft.json"
            output_path = root / "release-governance.json"
            summary_path = root / "release-governance-summary.json"
            draft_path.write_text(json.dumps(draft, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            result = compiler.compile_file(
                draft_path,
                output_path,
                summary_path=summary_path,
            )
            self.assertEqual(draft["candidate"]["commit_sha"], result.candidate_sha)
            self.assertTrue(output_path.is_file())
            self.assertTrue(summary_path.is_file())
            self.assertIsInstance(json.loads(draft_path.read_text())["device_acceptance"], str)
            revalidated = governance.load_and_validate(output_path)
            self.assertEqual(result.manifest_sha256, revalidated.manifest_sha256)


    def test_symlink_component_cannot_alias_governance_evidence(self) -> None:
        if not hasattr(Path, "symlink_to"):
            self.skipTest("symbolic links are unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            real_dir = root / "governance-evidence"
            alias = root / "governance-alias"
            try:
                alias.symlink_to(real_dir, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            reference = compiled["evidence"]["asset_provenance"]
            reference["path"] = "governance-alias/asset_provenance.txt"
            with self.assertRaises(governance.GovernanceError) as raised:
                governance.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )
            self.assertIn("must not traverse a symbolic link", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
