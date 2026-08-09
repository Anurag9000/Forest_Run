#!/usr/bin/env python3
"""Validate candidate-bound human gameplay, accessibility, and presentation evidence.

This layer complements physical/device metrics. It never manufactures a pass: every
required human check must be recorded against a session from an already-valid
physical-device acceptance manifest, with immutable evidence files and at least two
distinct final reviewers.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

import strict_json
import validate_device_acceptance as device_acceptance

SCHEMA_VERSION = 1
MAX_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_EVIDENCE_FILE_BYTES = 2 * 1024 * 1024 * 1024
SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REVIEW_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,79}$")

MANDATORY_DEVICE_CLASSES = frozenset(device_acceptance.MANDATORY_DEVICE_CLASSES)

GAMEPLAY_CHECKS = frozenset(
    {
        "touch_latency",
        "short_jump_feel",
        "hold_jump_feel",
        "swipe_down_duck",
        "gesture_cancellation",
        "all_entity_telegraphs_hitboxes_outcomes",
        "high_speed_encounter_combinations",
        "bloom_hazard_readability",
        "rest_restart_continuity",
        "safe_content_and_system_bars",
        "text_contrast_readability",
        "garden_wardrobe_continuity",
        "ghost_readability",
        "relationship_progression_cadence",
    }
)

ACCESSIBILITY_CHECKS = frozenset(
    {
        "talkback_focus_order",
        "labels_and_state_descriptions",
        "semantic_action_reliability",
        "settings_toggles",
        "playing_controls",
        "garden_plants",
        "wardrobe",
        "rest_flow",
        "recovery_dialogs",
        "announcement_cadence",
        "lifecycle_resume",
        "large_text_and_display_scale",
        "cutout_and_aspect_variants",
        "audio_coexistence",
        "reduced_motion",
        "switch_access",
    }
)

PRESENTATION_CHECKS = frozenset(
    {
        "artwork_animation",
        "wolf_animation",
        "procedural_scenery",
        "fixed_landscape_composition",
        "audio_balance_latency",
        "haptic_intensity_cadence",
        "reduced_motion_presentation",
    }
)

NOT_APPLICABLE_ALLOWED = frozenset(
    {
        "switch_access",
        "haptic_intensity_cadence",
        "cutout_and_aspect_variants",
    }
)


class HumanAcceptanceError(ValueError):
    """Raised when human acceptance evidence is incomplete, stale, or unsafe."""


@dataclass(frozen=True)
class HumanAcceptanceSummary:
    candidate_sha: str
    artifact_sha256: str
    device_acceptance_sha256: str
    review_count: int
    reviewer_count: int
    covered_device_classes: tuple[str, ...]
    evidence_file_count: int
    manifest_sha256: str

    def to_json(self) -> dict[str, object]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "artifact_sha256": self.artifact_sha256,
            "device_acceptance_sha256": self.device_acceptance_sha256,
            "review_count": self.review_count,
            "reviewer_count": self.reviewer_count,
            "covered_device_classes": list(self.covered_device_classes),
            "evidence_file_count": self.evidence_file_count,
            "manifest_sha256": self.manifest_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise HumanAcceptanceError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise HumanAcceptanceError(f"{label} must be an array")
    return value


def _string(value: Any, label: str, *, maximum: int = 4096) -> str:
    if not isinstance(value, str):
        raise HumanAcceptanceError(f"{label} must be a string")
    result = value.strip()
    if not result:
        raise HumanAcceptanceError(f"{label} must not be blank")
    if len(result) > maximum:
        raise HumanAcceptanceError(f"{label} exceeds the {maximum}-character limit")
    if any(ord(character) < 32 or ord(character) == 127 for character in result):
        raise HumanAcceptanceError(f"{label} must not contain control characters")
    return result


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise HumanAcceptanceError(f"{label} must be an integer")
    if value < minimum:
        raise HumanAcceptanceError(f"{label} must be >= {minimum}")
    return value


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label, maximum=64)
    if not text.endswith("Z"):
        raise HumanAcceptanceError(f"{label} must use ISO-8601 UTC Z notation")
    try:
        parsed = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as exc:
        raise HumanAcceptanceError(f"{label} is not a valid ISO-8601 timestamp") from exc
    return parsed.astimezone(timezone.utc)


def _safe_relative_path(value: Any, label: str) -> str:
    text = _string(value, label, maximum=512)
    if "\\" in text or text.startswith("~"):
        raise HumanAcceptanceError(f"{label} must be a normalized POSIX relative path")
    path = PurePosixPath(text)
    if path.is_absolute() or not path.parts or any(part in {"", ".", ".."} for part in path.parts):
        raise HumanAcceptanceError(f"{label} must be a safe relative path")
    return path.as_posix()


def _require_exact_keys(value: Mapping[str, Any], keys: Iterable[str], label: str) -> None:
    expected = set(keys)
    actual = set(value)
    missing = sorted(expected - actual)
    extras = sorted(actual - expected)
    if missing:
        raise HumanAcceptanceError(f"{label} is missing: {', '.join(missing)}")
    if extras:
        raise HumanAcceptanceError(f"{label} contains unrecognized keys: {', '.join(extras)}")


def _hash_regular_file(path: Path, label: str, maximum_bytes: int) -> tuple[str, os.stat_result]:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise HumanAcceptanceError(f"{label} is missing: {path}") from exc
    except OSError as exc:
        raise HumanAcceptanceError(f"could not inspect {label}: {path}: {exc}") from exc
    if path.is_symlink() or not path.is_file():
        raise HumanAcceptanceError(f"{label} must be a regular non-symlink file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise HumanAcceptanceError(
            f"{label} must be between 1 and {maximum_bytes} bytes: {path}"
        )
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        after = path.lstat()
    except OSError as exc:
        raise HumanAcceptanceError(f"could not read {label}: {path}: {exc}") from exc
    if (
        before.st_size != after.st_size
        or before.st_mtime_ns != after.st_mtime_ns
        or (before.st_ino and before.st_ino != after.st_ino)
    ):
        raise HumanAcceptanceError(f"{label} changed while being hashed: {path}")
    return digest.hexdigest(), after


def _resolve_inside(base: Path, relative: str, label: str) -> Path:
    base = base.resolve()
    lexical = base / relative
    try:
        lexical.relative_to(base)
    except ValueError as exc:
        raise HumanAcceptanceError(f"{label} escapes the evidence root") from exc
    resolved = lexical.resolve()
    try:
        resolved.relative_to(base)
    except ValueError as exc:
        raise HumanAcceptanceError(f"{label} resolves outside the evidence root") from exc
    return resolved


def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, int]:
    _require_exact_keys(
        candidate,
        {
            "repository",
            "branch",
            "application_id",
            "commit_sha",
            "version_code",
            "artifact_sha256",
            "certificate_sha256",
        },
        "candidate",
    )
    if _string(candidate["repository"], "candidate.repository") != device_acceptance.CANONICAL_REPOSITORY:
        raise HumanAcceptanceError("candidate.repository is not canonical")
    if _string(candidate["branch"], "candidate.branch") != device_acceptance.CANONICAL_BRANCH:
        raise HumanAcceptanceError("candidate.branch must be main")
    if _string(candidate["application_id"], "candidate.application_id") != device_acceptance.CANONICAL_APPLICATION_ID:
        raise HumanAcceptanceError("candidate.application_id is not canonical")
    sha = _string(candidate["commit_sha"], "candidate.commit_sha", maximum=40).lower()
    artifact = _string(candidate["artifact_sha256"], "candidate.artifact_sha256", maximum=64).lower()
    certificate = _string(candidate["certificate_sha256"], "candidate.certificate_sha256", maximum=64).lower()
    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)
    if not SHA40_RE.fullmatch(sha):
        raise HumanAcceptanceError("candidate.commit_sha must be lowercase 40-hex")
    if not SHA256_RE.fullmatch(artifact):
        raise HumanAcceptanceError("candidate.artifact_sha256 must be lowercase 64-hex")
    if not SHA256_RE.fullmatch(certificate):
        raise HumanAcceptanceError("candidate.certificate_sha256 must be lowercase 64-hex")
    return sha, artifact, certificate, version_code


def _validate_check_group(
    raw: Any,
    *,
    required: frozenset[str],
    label: str,
    device_class: str,
) -> None:
    checks = _mapping(raw, label)
    _require_exact_keys(checks, required, label)
    for name in required:
        state = _string(checks[name], f"{label}.{name}", maximum=32)
        if state not in {"pass", "not_applicable"}:
            raise HumanAcceptanceError(f"{label}.{name} must be pass or not_applicable")
        if state == "not_applicable" and name not in NOT_APPLICABLE_ALLOWED:
            raise HumanAcceptanceError(f"{label}.{name} cannot be not_applicable")
        if name == "cutout_and_aspect_variants" and device_class == "cutout_phone" and state != "pass":
            raise HumanAcceptanceError(
                f"{label}.cutout_and_aspect_variants must pass on cutout_phone"
            )


def _normalized_name(value: str) -> str:
    return unicodedata.normalize("NFKC", value).casefold()


def _load_bound_device_acceptance(
    reference: Mapping[str, Any],
    *,
    base: Path,
    candidate_sha: str,
    artifact_sha: str,
    certificate_sha: str,
    version_code: int,
) -> tuple[str, dict[str, str]]:
    _require_exact_keys(reference, {"path", "sha256"}, "device_acceptance")
    relative = _safe_relative_path(reference["path"], "device_acceptance.path")
    expected = _string(reference["sha256"], "device_acceptance.sha256", maximum=64).lower()
    if not SHA256_RE.fullmatch(expected):
        raise HumanAcceptanceError("device_acceptance.sha256 must be lowercase 64-hex")
    path = _resolve_inside(base, relative, "device_acceptance.path")
    actual, _ = _hash_regular_file(path, "device acceptance manifest", MAX_MANIFEST_BYTES)
    if actual != expected:
        raise HumanAcceptanceError("device acceptance manifest digest mismatch")
    try:
        raw = path.read_bytes()
        data = strict_json.loads(raw, label=str(path), maximum_bytes=MAX_MANIFEST_BYTES, require_object=True)
        summary = device_acceptance.validate_bundle(
            data,
            source_bytes=raw,
            evidence_base=path.parent,
        )
    except (OSError, strict_json.StrictJsonError, device_acceptance.EvidenceError) as exc:
        raise HumanAcceptanceError(f"referenced device acceptance is invalid: {exc}") from exc
    candidate = _mapping(data.get("candidate"), "device candidate")
    if summary.candidate_sha != candidate_sha or summary.artifact_sha256 != artifact_sha:
        raise HumanAcceptanceError("device acceptance candidate does not match human candidate")
    if _string(candidate.get("certificate_sha256"), "device candidate.certificate_sha256").lower() != certificate_sha:
        raise HumanAcceptanceError("device acceptance certificate does not match human candidate")
    if _integer(candidate.get("version_code"), "device candidate.version_code", minimum=1) != version_code:
        raise HumanAcceptanceError("device acceptance version does not match human candidate")
    sessions: dict[str, str] = {}
    for index, raw_session in enumerate(_sequence(data.get("sessions"), "device sessions")):
        session = _mapping(raw_session, f"device sessions[{index}]")
        session_id = _string(session.get("session_id"), f"device sessions[{index}].session_id", maximum=80)
        device = _mapping(session.get("device"), f"device sessions[{index}].device")
        sessions[session_id] = _string(device.get("class"), f"device sessions[{index}].device.class", maximum=80)
    return actual, sessions


def validate_bundle(
    data: Any,
    *,
    source_bytes: bytes,
    evidence_base: Path,
) -> HumanAcceptanceSummary:
    if not source_bytes or len(source_bytes) > MAX_MANIFEST_BYTES:
        raise HumanAcceptanceError(
            f"human acceptance manifest must be between 1 and {MAX_MANIFEST_BYTES} bytes"
        )
    root = _mapping(data, "root")
    _require_exact_keys(
        root,
        {
            "schema_version",
            "generated_at_utc",
            "candidate",
            "device_acceptance",
            "reviews",
            "final_review",
        },
        "root",
    )
    if _integer(root["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise HumanAcceptanceError(f"schema_version must be {SCHEMA_VERSION}")
    generated_at = _parse_utc(root["generated_at_utc"], "generated_at_utc")
    candidate_sha, artifact_sha, certificate_sha, version_code = _validate_candidate(
        _mapping(root["candidate"], "candidate")
    )
    device_digest, device_sessions = _load_bound_device_acceptance(
        _mapping(root["device_acceptance"], "device_acceptance"),
        base=evidence_base,
        candidate_sha=candidate_sha,
        artifact_sha=artifact_sha,
        certificate_sha=certificate_sha,
        version_code=version_code,
    )

    reviews = _sequence(root["reviews"], "reviews")
    if not reviews:
        raise HumanAcceptanceError("reviews must not be empty")
    review_ids: set[str] = set()
    reviewers: dict[str, str] = {}
    covered_classes: set[str] = set()
    evidence_paths: dict[str, tuple[str, tuple[int, int] | None]] = {}
    canonical_base = evidence_base.resolve()

    for index, raw_review in enumerate(reviews):
        label = f"reviews[{index}]"
        review = _mapping(raw_review, label)
        _require_exact_keys(
            review,
            {
                "review_id",
                "device_acceptance_session_id",
                "device_class",
                "reviewer",
                "talkback_version",
                "switch_access_version",
                "started_at_utc",
                "completed_at_utc",
                "gameplay_checks",
                "accessibility_checks",
                "presentation_checks",
                "evidence_files",
                "notes",
            },
            label,
        )
        review_id = _string(review["review_id"], f"{label}.review_id", maximum=80)
        if not REVIEW_ID_RE.fullmatch(review_id) or review_id in review_ids:
            raise HumanAcceptanceError(f"{label}.review_id is invalid or duplicated")
        review_ids.add(review_id)
        session_id = _string(
            review["device_acceptance_session_id"],
            f"{label}.device_acceptance_session_id",
            maximum=80,
        )
        device_class = _string(review["device_class"], f"{label}.device_class", maximum=80)
        expected_class = device_sessions.get(session_id)
        if expected_class is None:
            raise HumanAcceptanceError(f"{label} references an unknown device acceptance session")
        if expected_class != device_class:
            raise HumanAcceptanceError(f"{label}.device_class does not match referenced session")
        if device_class not in MANDATORY_DEVICE_CLASSES:
            raise HumanAcceptanceError(f"{label}.device_class is not a mandatory release class")
        covered_classes.add(device_class)

        reviewer = _string(review["reviewer"], f"{label}.reviewer", maximum=120)
        normalized = _normalized_name(reviewer)
        reviewers.setdefault(normalized, reviewer)
        _string(review["talkback_version"], f"{label}.talkback_version", maximum=160)
        _string(review["switch_access_version"], f"{label}.switch_access_version", maximum=160)
        started = _parse_utc(review["started_at_utc"], f"{label}.started_at_utc")
        completed = _parse_utc(review["completed_at_utc"], f"{label}.completed_at_utc")
        if completed < started:
            raise HumanAcceptanceError(f"{label}.completed_at_utc precedes started_at_utc")
        if completed > generated_at:
            raise HumanAcceptanceError(f"{label} completes after generated_at_utc")

        _validate_check_group(
            review["gameplay_checks"],
            required=GAMEPLAY_CHECKS,
            label=f"{label}.gameplay_checks",
            device_class=device_class,
        )
        _validate_check_group(
            review["accessibility_checks"],
            required=ACCESSIBILITY_CHECKS,
            label=f"{label}.accessibility_checks",
            device_class=device_class,
        )
        _validate_check_group(
            review["presentation_checks"],
            required=PRESENTATION_CHECKS,
            label=f"{label}.presentation_checks",
            device_class=device_class,
        )
        _string(review["notes"], f"{label}.notes", maximum=5000)

        raw_files = _sequence(review["evidence_files"], f"{label}.evidence_files")
        if not raw_files:
            raise HumanAcceptanceError(f"{label}.evidence_files must not be empty")
        for file_index, raw_entry in enumerate(raw_files):
            entry_label = f"{label}.evidence_files[{file_index}]"
            entry = _mapping(raw_entry, entry_label)
            _require_exact_keys(entry, {"path", "sha256"}, entry_label)
            relative = _safe_relative_path(entry["path"], f"{entry_label}.path")
            expected = _string(entry["sha256"], f"{entry_label}.sha256", maximum=64).lower()
            if not SHA256_RE.fullmatch(expected):
                raise HumanAcceptanceError(f"{entry_label}.sha256 must be lowercase 64-hex")
            if relative in evidence_paths:
                raise HumanAcceptanceError(f"human evidence path is reused: {relative}")
            path = _resolve_inside(canonical_base, relative, f"{entry_label}.path")
            actual, stat_result = _hash_regular_file(
                path,
                "human acceptance evidence",
                MAX_EVIDENCE_FILE_BYTES,
            )
            if actual != expected:
                raise HumanAcceptanceError(f"human evidence digest mismatch: {relative}")
            identity = (stat_result.st_dev, stat_result.st_ino) if stat_result.st_ino else None
            if identity is not None and any(existing[1] == identity for existing in evidence_paths.values()):
                raise HumanAcceptanceError(f"human evidence file is reused through a hard link: {relative}")
            evidence_paths[relative] = (actual, identity)

    missing_classes = sorted(MANDATORY_DEVICE_CLASSES - covered_classes)
    if missing_classes:
        raise HumanAcceptanceError(
            "human acceptance is missing device classes: " + ", ".join(missing_classes)
        )

    final_review = _mapping(root["final_review"], "final_review")
    _require_exact_keys(
        final_review,
        {"decision", "reviewers", "reviewed_at_utc", "notes"},
        "final_review",
    )
    if _string(final_review["decision"], "final_review.decision", maximum=32) != "approved":
        raise HumanAcceptanceError("final_review.decision must be approved")
    final_names = [
        _string(value, "final_review.reviewers[]", maximum=120)
        for value in _sequence(final_review["reviewers"], "final_review.reviewers")
    ]
    if len(final_names) < 2:
        raise HumanAcceptanceError("final_review.reviewers must contain at least two reviewers")
    normalized_final = {_normalized_name(value) for value in final_names}
    if len(normalized_final) != len(final_names):
        raise HumanAcceptanceError("final_review.reviewers must be distinct")
    if not normalized_final.issubset(reviewers):
        raise HumanAcceptanceError("final reviewers must have authored at least one device review")
    reviewed_at = _parse_utc(final_review["reviewed_at_utc"], "final_review.reviewed_at_utc")
    if reviewed_at > generated_at:
        raise HumanAcceptanceError("final_review.reviewed_at_utc must not be after generated_at_utc")
    _string(final_review["notes"], "final_review.notes", maximum=5000)

    return HumanAcceptanceSummary(
        candidate_sha=candidate_sha,
        artifact_sha256=artifact_sha,
        device_acceptance_sha256=device_digest,
        review_count=len(reviews),
        reviewer_count=len(reviewers),
        covered_device_classes=tuple(sorted(covered_classes)),
        evidence_file_count=len(evidence_paths),
        manifest_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> HumanAcceptanceSummary:
    path = path.expanduser().resolve()
    try:
        raw = path.read_bytes()
        data = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except (OSError, strict_json.StrictJsonError) as exc:
        raise HumanAcceptanceError(str(exc)) from exc
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
    except HumanAcceptanceError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        _write_json(args.summary_output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
