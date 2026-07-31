from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("verify_main_candidate.py")
SPEC = importlib.util.spec_from_file_location("verify_main_candidate", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

CandidateVerificationError = MODULE.CandidateVerificationError
verify_main_candidate = MODULE.verify_main_candidate


class MainCandidateVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Forest Run Tests")
        self.git("config", "user.email", "forest-run-tests@example.invalid")
        (self.root / "README.md").write_text("candidate\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-m", "Initial candidate")
        self.head = self.git("rev-parse", "HEAD").strip()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def git(self, *arguments: str) -> str:
        result = subprocess.run(
            ["git", *arguments],
            cwd=self.root,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            raise AssertionError(
                f"git {' '.join(arguments)} failed:\n{result.stderr or result.stdout}"
            )
        return result.stdout

    def test_clean_main_tip_is_accepted(self) -> None:
        candidate = verify_main_candidate(self.root, expected_sha=self.head)

        self.assertEqual(self.head, candidate.sha)
        self.assertEqual("main", candidate.branch)
        self.assertEqual(str(self.root.resolve()), candidate.root)

    def test_dirty_tracked_file_is_rejected(self) -> None:
        (self.root / "README.md").write_text("modified\n", encoding="utf-8")

        with self.assertRaisesRegex(CandidateVerificationError, "completely clean"):
            verify_main_candidate(self.root)

    def test_untracked_file_is_rejected(self) -> None:
        (self.root / "untracked.txt").write_text("untracked\n", encoding="utf-8")

        with self.assertRaisesRegex(CandidateVerificationError, "untracked.txt"):
            verify_main_candidate(self.root)

    def test_feature_branch_is_rejected_even_at_same_commit(self) -> None:
        self.git("switch", "-c", "feature")

        with self.assertRaisesRegex(CandidateVerificationError, "only from the named main branch"):
            verify_main_candidate(self.root)

    def test_detached_head_is_rejected(self) -> None:
        self.git("checkout", "--detach", self.head)

        with self.assertRaises(CandidateVerificationError):
            verify_main_candidate(self.root)

    def test_expected_sha_mismatch_is_rejected(self) -> None:
        with self.assertRaisesRegex(CandidateVerificationError, "requested frozen commit"):
            verify_main_candidate(self.root, expected_sha="0" * 40)

    def test_abbreviated_expected_sha_is_rejected(self) -> None:
        with self.assertRaisesRegex(CandidateVerificationError, "full 40-character"):
            verify_main_candidate(self.root, expected_sha=self.head[:12])

    def test_subdirectory_is_not_accepted_as_repository_root(self) -> None:
        child = self.root / "nested"
        child.mkdir()

        with self.assertRaisesRegex(CandidateVerificationError, "repository root"):
            verify_main_candidate(child)


class MainReleaseWrapperContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.script = Path(__file__).with_name("prepare_main_release.sh").read_text(
            encoding="utf-8"
        )

    def test_wrapper_is_strict_and_freezes_main_before_preparation(self) -> None:
        self.assertIn("set -euo pipefail", self.script)
        self.assertIn("verify_main_candidate.py", self.script)
        self.assertIn("candidate_sha=", self.script)
        self.assertLess(
            self.script.index("candidate_sha="),
            self.script.index("prepare_play_release.py"),
        )

    def test_wrapper_rechecks_frozen_sha_after_preparation(self) -> None:
        preparer_index = self.script.index("prepare_play_release.py")
        expected_sha_index = self.script.rindex("--expected-sha")
        self.assertGreater(expected_sha_index, preparer_index)
        self.assertIn('"${candidate_sha}"', self.script[expected_sha_index:])

    def test_wrapper_does_not_pass_unsupported_flag_to_preparer(self) -> None:
        preparer_line = next(
            line for line in self.script.splitlines() if "prepare_play_release.py" in line
        )
        self.assertNotIn("--expected-sha", preparer_line)


if __name__ == "__main__":
    unittest.main()
