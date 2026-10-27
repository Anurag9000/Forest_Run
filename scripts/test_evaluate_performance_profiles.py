import json
import tempfile
from unittest.mock import patch
import unittest
from pathlib import Path

from evaluate_performance_profiles import (
    ConfigurationError,
    ThresholdProfile,
    _read_json,
    evaluate_report,
    load_thresholds,
    select_profile,
)
import evaluate_performance_profiles as evaluate_module



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

    def test_threshold_schema_revision_must_be_integer(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "thresholds.json"
            for invalid in (True, False, 1.0, "1", None):
                with self.subTest(value=invalid):
                    path.write_text(
                        json.dumps({"schemaVersion": invalid, "profiles": []}),
                        encoding="utf-8",
                    )
                    with self.assertRaisesRegex(ConfigurationError, "schemaVersion"):
                        load_thresholds(path)

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

    def test_performance_json_rejects_duplicate_metrics_and_nonstandard_numbers(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory, "profile.json")
            cases = (
                '{"sampledFrames":0,"sampledFrames":600}',
                '{"p95ProcessingNs":NaN}',
                '{"p95ProcessingNs":1e9999}',
            )
            for payload in cases:
                with self.subTest(payload=payload):
                    path.write_text(payload, encoding="utf-8")
                    with self.assertRaisesRegex(ConfigurationError, "invalid JSON"):
                        _read_json(path)

    def test_performance_json_is_bounded_before_loading(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory, "thresholds.json")
            path.write_text('{"schemaVersion":1}' + " " * 200, encoding="utf-8")
            with patch("evaluate_performance_profiles.MAX_PROFILE_JSON_BYTES", 64):
                with self.assertRaisesRegex(ConfigurationError, "between 1 and 64 bytes"):
                    load_thresholds(path)

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

class PerformanceProfileBoundaryTest(PerformanceProfileEvaluatorTest):
    def test_unrepresentable_finite_numbers_raise_configuration_error(self):
        enormous = 10 ** 1000
        with self.assertRaisesRegex(ConfigurationError, "must be finite"):
            evaluate_report(Path("huge-refresh.json"), dict(self.report, refreshRateHz=enormous), (self.profile(),))
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "thresholds.json"
            manifest = {
                "schemaVersion": 1,
                "profiles": [{
                    "name": "huge-refresh", "manufacturer": "*", "model": "*", "scenario": "*",
                    "minRefreshRateHz": enormous,
                    "minSampledFrames": 0, "maxP95ProcessingNs": 1, "maxP99ProcessingNs": 1,
                    "maxSlowFrameRatio": 1, "maxUsedHeapBytes": 0,
                }],
            }
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ConfigurationError, "invalid JSON.*digit safety limit"):
                load_thresholds(path)
        with self.assertRaisesRegex(ConfigurationError, "must be finite"):
            evaluate_module._finite_number({"value": enormous}, "value", "profile")

    def test_threshold_profiles_reject_reversed_bounds_ratios_and_percentile_limits(self):
        base = {
            "schemaVersion": 1,
            "profiles": [{
                "name": "bounded", "manufacturer": "*", "model": "*", "scenario": "*",
                "minSampledFrames": 0, "maxP95ProcessingNs": 3, "maxP99ProcessingNs": 4,
                "maxSlowFrameRatio": 0.5, "maxUsedHeapBytes": 10,
            }],
        }
        mutations = (
            ({"minRefreshRateHz": 61, "maxRefreshRateHz": 60}, "reversed"),
            ({"maxSlowFrameRatio": 1.01}, "between 0 and 1"),
            ({"maxP95ProcessingNs": 5}, "cannot exceed p99"),
            ({"minGhostWritesCompleted": -1}, "non-negative integer"),
            ({"maxGhostWriteDurationNs": -0.1}, "non-negative"),
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "thresholds.json"
            for changes, message in mutations:
                profile = dict(base["profiles"][0], **changes)
                path.write_text(json.dumps({"schemaVersion": 1, "profiles": [profile]}), encoding="utf-8")
                with self.subTest(changes=changes), self.assertRaisesRegex(ConfigurationError, message):
                    load_thresholds(path)

    def test_threshold_manifest_rejects_empty_profiles_and_duplicate_names(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "thresholds.json"
            for profiles, message in (([], "non-empty"), ([self.profile(), self.profile()], "unique")):
                if profiles and isinstance(profiles[0], ThresholdProfile):
                    profile_values = [{
                        "name": item.name, "manufacturer": item.manufacturer, "model": item.model,
                        "scenario": item.scenario, "minSampledFrames": item.min_sampled_frames,
                        "maxP95ProcessingNs": item.max_p95_processing_ns,
                        "maxP99ProcessingNs": item.max_p99_processing_ns,
                        "maxSlowFrameRatio": item.max_slow_frame_ratio,
                        "maxUsedHeapBytes": item.max_used_heap_bytes,
                    } for item in profiles]
                else:
                    profile_values = profiles
                path.write_text(json.dumps({"schemaVersion": 1, "profiles": profile_values}), encoding="utf-8")
                with self.assertRaisesRegex(ConfigurationError, message):
                    load_thresholds(path)

    def test_profile_selection_inclusive_refresh_and_case_insensitive_fields(self):
        selected = self.profile(manufacturer="EXAMPLE", model="MIDRANGE ONE", scenario="opening_readability")
        self.assertEqual("midrange-opening", select_profile(self.report, (selected,)).name)
        below = dict(self.report, refreshRateHz=58.99)
        with self.assertRaisesRegex(ConfigurationError, "no threshold profile"):
            select_profile(below, (self.profile(),))
        above = dict(self.report, refreshRateHz=61.01)
        with self.assertRaisesRegex(ConfigurationError, "no threshold profile"):
            select_profile(above, (self.profile(),))

    def test_report_evidence_rejects_nonfinite_consistency_errors(self):
        cases = (
            (dict(self.report, slowFrameRatio=float("nan")), "must be finite"),
            (dict(self.report, sampledFrames=0, totalFrames=10, slowFrames=11), "slowFrames cannot exceed"),
            (dict(self.report, meanProcessingNs=23_000_000), "meanProcessingNs cannot exceed"),
            (dict(self.report, meanRenderNs=23_000_000), "update/render means cannot exceed"),
            (dict(self.report, ghostWritesStarted=3, ghostWritesCompleted=2, ghostWritesFailed=3), "ghostWritesFailed cannot exceed"),
            (dict(self.report, latestGhostWriteDurationNs=2, maximumGhostWriteDurationNs=1), "latestGhostWriteDurationNs cannot exceed"),
        )
        for report, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(ConfigurationError, message):
                evaluate_report(Path("invalid.json"), report, (self.profile(),))

    def test_report_discovery_deduplicates_files_and_run_reports_all_outcomes(self):
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reports = root / "reports"
            reports.mkdir()
            good = reports / "good.json"
            bad = reports / "bad.json"
            good.write_text(json.dumps(self.report), encoding="utf-8")
            bad_report = dict(self.report, p95ProcessingNs=14_000_000)
            bad.write_text(json.dumps(bad_report), encoding="utf-8")
            self.assertEqual([bad.resolve(), good.resolve()], evaluate_module._expand_report_paths([reports, good]))
            thresholds = root / "thresholds.json"
            thresholds.write_text(json.dumps({"schemaVersion": 1, "profiles": [{
                "name": "test", "manufacturer": "*", "model": "*", "scenario": "*",
                "minSampledFrames": 0, "maxP95ProcessingNs": 13_000_000,
                "maxP99ProcessingNs": 16_000_000, "maxSlowFrameRatio": 1,
                "maxUsedHeapBytes": 100_000_000,
            }]}), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(1, evaluate_module.run(thresholds, [reports]))
            self.assertIn("PASS", output.getvalue())
            self.assertIn("FAIL", output.getvalue())
            self.assertIn("Evaluated 2 report(s): 1 passed, 1 failed", output.getvalue())

    def test_report_discovery_rejects_missing_and_empty_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(ConfigurationError, "does not exist"):
                evaluate_module._expand_report_paths([root / "missing"])
            with self.assertRaisesRegex(ConfigurationError, "no JSON performance reports"):
                evaluate_module._expand_report_paths([root])

    def test_profile_primitives_and_malformed_profile_objects(self):
        with self.assertRaisesRegex(ConfigurationError, "non-blank"):
            evaluate_module._required_string({"name": " "}, "name", "profile")
        with self.assertRaisesRegex(ConfigurationError, "must be numeric"):
            evaluate_module._finite_number({"value": True}, "value", "profile")
        with self.assertRaisesRegex(ConfigurationError, "non-negative integer"):
            evaluate_module._non_negative_int({"value": -1}, "value", "profile")
        with self.assertRaisesRegex(ConfigurationError, "positive integer"):
            evaluate_module._positive_int({"value": 0}, "value", "profile")
        self.assertIsNone(evaluate_module._optional_non_negative_int({}, "x", "p"))
        self.assertIsNone(evaluate_module._optional_non_negative_number({"x": None}, "x", "p"))
        with self.assertRaisesRegex(ConfigurationError, "non-negative"):
            evaluate_module._optional_non_negative_number({"x": -0.5}, "x", "p")
        with self.assertRaisesRegex(ConfigurationError, "must be an object"):
            evaluate_module._parse_profile([], 0)
        with self.assertRaisesRegex(ConfigurationError, "minSampledFrames is required"):
            evaluate_module._parse_profile({}, 0)

    def test_profile_manifest_and_report_json_file_failures(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            missing = root / "missing.json"
            with self.assertRaisesRegex(ConfigurationError, "invalid JSON"):
                _read_json(missing)
            path = root / "thresholds.json"
            for payload, message in (
                ("[]", "invalid JSON"),
                (json.dumps({"schemaVersion": 1, "profiles": []}), "non-empty array"),
                (json.dumps({"schemaVersion": 1, "profiles": [None]}), "must be an object"),
                (json.dumps({"schemaVersion": 1, "profiles": [{"minSampledFrames": 1,
                    "maxP95ProcessingNs": 1, "maxP99ProcessingNs": 1,
                    "maxSlowFrameRatio": 1, "maxUsedHeapBytes": 1}]}), "name must be a non-blank"),
            ):
                path.write_text(payload, encoding="utf-8")
                with self.subTest(message=message), self.assertRaisesRegex(ConfigurationError, message):
                    load_thresholds(path)

    def test_main_returns_configuration_status_and_script_entrypoint(self):
        import contextlib
        import io
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            thresholds = root / "thresholds.json"
            thresholds.write_text(json.dumps({"schemaVersion": 1, "profiles": [{
                "name": "fallback", "manufacturer": "*", "model": "*", "scenario": "*",
                "minSampledFrames": 0, "maxP95ProcessingNs": 13_000_000,
                "maxP99ProcessingNs": 16_000_000, "maxSlowFrameRatio": 1,
                "maxUsedHeapBytes": 100_000_000,
            }]}), encoding="utf-8")
            report = root / "report.json"
            report.write_text(json.dumps(self.report), encoding="utf-8")
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(0, evaluate_module.main(["--thresholds", str(thresholds), str(report)]))
            self.assertIn("PASS", stdout.getvalue())
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                self.assertEqual(2, evaluate_module.main(["--thresholds", str(root / "bad.json"), str(report)]))
            self.assertIn("ERROR:", stderr.getvalue())
            result = subprocess.run([sys.executable, str(Path(evaluate_module.__file__)), "--help"],
                capture_output=True, text=True, check=False)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--thresholds", result.stdout)

    def test_profile_selector_rejects_nonpositive_refresh_and_ratio_out_of_range(self):
        with self.assertRaisesRegex(ConfigurationError, "refreshRateHz must be positive"):
            select_profile(dict(self.report, refreshRateHz=0), (self.profile(),))
        with self.assertRaisesRegex(ConfigurationError, "slowFrameRatio must be between"):
            evaluate_report(Path("invalid-ratio.json"), dict(self.report, slowFrameRatio=1.1), (self.profile(),))
