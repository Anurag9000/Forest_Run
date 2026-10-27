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

import stable_file_reader
import finalize_screenshot_capture_session as finalizer
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

    def test_png_changed_after_inspection_is_not_published_as_a_valid_session(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            original_inspect = finalizer._inspect_png
            changed = False

            def inspect_then_replace(path):
                nonlocal changed
                result = original_inspect(path)
                if path.name == "01-opening.png" and not changed:
                    changed = True
                    self.write_png(path, b"replacement-after-inspection")
                return result

            with mock.patch.object(finalizer, "_inspect_png", side_effect=inspect_then_replace):
                with self.assertRaisesRegex(
                    CaptureSessionFinalizeError,
                    "changed after screenshot validation",
                ):
                    self.finalize(root)
            self.assertTrue(changed)
            self.assertFalse((root / "capture-session.json").exists())

    def test_symlinked_output_directory_is_rejected_without_publishing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            real_directory = parent / "real-capture"
            alias_directory = parent / "capture-alias"
            real_directory.mkdir()
            self.create_set(real_directory)
            try:
                alias_directory.symlink_to(real_directory, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "symbolic link"):
                self.finalize(alias_directory)

            self.assertFalse(real_directory.joinpath("capture-session.json").exists())

    def test_output_parent_swap_cannot_redirect_atomic_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "capture"
            output.mkdir()
            external = parent / "external"
            external.mkdir()
            saved_output = parent / "capture.saved"
            self.create_set(output)
            original_open = os.open
            swapped = False

            def swap_before_temp_open(path, flags, *args, **kwargs):
                nonlocal swapped
                if (
                    isinstance(path, str)
                    and path.startswith(".capture-session.json.")
                    and kwargs.get("dir_fd") is not None
                    and not swapped
                ):
                    output.rename(saved_output)
                    output.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(stable_file_reader.os, "open", side_effect=swap_before_temp_open):
                with self.assertRaisesRegex(
                    CaptureSessionFinalizeError,
                    "parent directory path traverses a symbolic link|changed during publication",
                ):
                    self.finalize(output)

            self.assertTrue(swapped)
            self.assertFalse((external / "capture-session.json").exists())
            self.assertFalse((saved_output / "capture-session.json").exists())

    def test_output_directory_replacement_before_atomic_writer_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "capture"
            output.mkdir()
            saved_output = parent / "capture.saved"
            self.create_set(output)
            original_writer = finalizer.atomic_write_bytes
            swapped = False

            def swap_before_writer(path, content, label, **kwargs):
                nonlocal swapped
                if not swapped:
                    output.rename(saved_output)
                    output.mkdir()
                    swapped = True
                return original_writer(path, content, label, **kwargs)

            with mock.patch.object(
                finalizer,
                "atomic_write_bytes",
                side_effect=swap_before_writer,
            ):
                with self.assertRaisesRegex(
                    CaptureSessionFinalizeError,
                    "changed before publication",
                ):
                    self.finalize(output)

            self.assertTrue(swapped)
            self.assertFalse((output / "capture-session.json").exists())
            self.assertTrue((saved_output / "01-opening.png").is_file())

    def test_new_capture_pair_after_initial_enumeration_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            original_inspect = finalizer._inspect_png
            added = False

            def inspect_then_add(path):
                nonlocal added
                result = original_inspect(path)
                if not added:
                    added = True
                    self.create_capture(
                        root,
                        "03-new",
                        "NEW_CONCURRENT_SCENARIO",
                        dt.datetime(2026, 7, 30, 12, 1, 30, tzinfo=dt.timezone.utc),
                    )
                return result

            with mock.patch.object(finalizer, "_inspect_png", side_effect=inspect_then_add):
                with self.assertRaisesRegex(
                    CaptureSessionFinalizeError,
                    "Capture set changed after screenshot validation",
                ):
                    self.finalize(root)
            self.assertTrue(added)
            self.assertFalse((root / "capture-session.json").exists())

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

    def test_requested_text_identity_fields_require_exact_nonblank_strings(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            for field in ("device_serial", "package_name", "activity_name"):
                for invalid in (None, 7, "", " padded "):
                    with self.subTest(field=field, value=invalid):
                        with self.assertRaisesRegex(
                            CaptureSessionFinalizeError,
                            field,
                        ):
                            self.finalize(root, **{field: invalid})
            self.assertFalse((root / "capture-session.json").exists())

    def test_uppercase_candidate_and_digest_inputs_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)

            with self.assertRaisesRegex(CaptureSessionFinalizeError, "lowercase hexadecimal"):
                self.finalize(root, candidate_sha=self.candidate_sha.upper())
            with self.assertRaisesRegex(CaptureSessionFinalizeError, "must equal"):
                self.finalize(root, origin_main_sha=self.candidate_sha.upper())
            with self.assertRaisesRegex(CaptureSessionFinalizeError, "lowercase hexadecimal"):
                self.finalize(root, apk_sha256=self.apk_sha256.upper())

            self.assertFalse((root / "capture-session.json").exists())

    def test_candidate_and_apk_hash_inputs_reject_surrounding_whitespace(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            for field, value, message in (
                ("candidate_sha", self.candidate_sha + " ", "candidate_sha"),
                ("origin_main_sha", self.candidate_sha + " ", "origin_main_sha"),
                ("apk_sha256", self.apk_sha256 + " ", "apk_sha256"),
            ):
                with self.subTest(field=field):
                    with self.assertRaisesRegex(CaptureSessionFinalizeError, message):
                        self.finalize(root, **{field: value})

            for invalid_count in (True, 1.0, "2", 0):
                with self.subTest(expected_count=invalid_count):
                    with self.assertRaisesRegex(CaptureSessionFinalizeError, "positive integer"):
                        self.finalize(root, expected_count=invalid_count)

            self.assertFalse((root / "capture-session.json").exists())

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

    def test_session_timestamp_rejects_non_datetime_api_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            for invalid in ("2026-07-30T12:02:00Z", 7, dt.date(2026, 7, 30)):
                with self.subTest(value=invalid):
                    with self.assertRaisesRegex(
                        CaptureSessionFinalizeError,
                        "captured_at_utc must be a datetime",
                    ):
                        self.finalize(root, captured_at_utc=invalid)


    def test_ambiguous_sidecar_cannot_publish_capture_session(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            self.create_set(root)
            sidecar = root / "01-opening.capture.json"
            original = sidecar.read_text(encoding="utf-8")
            sidecar.write_text(
                original.replace(
                    '"scenario": "OPENING_READABILITY"',
                    '"scenario": "WRONG", "scenario": "OPENING_READABILITY"',
                    1,
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                CaptureSessionFinalizeError, "duplicate JSON object key"
            ):
                self.finalize(root)
            self.assertFalse((root / "capture-session.json").exists())

if __name__ == "__main__":
    unittest.main()
