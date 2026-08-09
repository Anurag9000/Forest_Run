from __future__ import annotations

import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import compile_installed_identity_matrix as compiler
import test_installed_candidate_identity as installed_fixture
import test_validate_device_acceptance as device_fixture
import validate_installed_identity_matrix as matrix


def prepare(root: Path) -> tuple[dict, Path]:
    device = device_fixture.valid_bundle()
    device_fixture.materialize_files(root, device)
    device_path = root / "device-acceptance.json"
    device_path.write_text(json.dumps(device, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    records = []
    for index, session in enumerate(device["sessions"]):
        record_dir = root / "installed" / session["session_id"]
        record = installed_fixture.materialize(record_dir)
        record["captured_at_utc"] = "2026-08-01T10:10:00Z"
        record["candidate"]["commit_sha"] = device_fixture.SHA
        record["candidate"]["version_code"] = 7
        record["candidate"]["app_signing_certificate_sha256"] = device_fixture.APP_SIGNING_CERT_SHA
        record["device"]["serial_sha256"] = hashlib.sha256(
            f"physical-{index}".encode("utf-8")
        ).hexdigest()
        record["device"]["manufacturer"] = session["device"]["manufacturer"]
        record["device"]["model"] = session["device"]["model"]
        record["device"]["build_fingerprint"] = session["device"]["build_fingerprint"]
        record["device"]["sdk"] = session["device"]["sdk"]
        record["installed_package"]["version_code"] = 7
        record["installed_package"]["app_signing_certificate_sha256"] = device_fixture.APP_SIGNING_CERT_SHA
        for apk in record["installed_package"]["apk_set"]:
            apk["signing_certificate_sha256"] = device_fixture.APP_SIGNING_CERT_SHA
        record_path = record_dir / "installed-candidate-identity.json"
        record_path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        records.append(
            {
                "session_id": session["session_id"],
                "path": record_path.relative_to(root).as_posix(),
            }
        )

    return {
        "generated_at_utc": "2026-08-01T12:30:00Z",
        "device_acceptance": "device-acceptance.json",
        "records": records,
    }, device_path


class InstalledIdentityMatrixTest(unittest.TestCase):
    def compile_valid(self, root: Path):
        draft, _ = prepare(root)
        return compiler.compile_bundle(draft, base_dir=root)

    def test_complete_matrix_covers_every_physical_session_and_device(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = self.compile_valid(root)
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)
            self.assertEqual(7, summary.version_code)
            self.assertEqual(device_fixture.ARTIFACT_SHA, summary.artifact_sha256)
            self.assertEqual(device_fixture.UPLOAD_CERT_SHA, summary.upload_certificate_sha256)
            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)
            self.assertEqual(5, summary.record_count)
            self.assertEqual(5, summary.physical_device_count)
            self.assertEqual(5, len(compiled["records"]))

    def test_missing_or_unknown_physical_session_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft["records"].pop()
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("exactly one installed identity", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft["records"][0]["session_id"] = "not-a-real-session"
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("duplicated or unknown", str(raised.exception))

    def test_record_device_identity_must_match_its_physical_session(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            record_path = root / draft["records"][0]["path"]
            record = json.loads(record_path.read_text(encoding="utf-8"))
            record["device"]["build_fingerprint"] = "different/build/fingerprint"
            record_path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("build_fingerprint does not match physical session", str(raised.exception))

    def test_record_candidate_version_and_delivered_signer_must_match_physical_acceptance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            record_path = root / draft["records"][0]["path"]
            record = json.loads(record_path.read_text(encoding="utf-8"))
            record["candidate"]["app_signing_certificate_sha256"] = "9" * 64
            record["installed_package"]["app_signing_certificate_sha256"] = "9" * 64
            for apk in record["installed_package"]["apk_set"]:
                apk["signing_certificate_sha256"] = "9" * 64
            record_path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("app-signing certificate does not match", str(raised.exception))

    def test_each_physical_session_must_use_distinct_device_serial_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            first_path = root / draft["records"][0]["path"]
            second_path = root / draft["records"][1]["path"]
            first = json.loads(first_path.read_text(encoding="utf-8"))
            second = json.loads(second_path.read_text(encoding="utf-8"))
            second["device"]["serial_sha256"] = first["device"]["serial_sha256"]
            second_path.write_text(json.dumps(second, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("reuse the same installed-device serial identity", str(raised.exception))

    def test_record_must_be_temporally_close_to_physical_session(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            record_path = root / draft["records"][0]["path"]
            record = json.loads(record_path.read_text(encoding="utf-8"))
            record["captured_at_utc"] = "2026-08-04T10:10:00Z"
            record_path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            draft["generated_at_utc"] = "2026-08-04T12:00:00Z"
            with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                compiler.compile_bundle(draft, base_dir=root)
            self.assertIn("more than 24 hours", str(raised.exception))

    def test_record_tampering_and_symlink_aliasing_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            path = root / compiled["records"][0]["path"]
            path.write_text("{}\n", encoding="utf-8")
            with self.assertRaises(matrix.InstalledIdentityMatrixError) as raised:
                matrix.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )
            self.assertIn("digest mismatch", str(raised.exception))

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            original = Path(compiled["records"][0]["path"])
            real_parent = root / original.parent
            alias_parent = root / "installed-alias"
            try:
                alias_parent.symlink_to(real_parent, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            compiled["records"][0]["path"] = f"installed-alias/{original.name}"
            with self.assertRaises(matrix.InstalledIdentityMatrixError) as raised:
                matrix.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )
            self.assertIn("must not traverse a symbolic link", str(raised.exception))

    def test_compile_file_preserves_draft_and_publishes_valid_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft_path = root / "installed-identity-matrix-draft.json"
            final_path = root / "installed-identity-matrix.json"
            summary_path = root / "installed-identity-matrix-summary.json"
            draft_path.write_text(json.dumps(draft, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            result = compiler.compile_file(draft_path, final_path, summary_path=summary_path)
            self.assertEqual(5, result.record_count)
            self.assertTrue(final_path.is_file())
            self.assertTrue(summary_path.is_file())
            self.assertIsInstance(json.loads(draft_path.read_text())["device_acceptance"], str)
            revalidated = matrix.load_and_validate(final_path)
            self.assertEqual(result.manifest_sha256, revalidated.manifest_sha256)


if __name__ == "__main__":
    unittest.main()
