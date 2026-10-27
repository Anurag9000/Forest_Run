from __future__ import annotations

import hashlib
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import stable_file_reader as reader


class StableFileReaderTest(unittest.TestCase):
    @unittest.skipUnless(
        hasattr(os, "O_NONBLOCK") and hasattr(os, "mkfifo"),
        "nonblocking FIFO protection required",
    )
    def test_existing_output_is_opened_nonblocking_before_rollback_snapshot(self) -> None:
        for publisher_name in ("atomic_write", "atomic_replace", "atomic_publish_files"):
            with self.subTest(publisher=publisher_name), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                destination = root / "output.json"
                destination.write_bytes(b"previous")
                source = root / "staged.json"
                source.write_bytes(b"staged")
                original_open = os.open
                swapped = False

                def swap_destination_to_fifo(value, flags, *args, **kwargs):
                    nonlocal swapped
                    if (
                        not swapped
                        and value == destination.name
                        and kwargs.get("dir_fd") is not None
                        and flags & os.O_ACCMODE == os.O_RDONLY
                        and flags & os.O_NOFOLLOW
                    ):
                        destination.unlink()
                        os.mkfifo(destination)
                        swapped = True
                        if not flags & os.O_NONBLOCK:
                            raise AssertionError("rollback snapshot open must be nonblocking")
                    return original_open(value, flags, *args, **kwargs)

                if publisher_name == "atomic_write":
                    publish = lambda: reader.atomic_write_bytes(destination, b"new", "test")
                elif publisher_name == "atomic_replace":
                    publish = lambda: reader.atomic_replace_file(source, destination, "test")
                else:
                    publish = lambda: reader.atomic_publish_files(
                        [(destination, b"new")], "test"
                    )

                try:
                    with patch.object(reader.os, "open", side_effect=swap_destination_to_fifo):
                        with self.assertRaisesRegex(reader.StableFileError, "changed while preparing rollback"):
                            publish()
                    self.assertTrue(swapped)
                    self.assertTrue(destination.is_fifo())
                finally:
                    if destination.exists():
                        destination.unlink()

    def test_atomic_write_rollback_does_not_overwrite_concurrent_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            previous_mtime_ns = output.stat().st_mtime_ns
            real_fsync = os.fsync
            calls = 0

            def mutate_after_publish(descriptor):
                nonlocal calls
                calls += 1
                if calls == 3:
                    output.write_bytes(b"concurrent newer report")
                    raise OSError("injected directory sync failure")
                return real_fsync(descriptor)

            with patch.object(reader.os, "fsync", side_effect=mutate_after_publish):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    r"rollback failed: output changed (after publication|during rollback)",
                ):
                    reader.atomic_write_bytes(output, b"new", "test report")

            self.assertEqual(b"concurrent newer report", output.read_bytes())
            backups = list(root.glob(".report.json.*.bak"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"previous", backups[0].read_bytes())

    def test_atomic_write_rollback_exchange_preserves_writer_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            real_fsync = os.fsync
            real_exchange = reader._rename_exchange
            fsync_calls = 0
            exchange_calls = 0

            def fail_after_publication(descriptor):
                nonlocal fsync_calls
                fsync_calls += 1
                if fsync_calls == 3:
                    raise OSError("injected post-publication fsync failure")
                return real_fsync(descriptor)

            def concurrent_write_before_rollback(first, second, **kwargs):
                nonlocal exchange_calls
                exchange_calls += 1
                if exchange_calls == 2:
                    output.write_bytes(b"concurrent writer")
                return real_exchange(first, second, **kwargs)

            with (
                patch.object(reader.os, "fsync", side_effect=fail_after_publication),
                patch.object(
                    reader,
                    "_rename_exchange",
                    side_effect=concurrent_write_before_rollback,
                ),
                self.assertRaisesRegex(
                    reader.StableFileError,
                    "rollback failed: output changed during rollback; concurrent output restored",
                ),
            ):
                reader.atomic_write_bytes(output, b"new publication", "test output")

            self.assertEqual(3, exchange_calls)
            self.assertEqual(b"concurrent writer", output.read_bytes())
            backups = list(root.glob(".report.json.*.bak"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"previous", backups[0].read_bytes())

    def test_new_output_rollback_preserves_writer_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "new-report.json"
            real_fsync = os.fsync
            real_noreplace = reader._rename_noreplace
            fsync_calls = 0
            rename_calls = 0

            def fail_after_publication(descriptor):
                nonlocal fsync_calls
                fsync_calls += 1
                if fsync_calls == 2:
                    raise OSError("injected post-publication fsync failure")
                return real_fsync(descriptor)

            def concurrent_write_before_rollback(source, destination, **kwargs):
                nonlocal rename_calls
                rename_calls += 1
                if rename_calls == 2:
                    output.write_bytes(b"concurrent writer")
                return real_noreplace(source, destination, **kwargs)

            with (
                patch.object(reader.os, "fsync", side_effect=fail_after_publication),
                patch.object(
                    reader,
                    "_rename_noreplace",
                    side_effect=concurrent_write_before_rollback,
                ),
                self.assertRaisesRegex(
                    reader.StableFileError,
                    "rollback failed: output changed during rollback; concurrent output restored",
                ),
            ):
                reader.atomic_write_bytes(output, b"new publication", "test output")

            self.assertEqual(3, rename_calls)
            self.assertEqual(b"concurrent writer", output.read_bytes())
            self.assertEqual([output], list(root.iterdir()))

    def test_atomic_write_preserves_writer_between_identity_check_and_exchange(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            previous_mtime_ns = output.stat().st_mtime_ns
            original_exchange = reader._rename_exchange
            changed = False

            def concurrent_write_before_exchange(*args, **kwargs):
                nonlocal changed
                if not changed:
                    output.write_bytes(b"newer!!!")
                    metadata = output.stat()
                    os.utime(output, ns=(metadata.st_atime_ns, previous_mtime_ns))
                    changed = True
                return original_exchange(*args, **kwargs)

            with patch.object(
                reader, "_rename_exchange", side_effect=concurrent_write_before_exchange
            ):
                with self.assertRaisesRegex(reader.StableFileError, "changed before publication"):
                    reader.atomic_write_bytes(output, b"published report", "test report")

            self.assertTrue(changed)
            self.assertEqual(b"newer!!!", output.read_bytes())

    def test_atomic_write_cleanup_preserves_entry_swapped_into_temporary_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            replaced_name: str | None = None
            saved_name: str | None = None

            def replace_before_temporary_cleanup(source, destination, **kwargs):
                nonlocal replaced_name, saved_name
                if source.endswith(".tmp") and destination.endswith(".cleanup"):
                    replaced_name = source
                    saved_name = source + ".attacker-saved"
                    original_noreplace(
                        source,
                        saved_name,
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=kwargs["source_dir_fd"],
                    )
                    descriptor = original_open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent writer")
                    finally:
                        os.close(descriptor)
                return original_noreplace(source, destination, **kwargs)

            with patch.object(
                reader, "_rename_noreplace", side_effect=replace_before_temporary_cleanup
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "temporary .* output changed during cleanup",
                ):
                    reader.atomic_write_bytes(output, b"new publication", "test report")

            self.assertIsNotNone(replaced_name)
            self.assertIsNotNone(saved_name)
            self.assertEqual(b"new publication", output.read_bytes())
            self.assertEqual(b"concurrent writer", (root / replaced_name).read_bytes())
            self.assertEqual(b"previous", (root / saved_name).read_bytes())

    def test_atomic_write_cleanup_failure_restores_displaced_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            original_unlink = os.unlink
            failed = False

            def fail_cleanup_remove(name, *args, **kwargs):
                nonlocal failed
                if name.endswith(".cleanup") and not failed:
                    failed = True
                    raise PermissionError("injected cleanup denial")
                return original_unlink(name, *args, **kwargs)

            with patch.object(reader.os, "unlink", side_effect=fail_cleanup_remove):
                with self.assertRaisesRegex(reader.StableFileError, "could not remove temporary"):
                    reader.atomic_write_bytes(output, b"new publication", "test report")

            self.assertTrue(failed)
            self.assertEqual(b"new publication", output.read_bytes())
            preserved = [
                candidate
                for candidate in root.iterdir()
                if candidate.name.endswith(".tmp")
            ]
            self.assertEqual(1, len(preserved))
            self.assertEqual(b"previous", preserved[0].read_bytes())

    def test_atomic_write_cleanup_preserves_in_place_modified_displaced_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            mutated_name: str | None = None

            def mutate_before_temporary_cleanup(source, destination, **kwargs):
                nonlocal mutated_name
                if source.endswith(".tmp") and destination.endswith(".cleanup"):
                    mutated_name = source
                    descriptor = original_open(
                        source, os.O_WRONLY, dir_fd=kwargs["source_dir_fd"]
                    )
                    try:
                        os.pwrite(descriptor, b"modified", 0)
                    finally:
                        os.close(descriptor)
                return original_noreplace(source, destination, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=mutate_before_temporary_cleanup,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "temporary .* output changed during cleanup",
                ):
                    reader.atomic_write_bytes(output, b"new publication", "test report")

            self.assertIsNotNone(mutated_name)
            self.assertEqual(b"new publication", output.read_bytes())
            self.assertEqual(b"modified", (root / mutated_name).read_bytes())
            backups = list(root.glob(".report.json.*.bak"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"previous", backups[0].read_bytes())

    def test_atomic_write_cleanup_retains_entry_when_restore_name_is_taken(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            retained_name: str | None = None
            saved_name: str | None = None
            concurrent_name: str | None = None

            def replace_and_block_restore(source, destination, **kwargs):
                nonlocal retained_name, saved_name, concurrent_name
                source_fd = kwargs["source_dir_fd"]
                destination_fd = kwargs["destination_dir_fd"]
                if source.endswith(".tmp") and destination.endswith(".cleanup"):
                    concurrent_name = source
                    saved_name = source + ".attacker-saved"
                    original_noreplace(
                        source,
                        saved_name,
                        source_dir_fd=source_fd,
                        destination_dir_fd=destination_fd,
                    )
                    descriptor = original_open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=source_fd,
                    )
                    try:
                        os.write(descriptor, b"concurrent writer")
                    finally:
                        os.close(descriptor)
                elif source.endswith(".cleanup") and destination.endswith(".tmp"):
                    retained_name = source
                    descriptor = original_open(
                        destination,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=destination_fd,
                    )
                    try:
                        os.write(descriptor, b"later writer")
                    finally:
                        os.close(descriptor)
                return original_noreplace(
                    source,
                    destination,
                    source_dir_fd=source_fd,
                    destination_dir_fd=destination_fd,
                )

            with patch.object(
                reader, "_rename_noreplace", side_effect=replace_and_block_restore
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "concurrent entry retained at",
                ):
                    reader.atomic_write_bytes(output, b"new publication", "test report")

            self.assertIsNotNone(retained_name)
            self.assertIsNotNone(saved_name)
            self.assertIsNotNone(concurrent_name)
            self.assertEqual(b"new publication", output.read_bytes())
            self.assertEqual(b"later writer", (root / concurrent_name).read_bytes())
            self.assertEqual(b"concurrent writer", (root / retained_name).read_bytes())
            self.assertEqual(b"previous", (root / saved_name).read_bytes())

    def test_atomic_write_backup_cleanup_preserves_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "report.json"
            output.write_bytes(b"previous")
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            backup_name: str | None = None
            saved_name: str | None = None

            def replace_backup_before_cleanup(source, destination, **kwargs):
                nonlocal backup_name, saved_name
                if source.endswith(".bak") and destination.endswith(".cleanup"):
                    backup_name = source
                    saved_name = source + ".attacker-saved"
                    original_noreplace(
                        source,
                        saved_name,
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=kwargs["destination_dir_fd"],
                    )
                    descriptor = original_open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent backup")
                    finally:
                        os.close(descriptor)
                return original_noreplace(source, destination, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_backup_before_cleanup,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "previous .* output backup changed during cleanup",
                ):
                    reader.atomic_write_bytes(output, b"new publication", "test report")

            self.assertIsNotNone(backup_name)
            self.assertIsNotNone(saved_name)
            self.assertEqual(b"new publication", output.read_bytes())
            self.assertEqual(b"concurrent backup", (root / backup_name).read_bytes())
            self.assertEqual(b"previous", (root / saved_name).read_bytes())

    def test_all_publishers_preserve_writer_between_identity_check_and_exchange(self) -> None:
        for publisher_name in ("atomic_replace_file", "atomic_publish_files"):
            with self.subTest(publisher=publisher_name), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                output = root / "output.json"
                source = root / "staged.json"
                output.write_bytes(b"previous")
                source.write_bytes(b"staged")
                previous_mtime_ns = output.stat().st_mtime_ns
                original_exchange = reader._rename_exchange
                changed = False

                def concurrent_write_before_exchange(*args, **kwargs):
                    nonlocal changed
                    if not changed:
                        output.write_bytes(b"newer!!!")
                        metadata = output.stat()
                        os.utime(output, ns=(metadata.st_atime_ns, previous_mtime_ns))
                        changed = True
                    return original_exchange(*args, **kwargs)

                with patch.object(
                    reader, "_rename_exchange", side_effect=concurrent_write_before_exchange
                ):
                    with self.assertRaisesRegex(reader.StableFileError, "changed before"):
                        if publisher_name == "atomic_replace_file":
                            reader.atomic_replace_file(source, output, "test output")
                        else:
                            reader.atomic_publish_files([(output, b"staged")], "test outputs")

                self.assertTrue(changed)
                self.assertEqual(b"newer!!!", output.read_bytes())

    def test_new_output_publication_never_replaces_concurrent_creator(self) -> None:
        for publisher_name in ("atomic_write_bytes", "atomic_replace_file", "atomic_publish_files"):
            with self.subTest(publisher=publisher_name), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                output = root / "output.json"
                source = root / "staged.json"
                source.write_bytes(b"staged")
                original_publish = reader._rename_noreplace
                created = False

                def create_destination_before_publish(src, dst, **kwargs):
                    nonlocal created
                    if not created:
                        output.write_bytes(b"concurrent creator")
                        created = True
                    return original_publish(src, dst, **kwargs)

                with patch.object(
                    reader, "_rename_noreplace", side_effect=create_destination_before_publish
                ):
                    with self.assertRaises(reader.StableFileError):
                        if publisher_name == "atomic_write_bytes":
                            reader.atomic_write_bytes(output, b"new", "test output")
                        elif publisher_name == "atomic_replace_file":
                            reader.atomic_replace_file(source, output, "test output")
                        else:
                            reader.atomic_publish_files([(output, b"new")], "test outputs")

                self.assertTrue(created)
                self.assertEqual(b"concurrent creator", output.read_bytes())

    def test_atomic_replace_rejects_same_source_and_destination_before_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "same.json"
            source.write_bytes(b"preserve this file")
            with self.assertRaisesRegex(reader.StableFileError, "must be different paths"):
                reader.atomic_replace_file(source, source, "test output")
            self.assertEqual(b"preserve this file", source.read_bytes())
            self.assertEqual([source], list(source.parent.iterdir()))

    def test_atomic_replace_rollback_does_not_move_concurrent_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "staged.json"
            destination = root / "published.json"
            source.write_bytes(b"staged")
            destination.write_bytes(b"previous")
            real_fsync = os.fsync
            calls = 0

            def mutate_after_replace(descriptor):
                nonlocal calls
                calls += 1
                if calls == 2:
                    destination.write_bytes(b"concurrent newer output")
                    raise OSError("injected directory sync failure")
                return real_fsync(descriptor)

            with patch.object(reader.os, "fsync", side_effect=mutate_after_replace):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    r"rollback failed: destination changed (after publication|during rollback)",
                ):
                    reader.atomic_replace_file(source, destination, "test artifact")

            self.assertEqual(b"concurrent newer output", destination.read_bytes())
            self.assertEqual(b"previous", source.read_bytes())
            backups = list(root.glob(".published.json.*.bak"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"previous", backups[0].read_bytes())

    def test_atomic_replace_rollback_exchange_preserves_writer_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "staged.json"
            destination = root / "published.json"
            source.write_bytes(b"staged payload")
            destination.write_bytes(b"previous output")
            real_fsync = os.fsync
            real_exchange = reader._rename_exchange
            fsync_calls = 0
            exchange_calls = 0

            def fail_after_publication(descriptor):
                nonlocal fsync_calls
                fsync_calls += 1
                if fsync_calls == 2:
                    raise OSError("injected post-publication fsync failure")
                return real_fsync(descriptor)

            def concurrent_write_before_rollback(first, second, **kwargs):
                nonlocal exchange_calls
                exchange_calls += 1
                if exchange_calls == 2:
                    destination.write_bytes(b"concurrent writer")
                return real_exchange(first, second, **kwargs)

            with (
                patch.object(reader.os, "fsync", side_effect=fail_after_publication),
                patch.object(
                    reader,
                    "_rename_exchange",
                    side_effect=concurrent_write_before_rollback,
                ),
                self.assertRaisesRegex(
                    reader.StableFileError,
                    "rollback failed: destination changed during rollback; concurrent output restored",
                ),
            ):
                reader.atomic_replace_file(source, destination, "test artifact")

            self.assertEqual(3, exchange_calls)
            self.assertEqual(b"concurrent writer", destination.read_bytes())
            self.assertEqual(b"previous output", source.read_bytes())
            backups = list(root.glob(".published.json.*.bak"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"previous output", backups[0].read_bytes())

    def test_new_destination_rollback_preserves_writer_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "staged.json"
            destination = root / "published.json"
            source.write_bytes(b"staged payload")
            real_fsync = os.fsync
            real_noreplace = reader._rename_noreplace
            fsync_calls = 0
            rename_calls = 0

            def fail_after_publication(descriptor):
                nonlocal fsync_calls
                fsync_calls += 1
                if fsync_calls == 1:
                    raise OSError("injected post-publication fsync failure")
                return real_fsync(descriptor)

            def concurrent_write_before_rollback(src, dst, **kwargs):
                nonlocal rename_calls
                rename_calls += 1
                if rename_calls == 2:
                    destination.write_bytes(b"concurrent writer")
                return real_noreplace(src, dst, **kwargs)

            with (
                patch.object(reader.os, "fsync", side_effect=fail_after_publication),
                patch.object(
                    reader,
                    "_rename_noreplace",
                    side_effect=concurrent_write_before_rollback,
                ),
                self.assertRaisesRegex(
                    reader.StableFileError,
                    "rollback failed: destination changed during rollback; concurrent output restored",
                ),
            ):
                reader.atomic_replace_file(source, destination, "test artifact")

            self.assertEqual(3, rename_calls)
            self.assertEqual(b"concurrent writer", destination.read_bytes())
            self.assertFalse(source.exists())
            self.assertEqual([destination], list(root.iterdir()))

    @unittest.skipUnless(hasattr(os, "O_NOFOLLOW"), "no-follow directory opens required")
    def test_checked_directory_path_rejects_symlinked_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "evidence"
            target.mkdir()
            alias = root / "alias"
            alias.symlink_to(target, target_is_directory=True)
            with self.assertRaisesRegex(reader.StableFileError, "symbolic link"):
                reader.checked_directory_path(alias, "evidence root")

    def test_hashes_and_returns_the_same_bounded_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            payload = b"stable payload"
            path.write_bytes(payload)
            raw, digest, metadata = reader.consume_regular_file(
                path, "payload", len(payload), retain_bytes=True
            )
            self.assertEqual(payload, raw)
            self.assertEqual(hashlib.sha256(payload).hexdigest(), digest)
            self.assertEqual(len(payload), metadata.st_size)

    def test_hash_only_mode_returns_digest_and_metadata_without_retaining_content(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            payload = b"hash without retaining"
            path.write_bytes(payload)

            raw, digest, metadata = reader.consume_regular_file(
                path, "payload", len(payload), retain_bytes=False
            )

            self.assertIsNone(raw)
            self.assertEqual(hashlib.sha256(payload).hexdigest(), digest)
            self.assertEqual(len(payload), metadata.st_size)

    def test_consumer_rejects_empty_oversized_missing_and_non_regular_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            empty = root / "empty.bin"
            empty.write_bytes(b"")
            oversized = root / "oversized.bin"
            oversized.write_bytes(b"12345")
            directory = root / "directory"
            directory.mkdir()
            missing = root / "missing.bin"

            cases = (
                (empty, 10, "empty"),
                (oversized, 4, "safety limit"),
                (directory, 10, "regular non-symlink"),
                (missing, 10, "missing or changed"),
            )
            for path, limit, message in cases:
                with self.subTest(path=path.name), self.assertRaisesRegex(
                    reader.StableFileError, message
                ):
                    reader.consume_regular_file(path, "payload", limit, retain_bytes=True)

            if hasattr(os, "mkfifo"):
                fifo = root / "payload.fifo"
                os.mkfifo(fifo)
                with self.assertRaisesRegex(reader.StableFileError, "regular non-symlink"):
                    reader.consume_regular_file(fifo, "payload", 10, retain_bytes=True)

            if hasattr(os, "symlink"):
                alias = root / "alias.bin"
                alias.symlink_to(oversized)
                with self.assertRaisesRegex(reader.StableFileError, "regular non-symlink"):
                    reader.consume_regular_file(alias, "payload", 10, retain_bytes=True)

    def test_consumer_rejects_growth_after_the_initial_size_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"original")
            original_open = os.open
            original_read = os.read
            target_fd: int | None = None
            extended = False

            def observe_open(value, flags, *args, **kwargs):
                nonlocal target_fd
                descriptor = original_open(value, flags, *args, **kwargs)
                if value == path.name and kwargs.get("dir_fd") is not None:
                    target_fd = descriptor
                return descriptor

            def extend_after_read(descriptor, count):
                nonlocal extended
                chunk = original_read(descriptor, count)
                if descriptor == target_fd and chunk and not extended:
                    extended = True
                    with path.open("ab") as output:
                        output.write(b"changed")
                return chunk

            with patch.object(reader.os, "open", side_effect=observe_open), patch.object(
                reader.os, "read", side_effect=extend_after_read
            ):
                with self.assertRaisesRegex(reader.StableFileError, "changed while being read"):
                    reader.consume_regular_file(path, "payload", 100, retain_bytes=True)
            self.assertTrue(extended)

    def test_consumer_rejects_same_size_mutation_during_read(self) -> None:
        if not hasattr(os, "pwrite"):
            self.skipTest("positional writes are required for deterministic mutation")
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"original")
            original_open = os.open
            original_read = os.read
            target_fd: int | None = None
            mutated = False

            def observe_open(value, flags, *args, **kwargs):
                nonlocal target_fd
                descriptor = original_open(value, flags, *args, **kwargs)
                if value == path.name and kwargs.get("dir_fd") is not None:
                    target_fd = descriptor
                return descriptor

            def mutate_after_read(descriptor, count):
                nonlocal mutated
                chunk = original_read(descriptor, count)
                if descriptor == target_fd and chunk and not mutated:
                    mutated = True
                    writer = original_open(path, os.O_WRONLY)
                    try:
                        os.pwrite(writer, b"tampered", 0)
                    finally:
                        os.close(writer)
                return chunk

            with patch.object(reader.os, "open", side_effect=observe_open), patch.object(
                reader.os, "read", side_effect=mutate_after_read
            ):
                with self.assertRaisesRegex(reader.StableFileError, "changed while being read"):
                    reader.consume_regular_file(path, "payload", 100, retain_bytes=True)
            self.assertTrue(mutated)

    def test_consumer_rejects_truncation_during_read(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "payload.bin"
            path.write_bytes(b"original")
            original_open = os.open
            original_read = os.read
            target_fd: int | None = None
            truncated = False

            def observe_open(value, flags, *args, **kwargs):
                nonlocal target_fd
                descriptor = original_open(value, flags, *args, **kwargs)
                if value == path.name and kwargs.get("dir_fd") is not None:
                    target_fd = descriptor
                return descriptor

            def truncate_before_read(descriptor, count):
                nonlocal truncated
                if descriptor == target_fd and not truncated:
                    truncated = True
                    writer = original_open(path, os.O_WRONLY)
                    try:
                        os.ftruncate(writer, 0)
                    finally:
                        os.close(writer)
                return original_read(descriptor, count)

            with patch.object(reader.os, "open", side_effect=observe_open), patch.object(
                reader.os, "read", side_effect=truncate_before_read
            ):
                with self.assertRaisesRegex(reader.StableFileError, "changed while being read"):
                    reader.consume_regular_file(path, "payload", 100, retain_bytes=True)
            self.assertTrue(truncated)

    @unittest.skipUnless(hasattr(os, "O_NOFOLLOW"), "no-follow file opens required")
    def test_consumer_rejects_leaf_replaced_by_symlink_between_stat_and_open(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "payload.bin"
            path.write_bytes(b"inside")
            saved = root / "payload.saved"
            outside = root / "outside.bin"
            outside.write_bytes(b"outside")
            original_open = os.open
            swapped = False

            def replace_leaf_then_open(value, flags, *args, **kwargs):
                nonlocal swapped
                if value == path.name and kwargs.get("dir_fd") is not None and not swapped:
                    path.rename(saved)
                    path.symlink_to(outside)
                    swapped = True
                return original_open(value, flags, *args, **kwargs)

            with patch.object(reader.os, "open", side_effect=replace_leaf_then_open):
                with self.assertRaisesRegex(reader.StableFileError, "symbolic link"):
                    reader.consume_regular_file(path, "payload", 100, retain_bytes=True)
            self.assertTrue(swapped)
            self.assertTrue(path.is_symlink())
            self.assertEqual(b"inside", saved.read_bytes())

    def test_parent_swap_to_external_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "evidence"
            parent.mkdir()
            (parent / "payload.bin").write_bytes(b"evidence")
            saved_parent = root / "evidence.saved"
            external = root / "external"
            external.mkdir()
            (external / "payload.bin").write_bytes(b"external")
            original_open = os.open
            swapped = False

            def swap_then_open(value, flags, *args, **kwargs):
                nonlocal swapped
                if value == "evidence" and kwargs.get("dir_fd") is not None and not swapped:
                    parent.rename(saved_parent)
                    parent.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(value, flags, *args, **kwargs)

            with patch.object(reader.os, "open", side_effect=swap_then_open):
                with self.assertRaises(reader.StableFileError):
                    reader.consume_regular_file(
                        parent / "payload.bin", "evidence", 100, retain_bytes=True
                    )
            self.assertTrue(swapped)
            self.assertTrue(parent.is_symlink())

    def test_atomic_replace_requires_expected_regular_source_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "staged.json"
            destination = root / "published.json"
            source.write_bytes(b"verified")
            metadata = source.stat()
            identity = (
                metadata.st_dev,
                metadata.st_ino,
                metadata.st_size,
                metadata.st_mtime_ns,
                metadata.st_ctime_ns,
            )

            reader.atomic_replace_file(
                source,
                destination,
                "test publication",
                expected_source_identity=identity,
            )

            self.assertFalse(source.exists())
            self.assertEqual(b"verified", destination.read_bytes())

            replacement = root / "replacement.json"
            replacement.write_bytes(b"tampered")
            with self.assertRaisesRegex(reader.StableFileError, "source changed"):
                reader.atomic_replace_file(
                    replacement,
                    destination,
                    "test publication",
                    expected_source_identity=identity,
                )
            self.assertEqual(b"verified", destination.read_bytes())

    def test_atomic_replace_rechecks_source_after_destination_backup(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "staged.json"
            destination = root / "published.json"
            source.write_bytes(b"verified staged source")
            destination.write_bytes(b"previous destination")
            original_snapshot = reader._snapshot_regular_file

            def mutate_source_after_backup(*args, **kwargs):
                original_snapshot(*args, **kwargs)
                source.write_bytes(b"concurrent source replacement")

            with patch.object(
                reader,
                "_snapshot_regular_file",
                side_effect=mutate_source_after_backup,
            ):
                with self.assertRaisesRegex(reader.StableFileError, "source changed before"):
                    reader.atomic_replace_file(source, destination, "test artifact")

            self.assertEqual(b"concurrent source replacement", source.read_bytes())
            self.assertEqual(b"previous destination", destination.read_bytes())

    def test_atomic_replace_rejects_parent_swap_before_rename(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "publish"
            parent.mkdir()
            source = parent / "staged.json"
            source.write_bytes(b"verified")
            moved = root / "publish-moved"
            external = root / "external"
            external.mkdir()
            real_stat = os.stat
            swapped = False

            def swap_before_source_stat(path, *args, **kwargs):
                nonlocal swapped
                if path == source.name and kwargs.get("dir_fd") is not None:
                    swapped = True
                    parent.rename(moved)
                    parent.symlink_to(external, target_is_directory=True)
                return real_stat(path, *args, **kwargs)

            with patch.object(reader.os, "stat", side_effect=swap_before_source_stat):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "symbolic link or non-directory|could not verify.*recovery snapshot",
                ):
                    reader.atomic_replace_file(
                        source,
                        parent / "published.json",
                        "test publication",
                    )

            self.assertTrue(swapped)
            self.assertTrue((moved / source.name).is_file())
            self.assertFalse((moved / "published.json").exists())
            self.assertFalse((external / "published.json").exists())

    def test_atomic_write_rolls_back_if_parent_moves_during_rename(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "publish"
            parent.mkdir()
            output = parent / "report.json"
            output.write_bytes(b"previous")
            moved = root / "publish-moved"
            external = root / "external"
            external.mkdir()
            real_exchange = reader._rename_exchange
            swapped = False

            def swap_during_exchange(src, dst, *args, **kwargs):
                nonlocal swapped
                if not swapped:
                    swapped = True
                    parent.rename(moved)
                    parent.symlink_to(external, target_is_directory=True)
                return real_exchange(src, dst, **kwargs)

            with patch.object(reader, "_rename_exchange", side_effect=swap_during_exchange):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "changed during publication|symbolic link or non-directory",
                ):
                    reader.atomic_write_bytes(output, b"new", "test report")

            self.assertTrue(swapped)
            self.assertEqual(b"previous", (moved / output.name).read_bytes())
            self.assertFalse((external / output.name).exists())
            self.assertEqual([], list(moved.glob(".report.json.*.tmp")))
            self.assertEqual([], list(moved.glob(".report.json.*.bak")))

    def test_atomic_replace_rolls_back_if_parent_moves_during_rename(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "publish"
            parent.mkdir()
            source = parent / "staged.json"
            destination = parent / "published.json"
            source.write_bytes(b"verified")
            destination.write_bytes(b"previous")
            moved = root / "publish-moved"
            external = root / "external"
            external.mkdir()
            real_exchange = reader._rename_exchange
            swapped = False

            def swap_during_exchange(src, dst, *args, **kwargs):
                nonlocal swapped
                if not swapped:
                    swapped = True
                    parent.rename(moved)
                    parent.symlink_to(external, target_is_directory=True)
                return real_exchange(src, dst, **kwargs)

            with patch.object(reader, "_rename_exchange", side_effect=swap_during_exchange):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "changed during atomic replacement|symbolic link or non-directory",
                ):
                    reader.atomic_replace_file(source, destination, "test aggregate")

            self.assertTrue(swapped)
            self.assertEqual(b"verified", (moved / source.name).read_bytes())
            self.assertEqual(b"previous", (moved / destination.name).read_bytes())
            self.assertFalse((external / destination.name).exists())
            self.assertEqual([], list(moved.glob(".published.json.*.bak")))

    def test_atomic_multi_file_publish_restores_previous_outputs_on_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"previous first")
            second.write_bytes(b"previous second")
            real_exchange = reader._rename_exchange

            def fail_second_publish(src, dst, **kwargs):
                if src == "staged-1":
                    raise OSError("injected second output failure")
                return real_exchange(src, dst, **kwargs)

            with patch.object(reader, "_rename_exchange", side_effect=fail_second_publish):
                with self.assertRaisesRegex(reader.StableFileError, "second output failure"):
                    reader.atomic_publish_files(
                        [(first, b"new first"), (second, b"new second")],
                        "test output bundle",
                    )

            self.assertEqual(b"previous first", first.read_bytes())
            self.assertEqual(b"previous second", second.read_bytes())
            self.assertEqual([], list(root.glob(".*.txn")))

    def test_multi_file_rollback_preserves_concurrent_output_and_private_backup(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"previous first")
            second.write_bytes(b"previous second")
            real_exchange = reader._rename_exchange

            def mutate_published_output_then_fail(src, dst, **kwargs):
                if src == "staged-1":
                    first.write_bytes(b"concurrent mutation")
                    raise OSError("injected later publication failure")
                return real_exchange(src, dst, **kwargs)

            with patch.object(reader, "_rename_exchange", side_effect=mutate_published_output_then_fail):
                with self.assertRaisesRegex(reader.StableFileError, "later publication failure"):
                    reader.atomic_publish_files(
                        [(first, b"new first"), (second, b"new second")],
                        "test output bundle",
                    )

            self.assertEqual(b"concurrent mutation", first.read_bytes())
            self.assertEqual(b"previous second", second.read_bytes())
            transactions = list(root.glob(".*.txn"))
            self.assertEqual(1, len(transactions))
            self.assertEqual(b"previous first", (transactions[0] / "backup-0").read_bytes())

    def test_multi_file_rollback_does_not_overwrite_writer_after_identity_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"previous first")
            second.write_bytes(b"previous second")
            real_exchange = reader._rename_exchange
            injected = False

            def fail_second_publish(src, dst, **kwargs):
                nonlocal injected
                if src == "staged-1":
                    raise OSError("injected later publication failure")
                if src == "backup-0" and not injected:
                    injected = True
                    first.write_bytes(b"concurrent writer")
                return real_exchange(src, dst, **kwargs)

            with (
                patch.object(reader, "_rename_exchange", side_effect=fail_second_publish),
                self.assertRaisesRegex(reader.StableFileError, "rollback errors"),
            ):
                reader.atomic_publish_files(
                    [(first, b"new first"), (second, b"new second")],
                    "test output bundle",
                )

            self.assertTrue(injected)
            self.assertEqual(b"concurrent writer", first.read_bytes())
            self.assertEqual(b"previous second", second.read_bytes())
            transactions = list(root.glob(".*.txn"))
            self.assertEqual(1, len(transactions))
            self.assertEqual(b"previous first", (transactions[0] / "backup-0").read_bytes())

    def test_multi_file_new_output_rollback_restores_concurrent_writer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            real_noreplace = reader._rename_noreplace
            injected = False

            def interrupt_publish(src, dst, **kwargs):
                nonlocal injected
                if src == "staged-1":
                    raise OSError("injected later publication failure")
                if src == first.name and not injected:
                    injected = True
                    first.write_bytes(b"concurrent writer")
                return real_noreplace(src, dst, **kwargs)

            with (
                patch.object(reader, "_rename_noreplace", side_effect=interrupt_publish),
                self.assertRaisesRegex(reader.StableFileError, "rollback errors"),
            ):
                reader.atomic_publish_files(
                    [(first, b"new first"), (second, b"new second")],
                    "test output bundle",
                )

            self.assertTrue(injected)
            self.assertEqual(b"concurrent writer", first.read_bytes())
            self.assertFalse(second.exists())
            transactions = list(root.glob(".*.txn"))
            self.assertEqual(1, len(transactions))
            self.assertTrue((transactions[0] / "staged-1").exists())

    def test_multi_file_publisher_keeps_backup_if_output_changes_before_identity_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"previous first")
            second.write_bytes(b"previous second")
            real_exchange = reader._rename_exchange
            real_stat = os.stat
            replaced_first = False

            def exchange_then_mark(src, dst, **kwargs):
                nonlocal replaced_first
                result = real_exchange(src, dst, **kwargs)
                if src == "staged-0":
                    replaced_first = True
                if src == "staged-1":
                    raise OSError("injected later publication failure")
                return result

            def mutate_before_identity_capture(path, *args, **kwargs):
                if replaced_first and path == first.name and kwargs.get("dir_fd") is not None:
                    first.write_bytes(b"concurrent replacement before identity capture")
                    raise OSError("injected identity capture failure")
                return real_stat(path, *args, **kwargs)

            with (
                patch.object(reader, "_rename_exchange", side_effect=exchange_then_mark),
                patch.object(reader.os, "stat", side_effect=mutate_before_identity_capture),
            ):
                with self.assertRaisesRegex(reader.StableFileError, "rollback errors"):
                    reader.atomic_publish_files(
                        [(first, b"new first"), (second, b"new second")],
                        "test output bundle",
                    )

            self.assertEqual(
                b"concurrent replacement before identity capture",
                first.read_bytes(),
            )
            transactions = list(root.glob(".*.txn"))
            self.assertEqual(1, len(transactions))
            self.assertEqual(b"previous first", (transactions[0] / "backup-0").read_bytes())

    def test_atomic_multi_file_publish_reports_post_commit_cleanup_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            first.write_bytes(b"previous")
            real_unlink = os.unlink

            def fail_backup_cleanup(path, *args, **kwargs):
                if isinstance(path, str) and path.endswith(".cleanup"):
                    raise OSError("injected backup cleanup failure")
                return real_unlink(path, *args, **kwargs)

            with patch.object(reader.os, "unlink", side_effect=fail_backup_cleanup):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "outputs were published.*transaction retained at",
                ) as raised:
                    reader.atomic_publish_files([(first, b"committed")], "test outputs")

            self.assertEqual(b"committed", first.read_bytes())
            transaction = Path(str(raised.exception).rsplit("transaction retained at ", 1)[1])
            self.assertTrue(transaction.is_dir())
            self.assertEqual(b"previous", (transaction / "backup-0").read_bytes())

    def test_multi_file_cleanup_does_not_remove_replacement_transaction_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "published.json"
            saved_transaction: str | None = None
            replacement_transaction: str | None = None
            original_noreplace = reader._rename_noreplace
            injected = False

            def replace_transaction_before_cleanup(source, destination, **kwargs):
                nonlocal saved_transaction, replacement_transaction, injected
                if (
                    source.startswith(".published.json.")
                    and source.endswith(".txn")
                    and destination.endswith(".remove")
                    and not injected
                ):
                    injected = True
                    saved_transaction = source + ".attacker-saved"
                    replacement_transaction = source
                    original_noreplace(
                        source,
                        saved_transaction,
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=kwargs["destination_dir_fd"],
                    )
                    os.mkdir(source, mode=0o700, dir_fd=kwargs["source_dir_fd"])
                return original_noreplace(source, destination, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_transaction_before_cleanup,
            ):
                with self.assertRaisesRegex(reader.StableFileError, "changed during tree removal"):
                    reader.atomic_publish_files([(output, b"published")], "test output")

            self.assertTrue(injected)
            self.assertIsNotNone(saved_transaction)
            self.assertIsNotNone(replacement_transaction)
            self.assertEqual(b"published", output.read_bytes())
            self.assertTrue((root / saved_transaction).is_dir())
            self.assertTrue((root / replacement_transaction).is_dir())

    def test_multi_file_backup_cleanup_preserves_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "published.json"
            output.write_bytes(b"previous")
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            injected = False

            def replace_backup_before_cleanup(source, destination, **kwargs):
                nonlocal injected
                if source == "backup-0" and destination.endswith(".cleanup") and not injected:
                    injected = True
                    saved = source + ".attacker-saved"
                    original_noreplace(
                        source,
                        saved,
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=kwargs["destination_dir_fd"],
                    )
                    descriptor = original_open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent backup")
                    finally:
                        os.close(descriptor)
                return original_noreplace(source, destination, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_backup_before_cleanup,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "outputs were published but transaction cleanup failed",
                ) as raised:
                    reader.atomic_publish_files([(output, b"published")], "test output")

            transaction = Path(str(raised.exception).rsplit("transaction retained at ", 1)[1])
            self.assertTrue(injected)
            self.assertEqual(b"published", output.read_bytes())
            self.assertEqual(b"concurrent backup", (transaction / "backup-0").read_bytes())
            self.assertEqual(b"previous", (transaction / "backup-0.attacker-saved").read_bytes())

    def test_multi_file_staged_cleanup_preserves_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "published.json"
            original_noreplace = reader._rename_noreplace
            original_open = os.open
            injected = False

            def replace_staged_after_publish(source, destination, **kwargs):
                nonlocal injected
                result = original_noreplace(source, destination, **kwargs)
                if source == "staged-0" and destination == output.name and not injected:
                    injected = True
                    descriptor = original_open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent staged entry")
                    finally:
                        os.close(descriptor)
                return result

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_staged_after_publish,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "outputs were published but transaction cleanup failed",
                ) as raised:
                    reader.atomic_publish_files([(output, b"published")], "test output")

            transaction = Path(str(raised.exception).rsplit("transaction retained at ", 1)[1])
            self.assertTrue(injected)
            self.assertEqual(b"published", output.read_bytes())
            self.assertEqual(b"concurrent staged entry", (transaction / "staged-0").read_bytes())

    def test_quarantined_file_cleanup_restores_swap_at_rollback_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            name = "rollback-entry"
            path = root / name
            path.write_bytes(b"expected rollback bytes")
            directory_fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
            metadata = path.stat()
            identity = (metadata.st_dev, metadata.st_ino)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            original_noreplace = reader._rename_noreplace
            injected = False

            def replace_before_quarantine(source, destination, **kwargs):
                nonlocal injected
                if source == name and destination.endswith(".cleanup") and not injected:
                    injected = True
                    original_noreplace(
                        source,
                        source + ".saved",
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=kwargs["destination_dir_fd"],
                    )
                    descriptor = os.open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent rollback entry")
                    finally:
                        os.close(descriptor)
                return original_noreplace(source, destination, **kwargs)

            try:
                with patch.object(reader, "_rename_noreplace", side_effect=replace_before_quarantine):
                    with self.assertRaisesRegex(reader.StableFileError, "changed during cleanup"):
                        reader._remove_file_if_unchanged(
                            directory_fd, name, identity, digest, "test rollback"
                        )
                self.assertTrue(injected)
                self.assertEqual(b"expected rollback bytes", (root / (name + ".saved")).read_bytes())
                self.assertEqual(b"concurrent rollback entry", path.read_bytes())
            finally:
                os.close(directory_fd)

    def test_descriptor_tree_cleanup_does_not_use_python_recursion(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "deep"
            root.mkdir()
            parent_fd = os.open(root.parent, os.O_RDONLY | os.O_DIRECTORY)
            root_fd = os.open(root.name, os.O_RDONLY | os.O_DIRECTORY, dir_fd=parent_fd)
            current_fd = os.dup(root_fd)
            try:
                for _ in range(120):
                    os.mkdir("nested", dir_fd=current_fd)
                    following = os.open(
                        "nested", os.O_RDONLY | os.O_DIRECTORY, dir_fd=current_fd
                    )
                    os.close(current_fd)
                    current_fd = following
                previous_limit = sys.getrecursionlimit()
                try:
                    sys.setrecursionlimit(80)
                    reader.remove_directory_at(parent_fd, root.name)
                finally:
                    sys.setrecursionlimit(previous_limit)
                self.assertFalse(root.exists())
            finally:
                os.close(current_fd)
                os.close(root_fd)
                os.close(parent_fd)

    def test_descriptor_tree_cleanup_rejects_path_escapes_before_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            parent = root / "parent"
            parent.mkdir()
            sentinel = parent / "must-survive"
            sentinel.write_bytes(b"preserved")
            parent_fd = os.open(parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                for name in (".", "..", str(root), "nested/child", "", None, b"parent"):
                    with self.subTest(name=name):
                        with self.assertRaisesRegex(
                            reader.StableFileError,
                            "one normal directory name",
                        ):
                            reader.remove_directory_at(parent_fd, name)
                self.assertEqual(b"preserved", sentinel.read_bytes())
                self.assertTrue(parent.is_dir())
                self.assertEqual(["must-survive"], [entry.name for entry in parent.iterdir()])
            finally:
                os.close(parent_fd)

    def test_descriptor_tree_cleanup_does_not_remove_replacement_at_postorder_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tree = root / "tree"
            moved_original = root / "tree-original"
            tree.mkdir()
            parent_fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
            real_noreplace = reader._rename_noreplace
            swapped = False

            def replace_directory_before_quarantine(src, dst, **kwargs):
                nonlocal swapped
                if src == tree.name and dst.endswith(".remove") and not swapped:
                    swapped = True
                    tree.rename(moved_original)
                    tree.mkdir()
                return real_noreplace(src, dst, **kwargs)

            try:
                with patch.object(
                    reader,
                    "_rename_noreplace",
                    side_effect=replace_directory_before_quarantine,
                ):
                    with self.assertRaisesRegex(
                        reader.StableFileError,
                        "directory changed during tree removal",
                    ):
                        reader.remove_directory_at(parent_fd, tree.name)
                self.assertTrue(swapped)
                self.assertTrue(tree.is_dir())
                self.assertTrue(moved_original.is_dir())
            finally:
                os.close(parent_fd)

    def test_descriptor_tree_cleanup_preserves_replacement_child(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tree = root / "tree"
            tree.mkdir()
            (tree / "child.txt").write_bytes(b"original child")
            parent_fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
            real_noreplace = reader._rename_noreplace
            swapped = False

            def replace_child_before_quarantine(source, destination, **kwargs):
                nonlocal swapped
                if source == "child.txt" and destination.endswith(".remove") and not swapped:
                    swapped = True
                    real_noreplace(
                        source,
                        "saved-child.txt",
                        source_dir_fd=kwargs["source_dir_fd"],
                        destination_dir_fd=parent_fd,
                    )
                    descriptor = os.open(
                        source,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                        0o600,
                        dir_fd=kwargs["source_dir_fd"],
                    )
                    try:
                        os.write(descriptor, b"concurrent replacement")
                    finally:
                        os.close(descriptor)
                return real_noreplace(source, destination, **kwargs)

            try:
                with patch.object(reader, "_rename_noreplace", side_effect=replace_child_before_quarantine):
                    with self.assertRaisesRegex(reader.StableFileError, "entry changed during tree removal"):
                        reader.remove_directory_at(parent_fd, tree.name)
                self.assertTrue(swapped)
                self.assertEqual(b"original child", (root / "saved-child.txt").read_bytes())
                self.assertEqual(b"concurrent replacement", (tree / "child.txt").read_bytes())
            finally:
                os.close(parent_fd)

    def test_directory_publisher_rejects_dotdot_sibling_before_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            destination = root / "output"
            staging.mkdir()
            (staging / "new.txt").write_text("new", encoding="utf-8")
            destination.mkdir()
            (destination / "old.txt").write_text("old", encoding="utf-8")
            with self.assertRaisesRegex(
                reader.StableFileError,
                "distinct siblings|normal sibling names",
            ):
                reader.atomic_publish_directory(
                    staging,
                    destination,
                    root / "..",
                    "test directory",
                )
            self.assertEqual("new", (staging / "new.txt").read_text())
            self.assertEqual("old", (destination / "old.txt").read_text())

    def test_directory_publisher_never_replaces_concurrent_empty_destination(self) -> None:
        for has_previous in (False, True):
            with self.subTest(has_previous=has_previous), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                staging = root / "stage"
                destination = root / "output"
                backup = root / "backup"
                staging.mkdir()
                (staging / "new.txt").write_text("new", encoding="utf-8")
                if has_previous:
                    destination.mkdir()
                    (destination / "old.txt").write_text("old", encoding="utf-8")
                real_noreplace = reader._rename_noreplace
                created = False

                def create_destination_after_check(src, dst, **kwargs):
                    nonlocal created
                    if src == staging.name and dst == destination.name and not created:
                        created = True
                        destination.mkdir()
                    return real_noreplace(src, dst, **kwargs)

                with patch.object(
                    reader,
                    "_rename_noreplace",
                    side_effect=create_destination_after_check,
                ):
                    with self.assertRaises(reader.StableFileError):
                        reader.atomic_publish_directory(
                            staging, destination, backup, "test directory"
                        )

                self.assertTrue(created)
                self.assertTrue(destination.is_dir())
                self.assertEqual([], list(destination.iterdir()))
                self.assertEqual("new", (staging / "new.txt").read_text())
                if has_previous:
                    self.assertEqual("old", (backup / "old.txt").read_text())
                else:
                    self.assertFalse(backup.exists())

    def test_directory_publisher_never_replaces_concurrent_backup_directory(self) -> None:
        for moving_existing_destination in (False, True):
            with self.subTest(moving_existing_destination=moving_existing_destination), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                staging = root / "stage"
                destination = root / "output"
                backup = root / "backup"
                staging.mkdir()
                (staging / "new.txt").write_text("new", encoding="utf-8")
                if moving_existing_destination:
                    destination.mkdir()
                    (destination / "old.txt").write_text("old", encoding="utf-8")
                    source_name = destination.name
                    destination_name = backup.name
                else:
                    backup.mkdir()
                    (backup / "old.txt").write_text("old", encoding="utf-8")
                    source_name = backup.name
                    destination_name = destination.name
                real_noreplace = reader._rename_noreplace
                created = False

                def create_backup_target_after_check(src, dst, **kwargs):
                    nonlocal created
                    if src == source_name and dst == destination_name and not created:
                        created = True
                        (backup if moving_existing_destination else destination).mkdir()
                    return real_noreplace(src, dst, **kwargs)

                with patch.object(
                    reader,
                    "_rename_noreplace",
                    side_effect=create_backup_target_after_check,
                ):
                    with self.assertRaises(reader.StableFileError):
                        reader.atomic_publish_directory(
                            staging, destination, backup, "test directory"
                        )

                self.assertTrue(created)
                self.assertEqual("new", (staging / "new.txt").read_text())
                self.assertTrue((backup if moving_existing_destination else destination).is_dir())
                self.assertEqual(
                    "old",
                    (
                        (destination / "old.txt")
                        if moving_existing_destination
                        else (backup / "old.txt")
                    ).read_text(),
                )
                self.assertEqual(
                    [],
                    list((backup if moving_existing_destination else destination).iterdir()),
                )

    def test_directory_publisher_rechecks_staging_identity_after_rename(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            displaced_staging = root / "stage-original"
            destination = root / "output"
            backup = root / "backup"
            staging.mkdir()
            (staging / "candidate.txt").write_text("candidate", encoding="utf-8")
            destination.mkdir()
            (destination / "previous.txt").write_text("previous", encoding="utf-8")
            real_noreplace = reader._rename_noreplace
            replaced = False

            def replace_staging_before_rename(src, dst, **kwargs):
                nonlocal replaced
                if src == staging.name and dst == destination.name and not replaced:
                    replaced = True
                    staging.rename(displaced_staging)
                    staging.mkdir()
                    (staging / "unexpected.txt").write_text("unexpected", encoding="utf-8")
                return real_noreplace(src, dst, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_staging_before_rename,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "does not match the validated staging directory|changed before rollback",
                ):
                    reader.atomic_publish_directory(
                        staging, destination, backup, "test directory"
                    )

            self.assertTrue(replaced)
            self.assertEqual("unexpected", (destination / "unexpected.txt").read_text())
            self.assertEqual("candidate", (displaced_staging / "candidate.txt").read_text())
            self.assertEqual("previous", (backup / "previous.txt").read_text())

    def test_directory_publisher_does_not_delete_replaced_destination_during_backup(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            destination = root / "output"
            displaced_original = root / "original-output"
            backup = root / "backup"
            staging.mkdir()
            (staging / "new.txt").write_text("new", encoding="utf-8")
            destination.mkdir()
            (destination / "old.txt").write_text("old", encoding="utf-8")
            real_noreplace = reader._rename_noreplace
            swapped = False

            def replace_destination_before_backup(src, dst, **kwargs):
                nonlocal swapped
                if src == destination.name and dst == backup.name and not swapped:
                    swapped = True
                    destination.rename(displaced_original)
                    destination.mkdir()
                    (destination / "concurrent.txt").write_text(
                        "concurrent", encoding="utf-8"
                    )
                return real_noreplace(src, dst, **kwargs)

            with patch.object(
                reader,
                "_rename_noreplace",
                side_effect=replace_destination_before_backup,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "destination changed while moving it to the backup path",
                ):
                    reader.atomic_publish_directory(
                        staging, destination, backup, "test directory"
                    )

            self.assertTrue(swapped)
            self.assertEqual("concurrent", (destination / "concurrent.txt").read_text())
            self.assertEqual("old", (displaced_original / "old.txt").read_text())
            self.assertEqual("new", (staging / "new.txt").read_text())
            self.assertFalse(backup.exists())

    def test_directory_publisher_cleanup_rejects_replaced_backup_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            destination = root / "output"
            backup = root / "backup"
            moved_backup = root / "backup-original"
            staging.mkdir()
            (staging / "new.txt").write_text("new", encoding="utf-8")
            destination.mkdir()
            (destination / "old.txt").write_text("old", encoding="utf-8")
            real_remove = reader.remove_directory_at
            swapped = False

            def replace_backup_before_cleanup(parent_fd, name, **kwargs):
                nonlocal swapped
                if name == backup.name and not swapped:
                    swapped = True
                    backup.rename(moved_backup)
                    backup.mkdir()
                    (backup / "concurrent.txt").write_text("preserve", encoding="utf-8")
                return real_remove(parent_fd, name, **kwargs)

            with patch.object(
                reader,
                "remove_directory_at",
                side_effect=replace_backup_before_cleanup,
            ):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "backup cleanup failed.*changed before tree removal",
                ):
                    reader.atomic_publish_directory(
                        staging, destination, backup, "test directory"
                    )

            self.assertTrue(swapped)
            self.assertEqual("new", (destination / "new.txt").read_text())
            self.assertEqual("preserve", (backup / "concurrent.txt").read_text())
            self.assertEqual("old", (moved_backup / "old.txt").read_text())

    def test_directory_publisher_reports_partial_backup_after_cleanup_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            destination = root / "output"
            backup = root / "backup"
            staging.mkdir()
            (staging / "new.txt").write_text("new", encoding="utf-8")
            destination.mkdir()
            (destination / "first.txt").write_text("first", encoding="utf-8")
            (destination / "second.txt").write_text("second", encoding="utf-8")
            real_unlink = os.unlink
            removed = 0

            def fail_second_backup_unlink(name, *args, **kwargs):
                nonlocal removed
                if kwargs.get("dir_fd") is not None and isinstance(name, str) and name.endswith(".remove"):
                    removed += 1
                    if removed == 2:
                        raise PermissionError("injected backup cleanup failure")
                return real_unlink(name, *args, **kwargs)

            with patch.object(reader.os, "unlink", side_effect=fail_second_backup_unlink):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "backup cleanup failed.*partial backup",
                ) as raised:
                    reader.atomic_publish_directory(staging, destination, backup, "test directory")

            self.assertEqual("new", (destination / "new.txt").read_text())
            self.assertTrue(backup.is_dir())
            remaining = list(backup.iterdir())
            self.assertEqual(1, len(remaining))
            self.assertIn(str(backup), str(raised.exception))

    def test_directory_publisher_normalizes_fsync_errors_after_backup_restore(self) -> None:
        for failing_calls in ((1, 2), (2, 4)):
            with self.subTest(failing_calls=failing_calls), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                staging = root / "stage"
                destination = root / "output"
                backup = root / "backup"
                staging.mkdir()
                (staging / "new.txt").write_text("new", encoding="utf-8")
                destination.mkdir()
                (destination / "old.txt").write_text("old", encoding="utf-8")
                real_fsync = os.fsync
                calls = 0

                def fail_selected_fsync(descriptor):
                    nonlocal calls
                    calls += 1
                    if calls in failing_calls:
                        raise OSError(f"injected directory fsync failure {calls}")
                    return real_fsync(descriptor)

                with patch.object(reader.os, "fsync", side_effect=fail_selected_fsync):
                    with self.assertRaisesRegex(
                        reader.StableFileError,
                        "publication failed.*previous directory.*sync failed",
                    ):
                        reader.atomic_publish_directory(
                            staging,
                            destination,
                            backup,
                            "test directory",
                        )

                self.assertEqual("old", (destination / "old.txt").read_text())
                self.assertFalse(backup.exists())
                self.assertTrue(staging.is_dir())

    def test_directory_publisher_reports_parent_sync_failure_after_backup_removal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            staging = root / "stage"
            destination = root / "output"
            backup = root / "backup"
            staging.mkdir()
            (staging / "new.txt").write_text("new", encoding="utf-8")
            destination.mkdir()
            (destination / "old.txt").write_text("old", encoding="utf-8")
            real_fsync = os.fsync
            calls = 0

            def fail_final_fsync(descriptor):
                nonlocal calls
                calls += 1
                if calls == 3:
                    raise OSError("injected post-cleanup directory fsync failure")
                return real_fsync(descriptor)

            with patch.object(reader.os, "fsync", side_effect=fail_final_fsync):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "published.*backup was removed.*directory sync failed",
                ):
                    reader.atomic_publish_directory(staging, destination, backup, "test directory")

            self.assertEqual("new", (destination / "new.txt").read_text())
            self.assertFalse(backup.exists())

    def test_atomic_multi_file_publish_rolls_back_parent_swap_during_rename(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "outputs"
            root.mkdir()
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"previous first")
            moved = root.with_name("outputs-moved")
            external = root.with_name("outputs-external")
            external.mkdir()
            real_exchange = reader._rename_exchange
            swapped = False

            def swap_during_first_publish(src, dst, **kwargs):
                nonlocal swapped
                if not swapped and src == "staged-0":
                    swapped = True
                    root.rename(moved)
                    root.symlink_to(external, target_is_directory=True)
                return real_exchange(src, dst, **kwargs)

            with patch.object(reader, "_rename_exchange", side_effect=swap_during_first_publish):
                with self.assertRaisesRegex(
                    reader.StableFileError,
                    "changed during publication|symbolic link or non-directory",
                ):
                    reader.atomic_publish_files(
                        [(first, b"new first"), (second, b"new second")],
                        "test output bundle",
                    )

            self.assertTrue(swapped)
            self.assertEqual(b"previous first", (moved / first.name).read_bytes())
            self.assertFalse((moved / second.name).exists())
            self.assertFalse((external / first.name).exists())
            self.assertFalse((external / second.name).exists())
            self.assertEqual([], list(moved.glob(".*.txn")))


if __name__ == "__main__":
    unittest.main()
