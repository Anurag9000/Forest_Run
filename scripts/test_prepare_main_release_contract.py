from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path
import shlex

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = Path(__file__).with_name("prepare_main_release.sh")
RELEASE_DOC = ROOT / "docs/RELEASE.md"


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
        self.assertIn('local destination="${RELEASE_ROOT}/${filename}"', self.source)
        self.assertIn('rm -f "${destination}"', self.source)
        self.assertIn('mv "${summary_backup_dir}/${filename}"', self.source)
        self.assertIn('rm -rf "${summary_backup_dir}"', self.source)

    def test_failed_summary_restore_preserves_and_reports_backup_directory(self) -> None:
        restore = self.source[
            self.source.index("restore_release_summaries() {"):
            self.source.index("trap restore_release_summaries EXIT")
        ]
        self.assertIn("local restore_failed=0", restore)
        self.assertIn("if [[ ${restore_failed} -eq 0 ]]", restore)
        self.assertIn('Release summary backups retained at ${summary_backup_dir}', restore)
        self.assertIn("if ! mv", restore)

    def test_restore_failure_keeps_quarantined_summary_for_recovery(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            release = root / "release"
            backup = root / "backup"
            release.mkdir()
            backup.mkdir()
            prior = backup / "build_summary.json"
            prior.write_text("prior summary", encoding="utf-8")
            (release / "build_summary.json").write_text("failed summary", encoding="utf-8")
            restore = self.source[
                self.source.index("restore_release_summaries() {"):
                self.source.index("trap restore_release_summaries EXIT")
            ]
            script = "\n".join(
                (
                    "set -euo pipefail",
                    f"RELEASE_ROOT={shlex.quote(str(release))}",
                    f"summary_backup_dir={shlex.quote(str(backup))}",
                    "mv() { return 1; }",
                    restore,
                    "trap restore_release_summaries EXIT",
                    "false",
                )
            )
            result = subprocess.run(
                ["bash", "-c", script],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn(str(backup), result.stderr)
            self.assertEqual("prior summary", prior.read_text(encoding="utf-8"))
            self.assertFalse((release / "build_summary.json").exists())

    def test_new_summary_is_verified_before_final_candidate_checks(self) -> None:
        summary_index = self.source.index("verify_release_summary.py")
        final_local_index = self.source.rindex("verify_main_candidate.py")
        final_origin_index = self.source.rindex("verify_origin_main.sh")
        self.assertLess(summary_index, final_local_index)
        self.assertLess(summary_index, final_origin_index)
        self.assertIn('--candidate-sha "${candidate_sha}"', self.source[summary_index:final_local_index])

    def test_release_guide_uses_only_canonical_candidate_entrypoint(self) -> None:
        guide = RELEASE_DOC.read_text(encoding="utf-8")
        self.assertIn("bash scripts/prepare_main_release.sh", guide)
        self.assertNotIn("python3 scripts/prepare_play_release.py", guide)
        self.assertIn("only supported release-preparation entry point", guide)
        self.assertIn("Do not invoke `scripts/prepare_play_release.py` directly", guide)

    def test_release_guide_does_not_hard_code_connected_test_cardinality(self) -> None:
        guide = RELEASE_DOC.read_text(encoding="utf-8")
        self.assertNotIn("fourteen", guide.lower())
        self.assertIn("complete ordinary connected suite", guide)
        self.assertIn("zero failures", guide)
        self.assertIn("zero errors", guide)
        self.assertIn("zero skips", guide)

    def test_all_operator_release_docs_preserve_the_canonical_boundary(self) -> None:
        operator_docs = (
            ROOT / "docs/RELEASE.md",
            ROOT / "docs/RELEASE_READINESS.md",
            ROOT / "docs/STORE_EVIDENCE.md",
            ROOT / "release/google-play/README.md",
            ROOT / "release/google-play/PUBLISHING_CHECKLIST.md",
            ROOT / "release/google-play/screenshots/CAPTURE_PLAN.md",
        )
        forbidden_commands = {
            "scripts/prepare_play_release.py",
            "python3 scripts/prepare_play_release.py",
            "python scripts/prepare_play_release.py",
        }
        machine_local_path = re.compile(
            r"(?i)(?:^|[\s`=])/(?:home|users|opt|tmp|var/tmp|root|mnt|media|private/var/folders)/[^\s`]+"
        )
        for path in operator_docs:
            with self.subTest(path=path):
                text = path.read_text(encoding="utf-8")
                stripped_lines = {line.strip() for line in text.splitlines()}
                self.assertTrue(forbidden_commands.isdisjoint(stripped_lines))
                self.assertNotRegex(text, machine_local_path)

        play_readme = (ROOT / "release/google-play/README.md").read_text(
            encoding="utf-8"
        )
        normalized = " ".join(play_readme.split())
        self.assertIn("bash scripts/prepare_main_release.sh", normalized)
        self.assertIn("must not be invoked directly", normalized)


if __name__ == "__main__":
    unittest.main()
