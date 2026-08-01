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
            "durationMs": 20_000,
            "manufacturer": "Example",
            "model": "Midrange One",
            "apiLevel": 35,
            "refreshRateHz": 60.0,
            "sampledFrames": 600,
            "totalFrames": 1_200,
            "slowFrames": 12,
            "slowFrameRatio": 0.01,
            "frameBudgetNs": 16_666_666,
            "meanUpdateNs": 2_000_000,
            "meanRenderNs": 4_000_000,
            "meanProcessingNs": 6_500_000,
            "p50ProcessingNs": 6_000_000,
            "p95ProcessingNs": 12_000_000,
            "p99ProcessingNs": 15_000_000,
            "maximumProcessingNs": 22_000_000,
            "usedHeapBytes": 48_000_000,
            "maxHeapBytes": 256_000_000,
            "currentEntities": 2,
            "peakEntities": 5,
            "currentSeedOrbs": 1,
            "peakSeedOrbs": 2,
            "currentParticles": 40,
            "peakParticles": 120,
            "currentDialogueBubbles": 1,
            "peakDialogueBubbles": 3,
            "currentFlavorTexts": 1,
            "peakFlavorTexts": 4,
            "ghostWritesStarted": 0,
            "ghostWritesCompleted": 0,
            "ghostWritesFailed": 0,
            "latestGhostFrameCount": 0,
            "maximumGhostFrameCount": 0,
            "latestGhostWriteDurationNs": 0,
            "maximumGhostWriteDurationNs": 0,
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
            ghostWritesStarted=2,
            ghostWritesCompleted=2,
            ghostWritesFailed=0,
            latestGhostFrameCount=36_000,
            maximumGhostFrameCount=36_000,
            latestGhostWriteDurationNs=18_000_000,
            maximumGhostWriteDurationNs=18_000_000,
        )
        profile = self.profile(
            name="ghost-max",
            scenario="GHOST_PERSISTENCE_MAX",
            min_ghost_writes_completed=2,
            max_ghost_write_failures=0,
            min_maximum_ghost_frame_count=36_000,
            max_ghost_write_duration_ns=20_000_000,
        )

        self.assertTrue(evaluate_report(Path("ghost.json"), report, (profile,)).passed)

        report["ghostWritesCompleted"] = 1
        report["ghostWritesFailed"] = 1
        report["maximumGhostFrameCount"] = 1_000
        report["latestGhostFrameCount"] = 1_000
        report["maximumGhostWriteDurationNs"] = 40_000_000
        report["latestGhostWriteDurationNs"] = 40_000_000
        result = evaluate_report(Path("ghost.json"), report, (profile,))

        self.assertFalse(result.passed)
        self.assertEqual(4, len(result.violations))
        self.assertTrue(any("ghostWritesCompleted" in item for item in result.violations))
        self.assertTrue(any("ghostWritesFailed" in item for item in result.violations))
        self.assertTrue(any("maximumGhostFrameCount" in item for item in result.violations))
        self.assertTrue(any("maximumGhostWriteDurationNs" in item for item in result.violations))

    def test_impossible_frame_count_and_ratio_evidence_is_rejected(self):
        malformed = dict(self.report, sampledFrames=1_201)
        with self.assertRaisesRegex(ConfigurationError, "sampledFrames"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

        malformed = dict(self.report, slowFrames=13)
        with self.assertRaisesRegex(ConfigurationError, "slowFrameRatio"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

    def test_impossible_timing_heap_and_workload_evidence_is_rejected(self):
        malformed = dict(self.report, p50ProcessingNs=13_000_000)
        with self.assertRaisesRegex(ConfigurationError, "percentiles"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

        malformed = dict(self.report, usedHeapBytes=300_000_000)
        with self.assertRaisesRegex(ConfigurationError, "usedHeapBytes"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

        malformed = dict(self.report, currentParticles=121)
        with self.assertRaisesRegex(ConfigurationError, "currentParticles"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

    def test_impossible_ghost_publication_is_rejected_before_thresholds(self):
        malformed = dict(
            self.report,
            ghostWritesStarted=1,
            ghostWritesCompleted=2,
        )
        with self.assertRaisesRegex(ConfigurationError, "ghostWritesCompleted"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

        malformed = dict(
            self.report,
            latestGhostFrameCount=2,
            maximumGhostFrameCount=1,
        )
        with self.assertRaisesRegex(ConfigurationError, "latestGhostFrameCount"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

    def test_zero_refresh_rate_is_rejected_as_unusable_hardware_evidence(self):
        malformed = dict(self.report, refreshRateHz=0.0)

        with self.assertRaisesRegex(ConfigurationError, "refreshRateHz"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

    def test_missing_current_schema_metric_is_configuration_error(self):
        malformed = dict(self.report)
        del malformed["maxHeapBytes"]

        with self.assertRaisesRegex(ConfigurationError, "maxHeapBytes"):
            evaluate_report(Path("opening.json"), malformed, (self.profile(),))

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
