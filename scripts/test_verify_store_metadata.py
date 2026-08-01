import json
import tempfile
import unittest
from pathlib import Path

from verify_store_metadata import (
    StoreMetadataError,
    finalize_metadata,
    verify_metadata,
)

ROOT = Path(__file__).resolve().parent.parent


class StoreMetadataVerifierTest(unittest.TestCase):
    candidate = "a" * 40

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.metadata = Path(self.temp.name, "metadata")
        self.metadata.mkdir()
        (self.metadata / "title.txt").write_text("Forest Run", encoding="utf-8")
        (self.metadata / "short-description.txt").write_text(
            "Run gently through a living forest.", encoding="utf-8"
        )
        (self.metadata / "full-description.txt").write_text(
            "Forest Run is a handcrafted endless runner where mercy changes the path.\n\n"
            "Collect Seeds, enter Bloom, meet memorable creatures, and return to a "
            "persistent Garden that remembers how you played.",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_finalize_then_verify_binds_exact_text_to_candidate(self) -> None:
        manifest = finalize_metadata(self.metadata, self.candidate)
        facts = verify_metadata(self.metadata, self.candidate)
        payload = json.loads(manifest.read_text(encoding="utf-8"))

        self.assertEqual(3, len(facts))
        self.assertEqual(self.candidate, payload["candidateSha"])
        self.assertEqual("en-US", payload["locale"])
        self.assertEqual(3, len(payload["files"]))

    def test_changed_text_and_wrong_candidate_are_rejected(self) -> None:
        finalize_metadata(self.metadata, self.candidate)
        (self.metadata / "title.txt").write_text("Forest Run Updated", encoding="utf-8")
        with self.assertRaisesRegex(StoreMetadataError, "stale"):
            verify_metadata(self.metadata, self.candidate)

        finalize_metadata(self.metadata, self.candidate)
        with self.assertRaisesRegex(StoreMetadataError, "candidate"):
            verify_metadata(self.metadata, "b" * 40)

    def test_control_whitespace_line_endings_and_placeholders_fail_closed(self) -> None:
        cases = (
            ("title.txt", " Forest Run", "leading or trailing"),
            ("title.txt", "Forest\nRun", "exactly one line"),
            ("short-description.txt", "TODO replace this", "Placeholder"),
            ("short-description.txt", "Valid text\x00hidden", "control"),
            ("full-description.txt", "A" * 100 + "\r\nB", "LF line endings"),
            ("full-description.txt", "A" * 100 + "\n\n\nB", "excessive blank"),
        )
        for filename, text, message in cases:
            with self.subTest(filename=filename, message=message):
                self.setUp()
                try:
                    (self.metadata / filename).write_text(text, encoding="utf-8")
                    with self.assertRaisesRegex(StoreMetadataError, message):
                        finalize_metadata(self.metadata, self.candidate)
                finally:
                    self.tearDown()

    def test_missing_extra_and_malformed_manifest_entries_are_rejected(self) -> None:
        finalize_metadata(self.metadata, self.candidate)
        (self.metadata / "extra.txt").write_text("stale", encoding="utf-8")
        with self.assertRaisesRegex(StoreMetadataError, "unrecognized"):
            verify_metadata(self.metadata, self.candidate)

        (self.metadata / "extra.txt").unlink()
        manifest = self.metadata / "metadata_manifest.json"
        payload = json.loads(manifest.read_text(encoding="utf-8"))
        payload["files"].append(dict(payload["files"][0]))
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(StoreMetadataError, "Duplicate"):
            verify_metadata(self.metadata, self.candidate)

    def test_finalize_replaces_stale_manifest_atomically(self) -> None:
        manifest = self.metadata / "metadata_manifest.json"
        manifest.write_text("stale", encoding="utf-8")
        finalize_metadata(self.metadata, self.candidate)
        self.assertEqual(1, json.loads(manifest.read_text())["schemaVersion"])


class StoreMetadataReleaseContractTest(unittest.TestCase):
    def test_canonical_release_verifies_metadata_before_play_preparer(self) -> None:
        source = (ROOT / "scripts/prepare_main_release.sh").read_text(encoding="utf-8")
        self.assertNotIn("verify_store_metadata.py", source)  # Updated with wrapper commit.


if __name__ == "__main__":
    unittest.main()
