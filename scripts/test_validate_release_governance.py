from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import compile_human_acceptance
import compile_installed_identity_matrix
import compile_play_delivery_evidence
import compile_release_governance as compiler
import stable_file_reader
import test_installed_identity_matrix as matrix_fixture
import test_play_delivery_evidence as play_fixture
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
    matrix_draft, _ = matrix_fixture.prepare(root)
    matrix_draft_path = root / "installed-identity-matrix-draft.json"
    matrix_path = root / "installed-identity-matrix.json"
    matrix_draft_path.write_text(
        json.dumps(matrix_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_installed_identity_matrix.compile_file(matrix_draft_path, matrix_path)

    play_draft = play_fixture.prepare(root)
    play_draft_path = root / "play-delivery-draft.json"
    play_path = root / "play-delivery.json"
    play_draft_path.write_text(
        json.dumps(play_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_play_delivery_evidence.compile_file(play_draft_path, play_path)

    evidence: dict[str, str] = {}
    for kind in sorted(governance.REQUIRED_EVIDENCE_KINDS):
        if kind == "installed_identity_matrix":
            evidence[kind] = "installed-identity-matrix.json"
            continue
        if kind == "play_delivery_record":
            evidence[kind] = "play-delivery.json"
            continue
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
    def test_compile_file_rejects_output_symlink_without_modifying_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = _governance_draft(root)
            draft_path = root / "release-governance-draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            target = root / "unrelated.json"
            target.write_text("preserve me", encoding="utf-8")
            output = root / "release-governance.json"
            output.symlink_to(target)

            with self.assertRaisesRegex(
                compiler.GovernanceCompilationError,
                "output paths must not be symlinks",
            ):
                compiler.compile_file(draft_path, output)

            self.assertEqual("preserve me", target.read_text())
            self.assertTrue(output.is_symlink())

    def test_compile_file_rejects_symlinked_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(_governance_draft(root)), encoding="utf-8")
            alias = root / "draft-alias.json"
            alias.symlink_to(draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.GovernanceCompilationError, "non-symlink"):
                compiler.compile_file(alias, output)
            self.assertFalse(output.exists())

    def compile_valid(self, root: Path):
        return compiler.compile_bundle(_governance_draft(root), base_dir=root)

    def test_source_bytes_must_match_the_manifest_object(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with self.assertRaisesRegex(governance.GovernanceError, "source bytes do not encode"):
                governance.validate_bundle(
                    compiled,
                    source_bytes=b"{}",
                    evidence_base=root,
                )

    def test_compile_bundle_normalizes_non_object_and_non_json_python_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(
                compiler.GovernanceCompilationError,
                "draft must be an object",
            ):
                compiler.compile_bundle(None, base_dir=root)  # type: ignore[arg-type]

            draft = _governance_draft(root)
            draft["unexpected_python_value"] = float("nan")
            with self.assertRaisesRegex(
                compiler.GovernanceCompilationError,
                "draft contains values that are not valid JSON",
            ):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compiler_hashes_all_references_and_validator_accepts_complete_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = self.compile_valid(root)
            self.assertEqual(20, summary.decision_count)
            self.assertEqual(len(governance.REQUIRED_EVIDENCE_KINDS), summary.evidence_file_count)
            self.assertEqual(2, summary.reviewer_count)
            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")
            self.assertRegex(summary.play_delivery_sha256, r"^[0-9a-f]{64}$")
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

    def test_candidate_hashes_reject_uppercase_hex_encodings(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            candidate = dict(compiled["candidate"])
            candidate["artifact_sha256"] = candidate["artifact_sha256"].upper()
            with self.assertRaisesRegex(governance.GovernanceError, "lowercase 64-hex"):
                governance._validate_candidate(candidate)

    def test_candidate_hashes_reject_surrounding_whitespace(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            compiled, _ = self.compile_valid(Path(temporary))
            for key in (
                "commit_sha",
                "artifact_sha256",
                "upload_certificate_sha256",
                "app_signing_certificate_sha256",
            ):
                candidate = dict(compiled["candidate"])
                candidate[key] = f" {candidate[key]} "
                with self.subTest(key=key), self.assertRaises(governance.GovernanceError):
                    governance._validate_candidate(candidate)

    def test_evidence_digest_references_reject_surrounding_whitespace(self) -> None:
        reference = {"path": "unused", "sha256": f" {'a' * 64} "}
        for label, call in (
            (
                "evidence",
                lambda: governance._validate_file_reference(
                    reference, label="evidence", base=Path("."), seen_paths={}
                ),
            ),
            (
                "device acceptance",
                lambda: governance._load_device_reference(
                    reference,
                    base=Path("."),
                    candidate_sha="a" * 40,
                    artifact_sha="a" * 64,
                    upload_certificate_sha="a" * 64,
                    app_signing_certificate_sha="a" * 64,
                    version_code=1,
                ),
            ),
            (
                "human acceptance",
                lambda: governance._load_human_reference(
                    reference,
                    base=Path("."),
                    candidate_sha="a" * 40,
                    artifact_sha="a" * 64,
                    upload_certificate_sha="a" * 64,
                    app_signing_certificate_sha="a" * 64,
                    version_code=1,
                    device_digest="a" * 64,
                ),
            ),
        ):
            with self.subTest(label=label), self.assertRaises(governance.GovernanceError):
                call()

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

    def test_summary_parent_swap_cannot_redirect_atomic_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output_dir = root / "nested"
            external = root / "external"
            external.mkdir()
            saved_output_dir = root / "nested.saved"
            output = output_dir / "summary.json"
            original_open = os.open
            swapped = False

            def swap_before_temp_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if (
                    isinstance(path, str)
                    and path.startswith(".summary.json.")
                    and kwargs.get("dir_fd") is not None
                    and not swapped
                ):
                    output_dir.rename(saved_output_dir)
                    output_dir.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with patch.object(
                stable_file_reader.os, "open", side_effect=swap_before_temp_open
            ):
                with self.assertRaisesRegex(governance.GovernanceError, "symbolic link"):
                    governance._write_json(output, {"status": "valid"})

            self.assertTrue(swapped)
            self.assertFalse((external / "summary.json").exists())
            self.assertFalse((saved_output_dir / "summary.json").exists())

    def test_failed_rollback_retains_governance_backups(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "governance.json"
            second = root / "governance-summary.json"
            first.write_text("old governance", encoding="utf-8")
            second.write_text("old summary", encoding="utf-8")
            with patch("compile_release_governance.atomic_publish_files", side_effect=compiler.StableFileError("injected publication failure")):
                with self.assertRaises(compiler.GovernanceCompilationError) as raised:
                    compiler._publish_transaction(
                        [(first, {"new": 1}), (second, {"new": 2})]
                    )

            self.assertEqual("old governance", first.read_text())
            self.assertEqual("old summary", second.read_text())
            self.assertIn("injected publication failure", str(raised.exception))


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

    def test_symlinked_evidence_root_is_rejected(self) -> None:
        if not hasattr(Path, "symlink_to"):
            self.skipTest("symbolic links are unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            alias = root.parent / f"{root.name}-alias"
            try:
                alias.symlink_to(root, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            try:
                with self.assertRaisesRegex(governance.GovernanceError, "evidence root.*symbolic link"):
                    governance.validate_bundle(
                        compiled,
                        source_bytes=json.dumps(compiled).encode(),
                        evidence_base=alias,
                    )
            finally:
                alias.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()

class ReleaseGovernanceBoundaryTest(unittest.TestCase):
    def compile_valid(self, root: Path):
        return compiler.compile_bundle(_governance_draft(root), base_dir=root)

    def test_top_level_loader_rejects_fifo_symlink_and_oversized_input_before_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            oversized = root / "oversized.json"
            with oversized.open("wb") as handle:
                handle.truncate(governance.MAX_MANIFEST_BYTES + 1)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("unbounded read attempted")):
                with self.assertRaisesRegex(governance.GovernanceError, "invalid size"):
                    governance.load_and_validate(oversized)
            target = root / "target.json"
            target.write_text("{}")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("symlink read attempted")):
                with self.assertRaisesRegex(governance.GovernanceError, "non-symlink"):
                    governance.load_and_validate(alias)
            fifo = root / "governance.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(governance.GovernanceError, "non-symlink"):
                governance.load_and_validate(fifo)

    def test_all_digest_bound_validator_inputs_use_the_same_read_without_reopening(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with (
                patch.object(governance.device_acceptance, "load_and_validate", side_effect=AssertionError("reopened device acceptance")),
                patch.object(governance.human_acceptance, "load_and_validate", side_effect=AssertionError("reopened human acceptance")),
                patch.object(governance.installed_matrix, "load_and_validate", side_effect=AssertionError("reopened identity matrix")),
                patch.object(governance.play_delivery, "load_and_validate", side_effect=AssertionError("reopened Play evidence")),
            ):
                summary = governance.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled, sort_keys=True).encode(),
                    evidence_base=root,
                )
            self.assertEqual(compiled["candidate"]["commit_sha"], summary.candidate_sha)

    def test_release_notes_are_size_bounded_and_checked_against_indexed_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            notes = root / compiled["evidence"]["release_notes"]["path"]
            original = notes.read_bytes()
            real_read = governance._read_regular_bytes
            mutated = False
            def change_release_notes(path, label, maximum):
                nonlocal mutated
                if path == notes and not mutated:
                    mutated = True
                    notes.write_bytes(original + b"changed after hash")
                return real_read(path, label, maximum)
            with patch.object(governance, "_read_regular_bytes", side_effect=change_release_notes):
                with self.assertRaisesRegex(governance.GovernanceError, "release notes changed after evidence hashing"):
                    governance.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            oversized = original + b" " * governance.MAX_RELEASE_NOTES_BYTES
            notes.write_bytes(oversized)
            compiled["evidence"]["release_notes"]["sha256"] = hashlib.sha256(oversized).hexdigest()
            with self.assertRaisesRegex(governance.GovernanceError, "invalid size"):
                governance.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)

    def test_normalized_path_helper_rejects_redundant_segments(self):
        self.assertEqual("evidence/report.txt", governance._safe_relative_path("evidence/report.txt", "path"))
        for value in ("evidence//report.txt", "evidence/./report.txt", "./evidence/report.txt", "evidence/report.txt/"):
            with self.subTest(value=value), self.assertRaisesRegex(governance.GovernanceError, "normalized POSIX"):
                governance._safe_relative_path(value, "path")

    def test_streaming_hasher_uses_nonblocking_nofollow_descriptor_and_rejects_fifo_swap(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = root / "evidence.txt"
            payload.write_bytes(b"evidence")
            seen = []
            real_open = os.open
            def capture_open(path, flags, *args, **kwargs):
                seen.append(flags)
                return real_open(path, flags, *args, **kwargs)
            with patch.object(governance.os, "open", side_effect=capture_open):
                digest, _ = governance._hash_regular_file(payload, "evidence", 100)
            self.assertEqual(hashlib.sha256(b"evidence").hexdigest(), digest)
            self.assertGreaterEqual(len(seen), 2)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(seen[-1] & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(seen[-1] & os.O_NONBLOCK)

            changed = False
            def swap_to_fifo(path, flags, *args, **kwargs):
                nonlocal changed
                if path == payload.name and kwargs.get("dir_fd") is not None and not changed:
                    changed = True
                    payload.unlink()
                    os.mkfifo(payload)
                return real_open(path, flags, *args, **kwargs)
            payload.write_bytes(b"again")
            with patch.object(governance.os, "open", side_effect=swap_to_fifo):
                with self.assertRaisesRegex(governance.GovernanceError, "changed while being opened"):
                    governance._hash_regular_file(payload, "evidence", 100)

    def test_privacy_url_requires_valid_public_https_authority(self):
        self.assertEqual("https://example.org/privacy", governance._validate_https_url("https://example.org/privacy", "privacy"))
        invalid = (
            "http://example.org/privacy",
            "https://user:pass@example.org/privacy",
            "https://example.org:invalid/privacy",
            "https://example.org:70000/privacy",
            "https://[invalid/privacy",
            "https://localhost/privacy",
            "https://service.local/privacy",
            "https://127.0.0.1/privacy",
            "https://10.0.0.4/privacy",
            "https://example.org/privacy#fragment",
        )
        for url in invalid:
            with self.subTest(url=url), self.assertRaises(governance.GovernanceError):
                governance._validate_https_url(url, "privacy")
