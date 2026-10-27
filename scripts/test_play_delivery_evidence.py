from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

import compile_installed_identity_matrix
import compile_play_delivery_evidence as compiler
import test_installed_identity_matrix as matrix_fixture
import validate_play_delivery_evidence as delivery


def prepare(root: Path) -> dict:
    matrix_draft, _ = matrix_fixture.prepare(root)
    matrix_draft_path = root / "installed-identity-matrix-draft.json"
    matrix_path = root / "installed-identity-matrix.json"
    matrix_draft_path.write_text(
        json.dumps(matrix_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_installed_identity_matrix.compile_file(matrix_draft_path, matrix_path)

    evidence: dict[str, str] = {}
    for kind in sorted(delivery.REQUIRED_EVIDENCE_KINDS):
        relative = f"play-delivery/{kind}.txt"
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"candidate-bound external Play evidence for {kind}\n", encoding="utf-8")
        evidence[kind] = relative

    return {
        "generated_at_utc": "2026-08-01T14:00:00Z",
        "installed_identity_matrix": "installed-identity-matrix.json",
        "delivery": {
            "store": "google_play",
            "track": "internal",
            "release_id": "forest-run-internal-7",
            "bundle_uploaded": True,
            "internal_release_created": True,
            "tester_eligible": True,
            "tester_install_completed": True,
            "update_path_verified": True,
            "uploaded_at_utc": "2026-08-01T09:00:00Z",
            "release_created_at_utc": "2026-08-01T09:10:00Z",
            "install_verified_at_utc": "2026-08-01T10:30:00Z",
            "update_verified_at_utc": "2026-08-01T12:30:00Z",
        },
        "evidence": evidence,
        "final_review": {
            "status": "approved",
            "release_operator": "release-owner",
            "independent_reviewer": "independent-reviewer",
            "reviewed_at_utc": "2026-08-01T13:00:00Z",
            "notes": "Reviewed the external Play Console and tester delivery evidence.",
        },
    }


class PlayDeliveryEvidenceTest(unittest.TestCase):
    def test_compile_file_rejects_output_symlink_without_modifying_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft_path = root / "play-delivery-draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            target = root / "unrelated.json"
            target.write_text("preserve me", encoding="utf-8")
            output = root / "play-delivery.json"
            output.symlink_to(target)

            with self.assertRaisesRegex(
                compiler.PlayDeliveryCompilationError,
                "output paths must not be symlinks",
            ):
                compiler.compile_file(draft_path, output)

            self.assertEqual("preserve me", target.read_text())
            self.assertTrue(output.is_symlink())

    def test_compile_file_rejects_symlinked_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(prepare(root)), encoding="utf-8")
            alias = root / "draft-alias.json"
            alias.symlink_to(draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.PlayDeliveryCompilationError, "non-symlink"):
                compiler.compile_file(alias, output)
            self.assertFalse(output.exists())

    def compile_valid(self, root: Path):
        return compiler.compile_bundle(prepare(root), base_dir=root)

    def test_referenced_matrix_mutation_between_validation_and_hash_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            matrix_path = root / draft["installed_identity_matrix"]
            original_load = compiler.installed_matrix.load_and_validate
            mutated = False

            def load_then_mutate(path):
                nonlocal mutated
                result = original_load(path)
                if not mutated:
                    mutated = True
                    matrix_path.write_text("{}\n", encoding="utf-8")
                return result

            with patch.object(
                compiler.installed_matrix,
                "load_and_validate",
                side_effect=load_then_mutate,
            ):
                with self.assertRaises(compiler.PlayDeliveryCompilationError):
                    compiler.compile_bundle(draft, base_dir=root)
            self.assertTrue(mutated)

    def test_compile_bundle_rejects_malformed_draft_paths_and_evidence_sets(self) -> None:
        cases = (
            ("matrix type", lambda draft: draft.__setitem__("installed_identity_matrix", None), "relative path string"),
            ("matrix traversal", lambda draft: draft.__setitem__("installed_identity_matrix", "../matrix.json"), "safe relative path"),
            ("missing matrix", lambda draft: draft.__setitem__("installed_identity_matrix", "missing.json"), "missing"),
            ("evidence type", lambda draft: draft.__setitem__("evidence", []), "evidence must be an object"),
            ("missing evidence kind", lambda draft: draft["evidence"].pop(sorted(delivery.REQUIRED_EVIDENCE_KINDS)[0]), "evidence is missing"),
            ("extra evidence kind", lambda draft: draft["evidence"].__setitem__("unexpected", "extra.txt"), "unrecognized kinds"),
            ("evidence path type", lambda draft: draft["evidence"].__setitem__(sorted(delivery.REQUIRED_EVIDENCE_KINDS)[0], None), "relative path string"),
            ("evidence traversal", lambda draft: draft["evidence"].__setitem__(sorted(delivery.REQUIRED_EVIDENCE_KINDS)[0], "../outside.txt"), "safe relative path"),
            ("missing evidence file", lambda draft: draft["evidence"].__setitem__(sorted(delivery.REQUIRED_EVIDENCE_KINDS)[0], "missing.txt"), "missing"),
            ("invalid timestamp", lambda draft: draft.__setitem__("generated_at_utc", "tomorrow"), "generated_at_utc"),
        )
        for label, mutate, expected in cases:
            with self.subTest(case=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                draft = prepare(root)
                mutate(draft)
                with self.assertRaisesRegex(compiler.PlayDeliveryCompilationError, expected):
                    compiler.compile_bundle(draft, base_dir=root)

    def test_compile_file_rejects_draft_and_summary_output_aliases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            with self.assertRaisesRegex(compiler.PlayDeliveryCompilationError, "must not overwrite the draft"):
                compiler.compile_file(draft_path, draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.PlayDeliveryCompilationError, "distinct from draft/output"):
                compiler.compile_file(draft_path, output, summary_path=output)
            self.assertFalse(output.exists())

    def test_compiler_cli_round_trip_and_invalid_input_result(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(prepare(root)), encoding="utf-8")
            output = root / "play-delivery.json"
            stdout = StringIO()
            with redirect_stdout(stdout):
                self.assertEqual(0, compiler.main([str(draft_path), str(output)]))
            self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
            self.assertTrue(output.is_file())

            bad = root / "bad.json"
            bad.write_text("{", encoding="utf-8")
            failure = StringIO()
            with redirect_stdout(failure):
                self.assertEqual(1, compiler.main([str(bad), str(root / "bad-output.json")]))
            self.assertEqual("invalid", json.loads(failure.getvalue())["status"])

    def test_complete_internal_delivery_record_binds_matrix_and_external_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = self.compile_valid(root)
            self.assertEqual("internal", summary.track)
            self.assertEqual("forest-run-internal-7", summary.release_id)
            self.assertEqual(5, summary.evidence_file_count)
            self.assertEqual(2, summary.reviewer_count)
            self.assertEqual(7, compiled["candidate"]["version_code"])
            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")

    def test_source_bytes_must_encode_the_validated_manifest_object(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with self.assertRaisesRegex(
                delivery.PlayDeliveryError,
                "source bytes do not encode the supplied object",
            ):
                delivery.validate_bundle(
                    compiled,
                    source_bytes=b"{}\n",
                    evidence_base=root,
                )

    def test_compile_bundle_rejects_non_object_and_non_json_python_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(
                compiler.PlayDeliveryCompilationError,
                "draft must be an object",
            ):
                compiler.compile_bundle(None, base_dir=root)  # type: ignore[arg-type]

            draft = prepare(root)
            draft["unexpected_python_value"] = float("nan")
            with self.assertRaisesRegex(
                compiler.PlayDeliveryCompilationError,
                "draft contains values that are not valid JSON",
            ):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compiler_generates_timestamp_and_normalizes_copy_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft.pop("generated_at_utc")
            compiled, _ = compiler.compile_bundle(draft, base_dir=root)
            self.assertRegex(compiled["generated_at_utc"], r"^\d{4}-\d\d-\d\dT.*Z$")

            class BrokenCopy:
                def __deepcopy__(self, memo):
                    raise ValueError("copy refused")

            with self.assertRaisesRegex(compiler.PlayDeliveryCompilationError, "cannot be copied safely"):
                compiler.compile_bundle({"bad": BrokenCopy()}, base_dir=root)

    def test_track_and_every_delivery_assertion_are_hard_gates(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["delivery"]["track"] = "production"
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must be internal", str(raised.exception))

            for key in (
                "bundle_uploaded",
                "internal_release_created",
                "tester_eligible",
                "tester_install_completed",
                "update_path_verified",
            ):
                with self.subTest(key=key):
                    mutated = copy.deepcopy(compiled)
                    mutated["delivery"][key] = False
                    with self.assertRaises(delivery.PlayDeliveryError) as raised:
                        delivery.validate_bundle(
                            mutated,
                            source_bytes=json.dumps(mutated).encode(),
                            evidence_base=root,
                        )
                    self.assertIn(f"delivery.{key} must be true", str(raised.exception))

    def test_candidate_version_and_matrix_identity_cannot_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["candidate"]["version_code"] += 1
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("version_code does not match installed identity matrix", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["candidate"]["artifact_sha256"] = "f" * 64
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("artifact_sha256 does not match installed identity matrix", str(raised.exception))

    def test_candidate_and_evidence_hash_fields_reject_surrounding_whitespace(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            original, _ = self.compile_valid(root)
            mutations = (
                ("candidate.commit_sha", lambda data: data["candidate"].__setitem__(
                    "commit_sha", data["candidate"]["commit_sha"] + " "
                )),
                ("candidate.artifact_sha256", lambda data: data["candidate"].__setitem__(
                    "artifact_sha256", data["candidate"]["artifact_sha256"] + " "
                )),
                ("candidate.upload_certificate_sha256", lambda data: data["candidate"].__setitem__(
                    "upload_certificate_sha256", data["candidate"]["upload_certificate_sha256"] + " "
                )),
                ("candidate.app_signing_certificate_sha256", lambda data: data["candidate"].__setitem__(
                    "app_signing_certificate_sha256", data["candidate"]["app_signing_certificate_sha256"] + " "
                )),
                ("installed_identity_matrix.sha256", lambda data: data["installed_identity_matrix"].__setitem__(
                    "sha256", data["installed_identity_matrix"]["sha256"] + " "
                )),
                ("evidence.sha256", lambda data: data["evidence"][next(iter(data["evidence"]))].__setitem__(
                    "sha256", data["evidence"][next(iter(data["evidence"]))]["sha256"] + " "
                )),
            )
            for label, mutate in mutations:
                with self.subTest(field=label):
                    data = copy.deepcopy(original)
                    mutate(data)
                    with self.assertRaisesRegex(delivery.PlayDeliveryError, "lowercase .*hex"):
                        delivery.validate_bundle(
                            data,
                            source_bytes=json.dumps(data, sort_keys=True).encode(),
                            evidence_base=root,
                        )

    def test_delivery_timestamps_are_monotonic_and_review_follows_update(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["delivery"]["install_verified_at_utc"] = "2026-08-01T08:00:00Z"
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("timestamps must be monotonic", str(raised.exception))

            mutated = copy.deepcopy(compiled)
            mutated["final_review"]["reviewed_at_utc"] = "2026-08-01T11:00:00Z"
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("must not precede update verification", str(raised.exception))

    def test_external_evidence_is_exact_complete_unique_and_immutable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft["evidence"].pop("update_receipt_record")
            with self.assertRaises(compiler.PlayDeliveryCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("evidence is missing", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            reference = compiled["evidence"]["install_receipt_record"]
            (root / reference["path"]).write_text("tampered\n", encoding="utf-8")
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("digest mismatch", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            mutated = copy.deepcopy(compiled)
            mutated["evidence"]["update_receipt_record"] = copy.deepcopy(
                mutated["evidence"]["install_receipt_record"]
            )
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(mutated, source_bytes=json.dumps(mutated).encode(), evidence_base=root)
            self.assertIn("path is reused", str(raised.exception))

    def test_external_evidence_cannot_reuse_matrix_path_or_inode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            matrix_path = root / "installed-identity-matrix.json"
            matrix_digest = hashlib.sha256(matrix_path.read_bytes()).hexdigest()
            mutated = copy.deepcopy(compiled)
            mutated["evidence"]["play_console_upload_record"] = {
                "path": matrix_path.name,
                "sha256": matrix_digest,
            }
            with self.assertRaisesRegex(
                delivery.PlayDeliveryError,
                "must not reuse the installed identity matrix",
            ):
                delivery.validate_bundle(
                    mutated,
                    source_bytes=json.dumps(mutated).encode(),
                    evidence_base=root,
                )

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            matrix_path = root / "installed-identity-matrix.json"
            alias = root / "play-delivery/matrix-alias.json"
            try:
                os.link(matrix_path, alias)
            except OSError as exc:
                self.skipTest(f"hard links unavailable: {exc}")
            mutated = copy.deepcopy(compiled)
            mutated["evidence"]["play_console_upload_record"] = {
                "path": alias.relative_to(root).as_posix(),
                "sha256": hashlib.sha256(alias.read_bytes()).hexdigest(),
            }
            with self.assertRaisesRegex(
                delivery.PlayDeliveryError,
                "must not reuse the installed identity matrix",
            ):
                delivery.validate_bundle(
                    mutated,
                    source_bytes=json.dumps(mutated).encode(),
                    evidence_base=root,
                )

    def test_symlink_component_and_reviewer_aliasing_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            real = root / "play-delivery"
            alias = root / "play-alias"
            try:
                alias.symlink_to(real, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            reference = compiled["evidence"]["install_receipt_record"]
            reference["path"] = f"play-alias/{Path(reference['path']).name}"
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("must not traverse a symbolic link", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            compiled["final_review"]["independent_reviewer"] = "Release-Owner"
            with self.assertRaises(delivery.PlayDeliveryError) as raised:
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
            self.assertIn("must be distinct", str(raised.exception))

    def test_compile_file_preserves_draft_and_revalidates_final_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft_path = root / "play-delivery-draft.json"
            final_path = root / "play-delivery.json"
            summary_path = root / "play-delivery-summary.json"
            draft_path.write_text(json.dumps(draft, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            result = compiler.compile_file(draft_path, final_path, summary_path=summary_path)
            self.assertEqual("internal", result.track)
            self.assertTrue(final_path.is_file())
            self.assertTrue(summary_path.is_file())
            self.assertIsInstance(json.loads(draft_path.read_text())["installed_identity_matrix"], str)
            revalidated = delivery.load_and_validate(final_path)
            self.assertEqual(result.manifest_sha256, revalidated.manifest_sha256)

    def test_publish_failure_restores_existing_manifest_and_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft_path = root / "play-delivery-draft.json"
            output_path = root / "play-delivery.json"
            summary_path = root / "play-delivery-summary.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            output_path.write_text("previous manifest", encoding="utf-8")
            summary_path.write_text("previous summary", encoding="utf-8")
            with patch("compile_play_delivery_evidence.atomic_publish_files", side_effect=compiler.StableFileError("injected second publication failure")):
                with self.assertRaisesRegex(
                    compiler.PlayDeliveryCompilationError,
                    "could not publish Play delivery outputs",
                ):
                    compiler.compile_file(draft_path, output_path, summary_path=summary_path)

            self.assertEqual("previous manifest", output_path.read_text())
            self.assertEqual("previous summary", summary_path.read_text())
            self.assertEqual([], list(root.glob(".play-delivery-*")))

    def test_failed_rollback_retains_transaction_backup_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft = prepare(root)
            draft_path = root / "play-delivery-draft.json"
            output_path = root / "play-delivery.json"
            summary_path = root / "play-delivery-summary.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            output_path.write_text("previous manifest", encoding="utf-8")
            summary_path.write_text("previous summary", encoding="utf-8")
            with patch(
                "compile_play_delivery_evidence.atomic_publish_files",
                side_effect=compiler.StableFileError("injected publication failure"),
            ):
                with self.assertRaises(compiler.PlayDeliveryCompilationError) as raised:
                    compiler.compile_file(draft_path, output_path, summary_path=summary_path)

            message = str(raised.exception)
            self.assertIn("injected publication failure", message)
            self.assertEqual("previous manifest", output_path.read_text())
            self.assertEqual("previous summary", summary_path.read_text())


if __name__ == "__main__":
    unittest.main()

class PlayDeliveryReaderHardeningTest(unittest.TestCase):
    def test_loader_rejects_fifo_symlink_and_oversize_before_payload_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            oversized = root / "large.json"
            with oversized.open("wb") as handle:
                handle.truncate(delivery.MAX_MANIFEST_BYTES + 1)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("unbounded read attempted")):
                with self.assertRaisesRegex(delivery.PlayDeliveryError, "invalid size"):
                    delivery.load_and_validate(oversized)
            target = root / "target.json"
            target.write_text("{}")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with self.assertRaisesRegex(delivery.PlayDeliveryError, "non-symlink"):
                delivery.load_and_validate(alias)
            fifo = root / "delivery.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(delivery.PlayDeliveryError, "non-symlink"):
                delivery.load_and_validate(fifo)

    def test_matrix_reference_validates_the_hashed_bytes_without_reopening(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = PlayDeliveryEvidenceTest().compile_valid(root)
            with patch.object(
                delivery.installed_matrix,
                "load_and_validate",
                side_effect=AssertionError("reopened matrix file"),
            ):
                result = delivery.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled, sort_keys=True).encode(),
                    evidence_base=root,
                )
            self.assertEqual("internal", result.track)

    def test_safe_path_rejects_silent_normalization(self):
        self.assertEqual("evidence/file.json", delivery._safe_relative_path("evidence/file.json", "path"))
        for value in ("evidence//file.json", "evidence/./file.json", "./evidence/file.json"):
            with self.subTest(value=value), self.assertRaisesRegex(delivery.PlayDeliveryError, "normalized POSIX"):
                delivery._safe_relative_path(value, "path")

    def test_parent_swap_to_symlink_during_evidence_open_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = PlayDeliveryEvidenceTest().compile_valid(root)
            evidence_directory = root / "play-delivery"
            preserved_directory = root / "play-delivery-original"
            external = root / "external"
            external.mkdir()
            real_open = os.open
            swapped = False

            def swap_before_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if path == "play-delivery" and kwargs.get("dir_fd") is not None and not swapped:
                    evidence_directory.rename(preserved_directory)
                    evidence_directory.symlink_to(external, target_is_directory=True)
                    swapped = True
                return real_open(path, flags, *args, **kwargs)

            try:
                with patch.object(delivery.os, "open", side_effect=swap_before_open):
                    with self.assertRaisesRegex(
                        delivery.PlayDeliveryError,
                        "could not safely read",
                    ):
                        delivery.validate_bundle(
                            compiled,
                            source_bytes=json.dumps(compiled).encode(),
                            evidence_base=root,
                        )
            finally:
                if evidence_directory.is_symlink():
                    evidence_directory.unlink()
                if preserved_directory.exists():
                    preserved_directory.rename(evidence_directory)
            self.assertTrue(swapped)

    def test_hash_fields_reject_uppercase_hex_encodings(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = PlayDeliveryEvidenceTest().compile_valid(root)
            compiled["candidate"]["artifact_sha256"] = compiled["candidate"]["artifact_sha256"].upper()
            with self.assertRaisesRegex(delivery.PlayDeliveryError, "candidate.artifact_sha256 must be lowercase 64-hex"):
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = PlayDeliveryEvidenceTest().compile_valid(root)
            compiled["installed_identity_matrix"]["sha256"] = compiled["installed_identity_matrix"]["sha256"].upper()
            with self.assertRaisesRegex(delivery.PlayDeliveryError, "lowercase 64-hex"):
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = PlayDeliveryEvidenceTest().compile_valid(root)
            kind = sorted(delivery.REQUIRED_EVIDENCE_KINDS)[0]
            compiled["evidence"][kind]["sha256"] = compiled["evidence"][kind]["sha256"].upper()
            with self.assertRaisesRegex(delivery.PlayDeliveryError, "lowercase 64-hex"):
                delivery.validate_bundle(compiled, source_bytes=json.dumps(compiled).encode(), evidence_base=root)
