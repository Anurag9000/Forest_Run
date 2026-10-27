import io
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from publish_screenshot_capture_set import (
    ScreenshotSetPublishError,
    main as publisher_main,
    promote_capture_set,
    validate_output_directory,
)
import stable_file_reader as stable_files


class ScreenshotSetPublisherTest(unittest.TestCase):
    def test_unmanaged_nested_tree_and_symlink_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            nested = output / "operator" / "notes"
            nested.mkdir(parents=True)
            staging.mkdir()
            note = nested / "keep.txt"
            note.write_text("keep", encoding="utf-8")
            note.chmod(0o640)
            expected_mtime = 1_600_000_000_000_000_000
            os.utime(note, ns=(expected_mtime, expected_mtime))
            (output / "operator-link").symlink_to("operator", target_is_directory=True)
            (staging / "new.png").write_bytes(b"capture")

            promote_capture_set(staging, output)

            copied = output / "operator" / "notes" / "keep.txt"
            self.assertEqual("keep", copied.read_text(encoding="utf-8"))
            self.assertEqual(0o640, copied.stat().st_mode & 0o777)
            self.assertEqual(expected_mtime, copied.stat().st_mtime_ns)
            self.assertTrue((output / "operator-link").is_symlink())
            self.assertEqual("operator", os.readlink(output / "operator-link"))

    def test_staged_unmanaged_entry_wins_and_managed_files_are_not_copied(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "operator.txt").write_text("old", encoding="utf-8")
            (staging / "operator.txt").write_text("staged", encoding="utf-8")
            (output / "capture-session.json").write_text("old session", encoding="utf-8")
            (output / "old.png").write_bytes(b"old capture")
            (output / "old.capture.json").write_text("old sidecar", encoding="utf-8")
            (staging / "new.png").write_bytes(b"new capture")

            promote_capture_set(staging, output)

            self.assertEqual("staged", (output / "operator.txt").read_text(encoding="utf-8"))
            self.assertFalse((output / "capture-session.json").exists())
            self.assertFalse((output / "old.png").exists())
            self.assertFalse((output / "old.capture.json").exists())

    def test_unsupported_unmanaged_fifo_aborts_without_replacing_output(self):
        if not hasattr(os, "mkfifo"):
            self.skipTest("FIFO creation unavailable")
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            os.mkfifo(output / "operator-pipe")
            (staging / "replacement.png").write_bytes(b"replacement")

            with self.assertRaisesRegex(ScreenshotSetPublishError, "unsupported output entry"):
                promote_capture_set(staging, output)

            self.assertEqual(b"accepted", (output / "accepted.png").read_bytes())
            self.assertTrue((output / "operator-pipe").is_fifo())
            self.assertTrue(staging.is_dir())

    def test_nested_directory_mutation_during_copy_aborts_publication(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            nested = output / "operator"
            nested.mkdir(parents=True)
            staging.mkdir()
            (nested / "note.txt").write_text("note", encoding="utf-8")
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")
            real_listdir = os.listdir
            mutated = False

            def mutate_after_listing(directory_fd):
                nonlocal mutated
                names = real_listdir(directory_fd)
                identity = os.fstat(directory_fd)
                if identity.st_ino == nested.stat().st_ino and not mutated:
                    mutated = True
                    (nested / "late.txt").write_text("late", encoding="utf-8")
                return names

            with patch("publish_screenshot_capture_set.os.listdir", mutate_after_listing):
                with self.assertRaisesRegex(ScreenshotSetPublishError, "directory changed"):
                    promote_capture_set(staging, output)

            self.assertTrue(mutated)
            self.assertEqual(b"accepted", (output / "accepted.png").read_bytes())
            self.assertTrue(staging.is_dir())

    def test_publication_replaces_old_set_and_preserves_unmanaged_files(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "01-old.png").write_bytes(b"old capture")
            (output / "operator-note.txt").write_text("retain", encoding="utf-8")
            (staging / "01-new.png").write_bytes(b"new capture")
            (staging / "capture-session.json").write_text("{}", encoding="utf-8")

            promote_capture_set(staging, output)

            self.assertFalse(staging.exists())
            self.assertFalse((output / "01-old.png").exists())
            self.assertEqual(b"new capture", (output / "01-new.png").read_bytes())
            self.assertEqual("retain", (output / "operator-note.txt").read_text())
            self.assertEqual("{}", (output / "capture-session.json").read_text())

    def test_publish_failure_restores_previous_directory(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")
            real_rename = os.rename
            rename_calls = 0

            def fail_stage_move(source, destination, *args, **kwargs):
                nonlocal rename_calls
                rename_calls += 1
                if rename_calls == 2:
                    raise OSError("injected stage publication failure")
                real_rename(source, destination, *args, **kwargs)

            with patch("publish_screenshot_capture_set.os.rename", fail_stage_move):
                with self.assertRaisesRegex(ScreenshotSetPublishError, "atomically publish"):
                    promote_capture_set(staging, output)

            self.assertEqual(b"accepted", (output / "accepted.png").read_bytes())
            self.assertFalse((output / "replacement.png").exists())
            self.assertTrue(staging.is_dir())
            self.assertEqual(
                [".raw.capture.stage", "raw"],
                sorted(path.name for path in parent.iterdir()),
            )

    def test_failed_rollback_retains_previous_capture_set(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")
            real_rename = os.rename
            calls = 0

            def fail_publish_and_restore(source, destination, *args, **kwargs):
                nonlocal calls
                calls += 1
                if calls in {2, 3}:
                    raise OSError(f"injected replacement failure {calls}")
                real_rename(source, destination, *args, **kwargs)

            with patch("publish_screenshot_capture_set.os.rename", fail_publish_and_restore):
                with self.assertRaises(ScreenshotSetPublishError) as raised:
                    promote_capture_set(staging, output)

            message = str(raised.exception)
            self.assertIn("previous set remains at", message)
            backup = Path(message.split("previous set remains at ", 1)[1].split(": publish=", 1)[0])
            self.assertTrue(backup.is_dir())
            self.assertEqual(b"accepted", (backup / "accepted.png").read_bytes())
            self.assertFalse(output.exists())

    def test_parent_swap_during_publish_rolls_back_inside_held_directory(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            parent = root / "parent"
            parent.mkdir()
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")
            moved = root / "parent-moved"
            external = root / "external"
            external.mkdir()
            real_rename = os.rename
            swapped = False

            def swap_parent_on_staging_publish(source, destination, *args, **kwargs):
                nonlocal swapped
                if source == staging.name and not swapped:
                    swapped = True
                    real_rename(parent, moved)
                    parent.symlink_to(external, target_is_directory=True)
                return real_rename(source, destination, *args, **kwargs)

            with patch(
                "publish_screenshot_capture_set.os.rename",
                swap_parent_on_staging_publish,
            ):
                with self.assertRaisesRegex(ScreenshotSetPublishError, "parent changed"):
                    promote_capture_set(staging, output)

            self.assertTrue(swapped)
            self.assertTrue(parent.is_symlink())
            self.assertEqual(b"accepted", (moved / "raw" / "accepted.png").read_bytes())
            self.assertTrue((moved / ".raw.capture.stage" / "replacement.png").is_file())
            self.assertEqual([], list(external.iterdir()))

    def test_backup_cleanup_failure_reports_committed_output_and_recovery_path(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")

            with patch(
                "publish_screenshot_capture_set.remove_directory_at",
                side_effect=OSError("injected backup cleanup failure"),
            ):
                with self.assertRaisesRegex(
                    ScreenshotSetPublishError,
                    "was published but backup cleanup failed",
                ) as raised:
                    promote_capture_set(staging, output)

            self.assertEqual(b"replacement", (output / "replacement.png").read_bytes())
            self.assertFalse((output / "accepted.png").exists())
            recovery = Path(str(raised.exception).split("partial backup may remain at ", 1)[1])
            self.assertEqual(b"accepted", (recovery / "accepted.png").read_bytes())

    def test_post_cleanup_fsync_failure_reports_that_backup_was_removed(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            output.mkdir()
            staging.mkdir()
            (output / "accepted.png").write_bytes(b"accepted")
            (staging / "replacement.png").write_bytes(b"replacement")
            real_fsync = os.fsync
            calls = 0

            def fail_post_cleanup_fsync(descriptor):
                nonlocal calls
                calls += 1
                if calls == 3:
                    raise OSError("injected post-cleanup directory fsync failure")
                return real_fsync(descriptor)

            with patch("publish_screenshot_capture_set.os.fsync", side_effect=fail_post_cleanup_fsync):
                with self.assertRaisesRegex(
                    ScreenshotSetPublishError,
                    "published and backup was removed.*directory sync failed",
                ):
                    promote_capture_set(staging, output)

            self.assertEqual(b"replacement", (output / "replacement.png").read_bytes())
            self.assertFalse((output / "accepted.png").exists())
            self.assertFalse(any(parent.glob(".raw.backup.*")))

    def test_publication_creates_output_when_no_previous_set_exists(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            staging = parent / ".raw.capture.stage"
            staging.mkdir()
            (staging / "capture-session.json").write_text("{}", encoding="utf-8")

            promote_capture_set(staging, output)

            self.assertTrue(output.is_dir())
            self.assertEqual("{}", (output / "capture-session.json").read_text())
            self.assertFalse(staging.exists())

    def test_symlinked_output_is_rejected_without_touching_target(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            parent = Path(temporary_directory)
            output = parent / "raw"
            target = parent / "outside"
            staging = parent / ".raw.capture.stage"
            target.mkdir()
            staging.mkdir()
            (target / "accepted.png").write_bytes(b"accepted")
            try:
                output.symlink_to(target, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")

            with self.assertRaisesRegex(ScreenshotSetPublishError, "symbolic link"):
                promote_capture_set(staging, output)

            self.assertEqual(b"accepted", (target / "accepted.png").read_bytes())
            self.assertTrue(staging.is_dir())

    def test_symlinked_output_ancestor_is_rejected_during_preflight(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            real_parent = root / "real-parent"
            alias_parent = root / "parent-alias"
            real_parent.mkdir()
            try:
                alias_parent.symlink_to(real_parent, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")

            with self.assertRaisesRegex(ScreenshotSetPublishError, "symbolic link"):
                validate_output_directory(alias_parent / "raw")

            self.assertEqual([], list(real_parent.iterdir()))

    def test_staging_and_output_must_share_parent(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            staging_parent = root / "staging"
            output_parent = root / "output"
            staging_parent.mkdir()
            output_parent.mkdir()
            staging = staging_parent / "set"
            staging.mkdir()
            output = output_parent / "set"

            with self.assertRaisesRegex(ScreenshotSetPublishError, "share a parent"):
                promote_capture_set(staging, output)

            self.assertTrue(staging.is_dir())
            self.assertFalse(output.exists())

    def test_staging_and_output_must_be_distinct(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "raw"
            output.mkdir()
            with self.assertRaisesRegex(ScreenshotSetPublishError, "must be distinct"):
                promote_capture_set(output, output)
            self.assertTrue(output.is_dir())

    def test_check_only_cli_validates_and_reports_output_directory(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "raw"
            captured = io.StringIO()
            with redirect_stdout(captured):
                result = publisher_main(["--output-dir", str(output), "--check-only"])
            self.assertEqual(0, result)
            self.assertEqual(f"{output}\n", captured.getvalue())

    def test_cli_requires_staging_directory_for_publication(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "raw"
            with self.assertRaisesRegex(SystemExit, "--staging-dir is required"):
                publisher_main(["--output-dir", str(output)])


if __name__ == "__main__":
    unittest.main()
