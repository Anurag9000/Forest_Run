from __future__ import annotations

import hashlib
import copy
import contextlib
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import stable_file_reader

SCRIPT = Path(__file__).with_name("validate_device_acceptance.py")
SPEC = importlib.util.spec_from_file_location("device_acceptance", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

ARTIFACT_BYTES = b"forest-run-signed-aab-v1\n"
EVIDENCE_BYTES = b"forest-run-device-evidence-v1\n"
ARTIFACT_SHA = hashlib.sha256(ARTIFACT_BYTES).hexdigest()
EVIDENCE_SHA = hashlib.sha256(EVIDENCE_BYTES).hexdigest()
UPLOAD_CERT_SHA = "3" * 64
APP_SIGNING_CERT_SHA = "4" * 64
CERT_SHA = UPLOAD_CERT_SHA  # compatibility alias for tests importing this fixture
SHA = "1" * 40
SCENARIOS = (
    "ordinary_play_15m",
    "all_entities",
    "bloom_dense",
    "lifecycle_recovery",
    "settings_accessibility",
    "garden_transactions",
    "ghost_persistence",
)
CLASSES = (
    "older_phone",
    "midrange_phone",
    "high_refresh_phone",
    "cutout_phone",
    "tablet",
)


def session(device_class: str, index: int) -> dict:
    return {
        "session_id": f"session-{device_class}-{index}",
        "started_at_utc": "2026-08-01T10:00:00Z",
        "completed_at_utc": "2026-08-01T10:20:00Z",
        "duration_seconds": 1200,
        "device": {
            "class": device_class,
            "manufacturer": "Example",
            "model": f"Model-{index}",
            "build_fingerprint": f"example/device/{index}:15/ABC/123:user/release-keys",
            "sdk": 35,
            "ram_mb": 4096,
            "refresh_hz": 120 if device_class == "high_refresh_phone" else 60,
            "width_px": 2400,
            "height_px": 1080,
            "density_dpi": 420,
            "tablet": device_class == "tablet",
            "cutout": device_class == "cutout_phone",
        },
        "build": {
            "commit_sha": SHA,
            "artifact_sha256": ARTIFACT_SHA,
            "app_signing_certificate_sha256": APP_SIGNING_CERT_SHA,
            "version_code": 7,
            "signed": True,
            "installed_via": "internal_store",
        },
        "scenarios": {
            name: {
                "passed": True,
                "evidence_files": [
                    {
                        "path": f"evidence/{device_class}/{name}-{index}.json",
                        "sha256": EVIDENCE_SHA,
                    }
                ],
            }
            for name in SCENARIOS
        },
        "performance": {
            "p95_frame_ms": 16.0,
            "p99_frame_ms": 25.0,
            "slow_frame_ratio": 0.01,
            "peak_pss_mb": 220.0,
            "crashes": 0,
            "anrs": 0,
        },
        "manual_checks": {
            "touch_controls": "pass",
            "safe_content_readability": "pass",
            "audio": "pass",
            "haptics": "pass",
            "reduced_motion": "pass",
            "lifecycle_recovery": "pass",
            "artwork_animation": "pass",
        },
    }


def valid_bundle() -> dict:
    return {
        "schema_version": MODULE.SCHEMA_VERSION,
        "generated_at_utc": "2026-08-01T12:00:00Z",
        "candidate": {
            "repository": "Anurag9000/Forest_Run",
            "branch": "main",
            "commit_sha": SHA,
            "application_id": "com.anurag9000.forestrun",
            "version_code": 7,
            "artifact_sha256": ARTIFACT_SHA,
            "artifact_path": "artifact/app-release.aab",
            "signed": True,
            "upload_certificate_sha256": UPLOAD_CERT_SHA,
            "store_delivery": {
                "track": "internal",
                "installed": True,
                "package_name": "com.anurag9000.forestrun",
                "version_code": 7,
                "artifact_sha256": ARTIFACT_SHA,
                "app_signing_certificate_sha256": APP_SIGNING_CERT_SHA,
            },
        },
        "policy": {
            "required_device_classes": list(CLASSES),
            "required_scenarios": list(SCENARIOS),
            "min_sessions_per_class": 1,
            "thresholds": {
                "max_p95_frame_ms": 20.0,
                "max_p99_frame_ms": 33.4,
                "max_slow_frame_ratio": 0.02,
                "max_peak_pss_mb": 300.0,
                "max_crashes": 0,
                "max_anrs": 0,
                "min_duration_seconds": 900.0,
            },
        },
        "sessions": [session(name, index) for index, name in enumerate(CLASSES)],
        "approvals": {
            **{name: "approved" for name in MODULE.REQUIRED_APPROVALS},
            "reviewers": ["release-owner", "independent-reviewer"],
            "reviewed_at_utc": "2026-08-01T11:30:00Z",
        },
    }


def materialize_files(root: Path, bundle: dict, *, corrupt: bool = False) -> None:
    """Materialize the candidate artifact and every declared evidence file."""
    artifact = root / bundle["candidate"]["artifact_path"]
    artifact.parent.mkdir(parents=True, exist_ok=True)
    artifact.write_bytes(ARTIFACT_BYTES)
    first = True
    for item in bundle["sessions"]:
        for result in item["scenarios"].values():
            for evidence in result["evidence_files"]:
                target = root / evidence["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(
                    b"bad\n" if corrupt and first else EVIDENCE_BYTES
                )
                first = False


class DeviceAcceptanceTest(unittest.TestCase):
    def validate(self, bundle: dict):
        raw = json.dumps(bundle, sort_keys=True).encode()
        return MODULE.validate_bundle(bundle, source_bytes=raw)

    def invalid(self, bundle: dict, fragment: str) -> None:
        with self.assertRaises(MODULE.EvidenceError) as raised:
            self.validate(bundle)
        self.assertIn(fragment, str(raised.exception))

    @staticmethod
    def materialize(root: Path, bundle: dict, *, corrupt: bool = False) -> None:
        materialize_files(root, bundle, corrupt=corrupt)

    def test_complete_bundle(self) -> None:
        summary = self.validate(valid_bundle())
        self.assertEqual(
            (5, 35, SHA),
            (
                summary.session_count,
                summary.evidence_file_count,
                summary.candidate_sha,
            ),
        )

    def test_source_bytes_must_match_acceptance_object(self) -> None:
        bundle = valid_bundle()
        with self.assertRaisesRegex(MODULE.EvidenceError, "source bytes do not encode"):
            MODULE.validate_bundle(bundle, source_bytes=b"{}")

    def test_unknown_keys_are_rejected_at_each_manifest_schema_boundary(self) -> None:
        mutations = (
            ("root", lambda bundle: bundle),
            ("policy", lambda bundle: bundle["policy"]),
            ("policy.thresholds", lambda bundle: bundle["policy"]["thresholds"]),
            ("sessions[0]", lambda bundle: bundle["sessions"][0]),
            ("sessions[0].device", lambda bundle: bundle["sessions"][0]["device"]),
            ("sessions[0].scenarios", lambda bundle: bundle["sessions"][0]["scenarios"]),
            (
                "sessions[0].scenarios.ordinary_play_15m",
                lambda bundle: bundle["sessions"][0]["scenarios"]["ordinary_play_15m"],
            ),
            (
                "sessions[0].scenarios.ordinary_play_15m.evidence_files[0]",
                lambda bundle: bundle["sessions"][0]["scenarios"]["ordinary_play_15m"]["evidence_files"][0],
            ),
            ("sessions[0].performance", lambda bundle: bundle["sessions"][0]["performance"]),
        )
        for label, select in mutations:
            with self.subTest(label=label):
                bundle = copy.deepcopy(valid_bundle())
                select(bundle)["unrecognized_probe"] = "ignored before fix"
                self.invalid(bundle, f"{label} contains unrecognized keys")

    def test_artifact_parent_swap_to_external_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            artifact_parent = root / "artifact"
            saved_parent = root / "artifact.saved"
            external = root / "external"
            external.mkdir()
            (external / "app-release.aab").write_bytes(ARTIFACT_BYTES)
            real_open = os.open
            swapped = False

            def swap_then_open(value, flags, *args, **kwargs):
                nonlocal swapped
                if value == "artifact" and kwargs.get("dir_fd") is not None and not swapped:
                    artifact_parent.rename(saved_parent)
                    artifact_parent.symlink_to(external, target_is_directory=True)
                    swapped = True
                return real_open(value, flags, *args, **kwargs)

            with patch.object(MODULE.os, "open", side_effect=swap_then_open):
                with self.assertRaises(MODULE.EvidenceError):
                    MODULE.validate_bundle(
                        bundle,
                        source_bytes=json.dumps(bundle, sort_keys=True).encode(),
                        evidence_base=root,
                    )
            self.assertTrue(swapped)
            self.assertTrue(artifact_parent.is_symlink())

    def test_candidate_and_store_identity_fail_closed(self) -> None:
        cases = (
            ("repository", "fork/repo", "candidate.repository"),
            ("branch", "feature", "candidate.branch"),
            ("application_id", "bad.id", "candidate.application_id"),
            ("signed", False, "candidate.signed"),
        )
        for field, value, message in cases:
            with self.subTest(field=field):
                bundle = valid_bundle()
                bundle["candidate"][field] = value
                self.invalid(bundle, message)
        bundle = valid_bundle()
        bundle["candidate"]["store_delivery"]["installed"] = False
        self.invalid(bundle, "store_delivery.installed")

    def test_coverage_and_device_semantics(self) -> None:
        bundle = valid_bundle()
        bundle["sessions"].pop()
        self.invalid(bundle, "insufficient sessions")
        for index, field, value, message in (
            (2, "refresh_hz", 60, "must be >= 90"),
            (3, "cutout", False, "must be true for cutout_phone"),
            (4, "tablet", False, "must be true for tablet"),
        ):
            bundle = valid_bundle()
            bundle["sessions"][index]["device"][field] = value
            self.invalid(bundle, message)

    def test_scenarios_paths_and_build_binding(self) -> None:
        bundle = valid_bundle()
        del bundle["sessions"][0]["scenarios"]["all_entities"]
        self.invalid(bundle, "scenarios is missing")
        bundle = valid_bundle()
        bundle["sessions"][0]["build"]["artifact_sha256"] = "9" * 64
        self.invalid(bundle, "does not match candidate")
        bundle = valid_bundle()
        bundle["sessions"][0]["scenarios"]["all_entities"]["evidence_files"][0][
            "path"
        ] = "../escape"
        self.invalid(bundle, "safe relative path")

    def test_thresholds_timestamps_and_approvals(self) -> None:
        for field, value in {
            "p95_frame_ms": 21.0,
            "p99_frame_ms": 34.0,
            "slow_frame_ratio": 0.03,
            "peak_pss_mb": 301.0,
            "crashes": 1,
            "anrs": 1,
        }.items():
            bundle = valid_bundle()
            bundle["sessions"][0]["performance"][field] = value
            self.invalid(bundle, "exceeds")
        bundle = valid_bundle()
        bundle["sessions"][0]["duration_seconds"] = 1000
        self.invalid(bundle, "inconsistent with timestamps")
        bundle = valid_bundle()
        bundle["approvals"]["reviewers"] = ["only-one"]
        self.invalid(bundle, "at least 2")

    def test_cli_verifies_artifact_evidence_and_atomic_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "manifest.json"
            summary = root / "out" / "summary.json"
            bundle = valid_bundle()
            manifest.write_text(json.dumps(bundle))
            self.assertEqual(1, MODULE.main([str(manifest)]))
            self.materialize(root, bundle, corrupt=True)
            self.assertEqual(1, MODULE.main([str(manifest)]))
            self.materialize(root, bundle)
            self.assertEqual(
                0,
                MODULE.main(
                    [str(manifest), "--summary-output", str(summary)]
                ),
            )
            self.assertEqual("valid", json.loads(summary.read_text())["status"])
            self.assertFalse(summary.with_name(summary.name + ".tmp").exists())

    def test_summary_parent_swap_cannot_redirect_atomic_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output_dir = root / "out"
            external = root / "external"
            external.mkdir()
            saved_output_dir = root / "out.saved"
            summary = output_dir / "summary.json"
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
                with self.assertRaisesRegex(MODULE.EvidenceError, "symbolic link"):
                    MODULE._write_json(summary, {"status": "valid"})

            self.assertTrue(swapped)
            self.assertFalse((external / "summary.json").exists())
            self.assertFalse((saved_output_dir / "summary.json").exists())

    def test_cli_reports_summary_publication_failure_as_json(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            self.materialize(root, bundle)
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(bundle), encoding="utf-8")
            external = root / "external.json"
            external.write_text("preserve", encoding="utf-8")
            output = root / "summary.json"
            output.symlink_to(external)
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                result = MODULE.main(
                    [str(manifest), "--summary-output", str(output)]
                )
            self.assertEqual(1, result)
            payload = json.loads(stdout.getvalue())
            self.assertEqual("invalid", payload["status"])
            self.assertRegex(payload["error"], r"symbolic link|symlink")
            self.assertEqual("preserve", external.read_text(encoding="utf-8"))

    def test_invalid_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.json"
            manifest.write_text("{")
            self.assertEqual(1, MODULE.main([str(manifest)]))


    def test_load_rejects_duplicate_json_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "device-acceptance.json"
            path.write_text('{"schema_version":1,"schema_version":1}\n', encoding="utf-8")
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.load_and_validate(path)
            self.assertIn("duplicate JSON object key", str(raised.exception))

    def test_manifest_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            real = root / "real.json"
            real.write_text(json.dumps(bundle) + "\n", encoding="utf-8")
            alias = root / "alias.json"
            try:
                alias.symlink_to(real)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.load_and_validate(alias)
            self.assertRegex(str(raised.exception), r"symbolic link|non-symlink")

    def test_artifact_symlink_component_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            real_dir = root / "artifact"
            alias_dir = root / "artifact-alias"
            try:
                alias_dir.symlink_to(real_dir, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            bundle["candidate"]["artifact_path"] = "artifact-alias/app-release.aab"
            raw = json.dumps(bundle, sort_keys=True).encode()
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)
            self.assertIn("must not traverse a symbolic link", str(raised.exception))

    def test_scenario_evidence_symlink_component_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            first_session = bundle["sessions"][0]
            first_scenario = next(iter(first_session["scenarios"].values()))
            original = first_scenario["evidence_files"][0]["path"]
            real_parent = (root / original).parent
            alias_parent = root / "evidence-alias"
            try:
                alias_parent.symlink_to(real_parent, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            first_scenario["evidence_files"][0]["path"] = f"evidence-alias/{Path(original).name}"
            raw = json.dumps(bundle, sort_keys=True).encode()
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)
            self.assertIn("must not traverse a symbolic link", str(raised.exception))


    def test_upload_and_app_signing_certificates_are_distinct_identities(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            raw = json.dumps(bundle, sort_keys=True).encode()
            summary = MODULE.validate_bundle(bundle, source_bytes=raw, evidence_base=root)
            self.assertEqual(UPLOAD_CERT_SHA, summary.upload_certificate_sha256)
            self.assertEqual(APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)
            self.assertNotEqual(summary.upload_certificate_sha256, summary.app_signing_certificate_sha256)

    def test_session_must_match_delivered_app_signing_certificate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            bundle["sessions"][0]["build"]["app_signing_certificate_sha256"] = "5" * 64
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.validate_bundle(bundle, source_bytes=json.dumps(bundle).encode(), evidence_base=root)
            self.assertIn("does not match store delivery", str(raised.exception))

    def test_candidate_hashes_reject_uppercase_hex_encodings(self) -> None:
        candidate = dict(valid_bundle()["candidate"])
        candidate["artifact_sha256"] = candidate["artifact_sha256"].upper()
        with self.assertRaisesRegex(MODULE.EvidenceError, "lowercase 64-hex"):
            MODULE._validate_candidate(candidate)

    def test_legacy_ambiguous_certificate_fields_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            bundle["candidate"]["certificate_sha256"] = bundle["candidate"].pop("upload_certificate_sha256")
            with self.assertRaises(MODULE.EvidenceError) as raised:
                MODULE.validate_bundle(bundle, source_bytes=json.dumps(bundle).encode(), evidence_base=root)
            self.assertIn("candidate is missing", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
