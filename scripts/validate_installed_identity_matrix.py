#!/usr/bin/env python3
"""Validate one installed-package identity record for every physical acceptance session."""

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
from stable_file_reader import (
    StableFileError,
    atomic_write_json,
    checked_directory_path,
    consume_regular_file,
)
import validate_device_acceptance as device_acceptance
import validate_installed_candidate_identity as installed_identity

SCHEMA_VERSION = 1
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class InstalledIdentityMatrixError(ValueError):
    """Raised when installed identity coverage does not match physical acceptance."""


@dataclass(frozen=True)
class InstalledIdentityMatrixSummary:
    candidate_sha: str
    version_code: int
    artifact_sha256: str
    upload_certificate_sha256: str
    app_signing_certificate_sha256: str
    device_acceptance_sha256: str
    record_count: int
    physical_device_count: int
    manifest_sha256: str

    def to_json(self) -> dict[str, object]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "version_code": self.version_code,
            "artifact_sha256": self.artifact_sha256,
            "upload_certificate_sha256": self.upload_certificate_sha256,
            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,
            "device_acceptance_sha256": self.device_acceptance_sha256,
            "record_count": self.record_count,
            "physical_device_count": self.physical_device_count,
            "manifest_sha256": self.manifest_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise InstalledIdentityMatrixError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise InstalledIdentityMatrixError(f"{label} must be an array")
    return value


def _string(value: Any, label: str, *, maximum: int = 4096) -> str:
    if not isinstance(value, str):
        raise InstalledIdentityMatrixError(f"{label} must be a string")
    result = value.strip()
    if not result:
        raise InstalledIdentityMatrixError(f"{label} must not be blank")
    if len(result) > maximum:
        raise InstalledIdentityMatrixError(f"{label} exceeds the {maximum}-character limit")
    if any(ord(character) < 32 or ord(character) == 127 for character in result):
        raise InstalledIdentityMatrixError(f"{label} must not contain control characters")
    return result


def _canonical_hash(value: Any, label: str, pattern: re.Pattern[str], description: str) -> str:
    """Validate a digest's exact spelling without display-text trimming."""
    if not isinstance(value, str) or not pattern.fullmatch(value):
        raise InstalledIdentityMatrixError(f"{label} must be {description}")
    return value


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise InstalledIdentityMatrixError(f"{label} must be an integer")
    if value < minimum:
        raise InstalledIdentityMatrixError(f"{label} must be >= {minimum}")
    return value


def _require_exact_keys(value: Mapping[str, Any], keys: Iterable[str], label: str) -> None:
    expected = set(keys)
    actual = set(value)
    missing = sorted(expected - actual)
    extras = sorted(actual - expected)
    if missing:
        raise InstalledIdentityMatrixError(f"{label} is missing: {', '.join(missing)}")
    if extras:
        raise InstalledIdentityMatrixError(f"{label} contains unrecognized keys: {', '.join(extras)}")


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label, maximum=64)
    if not text.endswith("Z"):
        raise InstalledIdentityMatrixError(f"{label} must use ISO-8601 UTC Z notation")
    try:
        return datetime.fromisoformat(text[:-1] + "+00:00").astimezone(timezone.utc)
    except ValueError as exc:
        raise InstalledIdentityMatrixError(f"{label} is not a valid UTC timestamp") from exc


def _safe_relative(value: Any, label: str) -> str:
    text = _string(value, label, maximum=512)
    if value != text:
        raise InstalledIdentityMatrixError(
            f"{label} must not have surrounding whitespace"
        )
    try:
        text.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise InstalledIdentityMatrixError(
            f"{label} must contain valid Unicode text"
        ) from exc
    if "\\" in text or ":" in text or text.startswith("~") or "\x00" in text:
        raise InstalledIdentityMatrixError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise InstalledIdentityMatrixError(f"{label} must be a safe relative path")
    if path.as_posix() != text:
        raise InstalledIdentityMatrixError(f"{label} must be a normalized POSIX relative path")
    return path.as_posix()


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    try:
        canonical = checked_directory_path(base, f"{label} evidence root")
    except StableFileError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc
    relative_path = PurePosixPath(relative)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise InstalledIdentityMatrixError(f"{label} resolves outside the evidence root")
    lexical = canonical / relative
    try:
        parts = lexical.relative_to(canonical).parts
    except ValueError as exc:
        raise InstalledIdentityMatrixError(f"{label} escapes the evidence root") from exc
    current = canonical
    for part in parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            break
        except OSError as exc:
            raise InstalledIdentityMatrixError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise InstalledIdentityMatrixError(f"{label} must not traverse a symbolic link: {current}")
    return lexical


def _hash_regular(path: Path, label: str, maximum: int) -> tuple[str, os.stat_result]:
    try:
        _, digest, metadata = consume_regular_file(
            path, label, maximum, retain_bytes=False
        )
    except StableFileError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc
    return digest, metadata


def _read_regular_bytes(path: Path, label: str, maximum: int) -> tuple[bytes, os.stat_result]:
    try:
        raw, _, metadata = consume_regular_file(
            path, label, maximum, retain_bytes=True
        )
    except StableFileError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc
    assert raw is not None
    return raw, metadata


def _load_device_manifest(reference: Mapping[str, Any], base: Path) -> tuple[dict[str, Any], device_acceptance.ValidationSummary, str]:
    _require_exact_keys(reference, {"path", "sha256"}, "device_acceptance")
    relative = _safe_relative(reference["path"], "device_acceptance.path")
    expected = _canonical_hash(
        reference["sha256"], "device_acceptance.sha256", SHA256_RE, "lowercase 64-hex"
    )
    path = _resolve_inside(base, relative, "device_acceptance.path")
    raw, _ = _read_regular_bytes(
        path, "device acceptance manifest", device_acceptance.MAX_MANIFEST_BYTES
    )
    actual = hashlib.sha256(raw).hexdigest()
    if actual != expected:
        raise InstalledIdentityMatrixError("device acceptance manifest digest mismatch")
    try:
        data = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=device_acceptance.MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except (device_acceptance.EvidenceError, strict_json.StrictJsonError) as exc:
        raise InstalledIdentityMatrixError(f"referenced device acceptance is invalid: {exc}") from exc
    try:
        summary = device_acceptance.validate_bundle(
            data,
            source_bytes=raw,
            evidence_base=base,
        )
    except device_acceptance.EvidenceError as exc:
        raise InstalledIdentityMatrixError(f"referenced device acceptance is invalid: {exc}") from exc
    return dict(data), summary, actual


def validate_bundle(data: Any, *, source_bytes: bytes, evidence_base: Path) -> InstalledIdentityMatrixSummary:
    if not source_bytes or len(source_bytes) > MAX_MANIFEST_BYTES:
        raise InstalledIdentityMatrixError("installed identity matrix has an invalid size")
    try:
        strict_json.loads_matching_object(
            source_bytes,
            data,
            label="installed identity matrix source bytes",
            maximum_bytes=MAX_MANIFEST_BYTES,
        )
    except strict_json.StrictJsonError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc
    root = _mapping(data, "root")
    _require_exact_keys(
        root,
        {"schema_version", "generated_at_utc", "candidate", "device_acceptance", "records"},
        "root",
    )
    if _integer(root["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise InstalledIdentityMatrixError(f"schema_version must be {SCHEMA_VERSION}")
    generated_at = _parse_utc(root["generated_at_utc"], "generated_at_utc")

    device_data, device_summary, device_digest = _load_device_manifest(
        _mapping(root["device_acceptance"], "device_acceptance"), evidence_base
    )
    device_candidate = _mapping(device_data.get("candidate"), "device candidate")
    candidate = _mapping(root["candidate"], "candidate")
    _require_exact_keys(
        candidate,
        {
            "repository", "branch", "commit_sha", "application_id", "version_code",
            "artifact_sha256", "upload_certificate_sha256", "app_signing_certificate_sha256",
        },
        "candidate",
    )
    expected_candidate = {
        "repository": device_candidate.get("repository"),
        "branch": device_candidate.get("branch"),
        "commit_sha": device_summary.candidate_sha,
        "application_id": device_candidate.get("application_id"),
        "version_code": device_candidate.get("version_code"),
        "artifact_sha256": device_summary.artifact_sha256,
        "upload_certificate_sha256": device_summary.upload_certificate_sha256,
        "app_signing_certificate_sha256": device_summary.app_signing_certificate_sha256,
    }
    if dict(candidate) != expected_candidate:
        raise InstalledIdentityMatrixError("matrix candidate does not exactly match device acceptance")

    sessions_raw = _sequence(device_data.get("sessions"), "device sessions")
    sessions: dict[str, Mapping[str, Any]] = {}
    for index, raw_session in enumerate(sessions_raw):
        session = _mapping(raw_session, f"device sessions[{index}]")
        session_id = _string(session.get("session_id"), f"device sessions[{index}].session_id", maximum=80)
        sessions[session_id] = session

    records = _sequence(root["records"], "records")
    if len(records) != len(sessions):
        raise InstalledIdentityMatrixError("records must contain exactly one installed identity per physical session")
    seen_sessions: set[str] = set()
    seen_paths: set[str] = set()
    seen_inodes: set[tuple[int, int]] = set()
    serial_digests: set[str] = set()

    for index, raw_record in enumerate(records):
        label = f"records[{index}]"
        record = _mapping(raw_record, label)
        _require_exact_keys(record, {"session_id", "path", "sha256"}, label)
        session_id = _string(record["session_id"], f"{label}.session_id", maximum=80)
        if session_id in seen_sessions or session_id not in sessions:
            raise InstalledIdentityMatrixError(f"{label}.session_id is duplicated or unknown")
        seen_sessions.add(session_id)
        relative = _safe_relative(record["path"], f"{label}.path")
        if relative in seen_paths:
            raise InstalledIdentityMatrixError(f"installed identity path is reused: {relative}")
        seen_paths.add(relative)
        expected_digest = _canonical_hash(
            record["sha256"], f"{label}.sha256", SHA256_RE, "lowercase 64-hex"
        )
        path = _resolve_inside(evidence_base, relative, f"{label}.path")
        raw_record, metadata = _read_regular_bytes(
            path, "installed identity record", installed_identity.MAX_MANIFEST_BYTES
        )
        actual_digest = hashlib.sha256(raw_record).hexdigest()
        if actual_digest != expected_digest:
            raise InstalledIdentityMatrixError(f"installed identity digest mismatch: {relative}")
        if metadata.st_ino:
            inode = (metadata.st_dev, metadata.st_ino)
            if inode in seen_inodes:
                raise InstalledIdentityMatrixError(f"installed identity file is reused through a hard link: {relative}")
            seen_inodes.add(inode)
        try:
            record_data = strict_json.loads(
                raw_record,
                label=str(path),
                maximum_bytes=installed_identity.MAX_MANIFEST_BYTES,
                require_object=True,
            )
            summary = installed_identity.validate_bundle(
                record_data,
                source_bytes=raw_record,
                evidence_base=path.parent,
            )
        except (installed_identity.InstalledIdentityError, strict_json.StrictJsonError) as exc:
            raise InstalledIdentityMatrixError(f"invalid installed identity for {session_id}: {exc}") from exc
        if summary.candidate_sha != device_summary.candidate_sha:
            raise InstalledIdentityMatrixError(f"installed identity {session_id} candidate does not match")
        if summary.version_code != device_candidate.get("version_code"):
            raise InstalledIdentityMatrixError(f"installed identity {session_id} version does not match")
        if summary.app_signing_certificate_sha256 != device_summary.app_signing_certificate_sha256:
            raise InstalledIdentityMatrixError(f"installed identity {session_id} app-signing certificate does not match")

        session = sessions[session_id]
        session_device = _mapping(session.get("device"), f"session {session_id}.device")
        observed_device = _mapping(record_data.get("device"), f"installed identity {session_id}.device")
        for key in ("manufacturer", "model", "build_fingerprint", "sdk"):
            if observed_device.get(key) != session_device.get(key):
                raise InstalledIdentityMatrixError(
                    f"installed identity {session_id} device.{key} does not match physical session"
                )
        serial_digest = _canonical_hash(
            observed_device.get("serial_sha256"),
            f"installed identity {session_id}.device.serial_sha256",
            SHA256_RE,
            "lowercase 64-hex",
        )
        if serial_digest in serial_digests:
            raise InstalledIdentityMatrixError("physical sessions reuse the same installed-device serial identity")
        serial_digests.add(serial_digest)
        captured_at = _parse_utc(record_data.get("captured_at_utc"), f"installed identity {session_id}.captured_at_utc")
        session_started = _parse_utc(session.get("started_at_utc"), f"session {session_id}.started_at_utc")
        session_completed = _parse_utc(session.get("completed_at_utc"), f"session {session_id}.completed_at_utc")
        if captured_at > generated_at:
            raise InstalledIdentityMatrixError(f"installed identity {session_id} was captured after matrix generation")
        # The identity can be captured immediately before or after gameplay, but it
        # must remain close enough to be meaningful for that physical session.
        if abs((captured_at - session_started).total_seconds()) > 24 * 60 * 60 and abs((captured_at - session_completed).total_seconds()) > 24 * 60 * 60:
            raise InstalledIdentityMatrixError(
                f"installed identity {session_id} is more than 24 hours from its physical session"
            )

    if seen_sessions != set(sessions):
        raise InstalledIdentityMatrixError("not every physical session has installed identity evidence")

    return InstalledIdentityMatrixSummary(
        candidate_sha=device_summary.candidate_sha,
        version_code=_integer(device_candidate.get("version_code"), "device candidate.version_code", minimum=1),
        artifact_sha256=device_summary.artifact_sha256,
        upload_certificate_sha256=device_summary.upload_certificate_sha256,
        app_signing_certificate_sha256=device_summary.app_signing_certificate_sha256,
        device_acceptance_sha256=device_digest,
        record_count=len(records),
        physical_device_count=len(serial_digests),
        manifest_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> InstalledIdentityMatrixSummary:
    path = path.expanduser()
    raw, _ = _read_regular_bytes(
        path, "installed identity matrix", MAX_MANIFEST_BYTES
    )
    try:
        data = strict_json.loads(raw, label=str(path), maximum_bytes=MAX_MANIFEST_BYTES, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc
    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    try:
        atomic_write_json(path, payload, "installed-identity-matrix summary")
    except StableFileError as exc:
        raise InstalledIdentityMatrixError(str(exc)) from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = load_and_validate(args.manifest)
    except InstalledIdentityMatrixError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        try:
            _write_json(args.summary_output, payload)
        except (OSError, InstalledIdentityMatrixError) as exc:
            print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
            return 1
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
