from __future__ import annotations

import copy
import hashlib
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

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
    def test_compile_file_rejects_symlinked_draft(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft_path = root / "matrix-draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            alias = root / "matrix-draft-alias.json"
            alias.symlink_to(draft_path)
            output = root / "matrix.json"
            with self.assertRaisesRegex(
                compiler.InstalledIdentityMatrixCompilationError, "non-symlink"
            ):
                compiler.compile_file(alias, output)
            self.assertFalse(output.exists())

    def compile_valid(self, root: Path):
        draft, _ = prepare(root)
        return compiler.compile_bundle(draft, base_dir=root)

    def test_source_bytes_must_match_matrix_object(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = self.compile_valid(root)
            with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "source bytes do not encode"):
                matrix.validate_bundle(compiled, source_bytes=b"{}", evidence_base=root)

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

    def test_device_manifest_mutation_between_hash_and_validation_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, device_path = prepare(root)
            original_read = compiler.device_acceptance._read_bounded_file
            mutated = False

            def read_then_mutate(*args, **kwargs):
                nonlocal mutated
                result = original_read(*args, **kwargs)
                if not mutated:
                    mutated = True
                    device_path.write_text("{}\n", encoding="utf-8")
                return result

            with patch.object(
                compiler.device_acceptance,
                "_read_bounded_file",
                side_effect=read_then_mutate,
            ):
                with self.assertRaisesRegex(
                    compiler.InstalledIdentityMatrixCompilationError,
                    "digest mismatch",
                ):
                    compiler.compile_bundle(draft, base_dir=root)
            self.assertTrue(mutated)

    def test_compile_bundle_rejects_non_object_and_non_json_python_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(
                compiler.InstalledIdentityMatrixCompilationError,
                "draft must be an object",
            ):
                compiler.compile_bundle(None, base_dir=root)  # type: ignore[arg-type]

            draft, _ = prepare(root)
            draft["unexpected_python_value"] = float("nan")
            with self.assertRaisesRegex(
                compiler.InstalledIdentityMatrixCompilationError,
                "draft contains values that are not valid JSON",
            ):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compile_bundle_generates_timestamp_and_normalizes_copy_and_device_errors(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, device_path = prepare(root)
            draft.pop("generated_at_utc")
            compiled, _ = compiler.compile_bundle(draft, base_dir=root)
            self.assertRegex(compiled["generated_at_utc"], r"^\d{4}-\d\d-\d\dT.*Z$")

            class BrokenCopy:
                def __deepcopy__(self, memo):
                    raise ValueError("copy refused")

            with self.assertRaisesRegex(compiler.InstalledIdentityMatrixCompilationError, "cannot be copied safely"):
                compiler.compile_bundle({"bad": BrokenCopy()}, base_dir=root)

            device_path.write_text("{}\n", encoding="utf-8")
            with self.assertRaisesRegex(
                compiler.InstalledIdentityMatrixCompilationError,
                "referenced device acceptance is invalid",
            ):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compile_file_rejects_draft_and_summary_output_aliases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            with self.assertRaisesRegex(compiler.InstalledIdentityMatrixCompilationError, "must not overwrite the draft"):
                compiler.compile_file(draft_path, draft_path)
            output = root / "output.json"
            with self.assertRaisesRegex(compiler.InstalledIdentityMatrixCompilationError, "distinct from draft/output"):
                compiler.compile_file(draft_path, output, summary_path=output)
            self.assertFalse(output.exists())

    def test_compile_bundle_rejects_malformed_record_shapes_paths_and_timestamps(self) -> None:
        cases = (
            ("device type", lambda draft: draft.__setitem__("device_acceptance", None), "relative path string"),
            ("device traversal", lambda draft: draft.__setitem__("device_acceptance", "../device.json"), "safe relative path"),
            ("records type", lambda draft: draft.__setitem__("records", None), "non-empty array"),
            ("empty records", lambda draft: draft.__setitem__("records", []), "non-empty array"),
            ("record type", lambda draft: draft["records"].__setitem__(0, None), "records\\[0\\] must be an object"),
            ("record extra key", lambda draft: draft["records"][0].__setitem__("extra", True), "exactly session_id and path"),
            ("blank session", lambda draft: draft["records"][0].__setitem__("session_id", "  "), "session_id must be nonblank"),
            ("path type", lambda draft: draft["records"][0].__setitem__("path", None), "relative path string"),
            ("missing record", lambda draft: draft["records"][0].__setitem__("path", "missing.json"), "missing"),
            ("invalid timestamp", lambda draft: draft.__setitem__("generated_at_utc", "tomorrow"), "generated_at_utc"),
        )
        for label, mutate, expected in cases:
            with self.subTest(case=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                draft, _ = prepare(root)
                mutate(draft)
                with self.assertRaisesRegex(compiler.InstalledIdentityMatrixCompilationError, expected):
                    compiler.compile_bundle(draft, base_dir=root)

    def test_compiler_cli_round_trip_and_invalid_input_result(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft_path = root / "draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            output = root / "matrix.json"
            stdout = StringIO()
            with redirect_stdout(stdout):
                self.assertEqual(0, compiler.main([str(draft_path), str(output)]))
            self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
            self.assertTrue(output.is_file())

            bad = root / "bad.json"
            bad.write_text("{", encoding="utf-8")
            stderr = StringIO()
            with redirect_stdout(stderr):
                self.assertEqual(1, compiler.main([str(bad), str(root / "bad-output.json")]))
            self.assertEqual("invalid", json.loads(stderr.getvalue())["status"])

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

    def test_compile_file_rejects_output_symlink_without_modifying_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            draft, _ = prepare(root)
            draft_path = root / "installed-identity-matrix-draft.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            target = root / "unrelated.json"
            target.write_text("preserve me", encoding="utf-8")
            output = root / "installed-identity-matrix.json"
            output.symlink_to(target)

            with self.assertRaisesRegex(
                compiler.InstalledIdentityMatrixCompilationError,
                "output paths must not be symlinks",
            ):
                compiler.compile_file(draft_path, output)

            self.assertEqual("preserve me", target.read_text())
            self.assertTrue(output.is_symlink())

    def test_failed_rollback_retains_installed_matrix_backups(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "matrix.json"
            second = root / "matrix-summary.json"
            first.write_text("old matrix", encoding="utf-8")
            second.write_text("old summary", encoding="utf-8")
            with patch("compile_installed_identity_matrix.atomic_publish_files", side_effect=compiler.StableFileError("injected publication failure")):
                with self.assertRaises(compiler.InstalledIdentityMatrixCompilationError) as raised:
                    compiler._publish([(first, {"new": 1}), (second, {"new": 2})])

            self.assertEqual("old matrix", first.read_text())
            self.assertEqual("old summary", second.read_text())
            self.assertIn("injected publication failure", str(raised.exception))


if __name__ == "__main__":
    unittest.main()

class InstalledIdentityMatrixReaderHardeningTest(unittest.TestCase):
    def test_rejects_oversized_symlink_and_fifo_before_reading_payload(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            oversized = root / "oversized.json"
            with oversized.open("wb") as handle:
                handle.truncate(matrix.MAX_MANIFEST_BYTES + 1)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("unbounded read attempted")):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "invalid size"):
                    matrix.load_and_validate(oversized)

            target = root / "target.json"
            target.write_text("{}")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("symlink target read")):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "regular non-symlink"):
                    matrix.load_and_validate(alias)

            fifo = root / "pipe.json"
            os.mkfifo(fifo)
            with patch.object(Path, "read_bytes", side_effect=AssertionError("FIFO read attempted")):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "regular non-symlink"):
                    matrix.load_and_validate(fifo)

    def test_referenced_manifests_are_validated_from_the_hashed_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = InstalledIdentityMatrixTest().compile_valid(root)
            matrix_path = root / "matrix.json"
            matrix_path.write_text(json.dumps(compiled), encoding="utf-8")
            with patch.object(matrix.device_acceptance, "load_and_validate", side_effect=AssertionError("reopened device manifest")), \
                    patch.object(matrix.installed_identity, "load_and_validate", side_effect=AssertionError("reopened installed identity")):
                result = matrix.load_and_validate(matrix_path)
            self.assertEqual(device_fixture.SHA, result.candidate_sha)

    def test_matrix_reader_uses_no_follow_nonblocking_descriptor_flags(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = InstalledIdentityMatrixTest().compile_valid(root)
            matrix_path = root / "matrix.json"
            matrix_path.write_text(json.dumps(compiled), encoding="utf-8")
            real_open = os.open
            captured = []
            def inspect_open(path, flags, *args, **kwargs):
                captured.append(flags)
                return real_open(path, flags, *args, **kwargs)
            with patch.object(matrix.os, "open", side_effect=inspect_open):
                result = matrix.load_and_validate(matrix_path)
            self.assertEqual(device_fixture.SHA, result.candidate_sha)
            self.assertGreaterEqual(len(captured), 2)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(captured[-1] & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(captured[-1] & os.O_NONBLOCK)

    def test_matrix_path_replacement_during_read_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = InstalledIdentityMatrixTest().compile_valid(root)
            matrix_path = root / "matrix.json"
            matrix_path.write_text(json.dumps(compiled), encoding="utf-8")
            real_read = os.read
            changed = False
            def replace_after_read(descriptor: int, amount: int) -> bytes:
                nonlocal changed
                result = real_read(descriptor, amount)
                if result and not changed:
                    changed = True
                    replacement = root / "replacement.json"
                    replacement.write_text("{}", encoding="utf-8")
                    os.replace(replacement, matrix_path)
                return result
            with patch.object(matrix.os, "read", side_effect=replace_after_read):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "changed while being read"):
                    matrix.load_and_validate(matrix_path)

class InstalledIdentityMatrixPrimitiveBoundaryTest(unittest.TestCase):
    def test_referenced_digests_reject_uppercase_hex_encodings(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, _ = InstalledIdentityMatrixTest().compile_valid(root)
            compiled["device_acceptance"]["sha256"] = compiled["device_acceptance"]["sha256"].upper()
            with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "lowercase 64-hex"):
                matrix.validate_bundle(
                    compiled,
                    source_bytes=json.dumps(compiled).encode(),
                    evidence_base=root,
                )

    def test_referenced_digests_reject_surrounding_whitespace(self) -> None:
        for field in ("device_acceptance", "record"):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                compiled, _ = InstalledIdentityMatrixTest().compile_valid(root)
                if field == "device_acceptance":
                    compiled["device_acceptance"]["sha256"] += " "
                else:
                    compiled["records"][0]["sha256"] += " "
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "lowercase 64-hex"):
                    matrix.validate_bundle(
                        compiled,
                        source_bytes=json.dumps(compiled).encode(),
                        evidence_base=root,
                    )

    def test_scalar_and_container_helpers_reject_ambiguous_types_and_bounds(self) -> None:
        for value in (None, [], "x"):
            with self.subTest(helper="mapping", value=value), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._mapping(value, "field")
        for value in (None, {}, "x"):
            with self.subTest(helper="sequence", value=value), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._sequence(value, "field")
        for value in (None, 1, True, "\x01bad", "x" * 5, "  "):
            with self.subTest(helper="string", value=str(value)[:8]), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._string(value, "field", maximum=4)
        for value in (True, 1.0, "1", -1):
            with self.subTest(helper="integer", value=value), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._integer(value, "field", minimum=0)
        with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "missing: a"):
            matrix._require_exact_keys({"b": 1}, {"a", "b"}, "field")
        with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "unrecognized keys: c"):
            matrix._require_exact_keys({"a": 1, "c": 2}, {"a"}, "field")

    def test_timestamp_and_relative_path_normalization_boundaries(self) -> None:
        for value in ("2026-08-01T10:00:00+00:00", "not-a-dateZ", "", None):
            with self.subTest(value=value), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._parse_utc(value, "captured_at_utc")
        self.assertEqual("evidence/item.json", matrix._safe_relative("evidence/item.json", "path"))
        for value in ("/absolute.json", "../escape.json", "a\\b", "~/file", "a//b", "a/./b", "./a", "a/"):
            with self.subTest(path=value), self.assertRaises(matrix.InstalledIdentityMatrixError):
                matrix._safe_relative(value, "path")

    def test_path_resolver_and_hash_reader_reject_unsafe_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            outside = root.parent / f"{root.name}-outside.json"
            outside.write_text("outside")
            try:
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "resolves outside the evidence root"):
                    matrix._resolve_inside(root, "../" + outside.name, "path")
                self.assertEqual("outside", outside.read_text())
            finally:
                outside.unlink(missing_ok=True)

            with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "is missing"):
                matrix._hash_regular(root / "missing.json", "item", 100)
            empty = root / "empty.json"
            empty.touch()
            with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "invalid size|empty"):
                matrix._hash_regular(empty, "item", 100)
            fifo = root / "fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "regular non-symlink"):
                matrix._hash_regular(fifo, "item", 100)

    def test_compiled_summary_serialization_and_module_cli(self) -> None:
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            compiled, summary = InstalledIdentityMatrixTest().compile_valid(root)
            serialized = summary.to_json()
            self.assertEqual("valid", serialized["status"])
            self.assertEqual(summary.manifest_sha256, serialized["manifest_sha256"])
            manifest = root / "matrix.json"
            manifest.write_text(json.dumps(compiled, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, matrix.main([str(manifest)]))
            self.assertEqual("valid", json.loads(output.getvalue())["status"])
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(1, matrix.main([str(root / "missing.json")]))
            self.assertEqual("invalid", json.loads(output.getvalue())["status"])

    def test_descriptor_open_errors_are_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "data.json"
            path.write_text("{}")
            with patch.object(matrix.os, "open", side_effect=PermissionError("denied")):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "could not safely read"):
                    matrix._read_regular_bytes(path, "input", 100)

    def test_hashed_evidence_opens_nonblocking_without_following_links(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"payload")
            flags_seen = []
            real_open = os.open
            def record_open(value, flags, *args, **kwargs):
                flags_seen.append(flags)
                return real_open(value, flags, *args, **kwargs)
            with patch.object(matrix.os, "open", side_effect=record_open):
                digest, _ = matrix._hash_regular(path, "payload", 100)
            self.assertEqual(hashlib.sha256(b"payload").hexdigest(), digest)
            self.assertGreaterEqual(len(flags_seen), 2)
            if hasattr(os, "O_NOFOLLOW"):
                self.assertTrue(flags_seen[-1] & os.O_NOFOLLOW)
            if hasattr(os, "O_NONBLOCK"):
                self.assertTrue(flags_seen[-1] & os.O_NONBLOCK)

    def test_matrix_evidence_hasher_rejects_fifo_swap_without_blocking(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"payload")
            real_open = os.open
            changed = False
            def swap_to_fifo(value, flags, *args, **kwargs):
                nonlocal changed
                if value == path.name and kwargs.get("dir_fd") is not None and not changed:
                    changed = True
                    path.unlink()
                    os.mkfifo(path)
                return real_open(value, flags, *args, **kwargs)
            with patch.object(matrix.os, "open", side_effect=swap_to_fifo):
                with self.assertRaisesRegex(matrix.InstalledIdentityMatrixError, "changed while being opened"):
                    matrix._hash_regular(path, "payload", 100)
