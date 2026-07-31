from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_origin_main.sh")


class OriginMainVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary_directory.name)
        self.remote = self.base / "origin.git"
        self.work = self.base / "work"

        self.run(["git", "init", "--bare", str(self.remote)], cwd=self.base)
        self.run(
            ["git", "init", "--initial-branch=main", str(self.work)],
            cwd=self.base,
        )
        self.git("config", "user.name", "Forest Run Tests")
        self.git("config", "user.email", "forest-run-tests@example.invalid")
        (self.work / "README.md").write_text("candidate\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-m", "Initial candidate")
        self.git("remote", "add", "origin", str(self.remote))
        self.git("push", "-u", "origin", "main")
        self.head = self.git("rev-parse", "HEAD").stdout.strip()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run(
        self,
        command: list[str],
        *,
        cwd: Path,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            command,
            cwd=cwd,
            text=True,
            capture_output=True,
            check=False,
        )
        if check and result.returncode != 0:
            raise AssertionError(
                f"{' '.join(command)} failed:\n{result.stderr or result.stdout}"
            )
        return result

    def git(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return self.run(["git", *arguments], cwd=self.work, check=check)

    def verify(self) -> subprocess.CompletedProcess[str]:
        return self.run(
            ["bash", str(SCRIPT), str(self.work)],
            cwd=self.work,
            check=False,
        )

    def test_synchronized_main_is_accepted(self) -> None:
        result = self.verify()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.head, result.stdout.strip())

    def test_unpushed_local_main_commit_is_rejected(self) -> None:
        (self.work / "local.txt").write_text("local only\n", encoding="utf-8")
        self.git("add", "local.txt")
        self.git("commit", "-m", "Local only")

        result = self.verify()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("not the canonical origin/main tip", result.stderr)

    def test_remote_advance_invalidates_stale_local_main(self) -> None:
        other = self.base / "other"
        self.run(["git", "clone", str(self.remote), str(other)], cwd=self.base)
        self.run(["git", "config", "user.name", "Remote Writer"], cwd=other)
        self.run(
            ["git", "config", "user.email", "remote-writer@example.invalid"],
            cwd=other,
        )
        (other / "remote.txt").write_text("remote advance\n", encoding="utf-8")
        self.run(["git", "add", "remote.txt"], cwd=other)
        self.run(["git", "commit", "-m", "Remote advance"], cwd=other)
        self.run(["git", "push", "origin", "main"], cwd=other)

        result = self.verify()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("not the canonical origin/main tip", result.stderr)

    def test_missing_origin_is_rejected(self) -> None:
        self.git("remote", "remove", "origin")

        result = self.verify()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("requires the canonical Git remote named origin", result.stderr)


class MainReleaseOriginContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.wrapper = Path(__file__).with_name("prepare_main_release.sh").read_text(
            encoding="utf-8"
        )

    def test_wrapper_checks_origin_before_and_after_preparation(self) -> None:
        origin_occurrences = [
            index
            for index in range(len(self.wrapper))
            if self.wrapper.startswith("verify_origin_main.sh", index)
        ]
        self.assertEqual(2, len(origin_occurrences))
        preparer_index = self.wrapper.index("prepare_play_release.py")
        self.assertLess(origin_occurrences[0], preparer_index)
        self.assertGreater(origin_occurrences[1], preparer_index)

    def test_wrapper_compares_local_candidate_and_final_origin_sha(self) -> None:
        self.assertIn('"${candidate_sha}" != "${origin_sha}"', self.wrapper)
        self.assertIn('"${final_origin_sha}" != "${candidate_sha}"', self.wrapper)


if __name__ == "__main__":
    unittest.main()
