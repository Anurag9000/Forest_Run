#!/usr/bin/env python3
"""Atomically replace a published raw screenshot directory with a validated set."""

from __future__ import annotations

import argparse
import os
import secrets
import stat
from pathlib import Path
from typing import Sequence

from stable_file_reader import (
    StableFileError,
    open_directory_descriptor,
    remove_directory_at,
)


class ScreenshotSetPublishError(ValueError):
    """Raised when a staged screenshot set cannot be safely published."""


def _copy_unmanaged_entries(source_fd: int, staging_fd: int, label: str = "output") -> None:
    for name in os.listdir(source_fd):
        if (
            name == "capture-session.json"
            or name.endswith(".png")
            or name.endswith(".capture.json")
        ):
            continue
        try:
            os.stat(name, dir_fd=staging_fd, follow_symlinks=False)
            continue
        except FileNotFoundError:
            pass

        metadata = os.stat(name, dir_fd=source_fd, follow_symlinks=False)
        if stat.S_ISLNK(metadata.st_mode):
            target = os.readlink(name, dir_fd=source_fd)
            current = os.stat(name, dir_fd=source_fd, follow_symlinks=False)
            if (current.st_dev, current.st_ino, current.st_ctime_ns) != (
                metadata.st_dev,
                metadata.st_ino,
                metadata.st_ctime_ns,
            ):
                raise ScreenshotSetPublishError(f"Unmanaged symlink changed during copy: {label}/{name}")
            os.symlink(target, name, dir_fd=staging_fd)
        elif stat.S_ISDIR(metadata.st_mode):
            os.mkdir(name, mode=0o700, dir_fd=staging_fd)
            child_source = os.open(
                name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=source_fd
            )
            child_staging = os.open(
                name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=staging_fd
            )
            try:
                opened = os.fstat(child_source)
                directory_id = (
                    metadata.st_dev,
                    metadata.st_ino,
                    metadata.st_mode,
                    metadata.st_mtime_ns,
                    metadata.st_ctime_ns,
                )
                if (
                    opened.st_dev,
                    opened.st_ino,
                    opened.st_mode,
                    opened.st_mtime_ns,
                    opened.st_ctime_ns,
                ) != directory_id:
                    raise ScreenshotSetPublishError(f"Unmanaged directory changed during copy: {label}/{name}")
                _copy_unmanaged_entries(child_source, child_staging, f"{label}/{name}")
                after = os.fstat(child_source)
                if (
                    after.st_dev,
                    after.st_ino,
                    after.st_mode,
                    after.st_mtime_ns,
                    after.st_ctime_ns,
                ) != directory_id:
                    raise ScreenshotSetPublishError(f"Unmanaged directory changed during copy: {label}/{name}")
                os.fchmod(child_staging, stat.S_IMODE(metadata.st_mode))
                os.utime(
                    child_staging,
                    ns=(metadata.st_atime_ns, metadata.st_mtime_ns),
                )
                os.fsync(child_staging)
            finally:
                os.close(child_source)
                os.close(child_staging)
        elif stat.S_ISREG(metadata.st_mode):
            source_file = os.open(
                name, os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0),
                dir_fd=source_fd,
            )
            destination_file: int | None = None
            try:
                before = os.fstat(source_file)
                before_id = (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
                expected = (metadata.st_dev, metadata.st_ino, metadata.st_size, metadata.st_mtime_ns, metadata.st_ctime_ns)
                if before_id != expected:
                    raise ScreenshotSetPublishError(f"Unmanaged output changed during copy: {label}/{name}")
                destination_file = os.open(
                    name,
                    os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                    stat.S_IMODE(before.st_mode),
                    dir_fd=staging_fd,
                )
                while True:
                    block = os.read(source_file, 1024 * 1024)
                    if not block:
                        break
                    view = memoryview(block)
                    while view:
                        written = os.write(destination_file, view)
                        if written <= 0:
                            raise OSError("short write while preserving unmanaged output")
                        view = view[written:]
                after = os.fstat(source_file)
                after_id = (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns)
                if after_id != expected:
                    raise ScreenshotSetPublishError(f"Unmanaged output changed during copy: {label}/{name}")
                os.fchmod(destination_file, stat.S_IMODE(before.st_mode))
                os.utime(destination_file, ns=(before.st_atime_ns, before.st_mtime_ns))
                os.fsync(destination_file)
            except BaseException:
                if destination_file is not None:
                    os.close(destination_file)
                    destination_file = None
                try:
                    os.unlink(name, dir_fd=staging_fd)
                except FileNotFoundError:
                    pass
                raise
            finally:
                os.close(source_file)
                if destination_file is not None:
                    os.close(destination_file)
        else:
            raise ScreenshotSetPublishError(f"Cannot preserve unsupported output entry: {label}/{name}")


def _admit_path(path: Path, label: str, *, allow_missing: bool) -> Path:
    lexical = Path(os.path.abspath(os.fspath(path.expanduser())))
    current = Path(lexical.anchor)
    for index, part in enumerate(lexical.parts[1:]):
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            if allow_missing:
                return lexical
            raise ScreenshotSetPublishError(f"{label} path component is missing: {current}")
        except OSError as exc:
            raise ScreenshotSetPublishError(
                f"Could not inspect {label} path component {current}: {exc}"
            ) from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise ScreenshotSetPublishError(
                f"{label} path must not traverse a symbolic link: {current}"
            )
        if index < len(lexical.parts[1:]) - 1 and not stat.S_ISDIR(metadata.st_mode):
            raise ScreenshotSetPublishError(
                f"{label} path component is not a directory: {current}"
            )
    return lexical


def validate_output_directory(output_dir: Path) -> Path:
    output_dir = _admit_path(output_dir, "Output", allow_missing=True)
    if output_dir.exists() and not stat.S_ISDIR(output_dir.lstat().st_mode):
        raise ScreenshotSetPublishError(
            "Existing output path must be a non-symlink directory"
        )
    return output_dir


def promote_capture_set(staging_dir: Path, output_dir: Path) -> None:
    staging_dir = _admit_path(staging_dir, "Staging", allow_missing=False)
    output_dir = validate_output_directory(output_dir)
    if staging_dir == output_dir:
        raise ScreenshotSetPublishError(
            "Staging and output directories must be distinct"
        )
    if staging_dir.parent != output_dir.parent:
        raise ScreenshotSetPublishError(
            "Staging and output directories must share a parent for atomic publication"
        )
    parent_fd: int | None = None
    staging_fd: int | None = None
    output_fd: int | None = None
    backup_name: str | None = None
    commit_complete = False
    try:
        parent_fd = open_directory_descriptor(output_dir.parent, "screenshot output parent")
        original_parent = os.fstat(parent_fd)

        def check_parent_binding() -> None:
            try:
                current_fd = open_directory_descriptor(
                    output_dir.parent, "screenshot output parent"
                )
            except StableFileError as exc:
                raise ScreenshotSetPublishError(
                    "Screenshot output parent changed during publication"
                ) from exc
            try:
                current = os.fstat(current_fd)
                if (current.st_dev, current.st_ino) != (original_parent.st_dev, original_parent.st_ino):
                    raise ScreenshotSetPublishError("Screenshot output parent changed during publication")
            finally:
                os.close(current_fd)

        staging_metadata = os.stat(staging_dir.name, dir_fd=parent_fd, follow_symlinks=False)
        if not stat.S_ISDIR(staging_metadata.st_mode):
            raise ScreenshotSetPublishError("Staging path must be a non-symlink directory")
        staging_fd = os.open(staging_dir.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
        opened_staging = os.fstat(staging_fd)
        if (opened_staging.st_dev, opened_staging.st_ino) != (staging_metadata.st_dev, staging_metadata.st_ino):
            raise ScreenshotSetPublishError("Screenshot staging directory changed during admission")
        try:
            output_metadata = os.stat(output_dir.name, dir_fd=parent_fd, follow_symlinks=False)
        except FileNotFoundError:
            output_metadata = None
        if output_metadata is not None and not stat.S_ISDIR(output_metadata.st_mode):
            raise ScreenshotSetPublishError("Existing output path must be a non-symlink directory")
        output_exists = output_metadata is not None
        if output_exists:
            output_fd = os.open(output_dir.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
            opened_output = os.fstat(output_fd)
            if (opened_output.st_dev, opened_output.st_ino) != (output_metadata.st_dev, output_metadata.st_ino):
                raise ScreenshotSetPublishError("Screenshot output directory changed during admission")
            _copy_unmanaged_entries(output_fd, staging_fd)
        check_parent_binding()

        if output_exists:
            for _ in range(100):
                candidate = f".{output_dir.name}.backup.{secrets.token_hex(12)}"
                try:
                    os.stat(candidate, dir_fd=parent_fd, follow_symlinks=False)
                except FileNotFoundError:
                    backup_name = candidate
                    break
            if backup_name is None:
                raise ScreenshotSetPublishError("Could not allocate screenshot rollback directory")
            current_output = os.stat(output_dir.name, dir_fd=parent_fd, follow_symlinks=False)
            if (current_output.st_dev, current_output.st_ino) != (output_metadata.st_dev, output_metadata.st_ino):
                raise ScreenshotSetPublishError("Screenshot output changed before publication")
            os.rename(output_dir.name, backup_name, src_dir_fd=parent_fd, dst_dir_fd=parent_fd)
            os.fsync(parent_fd)

        published_new = False
        try:
            check_parent_binding()
            current_staging = os.stat(staging_dir.name, dir_fd=parent_fd, follow_symlinks=False)
            if (current_staging.st_dev, current_staging.st_ino) != (staging_metadata.st_dev, staging_metadata.st_ino):
                raise ScreenshotSetPublishError("Screenshot staging directory changed before publication")
            os.rename(staging_dir.name, output_dir.name, src_dir_fd=parent_fd, dst_dir_fd=parent_fd)
            published_new = True
            os.fsync(parent_fd)
            check_parent_binding()
        except (OSError, ScreenshotSetPublishError) as publish_error:
            if published_new:
                try:
                    current_new = os.stat(output_dir.name, dir_fd=parent_fd, follow_symlinks=False)
                    if (current_new.st_dev, current_new.st_ino) != (staging_metadata.st_dev, staging_metadata.st_ino):
                        raise OSError("published output changed before rollback")
                    os.rename(output_dir.name, staging_dir.name, src_dir_fd=parent_fd, dst_dir_fd=parent_fd)
                    os.fsync(parent_fd)
                except OSError as rollback_error:
                    if backup_name is not None:
                        raise ScreenshotSetPublishError(
                            "Screenshot set publication failed and the previous set could not "
                            f"be restored; previous set remains at {output_dir.parent / backup_name}: "
                            f"publish={publish_error}; rollback={rollback_error}"
                        ) from publish_error
                    raise ScreenshotSetPublishError(
                        f"Screenshot publication rollback failed: {rollback_error}"
                    ) from publish_error
            if backup_name is not None:
                try:
                    os.rename(backup_name, output_dir.name, src_dir_fd=parent_fd, dst_dir_fd=parent_fd)
                    backup_name = None
                    os.fsync(parent_fd)
                except OSError as rollback_error:
                    if backup_name is None:
                        recovery_detail = f"previous set was restored at {output_dir}"
                    else:
                        recovery_detail = f"previous set remains at {output_dir.parent / backup_name}"
                    raise ScreenshotSetPublishError(
                        "Screenshot set publication failed and the previous set could not "
                        f"be safely restored; {recovery_detail}: "
                        f"publish={publish_error}; rollback={rollback_error}"
                    ) from publish_error
            if isinstance(publish_error, ScreenshotSetPublishError):
                raise
            raise ScreenshotSetPublishError(
                f"Could not atomically publish screenshot set: {publish_error}"
            ) from publish_error

        commit_complete = True
        if backup_name is not None:
            remove_directory_at(parent_fd, backup_name)
            backup_name = None
            os.fsync(parent_fd)
    except (OSError, StableFileError) as exc:
        if commit_complete:
            if backup_name is None:
                raise ScreenshotSetPublishError(
                    f"Screenshot set was published and backup was removed, but directory sync failed: {exc}"
                ) from exc
            recovery = (
                f"; partial backup may remain at {output_dir.parent / backup_name}"
            )
            raise ScreenshotSetPublishError(
                f"Screenshot set was published but backup cleanup failed: {exc}{recovery}"
            ) from exc
        if backup_name is not None and parent_fd is not None:
            try:
                os.stat(output_dir.name, dir_fd=parent_fd, follow_symlinks=False)
            except FileNotFoundError:
                try:
                    os.rename(
                        backup_name,
                        output_dir.name,
                        src_dir_fd=parent_fd,
                        dst_dir_fd=parent_fd,
                    )
                    backup_name = None
                    os.fsync(parent_fd)
                except OSError as rollback_error:
                    if backup_name is None:
                        recovery_detail = f"previous set was restored at {output_dir}"
                    else:
                        recovery_detail = f"previous set remains at {output_dir.parent / backup_name}"
                    raise ScreenshotSetPublishError(
                        "Screenshot set publication failed and the previous set could not "
                        f"be safely restored; {recovery_detail}: "
                        f"publish={exc}; rollback={rollback_error}"
                    ) from exc
        if isinstance(exc, ScreenshotSetPublishError):
            raise
        raise ScreenshotSetPublishError(f"Could not safely publish screenshot set: {exc}") from exc
    finally:
        if output_fd is not None:
            os.close(output_fd)
        if staging_fd is not None:
            os.close(staging_fd)
        if parent_fd is not None:
            os.close(parent_fd)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Atomically publish a completed raw screenshot capture directory."
    )
    parser.add_argument("--staging-dir", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--check-only", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.check_only:
            validate_output_directory(args.output_dir)
            print(args.output_dir)
            return 0
        if args.staging_dir is None:
            raise ScreenshotSetPublishError("--staging-dir is required for publication")
        promote_capture_set(args.staging_dir, args.output_dir)
    except ScreenshotSetPublishError as exc:
        raise SystemExit(str(exc)) from exc
    print(args.output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
