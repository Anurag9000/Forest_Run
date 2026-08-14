#!/usr/bin/env python3
"""Inspect one packaged Android artifact for native-code page-size risk."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any


SHA_RE = re.compile(r"^[0-9a-f]{40}$")
MAX_ARTIFACT_BYTES = 512 * 1024 * 1024
MAX_ENTRY_COUNT = 20_000
MAX_ENTRY_BYTES = 256 * 1024 * 1024
MAX_TOTAL_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024


class PageSizeInspectionError(RuntimeError):
    pass


def _canonical_bytes(payload: dict[str, Any]) -> bytes:
    return (
        json.dumps(
            payload,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )
        + "\n"
    ).encode("utf-8")


def _safe_archive_name(name: str) -> str:
    if not name or "\x00" in name or "\\" in name:
        raise PageSizeInspectionError("artifact contains an unsafe archive path")
    path = PurePosixPath(name)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise PageSizeInspectionError("artifact contains an unsafe archive path")
    return path.as_posix()


def _open_regular_file(path: Path):
    if path.is_symlink():
        raise PageSizeInspectionError("artifact must not be a symbolic link")
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        raise PageSizeInspectionError(f"unable to open artifact: {error}") from error
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise PageSizeInspectionError("artifact must be a regular file")
        if metadata.st_size <= 0 or metadata.st_size > MAX_ARTIFACT_BYTES:
            raise PageSizeInspectionError("artifact size is outside the accepted bound")
        return os.fdopen(descriptor, "rb"), metadata.st_size
    except Exception:
        os.close(descriptor)
        raise


def inspect_artifact(
    artifact: Path,
    candidate_sha: str,
    require_no_native_code: bool = False,
) -> dict[str, Any]:
    if not SHA_RE.fullmatch(candidate_sha):
        raise PageSizeInspectionError(
            "candidate SHA must be 40 lowercase hexadecimal characters"
        )

    original_artifact = artifact
    if original_artifact.is_symlink():
        raise PageSizeInspectionError("artifact must not be a symbolic link")
    artifact = original_artifact.resolve(strict=True)
    stream, expected_size = _open_regular_file(artifact)
    digest = hashlib.sha256()
    with stream:
        with tempfile.NamedTemporaryFile(prefix="forest-run-page-size-", suffix=".zip") as frozen:
            copied = 0
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                copied += len(chunk)
                if copied > expected_size:
                    raise PageSizeInspectionError("artifact grew while being inspected")
                digest.update(chunk)
                frozen.write(chunk)
            if copied != expected_size:
                raise PageSizeInspectionError("artifact changed while being inspected")
            frozen.flush()
            os.fsync(frozen.fileno())

            native_libraries: list[str] = []
            seen: set[str] = set()
            total_uncompressed = 0
            try:
                with zipfile.ZipFile(frozen.name) as archive:
                    entries = archive.infolist()
                    if len(entries) > MAX_ENTRY_COUNT:
                        raise PageSizeInspectionError("artifact contains too many entries")
                    for entry in entries:
                        name = _safe_archive_name(entry.filename)
                        if name in seen:
                            raise PageSizeInspectionError(
                                f"artifact contains duplicate entry: {name}"
                            )
                        seen.add(name)
                        if entry.flag_bits & 0x1:
                            raise PageSizeInspectionError(
                                f"artifact contains encrypted entry: {name}"
                            )
                        mode = (entry.external_attr >> 16) & 0xFFFF
                        if stat.S_ISLNK(mode):
                            raise PageSizeInspectionError(
                                f"artifact contains symbolic-link entry: {name}"
                            )
                        if entry.file_size < 0 or entry.file_size > MAX_ENTRY_BYTES:
                            raise PageSizeInspectionError(
                                f"artifact entry exceeds size bound: {name}"
                            )
                        total_uncompressed += entry.file_size
                        if total_uncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES:
                            raise PageSizeInspectionError(
                                "artifact exceeds total uncompressed size bound"
                            )
                        if not entry.is_dir() and name.lower().endswith(".so"):
                            native_libraries.append(name)
            except zipfile.BadZipFile as error:
                raise PageSizeInspectionError("artifact is not a valid ZIP container") from error

    native_libraries.sort()
    if require_no_native_code and native_libraries:
        raise PageSizeInspectionError(
            "native libraries require ELF and package-alignment verification: "
            + ", ".join(native_libraries)
        )

    assessment = (
        "no-native-code"
        if not native_libraries
        else "native-verification-required"
    )
    return {
        "schemaVersion": 1,
        "candidateSha": candidate_sha,
        "artifactSha256": digest.hexdigest(),
        "artifactBytes": expected_size,
        "archiveEntryCount": len(seen),
        "totalUncompressedBytes": total_uncompressed,
        "nativeLibraryCount": len(native_libraries),
        "nativeLibraries": native_libraries,
        "assessment": assessment,
        "compatibleByPackageInspection": not native_libraries,
        "limitations": [
            "Package inspection does not replace runtime testing on a 16384-byte page-size device.",
            "Artifacts containing native libraries require independent ZIP and ELF alignment verification.",
        ],
    }


def publish(output: Path, payload: dict[str, Any]) -> None:
    if output.is_symlink():
        raise PageSizeInspectionError("output must not be a symbolic link")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output.name}.", suffix=".tmp", dir=output.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(_canonical_bytes(payload))
            stream.flush()
            os.fsync(stream.fileno())
        if output.is_symlink():
            raise PageSizeInspectionError("output became a symbolic link")
        os.replace(temporary, output)
        directory_descriptor = os.open(output.parent, os.O_RDONLY)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    finally:
        temporary.unlink(missing_ok=True)


def _resolve_cli_inputs(arguments: argparse.Namespace) -> tuple[Path, str, bool]:
    positional_artifact: Path | None = arguments.artifact
    legacy_artifact: Path | None = arguments.legacy_artifact
    if positional_artifact is not None and legacy_artifact is not None:
        raise PageSizeInspectionError(
            "artifact must be supplied exactly once, either positionally or with --artifact"
        )
    artifact = positional_artifact or legacy_artifact
    if artifact is None:
        raise PageSizeInspectionError("artifact path is required")

    candidate_sha = arguments.candidate_sha or os.environ.get("GITHUB_SHA", "")
    if not SHA_RE.fullmatch(candidate_sha):
        raise PageSizeInspectionError(
            "candidate SHA must be provided explicitly or by GITHUB_SHA as 40 lowercase hexadecimal characters"
        )

    legacy_mode = legacy_artifact is not None or arguments.legacy_build_tools_dir is not None
    require_no_native_code = arguments.require_no_native_code or legacy_mode
    return artifact, candidate_sha, require_no_native_code


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact", nargs="?", type=Path)
    parser.add_argument("--candidate-sha")
    parser.add_argument("--require-no-native-code", action="store_true")
    parser.add_argument("--output", type=Path)
    # Compatibility with the historical CI invocation. Legacy mode is intentionally
    # stricter than the general inspector: it always requires a native-free package.
    parser.add_argument("--artifact", dest="legacy_artifact", type=Path)
    parser.add_argument("--build-tools-dir", dest="legacy_build_tools_dir", type=Path)
    arguments = parser.parse_args()
    try:
        artifact, candidate_sha, require_no_native_code = _resolve_cli_inputs(arguments)
        payload = inspect_artifact(
            artifact,
            candidate_sha,
            require_no_native_code=require_no_native_code,
        )
        if arguments.output is not None:
            publish(arguments.output, payload)
        print(json.dumps({"status": "valid", **payload}, sort_keys=True))
        return 0
    except (OSError, PageSizeInspectionError) as error:
        print(json.dumps({"status": "invalid", "error": str(error)}, sort_keys=True))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
