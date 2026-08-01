from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import validate_manifest_scenario_traces as manifest_traces
from test_validate_device_acceptance import (
    ARTIFACT_SHA,
    SHA,
    materialize_files,
    valid_bundle,
)


ROOT = Path(__file__).resolve().parents[1]


def trace_payload(*, commit: str = SHA, artifact: str = ARTIFACT_SHA) -> dict:
    return {
        "schema_version": 1,
        "candidate_commit_sha": commit,
        "artifact_sha256": artifact,
        "captured_at_utc_ms": 1_775_000_000_000,
        "scenario": "CACTUS_READ",
        "event_count": 2,
        "events": [
            {
                "sequence": 0,
                "scheduled_at_micros": 3_180_000,
                "dispatched_at_micros": 3_200_000,
                "lateness_micros": 20_000,
                "action": "HOLD_JUMP_START",
            },
            {
                "sequence": 1,
                "scheduled_at_micros": 3_480_000,
                "dispatched_at_micros": 3_500_000,
                "lateness_micros": 20_000,
                "action": "HOLD_JUMP_END",
            },
        ],
    }


def attach_trace(root: Path, bundle: dict, payload: dict) -> Path:
    raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
    entry = bundle["sessions"][0]["scenarios"]["ordinary_play_15m"][
        "evidence_files"
    ][0]
    entry["path"] = "evidence/older_phone/scenario-trace-cactus_read.json"
    entry["sha256"] = hashlib.sha256(raw).hexdigest()
    materialize_files(root, bundle)
    trace_path = root / entry["path"]
    trace_path.write_bytes(raw)
    manifest = root / "device-acceptance.json"
    manifest.write_text(
        json.dumps(bundle, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


class ValidateManifestScenarioTracesTest(unittest.TestCase):
    def test_valid_referenced_trace_is_bound_to_candidate_and_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = attach_trace(root, valid_bundle(), trace_payload())

            summary = manifest_traces.validate_manifest_traces(
                manifest,
                repository_root=ROOT,
                require_at_least_one=True,
            )

            self.assertEqual("valid", summary["status"])
            self.assertEqual(1, summary["trace_count"])
            self.assertEqual("CACTUS_READ", summary["traces"][0]["trace_scenario"])
            self.assertEqual("ordinary_play_15m", summary["traces"][0]["acceptance_scenario"])

    def test_structurally_valid_but_wrong_candidate_trace_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = attach_trace(
                root,
                valid_bundle(),
                trace_payload(commit="9" * 40),
            )

            with self.assertRaisesRegex(
                manifest_traces.ManifestTraceError,
                "does not match the expected candidate",
            ):
                manifest_traces.validate_manifest_traces(
                    manifest,
                    repository_root=ROOT,
                )

    def test_wrong_artifact_trace_is_rejected_even_when_manifest_hash_matches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = attach_trace(
                root,
                valid_bundle(),
                trace_payload(artifact="8" * 64),
            )

            with self.assertRaisesRegex(
                manifest_traces.ManifestTraceError,
                "does not match the expected artifact",
            ):
                manifest_traces.validate_manifest_traces(
                    manifest,
                    repository_root=ROOT,
                )

    def test_non_trace_evidence_is_ignored_unless_trace_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            manifest = root / "device-acceptance.json"
            manifest.write_text(json.dumps(bundle), encoding="utf-8")

            summary = manifest_traces.validate_manifest_traces(
                manifest,
                repository_root=ROOT,
            )
            self.assertEqual(0, summary["trace_count"])

            with self.assertRaisesRegex(
                manifest_traces.ManifestTraceError,
                "references no deterministic scenario trace",
            ):
                manifest_traces.validate_manifest_traces(
                    manifest,
                    repository_root=ROOT,
                    require_at_least_one=True,
                )

    def test_cli_is_fail_closed_for_invalid_trace(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = attach_trace(
                root,
                valid_bundle(),
                trace_payload(commit="7" * 40),
            )
            self.assertEqual(
                1,
                manifest_traces.main(
                    [str(manifest), "--root", str(ROOT)]
                ),
            )


if __name__ == "__main__":
    unittest.main()
