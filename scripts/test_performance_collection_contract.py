from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("collect_performance_profiles.sh")


class PerformanceCollectionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_script_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_candidate_is_verified_before_any_device_build(self) -> None:
        origin_index = self.source.index("verify_origin_main.sh")
        local_index = self.source.index("verify_main_candidate.py")
        gradle_index = self.source.index("./gradlew connectedDebugAndroidTest")

        self.assertLess(origin_index, gradle_index)
        self.assertLess(local_index, gradle_index)
        self.assertIn('if [[ "$CANDIDATE_SHA" != "$ORIGIN_SHA" ]]', self.source)

    def test_candidate_and_origin_are_rechecked_after_capture(self) -> None:
        gradle_index = self.source.index("./gradlew connectedDebugAndroidTest")
        final_local_index = self.source.rindex("verify_main_candidate.py")
        final_origin_index = self.source.rindex("verify_origin_main.sh")

        self.assertGreater(final_local_index, gradle_index)
        self.assertGreater(final_origin_index, gradle_index)
        self.assertIn('--expected-sha "$CANDIDATE_SHA"', self.source)
        self.assertIn('"$FINAL_ORIGIN_SHA" != "$CANDIDATE_SHA"', self.source)

    def test_evidence_metadata_records_both_candidate_identities(self) -> None:
        self.assertIn('echo "candidate_sha=$CANDIDATE_SHA"', self.source)
        self.assertIn('echo "origin_main_sha=$ORIGIN_SHA"', self.source)

    def test_threshold_manifest_hash_uses_required_python_runtime(self) -> None:
        self.assertIn("hashlib.sha256", self.source)
        self.assertIn("THRESHOLDS_SHA256", self.source)
        self.assertNotIn("sha256sum", self.source)

    def test_legacy_tracked_only_cleanliness_check_cannot_return(self) -> None:
        self.assertNotIn("git diff --quiet", self.source)
        self.assertNotIn("git diff --cached --quiet", self.source)

    def test_health_snapshots_cover_thermal_battery_cpu_audio_and_power(self) -> None:
        self.assertIn("capture_device_health_snapshot before", self.source)
        self.assertIn("capture_device_health_snapshot after", self.source)
        for command in (
            "dumpsys battery",
            "dumpsys thermalservice",
            "dumpsys power",
            "dumpsys cpuinfo",
            "dumpsys audio",
            "dumpsys media.audio_flinger",
        ):
            self.assertIn(command, self.source)

    def test_post_run_app_diagnostics_cover_frames_memory_process_and_package(self) -> None:
        for output in (
            '"gfxinfo-framestats-after.txt" dumpsys gfxinfo "$APP_ID" framestats',
            '"meminfo-after.txt" dumpsys meminfo "$APP_ID"',
            '"procstats-after.txt" dumpsys procstats --hours 3 "$APP_ID"',
            '"package-after.txt" dumpsys package "$APP_ID"',
        ):
            self.assertIn(output, self.source)
        self.assertIn('cp "${OUTPUT_DIR}/gfxinfo-framestats-after.txt" "${OUTPUT_DIR}/gfxinfo.txt"', self.source)
        self.assertIn('cp "${OUTPUT_DIR}/meminfo-after.txt" "${OUTPUT_DIR}/meminfo.txt"', self.source)

    def test_perfetto_is_opt_in_and_fail_closed_when_requested(self) -> None:
        self.assertIn('CAPTURE_PERFETTO="${FOREST_RUN_CAPTURE_PERFETTO:-0}"', self.source)
        self.assertIn('FOREST_RUN_PERFETTO_DURATION', self.source)
        self.assertIn('FOREST_RUN_PERFETTO_CATEGORIES', self.source)
        self.assertIn('shell perfetto', self.source)
        self.assertIn('system-trace.perfetto-trace', self.source)
        self.assertIn('Requested Perfetto capture failed', self.source)
        self.assertIn('Requested Perfetto trace is empty.', self.source)

    def test_instrumentation_failure_retains_diagnostics_before_failing(self) -> None:
        gradle_index = self.source.index("./gradlew connectedDebugAndroidTest")
        after_snapshot_index = self.source.index("capture_device_health_snapshot after")
        failure_index = self.source.index('if [[ "$GRADLE_STATUS" -ne 0 ]]')
        self.assertLess(gradle_index, after_snapshot_index)
        self.assertLess(after_snapshot_index, failure_index)


if __name__ == "__main__":
    unittest.main()
