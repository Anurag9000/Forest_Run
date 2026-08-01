import datetime as dt
import hashlib
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from finalize_screenshot_capture_session import (
    CaptureSessionFinalizeError,
    finalize_capture_session,
)
from write_screenshot_capture_evidence import write_capture_evidence


class ScreenshotCaptureSessionFinalizerTest(unittest.TestCase):
    candidate_sha = "a" * 40
    apk_sha256 = "b" * 64

    @staticmethod
    def chunk(chunk_type: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(chunk_type)
        crc = zlib.crc32(data, crc) & 0xFFFFFFFF
        return (
            struct.pack(">I", len(data))
            + chunk_type
            + data
            + struct.pack(">I", crc)
        )

    def write_png(self, path: Path, marker: bytes) -> None:
        width, height = 800, 480
        colour = hashlib.sha256(marker).digest()[:3] + b"\xff"
        raw = (b"\x00" + colour * width) * height
        path.write_bytes(
            b"\x89PNG\r\n\x1a\n"
            + self.chunk(
                b"IHDR",
                struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0),
            )
            + self.chunk(b"IDAT", zlib.compress(raw, level=9))
            + self.chunk(b"IEND", b"")
        )

    def create_capture(
        self,
        root: Path,
        name: str,
        scenario: str,
        captured_at: dt.datetime,
        **overrides,
    ) -> Path:
        image = root / f"{name}.png"
        self.write_png(image, name.encode())
        values = {
            "scenario": scenario,
            "settle_seconds": 2.0,
            "readiness_marker": (
                f"FOREST_RUN_SCENARIO_READY scenario={scenario} "
                "mode=SCREENSHOT_CAPTURE"
            ),
            "candidate_sha": self.candidate_sha,
            "apk_sha256": self.apk_sha256,
            "device_serial": "device-1",
            "package_name": "com.anurag9000.forestrun.debug",
            "activity_name": "com.anurag9000.forestrun.MainActivity",
            "run_mode": "SCREENSHOT_CAPTURE",
            "captured_at_utc": captured_at,
        }
        values.update(overrides)
        write_capture_evidence(image, **values)
        return image

    def create_set(self, root: Path) -> None:
        self.create_capture(
            root,
            "01-opening",
            "OPENING_READABILITY",
            dt.datetime(2026, 7, 30, 12, 0, tzinfo=dt.timezone.utc),
        )
        self.create_capture(
            root,
            "02-bloom",
            "BLOOM_SHOWCASE",
            dt.datetime(2026, 7, 30, 12, 1, tzinfo=dt.timezone.utc),
        )

    def finalize(self, root: Path, **overrides) -> Path:
        values = {
            "candidate_sha": self.candidate_sha,
            "origin_main_sha": self.candidate_sha,
            "apk_sha256": self.apk_sha256,
            "device_serial": "device-1",
            "package_name": "com.anurag9000.forestrun.debug",
            "activity_name": "com.anurag9000.forestrun.MainActivity",
            "expected_count": 2,
            "captured_at_utc": dt.datetime(
                2026, 7, 30, 12, 2, tzinfo=dt.timezone.utc
            ),
        }
        values.update(overrides)
        return finalize_capture_session(root, **values)

    def test_valid_set_publishes_atomic_session_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            destination = self.finalize(root)
            payload = json.loads(destination.read_text(encoding="utf-8"))

            self.assertEqual(self.candidate_sha, payload["candidateSha"])
            self.assertEqual(self.candidate_sha, payload["originMainSha"])
            self.assertEqual(self.apk_sha256, payload["apkSha256"])
            self.assertEqual(2, payload["screenshotCount"])
            self.assertEqual("2026-07-30T12:02:00+00:00", payload["capturedAtUtc"])

    def test_missing_or_orphan_sidecars_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "02-bloom.capture.json").unlink()

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "sets differ"):
                self.finalize(root)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            (root / "orphan.capture.json").write_text("{}", encoding="utf-8")

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "sets differ"):
                self.finalize(root)

    def test_requested_count_and_origin_are_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "Expected 3"):
                self.finalize(root, expected_count=3)
            with self.assertRaisesRegex(CaptureSessionFinalizeError, "must equal"):
                self.finalize(root, origin_main_sha="c" * 40)

    def test_mixed_identity_or_duplicate_scenario_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            sidecar = root / "02-bloom.capture.json"
            payload = json.loads(sidecar.read_text(encoding="utf-8"))
            payload["deviceSerial"] = "device-2"
            sidecar.write_text(json.dumps(payload), encoding="utf-8")

            with self.assertRaisesRegex(
                CaptureSessionFinalizeError,
                "mixed capture identity|differs from requested session",
            ):
                self.finalize(root)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            sidecar = root / "02-bloom.capture.json"
            payload = json.loads(sidecar.read_text(encoding="utf-8"))
            payload["scenario"] = "OPENING_READABILITY"
            payload["readinessMarker"] = (
                "FOREST_RUN_SCENARIO_READY scenario=OPENING_READABILITY "
                "mode=SCREENSHOT_CAPTURE"
            )
            sidecar.write_text(json.dumps(payload), encoding="utf-8")

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "Duplicate capture scenario"):
                self.finalize(root)

    def test_session_timestamp_cannot_precede_latest_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "cannot predate"):
                self.finalize(
                    root,
                    captured_at_utc=dt.datetime(
                        2026, 7, 30, 12, 0, 30, tzinfo=dt.timezone.utc
                    ),
                )


if __name__ == "__main__":
    unittest.main()
