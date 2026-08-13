from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import evaluate_input_latency_profiles as latency


class InputLatencyProfileEvaluatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.report = {
            "schemaVersion": 1,
            "measurementKind": "app_touch_to_posted_frame",
            "scenario": "INPUT_GESTURES",
            "durationMs": 12000,
            "manufacturer": "Example",
            "model": "Phone",
            "apiLevel": 35,
            "refreshRateHz": 120.0,
            "injectedActions": 40,
            "sampledActions": 40,
            "droppedActions": 0,
            "p50TouchToDecisionNs": 12000000,
            "p95TouchToDecisionNs": 70000000,
            "p99TouchToDecisionNs": 80000000,
            "p50DecisionToResponseNs": 100000,
            "p95DecisionToResponseNs": 400000,
            "p99DecisionToResponseNs": 800000,
            "p50ResponseToRenderNs": 5000000,
            "p95ResponseToRenderNs": 9000000,
            "p99ResponseToRenderNs": 12000000,
            "p50TouchToRenderNs": 18000000,
            "p95TouchToRenderNs": 79000000,
            "p99TouchToRenderNs": 92000000,
        }
        self.profile = latency.ThresholdProfile(
            name="example-120hz",
            manufacturer="Example",
            model="Phone",
            min_refresh_rate_hz=110.0,
            max_refresh_rate_hz=130.0,
            min_sampled_actions=40,
            max_dropped_action_ratio=0.0,
            max_p95_touch_to_decision_ns=75000000,
            max_p95_decision_to_response_ns=1000000,
            max_p95_response_to_render_ns=12000000,
            max_p95_touch_to_render_ns=90000000,
        )

    def test_passing_report_has_no_violations(self) -> None:
        result = latency.evaluate_report(Path("latency.json"), self.report, (self.profile,))
        self.assertTrue(result.passed)
        self.assertEqual((), result.violations)

    def test_every_measured_limit_is_enforced(self) -> None:
        strict = latency.ThresholdProfile(
            **{
                **self.profile.__dict__,
                "min_sampled_actions": 41,
                "max_dropped_action_ratio": 0.01,
                "max_p95_touch_to_decision_ns": 69000000,
                "max_p95_decision_to_response_ns": 399999,
                "max_p95_response_to_render_ns": 8999999,
                "max_p95_touch_to_render_ns": 78999999,
            }
        )
        report = dict(self.report, droppedActions=1)
        result = latency.evaluate_report(Path("latency.json"), report, (strict,))
        self.assertFalse(result.passed)
        self.assertEqual(6, len(result.violations))

    def test_wrong_measurement_kind_is_rejected(self) -> None:
        report = dict(self.report, measurementKind="touch_to_photon")
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "measurementKind"):
            latency.validate_report(report)

    def test_impossible_sample_accounting_is_rejected(self) -> None:
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "sampledActions"):
            latency.validate_report(dict(self.report, sampledActions=41))
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "sampled\+dropped"):
            latency.validate_report(dict(self.report, sampledActions=39, droppedActions=0))

    def test_percentiles_and_total_relationships_are_rejected_when_impossible(self) -> None:
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "percentiles"):
            latency.validate_report(dict(self.report, p95TouchToRenderNs=100, p99TouchToRenderNs=99))
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "TouchToDecision"):
            latency.validate_report(dict(self.report, p95TouchToRenderNs=60000000, p99TouchToRenderNs=92000000))

    def test_exact_profile_beats_wildcard_and_equal_specificity_is_ambiguous(self) -> None:
        wildcard = latency.ThresholdProfile(
            **{
                **self.profile.__dict__,
                "name": "wildcard",
                "manufacturer": "*",
                "model": "*",
                "min_refresh_rate_hz": None,
                "max_refresh_rate_hz": None,
            }
        )
        self.assertEqual(
            "example-120hz",
            latency.select_profile(self.report, (wildcard, self.profile)).name,
        )
        duplicate = latency.ThresholdProfile(**{**self.profile.__dict__, "name": "duplicate"})
        with self.assertRaisesRegex(latency.InputLatencyConfigurationError, "ambiguous"):
            latency.select_profile(self.report, (self.profile, duplicate))

    def test_threshold_manifest_is_strict_json_and_requires_all_limits(self) -> None:
        manifest = {
            "schemaVersion": 1,
            "profiles": [
                {
                    "name": "example",
                    "manufacturer": "Example",
                    "model": "Phone",
                    "minRefreshRateHz": 110,
                    "maxRefreshRateHz": 130,
                    "minSampledActions": 40,
                    "maxDroppedActionRatio": 0.0,
                    "maxP95TouchToDecisionNs": 75000000,
                    "maxP95DecisionToResponseNs": 1000000,
                    "maxP95ResponseToRenderNs": 12000000,
                    "maxP95TouchToRenderNs": 90000000,
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary, "thresholds.json")
            path.write_text(json.dumps(manifest), encoding="utf-8")
            profiles = latency.load_thresholds(path)
            self.assertEqual("example", profiles[0].name)

            path.write_text('{"schemaVersion":1,"schemaVersion":1,"profiles":[]}', encoding="utf-8")
            with self.assertRaises(latency.InputLatencyConfigurationError):
                latency.load_thresholds(path)

    def test_refresh_rate_must_be_physical_and_bounded(self) -> None:
        for value in (0.0, -1.0, 241.0, float("nan"), float("inf")):
            with self.subTest(value=value):
                with self.assertRaises(latency.InputLatencyConfigurationError):
                    latency.validate_report(dict(self.report, refreshRateHz=value))


if __name__ == "__main__":
    unittest.main()
