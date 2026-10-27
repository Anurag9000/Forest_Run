from __future__ import annotations

import json
import hashlib
from unittest.mock import patch
import os

import tempfile
import unittest
from pathlib import Path
from PIL import Image

import curate_store_screenshots as curator
import stable_file_reader as stable_files
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

    def test_difference_hash_reads_luminance_bytes_and_preserves_bit_order(self) -> None:
        descending = Image.new("L", (9, 8))
        for y in range(8):
            for x in range(9):
                descending.putpixel((x, y), 255 - x * 20)

        self.assertEqual((1 << 64) - 1, curator.difference_hash(descending))

    def write_valid_png(self, path: Path) -> None:
        image = Image.effect_noise((1200, 800), 70).convert("RGB")
        image.save(path, format="PNG")

    def test_inspect_image_reads_valid_png_and_binds_exact_bytes(self) -> None:
        path = self.root / "raw.png"
        self.write_valid_png(path)

        facts = curator.inspect_image(path, allow_dark_bars=False)

        self.assertEqual((1200, 800), (facts.width, facts.height))
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), facts.sha256)
        self.assertGreaterEqual(facts.luma_stddev, curator.MIN_LUMA_STDDEV)

    def test_inspect_image_rejects_file_and_parent_symlinks(self) -> None:
        target = self.root / "outside.png"
        self.write_valid_png(target)
        file_alias = self.root / "raw.png"
        try:
            file_alias.symlink_to(target)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")

        with self.assertRaisesRegex(SystemExit, "must not be a symbolic link"):
            curator.inspect_image(file_alias, allow_dark_bars=False)

        real_parent = self.root / "real-raw"
        real_parent.mkdir()
        self.write_valid_png(real_parent / "raw.png")
        parent_alias = self.root / "raw-alias"
        try:
            parent_alias.symlink_to(real_parent, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")

        with self.assertRaisesRegex(SystemExit, "must not traverse a symbolic link"):
            curator.inspect_image(parent_alias / "raw.png", allow_dark_bars=False)

    def test_inspect_image_rejects_missing_empty_oversized_nonfile_and_corrupt_inputs(self) -> None:
        with self.assertRaisesRegex(SystemExit, "Missing raw screenshot"):
            curator.inspect_image(self.root / "missing.png", allow_dark_bars=False)

        empty = self.root / "empty.png"
        empty.write_bytes(b"")
        with self.assertRaisesRegex(SystemExit, "is empty"):
            curator.inspect_image(empty, allow_dark_bars=False)

        directory = self.root / "directory.png"
        directory.mkdir()
        with self.assertRaisesRegex(SystemExit, "regular file"):
            curator.inspect_image(directory, allow_dark_bars=False)

        oversized = self.root / "oversized.png"
        with oversized.open("wb") as stream:
            stream.truncate(curator.MAX_FILE_BYTES + 1)
        with self.assertRaisesRegex(SystemExit, "exceeds"):
            curator.inspect_image(oversized, allow_dark_bars=False)

        corrupt = self.root / "corrupt.png"
        corrupt.write_bytes(b"not a png")
        with self.assertRaisesRegex(SystemExit, "Unreadable or corrupt PNG"):
            curator.inspect_image(corrupt, allow_dark_bars=False)

    def test_inspect_image_enforces_size_orientation_blank_and_dark_bar_gates(self) -> None:
        tiny = self.root / "tiny.png"
        Image.new("RGB", (320, 200), (20, 100, 30)).save(tiny)
        with self.assertRaisesRegex(SystemExit, "too small"):
            curator.inspect_image(tiny, allow_dark_bars=False)

        portrait = self.root / "portrait.png"
        self.write_valid_png(portrait)
        with Image.open(portrait) as image:
            image.rotate(90, expand=True).save(portrait)
        with self.assertRaisesRegex(SystemExit, "must be landscape"):
            curator.inspect_image(portrait, allow_dark_bars=False)

        oversized = self.root / "too-wide.png"
        Image.new("RGB", (curator.MAX_WIDTH + 1, curator.MIN_HEIGHT), (20, 100, 30)).save(oversized)
        with self.assertRaisesRegex(SystemExit, "too large"):
            curator.inspect_image(oversized, allow_dark_bars=False)

        blank = self.root / "blank.png"
        Image.new("RGB", (1200, 800), (100, 100, 100)).save(blank)
        with self.assertRaisesRegex(SystemExit, "nearly uniform"):
            curator.inspect_image(blank, allow_dark_bars=False)

        dark_bars = self.root / "dark-bars.png"
        image = Image.effect_noise((1200, 800), 70).convert("RGB")
        image.paste((0, 0, 0), (0, 0, 1200, 20))
        image.save(dark_bars)
        with self.assertRaisesRegex(SystemExit, "black system-bar band"):
            curator.inspect_image(dark_bars, allow_dark_bars=False)
        self.assertEqual(
            (1200, 800),
            (
                curator.inspect_image(dark_bars, allow_dark_bars=True).width,
                curator.inspect_image(dark_bars, allow_dark_bars=True).height,
            ),
        )

    def test_inspect_image_detects_same_file_mutation_during_descriptor_read(self) -> None:
        path = self.root / "race.png"
        self.write_valid_png(path)
        original_bytes = path.read_bytes()
        real_read = curator.os.read
        calls = 0

        def mutate_after_first_read(descriptor: int, size: int) -> bytes:
            nonlocal calls
            result = real_read(descriptor, size)
            calls += 1
            if calls == 1:
                changed = bytearray(original_bytes)
                changed[-1] ^= 1
                path.write_bytes(changed)
            return result

        with patch.object(curator.os, "read", mutate_after_first_read):
            with self.assertRaisesRegex(SystemExit, "changed while being read"):
                curator.inspect_image(path, allow_dark_bars=False)

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
        for index, item in enumerate(items):
            (curator.RAW_DIR / item["raw_file"]).write_bytes(f"raw-image-{index}".encode())

    def install_fake_inspection(self, items, fail_scenario: str | None = None) -> None:
        facts_by_file = {
            item["raw_file"]: curator.ImageFacts(
                width=1920,
                height=1080,
                sha256=hashlib.sha256(
                    (curator.RAW_DIR / item["raw_file"]).read_bytes()
                ).hexdigest(),
                perceptual_hash=0 if index == 0 else (1 << 64) - 1,
                luma_stddev=20.0,
            )
            for index, item in enumerate(items)
        }
        for item in items:
            digest = facts_by_file[item["raw_file"]].sha256
            if "sha256" in item:
                item["sha256"] = digest
            scenario = item["scenario"]
            if scenario == fail_scenario:
                scenario = "INVALID_SCENARIO"
            evidence = self.evidence(item["raw_file"], scenario, digest)
            payload = {
                "schemaVersion": 1,
                "rawFile": evidence.raw_file,
                "scenario": evidence.scenario,
                "runMode": evidence.run_mode,
                "readinessMarker": evidence.readiness_marker,
                "candidateSha": evidence.candidate_sha,
                "apkSha256": evidence.apk_sha256,
                "deviceSerial": evidence.device_serial,
                "packageName": evidence.package_name,
                "activityName": evidence.activity_name,
                "settleSeconds": evidence.settle_seconds,
                "capturedAtUtc": evidence.captured_at_utc,
                "imageSha256": evidence.image_sha256,
                "width": evidence.width,
                "height": evidence.height,
            }
            (curator.RAW_DIR / item["raw_file"]).with_suffix(".capture.json").write_text(
                json.dumps(payload), encoding="utf-8"
            )

        def inspect(path: Path, allow_dark_bars: bool):
            self.assertIsInstance(allow_dark_bars, bool)
            return facts_by_file[path.name]

        curator.inspect_image = inspect
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

        with self.assertRaisesRegex(SystemExit, "scenario mismatch"):
            curator.curate(items)

        self.assertEqual(
            b"accepted",
            (curator.FINAL_DIR / "accepted.png").read_bytes(),
        )
        self.assertFalse(curator.STAGING_DIR.exists())
        self.assertFalse(curator.BACKUP_DIR.exists())

    def test_curation_stages_the_exact_bytes_that_passed_validation(self) -> None:
        items = curator.validate_manifest(self.manifest())
        self.prepare_raw_files(items)
        self.install_fake_inspection(items)
        original_raw = {
            item["raw_file"]: (curator.RAW_DIR / item["raw_file"]).read_bytes()
            for item in items
        }
        original_sidecars = {
            item["raw_file"]: (curator.RAW_DIR / item["raw_file"]).with_suffix(
                ".capture.json"
            ).read_bytes()
            for item in items
        }
        real_write_staged_file = curator._write_staged_file
        mutated = False

        def mutate_inputs_during_staging(directory_fd: int, name: str, content: bytes) -> None:
            nonlocal mutated
            if not mutated:
                mutated = True
                for item in items:
                    raw = curator.RAW_DIR / item["raw_file"]
                    raw.write_bytes(b"mutated after validation")
                    raw.with_suffix(".capture.json").write_bytes(b"mutated sidecar")
            return real_write_staged_file(directory_fd, name, content)

        with patch.object(curator, "_write_staged_file", mutate_inputs_during_staging):
            curator.curate(items)

        for item in items:
            final = curator.FINAL_DIR / item["final_file"]
            self.assertEqual(original_raw[item["raw_file"]], final.read_bytes())
            self.assertEqual(
                original_sidecars[item["raw_file"]],
                final.with_suffix(".capture.json").read_bytes(),
            )

    def test_staging_parent_swap_cannot_redirect_curated_screenshots(self) -> None:
        items = curator.validate_manifest(self.manifest())
        self.prepare_raw_files(items)
        self.install_fake_inspection(items)
        output_parent = self.root / "output-parent"
        output_parent.mkdir()
        curator.FINAL_DIR = output_parent / "final"
        curator.STAGING_DIR = output_parent / ".final-staging"
        curator.BACKUP_DIR = output_parent / ".final-backup"
        curator.FINAL_DIR.mkdir()
        (curator.FINAL_DIR / "accepted.png").write_bytes(b"accepted")
        moved = self.root / "output-parent-moved"
        external = self.root / "external"
        external.mkdir()
        real_write = curator._write_staged_file
        swapped = False

        def swap_parent_during_staging(directory_fd: int, name: str, content: bytes) -> None:
            nonlocal swapped
            if not swapped:
                output_parent.rename(moved)
                output_parent.symlink_to(external, target_is_directory=True)
                swapped = True
            real_write(directory_fd, name, content)

        with patch.object(curator, "_write_staged_file", swap_parent_during_staging):
            with self.assertRaisesRegex(SystemExit, "symbolic link or non-directory"):
                curator.curate(items)

        self.assertTrue(swapped)
        self.assertTrue(output_parent.is_symlink())
        self.assertEqual(b"accepted", (moved / "final" / "accepted.png").read_bytes())
        self.assertFalse((moved / ".final-staging").exists())
        self.assertEqual([], list(external.iterdir()))

    def test_sidecar_mutation_between_parse_and_staging_is_rejected(self) -> None:
        items = curator.validate_manifest(self.manifest())
        self.prepare_raw_files(items)
        self.install_fake_inspection(items)
        old = curator.FINAL_DIR
        old.mkdir()
        (old / "accepted.png").write_bytes(b"accepted")
        real_load = curator.load_capture_evidence

        def load_then_mutate(path: Path, **expected):
            evidence = real_load(path, **expected)
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["candidateSha"] = "c" * 40
            path.write_text(json.dumps(payload), encoding="utf-8")
            return evidence

        with patch.object(curator, "load_capture_evidence", load_then_mutate):
            with self.assertRaisesRegex(SystemExit, "changed while being validated"):
                curator.curate(items)

        self.assertEqual(b"accepted", (old / "accepted.png").read_bytes())
        self.assertFalse(curator.STAGING_DIR.exists())

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

    def test_interrupted_prior_publication_backup_is_recovered_before_retry(self) -> None:
        curator.BACKUP_DIR.mkdir()
        (curator.BACKUP_DIR / "previously-accepted.png").write_bytes(b"accepted")
        curator.STAGING_DIR.mkdir()
        (curator.STAGING_DIR / "candidate.png").write_bytes(b"candidate")
        original_rename = stable_files._rename_noreplace

        def fail_candidate_publish(source, target, *args, **kwargs):
            if source == curator.STAGING_DIR.name and target == curator.FINAL_DIR.name:
                raise OSError("injected candidate publication failure")
            return original_rename(source, target, **kwargs)

        with patch.object(stable_files, "_rename_noreplace", fail_candidate_publish):
            with self.assertRaisesRegex(SystemExit, "injected candidate publication failure"):
                curator._publish_staged_directory()

        self.assertEqual(
            b"accepted",
            (curator.FINAL_DIR / "previously-accepted.png").read_bytes(),
        )
        self.assertFalse(curator.BACKUP_DIR.exists())

    def test_existing_final_and_backup_are_preserved_and_fail_closed(self) -> None:
        curator.FINAL_DIR.mkdir()
        curator.BACKUP_DIR.mkdir()
        (curator.FINAL_DIR / "current.png").write_bytes(b"current")
        (curator.BACKUP_DIR / "previous.png").write_bytes(b"previous")

        with self.assertRaisesRegex(SystemExit, "backup already exists"):
            curator._publish_staged_directory()

        self.assertEqual(b"current", (curator.FINAL_DIR / "current.png").read_bytes())
        self.assertEqual(b"previous", (curator.BACKUP_DIR / "previous.png").read_bytes())

    def test_non_directory_output_or_backup_is_preserved_and_rejected(self) -> None:
        curator.FINAL_DIR.write_bytes(b"do not move or delete")
        curator.STAGING_DIR.mkdir()

        with self.assertRaisesRegex(SystemExit, "destination must be a directory"):
            curator._publish_staged_directory()

        self.assertEqual(b"do not move or delete", curator.FINAL_DIR.read_bytes())
        self.assertFalse(curator.BACKUP_DIR.exists())

        curator.FINAL_DIR.unlink()
        curator.BACKUP_DIR.write_bytes(b"backup file")
        with self.assertRaisesRegex(SystemExit, "backup path must be a directory"):
            curator._publish_staged_directory()
        self.assertEqual(b"backup file", curator.BACKUP_DIR.read_bytes())


    def test_manifest_reader_admits_valid_and_rejects_ambiguous_evidence(self) -> None:
        with patch.object(curator, "MANIFEST_PATH", self.root / "curation_manifest.json"):
            original = json.dumps(self.manifest())
            curator.MANIFEST_PATH.write_text(original, encoding="utf-8")
            self.assertEqual(self.manifest(), curator.load_manifest())

            variants = (
                (
                    original.replace(
                        '"screenshots":', '"extra": 1, "extra": 2, "screenshots":', 1
                    ),
                    "duplicate JSON object key",
                ),
                (
                    original.replace('"screenshots":', '"probe": NaN, "screenshots":', 1),
                    "non-finite",
                ),
                (" " * (curator.MAX_MANIFEST_BYTES + 1), "between"),
            )
            for text, error in variants:
                with self.subTest(error=error):
                    curator.MANIFEST_PATH.write_text(text, encoding="utf-8")
                    with self.assertRaisesRegex(SystemExit, error):
                        curator.load_manifest()


if __name__ == "__main__":
    unittest.main()
