from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import build_release_evidence_index as index_builder
import compile_release_governance
import test_validate_device_acceptance as device_fixture
import test_validate_release_governance as governance_fixture
import validate_release_readiness as readiness
import verify_store_metadata as store_metadata
import stable_file_reader


GENERIC_BOUND_KINDS = (
    "artifact_verification",
    "declared_dependencies",
    "sbom",
    "device_aggregate",
    "screenshot_manifest",
    "graphics_manifest",
)


def _prepare_store_metadata(root: Path) -> Path:
    metadata = root / "store-metadata"
    metadata.mkdir(parents=True, exist_ok=True)
    (metadata / "title.txt").write_text("Forest Run", encoding="utf-8")
    (metadata / "short-description.txt").write_text(
        "Run gently through a living forest that remembers your choices.",
        encoding="utf-8",
    )
    (metadata / "full-description.txt").write_text(
        "Forest Run is a handcrafted endless runner where mercy changes the path.\n\n"
        "Collect Seeds, enter Bloom, meet memorable creatures, and return to a "
        "persistent Garden that remembers how you played. Open the Forest Journal "
        "to revisit discoveries and bonds from earlier journeys.",
        encoding="utf-8",
    )
    return store_metadata.finalize_metadata(metadata, device_fixture.SHA)


def _base_specs(generic_paths: dict[str, str], *, signed_bundle_path: str) -> list[str]:
    return [
        f"signed_bundle={signed_bundle_path}",
        "device_acceptance=device-acceptance.json",
        "human_acceptance=human-acceptance.json",
        "installed_identity_matrix=installed-identity-matrix.json",
        "play_delivery=play-delivery.json",
        "release_governance=release-governance.json",
        "store_metadata=store-metadata/metadata_manifest.json",
    ] + [f"{kind}={generic_paths[kind]}" for kind in GENERIC_BOUND_KINDS]


def prepare_complete_evidence(root: Path, *, signed_bundle_path: str = "artifact/app-release.aab") -> dict[str, Path]:
    governance_draft = governance_fixture._governance_draft(root)
    governance_draft_path = root / "release-governance-draft.json"
    governance_path = root / "release-governance.json"
    governance_draft_path.write_text(
        json.dumps(governance_draft, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    compile_release_governance.compile_file(governance_draft_path, governance_path)
    metadata_manifest = _prepare_store_metadata(root)

    generic_paths: dict[str, str] = {}
    for kind in GENERIC_BOUND_KINDS:
        relative = f"final-index/{kind}.json"
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "candidateSha": device_fixture.SHA,
                    "kind": kind,
                },
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        generic_paths[kind] = relative

    specs = _base_specs(generic_paths, signed_bundle_path=signed_bundle_path)
    index_path = root / "release-evidence-index.json"
    payload = index_builder.build_index(
        root,
        device_fixture.SHA,
        specs,
        generated_at_utc="2026-08-01T16:00:00Z",
        require_bound_kinds=readiness.REQUIRED_BOUND_KINDS,
        output=index_path,
    )
    index_builder.publish_index(index_path, payload, root=root)
    return {
        "device": root / "device-acceptance.json",
        "human": root / "human-acceptance.json",
        "matrix": root / "installed-identity-matrix.json",
        "play": root / "play-delivery.json",
        "governance": governance_path,
        "metadata": metadata_manifest,
        "index": index_path,
    }


class ReleaseReadinessTest(unittest.TestCase):
    def validate(self, root: Path, paths: dict[str, Path]):
        return readiness.validate_readiness(
            root=root,
            expected_candidate_sha=device_fixture.SHA,
            device_manifest=paths["device"],
            human_manifest=paths["human"],
            installed_identity_matrix=paths["matrix"],
            play_delivery_manifest=paths["play"],
            governance_manifest=paths["governance"],
            store_metadata_manifest=paths["metadata"],
            release_index=paths["index"],
        )

    def test_complete_cross_bound_evidence_is_ready(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            summary = self.validate(root, paths)
            self.assertEqual(device_fixture.SHA, summary.candidate_sha)
            self.assertEqual(device_fixture.ARTIFACT_SHA, summary.artifact_sha256)
            self.assertEqual(device_fixture.UPLOAD_CERT_SHA, summary.upload_certificate_sha256)
            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)
            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")
            self.assertRegex(summary.play_delivery_sha256, r"^[0-9a-f]{64}$")
            self.assertRegex(summary.store_metadata_sha256, r"^[0-9a-f]{64}$")
            self.assertGreaterEqual(summary.evidence_entry_count, 13)
            self.assertRegex(summary.evidence_index_sha256, r"^[0-9a-f]{64}$")
            self.assertRegex(summary.evidence_set_sha256, r"^[0-9a-f]{64}$")

    def test_readiness_rejects_symlinked_evidence_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            root = workspace / "evidence"
            root.mkdir()
            paths = prepare_complete_evidence(root)
            alias = workspace / "evidence-alias"
            try:
                alias.symlink_to(root, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaisesRegex(
                readiness.ReleaseReadinessError,
                "evidence root.*symbolic link",
            ):
                self.validate(alias, paths)

    def test_expected_candidate_must_match_all_validated_layers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                readiness.validate_readiness(
                    root=root,
                    expected_candidate_sha="f" * 40,
                    device_manifest=paths["device"],
                    human_manifest=paths["human"],
                    installed_identity_matrix=paths["matrix"],
                    play_delivery_manifest=paths["play"],
                    governance_manifest=paths["governance"],
                    store_metadata_manifest=paths["metadata"],
                    release_index=paths["index"],
                )
            self.assertIn("must all match", str(raised.exception))

    def test_index_must_contain_every_required_candidate_bound_kind(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            payload = json.loads(paths["index"].read_text(encoding="utf-8"))
            payload["entries"] = [
                entry for entry in payload["entries"] if entry["kind"] != "graphics_manifest"
            ]
            payload["entryCount"] = len(payload["entries"])
            # Deliberately leave the old evidenceSetSha256. The independent index
            # verifier must reject this before readiness can claim completion.
            paths["index"].write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("release evidence index failed", str(raised.exception))

    def test_store_metadata_manifest_is_a_required_candidate_bound_kind(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            payload = json.loads(paths["index"].read_text(encoding="utf-8"))
            payload["entries"] = [
                entry for entry in payload["entries"] if entry["kind"] != "store_metadata"
            ]
            payload["entryCount"] = len(payload["entries"])
            paths["index"].write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("release evidence index failed", str(raised.exception))

    def test_tampered_store_copy_is_rejected_even_when_manifest_was_indexed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            title = paths["metadata"].parent / "title.txt"
            title.write_text("Forest Run Changed", encoding="utf-8")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("store metadata failed", str(raised.exception))

    def test_store_metadata_text_symlink_is_rejected_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            title = paths["metadata"].parent / "title.txt"
            replacement = root / "safe-title.txt"
            replacement.write_text("Forest Run", encoding="utf-8")
            title.unlink()
            try:
                title.symlink_to(replacement)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("store metadata title.txt must not traverse a symbolic link", str(raised.exception))

    def test_indexed_signed_bundle_must_be_the_accepted_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            # Governance/device acceptance still point to the real candidate AAB.
            governance_fixture._prepare_human(root)
            other = root / "artifact" / "other-release.aab"
            other.parent.mkdir(parents=True, exist_ok=True)
            other.write_bytes(b"different-signed-artifact\n")
            paths = prepare_complete_evidence(root, signed_bundle_path="artifact/other-release.aab")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("signed_bundle digest does not match", str(raised.exception))

    def test_indexed_manifest_path_must_be_the_manifest_that_was_revalidated(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            copied = root / "final-index" / "copied-device.json"
            copied.write_bytes(paths["device"].read_bytes())

            generic = {
                kind: f"final-index/{kind}.json"
                for kind in GENERIC_BOUND_KINDS
            }
            specs = [
                "signed_bundle=artifact/app-release.aab",
                "device_acceptance=final-index/copied-device.json",
                "human_acceptance=human-acceptance.json",
                "installed_identity_matrix=installed-identity-matrix.json",
                "play_delivery=play-delivery.json",
                "release_governance=release-governance.json",
                "store_metadata=store-metadata/metadata_manifest.json",
            ] + [f"{kind}={generic[kind]}" for kind in GENERIC_BOUND_KINDS]
            payload = index_builder.build_index(
                root,
                device_fixture.SHA,
                specs,
                generated_at_utc="2026-08-01T16:05:00Z",
                require_bound_kinds=readiness.REQUIRED_BOUND_KINDS,
                output=paths["index"],
            )
            index_builder.publish_index(paths["index"], payload, root=root)
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("path does not match the validated input", str(raised.exception))

    def test_tampered_governance_cannot_be_masked_by_a_still_valid_index(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            governance = json.loads(paths["governance"].read_text(encoding="utf-8"))
            governance["final_decision"]["status"] = "pending"
            paths["governance"].write_text(
                json.dumps(governance, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                self.validate(root, paths)
            self.assertIn("release governance failed", str(raised.exception))

    def test_symlinked_manifest_component_is_rejected_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            alias = root / "manifest-alias"
            real = root / "manifest-real"
            real.mkdir()
            copied = real / "device-acceptance.json"
            copied.write_bytes(paths["device"].read_bytes())
            try:
                alias.symlink_to(real, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaises(readiness.ReleaseReadinessError) as raised:
                readiness.validate_readiness(
                    root=root,
                    expected_candidate_sha=device_fixture.SHA,
                    device_manifest=alias / "device-acceptance.json",
                    human_manifest=paths["human"],
                    installed_identity_matrix=paths["matrix"],
                    play_delivery_manifest=paths["play"],
                    governance_manifest=paths["governance"],
                    store_metadata_manifest=paths["metadata"],
                    release_index=paths["index"],
                )
            self.assertIn("must not traverse a symbolic link", str(raised.exception))


if __name__ == "__main__":
    unittest.main()

class ReleaseReadinessHelperBoundaryTest(unittest.TestCase):
    def test_bound_manifest_hasher_rejects_parent_swap_to_external_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            root = workspace / "evidence"
            root.mkdir()
            path = root / "manifest.json"
            path.write_text("{}", encoding="utf-8")
            saved_root = workspace / "evidence.saved"
            external = workspace / "external"
            external.mkdir()
            (external / "manifest.json").write_text("{}", encoding="utf-8")
            original_open = os.open
            swapped = False

            def swap_then_open(value, flags, *args, **kwargs):
                nonlocal swapped
                if value == root.name and kwargs.get("dir_fd") is not None and not swapped:
                    root.rename(saved_root)
                    root.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(value, flags, *args, **kwargs)

            with patch.object(readiness.os, "open", side_effect=swap_then_open):
                with self.assertRaises(readiness.ReleaseReadinessError):
                    readiness._sha256_file(path)
            self.assertTrue(swapped)
            self.assertTrue(root.is_symlink())

    def test_expected_sha_normalizes_whitespace_but_requires_lowercase_full_sha(self) -> None:
        self.assertEqual(device_fixture.SHA, readiness._expected_sha(f"  {device_fixture.SHA}\n"))
        for value in ("A" * 40, "f" * 39, "g" * 40, "main"):
            with self.subTest(value=value), self.assertRaises(readiness.ReleaseReadinessError):
                readiness._expected_sha(value)

    def test_safe_relative_path_rejects_outside_missing_symlink_and_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            file = root / "valid.json"
            file.write_text("{}")
            self.assertEqual("valid.json", readiness._safe_relative_path(file, root, "input")[0])
            with self.assertRaisesRegex(readiness.ReleaseReadinessError, "inside the evidence root"):
                readiness._safe_relative_path(root.parent / "escape.json", root, "input")
            with self.assertRaisesRegex(readiness.ReleaseReadinessError, "is missing"):
                readiness._safe_relative_path(root / "missing.json", root, "input")
            directory = root / "directory"
            directory.mkdir()
            with self.assertRaisesRegex(readiness.ReleaseReadinessError, "regular file"):
                readiness._safe_relative_path(directory, root, "input")
            alias = root / "alias.json"
            try:
                alias.symlink_to(file)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaisesRegex(readiness.ReleaseReadinessError, "symbolic link"):
                readiness._safe_relative_path(alias, root, "input")

    def test_safe_relative_path_rejects_symlinked_evidence_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            root = workspace / "evidence"
            root.mkdir()
            file = root / "valid.json"
            file.write_text("{}", encoding="utf-8")
            alias = workspace / "evidence-alias"
            try:
                alias.symlink_to(root, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaisesRegex(
                readiness.ReleaseReadinessError,
                "evidence root.*symbolic link",
            ):
                readiness._safe_relative_path(file, alias, "input")

    def test_entry_map_rejects_malformed_and_duplicate_entries(self) -> None:
        for value in ({}, {"entries": "bad"}, {"entries": [1]},
                      {"entries": [{"kind": 1}]},
                      {"entries": [{"kind": "x"}, {"kind": "x"}]}):
            with self.subTest(value=value), self.assertRaises(readiness.ReleaseReadinessError):
                readiness._entry_map(value)
        self.assertEqual({"ok": {"kind": "ok"}}, readiness._entry_map({"entries": [{"kind": "ok"}]}))

    def test_index_reference_requires_exact_path_and_digest(self) -> None:
        entries = {"x": {"path": "evidence.json", "sha256": "a" * 64}}
        readiness._require_index_reference(entries, kind="x", expected_path="evidence.json", expected_sha256="a" * 64)
        cases = (
            ({}, "missing x"),
            ({"x": {"path": "other", "sha256": "a" * 64}}, "path does not match"),
            ({"x": {"path": "evidence.json", "sha256": "b" * 64}}, "digest does not match"),
        )
        for value, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(readiness.ReleaseReadinessError, message):
                readiness._require_index_reference(value, kind="x", expected_path="evidence.json", expected_sha256="a" * 64)

    def test_summary_writer_is_atomic_and_removes_temporary_on_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "nested" / "summary.json"
            readiness._write_summary(output, {"status": "valid", "value": 3})
            self.assertEqual({"status": "valid", "value": 3}, json.loads(output.read_text()))
            self.assertEqual([], list(output.parent.glob("*.tmp")))
            with self.assertRaises(ValueError):
                readiness._write_summary(output, {"bad": float("nan")})
            self.assertEqual({"status": "valid", "value": 3}, json.loads(output.read_text()))
            self.assertEqual([], list(output.parent.glob("*.tmp")))

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
                with self.assertRaisesRegex(
                    readiness.ReleaseReadinessError, "symbolic link"
                ):
                    readiness._write_summary(output, {"status": "valid"})

            self.assertTrue(swapped)
            self.assertFalse((external / "summary.json").exists())
            self.assertFalse((saved_output_dir / "summary.json").exists())

    def test_cli_success_publishes_summary_and_invalid_input_returns_one(self) -> None:
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = prepare_complete_evidence(root)
            args = ["--root", str(root), "--expected-candidate-sha", device_fixture.SHA,
                "--device-acceptance", str(paths["device"]), "--human-acceptance", str(paths["human"]),
                "--installed-identity-matrix", str(paths["matrix"]), "--play-delivery", str(paths["play"]),
                "--release-governance", str(paths["governance"]), "--store-metadata-manifest", str(paths["metadata"]),
                "--release-evidence-index", str(paths["index"])]
            output = root / "summary.json"
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(0, readiness.main([*args, "--summary-output", str(output)]))
            self.assertEqual("valid", json.loads(stdout.getvalue())["status"])
            self.assertEqual("valid", json.loads(output.read_text())["status"])
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(1, readiness.main([*args[:-1], str(root / "missing-index.json")]))
            self.assertEqual("invalid", json.loads(stdout.getvalue())["status"])

    def test_artifact_and_certificate_identity_mismatches_are_rejected(self) -> None:
        from dataclasses import replace
        from unittest import mock
        cases = (
            ("artifact_sha256", "artifact SHA-256 values do not match"),
            ("upload_certificate_sha256", "upload-certificate SHA-256 values do not match"),
            ("app_signing_certificate_sha256", "app-signing-certificate SHA-256 values do not match"),
        )
        for attribute, message in cases:
            with self.subTest(attribute=attribute), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                paths = prepare_complete_evidence(root)
                original = readiness.governance.load_and_validate(paths["governance"])
                changed = replace(original, **{attribute: "f" * 64})
                with mock.patch.object(readiness.governance, "load_and_validate", return_value=changed):
                    with self.assertRaisesRegex(readiness.ReleaseReadinessError, message):
                        readiness.validate_readiness(root=root, expected_candidate_sha=device_fixture.SHA,
                            device_manifest=paths["device"], human_manifest=paths["human"],
                            installed_identity_matrix=paths["matrix"], play_delivery_manifest=paths["play"],
                            governance_manifest=paths["governance"], store_metadata_manifest=paths["metadata"],
                            release_index=paths["index"])
