from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("capture_store_screenshots.sh")
WRITER = Path(__file__).with_name("write_screenshot_capture_evidence.py")
FINALIZER = Path(__file__).with_name("finalize_screenshot_capture_session.py")
CURATED_VERIFIER = Path(__file__).with_name("verify_curated_screenshot_set.py")


class StoreScreenshotCaptureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")
        cls.writer = WRITER.read_text(encoding="utf-8")
        cls.finalizer = FINALIZER.read_text(encoding="utf-8")
        cls.curated_verifier = CURATED_VERIFIER.read_text(encoding="utf-8")

    def test_script_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_exact_main_is_verified_before_build_and_after_capture(self) -> None:
        build_index = self.source.index("./gradlew clean assembleDebug")
        first_origin_index = self.source.index("verify_origin_main.sh")
        first_local_index = self.source.index("verify_main_candidate.py")
        final_origin_index = self.source.rindex("verify_origin_main.sh")
        final_local_index = self.source.rindex("verify_main_candidate.py")

        self.assertLess(first_origin_index, build_index)
        self.assertLess(first_local_index, build_index)
        self.assertGreater(final_origin_index, build_index)
        self.assertGreater(final_local_index, build_index)
        self.assertIn('--expected-sha "${CANDIDATE_SHA}"', self.source)
        self.assertIn('"${FINAL_ORIGIN_SHA}" != "${CANDIDATE_SHA}"', self.source)

    def test_capture_uses_only_the_fresh_canonical_debug_apk(self) -> None:
        self.assertIn(
            'readonly APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"',
            self.source,
        )
        self.assertIn("./gradlew clean assembleDebug", self.source)
        self.assertNotIn("APK_PATH:-", self.source)
        self.assertNotIn("COMMIT_EPOCH", self.source)
        self.assertNotIn("predates candidate commit", self.source)
        self.assertIn("verify_release_source_assets.py", self.source)

    def test_capture_resets_app_data_and_waits_for_verified_scenario(self) -> None:
        install_index = self.source.index('install -r -d "${APK_PATH}"')
        clear_index = self.source.index('shell pm clear "${PACKAGE_NAME}"')
        first_capture_index = self.source.index(
            'capture "01-opening" "OPENING_READABILITY"'
        )

        self.assertLess(install_index, clear_index)
        self.assertLess(clear_index, first_capture_index)
        self.assertIn("wait_for_scenario", self.source)
        self.assertIn("FOREST_RUN_SCENARIO_READY", self.source)
        self.assertIn("logcat -c", self.source)

    def test_device_and_timeout_inputs_fail_closed(self) -> None:
        self.assertIn("STARTUP_TIMEOUT_S must be a positive integer", self.source)
        self.assertIn("grep -Fxq", self.source)
        self.assertNotIn('=~ " ${DEVICE_SERIAL}', self.source)
        self.assertIn("ANDROID_HOME", self.source)
        self.assertIn("ANDROID_SDK_ROOT", self.source)

    def test_package_activity_and_capture_count_are_canonical(self) -> None:
        self.assertIn(
            'readonly PACKAGE_NAME="com.anurag9000.forestrun.debug"',
            self.source,
        )
        self.assertIn(
            'readonly ACTIVITY_NAME="com.anurag9000.forestrun.MainActivity"',
            self.source,
        )
        capture_lines = [
            line
            for line in self.source.splitlines()
            if line.startswith('capture "')
        ]
        self.assertEqual(8, len(capture_lines))
        self.assertIn("readonly CAPTURE_COUNT=8", self.source)
        self.assertIn('--expected-count "${CAPTURE_COUNT}"', self.source)
        self.assertIn('"screenshotCount": len(png_paths)', self.finalizer)
        self.assertIn("len(png_paths) != expected_count", self.finalizer)

    def test_capture_sidecars_validate_landscape_and_record_session_identity(self) -> None:
        self.assertIn("write_screenshot_capture_evidence.py", self.source)
        self.assertIn("finalize_screenshot_capture_session.py", self.source)
        self.assertIn("width <= height or width < 800 or height < 480", self.curated_verifier)
        combined_evidence = self.writer + "\n" + self.finalizer
        for field in (
            '"candidateSha": candidate_sha',
            '"originMainSha": origin_main_sha',
            '"apkSha256": apk_sha256',
            '"deviceSerial": device_serial',
            '"capturedAtUtc"',
        ):
            self.assertIn(field, combined_evidence)
        self.assertIn("capture-session.json", self.source)

    def test_legacy_tracked_only_and_prebuilt_apk_paths_cannot_return(self) -> None:
        self.assertNotIn("git -C \"$ROOT_DIR\" diff --quiet", self.source)
        self.assertNotIn("git -C \"$ROOT_DIR\" diff --cached --quiet", self.source)
        self.assertNotIn("Run ./gradlew assembleDebug first", self.source)


if __name__ == "__main__":
    unittest.main()
