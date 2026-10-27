#!/usr/bin/env python3
"""Validate measured identity evidence for a Play-delivered Forest Run install.

The record proves package/version/installer/APK-set/signing-certificate observations
from one connected Android device and binds those observations to an expected clean
main candidate. It deliberately does not claim which Play track delivered the app.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

import strict_json
from stable_file_reader import StableFileError, atomic_write_json, checked_directory_path

SCHEMA_VERSION = 1
CANONICAL_REPOSITORY = "Anurag9000/Forest_Run"
CANONICAL_BRANCH = "main"
CANONICAL_APPLICATION_ID = "com.anurag9000.forestrun"
PLAY_STORE_INSTALLER = "com.android.vending"
MAX_MANIFEST_BYTES = 4 * 1024 * 1024
MAX_EVIDENCE_FILE_BYTES = 512 * 1024 * 1024
SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
APK_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._+-]{0,159}\.apk$")


class InstalledIdentityError(ValueError):
    """Raised when installed-package identity evidence is incomplete or unsafe."""


@dataclass(frozen=True)
class InstalledIdentitySummary:
    candidate_sha: str
    application_id: str
    version_code: int
    app_signing_certificate_sha256: str
    installer_package: str
    apk_count: int
    evidence_file_count: int
    manifest_sha256: str

    def to_json(self) -> dict[str, object]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "application_id": self.application_id,
            "version_code": self.version_code,
            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,
            "installer_package": self.installer_package,
            "apk_count": self.apk_count,
            "evidence_file_count": self.evidence_file_count,
            "manifest_sha256": self.manifest_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise InstalledIdentityError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise InstalledIdentityError(f"{label} must be an array")
    return value


def _string(value: Any, label: str, *, maximum: int = 4096) -> str:
    if not isinstance(value, str):
        raise InstalledIdentityError(f"{label} must be a string")
    result = value.strip()
    if not result:
        raise InstalledIdentityError(f"{label} must not be blank")
    if len(result) > maximum:
        raise InstalledIdentityError(f"{label} exceeds the {maximum}-character limit")
    if any(ord(character) < 32 or ord(character) == 127 for character in result):
        raise InstalledIdentityError(f"{label} must not contain control characters")
    return result


def _canonical_hash(value: Any, label: str, pattern: re.Pattern[str], description: str) -> str:
    """Validate a digest's exact spelling instead of trimming it as display text."""
    if not isinstance(value, str) or not pattern.fullmatch(value):
        raise InstalledIdentityError(f"{label} must be {description}")
    return value


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise InstalledIdentityError(f"{label} must be an integer")
    if value < minimum:
        raise InstalledIdentityError(f"{label} must be >= {minimum}")
    return value


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise InstalledIdentityError(f"{label} must be a boolean")
    return value


def _require_exact_keys(value: Mapping[str, Any], keys: Iterable[str], label: str) -> None:
    expected = set(keys)
    actual = set(value)
    missing = sorted(expected - actual)
    extras = sorted(actual - expected)
    if missing:
        raise InstalledIdentityError(f"{label} is missing: {', '.join(missing)}")
    if extras:
        raise InstalledIdentityError(f"{label} contains unrecognized keys: {', '.join(extras)}")


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label, maximum=64)
    if not text.endswith("Z"):
        raise InstalledIdentityError(f"{label} must use ISO-8601 UTC Z notation")
    try:
        parsed = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as exc:
        raise InstalledIdentityError(f"{label} is not a valid UTC timestamp") from exc
    return parsed.astimezone(timezone.utc)


def _safe_relative_path(value: Any, label: str) -> str:
    text = _string(value, label, maximum=512)
    if value != text:
        raise InstalledIdentityError(f"{label} must not have surrounding whitespace")
    try:
        text.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise InstalledIdentityError(
            f"{label} must contain valid Unicode text"
        ) from exc
    if "\\" in text or ":" in text or text.startswith("~") or "\x00" in text:
        raise InstalledIdentityError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise InstalledIdentityError(f"{label} must be a safe relative path")
    if path.as_posix() != text:
        raise InstalledIdentityError(f"{label} must be a normalized POSIX relative path")
    return path.as_posix()


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    try:
        canonical = checked_directory_path(base, f"{label} evidence root")
    except StableFileError as exc:
        raise InstalledIdentityError(str(exc)) from exc
    relative_path = PurePosixPath(relative)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise InstalledIdentityError(f"{label} resolves outside the evidence root")
    lexical = canonical / relative
    try:
        parts = lexical.relative_to(canonical).parts
    except ValueError as exc:
        raise InstalledIdentityError(f"{label} escapes the evidence root") from exc
    current = canonical
    for part in parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            break
        except OSError as exc:
            raise InstalledIdentityError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise InstalledIdentityError(f"{label} must not traverse a symbolic link: {current}")
    return lexical


def _consume_regular_file(
    path: Path, label: str, maximum_bytes: int, *, retain_bytes: bool
) -> tuple[bytes | None, str, os.stat_result]:
    """Consume a bounded file through no-follow directory descriptors."""
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "O_DIRECTORY"):
        raise InstalledIdentityError("safe evidence reads require O_NOFOLLOW and O_DIRECTORY")
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
            raise InstalledIdentityError(f"{label} must be a regular non-symlink file: {absolute}")
        if before.st_size <= 0 or before.st_size > maximum_bytes:
            raise InstalledIdentityError(
                f"{label} must be between 1 and {maximum_bytes} bytes: {absolute}"
            )
        descriptor = os.open(leaf, file_flags, dir_fd=current)
        identity_fields = (
            "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns"
        )
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field) for field in identity_fields
        ):
            raise InstalledIdentityError(f"{label} changed while being opened: {absolute}")
        digest = hashlib.sha256()
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise InstalledIdentityError(f"{label} changed while being read: {absolute}")
            digest.update(chunk)
            if retain_bytes:
                chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise InstalledIdentityError(f"{label} changed while being read: {absolute}")
        after = os.fstat(descriptor)
        after_path = os.stat(leaf, dir_fd=current, follow_symlinks=False)
        if any(
            getattr(before, field) != getattr(after, field)
            or getattr(before, field) != getattr(after_path, field)
            for field in identity_fields
        ):
            raise InstalledIdentityError(f"{label} changed while being read: {absolute}")
        return (b"".join(chunks) if retain_bytes else None), digest.hexdigest(), after
    except InstalledIdentityError:
        raise
    except FileNotFoundError as exc:
        raise InstalledIdentityError(f"{label} is missing or changed: {absolute}") from exc
    except OSError as exc:
        raise InstalledIdentityError(f"could not safely read {label}: {absolute}: {exc}") from exc
    finally:
        if descriptor is not None:
            os.close(descriptor)
        for directory in reversed(directories):
            os.close(directory)


def _hash_regular_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:
    _, digest, metadata = _consume_regular_file(path, label, maximum_bytes, retain_bytes=False)
    return digest, metadata


def _read_regular_bytes(path: Path, label: str, maximum_bytes: int) -> tuple[bytes, os.stat_result]:
    """Read exact bytes from one stable, bounded regular-file inode."""
    raw, _, metadata = _consume_regular_file(path, label, maximum_bytes, retain_bytes=True)
    assert raw is not None
    return raw, metadata


def validate_bundle(
    data: Any,
    *,
    source_bytes: bytes,
    evidence_base: Path,
) -> InstalledIdentitySummary:
    if not source_bytes or len(source_bytes) > MAX_MANIFEST_BYTES:
        raise InstalledIdentityError(
            f"installed identity manifest must be between 1 and {MAX_MANIFEST_BYTES} bytes"
        )
    try:
        strict_json.loads_matching_object(
            source_bytes,
            data,
            label="installed identity manifest source bytes",
            maximum_bytes=MAX_MANIFEST_BYTES,
        )
    except strict_json.StrictJsonError as exc:
        raise InstalledIdentityError(str(exc)) from exc
    root = _mapping(data, "root")
    _require_exact_keys(
        root,
        {
            "schema_version",
            "captured_at_utc",
            "candidate",
            "device",
            "installed_package",
            "claims",
            "evidence_files",
        },
        "root",
    )
    if _integer(root["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise InstalledIdentityError(f"schema_version must be {SCHEMA_VERSION}")
    _parse_utc(root["captured_at_utc"], "captured_at_utc")

    candidate = _mapping(root["candidate"], "candidate")
    _require_exact_keys(
        candidate,
        {
            "repository",
            "branch",
            "commit_sha",
            "application_id",
            "version_code",
            "app_signing_certificate_sha256",
            "expected_installer_package",
        },
        "candidate",
    )
    if _string(candidate["repository"], "candidate.repository") != CANONICAL_REPOSITORY:
        raise InstalledIdentityError("candidate.repository is not canonical")
    if _string(candidate["branch"], "candidate.branch") != CANONICAL_BRANCH:
        raise InstalledIdentityError("candidate.branch must be main")
    candidate_sha = _canonical_hash(
        candidate["commit_sha"], "candidate.commit_sha", SHA40_RE, "lowercase 40-hex"
    )
    application_id = _string(candidate["application_id"], "candidate.application_id", maximum=255)
    if application_id != CANONICAL_APPLICATION_ID:
        raise InstalledIdentityError("candidate.application_id is not canonical")
    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)
    expected_cert = _canonical_hash(
        candidate["app_signing_certificate_sha256"],
        "candidate.app_signing_certificate_sha256",
        SHA256_RE,
        "lowercase 64-hex",
    )
    expected_installer = _string(
        candidate["expected_installer_package"],
        "candidate.expected_installer_package",
        maximum=255,
    )
    if expected_installer != PLAY_STORE_INSTALLER:
        raise InstalledIdentityError(
            f"candidate.expected_installer_package must be {PLAY_STORE_INSTALLER}"
        )

    device = _mapping(root["device"], "device")
    _require_exact_keys(
        device,
        {"serial_sha256", "manufacturer", "model", "device", "sdk", "build_fingerprint"},
        "device",
    )
    serial_digest = _canonical_hash(
        device["serial_sha256"], "device.serial_sha256", SHA256_RE, "lowercase 64-hex"
    )
    for key in ("manufacturer", "model", "device", "build_fingerprint"):
        _string(device[key], f"device.{key}", maximum=1024)
    _integer(device["sdk"], "device.sdk", minimum=21)

    installed = _mapping(root["installed_package"], "installed_package")
    _require_exact_keys(
        installed,
        {
            "application_id",
            "version_code",
            "version_name",
            "installer_package",
            "app_signing_certificate_sha256",
            "base_apk_sha256",
            "apk_set",
        },
        "installed_package",
    )
    if _string(installed["application_id"], "installed_package.application_id") != application_id:
        raise InstalledIdentityError("installed application_id does not match candidate")
    if _integer(installed["version_code"], "installed_package.version_code", minimum=1) != version_code:
        raise InstalledIdentityError("installed version_code does not match candidate")
    _string(installed["version_name"], "installed_package.version_name", maximum=255)
    installer = _string(installed["installer_package"], "installed_package.installer_package", maximum=255)
    if installer != expected_installer:
        raise InstalledIdentityError("installed package installer does not match expected Play Store installer")
    observed_cert = _canonical_hash(
        installed["app_signing_certificate_sha256"],
        "installed_package.app_signing_certificate_sha256",
        SHA256_RE,
        "lowercase 64-hex",
    )
    if observed_cert != expected_cert:
        raise InstalledIdentityError("installed app-signing certificate does not match candidate")
    base_apk_digest = _canonical_hash(
        installed["base_apk_sha256"],
        "installed_package.base_apk_sha256",
        SHA256_RE,
        "lowercase 64-hex",
    )

    apk_set = _sequence(installed["apk_set"], "installed_package.apk_set")
    if not apk_set:
        raise InstalledIdentityError("installed_package.apk_set must not be empty")
    names: set[str] = set()
    apk_observations: dict[str, tuple[str, int]] = {}
    seen_base = False
    for index, raw_apk in enumerate(apk_set):
        label = f"installed_package.apk_set[{index}]"
        apk = _mapping(raw_apk, label)
        _require_exact_keys(apk, {"name", "sha256", "size_bytes", "signing_certificate_sha256"}, label)
        name = _string(apk["name"], f"{label}.name", maximum=160)
        if not APK_NAME_RE.fullmatch(name) or name in names:
            raise InstalledIdentityError(f"{label}.name is invalid or duplicated")
        names.add(name)
        digest = _canonical_hash(
            apk["sha256"], f"{label}.sha256", SHA256_RE, "lowercase 64-hex"
        )
        size_bytes = _integer(apk["size_bytes"], f"{label}.size_bytes", minimum=1)
        apk_observations[name] = (digest, size_bytes)
        signer = _canonical_hash(
            apk["signing_certificate_sha256"],
            f"{label}.signing_certificate_sha256",
            SHA256_RE,
            "lowercase 64-hex",
        )
        if signer != observed_cert:
            raise InstalledIdentityError(f"{label} signer does not match installed app-signing certificate")
        if name == "base.apk":
            seen_base = True
            if digest != base_apk_digest:
                raise InstalledIdentityError("base_apk_sha256 does not match base.apk entry")
    if not seen_base:
        raise InstalledIdentityError("installed_package.apk_set must include base.apk")

    claims = _mapping(root["claims"], "claims")
    _require_exact_keys(
        claims,
        {"play_store_installer_observed", "specific_play_track_verified"},
        "claims",
    )
    if not _boolean(claims["play_store_installer_observed"], "claims.play_store_installer_observed"):
        raise InstalledIdentityError("claims.play_store_installer_observed must be true")
    if _boolean(claims["specific_play_track_verified"], "claims.specific_play_track_verified"):
        raise InstalledIdentityError(
            "claims.specific_play_track_verified must remain false; device package state cannot prove a Play track"
        )

    raw_files = _sequence(root["evidence_files"], "evidence_files")
    if not raw_files:
        raise InstalledIdentityError("evidence_files must not be empty")
    seen_paths: set[str] = set()
    seen_inodes: set[tuple[int, int]] = set()
    evidence_observations: dict[str, tuple[str, int]] = {}
    for index, raw_reference in enumerate(raw_files):
        label = f"evidence_files[{index}]"
        reference = _mapping(raw_reference, label)
        _require_exact_keys(reference, {"path", "sha256"}, label)
        relative = _safe_relative_path(reference["path"], f"{label}.path")
        if relative in seen_paths:
            raise InstalledIdentityError(f"evidence path is reused: {relative}")
        seen_paths.add(relative)
        expected = _canonical_hash(
            reference["sha256"], f"{label}.sha256", SHA256_RE, "lowercase 64-hex"
        )
        path = _resolve_inside(evidence_base, relative, f"{label}.path")
        actual, metadata = _hash_regular_file(path, "installed identity evidence", MAX_EVIDENCE_FILE_BYTES)
        if actual != expected:
            raise InstalledIdentityError(f"evidence digest mismatch: {relative}")
        evidence_observations[relative] = (actual, metadata.st_size)
        if metadata.st_ino:
            identity = (metadata.st_dev, metadata.st_ino)
            if identity in seen_inodes:
                raise InstalledIdentityError(f"evidence file is reused through a hard link: {relative}")
            seen_inodes.add(identity)

    for name, observation in apk_observations.items():
        evidence_path = f"apks/{name}"
        if evidence_observations.get(evidence_path) != observation:
            raise InstalledIdentityError(
                f"installed_package.apk_set entry {name} does not match pulled APK evidence {evidence_path}"
            )

    return InstalledIdentitySummary(
        candidate_sha=candidate_sha,
        application_id=application_id,
        version_code=version_code,
        app_signing_certificate_sha256=observed_cert,
        installer_package=installer,
        apk_count=len(apk_set),
        evidence_file_count=len(raw_files),
        manifest_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> InstalledIdentitySummary:
    path = path.expanduser()
    raw, _ = _read_regular_bytes(
        path, "installed identity manifest", MAX_MANIFEST_BYTES
    )
    try:
        data = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise InstalledIdentityError(str(exc)) from exc
    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    try:
        atomic_write_json(path, payload, "installed-identity summary")
    except StableFileError as exc:
        raise InstalledIdentityError(str(exc)) from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = load_and_validate(args.manifest)
    except InstalledIdentityError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        try:
            _write_json(args.summary_output, payload)
        except (OSError, InstalledIdentityError) as exc:
            print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
            return 1
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
