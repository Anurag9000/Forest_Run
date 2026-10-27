from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import build_release_evidence_index as indexer
import stable_file_reader


class ReleaseEvidenceIndexOutputSafetyTest(unittest.TestCase):
    candidate = "a" * 40
    timestamp = "2026-08-06T06:00:00Z"

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "evidence").mkdir()
        self.manifest = self.root / "evidence/device.json"
        self.manifest.write_text(
            json.dumps({"candidate": {"commit_sha": self.candidate}}),
            encoding="utf-8",
        )
        self.specs = ["device_acceptance=evidence/device.json"]

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def build(self, output: Path):
        return indexer.build_index(
            self.root,
            self.candidate,
            self.specs,
            generated_at_utc=self.timestamp,
            require_bound_kinds=["device_acceptance"],
            output=output,
        )

    def test_existing_output_hard_link_to_evidence_is_rejected(self) -> None:
        output = self.root / "index.json"
        try:
            os.link(self.manifest, output)
        except OSError as exc:
            self.skipTest(f"hard links unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "hard link"):
            self.build(output)

    def test_parent_directory_symlink_is_rejected(self) -> None:
        real_parent = self.root / "real"
        real_parent.mkdir()
        linked_parent = self.root / "linked"
        try:
            linked_parent.symlink_to(real_parent, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        output = linked_parent / "index.json"
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "symbolic link"):
            self.build(output)

    def test_publication_rejects_a_symlinked_evidence_root(self) -> None:
        payload = self.build(self.root / "index.json")
        alias = self.root.parent / (self.root.name + "-alias")
        alias.symlink_to(self.root, target_is_directory=True)
        try:
            with self.assertRaisesRegex(indexer.EvidenceIndexError, "symbolic link"):
                indexer.publish_index(alias / "index.json", payload, root=alias)
        finally:
            alias.unlink()

    def test_lexical_output_escape_is_rejected(self) -> None:
        output = self.root / ".." / "escaped-index.json"
        with self.assertRaisesRegex(
            indexer.EvidenceIndexError,
            "inside the evidence root",
        ):
            self.build(output)

    def test_publish_rechecks_output_hard_link_and_parent_symlink(self) -> None:
        output = self.root / "published/index.json"
        payload = self.build(output)
        output.parent.mkdir()
        try:
            os.link(self.manifest, output)
        except OSError as exc:
            self.skipTest(f"hard links unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "hard link"):
            indexer.publish_index(output, payload, root=self.root)
        output.unlink()

        redirected = self.root / "redirected"
        redirected.mkdir()
        output.parent.rmdir()
        try:
            output.parent.symlink_to(redirected, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        with self.assertRaisesRegex(indexer.EvidenceIndexError, "symbolic link"):
            indexer.publish_index(output, payload, root=self.root)

    def test_parent_swap_during_temporary_creation_cannot_redirect_publication(self) -> None:
        output = self.root / "published/index.json"
        payload = self.build(output)
        output.parent.mkdir()
        moved_parent = self.root / "published-moved"
        redirected = self.root / "redirected"
        redirected.mkdir()
        real_open = os.open
        swapped = False

        def swap_before_temp_open(path, flags, *args, **kwargs):
            nonlocal swapped
            if (
                not swapped
                and kwargs.get("dir_fd") is not None
                and isinstance(path, str)
                and path.startswith(".index.json.")
            ):
                swapped = True
                output.parent.rename(moved_parent)
                output.parent.symlink_to(redirected, target_is_directory=True)
            return real_open(path, flags, *args, **kwargs)

        with patch.object(stable_file_reader.os, "open", side_effect=swap_before_temp_open):
            with self.assertRaisesRegex(
                indexer.EvidenceIndexError,
                "parent directory changed|symbolic link or non-directory",
            ):
                indexer.publish_index(output, payload, root=self.root)

        self.assertTrue(swapped)
        self.assertFalse((redirected / "index.json").exists())
        self.assertFalse((moved_parent / "index.json").exists())
        self.assertEqual([], list(moved_parent.glob(".index.json.*.tmp")))


if __name__ == "__main__":
    unittest.main()
