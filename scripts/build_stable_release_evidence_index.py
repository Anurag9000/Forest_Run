#!/usr/bin/env python3
"""Build and independently verify a release-evidence index from stable snapshots."""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import stat
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable, Sequence

import build_release_evidence_index as builder
import verify_release_evidence_index as verifier
from stable_file_reader import (
    StableFileError,
    checked_directory_path,
    open_directory_descriptor,
    _remove_file_if_unchanged,
    _stable_file_digest_at,
)


class StableEvidenceIndexError(ValueError):
    """Raised when evidence cannot be frozen and published without races."""


@dataclass(frozen=True)
class StableSnapshot:
    relative_path: str
    device: int
    inode: int
    size: int
    mtime_ns: int
    sha256: str


def _safe_relative_path(spec: str) -> tuple[str, str]:
    kind, separator, path = spec.partition("=")
    if not separator:
        raise StableEvidenceIndexError("each entry must use kind=relative/path")
    relative = PurePosixPath(path)
    if (
        not kind
        or relative.is_absolute()
        or not relative.parts
        or any(part in {"", ".", ".."} for part in relative.parts)
        or "\\" in path
        or "\x00" in path
        or ":" in path
        or any(ord(character) < 32 or ord(character) == 127 for character in path)
        or relative.as_posix() != path
    ):
        raise StableEvidenceIndexError(f"unsafe evidence entry: {spec!r}")
    try:
        path.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise StableEvidenceIndexError(
            "evidence paths must contain valid Unicode text"
        ) from exc
    return kind, relative.as_posix()


def _assert_no_symlink_components(root: Path, relative: str) -> Path:
    current = root
    for part in PurePosixPath(relative).parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError as exc:
            raise StableEvidenceIndexError(
                f"evidence file is missing: {relative}"
            ) from exc
        except OSError as exc:
            raise StableEvidenceIndexError(
                f"could not inspect evidence path {relative}: {exc}"
            ) from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise StableEvidenceIndexError(
                f"evidence path must not traverse a symbolic link: {relative}"
            )
    return current


def _read_snapshot(
    root: Path,
    relative: str,
    destination: Path | None = None,
) -> StableSnapshot:
    path = _assert_no_symlink_components(root, relative)
    parent_descriptor: int | None = None
    descriptor: int | None = None
    try:
        parent_descriptor = open_directory_descriptor(
            path.parent,
            f"evidence parent directory for {relative}",
        )
        preopen = os.stat(
            path.name,
            dir_fd=parent_descriptor,
            follow_symlinks=False,
        )
    except StableFileError as exc:
        if parent_descriptor is not None:
            os.close(parent_descriptor)
        raise StableEvidenceIndexError(
            f"could not safely open evidence parent directory: {relative}: {exc}"
        ) from exc
    except OSError as exc:
        if parent_descriptor is not None:
            os.close(parent_descriptor)
        raise StableEvidenceIndexError(
            f"could not inspect evidence file before open: {relative}: {exc}"
        ) from exc
    if stat.S_ISLNK(preopen.st_mode):
        os.close(parent_descriptor)
        raise StableEvidenceIndexError(
            f"evidence path must not traverse a symbolic link: {relative}"
        )
    if not stat.S_ISREG(preopen.st_mode):
        os.close(parent_descriptor)
        raise StableEvidenceIndexError(
            f"evidence path is not a regular file: {relative}"
        )
    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    # A malicious/racing replacement with a FIFO must never block in open()
    # before the post-open fstat() can reject the non-regular descriptor.
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    try:
        descriptor = os.open(path.name, flags, dir_fd=parent_descriptor)
    except OSError as exc:
        os.close(parent_descriptor)
        raise StableEvidenceIndexError(
            f"could not open evidence file without following links: {relative}: {exc}"
        ) from exc
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise StableEvidenceIndexError(
                f"evidence path is not a regular file: {relative}"
            )
        identity_fields = (
            "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns"
        )
        if any(
            getattr(preopen, field) != getattr(before, field)
            for field in identity_fields
        ):
            raise StableEvidenceIndexError(
                f"evidence file changed before snapshot: {relative}"
            )
        if before.st_size <= 0 or before.st_size > builder.MAX_FILE_BYTES:
            raise StableEvidenceIndexError(
                f"evidence file size is outside the supported range: {relative}"
            )
        digest = hashlib.sha256()
        copied_stream = None
        if destination is not None:
            destination.parent.mkdir(parents=True, exist_ok=True)
            try:
                copied_fd = os.open(
                    destination,
                    os.O_WRONLY
                    | os.O_CREAT
                    | os.O_EXCL
                    | getattr(os, "O_NOFOLLOW", 0)
                    | getattr(os, "O_CLOEXEC", 0),
                    0o600,
                )
            except OSError as exc:
                raise StableEvidenceIndexError(
                    f"could not create private evidence snapshot: {relative}: {exc}"
                ) from exc
            copied_stream = os.fdopen(copied_fd, "wb")
        remaining = before.st_size
        try:
            while remaining:
                chunk = os.read(descriptor, min(1024 * 1024, remaining))
                if not chunk:
                    raise StableEvidenceIndexError(
                        f"evidence file ended during snapshot: {relative}"
                    )
                digest.update(chunk)
                if copied_stream is not None:
                    copied_stream.write(chunk)
                remaining -= len(chunk)
            if os.read(descriptor, 1):
                raise StableEvidenceIndexError(
                    f"evidence file grew during snapshot: {relative}"
                )
            after = os.fstat(descriptor)
            after_path = os.stat(
                path.name,
                dir_fd=parent_descriptor,
                follow_symlinks=False,
            )
            if any(
                getattr(before, field) != getattr(after_path, field)
                for field in identity_fields
            ):
                raise StableEvidenceIndexError(
                    f"evidence file changed during snapshot: {relative}"
                )
            if copied_stream is not None:
                copied_stream.flush()
        except OSError as exc:
            raise StableEvidenceIndexError(
                f"could not write evidence snapshot: {relative}: {exc}"
            ) from exc
        finally:
            if copied_stream is not None:
                copied_stream.close()
    finally:
        os.close(descriptor)
        os.close(parent_descriptor)

    if any(getattr(before, field) != getattr(after, field) for field in identity_fields):
        raise StableEvidenceIndexError(
            f"evidence file changed during snapshot: {relative}"
        )
    return StableSnapshot(
        relative_path=relative,
        device=before.st_dev,
        inode=before.st_ino,
        size=before.st_size,
        mtime_ns=before.st_mtime_ns,
        sha256=digest.hexdigest(),
    )


def _confirm_unchanged(root: Path, snapshot: StableSnapshot) -> None:
    confirmed = _read_snapshot(root, snapshot.relative_path)
    if (
        confirmed.device != snapshot.device
        or confirmed.inode != snapshot.inode
        or confirmed.size != snapshot.size
        or confirmed.mtime_ns != snapshot.mtime_ns
        or confirmed.sha256 != snapshot.sha256
    ):
        raise StableEvidenceIndexError(
            f"evidence file changed after snapshot: {snapshot.relative_path}"
        )


def build_stable_index(
    *,
    root: Path,
    candidate_sha: str,
    specs: Sequence[str],
    generated_at_utc: str,
    output: Path,
    require_bound_kinds: Iterable[str] = (),
) -> dict[str, object]:
    if isinstance(require_bound_kinds, str):
        require_bound_kinds = (require_bound_kinds,)
    else:
        try:
            require_bound_kinds = tuple(require_bound_kinds)
        except TypeError as exc:
            raise StableEvidenceIndexError(
                "required candidate-bound kinds must be an iterable of strings"
            ) from exc
    try:
        root = checked_directory_path(root, "evidence root")
    except StableFileError as exc:
        raise StableEvidenceIndexError(str(exc)) from exc
    if not root.is_dir():
        raise StableEvidenceIndexError("evidence root must be an existing directory")
    parsed = [_safe_relative_path(spec) for spec in specs]
    with tempfile.TemporaryDirectory(prefix="forest-run-evidence-snapshot-") as tmp:
        snapshot_root = Path(tmp)
        snapshots = []
        physical: set[tuple[int, int]] = set()
        for _, relative in parsed:
            destination = snapshot_root / relative
            snapshot = _read_snapshot(root, relative, destination)
            identity = (snapshot.device, snapshot.inode)
            if identity in physical:
                raise StableEvidenceIndexError(
                    "one physical evidence file is reused through a hard link"
                )
            physical.add(identity)
            snapshots.append(snapshot)
        snapshot_output = snapshot_root / "release-evidence-index.json"
        payload = builder.build_index(
            snapshot_root,
            candidate_sha,
            specs,
            generated_at_utc=generated_at_utc,
            require_bound_kinds=require_bound_kinds,
            output=snapshot_output,
        )
        for snapshot in snapshots:
            _confirm_unchanged(root, snapshot)

    real_output = output if output.is_absolute() else root / output
    builder.publish_index(real_output, payload, root=root)
    output_parent_fd: int | None = None
    try:
        output_parent_fd = open_directory_descriptor(
            real_output.parent, "published evidence-index parent"
        )
        published = os.stat(
            real_output.name,
            dir_fd=output_parent_fd,
            follow_symlinks=False,
        )
        if not stat.S_ISREG(published.st_mode):
            raise StableEvidenceIndexError(
                "published evidence index is not a regular file"
            )
        published_identity = (
            published.st_dev,
            published.st_ino,
            published.st_size,
            published.st_mtime_ns,
            published.st_ctime_ns,
        )
        published_digest = _stable_file_digest_at(
            output_parent_fd, real_output.name, "published evidence index"
        )
    except verifier.EvidenceIndexVerificationError as exc:
        if output_parent_fd is not None:
            os.close(output_parent_fd)
        raise StableEvidenceIndexError(
            f"could not bind published evidence index for independent verification: {exc}"
        ) from exc
    except StableEvidenceIndexError:
        if output_parent_fd is not None:
            os.close(output_parent_fd)
        raise
    except (OSError, StableFileError) as exc:
        if output_parent_fd is not None:
            os.close(output_parent_fd)
        raise StableEvidenceIndexError(
            f"could not safely bind published evidence index for verification: {exc}"
        ) from exc
    try:
        summary = verifier.verify_index(
            real_output,
            root=root,
            expected_candidate_sha=candidate_sha,
            require_bound_kinds=require_bound_kinds,
        )
        verified = os.stat(
            real_output.name,
            dir_fd=output_parent_fd,
            follow_symlinks=False,
        )
        verified_identity = (
            verified.st_dev,
            verified.st_ino,
            verified.st_size,
            verified.st_mtime_ns,
            verified.st_ctime_ns,
        )
        if verified_identity != published_identity:
            raise StableEvidenceIndexError(
                "published evidence index changed during independent verification"
            )
    except verifier.EvidenceIndexVerificationError as exc:
        cleanup_note = ""
        try:
            current = os.stat(
                real_output.name,
                dir_fd=output_parent_fd,
                follow_symlinks=False,
            )
            current_identity = (
                current.st_dev,
                current.st_ino,
                current.st_size,
                current.st_mtime_ns,
                current.st_ctime_ns,
            )
            if current_identity == published_identity:
                _remove_file_if_unchanged(
                    output_parent_fd,
                    real_output.name,
                    published_identity[:2],
                    published_digest,
                    "published evidence index",
                )
                os.fsync(output_parent_fd)
            else:
                cleanup_note = "; published output changed and was not removed"
        except FileNotFoundError:
            pass
        except (OSError, StableFileError) as cleanup_exc:
            cleanup_note = f"; could not remove published output safely: {cleanup_exc}"
        raise StableEvidenceIndexError(
            f"independent verification failed after publication: {exc}{cleanup_note}"
        ) from exc
    finally:
        if output_parent_fd is not None:
            os.close(output_parent_fd)
    return summary


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--entry", action="append", default=[], metavar="KIND=PATH")
    parser.add_argument("--require-bound-kind", action="append", default=[])
    parser.add_argument("--generated-at-utc", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        summary = build_stable_index(
            root=args.root,
            candidate_sha=args.candidate_sha,
            specs=args.entry,
            generated_at_utc=args.generated_at_utc,
            output=args.output,
            require_bound_kinds=args.require_bound_kind,
        )
    except (StableEvidenceIndexError, builder.EvidenceIndexError) as exc:
        print(f"stable release evidence index error: {exc}", file=os.sys.stderr)
        return 1
    print(
        f"built and verified {summary['entryCount']} stable evidence files "
        f"for {summary['candidateSha']} ({summary['evidenceSetSha256']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
