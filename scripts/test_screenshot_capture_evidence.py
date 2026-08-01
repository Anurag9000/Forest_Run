import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from screenshot_capture_evidence import (
    CaptureEvidenceError,
    load_capture_evidence,
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


if __name__ == "__main__":
    unittest.main()
