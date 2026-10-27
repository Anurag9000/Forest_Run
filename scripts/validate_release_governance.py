#!/usr/bin/env python3
"""Fail-closed validator for candidate-bound Forest Run release governance.

This validator proves identity, completeness, reviewer separation, file integrity,
and cross-binding to already-valid device/human acceptance. It deliberately does
not decide legal sufficiency, store-policy correctness, licence compatibility,
art quality, or security posture; accountable people must enter those decisions.
"""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import os
import re
import stat
import sys
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence
from urllib.parse import urlsplit

import strict_json
from stable_file_reader import (
    StableFileError,
    atomic_write_json,
    checked_directory_path,
    consume_regular_file,
)
import validate_device_acceptance as device_acceptance
import validate_human_acceptance as human_acceptance
import validate_installed_identity_matrix as installed_matrix
import validate_play_delivery_evidence as play_delivery

SCHEMA_VERSION = 2
MAX_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_EVIDENCE_FILE_BYTES = 2 * 1024 * 1024 * 1024
MAX_RELEASE_NOTES_BYTES = 1024 * 1024
SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

DECISION_DOMAINS = frozenset(
    {
        "security_disclosure",
        "software_licensing",
        "creative_asset_licensing",
        "dependency_licensing",
        "dependency_vulnerability",
        "dependency_verification",
        "privacy_policy",
        "data_safety",
        "content_rating",
        "target_audience",
        "store_policy",
        "visual_artwork_animation",
        "store_screenshots_graphics",
        "audio_presentation",
        "haptic_presentation",
        "reduced_motion_presentation",
        "accessibility_presentation",
        "orientation_policy",
        "signed_artifact_provenance",
        "release_notes",
    }
)

REQUIRED_EVIDENCE_KINDS = frozenset(
    {
        "artifact_verification",
        "resolved_sbom",
        "dependency_license_report",
        "dependency_vulnerability_report",
        "dependency_verification_report",
        "installed_identity_matrix",
        "play_delivery_record",
        "asset_provenance",
        "security_reporting_record",
        "license_decision_record",
        "third_party_notices",
        "privacy_policy_snapshot",
        "data_safety_record",
        "content_rating_record",
        "target_audience_record",
        "store_policy_review",
        "visual_approval",
        "screenshot_graphics_approval",
        "audio_haptic_approval",
        "accessibility_approval",
        "signed_artifact_provenance",
        "release_notes",
        "changelog",
        "store_whats_new",
    }
)


class GovernanceError(ValueError):
    """Raised when release governance evidence is incomplete, stale, or unsafe."""


@dataclass(frozen=True)
class GovernanceSummary:
    candidate_sha: str
    artifact_sha256: str
    upload_certificate_sha256: str
    app_signing_certificate_sha256: str
    device_acceptance_sha256: str
    human_acceptance_sha256: str
    installed_identity_matrix_sha256: str
    play_delivery_sha256: str
    decision_count: int
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
            "device_acceptance_sha256": self.device_acceptance_sha256,
            "human_acceptance_sha256": self.human_acceptance_sha256,
            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,
            "play_delivery_sha256": self.play_delivery_sha256,
            "decision_count": self.decision_count,
            "evidence_file_count": self.evidence_file_count,
            "reviewer_count": self.reviewer_count,
            "manifest_sha256": self.manifest_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise GovernanceError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise GovernanceError(f"{label} must be an array")
    return value


def _string(value: Any, label: str, *, maximum: int = 4096) -> str:
    if not isinstance(value, str):
        raise GovernanceError(f"{label} must be a string")
    result = value.strip()
    if not result:
        raise GovernanceError(f"{label} must not be blank")
    if len(result) > maximum:
        raise GovernanceError(f"{label} exceeds the {maximum}-character limit")
    if any(ord(character) < 32 or ord(character) == 127 for character in result):
        raise GovernanceError(f"{label} must not contain control characters")
    return result


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise GovernanceError(f"{label} must be an integer")
    if value < minimum:
        raise GovernanceError(f"{label} must be >= {minimum}")
    return value


def _bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise GovernanceError(f"{label} must be a boolean")
    return value


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label, maximum=64)
    if not text.endswith("Z"):
        raise GovernanceError(f"{label} must use ISO-8601 UTC Z notation")
    try:
        parsed = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as exc:
        raise GovernanceError(f"{label} is not a valid ISO-8601 timestamp") from exc
    return parsed.astimezone(timezone.utc)


def _safe_relative_path(value: Any, label: str) -> str:
    text = _string(value, label, maximum=512)
    if value != text:
        raise GovernanceError(f"{label} must not have surrounding whitespace")
    try:
        text.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise GovernanceError(f"{label} must contain valid Unicode text") from exc
    if "\\" in text or ":" in text or text.startswith("~"):
        raise GovernanceError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise GovernanceError(f"{label} must be a safe relative path")
    if path.as_posix() != text:
        raise GovernanceError(f"{label} must be a normalized POSIX relative path")
    return path.as_posix()


def _require_exact_keys(value: Mapping[str, Any], keys: Iterable[str], label: str) -> None:
    expected = set(keys)
    actual = set(value)
    missing = sorted(expected - actual)
    extras = sorted(actual - expected)
    if missing:
        raise GovernanceError(f"{label} is missing: {', '.join(missing)}")
    if extras:
        raise GovernanceError(f"{label} contains unrecognized keys: {', '.join(extras)}")


def _normalized_name(value: str) -> str:
    return unicodedata.normalize("NFKC", value).casefold()


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    try:
        canonical = checked_directory_path(base, f"{label} evidence root")
    except StableFileError as exc:
        raise GovernanceError(str(exc)) from exc
    relative_path = PurePosixPath(relative)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise GovernanceError(f"{label} resolves outside the governance evidence root")
    lexical = canonical / relative
    try:
        path_parts = lexical.relative_to(canonical).parts
    except ValueError as exc:
        raise GovernanceError(f"{label} escapes the governance evidence root") from exc

    current = canonical
    for part in path_parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            break
        except OSError as exc:
            raise GovernanceError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise GovernanceError(
                f"{label} must not traverse a symbolic link: {current}"
            )

    return lexical


def _hash_regular_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:
    try:
        _, digest, metadata = consume_regular_file(
            path, label, maximum_bytes, retain_bytes=False
        )
    except StableFileError as exc:
        raise GovernanceError(str(exc)) from exc
    return digest, metadata


def _read_regular_bytes(path: Path, label: str, maximum_bytes: int) -> tuple[bytes, os.stat_result]:
    try:
        raw, _, metadata = consume_regular_file(
            path, label, maximum_bytes, retain_bytes=True
        )
    except StableFileError as exc:
        raise GovernanceError(str(exc)) from exc
    assert raw is not None
    return raw, metadata


def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, int, str]:
    _require_exact_keys(
        candidate,
        {
            "repository",
            "branch",
            "application_id",
            "commit_sha",
            "version_code",
            "version_name",
            "artifact_sha256",
            "upload_certificate_sha256",
            "app_signing_certificate_sha256",
        },
        "candidate",
    )
    if _string(candidate["repository"], "candidate.repository") != device_acceptance.CANONICAL_REPOSITORY:
        raise GovernanceError("candidate.repository is not canonical")
    if _string(candidate["branch"], "candidate.branch") != device_acceptance.CANONICAL_BRANCH:
        raise GovernanceError("candidate.branch must be main")
    if _string(candidate["application_id"], "candidate.application_id") != device_acceptance.CANONICAL_APPLICATION_ID:
        raise GovernanceError("candidate.application_id is not canonical")
    sha = _canonical_digest(candidate["commit_sha"], "candidate.commit_sha", SHA40_RE, 40)
    artifact = _canonical_digest(candidate["artifact_sha256"], "candidate.artifact_sha256", SHA256_RE, 64)
    upload_certificate = _canonical_digest(
        candidate["upload_certificate_sha256"], "candidate.upload_certificate_sha256", SHA256_RE, 64
    )
    app_signing_certificate = _canonical_digest(
        candidate["app_signing_certificate_sha256"], "candidate.app_signing_certificate_sha256", SHA256_RE, 64
    )
    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)
    version_name = _string(candidate["version_name"], "candidate.version_name", maximum=120)
    return sha, artifact, upload_certificate, app_signing_certificate, version_code, version_name


def _canonical_digest(raw: Any, label: str, pattern: re.Pattern[str], length: int) -> str:
    if not isinstance(raw, str) or raw != raw.strip():
        raise GovernanceError(f"{label} must be canonical lowercase {length}-hex")
    value = _string(raw, label, maximum=length)
    if not pattern.fullmatch(value):
        raise GovernanceError(f"{label} must be lowercase {length}-hex")
    return value


def _validate_file_reference(
    raw: Any,
    *,
    label: str,
    base: Path,
    seen_paths: dict[str, tuple[str, tuple[int, int] | None]],
) -> tuple[str, str, Path]:
    reference = _mapping(raw, label)
    _require_exact_keys(reference, {"path", "sha256"}, label)
    relative = _safe_relative_path(reference["path"], f"{label}.path")
    expected = _canonical_digest(reference["sha256"], f"{label}.sha256", SHA256_RE, 64)
    if relative in seen_paths:
        raise GovernanceError(f"governance evidence path is reused: {relative}")
    path = _resolve_inside(base, relative, f"{label}.path")
    actual, stat_result = _hash_regular_file(path, label, MAX_EVIDENCE_FILE_BYTES)
    if actual != expected:
        raise GovernanceError(f"governance evidence digest mismatch: {relative}")
    identity = (stat_result.st_dev, stat_result.st_ino) if stat_result.st_ino else None
    if identity is not None and any(existing[1] == identity for existing in seen_paths.values()):
        raise GovernanceError(f"governance evidence file is reused through a hard link: {relative}")
    seen_paths[relative] = (actual, identity)
    return relative, actual, path


def _load_device_reference(
    raw: Any,
    *,
    base: Path,
    candidate_sha: str,
    artifact_sha: str,
    upload_certificate_sha: str,
    app_signing_certificate_sha: str,
    version_code: int,
) -> str:
    reference = _mapping(raw, "device_acceptance")
    _require_exact_keys(reference, {"path", "sha256"}, "device_acceptance")
    relative = _safe_relative_path(reference["path"], "device_acceptance.path")
    expected = _canonical_digest(reference["sha256"], "device_acceptance.sha256", SHA256_RE, 64)
    path = _resolve_inside(base, relative, "device_acceptance.path")
    raw_bytes, _ = _read_regular_bytes(
        path, "device acceptance manifest", device_acceptance.MAX_MANIFEST_BYTES
    )
    actual = hashlib.sha256(raw_bytes).hexdigest()
    if actual != expected:
        raise GovernanceError("device acceptance manifest digest mismatch")
    try:
        data = strict_json.loads(raw_bytes, label=str(path), maximum_bytes=MAX_MANIFEST_BYTES, require_object=True)
        summary = device_acceptance.validate_bundle(data, source_bytes=raw_bytes, evidence_base=path.parent)
    except (OSError, strict_json.StrictJsonError, device_acceptance.EvidenceError) as exc:
        raise GovernanceError(f"referenced device acceptance is invalid: {exc}") from exc
    device_candidate = _mapping(data.get("candidate"), "device candidate")
    if summary.candidate_sha != candidate_sha or summary.artifact_sha256 != artifact_sha:
        raise GovernanceError("device acceptance candidate does not match governance candidate")
    if summary.upload_certificate_sha256 != upload_certificate_sha:
        raise GovernanceError("device acceptance upload certificate does not match governance candidate")
    if summary.app_signing_certificate_sha256 != app_signing_certificate_sha:
        raise GovernanceError("device acceptance app-signing certificate does not match governance candidate")
    if _integer(device_candidate.get("version_code"), "device candidate.version_code", minimum=1) != version_code:
        raise GovernanceError("device acceptance version does not match governance candidate")
    return actual


def _load_human_reference(
    raw: Any,
    *,
    base: Path,
    candidate_sha: str,
    artifact_sha: str,
    upload_certificate_sha: str,
    app_signing_certificate_sha: str,
    version_code: int,
    device_digest: str,
) -> str:
    reference = _mapping(raw, "human_acceptance")
    _require_exact_keys(reference, {"path", "sha256"}, "human_acceptance")
    relative = _safe_relative_path(reference["path"], "human_acceptance.path")
    expected = _canonical_digest(reference["sha256"], "human_acceptance.sha256", SHA256_RE, 64)
    path = _resolve_inside(base, relative, "human_acceptance.path")
    raw_bytes, _ = _read_regular_bytes(
        path, "human acceptance manifest", human_acceptance.MAX_MANIFEST_BYTES
    )
    actual = hashlib.sha256(raw_bytes).hexdigest()
    if actual != expected:
        raise GovernanceError("human acceptance manifest digest mismatch")
    try:
        data = strict_json.loads(
            raw_bytes,
            label=str(path),
            maximum_bytes=human_acceptance.MAX_MANIFEST_BYTES,
            require_object=True,
        )
        summary = human_acceptance.validate_bundle(
            data,
            source_bytes=raw_bytes,
            evidence_base=path.parent,
        )
    except (human_acceptance.HumanAcceptanceError, strict_json.StrictJsonError) as exc:
        raise GovernanceError(f"referenced human acceptance is invalid: {exc}") from exc
    if summary.candidate_sha != candidate_sha or summary.artifact_sha256 != artifact_sha:
        raise GovernanceError("human acceptance candidate does not match governance candidate")
    if summary.device_acceptance_sha256 != device_digest:
        raise GovernanceError("human acceptance references a different device acceptance manifest")
    human_candidate = _mapping(data.get("candidate"), "human candidate")
    if summary.upload_certificate_sha256 != upload_certificate_sha:
        raise GovernanceError("human acceptance upload certificate does not match governance candidate")
    if summary.app_signing_certificate_sha256 != app_signing_certificate_sha:
        raise GovernanceError("human acceptance app-signing certificate does not match governance candidate")
    if _integer(human_candidate.get("version_code"), "human candidate.version_code", minimum=1) != version_code:
        raise GovernanceError("human acceptance version does not match governance candidate")
    return actual


def _validate_https_url(value: Any, label: str) -> str:
    text = _string(value, label, maximum=2048)
    try:
        parts = urlsplit(text)
        host = parts.hostname
        _ = parts.port  # Accessing this property validates numeric/range syntax.
    except ValueError as exc:
        raise GovernanceError(f"{label} must be a valid HTTPS URL") from exc
    if parts.scheme.lower() != "https" or not host or parts.username or parts.password:
        raise GovernanceError(f"{label} must be a public HTTPS URL without embedded credentials")
    if parts.fragment:
        raise GovernanceError(f"{label} must not contain a URL fragment")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        try:
            normalized_host = host.rstrip(".").encode("idna").decode("ascii").lower()
        except UnicodeError as exc:
            raise GovernanceError(f"{label} must use a valid public DNS hostname") from exc
        labels = normalized_host.split(".")
        if (
            len(labels) < 2
            or len(normalized_host) > 253
            or any(
                not label
                or len(label) > 63
                or re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label) is None
                for label in labels
            )
            or normalized_host == "localhost"
            or normalized_host.endswith((".localhost", ".local", ".internal"))
        ):
            raise GovernanceError(f"{label} must use a public DNS hostname")
    else:
        if not address.is_global:
            raise GovernanceError(f"{label} must not target a private or reserved IP address")
    return text


def validate_bundle(
    data: Any,
    *,
    source_bytes: bytes,
    evidence_base: Path,
) -> GovernanceSummary:
    if not source_bytes or len(source_bytes) > MAX_MANIFEST_BYTES:
        raise GovernanceError(
            f"governance manifest must be between 1 and {MAX_MANIFEST_BYTES} bytes"
        )
    try:
        strict_json.loads_matching_object(
            source_bytes,
            data,
            label="governance manifest source bytes",
            maximum_bytes=MAX_MANIFEST_BYTES,
        )
    except strict_json.StrictJsonError as exc:
        raise GovernanceError(str(exc)) from exc
    root = _mapping(data, "root")
    _require_exact_keys(
        root,
        {
            "schema_version",
            "generated_at_utc",
            "candidate",
            "device_acceptance",
            "human_acceptance",
            "privacy_policy_url",
            "private_vulnerability_reporting_enabled",
            "decisions",
            "evidence",
            "final_decision",
        },
        "root",
    )
    if _integer(root["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise GovernanceError(f"schema_version must be {SCHEMA_VERSION}")
    generated_at = _parse_utc(root["generated_at_utc"], "generated_at_utc")
    (
        candidate_sha, artifact_sha, upload_certificate_sha,
        app_signing_certificate_sha, version_code, version_name,
    ) = _validate_candidate(
        _mapping(root["candidate"], "candidate")
    )
    _validate_https_url(root["privacy_policy_url"], "privacy_policy_url")
    if not _bool(
        root["private_vulnerability_reporting_enabled"],
        "private_vulnerability_reporting_enabled",
    ):
        raise GovernanceError("private_vulnerability_reporting_enabled must be true")

    device_digest = _load_device_reference(
        root["device_acceptance"],
        base=evidence_base,
        candidate_sha=candidate_sha,
        artifact_sha=artifact_sha,
        upload_certificate_sha=upload_certificate_sha,
        app_signing_certificate_sha=app_signing_certificate_sha,
        version_code=version_code,
    )
    human_digest = _load_human_reference(
        root["human_acceptance"],
        base=evidence_base,
        candidate_sha=candidate_sha,
        artifact_sha=artifact_sha,
        upload_certificate_sha=upload_certificate_sha,
        app_signing_certificate_sha=app_signing_certificate_sha,
        version_code=version_code,
        device_digest=device_digest,
    )

    evidence = _mapping(root["evidence"], "evidence")
    _require_exact_keys(evidence, REQUIRED_EVIDENCE_KINDS, "evidence")
    seen_paths: dict[str, tuple[str, tuple[int, int] | None]] = {}
    evidence_results: dict[str, tuple[str, str, Path]] = {}
    for kind in sorted(REQUIRED_EVIDENCE_KINDS):
        evidence_results[kind] = _validate_file_reference(
            evidence[kind],
            label=f"evidence.{kind}",
            base=evidence_base,
            seen_paths=seen_paths,
        )

    _, installed_matrix_digest, installed_matrix_path = evidence_results["installed_identity_matrix"]
    try:
        matrix_bytes, _ = _read_regular_bytes(
            installed_matrix_path,
            "installed identity matrix",
            installed_matrix.MAX_MANIFEST_BYTES,
        )
        if hashlib.sha256(matrix_bytes).hexdigest() != installed_matrix_digest:
            raise GovernanceError("installed identity matrix changed after evidence hashing")
        matrix_data = strict_json.loads(
            matrix_bytes,
            label=str(installed_matrix_path),
            maximum_bytes=installed_matrix.MAX_MANIFEST_BYTES,
            require_object=True,
        )
        installed_matrix_summary = installed_matrix.validate_bundle(
            matrix_data,
            source_bytes=matrix_bytes,
            evidence_base=installed_matrix_path.parent,
        )
    except (installed_matrix.InstalledIdentityMatrixError, strict_json.StrictJsonError) as exc:
        raise GovernanceError(f"installed identity matrix is invalid: {exc}") from exc
    if installed_matrix_summary.candidate_sha != candidate_sha:
        raise GovernanceError("installed identity matrix candidate does not match governance candidate")
    if installed_matrix_summary.artifact_sha256 != artifact_sha:
        raise GovernanceError("installed identity matrix artifact does not match governance candidate")
    if installed_matrix_summary.upload_certificate_sha256 != upload_certificate_sha:
        raise GovernanceError("installed identity matrix upload certificate does not match governance candidate")
    if installed_matrix_summary.app_signing_certificate_sha256 != app_signing_certificate_sha:
        raise GovernanceError("installed identity matrix app-signing certificate does not match governance candidate")
    if installed_matrix_summary.device_acceptance_sha256 != device_digest:
        raise GovernanceError("installed identity matrix references a different device acceptance manifest")

    _, play_delivery_digest, play_delivery_path = evidence_results["play_delivery_record"]
    try:
        play_bytes, _ = _read_regular_bytes(
            play_delivery_path,
            "Play delivery evidence",
            play_delivery.MAX_MANIFEST_BYTES,
        )
        if hashlib.sha256(play_bytes).hexdigest() != play_delivery_digest:
            raise GovernanceError("Play delivery evidence changed after evidence hashing")
        play_data = strict_json.loads(
            play_bytes,
            label=str(play_delivery_path),
            maximum_bytes=play_delivery.MAX_MANIFEST_BYTES,
            require_object=True,
        )
        play_delivery_summary = play_delivery.validate_bundle(
            play_data,
            source_bytes=play_bytes,
            evidence_base=play_delivery_path.parent,
        )
    except (play_delivery.PlayDeliveryError, strict_json.StrictJsonError) as exc:
        raise GovernanceError(f"Play delivery evidence is invalid: {exc}") from exc
    if play_delivery_summary.candidate_sha != candidate_sha:
        raise GovernanceError("Play delivery candidate does not match governance candidate")
    if play_delivery_summary.artifact_sha256 != artifact_sha:
        raise GovernanceError("Play delivery artifact does not match governance candidate")
    if play_delivery_summary.upload_certificate_sha256 != upload_certificate_sha:
        raise GovernanceError("Play delivery upload certificate does not match governance candidate")
    if play_delivery_summary.app_signing_certificate_sha256 != app_signing_certificate_sha:
        raise GovernanceError("Play delivery app-signing certificate does not match governance candidate")
    if play_delivery_summary.installed_identity_matrix_sha256 != installed_matrix_digest:
        raise GovernanceError("Play delivery references a different installed identity matrix")

    decisions = _mapping(root["decisions"], "decisions")
    _require_exact_keys(decisions, DECISION_DOMAINS, "decisions")
    decision_reviewers: dict[str, str] = {}
    latest_decision_time = datetime.min.replace(tzinfo=timezone.utc)
    for domain in sorted(DECISION_DOMAINS):
        label = f"decisions.{domain}"
        decision = _mapping(decisions[domain], label)
        _require_exact_keys(
            decision,
            {"status", "reviewer", "reviewed_at_utc", "notes"},
            label,
        )
        if _string(decision["status"], f"{label}.status", maximum=32) != "approved":
            raise GovernanceError(f"{label}.status must be approved")
        reviewer = _string(decision["reviewer"], f"{label}.reviewer", maximum=120)
        decision_reviewers.setdefault(_normalized_name(reviewer), reviewer)
        reviewed_at = _parse_utc(decision["reviewed_at_utc"], f"{label}.reviewed_at_utc")
        if reviewed_at > generated_at:
            raise GovernanceError(f"{label}.reviewed_at_utc must not be after generated_at_utc")
        latest_decision_time = max(latest_decision_time, reviewed_at)
        _string(decision["notes"], f"{label}.notes", maximum=5000)

    release_notes_reference = _mapping(evidence["release_notes"], "evidence.release_notes")
    release_notes_path = _resolve_inside(
        evidence_base,
        _safe_relative_path(release_notes_reference["path"], "evidence.release_notes.path"),
        "evidence.release_notes.path",
    )
    try:
        release_notes_bytes, _ = _read_regular_bytes(
            release_notes_path, "release notes", MAX_RELEASE_NOTES_BYTES
        )
        expected_notes_digest = evidence_results["release_notes"][1]
        if hashlib.sha256(release_notes_bytes).hexdigest() != expected_notes_digest:
            raise GovernanceError("release notes changed after evidence hashing")
        release_notes_text = release_notes_bytes.decode("utf-8")
    except (GovernanceError, UnicodeDecodeError) as exc:
        raise GovernanceError(f"release notes must be readable UTF-8 text: {exc}") from exc
    for required_literal, label in (
        (candidate_sha, "candidate commit SHA"),
        (artifact_sha, "artifact SHA-256"),
        (upload_certificate_sha, "upload certificate SHA-256"),
        (app_signing_certificate_sha, "app-signing certificate SHA-256"),
        (str(version_code), "version code"),
        (version_name, "version name"),
    ):
        if required_literal not in release_notes_text:
            raise GovernanceError(f"release notes do not contain exact {label}")

    final_decision = _mapping(root["final_decision"], "final_decision")
    _require_exact_keys(
        final_decision,
        {"status", "release_owner", "independent_reviewer", "reviewed_at_utc", "notes"},
        "final_decision",
    )
    if _string(final_decision["status"], "final_decision.status", maximum=32) != "approved":
        raise GovernanceError("final_decision.status must be approved")
    owner = _string(final_decision["release_owner"], "final_decision.release_owner", maximum=120)
    independent = _string(
        final_decision["independent_reviewer"],
        "final_decision.independent_reviewer",
        maximum=120,
    )
    if _normalized_name(owner) == _normalized_name(independent):
        raise GovernanceError("release owner and independent reviewer must be distinct")
    if _normalized_name(owner) not in decision_reviewers:
        raise GovernanceError("release owner must own at least one governance decision")
    if _normalized_name(independent) not in decision_reviewers:
        raise GovernanceError("independent reviewer must own at least one governance decision")
    final_time = _parse_utc(final_decision["reviewed_at_utc"], "final_decision.reviewed_at_utc")
    if final_time > generated_at:
        raise GovernanceError("final_decision.reviewed_at_utc must not be after generated_at_utc")
    if final_time < latest_decision_time:
        raise GovernanceError("final decision must not precede a domain decision")
    _string(final_decision["notes"], "final_decision.notes", maximum=5000)

    return GovernanceSummary(
        candidate_sha=candidate_sha,
        artifact_sha256=artifact_sha,
        upload_certificate_sha256=upload_certificate_sha,
        app_signing_certificate_sha256=app_signing_certificate_sha,
        device_acceptance_sha256=device_digest,
        human_acceptance_sha256=human_digest,
        installed_identity_matrix_sha256=installed_matrix_digest,
        play_delivery_sha256=play_delivery_digest,
        decision_count=len(decisions),
        evidence_file_count=len(seen_paths),
        reviewer_count=len(decision_reviewers),
        manifest_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> GovernanceSummary:
    path = path.expanduser()
    try:
        raw, _ = _read_regular_bytes(path, "governance manifest", MAX_MANIFEST_BYTES)
        data = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except (OSError, strict_json.StrictJsonError) as exc:
        raise GovernanceError(str(exc)) from exc
    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    try:
        atomic_write_json(path, payload, "release-governance summary")
    except StableFileError as exc:
        raise GovernanceError(str(exc)) from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = load_and_validate(args.manifest)
    except GovernanceError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        try:
            _write_json(args.summary_output, payload)
        except (OSError, GovernanceError) as exc:
            print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
            return 1
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
