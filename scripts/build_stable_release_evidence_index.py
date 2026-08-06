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


class StableEvidenceIndexError(ValueError):
    """Raised when evidence cannot be frozen and published without races."""


@dataclass(frozen=True)
class StableSnapshot:
    relative_path: str
    bytes_value: bytes
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
    ):
        raise StableEvidenceIndexError(f"unsafe evidence entry: {spec!r}")
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


def _read_snapshot(root: Path, relative: str) -> StableSnapshot:
    path = _assert_no_symlink_components(root, relative)
    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise StableEvidenceIndexError(
            f"could not open evidence file without following links: {relative}: {exc}"
        ) from exc
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise StableEvidenceIndexError(
                f"evidence path is not a regular file: {relative}"
            )
        if before.st_size <= 0 or before.st_size > builder.MAX_FILE_BYTES:
            raise StableEvidenceIndexError(
                f"evidence file size is outside the supported range: {relative}"
            )
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise StableEvidenceIndexError(
                    f"evidence file ended during snapshot: {relative}"
                )
            chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise StableEvidenceIndexError(
                f"evidence file grew during snapshot: {relative}"
            )
        after = os.fstat(descriptor)
    finally:
        os.close(descriptor)

    stable_fields = (
        "st_dev",
        "st_ino",
        "st_mode",
        "st_size",
        "st_mtime_ns",
        "st_ctime_ns",
    )
    if any(getattr(before, field) != getattr(after, field) for field in stable_fields):
        raise StableEvidenceIndexError(
            f"evidence file changed during snapshot: {relative}"
        )
    value = b"".join(chunks)
    if len(value) != before.st_size:
        raise StableEvidenceIndexError(
            f"evidence snapshot size mismatch: {relative}"
        )
    return StableSnapshot(
        relative_path=relative,
        bytes_value=value,
        device=before.st_dev,
        inode=before.st_ino,
        size=before.st_size,
        mtime_ns=before.st_mtime_ns,
        sha256=hashlib.sha256(value).hexdigest(),
    )


def _confirm_unchanged(root: Path, snapshot: StableSnapshot) -> None:
    confirmed = _read_snapshot(root, snapshot.relative_path)
    if (
        confirmed.device != snapshot.device
        or confirmed.inode != snapshot.inode
        or confirmed.size != snapshot.size
        or confirmed.mtime_ns != snapshot.mtime_ns
        or confirmed.sha256 != snapshot.sha256
        or confirmed.bytes_value != snapshot.bytes_value
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
    root = root.expanduser().resolve()
    if not root.is_dir():
        raise StableEvidenceIndexError("evidence root must be an existing directory")
    parsed = [_safe_relative_path(spec) for spec in specs]
    snapshots = [_read_snapshot(root, relative) for _, relative in parsed]
    physical = {(snapshot.device, snapshot.inode) for snapshot in snapshots}
    if len(physical) != len(snapshots):
        raise StableEvidenceIndexError(
            "one physical evidence file is reused through a hard link"
        )

    with tempfile.TemporaryDirectory(prefix="forest-run-evidence-snapshot-") as tmp:
        snapshot_root = Path(tmp)
        for snapshot in snapshots:
            destination = snapshot_root / snapshot.relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(snapshot.bytes_value)
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
    try:
        summary = verifier.verify_index(
            real_output,
            root=root,
            expected_candidate_sha=candidate_sha,
            require_bound_kinds=require_bound_kinds,
        )
    except verifier.EvidenceIndexVerificationError as exc:
        real_output.unlink(missing_ok=True)
        raise StableEvidenceIndexError(
            f"independent verification failed after publication: {exc}"
        ) from exc
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
