"""Bounded regular-file consumption without path-component symlink races."""

from __future__ import annotations

import hashlib
import errno
import ctypes
import json
import os
import secrets
import stat
import sys
from pathlib import Path


class StableFileError(ValueError):
    """Raised when a file cannot be consumed as one stable regular inode."""


def _rename_exchange(
    first: str,
    second: str,
    *,
    first_dir_fd: int,
    second_dir_fd: int,
) -> None:
    """Atomically exchange two names without clobbering an intervening writer."""
    libc = ctypes.CDLL(None, use_errno=True)
    operation = getattr(libc, "renameat2", None)
    if operation is None:
        raise StableFileError("safe concurrent publication requires renameat2 support")
    operation.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    operation.restype = ctypes.c_int
    result = operation(
        first_dir_fd,
        os.fsencode(first),
        second_dir_fd,
        os.fsencode(second),
        2,  # RENAME_EXCHANGE
    )
    if result != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error), f"{first} <-> {second}")


def _rename_noreplace(
    source: str,
    destination: str,
    *,
    source_dir_fd: int,
    destination_dir_fd: int,
) -> None:
    """Atomically publish a new name only when no destination exists."""
    libc = ctypes.CDLL(None, use_errno=True)
    operation = getattr(libc, "renameat2", None)
    if operation is None:
        raise StableFileError("safe concurrent publication requires renameat2 support")
    operation.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
    operation.restype = ctypes.c_int
    result = operation(
        source_dir_fd,
        os.fsencode(source),
        destination_dir_fd,
        os.fsencode(destination),
        1,  # RENAME_NOREPLACE
    )
    if result != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error), f"{source} -> {destination}")


def _remove_file_if_unchanged(
    directory_fd: int,
    name: str,
    expected_identity: tuple[int, int],
    expected_digest: str,
    label: str,
) -> None:
    """Quarantine then remove a regular file only if its identity and bytes match."""
    cleanup_name = f".{name}.{secrets.token_hex(12)}.cleanup"
    _rename_noreplace(name, cleanup_name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
    try:
        metadata = os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
        actual_identity = (metadata.st_dev, metadata.st_ino)
        digest = _stable_file_digest_at(directory_fd, cleanup_name, label)
        if not stat.S_ISREG(metadata.st_mode) or actual_identity != expected_identity or digest != expected_digest:
            _rename_noreplace(cleanup_name, name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
            raise StableFileError(f"{label} entry changed during cleanup; concurrent entry restored at {name}")
        os.unlink(cleanup_name, dir_fd=directory_fd)
    except BaseException:
        # If the cleanup name still exists, restore it without clobbering a replacement.
        try:
            os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            try:
                _rename_noreplace(cleanup_name, name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
            except OSError:
                pass
        raise


def _remove_entry_if_same(
    directory_fd: int,
    name: str,
    expected_identity: tuple[int, int],
) -> None:
    """Quarantine and unlink one non-directory entry only if its inode is unchanged."""
    cleanup_name = f".{name}.{secrets.token_hex(12)}.remove"
    _rename_noreplace(name, cleanup_name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
    try:
        metadata = os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
        if stat.S_ISDIR(metadata.st_mode) or (metadata.st_dev, metadata.st_ino) != expected_identity:
            _rename_noreplace(cleanup_name, name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
            raise StableFileError(f"entry changed during tree removal: {name}")
        os.unlink(cleanup_name, dir_fd=directory_fd)
    except BaseException:
        try:
            os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            try:
                _rename_noreplace(cleanup_name, name, source_dir_fd=directory_fd, destination_dir_fd=directory_fd)
            except OSError:
                pass
        raise


def absolute_lexical_path(path: Path) -> Path:
    """Return an absolute normalized path without resolving symbolic links."""
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def open_directory_descriptor(path: Path, label: str = "directory") -> int:
    """Open a directory through no-follow components and return its held fd."""
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_DIRECTORY"):
        raise StableFileError("safe evidence paths require O_NOFOLLOW and O_DIRECTORY")
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC

    descriptors: list[int] = []
    try:
        current = os.open(absolute.anchor, flags)
        descriptors.append(current)
        for component in absolute.parts[1:]:
            current = os.open(component, flags, dir_fd=current)
            descriptors.append(current)
        result = descriptors.pop()
        return result
    except FileNotFoundError as exc:
        raise StableFileError(f"{label} is missing or changed: {absolute}") from exc
    except OSError as exc:
        if exc.errno in {errno.ELOOP, errno.ENOTDIR}:
            raise StableFileError(
                f"{label} path traverses a symbolic link or non-directory: {absolute}: {exc}"
            ) from exc
        raise StableFileError(f"could not safely open {label}: {absolute}: {exc}") from exc
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


def checked_directory_path(path: Path, label: str = "directory") -> Path:
    """Validate every directory component without following links and retain its lexical path."""
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    descriptor = open_directory_descriptor(absolute, label)
    os.close(descriptor)
    return absolute


def ensure_directory_exists(path: Path, label: str = "directory") -> None:
    """Create missing directory components relative to held no-follow parents."""
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_DIRECTORY"):
        raise StableFileError("safe evidence paths require O_NOFOLLOW and O_DIRECTORY")
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC

    current: int | None = None
    try:
        current = os.open(absolute.anchor, flags)
        for component in absolute.parts[1:]:
            try:
                following = os.open(component, flags, dir_fd=current)
            except FileNotFoundError:
                try:
                    os.mkdir(component, mode=0o777, dir_fd=current)
                except FileExistsError:
                    pass
                following = os.open(component, flags, dir_fd=current)
            os.close(current)
            current = following
    except FileNotFoundError as exc:
        raise StableFileError(f"could not create {label}: {absolute}: {exc}") from exc
    except OSError as exc:
        if exc.errno in {errno.ELOOP, errno.ENOTDIR}:
            raise StableFileError(
                f"{label} path traverses a symbolic link or non-directory: {absolute}: {exc}"
            ) from exc
        raise StableFileError(f"could not create {label}: {absolute}: {exc}") from exc
    finally:
        if current is not None:
            os.close(current)


def atomic_write_bytes(
    path: Path,
    content: bytes,
    label: str = "file",
    *,
    reject_symlink_leaf: bool = False,
    expected_directory_identity: tuple[int, int] | None = None,
) -> None:
    """Atomically publish bytes through one held, no-follow parent directory."""
    path = Path(path).expanduser()
    directory_fd: int | None = None
    temporary_name: str | None = None
    backup_name: str | None = None
    rollback_name: str | None = None
    raw_fd: int | None = None
    published = False
    temporary_contains_displaced_output = False
    preserve_backup = False
    preserve_temporary = False
    preserve_rollback = False
    destination_identity: tuple[int, int, int, int, int] | None = None
    published_identity: tuple[int, int, int, int, int] | None = None
    temporary_identity: tuple[int, int] | None = None
    backup_identity: tuple[int, int] | None = None
    backup_digest: str | None = None
    rollback_cleanup_identity: tuple[int, int] | None = None
    rollback_cleanup_digest: str | None = None

    def rollback_published_output() -> None:
        """Restore only if the displaced name still contains our publication."""
        nonlocal backup_name, rollback_name, published, preserve_backup, preserve_rollback
        nonlocal rollback_cleanup_identity, rollback_cleanup_digest
        if directory_fd is None or published_identity is None:
            raise OSError("published output identity is unavailable; refusing rollback")
        if backup_name is None:
            for _ in range(100):
                candidate = f".{path.name}.{secrets.token_hex(12)}.rollback"
                try:
                    _rename_noreplace(
                        path.name,
                        candidate,
                        source_dir_fd=directory_fd,
                        destination_dir_fd=directory_fd,
                    )
                    rollback_name = candidate
                    break
                except FileExistsError:
                    continue
            if rollback_name is None:
                raise OSError("could not allocate a rollback name without replacing output")

            try:
                moved = os.stat(
                    rollback_name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
                moved_identity = (
                    moved.st_dev,
                    moved.st_ino,
                    moved.st_size,
                    moved.st_mtime_ns,
                )
                moved_digest = _stable_file_digest_at(directory_fd, rollback_name, label)
                rollback_cleanup_identity = (moved.st_dev, moved.st_ino)
                rollback_cleanup_digest = moved_digest
            except (OSError, StableFileError):
                try:
                    _rename_noreplace(
                        rollback_name,
                        path.name,
                        source_dir_fd=directory_fd,
                        destination_dir_fd=directory_fd,
                    )
                    rollback_name = None
                    published = False
                except OSError as restore_exc:
                    preserve_rollback = True
                    raise OSError(
                        f"could not inspect moved output and could not restore it; "
                        f"recovery file retained at {path.parent / rollback_name}: {restore_exc}"
                    ) from restore_exc
                raise

            if (
                moved_identity == published_identity[:4]
                and moved_digest == hashlib.sha256(content).hexdigest()
            ):
                _remove_file_if_unchanged(
                    directory_fd,
                    rollback_name,
                    published_identity[:2],
                    hashlib.sha256(content).hexdigest(),
                    label,
                )
                rollback_name = None
                published = False
                os.fsync(directory_fd)
                return

            try:
                _rename_noreplace(
                    rollback_name,
                    path.name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
                rollback_name = None
                published = False
            except OSError as restore_exc:
                preserve_rollback = True
                raise OSError(
                    f"output changed during rollback; recovery file retained at "
                    f"{path.parent / rollback_name}: {restore_exc}"
                ) from restore_exc
            raise OSError("output changed during rollback; concurrent output restored")

        backup_metadata = os.stat(
            backup_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        backup_identity = (
            backup_metadata.st_dev,
            backup_metadata.st_ino,
            backup_metadata.st_size,
            backup_metadata.st_mtime_ns,
        )
        backup_digest = _stable_file_digest_at(directory_fd, backup_name, label)
        _rename_exchange(
            backup_name,
            path.name,
            first_dir_fd=directory_fd,
            second_dir_fd=directory_fd,
        )
        displaced = os.stat(
            backup_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        displaced_identity = (
            displaced.st_dev,
            displaced.st_ino,
            displaced.st_size,
            displaced.st_mtime_ns,
        )
        displaced_digest = _stable_file_digest_at(directory_fd, backup_name, label)
        if (
            displaced_identity == published_identity[:4]
            and displaced_digest == hashlib.sha256(content).hexdigest()
        ):
            _remove_file_if_unchanged(
                directory_fd,
                backup_name,
                published_identity[:2],
                hashlib.sha256(content).hexdigest(),
                label,
            )
            backup_name = None
            published = False
            os.fsync(directory_fd)
            return

        # A writer changed the output after our last identity check. Exchange
        # its version back to the requested path instead of overwriting it.
        _rename_exchange(
            backup_name,
            path.name,
            first_dir_fd=directory_fd,
            second_dir_fd=directory_fd,
        )
        published = False
        recovered = os.stat(
            backup_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        recovered_identity = (
            recovered.st_dev,
            recovered.st_ino,
            recovered.st_size,
            recovered.st_mtime_ns,
        )
        if (
            recovered_identity != backup_identity
            or _stable_file_digest_at(directory_fd, backup_name, label) != backup_digest
        ):
            preserve_backup = True
            raise OSError(
                f"output changed during rollback; recovery file retained at "
                f"{path.parent / backup_name}"
            )
        preserve_backup = True
        raise OSError("output changed during rollback; concurrent output restored")

    def cleanup_temporary_output() -> None:
        """Remove only our temporary inode, preserving any concurrent replacement."""
        nonlocal temporary_name, temporary_identity, preserve_temporary, preserve_backup
        if directory_fd is None or temporary_name is None:
            return
        if temporary_identity is None:
            preserve_temporary = True
            raise StableFileError(
                f"cannot verify temporary {label} output; entry retained at "
                f"{path.parent / temporary_name}"
            )

        cleanup_name: str | None = None
        for _ in range(100):
            candidate = f".{path.name}.{secrets.token_hex(12)}.cleanup"
            try:
                _rename_noreplace(
                    temporary_name,
                    candidate,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
                cleanup_name = candidate
                break
            except FileExistsError:
                continue
            except FileNotFoundError:
                temporary_name = None
                return
        if cleanup_name is None:
            preserve_temporary = True
            raise StableFileError(
                f"could not allocate a private cleanup name for temporary {label} output; "
                f"entry retained at {path.parent / temporary_name}"
            )

        try:
            moved = os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
            moved_identity = (moved.st_dev, moved.st_ino)
        except OSError as inspect_exc:
            try:
                _rename_noreplace(
                    cleanup_name,
                    temporary_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_temporary = True
                raise StableFileError(
                    f"could not inspect temporary {label} output and restore failed; "
                    f"recovery entry retained at {path.parent / cleanup_name}: "
                    f"inspect={inspect_exc}; restore={restore_exc}"
                ) from inspect_exc
            preserve_temporary = True
            raise StableFileError(
                f"could not inspect temporary {label} output; entry retained at "
                f"{path.parent / temporary_name}: {inspect_exc}"
            ) from inspect_exc

        content_matches_backup = True
        if backup_name is not None and temporary_contains_displaced_output:
            try:
                content_matches_backup = (
                    _stable_file_digest_at(directory_fd, cleanup_name, label)
                    == _stable_file_digest_at(directory_fd, backup_name, label)
                )
            except StableFileError:
                content_matches_backup = False

        if moved_identity != temporary_identity or not content_matches_backup:
            try:
                _rename_noreplace(
                    cleanup_name,
                    temporary_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_temporary = True
                preserve_backup = backup_name is not None
                raise StableFileError(
                    f"temporary {label} output changed during cleanup; concurrent entry "
                    f"retained at {path.parent / cleanup_name}: {restore_exc}"
                ) from restore_exc
            preserve_temporary = True
            preserve_backup = backup_name is not None
            raise StableFileError(
                f"temporary {label} output changed during cleanup; concurrent entry "
                f"restored at {path.parent / temporary_name}"
            )

        try:
            os.unlink(cleanup_name, dir_fd=directory_fd)
        except OSError as remove_exc:
            try:
                _rename_noreplace(
                    cleanup_name,
                    temporary_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_temporary = True
                raise StableFileError(
                    f"could not remove temporary {label} output; recovery entry retained at "
                    f"{path.parent / cleanup_name}: remove={remove_exc}; restore={restore_exc}"
                ) from remove_exc
            preserve_temporary = True
            raise StableFileError(
                f"could not remove temporary {label} output; entry retained at "
                f"{path.parent / temporary_name}: {remove_exc}"
            ) from remove_exc
        temporary_name = None

    def cleanup_backup_output() -> None:
        """Remove only our rollback snapshot, preserving a concurrent replacement."""
        nonlocal backup_name, preserve_backup
        if directory_fd is None or backup_name is None:
            return
        if backup_identity is None or backup_digest is None:
            preserve_backup = True
            raise StableFileError(
                f"cannot verify previous {label} output backup; entry retained at "
                f"{path.parent / backup_name}"
            )

        cleanup_name: str | None = None
        for _ in range(100):
            candidate = f".{path.name}.{secrets.token_hex(12)}.cleanup"
            try:
                _rename_noreplace(
                    backup_name,
                    candidate,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
                cleanup_name = candidate
                break
            except FileExistsError:
                continue
            except FileNotFoundError:
                backup_name = None
                return
        if cleanup_name is None:
            preserve_backup = True
            raise StableFileError(
                f"could not allocate a private cleanup name for previous {label} output; "
                f"backup retained at {path.parent / backup_name}"
            )

        try:
            moved = os.stat(cleanup_name, dir_fd=directory_fd, follow_symlinks=False)
            moved_identity = (moved.st_dev, moved.st_ino)
            moved_digest = _stable_file_digest_at(directory_fd, cleanup_name, label)
        except (OSError, StableFileError) as inspect_exc:
            try:
                _rename_noreplace(
                    cleanup_name,
                    backup_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_backup = True
                raise StableFileError(
                    f"could not inspect previous {label} output backup; recovery entry retained at "
                    f"{path.parent / cleanup_name}: inspect={inspect_exc}; restore={restore_exc}"
                ) from inspect_exc
            preserve_backup = True
            raise StableFileError(
                f"could not inspect previous {label} output backup; entry retained at "
                f"{path.parent / backup_name}: {inspect_exc}"
            ) from inspect_exc

        if moved_identity != backup_identity or moved_digest != backup_digest:
            try:
                _rename_noreplace(
                    cleanup_name,
                    backup_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_backup = True
                raise StableFileError(
                    f"previous {label} output backup changed during cleanup; concurrent entry "
                    f"retained at {path.parent / cleanup_name}: {restore_exc}"
                ) from restore_exc
            preserve_backup = True
            raise StableFileError(
                f"previous {label} output backup changed during cleanup; concurrent entry "
                f"restored at {path.parent / backup_name}"
            )

        try:
            os.unlink(cleanup_name, dir_fd=directory_fd)
        except OSError as remove_exc:
            try:
                _rename_noreplace(
                    cleanup_name,
                    backup_name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
            except OSError as restore_exc:
                preserve_backup = True
                raise StableFileError(
                    f"could not remove previous {label} output backup; recovery entry retained at "
                    f"{path.parent / cleanup_name}: remove={remove_exc}; restore={restore_exc}"
                ) from remove_exc
            preserve_backup = True
            raise StableFileError(
                f"could not remove previous {label} output backup; entry retained at "
                f"{path.parent / backup_name}: {remove_exc}"
            ) from remove_exc
        backup_name = None

    try:
        directory_fd = open_directory_descriptor(path.parent, f"{label} parent directory")
        original_directory = os.fstat(directory_fd)
        original_identity = (original_directory.st_dev, original_directory.st_ino)
        if expected_directory_identity is not None and original_identity != expected_directory_identity:
            raise StableFileError(
                f"{label} parent directory changed before publication: {path.parent}"
            )

        def ensure_directory_is_still_bound() -> None:
            current_fd = open_directory_descriptor(
                path.parent, f"{label} parent directory"
            )
            try:
                current = os.fstat(current_fd)
                if (current.st_dev, current.st_ino) != (
                    original_directory.st_dev,
                    original_directory.st_ino,
                ):
                    raise StableFileError(
                        f"{label} parent directory changed during publication: {path.parent}"
                    )
            finally:
                os.close(current_fd)

        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
        if hasattr(os, "O_CLOEXEC"):
            flags |= os.O_CLOEXEC
        for _ in range(100):
            temporary_name = f".{path.name}.{secrets.token_hex(12)}.tmp"
            try:
                raw_fd = os.open(
                    temporary_name,
                    flags,
                    0o600,
                    dir_fd=directory_fd,
                )
                temporary_metadata = os.fstat(raw_fd)
                temporary_identity = (temporary_metadata.st_dev, temporary_metadata.st_ino)
                break
            except FileExistsError:
                temporary_name = None
        else:
            raise StableFileError(
                f"could not allocate a unique temporary file for {label}: {path}"
            )

        with os.fdopen(raw_fd, "wb") as stream:
            raw_fd = None
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        ensure_directory_is_still_bound()
        assert temporary_name is not None
        if reject_symlink_leaf:
            try:
                existing = os.stat(
                    path.name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
            except FileNotFoundError:
                existing = None
            if existing is not None and stat.S_ISLNK(existing.st_mode):
                raise StableFileError(f"{label} output must not be a symbolic link: {path}")
        else:
            try:
                existing = os.stat(
                    path.name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
            except FileNotFoundError:
                existing = None
        if existing is not None and stat.S_ISREG(existing.st_mode):
            destination_identity = (
                existing.st_dev,
                existing.st_ino,
                existing.st_size,
                existing.st_mtime_ns,
                existing.st_ctime_ns,
            )
            for _ in range(100):
                candidate = f".{path.name}.{secrets.token_hex(12)}.bak"
                try:
                    old_fd = os.open(
                        path.name,
                        os.O_RDONLY
                        | os.O_NOFOLLOW
                        | getattr(os, "O_CLOEXEC", 0)
                        | getattr(os, "O_NONBLOCK", 0),
                        dir_fd=directory_fd,
                    )
                    try:
                        _snapshot_regular_file(
                            old_fd,
                            candidate,
                            directory_fd,
                            destination_identity,
                            label,
                            path,
                        )
                    finally:
                        os.close(old_fd)
                    backup_name = candidate
                    backup_metadata = os.stat(
                        backup_name,
                        dir_fd=directory_fd,
                        follow_symlinks=False,
                    )
                    backup_identity = (backup_metadata.st_dev, backup_metadata.st_ino)
                    backup_digest = _stable_file_digest_at(directory_fd, backup_name, label)
                    break
                except FileExistsError:
                    continue
            else:
                raise StableFileError(f"could not preserve existing {label} output: {path}")
        elif existing is not None:
            raise StableFileError(f"{label} output must be a regular non-symlink file: {path}")

        try:
            current_destination = os.stat(
                path.name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
        except FileNotFoundError:
            current_destination = None
        current_identity = (
            (
                current_destination.st_dev,
                current_destination.st_ino,
                current_destination.st_size,
                current_destination.st_mtime_ns,
                current_destination.st_ctime_ns,
            )
            if current_destination is not None
            else None
        )
        if current_identity != destination_identity:
            raise StableFileError(f"{label} output changed before publication: {path}")
        staged = os.stat(
            temporary_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        staged_identity = (
            staged.st_dev,
            staged.st_ino,
            staged.st_size,
            staged.st_mtime_ns,
        )
        if destination_identity is None:
            _rename_noreplace(
                temporary_name,
                path.name,
                source_dir_fd=directory_fd,
                destination_dir_fd=directory_fd,
            )
            temporary_name = None
        else:
            _rename_exchange(
                temporary_name,
                path.name,
                first_dir_fd=directory_fd,
                second_dir_fd=directory_fd,
            )
            temporary_contains_displaced_output = True
        published = True
        current_published = os.stat(
            path.name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        current_published_identity = (
            current_published.st_dev,
            current_published.st_ino,
            current_published.st_size,
            current_published.st_mtime_ns,
            current_published.st_ctime_ns,
        )
        if current_published_identity[:4] != staged_identity:
            raise StableFileError(
                f"{label} output changed immediately after publication: {path}"
            )
        if destination_identity is not None:
            displaced = os.stat(
                temporary_name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
            displaced_identity = (
                displaced.st_dev,
                displaced.st_ino,
                displaced.st_size,
                displaced.st_mtime_ns,
                displaced.st_ctime_ns,
            )
            temporary_identity = (displaced.st_dev, displaced.st_ino)
            # renameat2 updates ctime on both exchanged inodes, so compare
            # stable fields and the bytes from the recovery snapshot.
            if (
                displaced_identity[:4] != destination_identity[:4]
                or _stable_file_digest_at(directory_fd, temporary_name, label)
                != _stable_file_digest_at(directory_fd, backup_name, label)
            ):
                # The exchange caught a writer that changed the destination
                # after our earlier check. Put that exact version back if our
                # staged inode still occupies the destination name.
                current = os.stat(
                    path.name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
                current_identity = (
                    current.st_dev,
                    current.st_ino,
                    current.st_size,
                    current.st_mtime_ns,
                    current.st_ctime_ns,
                )
                if current_identity == current_published_identity:
                    _rename_exchange(
                        temporary_name,
                        path.name,
                        first_dir_fd=directory_fd,
                        second_dir_fd=directory_fd,
                    )
                    published = False
                    temporary_contains_displaced_output = False
                else:
                    preserve_backup = backup_name is not None
                    preserve_temporary = True
                raise StableFileError(
                    f"{label} output changed before publication: {path}"
                )
        published_identity = current_published_identity
        os.fsync(directory_fd)
        ensure_directory_is_still_bound()
    except StableFileError as exc:
        if published and directory_fd is not None:
            try:
                rollback_published_output()
            except (OSError, StableFileError) as rollback_exc:
                preserve_backup = backup_name is not None
                recovery = (
                    f"; rollback failed: {rollback_exc}"
                    + (f"; previous output backup retained as {path.parent / backup_name}" if backup_name else "")
                    + (f"; output recovery file retained as {path.parent / rollback_name}" if preserve_rollback and rollback_name else "")
                )
                raise StableFileError(f"{exc}{recovery}") from rollback_exc
        raise
    except OSError as exc:
        if published and directory_fd is not None:
            try:
                rollback_published_output()
            except (OSError, StableFileError) as rollback_exc:
                preserve_backup = backup_name is not None
                recovery = (
                    f"; rollback failed: {rollback_exc}"
                    + (f"; previous output backup retained as {path.parent / backup_name}" if backup_name else "")
                    + (f"; output recovery file retained as {path.parent / rollback_name}" if preserve_rollback and rollback_name else "")
                )
                raise StableFileError(
                    f"could not safely publish {label}: {path}: {exc}{recovery}"
                ) from rollback_exc
        raise StableFileError(f"could not safely publish {label}: {path}: {exc}") from exc
    finally:
        active_exception = sys.exc_info()[1]
        cleanup_errors: list[BaseException] = []
        try:
            if raw_fd is not None:
                try:
                    os.close(raw_fd)
                except OSError as exc:
                    cleanup_errors.append(exc)
            if directory_fd is not None:
                if temporary_name is not None and not preserve_temporary:
                    try:
                        cleanup_temporary_output()
                    except FileNotFoundError:
                        pass
                    except BaseException as exc:
                        cleanup_errors.append(exc)
                if backup_name is not None and not preserve_backup:
                    try:
                        cleanup_backup_output()
                    except FileNotFoundError:
                        pass
                    except BaseException as exc:
                        cleanup_errors.append(exc)
                if rollback_name is not None and not preserve_rollback:
                    try:
                        if rollback_cleanup_identity is None or rollback_cleanup_digest is None:
                            raise StableFileError(
                                f"cannot verify rollback entry; recovery retained at "
                                f"{path.parent / rollback_name}"
                            )
                        _remove_file_if_unchanged(
                            directory_fd,
                            rollback_name,
                            rollback_cleanup_identity,
                            rollback_cleanup_digest,
                            label,
                        )
                    except FileNotFoundError:
                        pass
                    except BaseException as exc:
                        preserve_rollback = True
                        cleanup_errors.append(exc)
        finally:
            if directory_fd is not None:
                try:
                    os.close(directory_fd)
                except OSError as exc:
                    cleanup_errors.append(exc)
        if cleanup_errors:
            message = "; ".join(str(error) for error in cleanup_errors)
            if active_exception is not None:
                active_exception.add_note(f"publication cleanup also failed: {message}")
            elif len(cleanup_errors) == 1 and isinstance(cleanup_errors[0], StableFileError):
                raise cleanup_errors[0]
            else:
                raise StableFileError(
                    f"could not safely clean up {label} publication for {path}: {message}"
                ) from cleanup_errors[0]


def _snapshot_regular_file(
    source_fd: int,
    backup_name: str,
    backup_dir_fd: int,
    expected_identity: tuple[int, int, int, int, int],
    label: str,
    path: Path,
) -> None:
    """Copy a stable regular-file version into a private recovery file."""
    source_before = os.fstat(source_fd)
    identity = (
        source_before.st_dev,
        source_before.st_ino,
        source_before.st_size,
        source_before.st_mtime_ns,
        source_before.st_ctime_ns,
    )
    if identity != expected_identity:
        raise StableFileError(f"{label} output changed while preparing rollback: {path}")
    backup_fd: int | None = None
    backup_created = False
    backup_identity: tuple[int, int] | None = None
    try:
        backup_fd = os.open(
            backup_name,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
            stat.S_IMODE(source_before.st_mode),
            dir_fd=backup_dir_fd,
        )
        backup_created = True
        backup_metadata = os.fstat(backup_fd)
        backup_identity = (backup_metadata.st_dev, backup_metadata.st_ino)
        while True:
            block = os.read(source_fd, 1024 * 1024)
            if not block:
                break
            view = memoryview(block)
            while view:
                written = os.write(backup_fd, view)
                if written <= 0:
                    raise OSError("short write while preserving rollback data")
                view = view[written:]
        os.fchmod(backup_fd, stat.S_IMODE(source_before.st_mode))
        os.utime(
            backup_fd,
            ns=(source_before.st_atime_ns, source_before.st_mtime_ns),
        )
        os.fsync(backup_fd)
        source_after = os.fstat(source_fd)
        after_identity = (
            source_after.st_dev,
            source_after.st_ino,
            source_after.st_size,
            source_after.st_mtime_ns,
            source_after.st_ctime_ns,
        )
        if after_identity != expected_identity:
            raise StableFileError(f"{label} output changed while preparing rollback: {path}")
    except BaseException:
        if backup_fd is not None:
            os.close(backup_fd)
            backup_fd = None
        if backup_created:
            try:
                if backup_identity is None:
                    raise StableFileError("cannot verify partial rollback snapshot for cleanup")
                _remove_entry_if_same(backup_dir_fd, backup_name, backup_identity)
            except FileNotFoundError:
                pass
        raise
    finally:
        if backup_fd is not None:
            os.close(backup_fd)


def _stable_file_digest_at(directory_fd: int, name: str, label: str) -> str:
    """Hash one stable regular file opened relative to a held directory."""
    flags = os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    descriptor: int | None = None
    try:
        descriptor = os.open(name, flags, dir_fd=directory_fd)
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise StableFileError(f"{label} recovery snapshot is not a regular file: {name}")
        digest = hashlib.sha256()
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
        after = os.fstat(descriptor)
        current = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
        fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
        if any(
            getattr(before, field) != getattr(after, field)
            or getattr(before, field) != getattr(current, field)
            for field in fields
        ):
            raise StableFileError(f"{label} recovery snapshot changed while being checked: {name}")
        return digest.hexdigest()
    except StableFileError:
        raise
    except OSError as exc:
        raise StableFileError(f"could not verify {label} recovery snapshot {name}: {exc}") from exc
    finally:
        if descriptor is not None:
            os.close(descriptor)


def atomic_replace_file(
    source: Path,
    destination: Path,
    label: str = "file",
    *,
    expected_source_identity: tuple[int, int, int, int, int] | None = None,
) -> None:
    """Atomically rename a verified regular file within one held directory."""
    source = Path(os.path.abspath(os.fspath(source.expanduser())))
    destination = Path(os.path.abspath(os.fspath(destination.expanduser())))
    if source.parent != destination.parent:
        raise StableFileError(f"{label} source and destination must share a directory")
    if source == destination:
        raise StableFileError(f"{label} source and destination must be different paths")
    directory_fd: int | None = None
    backup_name: str | None = None
    recovery_name: str | None = None
    published = False
    destination_identity: tuple[int, int, int, int, int] | None = None
    displaced_destination_identity: tuple[int, int, int, int, int] | None = None
    preserve_backup = False
    preserve_recovery = False
    destination_identity: tuple[int, int, int, int, int] | None = None
    published_identity: tuple[int, int, int, int, int] | None = None
    source_digest: str | None = None
    backup_identity: tuple[int, int] | None = None
    backup_digest: str | None = None
    recovery_identity: tuple[int, int] | None = None
    recovery_digest: str | None = None

    def rollback_published_file() -> None:
        nonlocal backup_name, recovery_name, published, preserve_backup, preserve_recovery
        nonlocal recovery_identity, recovery_digest
        if destination_identity is None:
            if source_digest is None:
                raise OSError("source digest is unavailable; refusing rollback")
            for _ in range(100):
                candidate = f".{destination.name}.{secrets.token_hex(12)}.rollback"
                try:
                    _rename_noreplace(
                        destination.name,
                        candidate,
                        source_dir_fd=directory_fd,
                        destination_dir_fd=directory_fd,
                    )
                    recovery_name = candidate
                    break
                except FileExistsError:
                    continue
            if recovery_name is None:
                raise OSError("could not allocate a rollback name without replacing destination")

            try:
                moved = os.stat(
                    recovery_name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
                moved_identity = (
                    moved.st_dev,
                    moved.st_ino,
                    moved.st_size,
                    moved.st_mtime_ns,
                )
                moved_digest = _stable_file_digest_at(directory_fd, recovery_name, label)
                recovery_identity = (moved.st_dev, moved.st_ino)
                recovery_digest = moved_digest
            except (OSError, StableFileError):
                try:
                    _rename_noreplace(
                        recovery_name,
                        destination.name,
                        source_dir_fd=directory_fd,
                        destination_dir_fd=directory_fd,
                    )
                    recovery_name = None
                    published = False
                except OSError as restore_exc:
                    preserve_recovery = True
                    raise OSError(
                        f"could not inspect displaced destination and could not restore it; "
                        f"recovery file retained at {destination.parent / recovery_name}: {restore_exc}"
                    ) from restore_exc
                raise

            if moved_identity == identity[:4] and moved_digest == source_digest:
                _rename_noreplace(
                    recovery_name,
                    source.name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
                recovery_name = None
                published = False
                os.fsync(directory_fd)
                return
            try:
                _rename_noreplace(
                    recovery_name,
                    destination.name,
                    source_dir_fd=directory_fd,
                    destination_dir_fd=directory_fd,
                )
                recovery_name = None
                published = False
            except OSError as restore_exc:
                preserve_recovery = True
                raise OSError(
                    f"destination changed during rollback; recovery file retained at "
                    f"{destination.parent / recovery_name}: {restore_exc}"
                ) from restore_exc
            raise OSError("destination changed during rollback; concurrent output restored")

        if source_digest is None:
            raise OSError("source digest is unavailable; refusing rollback")
        _rename_exchange(
            source.name,
            destination.name,
            first_dir_fd=directory_fd,
            second_dir_fd=directory_fd,
        )
        displaced = os.stat(
            source.name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        displaced_identity = (
            displaced.st_dev,
            displaced.st_ino,
            displaced.st_size,
            displaced.st_mtime_ns,
        )
        displaced_digest = _stable_file_digest_at(directory_fd, source.name, label)
        if displaced_identity == identity[:4] and displaced_digest == source_digest:
            published = False
            if backup_name is not None:
                if backup_identity is None or backup_digest is None:
                    raise OSError("rollback backup identity is unavailable; refusing cleanup")
                _remove_file_if_unchanged(
                    directory_fd, backup_name, backup_identity, backup_digest, label
                )
                backup_name = None
            os.fsync(directory_fd)
            return

        _rename_exchange(
            source.name,
            destination.name,
            first_dir_fd=directory_fd,
            second_dir_fd=directory_fd,
        )
        published = False
        preserve_backup = backup_name is not None
        raise OSError("destination changed during rollback; concurrent output restored")

    try:
        directory_fd = open_directory_descriptor(
            source.parent, f"{label} transaction directory"
        )
        original_directory = os.fstat(directory_fd)
        source_stat = os.stat(
            source.name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        if not stat.S_ISREG(source_stat.st_mode):
            raise StableFileError(f"{label} source must be a regular non-symlink file")
        identity = (
            source_stat.st_dev,
            source_stat.st_ino,
            source_stat.st_size,
            source_stat.st_mtime_ns,
            source_stat.st_ctime_ns,
        )
        if expected_source_identity is not None and identity != expected_source_identity:
            raise StableFileError(f"{label} source changed before atomic replacement")
        source_digest = _stable_file_digest_at(directory_fd, source.name, label)

        try:
            existing_destination = os.stat(
                destination.name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
        except FileNotFoundError:
            existing_destination = None
        if existing_destination is not None and stat.S_ISREG(existing_destination.st_mode):
            destination_identity = (
                existing_destination.st_dev,
                existing_destination.st_ino,
                existing_destination.st_size,
                existing_destination.st_mtime_ns,
                existing_destination.st_ctime_ns,
            )
            for _ in range(100):
                candidate = f".{destination.name}.{secrets.token_hex(12)}.bak"
                try:
                    old_fd = os.open(
                        destination.name,
                        os.O_RDONLY
                        | os.O_NOFOLLOW
                        | getattr(os, "O_CLOEXEC", 0)
                        | getattr(os, "O_NONBLOCK", 0),
                        dir_fd=directory_fd,
                    )
                    try:
                        _snapshot_regular_file(
                            old_fd,
                            candidate,
                            directory_fd,
                            destination_identity,
                            label,
                            destination,
                        )
                    finally:
                        os.close(old_fd)
                    backup_name = candidate
                    backup_metadata = os.stat(candidate, dir_fd=directory_fd, follow_symlinks=False)
                    backup_identity = (backup_metadata.st_dev, backup_metadata.st_ino)
                    backup_digest = _stable_file_digest_at(directory_fd, candidate, label)
                    break
                except FileExistsError:
                    continue
            else:
                raise StableFileError(
                    f"could not preserve existing {label} destination: {destination}"
                )

        current_fd = open_directory_descriptor(
            source.parent, f"{label} transaction directory"
        )
        try:
            current_directory = os.fstat(current_fd)
            if (current_directory.st_dev, current_directory.st_ino) != (
                original_directory.st_dev,
                original_directory.st_ino,
            ):
                raise StableFileError(
                    f"{label} transaction directory changed before atomic replacement"
                )
        finally:
            os.close(current_fd)

        try:
            current_destination = os.stat(
                destination.name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
        except FileNotFoundError:
            current_destination = None
        current_identity = (
            (
                current_destination.st_dev,
                current_destination.st_ino,
                current_destination.st_size,
                current_destination.st_mtime_ns,
                current_destination.st_ctime_ns,
            )
            if current_destination is not None
            else None
        )
        if current_identity != destination_identity:
            raise StableFileError(
                f"{label} destination changed before atomic replacement: {destination}"
            )

        current_source = os.stat(
            source.name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        current_source_identity = (
            current_source.st_dev,
            current_source.st_ino,
            current_source.st_size,
            current_source.st_mtime_ns,
            current_source.st_ctime_ns,
        )
        if current_source_identity != identity or not stat.S_ISREG(current_source.st_mode):
            raise StableFileError(
                f"{label} source changed before atomic replacement: {source}"
            )

        if destination_identity is None:
            _rename_noreplace(
                source.name,
                destination.name,
                source_dir_fd=directory_fd,
                destination_dir_fd=directory_fd,
            )
        else:
            _rename_exchange(
                source.name,
                destination.name,
                first_dir_fd=directory_fd,
                second_dir_fd=directory_fd,
            )
        published = True
        current_published = os.stat(
            destination.name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        current_published_identity = (
            current_published.st_dev,
            current_published.st_ino,
            current_published.st_size,
            current_published.st_mtime_ns,
            current_published.st_ctime_ns,
        )
        if current_published_identity[:4] != identity[:4]:
            raise StableFileError(
                f"{label} destination changed immediately after atomic replacement: {destination}"
            )
        if destination_identity is not None:
            displaced = os.stat(
                source.name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
            displaced_destination_identity = (
                displaced.st_dev,
                displaced.st_ino,
                displaced.st_size,
                displaced.st_mtime_ns,
                displaced.st_ctime_ns,
            )
            if (
                displaced_destination_identity[:4] != destination_identity[:4]
                or _stable_file_digest_at(directory_fd, source.name, label)
                != _stable_file_digest_at(directory_fd, backup_name, label)
            ):
                current = os.stat(
                    destination.name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
                current_identity = (
                    current.st_dev,
                    current.st_ino,
                    current.st_size,
                    current.st_mtime_ns,
                    current.st_ctime_ns,
                )
                if current_identity == current_published_identity:
                    _rename_exchange(
                        source.name,
                        destination.name,
                        first_dir_fd=directory_fd,
                        second_dir_fd=directory_fd,
                    )
                    published = False
                else:
                    preserve_backup = backup_name is not None
                raise StableFileError(
                    f"{label} destination changed before atomic replacement: {destination}"
                )
        published_identity = current_published_identity
        os.fsync(directory_fd)
        current_fd = open_directory_descriptor(
            destination.parent, f"{label} transaction directory"
        )
        try:
            current_directory = os.fstat(current_fd)
            if (current_directory.st_dev, current_directory.st_ino) != (
                original_directory.st_dev,
                original_directory.st_ino,
            ):
                raise StableFileError(
                    f"{label} transaction directory changed during atomic replacement"
                )
        finally:
            os.close(current_fd)
        if destination_identity is not None:
            current_source = os.stat(
                source.name,
                dir_fd=directory_fd,
                follow_symlinks=False,
            )
            current_source_identity = (
                current_source.st_dev,
                current_source.st_ino,
                current_source.st_size,
                current_source.st_mtime_ns,
                current_source.st_ctime_ns,
            )
            if current_source_identity[:4] != displaced_destination_identity[:4]:
                raise StableFileError(
                    f"{label} previous destination changed before cleanup: {source}"
            )
            _remove_file_if_unchanged(
                directory_fd,
                source.name,
                displaced_destination_identity[:2],
                backup_digest or "",
                label,
            )
        if backup_name is not None:
            if backup_identity is None or backup_digest is None:
                raise StableFileError(f"cannot verify previous {label} destination backup")
            _remove_file_if_unchanged(
                directory_fd, backup_name, backup_identity, backup_digest, label
            )
            backup_name = None
    except StableFileError as exc:
        if published and directory_fd is not None:
            try:
                rollback_published_file()
            except (OSError, StableFileError) as rollback_exc:
                preserve_backup = backup_name is not None
                recovery = (
                    f"; rollback failed: {rollback_exc}"
                    + (
                        f"; previous destination backup retained as {destination.parent / backup_name}"
                        if backup_name
                        else ""
                    )
                    + (
                        f"; destination recovery file retained as {destination.parent / recovery_name}"
                        if preserve_recovery and recovery_name
                        else ""
                    )
                )
                raise StableFileError(f"{exc}{recovery}") from rollback_exc
        raise
    except OSError as exc:
        if published and directory_fd is not None:
            try:
                rollback_published_file()
            except (OSError, StableFileError) as rollback_exc:
                preserve_backup = backup_name is not None
                recovery = (
                    f"; rollback failed: {rollback_exc}"
                    + (
                        f"; previous destination backup retained as {destination.parent / backup_name}"
                        if backup_name
                        else ""
                    )
                    + (
                        f"; destination recovery file retained as {destination.parent / recovery_name}"
                        if preserve_recovery and recovery_name
                        else ""
                    )
                )
                raise StableFileError(
                    f"could not safely replace {label} {destination}: {exc}{recovery}"
                ) from rollback_exc
        raise StableFileError(
            f"could not safely replace {label} {destination}: {exc}"
        ) from exc
    finally:
        if directory_fd is not None:
            active_exception = sys.exc_info()[1]
            cleanup_errors: list[BaseException] = []
            if backup_name is not None and not preserve_backup:
                try:
                    if backup_identity is None or backup_digest is None:
                        raise StableFileError("cannot verify previous destination backup during cleanup")
                    _remove_file_if_unchanged(
                        directory_fd, backup_name, backup_identity, backup_digest, label
                    )
                except FileNotFoundError:
                    pass
                except BaseException as cleanup_exc:
                    preserve_backup = True
                    cleanup_errors.append(cleanup_exc)
            if recovery_name is not None and not preserve_recovery:
                try:
                    if recovery_identity is None or recovery_digest is None:
                        raise StableFileError("cannot verify destination recovery file during cleanup")
                    _remove_file_if_unchanged(
                        directory_fd, recovery_name, recovery_identity, recovery_digest, label
                    )
                except FileNotFoundError:
                    pass
                except BaseException as cleanup_exc:
                    preserve_recovery = True
                    cleanup_errors.append(cleanup_exc)
            try:
                os.close(directory_fd)
            except OSError as cleanup_exc:
                cleanup_errors.append(cleanup_exc)
            if cleanup_errors:
                message = "; ".join(str(error) for error in cleanup_errors)
                if active_exception is not None:
                    active_exception.add_note(f"replacement cleanup also failed: {message}")
                else:
                    raise StableFileError(
                        f"could not safely clean up {label} replacement: {message}"
                    ) from cleanup_errors[0]


def atomic_publish_files(
    outputs: list[tuple[Path, bytes]],
    label: str = "files",
) -> None:
    """Publish related regular files with descriptor-relative rollback."""
    if not outputs:
        raise StableFileError(f"at least one {label} output is required")
    destinations = [
        Path(os.path.abspath(os.fspath(path.expanduser()))) for path, _ in outputs
    ]
    content_by_name = {
        destination.name: content
        for destination, (_, content) in zip(destinations, outputs)
    }
    index_by_name = {destination.name: index for index, destination in enumerate(destinations)}
    parent = destinations[0].parent
    if any(path.parent != parent for path in destinations):
        raise StableFileError(f"{label} outputs must share one directory")
    if len({path.name for path in destinations}) != len(destinations):
        raise StableFileError(f"{label} outputs must have distinct names")

    ensure_directory_exists(parent, f"{label} parent directory")
    parent_fd: int | None = None
    transaction_fd: int | None = None
    transaction_name: str | None = None
    transaction_identity: tuple[int, int] | None = None
    staged_names: list[str] = []
    backup_names: dict[str, str] = {}
    destination_identities: dict[str, tuple[int, int, int, int, int] | None] = {}
    published_identities: dict[str, tuple[int, int, int, int, int]] = {}
    staged_identities: dict[str, tuple[int, int, int, int, int]] = {}
    staged_digests: dict[str, str] = {}
    backup_identities: dict[str, tuple[int, int]] = {}
    backup_digests: dict[str, str] = {}
    published: list[str] = []
    preserve_transaction = False
    commit_complete = False
    try:
        parent_fd = open_directory_descriptor(parent, f"{label} parent directory")
        original_parent = os.fstat(parent_fd)

        def ensure_parent_is_still_bound() -> None:
            current_fd = open_directory_descriptor(parent, f"{label} parent directory")
            try:
                current = os.fstat(current_fd)
                if (current.st_dev, current.st_ino) != (
                    original_parent.st_dev,
                    original_parent.st_ino,
                ):
                    raise StableFileError(
                        f"{label} parent directory changed during publication: {parent}"
                    )
            finally:
                os.close(current_fd)

        for _ in range(100):
            candidate = f".{destinations[0].name}.{secrets.token_hex(12)}.txn"
            try:
                os.mkdir(candidate, mode=0o700, dir_fd=parent_fd)
                transaction_name = candidate
                break
            except FileExistsError:
                continue
        if transaction_name is None:
            raise StableFileError(f"could not allocate a transaction directory for {label}")
        transaction_fd = os.open(
            transaction_name,
            os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
            dir_fd=parent_fd,
        )
        transaction_metadata = os.fstat(transaction_fd)
        transaction_identity = (transaction_metadata.st_dev, transaction_metadata.st_ino)

        for index, ((_, content), destination) in enumerate(zip(outputs, destinations)):
            staged = f"staged-{index}"
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
            if hasattr(os, "O_CLOEXEC"):
                flags |= os.O_CLOEXEC
            descriptor = os.open(staged, flags, 0o600, dir_fd=transaction_fd)
            staged_names.append(staged)
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
                staged_metadata = os.fstat(stream.fileno())
                staged_identities[destination.name] = (
                    staged_metadata.st_dev,
                    staged_metadata.st_ino,
                    staged_metadata.st_size,
                    staged_metadata.st_mtime_ns,
                    staged_metadata.st_ctime_ns,
                )
                staged_digests[destination.name] = hashlib.sha256(content).hexdigest()

            try:
                existing = os.stat(
                    destination.name,
                    dir_fd=parent_fd,
                    follow_symlinks=False,
                )
            except FileNotFoundError:
                existing = None
            if existing is not None:
                if not stat.S_ISREG(existing.st_mode):
                    raise StableFileError(
                        f"{label} output must be a regular non-symlink file: {destination}"
                    )
                backup = f"backup-{index}"
                source_fd = os.open(
                    destination.name,
                    os.O_RDONLY
                    | os.O_NOFOLLOW
                    | getattr(os, "O_CLOEXEC", 0)
                    | getattr(os, "O_NONBLOCK", 0),
                    dir_fd=parent_fd,
                )
                try:
                    expected_identity = (
                        existing.st_dev,
                        existing.st_ino,
                        existing.st_size,
                        existing.st_mtime_ns,
                        existing.st_ctime_ns,
                    )
                    _snapshot_regular_file(
                        source_fd,
                        backup,
                        transaction_fd,
                        expected_identity,
                        label,
                        destination,
                    )
                finally:
                    os.close(source_fd)
                backup_names[destination.name] = backup
                backup_metadata = os.stat(backup, dir_fd=transaction_fd, follow_symlinks=False)
                backup_identities[destination.name] = (backup_metadata.st_dev, backup_metadata.st_ino)
                backup_digests[destination.name] = _stable_file_digest_at(transaction_fd, backup, label)
                destination_identities[destination.name] = expected_identity
            else:
                destination_identities[destination.name] = None

        os.fsync(transaction_fd)
        ensure_parent_is_still_bound()
        for staged, destination in zip(staged_names, destinations):
            try:
                current = os.stat(
                    destination.name,
                    dir_fd=parent_fd,
                    follow_symlinks=False,
                )
            except FileNotFoundError:
                current = None
            expected = destination_identities[destination.name]
            current_identity = (
                (
                    current.st_dev,
                    current.st_ino,
                    current.st_size,
                    current.st_mtime_ns,
                    current.st_ctime_ns,
                )
                if current is not None
                else None
            )
            if current_identity != expected:
                raise StableFileError(
                    f"{label} output changed before publication: {destination}"
                )
            if expected is None:
                _rename_noreplace(
                    staged,
                    destination.name,
                    source_dir_fd=transaction_fd,
                    destination_dir_fd=parent_fd,
                )
            else:
                _rename_exchange(
                    staged,
                    destination.name,
                    first_dir_fd=transaction_fd,
                    second_dir_fd=parent_fd,
                )
            published.append(destination.name)
            current_published = os.stat(
                destination.name,
                dir_fd=parent_fd,
                follow_symlinks=False,
            )
            current_published_identity = (
                current_published.st_dev,
                current_published.st_ino,
                current_published.st_size,
                current_published.st_mtime_ns,
                current_published.st_ctime_ns,
            )
            if current_published_identity[:4] != staged_identities[destination.name][:4]:
                raise StableFileError(
                    f"{label} output changed immediately after publication: {destination}"
                )
            if expected is not None:
                displaced = os.stat(
                    staged,
                    dir_fd=transaction_fd,
                    follow_symlinks=False,
                )
                displaced_identity = (
                    displaced.st_dev,
                    displaced.st_ino,
                    displaced.st_size,
                    displaced.st_mtime_ns,
                    displaced.st_ctime_ns,
                )
                backup = backup_names.get(destination.name)
                if (
                    displaced_identity[:4] != expected[:4]
                    or backup is None
                    or _stable_file_digest_at(transaction_fd, staged, label)
                    != _stable_file_digest_at(transaction_fd, backup, label)
                ):
                    still_published = os.stat(
                        destination.name,
                        dir_fd=parent_fd,
                        follow_symlinks=False,
                    )
                    still_published_identity = (
                        still_published.st_dev,
                        still_published.st_ino,
                        still_published.st_size,
                        still_published.st_mtime_ns,
                        still_published.st_ctime_ns,
                    )
                    if still_published_identity == current_published_identity:
                        _rename_exchange(
                            staged,
                            destination.name,
                            first_dir_fd=transaction_fd,
                            second_dir_fd=parent_fd,
                        )
                        published.pop()
                    raise StableFileError(
                        f"{label} output changed before publication: {destination}"
                    )
                staged_identities[destination.name] = displaced_identity
                staged_digests[destination.name] = backup_digests[destination.name]
            published_identities[destination.name] = current_published_identity
            os.fsync(parent_fd)
            ensure_parent_is_still_bound()

        commit_complete = True
        for output_name, backup in backup_names.items():
            try:
                _remove_file_if_unchanged(
                    transaction_fd, backup, backup_identities[output_name],
                    backup_digests[output_name], label,
                )
            except OSError as cleanup_exc:
                # Keep recovery material and tell the caller that publication
                # succeeded but cleanup did not.
                preserve_transaction = True
                raise cleanup_exc
        backup_names.clear()
        for index, staged in enumerate(staged_names):
            output_name = destinations[index].name
            try:
                _remove_file_if_unchanged(
                    transaction_fd, staged, staged_identities[output_name][:2],
                    staged_digests[output_name], label,
                )
            except FileNotFoundError:
                # Each successfully published staged file was renamed away.
                continue
            except OSError as cleanup_exc:
                preserve_transaction = True
                raise cleanup_exc
        os.fsync(transaction_fd)
    except (OSError, StableFileError) as exc:
        if commit_complete:
            preserve_transaction = True
            transaction_path = parent / transaction_name if transaction_name else parent
            raise StableFileError(
                f"{label} outputs were published but transaction cleanup failed: {exc}; "
                f"transaction retained at {transaction_path}"
            ) from exc
        rollback_errors: list[str] = []
        if parent_fd is not None and transaction_fd is not None:
            for name in reversed(published):
                try:
                    current = os.stat(
                        name,
                        dir_fd=parent_fd,
                        follow_symlinks=False,
                    )
                    current_identity = (
                        current.st_dev,
                        current.st_ino,
                        current.st_size,
                        current.st_mtime_ns,
                        current.st_ctime_ns,
                    )
                    expected_published = published_identities.get(name)
                    if expected_published is None or current_identity != expected_published:
                        rollback_errors.append(
                            f"{name}: output changed after publication; refusing rollback"
                        )
                        continue
                    backup = backup_names.get(name)
                    if backup is not None:
                        backup_metadata = os.stat(
                            backup,
                            dir_fd=transaction_fd,
                            follow_symlinks=False,
                        )
                        backup_identity = (
                            backup_metadata.st_dev,
                            backup_metadata.st_ino,
                            backup_metadata.st_size,
                            backup_metadata.st_mtime_ns,
                        )
                        backup_digest = _stable_file_digest_at(
                            transaction_fd, backup, label
                        )
                        _rename_exchange(
                            backup,
                            name,
                            first_dir_fd=transaction_fd,
                            second_dir_fd=parent_fd,
                        )
                        displaced = os.stat(
                            backup,
                            dir_fd=transaction_fd,
                            follow_symlinks=False,
                        )
                        displaced_identity = (
                            displaced.st_dev,
                            displaced.st_ino,
                            displaced.st_size,
                            displaced.st_mtime_ns,
                        )
                        displaced_digest = _stable_file_digest_at(
                            transaction_fd, backup, label
                        )
                        if (
                            displaced_identity == expected_published[:4]
                            and displaced_digest
                            == hashlib.sha256(content_by_name[name]).hexdigest()
                        ):
                            _remove_file_if_unchanged(
                                transaction_fd,
                                backup,
                                expected_published[:2],
                                hashlib.sha256(content_by_name[name]).hexdigest(),
                                label,
                            )
                            backup_names.pop(name, None)
                            published.remove(name)
                        else:
                            _rename_exchange(
                                backup,
                                name,
                                first_dir_fd=transaction_fd,
                                second_dir_fd=parent_fd,
                            )
                            if (
                                _stable_file_digest_at(transaction_fd, backup, label)
                                != backup_digest
                            ):
                                preserve_transaction = True
                            rollback_errors.append(
                                f"{name}: output changed during rollback; concurrent output restored"
                            )
                    else:
                        recovery = f"rollback-{index_by_name[name]}"
                        _rename_noreplace(
                            name,
                            recovery,
                            source_dir_fd=parent_fd,
                            destination_dir_fd=transaction_fd,
                        )
                        recovered = os.stat(
                            recovery,
                            dir_fd=transaction_fd,
                            follow_symlinks=False,
                        )
                        recovered_identity = (
                            recovered.st_dev,
                            recovered.st_ino,
                            recovered.st_size,
                            recovered.st_mtime_ns,
                        )
                        recovered_digest = _stable_file_digest_at(
                            transaction_fd, recovery, label
                        )
                        if (
                            recovered_identity == expected_published[:4]
                            and recovered_digest
                            == hashlib.sha256(content_by_name[name]).hexdigest()
                        ):
                            _remove_file_if_unchanged(
                                transaction_fd,
                                recovery,
                                expected_published[:2],
                                hashlib.sha256(content_by_name[name]).hexdigest(),
                                label,
                            )
                            published.remove(name)
                        else:
                            try:
                                _rename_noreplace(
                                    recovery,
                                    name,
                                    source_dir_fd=transaction_fd,
                                    destination_dir_fd=parent_fd,
                                )
                            except OSError as restore_exc:
                                preserve_transaction = True
                                rollback_errors.append(
                                    f"{name}: concurrent output could not be restored; "
                                    f"recovery file retained as {transaction_name}/{recovery}: {restore_exc}"
                                )
                            else:
                                rollback_errors.append(
                                    f"{name}: output changed during rollback; concurrent output restored"
                                )
                except (OSError, StableFileError) as rollback_exc:
                    rollback_errors.append(f"{name}: {rollback_exc}")
            try:
                os.fsync(parent_fd)
            except OSError as rollback_exc:
                rollback_errors.append(f"parent fsync: {rollback_exc}")
        if rollback_errors:
            preserve_transaction = True
            transaction_path = parent / transaction_name if transaction_name else parent
            raise StableFileError(
                f"could not publish {label}: {exc}; rollback errors: "
                + "; ".join(rollback_errors)
                + f"; transaction backups retained at {transaction_path}"
            ) from exc
        raise StableFileError(f"could not publish {label}: {exc}") from exc
    finally:
        active_exception = sys.exc_info()[1]
        cleanup_errors: list[BaseException] = []
        if transaction_fd is not None:
            if not preserve_transaction:
                for index, name in enumerate(staged_names):
                    try:
                        output_name = destinations[index].name
                        _remove_file_if_unchanged(
                            transaction_fd, name, staged_identities[output_name][:2],
                            staged_digests[output_name], label,
                        )
                    except FileNotFoundError:
                        pass
                    except BaseException as cleanup_exc:
                        preserve_transaction = True
                        cleanup_errors.append(cleanup_exc)
                for output_name, name in backup_names.items():
                    try:
                        _remove_file_if_unchanged(
                            transaction_fd, name, backup_identities[output_name],
                            backup_digests[output_name], label,
                        )
                    except FileNotFoundError:
                        pass
                    except BaseException as cleanup_exc:
                        preserve_transaction = True
                        cleanup_errors.append(cleanup_exc)
            try:
                os.close(transaction_fd)
            except OSError as cleanup_exc:
                cleanup_errors.append(cleanup_exc)
        if parent_fd is not None:
            if transaction_name is not None and not preserve_transaction:
                try:
                    if transaction_identity is None:
                        raise StableFileError(
                            f"cannot verify {label} transaction directory; entry retained at "
                            f"{parent / transaction_name}"
                        )
                    remove_directory_at(
                        parent_fd,
                        transaction_name,
                        expected_identity=transaction_identity,
                    )
                except FileNotFoundError:
                    pass
                except BaseException as cleanup_exc:
                    cleanup_errors.append(cleanup_exc)
            try:
                os.close(parent_fd)
            except OSError as cleanup_exc:
                cleanup_errors.append(cleanup_exc)
        if cleanup_errors:
            message = "; ".join(str(error) for error in cleanup_errors)
            if active_exception is not None:
                active_exception.add_note(f"transaction cleanup also failed: {message}")
            elif len(cleanup_errors) == 1 and isinstance(cleanup_errors[0], StableFileError):
                raise cleanup_errors[0]
            else:
                raise StableFileError(
                    f"could not safely clean up {label} transaction: {message}"
                ) from cleanup_errors[0]


def remove_directory_at(
    parent_fd: int,
    name: str,
    *,
    expected_identity: tuple[int, int] | None = None,
) -> None:
    """Remove a directory tree without following links or using Python recursion."""
    if (
        not isinstance(name, str)
        or not name
        or name in {".", ".."}
        or "/" in name
        or "\x00" in name
    ):
        raise StableFileError("removal target must be one normal directory name")
    root_metadata = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
    if not stat.S_ISDIR(root_metadata.st_mode):
        raise StableFileError(f"removal target is not a directory: {name}")
    root_identity = (root_metadata.st_dev, root_metadata.st_ino)
    if expected_identity is not None and root_identity != expected_identity:
        raise StableFileError(f"directory changed before tree removal: {name}")
    pending: list[tuple[tuple[tuple[str, tuple[int, int]], ...], bool]] = [
        (((name, root_identity),), False)
    ]

    def open_relative_directory(
        path: tuple[tuple[str, tuple[int, int]], ...],
    ) -> int:
        descriptor = os.dup(parent_fd)
        try:
            for component, expected in path:
                following = os.open(
                    component,
                    os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                    dir_fd=descriptor,
                )
                actual = os.fstat(following)
                if (actual.st_dev, actual.st_ino) != expected:
                    os.close(following)
                    raise StableFileError(
                        f"directory changed during tree removal: {component}"
                    )
                os.close(descriptor)
                descriptor = following
            return descriptor
        except BaseException:
            os.close(descriptor)
            raise

    while pending:
        path, visited = pending.pop()
        if visited:
            parent_path = path[:-1]
            leaf, expected_identity = path[-1]
            containing_fd = os.dup(parent_fd) if not parent_path else open_relative_directory(parent_path)
            try:
                quarantine: str | None = None
                for _ in range(100):
                    candidate = f".{leaf}.{secrets.token_hex(12)}.remove"
                    try:
                        _rename_noreplace(
                            leaf,
                            candidate,
                            source_dir_fd=containing_fd,
                            destination_dir_fd=containing_fd,
                        )
                        quarantine = candidate
                        break
                    except FileExistsError:
                        continue
                if quarantine is None:
                    raise StableFileError(
                        f"could not allocate a private tree-removal name for {leaf}"
                    )

                moved = os.stat(
                    quarantine,
                    dir_fd=containing_fd,
                    follow_symlinks=False,
                )
                if (moved.st_dev, moved.st_ino) != expected_identity:
                    try:
                        _rename_noreplace(
                            quarantine,
                            leaf,
                            source_dir_fd=containing_fd,
                            destination_dir_fd=containing_fd,
                        )
                    except OSError as restore_exc:
                        raise StableFileError(
                            f"directory changed during tree removal; entry retained as "
                            f"{quarantine}: {restore_exc}"
                        ) from restore_exc
                    raise StableFileError(
                        f"directory changed during tree removal: {leaf}"
                    )

                try:
                    os.rmdir(quarantine, dir_fd=containing_fd)
                except OSError as removal_exc:
                    try:
                        _rename_noreplace(
                            quarantine,
                            leaf,
                            source_dir_fd=containing_fd,
                            destination_dir_fd=containing_fd,
                        )
                    except OSError as restore_exc:
                        raise StableFileError(
                            f"could not remove directory {leaf}; entry retained as "
                            f"{quarantine}: remove={removal_exc}; restore={restore_exc}"
                        ) from removal_exc
                    raise StableFileError(
                        f"could not remove directory {leaf}: {removal_exc}"
                    ) from removal_exc
            finally:
                os.close(containing_fd)
            continue

        directory_fd = open_relative_directory(path)
        try:
            pending.append((path, True))
            for child in os.listdir(directory_fd):
                metadata = os.stat(child, dir_fd=directory_fd, follow_symlinks=False)
                if stat.S_ISDIR(metadata.st_mode):
                    identity = (metadata.st_dev, metadata.st_ino)
                    pending.append((path + ((child, identity),), False))
                else:
                    _remove_entry_if_same(
                        directory_fd, child, (metadata.st_dev, metadata.st_ino)
                    )
        finally:
            os.close(directory_fd)


def atomic_publish_directory(
    staging: Path,
    destination: Path,
    backup: Path,
    label: str = "directory",
    *,
    destination_must_be_empty: bool = False,
) -> None:
    """Promote a sibling directory with descriptor-bound rollback and recovery."""
    paths = [
        Path(os.path.abspath(os.fspath(path.expanduser())))
        for path in (staging, destination, backup)
    ]
    staging, destination, backup = paths
    if len({path.name for path in paths}) != 3 or any(
        path.parent != staging.parent for path in paths
    ):
        raise StableFileError(f"{label} publication paths must be distinct siblings")
    if any(not path.name or path.name in {".", ".."} for path in paths):
        raise StableFileError(f"{label} publication paths must have normal sibling names")
    parent = staging.parent
    parent_fd: int | None = None
    backup_name: str | None = None
    backup_cleanup_identity: tuple[int, int] | None = None
    published_new = False
    commit_complete = False
    try:
        parent_fd = open_directory_descriptor(parent, f"{label} parent directory")
        original_parent = os.fstat(parent_fd)

        def check_parent_binding() -> None:
            try:
                current_fd = open_directory_descriptor(parent, f"{label} parent directory")
            except StableFileError as exc:
                raise StableFileError(f"{label} parent changed during publication") from exc
            try:
                current = os.fstat(current_fd)
                if (current.st_dev, current.st_ino) != (
                    original_parent.st_dev,
                    original_parent.st_ino,
                ):
                    raise StableFileError(f"{label} parent changed during publication")
            finally:
                os.close(current_fd)

        def entry(name: str) -> os.stat_result | None:
            try:
                return os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
            except FileNotFoundError:
                return None

        def require_empty_directory(name: str, expected: os.stat_result) -> None:
            descriptor = os.open(
                name,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=parent_fd,
            )
            try:
                actual = os.fstat(descriptor)
                if (actual.st_dev, actual.st_ino) != (expected.st_dev, expected.st_ino):
                    raise StableFileError(f"{label} destination changed during collection")
                if os.listdir(descriptor):
                    raise StableFileError(
                        f"{label} output directory must be absent or empty: {destination}"
                    )
            finally:
                os.close(descriptor)

        backup_stat = entry(backup.name)
        destination_stat = entry(destination.name)
        if backup_stat is not None:
            if not stat.S_ISDIR(backup_stat.st_mode):
                raise StableFileError(f"{label} backup path must be a directory: {backup}")
            if destination_stat is not None:
                raise StableFileError(
                    f"{label} backup already exists beside output: {backup}"
                )
            _rename_noreplace(
                backup.name,
                destination.name,
                source_dir_fd=parent_fd,
                destination_dir_fd=parent_fd,
            )
            os.fsync(parent_fd)
            destination_stat = backup_stat

        staging_stat = entry(staging.name)
        if staging_stat is None or not stat.S_ISDIR(staging_stat.st_mode):
            raise StableFileError(f"{label} staging path must be a directory: {staging}")

        if destination_stat is not None:
            if not stat.S_ISDIR(destination_stat.st_mode):
                raise StableFileError(f"{label} destination must be a directory: {destination}")
            if destination_must_be_empty:
                require_empty_directory(destination.name, destination_stat)
            if entry(backup.name) is not None:
                raise StableFileError(f"{label} backup already exists beside output: {backup}")
            current = entry(destination.name)
            if current is None or (current.st_dev, current.st_ino) != (
                destination_stat.st_dev,
                destination_stat.st_ino,
            ):
                raise StableFileError(f"{label} destination changed before publication")
            if destination_must_be_empty:
                require_empty_directory(destination.name, current)
            _rename_noreplace(
                destination.name,
                backup.name,
                source_dir_fd=parent_fd,
                destination_dir_fd=parent_fd,
            )
            backup_name = backup.name
            moved_destination = entry(backup_name)
            if moved_destination is None or (
                moved_destination.st_dev,
                moved_destination.st_ino,
            ) != (destination_stat.st_dev, destination_stat.st_ino):
                raise StableFileError(
                    f"{label} destination changed while moving it to the backup path"
                )
            backup_cleanup_identity = (
                moved_destination.st_dev,
                moved_destination.st_ino,
            )
            os.fsync(parent_fd)

        try:
            check_parent_binding()
            current_staging = entry(staging.name)
            if current_staging is None or (current_staging.st_dev, current_staging.st_ino) != (
                staging_stat.st_dev,
                staging_stat.st_ino,
            ):
                raise StableFileError(f"{label} staging directory changed before publication")
            if entry(destination.name) is not None:
                raise StableFileError(f"{label} destination appeared before publication")
            _rename_noreplace(
                staging.name,
                destination.name,
                source_dir_fd=parent_fd,
                destination_dir_fd=parent_fd,
            )
            published_new = True
            current_published = entry(destination.name)
            if current_published is None or (
                current_published.st_dev,
                current_published.st_ino,
            ) != (staging_stat.st_dev, staging_stat.st_ino):
                raise StableFileError(
                    f"{label} published directory does not match the validated staging directory"
                )
            os.fsync(parent_fd)
            check_parent_binding()
        except (OSError, StableFileError) as publish_error:
            if published_new:
                try:
                    current_new = entry(destination.name)
                    if current_new is None or (current_new.st_dev, current_new.st_ino) != (
                        staging_stat.st_dev,
                        staging_stat.st_ino,
                    ):
                        raise OSError("published directory changed before rollback")
                    _rename_noreplace(
                        destination.name,
                        staging.name,
                        source_dir_fd=parent_fd,
                        destination_dir_fd=parent_fd,
                    )
                    os.fsync(parent_fd)
                except OSError as rollback_error:
                    if backup_name is not None:
                        raise StableFileError(
                            f"{label} publication failed; previous directory remains at "
                            f"{parent / backup_name}: publish={publish_error}; rollback={rollback_error}"
                        ) from publish_error
                    raise StableFileError(
                        f"{label} publication rollback failed: {rollback_error}"
                    ) from publish_error
            if backup_name is not None:
                try:
                    _rename_noreplace(
                        backup_name,
                        destination.name,
                        source_dir_fd=parent_fd,
                        destination_dir_fd=parent_fd,
                    )
                    backup_name = None
                    os.fsync(parent_fd)
                except OSError as rollback_error:
                    recovery = (
                        f"previous directory was restored at {destination}"
                        if backup_name is None
                        else f"previous directory remains at {parent / backup_name}"
                    )
                    raise StableFileError(
                        f"{label} publication failed; {recovery}; directory sync failed: "
                        f"publish={publish_error}; rollback={rollback_error}"
                    ) from publish_error
            raise StableFileError(f"could not publish {label}: {publish_error}") from publish_error

        commit_complete = True
        if backup_name is not None:
            remove_directory_at(
                parent_fd,
                backup_name,
                expected_identity=backup_cleanup_identity,
            )
            backup_name = None
            os.fsync(parent_fd)
    except (OSError, StableFileError) as exc:
        if commit_complete:
            if backup_name is None:
                raise StableFileError(
                    f"{label} was published and backup was removed, but directory sync failed: {exc}"
                ) from exc
            recovery = f"; partial backup may remain at {parent / backup_name}"
            raise StableFileError(
                f"{label} was published but backup cleanup failed: {exc}{recovery}"
            ) from exc
        if backup_name is not None and parent_fd is not None:
            try:
                if entry(destination.name) is None:
                    _rename_noreplace(
                        backup_name,
                        destination.name,
                        source_dir_fd=parent_fd,
                        destination_dir_fd=parent_fd,
                    )
                    backup_name = None
                    os.fsync(parent_fd)
            except OSError as rollback_error:
                recovery = (
                    f"previous directory was restored at {destination}"
                    if backup_name is None
                    else f"previous directory remains at {parent / backup_name}"
                )
                raise StableFileError(
                    f"{label} publication failed; {recovery}; directory sync failed: "
                    f"publish={exc}; rollback={rollback_error}"
                ) from exc
        if isinstance(exc, StableFileError):
            raise
        raise StableFileError(f"could not safely publish {label}: {exc}") from exc
    finally:
        if parent_fd is not None:
            os.close(parent_fd)


def atomic_write_json(path: Path, payload: object, label: str = "JSON evidence") -> None:
    """Safely create parents and atomically publish deterministic JSON bytes."""
    content = (
        json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + "\n"
    ).encode("utf-8")
    ensure_directory_exists(path.parent, f"{label} parent directory")
    atomic_write_bytes(path, content, label)


def consume_regular_file(
    path: Path, label: str, maximum_bytes: int, *, retain_bytes: bool
) -> tuple[bytes | None, str, os.stat_result]:
    """Read/hash a bounded file by walking no-follow directory descriptors."""
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_DIRECTORY"):
        raise StableFileError("safe evidence reads require O_NOFOLLOW and O_DIRECTORY")
    directory_flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    file_flags = os.O_RDONLY | os.O_NOFOLLOW
    if hasattr(os, "O_CLOEXEC"):
        directory_flags |= os.O_CLOEXEC
        file_flags |= os.O_CLOEXEC
    if hasattr(os, "O_NONBLOCK"):
        file_flags |= os.O_NONBLOCK

    directories: list[int] = []
    descriptor: int | None = None
    try:
        current = os.open(absolute.anchor, directory_flags)
        directories.append(current)
        for component in absolute.parts[1:-1]:
            current = os.open(component, directory_flags, dir_fd=current)
            directories.append(current)
        leaf = absolute.parts[-1]
        before = os.stat(leaf, dir_fd=current, follow_symlinks=False)
        if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
            raise StableFileError(
                f"{label} must be a regular non-symlink file (not a regular file or symbolic link): {absolute}"
            )
        if before.st_size <= 0:
            raise StableFileError(
                f"{label} is empty and must be between 1 and {maximum_bytes} bytes: {absolute}"
            )
        if before.st_size > maximum_bytes:
            raise StableFileError(
                f"{label} has an invalid size; must be between 1 and {maximum_bytes} bytes; safety limit exceeded: {absolute}"
            )

        descriptor = os.open(leaf, file_flags, dir_fd=current)
        fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field) for field in fields
        ):
            raise StableFileError(f"{label} changed while being opened: {absolute}")

        digest = hashlib.sha256()
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise StableFileError(f"{label} changed while being read: {absolute}")
            digest.update(chunk)
            if retain_bytes:
                chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise StableFileError(f"{label} changed while being read: {absolute}")

        after = os.fstat(descriptor)
        after_path = os.stat(leaf, dir_fd=current, follow_symlinks=False)
        if any(
            getattr(before, field) != getattr(after, field)
            or getattr(before, field) != getattr(after_path, field)
            for field in fields
        ):
            raise StableFileError(f"{label} changed while being read: {absolute}")
        return (b"".join(chunks) if retain_bytes else None), digest.hexdigest(), after
    except StableFileError:
        raise
    except FileNotFoundError as exc:
        raise StableFileError(f"{label} is missing or changed: {absolute}") from exc
    except OSError as exc:
        if exc.errno in {errno.ELOOP, errno.ENOTDIR}:
            raise StableFileError(
                f"{label} path traverses a symbolic link or non-directory: {absolute}: {exc}"
            ) from exc
        raise StableFileError(f"could not safely read {label}: {absolute}: {exc}") from exc
    finally:
        if descriptor is not None:
            os.close(descriptor)
        for directory in reversed(directories):
            os.close(directory)
