from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import aggregate_device_acceptance as aggregate
from acceptance_test_support import materialize_traced_bundle
from test_validate_device_acceptance import materialize_files, valid_bundle


class AggregateDeviceAcceptanceTest(unittest.TestCase):
    @staticmethod
    def write_bundle(
        root: Path,
        bundle: dict,
        name: str = "manifest.json",
        *,
        traced: bool = True,
        trace_scenario: str = "CACTUS_READ",
    ) -> Path:
        root.mkdir(parents=True, exist_ok=True)
        if traced:
            materialize_traced_bundle(
                root,
                bundle,
                scenario_name=trace_scenario,
            )
        else:
            materialize_files(root, bundle)
        manifest = root / name
        manifest.write_text(
            json.dumps(bundle, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return manifest

    def test_valid_manifest_is_summarized_by_device_class_and_trace_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_bundle(root, valid_bundle())

            result = aggregate.aggregate(manifest)
            summary = result["candidate_summary"]

            self.assertEqual("valid", result["status"])
            self.assertEqual(5, summary["session_count"])
            self.assertEqual(35, summary["evidence_file_count"])
            self.assertEqual(5, summary["device_class_count"])
            self.assertEqual(1, summary["trace_count"])
            self.assertEqual("CACTUS_READ", summary["trace_contracts"][0]["scenario"])
            self.assertEqual(64, len(summary["trace_contracts"][0]["trace_contract_sha256"]))
            self.assertEqual(16.0, summary["global_metrics"]["p95_frame_ms"]["mean"])
            self.assertEqual(4.0, summary["threshold_headroom"]["p95_frame_ms"])
            self.assertEqual(
                1,
                summary["by_device_class"]["older_phone"]["physical_device_count"],
            )
            self.assertEqual(
                ["session-older_phone-0"],
                summary["by_device_class"]["older_phone"]["session_ids"],
            )

    def test_baseline_comparison_reports_deltas_without_inventing_tolerance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline_bundle = valid_bundle()
            candidate_bundle = valid_bundle()
            candidate_bundle["candidate"]["commit_sha"] = "2" * 40
            for session in candidate_bundle["sessions"]:
                session["build"]["commit_sha"] = "2" * 40
                session["performance"]["p95_frame_ms"] = 17.0
                session["performance"]["peak_pss_mb"] = 225.0

            baseline = self.write_bundle(root / "baseline", baseline_bundle)
            candidate = self.write_bundle(root / "candidate", candidate_bundle)

            result = aggregate.aggregate(candidate, baseline_path=baseline)
            comparison = result["baseline_comparison"]

            self.assertEqual("1" * 40, comparison["baseline_commit_sha"])
            self.assertEqual("CACTUS_READ", comparison["trace_contracts"][0]["scenario"])
            self.assertEqual(
                1.0,
                comparison["global_metric_deltas"]["p95_frame_ms"]["mean_delta"],
            )
            self.assertEqual(
                5.0,
                comparison["by_device_class"]["tablet"]["peak_pss_mb"]["maximum_delta"],
            )
            self.assertIn("does not invent", comparison["interpretation"])

    def test_trace_free_manifest_is_rejected_by_direct_python_core(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_bundle(root, valid_bundle(), traced=False)

            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "references no deterministic scenario trace",
            ):
                aggregate.aggregate(manifest)

    def test_candidate_and_baseline_trace_contract_sets_must_match(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = self.write_bundle(
                root / "baseline",
                valid_bundle(),
                trace_scenario="CACTUS_READ",
            )
            candidate_bundle = valid_bundle()
            candidate_bundle["candidate"]["commit_sha"] = "2" * 40
            for session in candidate_bundle["sessions"]:
                session["build"]["commit_sha"] = "2" * 40
            candidate = self.write_bundle(
                root / "candidate",
                candidate_bundle,
                trace_scenario="CAT_KINDNESS",
            )

            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "trace-contract sets differ",
            ):
                aggregate.aggregate(candidate, baseline_path=baseline)

    def test_tampered_evidence_is_rejected_before_aggregation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            manifest = self.write_bundle(root, bundle)
            first = next(
                evidence
                for session in bundle["sessions"]
                for result in session["scenarios"].values()
                for evidence in result["evidence_files"]
            )
            (root / first["path"]).write_bytes(b"tampered\n")

            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "evidence digest mismatch",
            ):
                aggregate.aggregate(manifest)

    def test_baseline_matrix_mismatch_is_rejected(self) -> None:
        candidate = {
            "candidate": {"commit_sha": "2" * 40, "artifact_sha256": "4" * 64},
            "global_metrics": {
                metric: {"mean": 1.0, "maximum": 1.0}
                for metric in aggregate.METRICS
            },
            "trace_contracts": [],
            "by_device_class": {
                "older_phone": {
                    "metrics": {
                        metric: {"mean": 1.0, "maximum": 1.0}
                        for metric in aggregate.METRICS
                    }
                }
            },
        }
        baseline = {
            "candidate": {"commit_sha": "1" * 40, "artifact_sha256": "3" * 64},
            "global_metrics": candidate["global_metrics"],
            "trace_contracts": [],
            "by_device_class": {
                **candidate["by_device_class"],
                "tablet": candidate["by_device_class"]["older_phone"],
            },
        }

        with self.assertRaisesRegex(
            aggregate.AggregationError,
            "device-class matrices differ",
        ):
            aggregate._compare(candidate, baseline)

    def test_cli_publishes_strict_finite_json_atomically(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_bundle(root, valid_bundle())
            output = root / "reports" / "aggregate.json"

            self.assertEqual(
                0,
                aggregate.main([str(manifest), "--output", str(output)]),
            )
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("valid", payload["status"])
            self.assertEqual(1, payload["candidate_summary"]["trace_count"])
            self.assertNotIn("NaN", output.read_text(encoding="utf-8"))
            self.assertNotIn("Infinity", output.read_text(encoding="utf-8"))
            self.assertFalse(any(output.parent.glob(f".{output.name}.*.tmp")))

    def test_invalid_manifest_returns_nonzero_without_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "invalid.json"
            output = root / "aggregate.json"
            manifest.write_text('{"schema_version": 1, "schema_version": 1}')

            self.assertEqual(
                1,
                aggregate.main([str(manifest), "--output", str(output)]),
            )
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
