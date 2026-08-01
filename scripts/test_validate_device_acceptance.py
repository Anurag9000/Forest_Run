from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

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
CERT_SHA = "3" * 64
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
            "certificate_sha256": CERT_SHA,
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
        "schema_version": 1,
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
            "certificate_sha256": CERT_SHA,
            "store_delivery": {
                "track": "internal",
                "installed": True,
                "package_name": "com.anurag9000.forestrun",
                "version_code": 7,
                "artifact_sha256": ARTIFACT_SHA,
                "certificate_sha256": CERT_SHA,
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

    def test_invalid_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.json"
            manifest.write_text("{")
            self.assertEqual(1, MODULE.main([str(manifest)]))


if __name__ == "__main__":
    unittest.main()
