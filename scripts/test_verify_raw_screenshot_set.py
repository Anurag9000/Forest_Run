from __future__ import annotations

import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path

from verify_raw_screenshot_set import (
    RawScreenshotSetError,
    verify_raw_screenshot_set,
)


class RawScreenshotSetVerifierTest(unittest.TestCase):
    candidate_sha = "a" * 40
    apk_sha256 = "b" * 64

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.raw_dir = self.root / "raw"
        self.raw_dir.mkdir()
        self.manifest_path = self.root / "curation_manifest.json"
        self.items = [
            {
                "order": 1,
                "raw_file": "01-opening.png",
                "final_file": "01-opening.png",
                "scenario": "OPENING_READABILITY",
                "title": "Opening",
                "purpose": "Opening readability.",
            },
            {
                "order": 2,
                "raw_file": "02-bloom.png",
                "final_file": "02-bloom.png",
                "scenario": "BLOOM_SHOWCASE",
                "title": "Bloom",
                "purpose": "Bloom showcase.",
            },
        ]
        self.manifest_path.write_text(
            json.dumps({"screenshots": self.items}),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def create_png(self, name: str, marker: bytes) -> str:
        content = (
            b"\x89PNG\r\n\x1a\n"
            + b"\x00\x00\x00\rIHDR"
            + struct.pack(">II", 1920, 1080)
            + marker
        )
        (self.raw_dir / name).write_bytes(content)
        return hashlib.sha256(content).hexdigest()

    def write_sidecar(
        self,
        item,
        image_hash: str,
        captured_at: str,
        **overrides,
    ) -> None:
        values = {
            "schemaVersion": 1,
            "rawFile": item["raw_file"],
            "scenario": item["scenario"],
            "runMode": "SCREENSHOT_CAPTURE",
            "readinessMarker": (
                f"FOREST_RUN_SCENARIO_READY scenario={item['scenario']} "
                "mode=SCREENSHOT_CAPTURE"
            ),
            "candidateSha": self.candidate_sha,
            "apkSha256": self.apk_sha256,
            "deviceSerial": "device-1",
            "packageName": "com.anurag9000.forestrun.debug",
            "activityName": "com.anurag9000.forestrun.MainActivity",
            "settleSeconds": 2.0,
            "capturedAtUtc": captured_at,
            "imageSha256": image_hash,
            "width": 1920,
            "height": 1080,
        }
        values.update(overrides)
        (self.raw_dir / item["raw_file"]).with_suffix(
            ".capture.json"
        ).write_text(json.dumps(values), encoding="utf-8")

    def write_session(self, **overrides) -> None:
        values = {
            "schemaVersion": 1,
            "candidateSha": self.candidate_sha,
            "originMainSha": self.candidate_sha,
            "apkSha256": self.apk_sha256,
            "deviceSerial": "device-1",
            "packageName": "com.anurag9000.forestrun.debug",
            "activityName": "com.anurag9000.forestrun.MainActivity",
            "capturedAtUtc": "2026-08-01T06:02:00+00:00",
            "screenshotCount": 2,
        }
        values.update(overrides)
        (self.raw_dir / "capture-session.json").write_text(
            json.dumps(values), encoding="utf-8"
        )

    def create_valid_set(self, duplicate_images: bool = False) -> None:
        first_hash = self.create_png("01-opening.png", b"opening")
        second_hash = self.create_png(
            "02-bloom.png",
            b"opening" if duplicate_images else b"bloom",
        )
        self.write_sidecar(
            self.items[0], first_hash, "2026-08-01T06:00:00+00:00"
        )
        self.write_sidecar(
            self.items[1], second_hash, "2026-08-01T06:01:00+00:00"
        )
        self.write_session()

    def test_valid_raw_capture_session_is_accepted(self) -> None:
        self.create_valid_set()

        result = verify_raw_screenshot_set(
            self.raw_dir,
            self.manifest_path,
            self.candidate_sha,
        )

        self.assertEqual(2, result.count)
        self.assertEqual(self.candidate_sha, result.candidate_sha)
        self.assertEqual(self.candidate_sha, result.origin_main_sha)
        self.assertEqual(self.apk_sha256, result.apk_sha256)
        self.assertEqual("device-1", result.device_serial)

    def test_missing_and_extra_files_are_rejected(self) -> None:
        self.create_valid_set()
        (self.raw_dir / "02-bloom.capture.json").unlink()
        with self.assertRaisesRegex(RawScreenshotSetError, "missing"):
            verify_raw_screenshot_set(
                self.raw_dir, self.manifest_path, self.candidate_sha
            )

        self.write_sidecar(
            self.items[1],
            hashlib.sha256(
                (self.raw_dir / "02-bloom.png").read_bytes()
            ).hexdigest(),
            "2026-08-01T06:01:00+00:00",
        )
        (self.raw_dir / "stale.txt").write_text("stale", encoding="utf-8")
        with self.assertRaisesRegex(RawScreenshotSetError, "extra"):
            verify_raw_screenshot_set(
                self.raw_dir, self.manifest_path, self.candidate_sha
            )

    def test_duplicate_images_are_rejected(self) -> None:
        self.create_valid_set(duplicate_images=True)

        with self.assertRaisesRegex(RawScreenshotSetError, "duplicate image hashes"):
            verify_raw_screenshot_set(
                self.raw_dir, self.manifest_path, self.candidate_sha
            )

    def test_session_candidate_origin_count_and_identity_must_match(self) -> None:
        cases = (
            ({"originMainSha": "c" * 40}, "candidate/origin mismatch"),
            ({"screenshotCount": 1}, "screenshotCount"),
            ({"apkSha256": "d" * 64}, "apkSha256 mismatch"),
            ({"deviceSerial": "device-2"}, "deviceSerial mismatch"),
        )
        for overrides, message in cases:
            with self.subTest(overrides=overrides):
                for path in self.raw_dir.iterdir():
                    path.unlink()
                self.create_valid_set()
                self.write_session(**overrides)
                with self.assertRaisesRegex(RawScreenshotSetError, message):
                    verify_raw_screenshot_set(
                        self.raw_dir,
                        self.manifest_path,
                        self.candidate_sha,
                    )

    def test_session_time_must_be_utc_and_not_predate_captures(self) -> None:
        self.create_valid_set()
        self.write_session(capturedAtUtc="2026-08-01T11:31:00+05:30")
        with self.assertRaisesRegex(RawScreenshotSetError, "must use UTC"):
            verify_raw_screenshot_set(
                self.raw_dir, self.manifest_path, self.candidate_sha
            )

        self.write_session(capturedAtUtc="2026-08-01T05:59:00+00:00")
        with self.assertRaisesRegex(RawScreenshotSetError, "predates"):
            verify_raw_screenshot_set(
                self.raw_dir, self.manifest_path, self.candidate_sha
            )


if __name__ == "__main__":
    unittest.main()
