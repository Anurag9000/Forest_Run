from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import compile_device_acceptance as compiler

ARTIFACT_BYTES = b"signed forest run candidate\n"
EVIDENCE_BYTES = b"physical evidence\n"
ARTIFACT_SHA = hashlib.sha256(ARTIFACT_BYTES).hexdigest()
EVIDENCE_SHA = hashlib.sha256(EVIDENCE_BYTES).hexdigest()
UPLOAD_CERTIFICATE_SHA = "3" * 64
APP_SIGNING_CERTIFICATE_SHA = "4" * 64
COMMIT_SHA = "1" * 40
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


def captured_build() -> dict:
    return {
        "commit_sha": COMMIT_SHA,
        "artifact_sha256": ARTIFACT_SHA,
        "version_code": 7,
        "app_signing_certificate_sha256": APP_SIGNING_CERTIFICATE_SHA,
        "signed": True,
        "installed_via": "internal_store",
    }


def draft_bundle() -> dict:
    sessions = []
    for index, device_class in enumerate(CLASSES):
        sessions.append(
            {
                "session_id": f"session-{device_class}-{index}",
                "started_at_utc": "2026-08-01T10:00:00Z",
                "completed_at_utc": "2026-08-01T10:20:00Z",
                "duration_seconds": 1200,
                "device": {
                    "class": device_class,
                    "manufacturer": "Example",
                    "model": f"Model-{index}",
                    "build_fingerprint": f"example/{index}:15/release",
                    "sdk": 35,
                    "ram_mb": 4096,
                    "refresh_hz": 120 if device_class == "high_refresh_phone" else 60,
                    "width_px": 2400,
                    "height_px": 1080,
                    "density_dpi": 420,
                    "tablet": device_class == "tablet",
                    "cutout": device_class == "cutout_phone",
                },
                "build": captured_build(),
                "scenarios": {
                    scenario: {
                        "passed": True,
                        "evidence_files": [
                            f"evidence/{device_class}/{scenario}-{index}.json"
                        ],
                    }
                    for scenario in SCENARIOS
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
        )
    return {
        "candidate": {
            "repository": "Anurag9000/Forest_Run",
            "branch": "main",
            "commit_sha": COMMIT_SHA,
            "application_id": "com.anurag9000.forestrun",
            "version_code": 7,
            "artifact_path": "artifact/app-release.aab",
            "signed": True,
            "upload_certificate_sha256": UPLOAD_CERTIFICATE_SHA,
            "store_delivery": {
                "track": "internal",
                "installed": True,
                "package_name": "com.anurag9000.forestrun",
                "version_code": 7,
                "artifact_sha256": ARTIFACT_SHA,
                "app_signing_certificate_sha256": APP_SIGNING_CERTIFICATE_SHA,
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
        "sessions": sessions,
        "approvals": {
            "visual": "approved",
            "metadata": "approved",
            "privacy": "approved",
            "data_safety": "approved",
            "content_rating": "approved",
            "target_audience": "approved",
            "store_policy": "approved",
            "reviewers": ["release-owner", "independent-reviewer"],
            "reviewed_at_utc": "2026-08-01T11:30:00Z",
        },
    }


def materialize(root: Path, draft: dict, *, omit_first: bool = False) -> None:
    artifact = root / draft["candidate"]["artifact_path"]
    artifact.parent.mkdir(parents=True, exist_ok=True)
    artifact.write_bytes(ARTIFACT_BYTES)
    first = True
    for session in draft["sessions"]:
        for result in session["scenarios"].values():
            for relative in result["evidence_files"]:
                if omit_first and first:
                    first = False
                    continue
                first = False
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(EVIDENCE_BYTES)


class CompileDeviceAcceptanceTest(unittest.TestCase):
    def test_compile_hashes_files_without_rewriting_captured_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            original_store = dict(draft["candidate"]["store_delivery"])
            original_builds = [dict(session["build"]) for session in draft["sessions"]]
            materialize(root, draft)
            compiled, summary = compiler.compile_bundle(
                draft,
                base_dir=root,
                generated_at_utc="2026-08-01T12:00:00Z",
            )
            self.assertEqual(ARTIFACT_SHA, compiled["candidate"]["artifact_sha256"])
            self.assertEqual(original_store, compiled["candidate"]["store_delivery"])
            self.assertEqual(35, summary.evidence_file_count)
            for index, session in enumerate(compiled["sessions"]):
                self.assertEqual(original_builds[index], session["build"])
                for result in session["scenarios"].values():
                    self.assertEqual(EVIDENCE_SHA, result["evidence_files"][0]["sha256"])

    def test_missing_artifact_or_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            with self.assertRaisesRegex(compiler.CompilationError, "candidate artifact is missing"):
                compiler.compile_bundle(draft, base_dir=root)
            materialize(root, draft, omit_first=True)
            with self.assertRaisesRegex(compiler.CompilationError, "evidence file is missing"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_draft_evidence_must_be_plain_safe_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            draft["sessions"][0]["scenarios"]["all_entities"]["evidence_files"] = [
                {"path": "stale.json", "sha256": "9" * 64}
            ]
            with self.assertRaisesRegex(compiler.CompilationError, "relative path string"):
                compiler.compile_bundle(draft, base_dir=root)
            draft = draft_bundle()
            draft["sessions"][0]["scenarios"]["all_entities"]["evidence_files"] = [
                "../escape.json"
            ]
            with self.assertRaisesRegex(compiler.CompilationError, "safe relative path"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compiler_rejects_failed_metrics_and_captured_identity_mismatches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            draft["sessions"][0]["performance"]["p95_frame_ms"] = 40.0
            draft["sessions"][0]["performance"]["p99_frame_ms"] = 45.0
            with self.assertRaisesRegex(compiler.CompilationError, "exceeds max_p95"):
                compiler.compile_bundle(draft, base_dir=root)

            draft = draft_bundle()
            materialize(root, draft)
            draft["candidate"]["store_delivery"]["artifact_sha256"] = "9" * 64
            with self.assertRaisesRegex(compiler.CompilationError, "store_delivery.artifact_sha256"):
                compiler.compile_bundle(draft, base_dir=root)

            draft = draft_bundle()
            materialize(root, draft)
            draft["sessions"][0]["build"]["commit_sha"] = "2" * 40
            with self.assertRaisesRegex(compiler.CompilationError, "build.commit_sha"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_missing_captured_session_build_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            del draft["sessions"][0]["build"]
            with self.assertRaisesRegex(compiler.CompilationError, r"sessions\[0\]\.build"):
                compiler.compile_bundle(draft, base_dir=root)

    def test_compile_file_publishes_manifest_and_summary_transactionally(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            draft_path = root / "draft.json"
            output_path = root / "device-acceptance.json"
            summary_path = root / "device-acceptance-summary.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            result = compiler.compile_file(
                draft_path,
                output_path,
                summary_path=summary_path,
                generated_at_utc="2026-08-01T12:00:00Z",
            )
            self.assertEqual(5, result.session_count)
            self.assertEqual("valid", json.loads(summary_path.read_text())["status"])
            self.assertFalse(any(root.glob(".device-acceptance-*")))

    def test_publication_failure_restores_prior_manifest_and_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft = draft_bundle()
            materialize(root, draft)
            draft_path = root / "draft.json"
            output_path = root / "device-acceptance.json"
            summary_path = root / "device-acceptance-summary.json"
            draft_path.write_text(json.dumps(draft), encoding="utf-8")
            output_path.write_text("old manifest\n", encoding="utf-8")
            summary_path.write_text("old summary\n", encoding="utf-8")
            original_replace = compiler._replace_path

            def fail_second_publish(source: Path, destination: Path) -> None:
                if source.name == "staged-1.json":
                    raise OSError("simulated interrupted publish")
                original_replace(source, destination)

            with mock.patch.object(
                compiler,
                "_replace_path",
                side_effect=fail_second_publish,
            ):
                with self.assertRaisesRegex(compiler.CompilationError, "could not publish"):
                    compiler.compile_file(
                        draft_path,
                        output_path,
                        summary_path=summary_path,
                        generated_at_utc="2026-08-01T12:00:00Z",
                    )
            self.assertEqual("old manifest\n", output_path.read_text())
            self.assertEqual("old summary\n", summary_path.read_text())
            self.assertFalse(any(root.glob(".device-acceptance-*")))

    def test_outputs_are_distinct_and_share_draft_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            draft_path = root / "draft.json"
            draft_path.write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(compiler.CompilationError, "share the draft directory"):
                compiler.compile_file(draft_path, root / "nested" / "manifest.json")
            with self.assertRaisesRegex(compiler.CompilationError, "must not overwrite the draft"):
                compiler.compile_file(draft_path, draft_path)
            output_path = root / "manifest.json"
            with self.assertRaisesRegex(compiler.CompilationError, "must not overwrite"):
                compiler.compile_file(
                    draft_path,
                    output_path,
                    summary_path=output_path,
                )


if __name__ == "__main__":
    unittest.main()
