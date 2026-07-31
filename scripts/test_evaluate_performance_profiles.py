import json
import tempfile
import unittest
from pathlib import Path

from evaluate_performance_profiles import (
    ConfigurationError,
    ThresholdProfile,
    evaluate_report,
    load_thresholds,
    select_profile,
)


class PerformanceProfileEvaluatorTest(unittest.TestCase):
    def setUp(self):
        self.report = {
            "scenario": "OPENING_READABILITY",
            "manufacturer": "Example",
            "model": "Midrange One",
            "refreshRateHz": 60.0,
            "sampledFrames": 600,
            "p95ProcessingNs": 12_000_000,
            "p99ProcessingNs": 15_000_000,
            "maximumProcessingNs": 22_000_000,
            "slowFrameRatio": 0.01,
            "usedHeapBytes": 48_000_000,
        }

    def profile(self, **overrides):
        values = {
            "name": "midrange-opening",
            "manufacturer": "Example",
            "model": "Midrange One",
            "scenario": "OPENING_READABILITY",
            "min_refresh_rate_hz": 59.0,
            "max_refresh_rate_hz": 61.0,
            "min_sampled_frames": 300,
            "max_p95_processing_ns": 13_000_000,
            "max_p99_processing_ns": 16_000_000,
            "max_slow_frame_ratio": 0.02,
            "max_used_heap_bytes": 64_000_000,
            "max_maximum_processing_ns": 30_000_000,
        }
        values.update(overrides)
        return ThresholdProfile(**values)

    def test_passing_report_has_no_violations(self):
        result = evaluate_report(
            Path("opening.json"),
            self.report,
            (self.profile(),),
        )

        self.assertTrue(result.passed)
        self.assertEqual((), result.violations)
        self.assertEqual("midrange-opening", result.profile_name)

    def test_all_exceeded_limits_are_reported(self):
        strict = self.profile(
            min_sampled_frames=700,
            max_p95_processing_ns=11_000_000,
            max_p99_processing_ns=14_000_000,
            max_slow_frame_ratio=0.005,
            max_used_heap_bytes=40_000_000,
            max_maximum_processing_ns=20_000_000,
        )

        result = evaluate_report(Path("opening.json"), self.report, (strict,))

        self.assertFalse(result.passed)
        self.assertEqual(6, len(result.violations))
        self.assertTrue(any("sampledFrames" in item for item in result.violations))
        self.assertTrue(any("p95ProcessingNs" in item for item in result.violations))
        self.assertTrue(any("p99ProcessingNs" in item for item in result.violations))
        self.assertTrue(any("slowFrameRatio" in item for item in result.violations))
        self.assertTrue(any("usedHeapBytes" in item for item in result.violations))
        self.assertTrue(any("maximumProcessingNs" in item for item in result.violations))

    def test_ghost_persistence_limits_are_evaluated_independently(self):
        report = dict(
            self.report,
            scenario="GHOST_PERSISTENCE_MAX",
            ghostWritesCompleted=1,
            ghostWritesFailed=0,
            maximumGhostFrameCount=36_000,
            maximumGhostWriteDurationNs=18_000_000,
        )
        profile = self.profile(
            name="ghost-max",
            scenario="GHOST_PERSISTENCE_MAX",
            min_ghost_writes_completed=1,
            max_ghost_write_failures=0,
            min_maximum_ghost_frame_count=36_000,
            max_ghost_write_duration_ns=20_000_000,
        )

        self.assertTrue(evaluate_report(Path("ghost.json"), report, (profile,)).passed)

        report["ghostWritesCompleted"] = 0
        report["ghostWritesFailed"] = 1
        report["maximumGhostFrameCount"] = 1_000
        report["maximumGhostWriteDurationNs"] = 40_000_000
        result = evaluate_report(Path("ghost.json"), report, (profile,))

        self.assertFalse(result.passed)
        self.assertEqual(4, len(result.violations))
        self.assertTrue(any("ghostWritesCompleted" in item for item in result.violations))
        self.assertTrue(any("ghostWritesFailed" in item for item in result.violations))
        self.assertTrue(any("maximumGhostFrameCount" in item for item in result.violations))
        self.assertTrue(any("maximumGhostWriteDurationNs" in item for item in result.violations))

    def test_missing_ghost_metric_is_configuration_error_when_required(self):
        profile = self.profile(
            min_ghost_writes_completed=1,
        )

        with self.assertRaisesRegex(ConfigurationError, "ghostWritesCompleted"):
            evaluate_report(Path("opening.json"), self.report, (profile,))

    def test_exact_device_profile_outranks_wildcard_profile(self):
        wildcard = self.profile(
            name="fallback",
            manufacturer="*",
            model="*",
            scenario="*",
            min_refresh_rate_hz=None,
            max_refresh_rate_hz=None,
        )
        exact = self.profile(name="exact")

        selected = select_profile(self.report, (wildcard, exact))

        self.assertEqual("exact", selected.name)

    def test_equal_specificity_profiles_are_rejected_as_ambiguous(self):
        first = self.profile(name="first")
        second = self.profile(name="second")

        with self.assertRaisesRegex(ConfigurationError, "ambiguous"):
            select_profile(self.report, (first, second))

    def test_threshold_manifest_requires_measured_core_limits(self):
        manifest = {
            "schemaVersion": 1,
            "profiles": [
                {
                    "name": "incomplete",
                    "manufacturer": "Example",
                    "model": "Midrange One",
                    "scenario": "OPENING_READABILITY",
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory, "thresholds.json")
            path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(ConfigurationError, "minSampledFrames"):
                load_thresholds(path)


if __name__ == "__main__":
    unittest.main()
