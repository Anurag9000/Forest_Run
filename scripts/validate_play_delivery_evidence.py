#!/usr/bin/env python3
"""Validate candidate-bound Google Play internal-delivery evidence.

This schema records external Play Console/operator facts that cannot be inferred from
an installed APK alone. It revalidates the installed-identity matrix, requires the
internal-track/upload/install/update assertions explicitly, binds immutable evidence
files, and requires two accountable reviewers. It never contacts Play Console and
never manufactures a delivery claim.
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
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

import strict_json
import validate_installed_identity_matrix as installed_matrix

SCHEMA_VERSION = 1
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
MAX_EVIDENCE_FILE_BYTES = 2 * 1024 * 1024 * 1024
SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
RELEASE_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:/+-]{2,199}$")

REQUIRED_EVIDENCE_KINDS = frozenset(
    {
        "play_console_upload_record",
        "internal_track_release_record",
        "tester_eligibility_record",
        "install_receipt_record",
        "update_receipt_record",
    }
)


class PlayDeliveryError(ValueError):
    """Raised when Play delivery evidence is incomplete, stale, or unsafe."""


@dataclass(frozen=True)
class PlayDeliverySummary:
    candidate_sha: str
    artifact_sha256: str
    upload_certificate_sha256: str
    app_signing_certificate_sha256: str
    installed_identity_matrix_sha256: str
    track: str
    release_id: str
    evidence_file_count: int
    reviewer_count: int
    manifest_sha256: str

    def to_json(self) -> dict[str, object]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "artifact_sha256": self.artifact_sha256,
            "upload_certificate_sha256": self.upload_certificate_sha256,
            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,
            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,
            "track": self.track,
            "release_id": self.release_id,
            "evidence_file_count": self.evidence_file_count,
            "reviewer_count": self.reviewer_count,
            "manifest_sha256": self.manifest_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise PlayDeliveryError(f"{label} must be an object")
    return value


def _string(value: Any, label: str, *, maximum: int = 4096) -> str:
    if not isinstance(value, str):
        raise PlayDeliveryError(f"{label} must be a string")
    result = value.strip()
    if not result:
        raise PlayDeliveryError(f"{label} must not be blank")
    if len(result) > maximum:
        raise PlayDeliveryError(f"{label} exceeds the {maximum}-character limit")
    if any(ord(character) < 32 or ord(character) == 127 for character in result):
        raise PlayDeliveryError(f"{label} must not contain control characters")
    return result


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise PlayDeliveryError(f"{label} must be an integer")
    if value < minimum:
        raise PlayDeliveryError(f"{label} must be >= {minimum}")
    return value


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise PlayDeliveryError(f"{label} must be a boolean")
    return value


def _require_exact_keys(value: Mapping[str, Any], keys: Iterable[str], label: str) -> None:
    expected = set(keys)
    actual = set(value)
    missing = sorted(expected - actual)
    extras = sorted(actual - expected)
    if missing:
        raise PlayDeliveryError(f"{label} is missing: {', '.join(missing)}")
    if extras:
        raise PlayDeliveryError(f"{label} contains unrecognized keys: {', '.join(extras)}")


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label, maximum=64)
    if not text.endswith("Z"):
        raise PlayDeliveryError(f"{label} must use ISO-8601 UTC Z notation")
    try:
        return datetime.fromisoformat(text[:-1] + "+00:00").astimezone(timezone.utc)
    except ValueError as exc:
        raise PlayDeliveryError(f"{label} is not a valid UTC timestamp") from exc


def _normalized_name(value: str) -> str:
    return unicodedata.normalize("NFKC", value).casefold()


def _safe_relative_path(value: Any, label: str) -> str:
    text = _string(value, label, maximum=512)
    if "\\" in text or text.startswith("~") or "\x00" in text:
        raise PlayDeliveryError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise PlayDeliveryError(f"{label} must be a safe relative path")
    return path.as_posix()


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    canonical = base.resolve()
    lexical = canonical / relative
    try:
        parts = lexical.relative_to(canonical).parts
    except ValueError as exc:
        raise PlayDeliveryError(f"{label} escapes the evidence root") from exc
    current = canonical
    for part in parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            break
        except OSError as exc:
            raise PlayDeliveryError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise PlayDeliveryError(f"{label} must not traverse a symbolic link: {current}")
    resolved = lexical.resolve()
    try:
        resolved.relative_to(canonical)
    except ValueError as exc:
        raise PlayDeliveryError(f"{label} resolves outside the evidence root") from exc
    return resolved


def _hash_regular_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise PlayDeliveryError(f"{label} is missing: {path}") from exc
    except OSError as exc:
        raise PlayDeliveryError(f"could not inspect {label}: {path}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise PlayDeliveryError(f"{label} must be a regular non-symlink file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise PlayDeliveryError(f"{label} has an invalid size: {path}")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        after = path.lstat()
    except OSError as exc:
        raise PlayDeliveryError(f"could not read {label}: {path}: {exc}") from exc
    if (
        before.st_size != after.st_size
        or before.st_mtime_ns != after.st_mtime_ns
        or (before.st_ino and before.st_ino != after.st_ino)
    ):
        raise PlayDeliveryError(f"{label} changed while being hashed: {path}")
    return digest.hexdigest(), after


def _load_matrix(reference: Mapping[str, Any], base: Path) -> tuple[installed_matrix.InstalledIdentityMatrixSummary, str]:
    _require_exact_keys(reference, {"path", "sha256"}, "installed_identity_matrix")
    relative = _safe_relative_path(reference["path"], "installed_identity_matrix.path")
    expected = _string(reference["sha256"], "installed_identity_matrix.sha256", maximum=64).lower()
    if not SHA256_RE.fullmatch(expected):
        raise PlayDeliveryError("installed_identity_matrix.sha256 must be lowercase 64-hex")
    path = _resolve_inside(base, relative, "installed_identity_matrix.path")
    actual, _ = _hash_regular_file(path, "installed identity matrix", installed_matrix.MAX_MANIFEST_BYTES)
    if actual != expected:
        raise PlayDeliveryError("installed identity matrix digest mismatch")
    try:
        summary = installed_matrix.load_and_validate(path)
    except installed_matrix.InstalledIdentityMatrixError as exc:
        raise PlayDeliveryError(f"referenced installed identity matrix is invalid: {exc}") from exc
    return summary, actual


def validate_bundle(data: Any, *, source_bytes: bytes, evidence_base: Path) -> PlayDeliverySummary:
    if not source_bytes or len(source_bytes) > MAX_MANIFEST_BYTES:
        raise PlayDeliveryError("Play delivery manifest has an invalid size")
    root = _mapping(data, "root")
    _require_exact_keys(
        root,
        {
            "schema_version",
            "generated_at_utc",
            "candidate",
            "installed_identity_matrix",
            "delivery",
            "evidence",
            "final_review",
        },
        "root",
    )
    if _integer(root["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise PlayDeliveryError(f"schema_version must be {SCHEMA_VERSION}")
    generated_at = _parse_utc(root["generated_at_utc"], "generated_at_utc")
    matrix_summary, matrix_digest = _load_matrix(
        _mapping(root["installed_identity_matrix"], "installed_identity_matrix"),
        evidence_base,
    )

    candidate = _mapping(root["candidate"], "candidate")
    _require_exact_keys(
        candidate,
        {
            "repository",
            "branch",
            "application_id",
            "commit_sha",
            "version_code",
            "artifact_sha256",
            "upload_certificate_sha256",
            "app_signing_certificate_sha256",
        },
        "candidate",
    )
    expected_candidate = {
        "repository": "Anurag9000/Forest_Run",
        "branch": "main",
        "application_id": "com.anurag9000.forestrun",
        "commit_sha": matrix_summary.candidate_sha,
        "version_code": None,
        "artifact_sha256": matrix_summary.artifact_sha256,
        "upload_certificate_sha256": matrix_summary.upload_certificate_sha256,
        "app_signing_certificate_sha256": matrix_summary.app_signing_certificate_sha256,
    }
    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)
    expected_candidate["version_code"] = version_code
    for key in (
        "repository",
        "branch",
        "application_id",
        "commit_sha",
        "artifact_sha256",
        "upload_certificate_sha256",
        "app_signing_certificate_sha256",
    ):
        observed = candidate[key]
        if isinstance(expected_candidate[key], str) and key.endswith("sha256"):
            observed = _string(observed, f"candidate.{key}", maximum=64).lower()
        elif key == "commit_sha":
            observed = _string(observed, "candidate.commit_sha", maximum=40).lower()
        else:
            observed = _string(observed, f"candidate.{key}", maximum=255)
        if observed != expected_candidate[key]:
            raise PlayDeliveryError(f"candidate.{key} does not match installed identity matrix")
    if not SHA40_RE.fullmatch(matrix_summary.candidate_sha):
        raise PlayDeliveryError("matrix candidate SHA is invalid")

    # The matrix intentionally does not carry version code in its summary; read it
    # from its bound device manifest indirectly by requiring the delivery record's
    # release version to be positive and cross-checking the same value again in the
    # governance/device layer. This schema does not invent it.

    delivery = _mapping(root["delivery"], "delivery")
    _require_exact_keys(
        delivery,
        {
            "store",
            "track",
            "release_id",
            "bundle_uploaded",
            "internal_release_created",
            "tester_eligible",
            "tester_install_completed",
            "update_path_verified",
            "uploaded_at_utc",
            "release_created_at_utc",
            "install_verified_at_utc",
            "update_verified_at_utc",
        },
        "delivery",
    )
    if _string(delivery["store"], "delivery.store", maximum=64) != "google_play":
        raise PlayDeliveryError("delivery.store must be google_play")
    track = _string(delivery["track"], "delivery.track", maximum=64)
    if track != "internal":
        raise PlayDeliveryError("delivery.track must be internal")
    release_id = _string(delivery["release_id"], "delivery.release_id", maximum=200)
    if not RELEASE_ID_RE.fullmatch(release_id):
        raise PlayDeliveryError("delivery.release_id has an invalid format")
    for key in (
        "bundle_uploaded",
        "internal_release_created",
        "tester_eligible",
        "tester_install_completed",
        "update_path_verified",
    ):
        if not _boolean(delivery[key], f"delivery.{key}"):
            raise PlayDeliveryError(f"delivery.{key} must be true")
    timestamps = [
        _parse_utc(delivery["uploaded_at_utc"], "delivery.uploaded_at_utc"),
        _parse_utc(delivery["release_created_at_utc"], "delivery.release_created_at_utc"),
        _parse_utc(delivery["install_verified_at_utc"], "delivery.install_verified_at_utc"),
        _parse_utc(delivery["update_verified_at_utc"], "delivery.update_verified_at_utc"),
    ]
    if timestamps != sorted(timestamps):
        raise PlayDeliveryError("delivery timestamps must be monotonic from upload through update verification")
    if timestamps[-1] > generated_at:
        raise PlayDeliveryError("delivery.update_verified_at_utc must not be after generated_at_utc")

    evidence = _mapping(root["evidence"], "evidence")
    _require_exact_keys(evidence, REQUIRED_EVIDENCE_KINDS, "evidence")
    seen_paths: set[str] = set()
    seen_inodes: set[tuple[int, int]] = set()
    for kind in sorted(REQUIRED_EVIDENCE_KINDS):
        label = f"evidence.{kind}"
        reference = _mapping(evidence[kind], label)
        _require_exact_keys(reference, {"path", "sha256"}, label)
        relative = _safe_relative_path(reference["path"], f"{label}.path")
        if relative in seen_paths:
            raise PlayDeliveryError(f"Play delivery evidence path is reused: {relative}")
        seen_paths.add(relative)
        expected = _string(reference["sha256"], f"{label}.sha256", maximum=64).lower()
        if not SHA256_RE.fullmatch(expected):
            raise PlayDeliveryError(f"{label}.sha256 must be lowercase 64-hex")
        path = _resolve_inside(evidence_base, relative, f"{label}.path")
        actual, metadata = _hash_regular_file(path, "Play delivery evidence", MAX_EVIDENCE_FILE_BYTES)
        if actual != expected:
            raise PlayDeliveryError(f"Play delivery evidence digest mismatch: {relative}")
        if metadata.st_ino:
            identity_value = (metadata.st_dev, metadata.st_ino)
            if identity_value in seen_inodes:
                raise PlayDeliveryError(f"Play delivery evidence is reused through a hard link: {relative}")
            seen_inodes.add(identity_value)

    review = _mapping(root["final_review"], "final_review")
    _require_exact_keys(
        review,
        {"status", "release_operator", "independent_reviewer", "reviewed_at_utc", "notes"},
        "final_review",
    )
    if _string(review["status"], "final_review.status", maximum=32) != "approved":
        raise PlayDeliveryError("final_review.status must be approved")
    operator = _string(review["release_operator"], "final_review.release_operator", maximum=120)
    independent = _string(review["independent_reviewer"], "final_review.independent_reviewer", maximum=120)
    if _normalized_name(operator) == _normalized_name(independent):
        raise PlayDeliveryError("release operator and independent reviewer must be distinct")
    reviewed_at = _parse_utc(review["reviewed_at_utc"], "final_review.reviewed_at_utc")
    if reviewed_at < timestamps[-1]:
        raise PlayDeliveryError("final review must not precede update verification")
    if reviewed_at > generated_at:
        raise PlayDeliveryError("final review must not be after generated_at_utc")
    _string(review["notes"], "final_review.notes", maximum=5000)

    return PlayDeliverySummary(
        candidate_sha=matrix_summary.candidate_sha,
        artifact_sha256=matrix_summary.artifact_sha256,
        upload_certificate_sha256=matrix_summary.upload_certificate_sha256,
        app_signing_certificate_sha256=matrix_summary.app_signing_certificate_sha256,
        installed_identity_matrix_sha256=matrix_digest,
        track=track,
        release_id=release_id,
        evidence_file_count=len(evidence),
        reviewer_count=2,
        manifest_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> PlayDeliverySummary:
    path = path.expanduser()
    try:
        before = path.lstat()
        raw = path.read_bytes()
        after = path.lstat()
    except OSError as exc:
        raise PlayDeliveryError(f"could not read Play delivery manifest: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise PlayDeliveryError("Play delivery manifest must be a regular non-symlink file")
    if (
        len(raw) != before.st_size
        or before.st_size <= 0
        or before.st_size > MAX_MANIFEST_BYTES
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise PlayDeliveryError("Play delivery manifest is invalid or changed while being read")
    try:
        data = strict_json.loads(raw, label=str(path), maximum_bytes=MAX_MANIFEST_BYTES, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise PlayDeliveryError(str(exc)) from exc
    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False) as handle:
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
    except PlayDeliveryError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        _write_json(args.summary_output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
