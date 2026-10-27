import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path

from screenshot_capture_evidence import (
    CaptureEvidenceError,
    load_capture_evidence,
    parse_capture_evidence,
    require_same_capture_identity,
)


class ScreenshotCaptureEvidenceTest(unittest.TestCase):
    def evidence(self, **overrides):
        values = {
            "schemaVersion": 1,
            "rawFile": "01-opening.png",
            "scenario": "OPENING_READABILITY",
            "runMode": "SCREENSHOT_CAPTURE",
            "readinessMarker": (
                "FOREST_RUN_SCENARIO_READY scenario=OPENING_READABILITY "
                "mode=SCREENSHOT_CAPTURE"
            ),
            "candidateSha": "a" * 40,
            "apkSha256": "b" * 64,
            "deviceSerial": "device-1",
            "packageName": "com.anurag9000.forestrun.debug",
            "activityName": "com.anurag9000.forestrun.MainActivity",
            "settleSeconds": 2.0,
            "capturedAtUtc": "2026-07-30T12:00:00+00:00",
            "imageSha256": hashlib.sha256(b"image").hexdigest(),
            "width": 1920,
            "height": 1080,
        }
        values.update(overrides)
        return values

    def write(self, root: Path, payload, name="01-opening.capture.json"):
        path = root / name
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def load(self, path: Path, **overrides):
        expected = {
            "expected_raw_file": "01-opening.png",
            "expected_scenario": "OPENING_READABILITY",
            "expected_image_sha256": hashlib.sha256(b"image").hexdigest(),
            "expected_width": 1920,
            "expected_height": 1080,
        }
        expected.update(overrides)
        return load_capture_evidence(path, **expected)

    def test_capture_sidecar_schema_version_requires_integer(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "capture.json"
            for invalid in (True, False, 1.0, "1", None):
                with self.subTest(value=invalid):
                    self.write(path.parent, self.evidence(schemaVersion=invalid), path.name)
                    with self.assertRaisesRegex(CaptureEvidenceError, "schemaVersion"):
                        self.load(path)

    def test_valid_evidence_is_loaded(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = self.write(Path(temporary_directory), self.evidence())

            evidence = self.load(path)

            self.assertEqual("OPENING_READABILITY", evidence.scenario)
            self.assertEqual("a" * 40, evidence.candidate_sha)
            self.assertEqual(2.0, evidence.settle_seconds)

    def test_scenario_marker_and_hash_must_match_requested_image(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            wrong_scenario = self.write(
                root,
                self.evidence(scenario="BLOOM_SHOWCASE"),
                "wrong-scenario.json",
            )
            wrong_marker = self.write(
                root,
                self.evidence(readinessMarker="FOREST_RUN_SCENARIO_READY scenario=WRONG"),
                "wrong-marker.json",
            )
            wrong_hash = self.write(
                root,
                self.evidence(imageSha256="c" * 64),
                "wrong-hash.json",
            )

            with self.assertRaisesRegex(CaptureEvidenceError, "scenario mismatch"):
                self.load(wrong_scenario)
            with self.assertRaisesRegex(CaptureEvidenceError, "readinessMarker mismatch"):
                self.load(wrong_marker)
            with self.assertRaisesRegex(CaptureEvidenceError, "imageSha256 mismatch"):
                self.load(wrong_hash)

    def test_debug_package_activity_and_utc_are_mandatory(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            wrong_package = self.write(
                root,
                self.evidence(packageName="com.example.wrong"),
                "wrong-package.json",
            )
            wrong_activity = self.write(
                root,
                self.evidence(activityName="WrongActivity"),
                "wrong-activity.json",
            )
            no_timezone = self.write(
                root,
                self.evidence(capturedAtUtc="2026-07-30T12:00:00"),
                "no-timezone.json",
            )
            non_utc = self.write(
                root,
                self.evidence(capturedAtUtc="2026-07-30T17:30:00+05:30"),
                "non-utc.json",
            )

            with self.assertRaisesRegex(CaptureEvidenceError, "packageName mismatch"):
                self.load(wrong_package)
            with self.assertRaisesRegex(CaptureEvidenceError, "activityName mismatch"):
                self.load(wrong_activity)
            with self.assertRaisesRegex(CaptureEvidenceError, "timezone"):
                self.load(no_timezone)
            with self.assertRaisesRegex(CaptureEvidenceError, "must use UTC"):
                self.load(non_utc)

    def test_candidate_apk_and_image_digests_require_exact_hex_lengths(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            bad_candidate = self.write(
                root,
                self.evidence(candidateSha="abc"),
                "bad-candidate.json",
            )
            bad_apk = self.write(
                root,
                self.evidence(apkSha256="xyz"),
                "bad-apk.json",
            )
            bad_image = self.write(
                root,
                self.evidence(imageSha256="xyz"),
                "bad-image.json",
            )
            valid = self.write(root, self.evidence(), "bad-expected.json")

            with self.assertRaisesRegex(CaptureEvidenceError, "40-character"):
                self.load(bad_candidate)
            with self.assertRaisesRegex(CaptureEvidenceError, "apkSha256"):
                self.load(bad_apk)
            with self.assertRaisesRegex(CaptureEvidenceError, "imageSha256"):
                self.load(bad_image)
            with self.assertRaisesRegex(CaptureEvidenceError, "expected_image_sha256"):
                self.load(valid, expected_image_sha256="not-a-digest")

    def test_identity_and_digest_fields_reject_surrounding_whitespace(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            for field, value in (
                ("rawFile", " 01-opening.png"),
                ("scenario", "OPENING_READABILITY "),
                ("candidateSha", "a" * 40 + " "),
                ("apkSha256", "b" * 64 + " "),
                ("imageSha256", hashlib.sha256(b"image").hexdigest() + " "),
            ):
                with self.subTest(field=field):
                    path = self.write(root, self.evidence(**{field: value}))
                    with self.assertRaisesRegex(
                        CaptureEvidenceError,
                        "surrounding whitespace|40-character|SHA-256",
                    ):
                        self.load(path)

    def test_mixed_capture_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            first_path = self.write(root, self.evidence(), "first.json")
            second_path = self.write(
                root,
                self.evidence(deviceSerial="device-2"),
                "second.json",
            )
            first = self.load(first_path)
            second = self.load(second_path)

            with self.assertRaisesRegex(CaptureEvidenceError, "deviceSerial"):
                require_same_capture_identity(first, second, second_path)


    def test_ambiguous_nonfinite_and_oversized_capture_sidecars_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = self.write(Path(temporary_directory), self.evidence())
            original = path.read_text(encoding="utf-8")
            poisoned_variants = (
                (
                    original.replace(
                        '"scenario": "OPENING_READABILITY"',
                        '"scenario": "OTHER", "scenario": "OPENING_READABILITY"',
                        1,
                    ),
                    "duplicate JSON object key",
                ),
                (
                    original.replace('"schemaVersion": 1', '"extra": NaN, "schemaVersion": 1', 1),
                    "non-finite",
                ),
                (" " * (64 * 1024 + 1), "between"),
            )
            for poisoned, error in poisoned_variants:
                with self.subTest(error=error):
                    path.write_text(poisoned, encoding="utf-8")
                    with self.assertRaisesRegex(CaptureEvidenceError, error):
                        self.load(path)
            with self.assertRaisesRegex(CaptureEvidenceError, "must be finite"):
                parse_capture_evidence(
                    self.evidence(settleSeconds=10 ** 1000),
                    path,
                    expected_raw_file="01-opening.png",
                    expected_scenario="OPENING_READABILITY",
                    expected_image_sha256=hashlib.sha256(b"image").hexdigest(),
                    expected_width=1920,
                    expected_height=1080,
                )

    def test_capture_hashes_require_lowercase_hex(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            for key, value, message in (
                ("candidateSha", "a" * 40, "candidateSha"),
                ("apkSha256", "b" * 64, "apkSha256"),
                ("imageSha256", hashlib.sha256(b"image").hexdigest(), "imageSha256"),
            ):
                path = self.write(root, self.evidence(**{key: value.upper()}), f"{key}.json")
                with self.subTest(key=key), self.assertRaisesRegex(CaptureEvidenceError, message):
                    self.load(path)
            valid = self.write(root, self.evidence(), "valid.json")
            with self.assertRaisesRegex(CaptureEvidenceError, "expected_image_sha256"):
                self.load(valid, expected_image_sha256=hashlib.sha256(b"image").hexdigest().upper())

    def test_capture_sidecar_loader_rejects_symlink_fifo_and_oversize_before_open(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            target = self.write(root, self.evidence(), "target.json")
            alias = root / "alias.json"
            alias.symlink_to(target)
            with self.assertRaisesRegex(CaptureEvidenceError, "non-symlink"):
                self.load(alias)

            fifo = root / "capture.fifo"
            os.mkfifo(fifo)
            with self.assertRaisesRegex(CaptureEvidenceError, "non-symlink"):
                self.load(fifo)

            oversized = root / "oversized.json"
            with oversized.open("wb") as stream:
                stream.truncate(64 * 1024 + 1)
            with self.assertRaisesRegex(CaptureEvidenceError, "between 1 and"):
                self.load(oversized)

if __name__ == "__main__":
    unittest.main()
