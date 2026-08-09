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
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

import strict_json

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
    if "\\" in text or text.startswith("~") or "\x00" in text:
        raise InstalledIdentityError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise InstalledIdentityError(f"{label} must be a safe relative path")
    return path.as_posix()


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    canonical = base.resolve()
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
    resolved = lexical.resolve()
    try:
        resolved.relative_to(canonical)
    except ValueError as exc:
        raise InstalledIdentityError(f"{label} resolves outside the evidence root") from exc
    return resolved


def _hash_regular_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise InstalledIdentityError(f"{label} is missing: {path}") from exc
    except OSError as exc:
        raise InstalledIdentityError(f"could not inspect {label}: {path}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise InstalledIdentityError(f"{label} must be a regular non-symlink file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise InstalledIdentityError(
            f"{label} must be between 1 and {maximum_bytes} bytes: {path}"
        )
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        after = path.lstat()
    except OSError as exc:
        raise InstalledIdentityError(f"could not read {label}: {path}: {exc}") from exc
    if (
        before.st_size != after.st_size
        or before.st_mtime_ns != after.st_mtime_ns
        or (before.st_ino and before.st_ino != after.st_ino)
    ):
        raise InstalledIdentityError(f"{label} changed while being hashed: {path}")
    return digest.hexdigest(), after


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
    candidate_sha = _string(candidate["commit_sha"], "candidate.commit_sha", maximum=40).lower()
    if not SHA40_RE.fullmatch(candidate_sha):
        raise InstalledIdentityError("candidate.commit_sha must be lowercase 40-hex")
    application_id = _string(candidate["application_id"], "candidate.application_id", maximum=255)
    if application_id != CANONICAL_APPLICATION_ID:
        raise InstalledIdentityError("candidate.application_id is not canonical")
    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)
    expected_cert = _string(
        candidate["app_signing_certificate_sha256"],
        "candidate.app_signing_certificate_sha256",
        maximum=64,
    ).lower()
    if not SHA256_RE.fullmatch(expected_cert):
        raise InstalledIdentityError(
            "candidate.app_signing_certificate_sha256 must be lowercase 64-hex"
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
    serial_digest = _string(device["serial_sha256"], "device.serial_sha256", maximum=64).lower()
    if not SHA256_RE.fullmatch(serial_digest):
        raise InstalledIdentityError("device.serial_sha256 must be lowercase 64-hex")
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
    observed_cert = _string(
        installed["app_signing_certificate_sha256"],
        "installed_package.app_signing_certificate_sha256",
        maximum=64,
    ).lower()
    if observed_cert != expected_cert:
        raise InstalledIdentityError("installed app-signing certificate does not match candidate")
    base_apk_digest = _string(installed["base_apk_sha256"], "installed_package.base_apk_sha256", maximum=64).lower()
    if not SHA256_RE.fullmatch(base_apk_digest):
        raise InstalledIdentityError("installed_package.base_apk_sha256 must be lowercase 64-hex")

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
        digest = _string(apk["sha256"], f"{label}.sha256", maximum=64).lower()
        if not SHA256_RE.fullmatch(digest):
            raise InstalledIdentityError(f"{label}.sha256 must be lowercase 64-hex")
        size_bytes = _integer(apk["size_bytes"], f"{label}.size_bytes", minimum=1)
        apk_observations[name] = (digest, size_bytes)
        signer = _string(
            apk["signing_certificate_sha256"],
            f"{label}.signing_certificate_sha256",
            maximum=64,
        ).lower()
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
        expected = _string(reference["sha256"], f"{label}.sha256", maximum=64).lower()
        if not SHA256_RE.fullmatch(expected):
            raise InstalledIdentityError(f"{label}.sha256 must be lowercase 64-hex")
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
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise InstalledIdentityError(f"installed identity manifest is missing: {path}") from exc
    except OSError as exc:
        raise InstalledIdentityError(f"could not inspect installed identity manifest: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise InstalledIdentityError("installed identity manifest must be a regular non-symlink file")
    try:
        raw = path.read_bytes()
        after = path.lstat()
    except OSError as exc:
        raise InstalledIdentityError(f"could not read installed identity manifest: {exc}") from exc
    if (
        len(raw) != before.st_size
        or before.st_size <= 0
        or before.st_size > MAX_MANIFEST_BYTES
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise InstalledIdentityError("installed identity manifest is invalid or changed while being read")
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
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            json.dump(payload, handle, indent=2, sort_keys=True, allow_nan=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


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
        _write_json(args.summary_output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
