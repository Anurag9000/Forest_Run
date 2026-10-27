import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import verify_store_metadata as store_metadata
import stable_file_reader
from verify_store_metadata import (
    StoreMetadataError,
    finalize_metadata,
    verify_metadata,
)

ROOT = Path(__file__).resolve().parent.parent


class StoreMetadataVerifierTest(unittest.TestCase):
    candidate = "a" * 40

    @staticmethod
    def write_valid_metadata(metadata: Path) -> None:
        (metadata / "title.txt").write_text("Forest Run", encoding="utf-8")
        (metadata / "short-description.txt").write_text(
            "Run gently through a living forest.", encoding="utf-8"
        )
        (metadata / "full-description.txt").write_text(
            "Forest Run is a handcrafted endless runner where mercy changes the path.\n\n"
            "Collect Seeds, enter Bloom, meet memorable creatures, and return to a "
            "persistent Garden that remembers how you played.",
            encoding="utf-8",
        )

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.metadata = Path(self.temp.name, "metadata")
        self.metadata.mkdir()
        self.write_valid_metadata(self.metadata)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_metadata_schema_version_must_be_integer(self) -> None:
        for invalid in (True, False, 1.0, "1", None):
            with self.subTest(value=invalid):
                manifest = finalize_metadata(self.metadata, self.candidate)
                payload = json.loads(manifest.read_text(encoding="utf-8"))
                payload["schemaVersion"] = invalid
                manifest.write_text(json.dumps(payload), encoding="utf-8")
                with self.assertRaisesRegex(StoreMetadataError, "schemaVersion"):
                    verify_metadata(self.metadata, self.candidate)

    def test_manifest_line_count_must_be_integer_not_boolean(self) -> None:
        manifest = finalize_metadata(self.metadata, self.candidate)
        payload = json.loads(manifest.read_text(encoding="utf-8"))
        title = next(entry for entry in payload["files"] if entry["file"] == "title.txt")
        title["lines"] = True
        manifest.write_text(json.dumps(payload), encoding="utf-8")

        with self.assertRaisesRegex(StoreMetadataError, "invalid file evidence types"):
            verify_metadata(self.metadata, self.candidate)

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

    def test_finalize_and_verify_require_lowercase_candidate_sha(self) -> None:
        with self.assertRaisesRegex(StoreMetadataError, "lowercase"):
            finalize_metadata(self.metadata, self.candidate.upper())
        finalize_metadata(self.metadata, self.candidate)
        with self.assertRaisesRegex(StoreMetadataError, "lowercase"):
            verify_metadata(self.metadata, self.candidate.upper())

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
                self.write_valid_metadata(self.metadata)
                (self.metadata / "metadata_manifest.json").unlink(missing_ok=True)
                (self.metadata / filename).write_text(text, encoding="utf-8")
                with self.assertRaisesRegex(StoreMetadataError, message):
                    finalize_metadata(self.metadata, self.candidate)

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

    def test_manifest_rejects_duplicate_keys_and_extra_schema_fields(self) -> None:
        manifest = finalize_metadata(self.metadata, self.candidate)
        original = json.loads(manifest.read_text(encoding="utf-8"))
        files_json = json.dumps(original["files"], separators=(",", ":"))
        manifest.write_text(
            "{"
            '"schemaVersion":1,'
            '"schemaVersion":1,'
            '"locale":"en-US",'
            f'"candidateSha":"{self.candidate}",'
            f'"files":{files_json}'
            "}",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(StoreMetadataError, "duplicate JSON object key"):
            verify_metadata(self.metadata, self.candidate)

        manifest = finalize_metadata(self.metadata, self.candidate)
        payload = json.loads(manifest.read_text(encoding="utf-8"))
        payload["unexpected"] = True
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        with self.assertRaisesRegex(StoreMetadataError, "fields are incomplete or contain extras"):
            verify_metadata(self.metadata, self.candidate)

    def test_symlinked_metadata_and_manifest_are_rejected(self) -> None:
        manifest = finalize_metadata(self.metadata, self.candidate)
        title = self.metadata / "title.txt"
        title_target = Path(self.temp.name, "title-target.txt")
        title_target.write_bytes(title.read_bytes())
        title.unlink()
        try:
            title.symlink_to(title_target)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")
        with self.assertRaisesRegex(StoreMetadataError, "symbolic link"):
            verify_metadata(self.metadata, self.candidate)

        title.unlink()
        title.write_text("Forest Run", encoding="utf-8")
        manifest_target = Path(self.temp.name, "manifest-target.json")
        manifest_target.write_bytes(manifest.read_bytes())
        manifest.unlink()
        manifest.symlink_to(manifest_target)
        with self.assertRaisesRegex(StoreMetadataError, "symbolic link"):
            verify_metadata(self.metadata, self.candidate)
        with self.assertRaisesRegex(StoreMetadataError, "symbolic link"):
            finalize_metadata(self.metadata, self.candidate)

    def test_symlinked_metadata_directory_is_rejected(self) -> None:
        real = Path(self.temp.name, "real-metadata")
        real.mkdir()
        self.write_valid_metadata(real)
        finalize_metadata(real, self.candidate)
        alias = Path(self.temp.name, "metadata-alias")
        try:
            alias.symlink_to(real, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")
        with self.assertRaisesRegex(StoreMetadataError, "symbolic link"):
            verify_metadata(alias, self.candidate)

    def test_symlinked_metadata_parent_is_rejected(self) -> None:
        real_parent = Path(self.temp.name, "real-parent")
        real = real_parent / "metadata"
        real.mkdir(parents=True)
        self.write_valid_metadata(real)
        finalize_metadata(real, self.candidate)
        parent_alias = Path(self.temp.name, "parent-alias")
        try:
            parent_alias.symlink_to(real_parent, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")

        alias = parent_alias / "metadata"
        with self.assertRaisesRegex(StoreMetadataError, "traverse a symbolic link"):
            verify_metadata(alias, self.candidate)
        with self.assertRaisesRegex(StoreMetadataError, "traverse a symbolic link"):
            finalize_metadata(alias, self.candidate)

    def test_finalize_does_not_create_metadata_beneath_symlinked_parent(self) -> None:
        outside = Path(self.temp.name, "outside")
        outside.mkdir()
        alias = Path(self.temp.name, "outside-alias")
        try:
            alias.symlink_to(outside, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"symbolic links unavailable: {exc}")
        destination = alias / "new" / "metadata"

        with self.assertRaisesRegex(StoreMetadataError, "traverse a symbolic link"):
            finalize_metadata(destination, self.candidate)
        self.assertFalse((outside / "new").exists())

    def test_metadata_file_replaced_by_symlink_after_inspection_is_rejected(self) -> None:
        source = self.metadata / "title.txt"
        target = Path(self.temp.name, "alternate-title.txt")
        target.write_text("Woodland!!", encoding="utf-8")
        original_open = store_metadata.os.open
        swapped = False

        def swap_before_open(path: object, flags: int, *args: object, **kwargs: object) -> int:
            nonlocal swapped
            if (Path(path) == source or path == source.name) and not swapped:
                swapped = True
                displaced = source.with_suffix(".displaced")
                source.replace(displaced)
                source.symlink_to(target)
            return original_open(path, flags, *args, **kwargs)

        with mock.patch.object(store_metadata.os, "open", side_effect=swap_before_open):
            with self.assertRaisesRegex(StoreMetadataError, "symbolic link|safely open|changed while"):
                store_metadata._read_regular_bytes(
                    source,
                    maximum_bytes=store_metadata.MAX_METADATA_FILE_BYTES,
                    label="store metadata",
                )
        self.assertTrue(swapped)

    def test_finalize_replaces_stale_manifest_atomically(self) -> None:
        manifest = self.metadata / "metadata_manifest.json"
        manifest.write_text("stale", encoding="utf-8")
        finalize_metadata(self.metadata, self.candidate)
        self.assertEqual(1, json.loads(manifest.read_text())["schemaVersion"])

    def test_manifest_parent_swap_cannot_redirect_or_delete_previous_manifest(self) -> None:
        manifest = finalize_metadata(self.metadata, self.candidate)
        old_bytes = manifest.read_bytes()
        external = Path(self.temp.name, "external")
        external.mkdir()
        saved_metadata = Path(self.temp.name, "metadata.saved")
        original_open = os.open
        swapped = False

        def swap_before_temp_open(path: object, flags: int, *args: object, **kwargs: object) -> int:
            nonlocal swapped
            if (
                isinstance(path, str)
                and path.startswith(".metadata_manifest.json.")
                and kwargs.get("dir_fd") is not None
                and not swapped
            ):
                self.metadata.rename(saved_metadata)
                self.metadata.symlink_to(external, target_is_directory=True)
                swapped = True
            return original_open(path, flags, *args, **kwargs)

        with mock.patch.object(
            stable_file_reader.os, "open", side_effect=swap_before_temp_open
        ):
            with self.assertRaisesRegex(StoreMetadataError, "symbolic link"):
                finalize_metadata(self.metadata, self.candidate)

        self.assertTrue(swapped)
        self.assertFalse((external / "metadata_manifest.json").exists())
        self.assertEqual(old_bytes, (saved_metadata / "metadata_manifest.json").read_bytes())


class StoreMetadataReleaseContractTest(unittest.TestCase):
    def test_canonical_release_verifies_metadata_before_play_preparer(self) -> None:
        source = (ROOT / "scripts/prepare_main_release.sh").read_text(encoding="utf-8")
        metadata_index = source.index("verify_store_metadata.py")
        preparer_index = source.index("prepare_play_release.py")
        self.assertLess(metadata_index, preparer_index)
        self.assertIn('--candidate-sha "${candidate_sha}"', source[metadata_index:preparer_index])
        self.assertIn('--metadata-dir "${ROOT}/release/google-play/metadata/en-US"', source)


if __name__ == "__main__":
    unittest.main()
