from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

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
    def compile_valid(self, root: Path):
        return compiler.compile_bundle(prepare(root), base_dir=root)

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


if __name__ == "__main__":
    unittest.main()
