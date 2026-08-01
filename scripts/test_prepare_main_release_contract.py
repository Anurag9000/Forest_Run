from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("prepare_main_release.sh")


class MainReleaseWrapperContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_wrapper_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_java_home_runtime_is_prepended_before_release_prechecks(self) -> None:
        java_index = self.source.index('if [[ -n "${JAVA_HOME:-}" ]]')
        path_index = self.source.index('export PATH="$(dirname "${java_home_binary}"):${PATH}"')
        origin_index = self.source.index("verify_origin_main.sh")
        self.assertLess(java_index, path_index)
        self.assertLess(path_index, origin_index)
        self.assertIn("JAVA_HOME does not contain an executable Java runtime", self.source)

    def test_candidate_bound_preflights_run_before_play_preparer(self) -> None:
        preparer_index = self.source.index("prepare_play_release.py")
        for verifier in (
            "verify_origin_main.sh",
            "verify_main_candidate.py",
            "verify_release_source_assets.py",
            "verify_store_graphics.py",
            "verify_store_metadata.py",
        ):
            self.assertLess(self.source.index(verifier), preparer_index)

    def test_previous_summaries_are_quarantined_and_restored_on_failure(self) -> None:
        trap_index = self.source.index("trap restore_release_summaries EXIT")
        preparer_index = self.source.index("prepare_play_release.py")
        verifier_index = self.source.index("verify_release_summary.py")
        self.assertLess(trap_index, preparer_index)
        self.assertGreater(verifier_index, preparer_index)
        self.assertIn('rm -f "${MACHINE_SUMMARY}" "${HUMAN_SUMMARY}"', self.source)
        self.assertIn('mv "${summary_backup_dir}/${filename}"', self.source)
        self.assertIn('rm -rf "${summary_backup_dir}"', self.source)

    def test_new_summary_is_verified_before_final_candidate_checks(self) -> None:
        summary_index = self.source.index("verify_release_summary.py")
        final_local_index = self.source.rindex("verify_main_candidate.py")
        final_origin_index = self.source.rindex("verify_origin_main.sh")
        self.assertLess(summary_index, final_local_index)
        self.assertLess(summary_index, final_origin_index)
        self.assertIn('--candidate-sha "${candidate_sha}"', self.source[summary_index:final_local_index])


if __name__ == "__main__":
    unittest.main()
