from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import compare_visual_regression as visual


class VisualRegressionComparatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.baseline = self.root / "baseline"
        self.candidate = self.root / "candidate"
        self.baseline.mkdir()
        self.candidate.mkdir()
        self.manifest = self.root / "curation_manifest.json"
        self.manifest.write_text(
            json.dumps(
                {
                    "screenshots": [
                        {
                            "order": 1,
                            "raw_file": "01_FOREST.png",
                            "final_file": "01_forest.png",
                            "scenario": "FOREST",
                            "title": "Forest",
                            "purpose": "Deterministic forest frame",
                        },
                        {
                            "order": 2,
                            "raw_file": "02_BLOOM.png",
                            "final_file": "02_bloom.png",
                            "scenario": "BLOOM",
                            "title": "Bloom",
                            "purpose": "Deterministic Bloom frame",
                        },
                    ]
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_png(
        self,
        directory: Path,
        name: str,
        *,
        size: tuple[int, int] = (10, 10),
        color: tuple[int, int, int] = (0, 0, 0),
    ) -> Path:
        path = directory / name
        Image.new("RGB", size, color).save(path, format="PNG")
        return path

    def test_identical_manifest_set_passes_exact_thresholds(self) -> None:
        for name in ("01_forest.png", "02_bloom.png"):
            self.write_png(self.baseline, name)
            self.write_png(self.candidate, name)

        result = visual.compare_manifest_set(
            manifest_path=self.manifest,
            baseline_dir=self.baseline,
            candidate_dir=self.candidate,
            filename_field="final_file",
            per_channel_tolerance=0,
            max_mean_absolute_channel_delta=0.0,
            max_changed_pixel_ratio=0.0,
            max_p95_pixel_max_channel_delta=0,
        )

        self.assertEqual("valid", result.status)
        self.assertEqual(2, result.comparison_count)
        self.assertEqual((), result.failed_scenarios)
        self.assertEqual(0.0, result.maximum_changed_pixel_ratio)

    def test_small_local_change_is_measured_and_can_be_tolerated(self) -> None:
        baseline = self.write_png(self.baseline, "frame.png")
        candidate = self.write_png(self.candidate, "frame.png")
        with Image.open(candidate) as source:
            modified = source.convert("RGB")
        modified.putpixel((0, 0), (12, 0, 0))
        modified.save(candidate, format="PNG")

        result = visual.compare_image(
            baseline_path=baseline,
            candidate_path=candidate,
            scenario="LOCAL_CHANGE",
            per_channel_tolerance=4,
            max_mean_absolute_channel_delta=0.1,
            max_changed_pixel_ratio=0.01,
            max_p95_pixel_max_channel_delta=0,
        )

        self.assertTrue(result.passed)
        self.assertAlmostEqual(0.04, result.mean_absolute_channel_delta)
        self.assertAlmostEqual(0.01, result.changed_pixel_ratio)
        self.assertEqual(0, result.p95_pixel_max_channel_delta)

    def test_same_local_change_fails_a_stricter_changed_pixel_budget(self) -> None:
        baseline = self.write_png(self.baseline, "frame.png")
        candidate = self.write_png(self.candidate, "frame.png")
        with Image.open(candidate) as source:
            modified = source.convert("RGB")
        modified.putpixel((0, 0), (12, 0, 0))
        modified.save(candidate, format="PNG")

        result = visual.compare_image(
            baseline_path=baseline,
            candidate_path=candidate,
            scenario="LOCAL_CHANGE",
            per_channel_tolerance=4,
            max_mean_absolute_channel_delta=0.1,
            max_changed_pixel_ratio=0.009,
            max_p95_pixel_max_channel_delta=0,
        )

        self.assertFalse(result.passed)
        self.assertAlmostEqual(0.01, result.changed_pixel_ratio)

    def test_broad_uniform_shift_trips_all_distribution_metrics(self) -> None:
        baseline = self.write_png(self.baseline, "frame.png", color=(20, 20, 20))
        candidate = self.write_png(self.candidate, "frame.png", color=(26, 26, 26))

        result = visual.compare_image(
            baseline_path=baseline,
            candidate_path=candidate,
            scenario="GLOBAL_SHIFT",
            per_channel_tolerance=4,
            max_mean_absolute_channel_delta=1.5,
            max_changed_pixel_ratio=0.01,
            max_p95_pixel_max_channel_delta=4,
        )

        self.assertFalse(result.passed)
        self.assertEqual(6.0, result.mean_absolute_channel_delta)
        self.assertEqual(1.0, result.changed_pixel_ratio)
        self.assertEqual(6, result.p95_pixel_max_channel_delta)

    def test_dimension_mismatch_fails_closed(self) -> None:
        baseline = self.write_png(self.baseline, "frame.png", size=(10, 10))
        candidate = self.write_png(self.candidate, "frame.png", size=(11, 10))

        with self.assertRaisesRegex(visual.VisualRegressionError, "dimensions differ"):
            visual.compare_image(
                baseline_path=baseline,
                candidate_path=candidate,
                scenario="SIZE",
                per_channel_tolerance=4,
                max_mean_absolute_channel_delta=1.5,
                max_changed_pixel_ratio=0.01,
                max_p95_pixel_max_channel_delta=4,
            )

    def test_non_png_fails_closed(self) -> None:
        baseline = self.baseline / "frame.png"
        baseline.write_bytes(b"not a png")
        candidate = self.write_png(self.candidate, "frame.png")

        with self.assertRaises(visual.VisualRegressionError):
            visual.compare_image(
                baseline_path=baseline,
                candidate_path=candidate,
                scenario="MALFORMED",
                per_channel_tolerance=4,
                max_mean_absolute_channel_delta=1.5,
                max_changed_pixel_ratio=0.01,
                max_p95_pixel_max_channel_delta=4,
            )

    def test_final_symlink_is_rejected(self) -> None:
        real = self.write_png(self.baseline, "real.png")
        baseline = self.baseline / "frame.png"
        try:
            baseline.symlink_to(real.name)
        except (OSError, NotImplementedError):
            self.skipTest("symbolic links are unavailable on this platform")
        candidate = self.write_png(self.candidate, "frame.png")

        with self.assertRaisesRegex(visual.VisualRegressionError, "symbolic link"):
            visual.compare_image(
                baseline_path=baseline,
                candidate_path=candidate,
                scenario="SYMLINK",
                per_channel_tolerance=4,
                max_mean_absolute_channel_delta=1.5,
                max_changed_pixel_ratio=0.01,
                max_p95_pixel_max_channel_delta=4,
            )

    def test_invalid_thresholds_are_rejected(self) -> None:
        baseline = self.write_png(self.baseline, "frame.png")
        candidate = self.write_png(self.candidate, "frame.png")
        invalid_cases = (
            {"per_channel_tolerance": -1},
            {"per_channel_tolerance": 256},
            {"max_mean_absolute_channel_delta": float("nan")},
            {"max_changed_pixel_ratio": -0.01},
            {"max_changed_pixel_ratio": 1.01},
            {"max_p95_pixel_max_channel_delta": 256},
        )
        defaults = {
            "per_channel_tolerance": 4,
            "max_mean_absolute_channel_delta": 1.5,
            "max_changed_pixel_ratio": 0.01,
            "max_p95_pixel_max_channel_delta": 4,
        }
        for overrides in invalid_cases:
            with self.subTest(overrides=overrides):
                arguments = {**defaults, **overrides}
                with self.assertRaises(visual.VisualRegressionError):
                    visual.compare_image(
                        baseline_path=baseline,
                        candidate_path=candidate,
                        scenario="INVALID_THRESHOLD",
                        **arguments,
                    )

    def test_cli_status_distinguishes_valid_regression_and_invalid(self) -> None:
        for name in ("01_forest.png", "02_bloom.png"):
            self.write_png(self.baseline, name)
            self.write_png(self.candidate, name)
        output = self.root / "result.json"
        self.assertEqual(
            0,
            visual.main(
                [
                    "--manifest",
                    str(self.manifest),
                    "--baseline-dir",
                    str(self.baseline),
                    "--candidate-dir",
                    str(self.candidate),
                    "--per-channel-tolerance",
                    "0",
                    "--max-mean-absolute-channel-delta",
                    "0",
                    "--max-changed-pixel-ratio",
                    "0",
                    "--max-p95-pixel-max-channel-delta",
                    "0",
                    "--output",
                    str(output),
                ]
            ),
        )
        self.assertEqual("valid", json.loads(output.read_text(encoding="utf-8"))["status"])

        Image.new("RGB", (10, 10), (50, 50, 50)).save(
            self.candidate / "01_forest.png", format="PNG"
        )
        self.assertEqual(
            1,
            visual.main(
                [
                    "--manifest",
                    str(self.manifest),
                    "--baseline-dir",
                    str(self.baseline),
                    "--candidate-dir",
                    str(self.candidate),
                    "--output",
                    str(output),
                ]
            ),
        )
        payload = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual("regression", payload["status"])
        self.assertEqual(["FOREST"], payload["failedScenarios"])

        (self.candidate / "02_bloom.png").unlink()
        self.assertEqual(
            2,
            visual.main(
                [
                    "--manifest",
                    str(self.manifest),
                    "--baseline-dir",
                    str(self.baseline),
                    "--candidate-dir",
                    str(self.candidate),
                    "--output",
                    str(output),
                ]
            ),
        )
        self.assertEqual("invalid", json.loads(output.read_text(encoding="utf-8"))["status"])


if __name__ == "__main__":
    unittest.main()
