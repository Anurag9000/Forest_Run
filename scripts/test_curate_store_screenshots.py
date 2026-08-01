from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import curate_store_screenshots as curator
from screenshot_capture_evidence import CaptureEvidence


class ScreenshotCurationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.original_paths = (
            curator.RAW_DIR,
            curator.FINAL_DIR,
            curator.STAGING_DIR,
            curator.BACKUP_DIR,
        )
        self.original_inspect = curator.inspect_image
        self.original_load = curator.load_capture_evidence
        self.original_identity = curator.require_same_capture_identity
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        curator.RAW_DIR = self.root / "raw"
        curator.FINAL_DIR = self.root / "final"
        curator.STAGING_DIR = self.root / ".final-staging"
        curator.BACKUP_DIR = self.root / ".final-backup"
        curator.RAW_DIR.mkdir()

    def tearDown(self) -> None:
        (
            curator.RAW_DIR,
            curator.FINAL_DIR,
            curator.STAGING_DIR,
            curator.BACKUP_DIR,
        ) = self.original_paths
        curator.inspect_image = self.original_inspect
        curator.load_capture_evidence = self.original_load
        curator.require_same_capture_identity = self.original_identity
        self.temporary_directory.cleanup()

    def manifest(self):
        return {
            "screenshots": [
                {
                    "order": 1,
                    "raw_file": "01-opening.png",
                    "final_file": "01-opening.png",
                    "scenario": "OPENING_READABILITY",
                    "title": "Opening Readability",
                    "purpose": "Show the readable first lane.",
                },
                {
                    "order": 2,
                    "raw_file": "02-bloom.png",
                    "final_file": "02-bloom.png",
                    "scenario": "BLOOM_SHOWCASE",
                    "title": "Bloom Showcase",
                    "purpose": "Show Bloom without obscuring hazards.",
                    "allow_dark_bars": False,
                    "sha256": "2" * 64,
                },
            ]
        }

    def evidence(self, raw_file: str, scenario: str, image_hash: str):
        return CaptureEvidence(
            raw_file=raw_file,
            scenario=scenario,
            run_mode="SCREENSHOT_CAPTURE",
            readiness_marker=(
                f"FOREST_RUN_SCENARIO_READY scenario={scenario} "
                "mode=SCREENSHOT_CAPTURE"
            ),
            candidate_sha="a" * 40,
            apk_sha256="b" * 64,
            device_serial="device-1",
            package_name="com.anurag9000.forestrun.debug",
            activity_name="com.anurag9000.forestrun.MainActivity",
            settle_seconds=2.0,
            captured_at_utc="2026-08-01T06:00:00+00:00",
            image_sha256=image_hash,
            width=1920,
            height=1080,
        )

    def prepare_raw_files(self, items) -> None:
        for item in items:
            (curator.RAW_DIR / item["raw_file"]).write_bytes(b"raw-image")
            (curator.RAW_DIR / item["raw_file"]).with_suffix(
                ".capture.json"
            ).write_text("{}", encoding="utf-8")

    def install_fake_inspection(self, items, fail_scenario: str | None = None) -> None:
        facts_by_file = {
            item["raw_file"]: curator.ImageFacts(
                width=1920,
                height=1080,
                sha256=str(index + 1) * 64,
                perceptual_hash=0 if index == 0 else (1 << 64) - 1,
                luma_stddev=20.0,
            )
            for index, item in enumerate(items)
        }

        def inspect(path: Path, allow_dark_bars: bool):
            self.assertIsInstance(allow_dark_bars, bool)
            return facts_by_file[path.name]

        def load(path: Path, **expected):
            scenario = expected["expected_scenario"]
            if scenario == fail_scenario:
                raise curator.CaptureEvidenceError("forced evidence failure")
            return self.evidence(
                expected["expected_raw_file"],
                scenario,
                expected["expected_image_sha256"],
            )

        curator.inspect_image = inspect
        curator.load_capture_evidence = load
        curator.require_same_capture_identity = lambda baseline, candidate, path: None

    def test_manifest_is_normalized_and_typed(self) -> None:
        items = curator.validate_manifest(self.manifest())

        self.assertEqual([1, 2], [item["order"] for item in items])
        self.assertFalse(items[0]["allow_dark_bars"])
        self.assertFalse(items[1]["allow_dark_bars"])
        self.assertEqual("2" * 64, items[1]["sha256"])

    def test_manifest_rejects_non_boolean_override_and_casefold_duplicates(self) -> None:
        manifest = self.manifest()
        manifest["screenshots"][0]["allow_dark_bars"] = "false"
        with self.assertRaisesRegex(SystemExit, "allow_dark_bars"):
            curator.validate_manifest(manifest)

        manifest = self.manifest()
        manifest["screenshots"][1]["title"] = "opening readability"
        with self.assertRaisesRegex(SystemExit, "Duplicate screenshot title"):
            curator.validate_manifest(manifest)

    def test_manifest_rejects_invalid_order_scenario_and_digest(self) -> None:
        manifest = self.manifest()
        manifest["screenshots"][1]["order"] = 3
        with self.assertRaisesRegex(SystemExit, "order must equal 2"):
            curator.validate_manifest(manifest)

        manifest = self.manifest()
        manifest["screenshots"][0]["scenario"] = "opening readability"
        with self.assertRaisesRegex(SystemExit, "uppercase scenario token"):
            curator.validate_manifest(manifest)

        manifest = self.manifest()
        manifest["screenshots"][1]["sha256"] = "not-a-digest"
        with self.assertRaisesRegex(SystemExit, "SHA-256"):
            curator.validate_manifest(manifest)

    def test_validation_failure_preserves_existing_final_set(self) -> None:
        items = curator.validate_manifest(self.manifest())
        self.prepare_raw_files(items)
        curator.FINAL_DIR.mkdir()
        (curator.FINAL_DIR / "accepted.png").write_bytes(b"accepted")
        self.install_fake_inspection(items, fail_scenario="BLOOM_SHOWCASE")

        with self.assertRaisesRegex(SystemExit, "forced evidence failure"):
            curator.curate(items)

        self.assertEqual(
            b"accepted",
            (curator.FINAL_DIR / "accepted.png").read_bytes(),
        )
        self.assertFalse(curator.STAGING_DIR.exists())
        self.assertFalse(curator.BACKUP_DIR.exists())

    def test_success_replaces_final_set_only_after_all_validation(self) -> None:
        manifest = self.manifest()
        manifest["screenshots"][1].pop("sha256")
        items = curator.validate_manifest(manifest)
        self.prepare_raw_files(items)
        curator.FINAL_DIR.mkdir()
        (curator.FINAL_DIR / "old.png").write_bytes(b"old")
        self.install_fake_inspection(items)

        curated = curator.curate(items)

        self.assertEqual(2, len(curated))
        self.assertFalse((curator.FINAL_DIR / "old.png").exists())
        for item in items:
            self.assertTrue((curator.FINAL_DIR / item["final_file"]).is_file())
            self.assertTrue(
                (curator.FINAL_DIR / item["final_file"])
                .with_suffix(".capture.json")
                .is_file()
            )
        self.assertFalse(curator.STAGING_DIR.exists())
        self.assertFalse(curator.BACKUP_DIR.exists())


if __name__ == "__main__":
    unittest.main()
