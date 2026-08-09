from __future__ import annotations

import copy
import hashlib
import json
import math
import tempfile
import unittest
from pathlib import Path

import validate_device_acceptance_aggregate as validator


def digest(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def distribution(count: int, minimum: float, mean: float, maximum: float) -> dict:
    return {
        "count": count,
        "minimum": minimum,
        "mean": mean,
        "maximum": maximum,
    }


def matrix_sha(by_class: dict) -> str:
    canonical = {
        device_class: {
            "session_count": summary["session_count"],
            "device_profile_ids": summary["device_profile_ids"],
        }
        for device_class, summary in sorted(by_class.items())
    }
    return hashlib.sha256(
        json.dumps(
            canonical,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
            allow_nan=False,
        ).encode("utf-8")
    ).hexdigest()


def valid_report(*, baseline: bool = False) -> dict:
    by_class = {}
    class_values = {}
    for index, device_class in enumerate(sorted(validator.MANDATORY_DEVICE_CLASSES), start=1):
        class_values[device_class] = float(index)
        by_class[device_class] = {
            "session_count": 1,
            "physical_device_count": 1,
            "physical_device_ids": [digest(f"physical:{device_class}")],
            "device_profile_ids": [digest(f"profile:{device_class}")],
            "session_ids": [f"{device_class}-session"],
            "metrics": {
                metric: distribution(1, float(index), float(index), float(index))
                for metric in validator.METRICS
            },
        }

    ordered_values = [class_values[item] for item in sorted(class_values)]
    trace_contracts = [
        {
            "scenario": "CACTUS_READ",
            "scenario_definition_sha256": digest("scenario"),
            "trace_contract_sha256": digest("trace"),
        }
    ]
    report = {
        "schema_version": validator.SCHEMA_VERSION,
        "status": "valid",
        "candidate_summary": {
            "candidate": {
                "commit_sha": "a" * 40,
                "artifact_sha256": digest("artifact"),
                "application_id": validator.CANONICAL_APPLICATION_ID,
                "version_code": 1,
                "upload_certificate_sha256": digest("upload-certificate"),
                "app_signing_certificate_sha256": digest("app-signing-certificate"),
            },
            "session_count": len(by_class),
            "evidence_file_count": 35,
            "device_class_count": len(by_class),
            "comparison_matrix_sha256": matrix_sha(by_class),
            "trace_count": 5,
            "trace_contracts": trace_contracts,
            "duration_seconds": distribution(len(by_class), 900.0, 900.0, 900.0),
            "global_metrics": {
                metric: distribution(
                    len(by_class),
                    min(ordered_values),
                    math.fsum(ordered_values) / len(ordered_values),
                    max(ordered_values),
                )
                for metric in validator.METRICS
            },
            "threshold_headroom": {metric: 1.0 for metric in validator.METRICS},
            "by_device_class": by_class,
        },
    }
    if baseline:
        zero_deltas = {
            metric: {"mean_delta": 0.0, "maximum_delta": 0.0}
            for metric in validator.METRICS
        }
        report["baseline_comparison"] = {
            "baseline_commit_sha": "b" * 40,
            "baseline_artifact_sha256": digest("baseline-artifact"),
            "comparison_matrix_sha256": report["candidate_summary"][
                "comparison_matrix_sha256"
            ],
            "trace_contracts": copy.deepcopy(trace_contracts),
            "global_metric_deltas": copy.deepcopy(zero_deltas),
            "by_device_class": {
                device_class: copy.deepcopy(zero_deltas)
                for device_class in sorted(validator.MANDATORY_DEVICE_CLASSES)
            },
            "interpretation": validator.BASELINE_INTERPRETATION,
        }
    return report


class ValidateDeviceAcceptanceAggregateTest(unittest.TestCase):
    def assert_invalid(self, report: dict, pattern: str) -> None:
        with self.assertRaisesRegex(validator.AggregateValidationError, pattern):
            validator.validate_report(report)

    def test_accepts_candidate_only_and_baseline_reports(self) -> None:
        candidate = validator.validate_report(valid_report())
        self.assertFalse(candidate["baseline_comparison"])
        self.assertEqual(5, candidate["session_count"])
        self.assertEqual(5, candidate["trace_count"])

        baseline = validator.validate_report(valid_report(baseline=True))
        self.assertTrue(baseline["baseline_comparison"])

    def test_rejects_unknown_root_and_candidate_fields(self) -> None:
        root_extra = valid_report()
        root_extra["producer_claim"] = True
        self.assert_invalid(root_extra, "aggregate keys differ")

        candidate_extra = valid_report()
        candidate_extra["candidate_summary"]["candidate"]["branch"] = "main"
        self.assert_invalid(candidate_extra, "candidate.*keys differ")

    def test_rejects_forged_matrix_and_global_distribution(self) -> None:
        forged_matrix = valid_report()
        forged_matrix["candidate_summary"]["comparison_matrix_sha256"] = "0" * 64
        self.assert_invalid(forged_matrix, "does not match the class matrix")

        forged_global = valid_report()
        forged_global["candidate_summary"]["global_metrics"]["p95_frame_ms"][
            "mean"
        ] += 0.25
        self.assert_invalid(forged_global, "weighted class summaries")

    def test_rejects_identifier_order_count_and_class_drift(self) -> None:
        report = valid_report()
        first_class = sorted(validator.MANDATORY_DEVICE_CLASSES)[0]
        summary = report["candidate_summary"]["by_device_class"][first_class]
        summary["physical_device_ids"] = ["f" * 64, "0" * 64]
        summary["physical_device_count"] = 2
        summary["session_count"] = 2
        summary["session_ids"] = ["session-z", "session-a"]
        summary["device_profile_ids"] = ["f" * 64, "0" * 64]
        for metric in validator.METRICS:
            summary["metrics"][metric]["count"] = 2
        report["candidate_summary"]["session_count"] = 6
        report["candidate_summary"]["duration_seconds"]["count"] = 6
        for metric in validator.METRICS:
            report["candidate_summary"]["global_metrics"][metric]["count"] = 6
        self.assert_invalid(report, "must be sorted")

        class_drift = valid_report()
        class_drift["candidate_summary"]["by_device_class"]["desktop"] = (
            class_drift["candidate_summary"]["by_device_class"].pop(first_class)
        )
        self.assert_invalid(class_drift, "mandatory matrix")

    def test_rejects_negative_headroom_and_impossible_trace_counts(self) -> None:
        negative = valid_report()
        negative["candidate_summary"]["threshold_headroom"]["p99_frame_ms"] = -0.01
        self.assert_invalid(negative, "must be >= 0.0")

        trace_overflow = valid_report()
        trace_overflow["candidate_summary"]["trace_count"] = 36
        self.assert_invalid(trace_overflow, "cannot exceed evidence_file_count")

    def test_rejects_baseline_binding_and_semantics_drift(self) -> None:
        matrix = valid_report(baseline=True)
        matrix["baseline_comparison"]["comparison_matrix_sha256"] = "0" * 64
        self.assert_invalid(matrix, "must match candidate_summary")

        contract = valid_report(baseline=True)
        contract["baseline_comparison"]["trace_contracts"][0][
            "trace_contract_sha256"
        ] = digest("different")
        self.assert_invalid(contract, "must exactly match candidate_summary")

        semantics = valid_report(baseline=True)
        semantics["baseline_comparison"]["interpretation"] = "Positive is fine."
        self.assert_invalid(semantics, "frozen semantics")

    def test_file_entrypoint_is_strict_and_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            valid = root / "valid.json"
            valid.write_text(
                json.dumps(valid_report(), indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            self.assertEqual(0, validator.main([str(valid)]))

            duplicate = root / "duplicate.json"
            duplicate.write_text(
                '{"schema_version":1,"schema_version":1}',
                encoding="utf-8",
            )
            self.assertEqual(1, validator.main([str(duplicate)]))

    def test_accepts_actual_aggregate_producer_output(self) -> None:
        import aggregate_device_acceptance as aggregate
        from acceptance_test_support import materialize_traced_bundle
        from test_validate_device_acceptance import valid_bundle

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            materialize_traced_bundle(root, bundle)
            manifest = root / "manifest.json"
            manifest.write_text(
                json.dumps(bundle, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            report = aggregate.aggregate(manifest)
            summary = validator.validate_report(report)
            self.assertEqual(
                report["candidate_summary"]["session_count"],
                summary["session_count"],
            )
            self.assertEqual(
                report["candidate_summary"]["comparison_matrix_sha256"],
                summary["comparison_matrix_sha256"],
            )


if __name__ == "__main__":
    unittest.main()
