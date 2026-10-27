import datetime as dt
import hashlib
import json
import os
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest import mock

from screenshot_capture_evidence import load_capture_evidence
import write_screenshot_capture_evidence as writer
from write_screenshot_capture_evidence import (
    CaptureEvidenceWriteError,
    write_capture_evidence,
)


class ScreenshotCaptureEvidenceWriterTest(unittest.TestCase):
    candidate_sha = "a" * 40
    apk_sha256 = "b" * 64
    scenario = "OPENING_READABILITY"
    marker = (
        "FOREST_RUN_SCENARIO_READY "
        "scenario=OPENING_READABILITY mode=SCREENSHOT_CAPTURE"
    )

    @staticmethod
    def chunk(chunk_type: bytes, data: bytes) -> bytes:
        checksum = zlib.crc32(chunk_type)
        checksum = zlib.crc32(data, checksum) & 0xFFFFFFFF
        return (
            struct.pack(">I", len(data))
            + chunk_type
            + data
            + struct.pack(">I", checksum)
        )

    def write_png(self, path: Path, width: int = 800, height: int = 480) -> str:
        pixel = b"\x20\x80\xd0\xff"
        raw = (b"\x00" + pixel * width) * height
        content = (
            b"\x89PNG\r\n\x1a\n"
            + self.chunk(
                b"IHDR",
                struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0),
            )
            + self.chunk(b"IDAT", zlib.compress(raw, level=9))
            + self.chunk(b"IEND", b"")
        )
        path.write_bytes(content)
        return hashlib.sha256(content).hexdigest()

    def write(self, image: Path, **overrides) -> Path:
        values = {
            "scenario": self.scenario,
            "settle_seconds": 2.0,
            "readiness_marker": self.marker,
            "candidate_sha": self.candidate_sha,
            "apk_sha256": self.apk_sha256,
            "device_serial": "device-1",
            "package_name": "com.anurag9000.forestrun.debug",
            "activity_name": "com.anurag9000.forestrun.MainActivity",
            "run_mode": "SCREENSHOT_CAPTURE",
            "captured_at_utc": dt.datetime(
                2026, 7, 30, 12, 0, tzinfo=dt.timezone.utc
            ),
        }
        values.update(overrides)
        return write_capture_evidence(image, **values)

    def test_valid_png_writes_loadable_candidate_bound_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            image = Path(temporary_directory, "opening.png")
            digest = self.write_png(image)

            sidecar = self.write(image)
            evidence = load_capture_evidence(
                sidecar,
                expected_raw_file=image.name,
                expected_scenario=self.scenario,
                expected_image_sha256=digest,
                expected_width=800,
                expected_height=480,
            )

            self.assertEqual(self.candidate_sha, evidence.candidate_sha)
            self.assertEqual(self.apk_sha256, evidence.apk_sha256)
            self.assertEqual("device-1", evidence.device_serial)
            self.assertEqual(
                "2026-07-30T12:00:00+00:00",
                evidence.captured_at_utc,
            )

    def test_existing_sidecar_is_replaced_only_after_successful_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            image = Path(temporary_directory, "opening.png")
            self.write_png(image)
            sidecar = image.with_suffix(".capture.json")
            sidecar.write_text("stale", encoding="utf-8")

            self.write(image)

            payload = json.loads(sidecar.read_text(encoding="utf-8"))
            self.assertEqual(self.candidate_sha, payload["candidateSha"])
            self.assertEqual(self.marker, payload["readinessMarker"])

    def test_corrupt_png_is_rejected_without_creating_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            image = Path(temporary_directory, "opening.png")
            self.write_png(image)
            content = bytearray(image.read_bytes())
            content[content.index(b"IDAT") + 5] ^= 0x01
            image.write_bytes(content)

            with self.assertRaisesRegex(CaptureEvidenceWriteError, "CRC mismatch"):
                self.write(image)

            self.assertFalse(image.with_suffix(".capture.json").exists())

    def test_identity_and_readiness_fields_fail_closed(self) -> None:
        invalid_cases = (
            ({"candidate_sha": "short"}, "candidate_sha"),
            ({"candidate_sha": "A" * 40}, "lowercase hexadecimal"),
            ({"apk_sha256": "bad"}, "apk_sha256"),
            ({"apk_sha256": "B" * 64}, "lowercase hexadecimal"),
            ({"candidate_sha": " " + self.candidate_sha}, "surrounding whitespace"),
            ({"apk_sha256": self.apk_sha256 + " "}, "surrounding whitespace"),
            ({"scenario": "opening"}, "uppercase scenario"),
            ({"scenario": self.scenario + " "}, "surrounding whitespace"),
            ({"readiness_marker": "stale"}, "readiness_marker mismatch"),
            ({"package_name": "com.example.wrong"}, "package_name"),
            ({"activity_name": "WrongActivity"}, "activity_name"),
            ({"run_mode": "NORMAL"}, "run_mode"),
            ({"settle_seconds": float("nan")}, "settle_seconds"),
            ({"settle_seconds": True}, "settle_seconds"),
            ({"candidate_sha": None}, "candidate_sha must be text"),
            ({"device_serial": 7}, "device_serial must be text"),
            ({"captured_at_utc": "2026-07-30T12:00:00Z"}, "captured_at_utc must be a datetime"),
        )
        for overrides, expected_message in invalid_cases:
            with self.subTest(overrides=overrides):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    image = Path(temporary_directory, "opening.png")
                    self.write_png(image)
                    with self.assertRaisesRegex(
                        CaptureEvidenceWriteError,
                        expected_message,
                    ):
                        self.write(image, **overrides)

    def test_writer_rejects_symlinked_image_and_parent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            real_image = root / "real.png"
            self.write_png(real_image)
            linked_image = root / "linked.png"
            try:
                linked_image.symlink_to(real_image)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable")

            with self.assertRaisesRegex(CaptureEvidenceWriteError, "symbolic link"):
                self.write(linked_image)
            self.assertFalse(real_image.with_suffix(".capture.json").exists())

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            real_directory = root / "real"
            real_directory.mkdir()
            image = real_directory / "image.png"
            self.write_png(image)
            alias = root / "alias"
            try:
                alias.symlink_to(real_directory, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable")

            with self.assertRaisesRegex(CaptureEvidenceWriteError, "symbolic link"):
                self.write(alias / image.name)
            self.assertFalse(image.with_suffix(".capture.json").exists())

    def test_writer_parent_swap_cannot_redirect_sidecar_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            output = root / "capture"
            output.mkdir()
            external = root / "external"
            external.mkdir()
            saved_output = root / "capture.saved"
            image = output / "opening.png"
            self.write_png(image)
            original_open = os.open
            swapped = False

            def swap_before_temp_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if (
                    isinstance(path, str)
                    and path.startswith(".opening.capture.json.")
                    and kwargs.get("dir_fd") is not None
                    and not swapped
                ):
                    output.rename(saved_output)
                    output.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(writer.os, "open", side_effect=swap_before_temp_open):
                with self.assertRaisesRegex(CaptureEvidenceWriteError, "symbolic link"):
                    self.write(image)

            self.assertTrue(swapped)
            self.assertFalse((external / "opening.capture.json").exists())
            self.assertFalse((saved_output / "opening.capture.json").exists())


if __name__ == "__main__":
    unittest.main()
