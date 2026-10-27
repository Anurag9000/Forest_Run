from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("capture_store_screenshots.sh")
WRITER = Path(__file__).with_name("write_screenshot_capture_evidence.py")
FINALIZER = Path(__file__).with_name("finalize_screenshot_capture_session.py")
CURATED_VERIFIER = Path(__file__).with_name("verify_curated_screenshot_set.py")
PUBLISHER = Path(__file__).with_name("publish_screenshot_capture_set.py")
SCENARIO_SCRIPT = Path(__file__).parents[1] / "app/src/main/java/com/anurag9000/forestrun/engine/DebugScenarioScript.kt"


class StoreScreenshotCaptureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")
        cls.writer = WRITER.read_text(encoding="utf-8")
        cls.finalizer = FINALIZER.read_text(encoding="utf-8")
        cls.curated_verifier = CURATED_VERIFIER.read_text(encoding="utf-8")
        cls.publisher = PUBLISHER.read_text(encoding="utf-8")
        cls.scenario_script = SCENARIO_SCRIPT.read_text(encoding="utf-8")

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

    def test_owl_capture_is_bound_to_a_scenario_with_a_visible_jump(self) -> None:
        self.assertIn('capture "07-owl" "OWL_DIVE" 2.1', self.source)
        self.assertIn(
            "EncounterScenario.OWL_DIVE -> listOf(\n"
            "                DebugScenarioStep(1.80f, DebugScenarioAction.TAP_JUMP)",
            self.scenario_script,
        )

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

    def test_capture_is_staged_and_old_evidence_is_not_deleted_before_success(self) -> None:
        self.assertIn('STAGING_DIR="$(mktemp -d', self.source)
        self.assertIn('local destination="${STAGING_DIR}/${name}.png"', self.source)
        self.assertIn('find "${STAGING_DIR}" -maxdepth 1', self.source)
        self.assertNotIn('find "${OUTPUT_DIR}"', self.source)
        self.assertIn('trap cleanup_staging_directory EXIT', self.source)
        device_inventory_index = self.source.index('mapfile -t devices')
        staging_index = self.source.index('STAGING_DIR="$(mktemp -d')
        self.assertLess(device_inventory_index, staging_index)
        finalize_index = self.source.index("scripts/finalize_screenshot_capture_session.py")
        raw_verify_index = self.source.index("scripts/verify_raw_screenshot_set.py")
        final_candidate_check_index = self.source.rindex("FINAL_ORIGIN_SHA")
        publish_index = self.source.rindex("scripts/publish_screenshot_capture_set.py")
        self.assertLess(finalize_index, raw_verify_index)
        self.assertLess(raw_verify_index, final_candidate_check_index)
        self.assertLess(final_candidate_check_index, publish_index)
        self.assertIn("os.rename(staging_dir.name, output_dir.name", self.publisher)
        self.assertIn("os.rename(backup_name, output_dir.name", self.publisher)
        self.assertIn("src_dir_fd=parent_fd", self.publisher)

    def test_managed_symlink_paths_are_removed_from_stage_before_capture(self) -> None:
        cleanup_index = self.source.index('find "${STAGING_DIR}" -maxdepth 1')
        first_capture_index = self.source.index('capture "01-opening"')
        self.assertLess(cleanup_index, first_capture_index)
        self.assertIn("-exec rm -rf -- {} +", self.source)
        self.assertIn('if [[ -L "${OUTPUT_DIR}" ]]', self.source)
        preflight_index = self.source.index("--check-only")
        staging_index = self.source.index('STAGING_DIR="$(mktemp -d')
        self.assertLess(preflight_index, staging_index)


if __name__ == "__main__":
    unittest.main()
