#!/usr/bin/env python3
"""Fail-closed validator for Forest Run physical-device release evidence.

The validator proves only that an evidence bundle is internally consistent and
covers the declared acceptance policy. It does not create device evidence and
must not be used to replace real hardware, store-delivery, visual, or policy
review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

SCHEMA_VERSION = 1
CANONICAL_REPOSITORY = "Anurag9000/Forest_Run"
CANONICAL_BRANCH = "main"
CANONICAL_APPLICATION_ID = "com.anurag9000.forestrun"
SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SESSION_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,79}$")
ALLOWED_MANUAL_STATES = {"pass", "not_applicable"}
ALLOWED_APPROVAL_STATES = {"approved"}
REQUIRED_MANUAL_CHECKS = {
    "touch_controls",
    "safe_content_readability",
    "audio",
    "haptics",
    "reduced_motion",
    "lifecycle_recovery",
    "artwork_animation",
}
REQUIRED_APPROVALS = {
    "visual",
    "metadata",
    "privacy",
    "data_safety",
    "content_rating",
    "target_audience",
    "store_policy",
}


class EvidenceError(ValueError):
    """Raised when an evidence bundle violates the acceptance contract."""


@dataclass(frozen=True)
class Thresholds:
    max_p95_frame_ms: float
    max_p99_frame_ms: float
    max_slow_frame_ratio: float
    max_peak_pss_mb: float
    max_crashes: int
    max_anrs: int
    min_duration_seconds: float


@dataclass(frozen=True)
class ValidationSummary:
    candidate_sha: str
    artifact_sha256: str
    session_count: int
    covered_device_classes: tuple[str, ...]
    covered_scenarios: tuple[str, ...]
    evidence_file_count: int
    bundle_sha256: str

    def to_json(self) -> dict[str, Any]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "artifact_sha256": self.artifact_sha256,
            "session_count": self.session_count,
            "covered_device_classes": list(self.covered_device_classes),
            "covered_scenarios": list(self.covered_scenarios),
            "evidence_file_count": self.evidence_file_count,
            "bundle_sha256": self.bundle_sha256,
        }


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise EvidenceError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise EvidenceError(f"{label} must be an array")
    return value


def _string(value: Any, label: str, *, nonempty: bool = True) -> str:
    if not isinstance(value, str):
        raise EvidenceError(f"{label} must be a string")
    result = value.strip()
    if nonempty and not result:
        raise EvidenceError(f"{label} must not be blank")
    return result


def _bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise EvidenceError(f"{label} must be a boolean")
    return value


def _finite_number(value: Any, label: str, *, minimum: float | None = None) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise EvidenceError(f"{label} must be a number")
    result = float(value)
    if not math.isfinite(result):
        raise EvidenceError(f"{label} must be finite")
    if minimum is not None and result < minimum:
        raise EvidenceError(f"{label} must be >= {minimum}")
    return result


def _integer(value: Any, label: str, *, minimum: int | None = None) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise EvidenceError(f"{label} must be an integer")
    if minimum is not None and value < minimum:
        raise EvidenceError(f"{label} must be >= {minimum}")
    return value


def _unique_strings(value: Any, label: str, *, minimum_items: int = 1) -> tuple[str, ...]:
    raw = _sequence(value, label)
    items = tuple(_string(item, f"{label}[]") for item in raw)
    if len(items) < minimum_items:
        raise EvidenceError(f"{label} must contain at least {minimum_items} item(s)")
    if len(set(items)) != len(items):
        raise EvidenceError(f"{label} must not contain duplicates")
    return items


def _parse_utc(value: Any, label: str) -> datetime:
    text = _string(value, label)
    if not text.endswith("Z"):
        raise EvidenceError(f"{label} must be an ISO-8601 UTC timestamp ending in Z")
    try:
        parsed = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as exc:
        raise EvidenceError(f"{label} is not a valid ISO-8601 timestamp") from exc
    return parsed.astimezone(timezone.utc)


def _safe_evidence_path(value: Any, label: str) -> str:
    text = _string(value, label)
    if "\\" in text:
        raise EvidenceError(f"{label} must use forward slashes")
    path = PurePosixPath(text)
    if path.is_absolute() or ".." in path.parts or text.startswith("~"):
        raise EvidenceError(f"{label} must be a safe relative path")
    if not path.parts or any(part in {"", "."} for part in path.parts):
        raise EvidenceError(f"{label} must be normalized")
    return text


def _parse_thresholds(policy: Mapping[str, Any]) -> Thresholds:
    raw = _mapping(policy.get("thresholds"), "policy.thresholds")
    p95 = _finite_number(
        raw.get("max_p95_frame_ms"),
        "policy.thresholds.max_p95_frame_ms",
        minimum=0.1,
    )
    p99 = _finite_number(
        raw.get("max_p99_frame_ms"),
        "policy.thresholds.max_p99_frame_ms",
        minimum=p95,
    )
    slow = _finite_number(
        raw.get("max_slow_frame_ratio"),
        "policy.thresholds.max_slow_frame_ratio",
        minimum=0.0,
    )
    if slow > 1.0:
        raise EvidenceError("policy.thresholds.max_slow_frame_ratio must be <= 1.0")
    return Thresholds(
        max_p95_frame_ms=p95,
        max_p99_frame_ms=p99,
        max_slow_frame_ratio=slow,
        max_peak_pss_mb=_finite_number(
            raw.get("max_peak_pss_mb"),
            "policy.thresholds.max_peak_pss_mb",
            minimum=1.0,
        ),
        max_crashes=_integer(
            raw.get("max_crashes"),
            "policy.thresholds.max_crashes",
            minimum=0,
        ),
        max_anrs=_integer(
            raw.get("max_anrs"),
            "policy.thresholds.max_anrs",
            minimum=0,
        ),
        min_duration_seconds=_finite_number(
            raw.get("min_duration_seconds"),
            "policy.thresholds.min_duration_seconds",
            minimum=1.0,
        ),
    )


def _require_exact_keys(
    mapping: Mapping[str, Any],
    required: Iterable[str],
    label: str,
) -> None:
    missing = sorted(set(required) - set(mapping))
    if missing:
        raise EvidenceError(f"{label} is missing: {', '.join(missing)}")


def _validate_candidate(candidate: Mapping[str, Any]) -> tuple[str, str, str, str, int]:
    repository = _string(candidate.get("repository"), "candidate.repository")
    branch = _string(candidate.get("branch"), "candidate.branch")
    application_id = _string(candidate.get("application_id"), "candidate.application_id")
    sha = _string(candidate.get("commit_sha"), "candidate.commit_sha").lower()
    artifact_sha = _string(
        candidate.get("artifact_sha256"),
        "candidate.artifact_sha256",
    ).lower()
    artifact_path = _safe_evidence_path(
        candidate.get("artifact_path"),
        "candidate.artifact_path",
    )
    version_code = _integer(
        candidate.get("version_code"),
        "candidate.version_code",
        minimum=1,
    )

    if repository != CANONICAL_REPOSITORY:
        raise EvidenceError(f"candidate.repository must be {CANONICAL_REPOSITORY}")
    if branch != CANONICAL_BRANCH:
        raise EvidenceError("candidate.branch must be main")
    if application_id != CANONICAL_APPLICATION_ID:
        raise EvidenceError(
            f"candidate.application_id must be {CANONICAL_APPLICATION_ID}"
        )
    if not SHA1_RE.fullmatch(sha):
        raise EvidenceError("candidate.commit_sha must be a lowercase 40-hex SHA")
    if not SHA256_RE.fullmatch(artifact_sha):
        raise EvidenceError(
            "candidate.artifact_sha256 must be a lowercase 64-hex digest"
        )
    if not _bool(candidate.get("signed"), "candidate.signed"):
        raise EvidenceError("candidate.signed must be true")

    certificate_sha = _string(
        candidate.get("certificate_sha256"),
        "candidate.certificate_sha256",
    ).lower()
    if not SHA256_RE.fullmatch(certificate_sha):
        raise EvidenceError(
            "candidate.certificate_sha256 must be a lowercase 64-hex digest"
        )

    store = _mapping(candidate.get("store_delivery"), "candidate.store_delivery")
    if _string(store.get("track"), "candidate.store_delivery.track") != "internal":
        raise EvidenceError("candidate.store_delivery.track must be internal")
    if not _bool(store.get("installed"), "candidate.store_delivery.installed"):
        raise EvidenceError("candidate.store_delivery.installed must be true")
    if (
        _string(store.get("package_name"), "candidate.store_delivery.package_name")
        != application_id
    ):
        raise EvidenceError(
            "candidate.store_delivery.package_name does not match application_id"
        )
    if (
        _integer(
            store.get("version_code"),
            "candidate.store_delivery.version_code",
            minimum=1,
        )
        != version_code
    ):
        raise EvidenceError(
            "candidate.store_delivery.version_code does not match candidate.version_code"
        )
    if (
        _string(
            store.get("artifact_sha256"),
            "candidate.store_delivery.artifact_sha256",
        ).lower()
        != artifact_sha
    ):
        raise EvidenceError(
            "candidate.store_delivery.artifact_sha256 does not match candidate"
        )
    if (
        _string(
            store.get("certificate_sha256"),
            "candidate.store_delivery.certificate_sha256",
        ).lower()
        != certificate_sha
    ):
        raise EvidenceError(
            "candidate.store_delivery.certificate_sha256 does not match candidate"
        )
    return sha, artifact_sha, artifact_path, certificate_sha, version_code


def _validate_session(
    raw_session: Any,
    *,
    index: int,
    candidate_sha: str,
    artifact_sha: str,
    certificate_sha: str,
    version_code: int,
    required_scenarios: tuple[str, ...],
    thresholds: Thresholds,
    evidence_paths: dict[str, str],
) -> tuple[str, str, set[str], datetime]:
    label = f"sessions[{index}]"
    session = _mapping(raw_session, label)
    session_id = _string(session.get("session_id"), f"{label}.session_id")
    if not SESSION_ID_RE.fullmatch(session_id):
        raise EvidenceError(f"{label}.session_id has invalid characters or length")

    started = _parse_utc(session.get("started_at_utc"), f"{label}.started_at_utc")
    completed = _parse_utc(
        session.get("completed_at_utc"),
        f"{label}.completed_at_utc",
    )
    if completed < started:
        raise EvidenceError(f"{label}.completed_at_utc precedes started_at_utc")
    duration = _finite_number(
        session.get("duration_seconds"),
        f"{label}.duration_seconds",
        minimum=thresholds.min_duration_seconds,
    )
    elapsed = (completed - started).total_seconds()
    if abs(duration - elapsed) > max(5.0, elapsed * 0.05):
        raise EvidenceError(f"{label}.duration_seconds is inconsistent with timestamps")

    device = _mapping(session.get("device"), f"{label}.device")
    device_class = _string(device.get("class"), f"{label}.device.class")
    _string(device.get("manufacturer"), f"{label}.device.manufacturer")
    _string(device.get("model"), f"{label}.device.model")
    _string(device.get("build_fingerprint"), f"{label}.device.build_fingerprint")
    _integer(device.get("sdk"), f"{label}.device.sdk", minimum=24)
    _integer(device.get("ram_mb"), f"{label}.device.ram_mb", minimum=512)
    width = _integer(device.get("width_px"), f"{label}.device.width_px", minimum=320)
    height = _integer(
        device.get("height_px"),
        f"{label}.device.height_px",
        minimum=320,
    )
    _integer(device.get("density_dpi"), f"{label}.device.density_dpi", minimum=120)
    is_tablet = _bool(device.get("tablet"), f"{label}.device.tablet")
    cutout = _bool(device.get("cutout"), f"{label}.device.cutout")
    refresh_hz = _finite_number(
        device.get("refresh_hz"),
        f"{label}.device.refresh_hz",
        minimum=30.0,
    )
    if device_class == "tablet" and not is_tablet:
        raise EvidenceError(f"{label}.device.tablet must be true for tablet class")
    if device_class != "tablet" and is_tablet:
        raise EvidenceError(f"{label}.device.tablet must be false outside tablet class")
    if device_class == "cutout_phone" and not cutout:
        raise EvidenceError(
            f"{label}.device.cutout must be true for cutout_phone class"
        )
    if device_class == "high_refresh_phone" and refresh_hz < 90.0:
        raise EvidenceError(
            f"{label}.device.refresh_hz must be >= 90 for high_refresh_phone class"
        )
    if width == height:
        raise EvidenceError(f"{label}.device dimensions must describe a non-square display")

    build = _mapping(session.get("build"), f"{label}.build")
    if (
        _string(build.get("commit_sha"), f"{label}.build.commit_sha").lower()
        != candidate_sha
    ):
        raise EvidenceError(f"{label}.build.commit_sha does not match candidate")
    if (
        _string(
            build.get("artifact_sha256"),
            f"{label}.build.artifact_sha256",
        ).lower()
        != artifact_sha
    ):
        raise EvidenceError(f"{label}.build.artifact_sha256 does not match candidate")
    if (
        _integer(
            build.get("version_code"),
            f"{label}.build.version_code",
            minimum=1,
        )
        != version_code
    ):
        raise EvidenceError(f"{label}.build.version_code does not match candidate")
    if (
        _string(
            build.get("certificate_sha256"),
            f"{label}.build.certificate_sha256",
        ).lower()
        != certificate_sha
    ):
        raise EvidenceError(
            f"{label}.build.certificate_sha256 does not match candidate"
        )
    if not _bool(build.get("signed"), f"{label}.build.signed"):
        raise EvidenceError(f"{label}.build.signed must be true")
    if (
        _string(build.get("installed_via"), f"{label}.build.installed_via")
        != "internal_store"
    ):
        raise EvidenceError(f"{label}.build.installed_via must be internal_store")

    scenarios = _mapping(session.get("scenarios"), f"{label}.scenarios")
    missing_scenarios = sorted(set(required_scenarios) - set(scenarios))
    if missing_scenarios:
        raise EvidenceError(
            f"{label}.scenarios is missing: {', '.join(missing_scenarios)}"
        )
    passed_scenarios: set[str] = set()
    for scenario_name in required_scenarios:
        result = _mapping(
            scenarios[scenario_name],
            f"{label}.scenarios.{scenario_name}",
        )
        if not _bool(
            result.get("passed"),
            f"{label}.scenarios.{scenario_name}.passed",
        ):
            raise EvidenceError(
                f"{label}.scenarios.{scenario_name}.passed must be true"
            )
        files = _sequence(
            result.get("evidence_files"),
            f"{label}.scenarios.{scenario_name}.evidence_files",
        )
        if not files:
            raise EvidenceError(
                f"{label}.scenarios.{scenario_name}.evidence_files must not be empty"
            )
        for file_index, raw_entry in enumerate(files):
            entry_label = (
                f"{label}.scenarios.{scenario_name}.evidence_files[{file_index}]"
            )
            entry = _mapping(raw_entry, entry_label)
            path = _safe_evidence_path(entry.get("path"), f"{entry_label}.path")
            digest = _string(entry.get("sha256"), f"{entry_label}.sha256").lower()
            if not SHA256_RE.fullmatch(digest):
                raise EvidenceError(
                    f"{entry_label}.sha256 must be a lowercase 64-hex digest"
                )
            if path in evidence_paths:
                raise EvidenceError(f"evidence file is reused across results: {path}")
            evidence_paths[path] = digest
        passed_scenarios.add(scenario_name)

    performance = _mapping(session.get("performance"), f"{label}.performance")
    p95 = _finite_number(
        performance.get("p95_frame_ms"),
        f"{label}.performance.p95_frame_ms",
        minimum=0.0,
    )
    p99 = _finite_number(
        performance.get("p99_frame_ms"),
        f"{label}.performance.p99_frame_ms",
        minimum=p95,
    )
    slow = _finite_number(
        performance.get("slow_frame_ratio"),
        f"{label}.performance.slow_frame_ratio",
        minimum=0.0,
    )
    peak_pss = _finite_number(
        performance.get("peak_pss_mb"),
        f"{label}.performance.peak_pss_mb",
        minimum=0.0,
    )
    crashes = _integer(
        performance.get("crashes"),
        f"{label}.performance.crashes",
        minimum=0,
    )
    anrs = _integer(
        performance.get("anrs"),
        f"{label}.performance.anrs",
        minimum=0,
    )
    if p95 > thresholds.max_p95_frame_ms:
        raise EvidenceError(f"{label} exceeds max_p95_frame_ms")
    if p99 > thresholds.max_p99_frame_ms:
        raise EvidenceError(f"{label} exceeds max_p99_frame_ms")
    if slow > thresholds.max_slow_frame_ratio:
        raise EvidenceError(f"{label} exceeds max_slow_frame_ratio")
    if peak_pss > thresholds.max_peak_pss_mb:
        raise EvidenceError(f"{label} exceeds max_peak_pss_mb")
    if crashes > thresholds.max_crashes:
        raise EvidenceError(f"{label} exceeds max_crashes")
    if anrs > thresholds.max_anrs:
        raise EvidenceError(f"{label} exceeds max_anrs")

    manual_checks = _mapping(session.get("manual_checks"), f"{label}.manual_checks")
    _require_exact_keys(
        manual_checks,
        REQUIRED_MANUAL_CHECKS,
        f"{label}.manual_checks",
    )
    for name in REQUIRED_MANUAL_CHECKS:
        state = _string(manual_checks[name], f"{label}.manual_checks.{name}")
        if state not in ALLOWED_MANUAL_STATES:
            raise EvidenceError(
                f"{label}.manual_checks.{name} must be pass or not_applicable"
            )

    return session_id, device_class, passed_scenarios, completed


def validate_bundle(
    data: Any,
    *,
    source_bytes: bytes,
    evidence_base: Path | None = None,
) -> ValidationSummary:
    root = _mapping(data, "root")
    if (
        _integer(root.get("schema_version"), "schema_version", minimum=1)
        != SCHEMA_VERSION
    ):
        raise EvidenceError(f"schema_version must be {SCHEMA_VERSION}")

    generated_at = _parse_utc(root.get("generated_at_utc"), "generated_at_utc")
    candidate = _mapping(root.get("candidate"), "candidate")
    (
        candidate_sha,
        artifact_sha,
        artifact_path,
        certificate_sha,
        version_code,
    ) = _validate_candidate(candidate)

    policy = _mapping(root.get("policy"), "policy")
    required_classes = _unique_strings(
        policy.get("required_device_classes"),
        "policy.required_device_classes",
    )
    required_scenarios = _unique_strings(
        policy.get("required_scenarios"),
        "policy.required_scenarios",
    )
    min_sessions_per_class = _integer(
        policy.get("min_sessions_per_class"),
        "policy.min_sessions_per_class",
        minimum=1,
    )
    thresholds = _parse_thresholds(policy)

    sessions = _sequence(root.get("sessions"), "sessions")
    if not sessions:
        raise EvidenceError("sessions must not be empty")

    session_ids: set[str] = set()
    class_counts = {name: 0 for name in required_classes}
    covered_scenarios: set[str] = set()
    evidence_paths: dict[str, str] = {}
    for index, raw_session in enumerate(sessions):
        session_id, device_class, passed, completed_at = _validate_session(
            raw_session,
            index=index,
            candidate_sha=candidate_sha,
            artifact_sha=artifact_sha,
            certificate_sha=certificate_sha,
            version_code=version_code,
            required_scenarios=required_scenarios,
            thresholds=thresholds,
            evidence_paths=evidence_paths,
        )
        if session_id in session_ids:
            raise EvidenceError(f"duplicate session_id: {session_id}")
        session_ids.add(session_id)
        if completed_at > generated_at:
            raise EvidenceError(
                f"session completed after generated_at_utc: {session_id}"
            )
        if device_class not in class_counts:
            raise EvidenceError(f"undeclared device class: {device_class}")
        class_counts[device_class] += 1
        covered_scenarios.update(passed)

    undercovered = [
        name
        for name, count in class_counts.items()
        if count < min_sessions_per_class
    ]
    if undercovered:
        raise EvidenceError(
            "insufficient sessions for device classes: "
            + ", ".join(sorted(undercovered))
        )

    if evidence_base is not None:
        canonical_base = evidence_base.resolve()
        resolved_artifact = (canonical_base / artifact_path).resolve()
        try:
            resolved_artifact.relative_to(canonical_base)
        except ValueError as exc:
            raise EvidenceError(
                "candidate.artifact_path escapes manifest directory"
            ) from exc
        if not resolved_artifact.is_file():
            raise EvidenceError(f"candidate artifact is missing: {artifact_path}")
        if hashlib.sha256(resolved_artifact.read_bytes()).hexdigest() != artifact_sha:
            raise EvidenceError(
                f"candidate artifact digest mismatch: {artifact_path}"
            )
        for relative_path, expected_digest in evidence_paths.items():
            candidate_path = (canonical_base / relative_path).resolve()
            try:
                candidate_path.relative_to(canonical_base)
            except ValueError as exc:
                raise EvidenceError(
                    f"evidence path escapes evidence base: {relative_path}"
                ) from exc
            if not candidate_path.is_file():
                raise EvidenceError(f"evidence file is missing: {relative_path}")
            actual_digest = hashlib.sha256(candidate_path.read_bytes()).hexdigest()
            if actual_digest != expected_digest:
                raise EvidenceError(f"evidence digest mismatch: {relative_path}")

    approvals = _mapping(root.get("approvals"), "approvals")
    _require_exact_keys(
        approvals,
        REQUIRED_APPROVALS | {"reviewers", "reviewed_at_utc"},
        "approvals",
    )
    for name in REQUIRED_APPROVALS:
        state = _string(approvals[name], f"approvals.{name}")
        if state not in ALLOWED_APPROVAL_STATES:
            raise EvidenceError(f"approvals.{name} must be approved")
    reviewers = _unique_strings(
        approvals.get("reviewers"),
        "approvals.reviewers",
        minimum_items=2,
    )
    if any(len(name) > 120 for name in reviewers):
        raise EvidenceError("approvals.reviewers contains an overlong value")
    reviewed_at = _parse_utc(
        approvals.get("reviewed_at_utc"),
        "approvals.reviewed_at_utc",
    )
    if reviewed_at > generated_at:
        raise EvidenceError(
            "approvals.reviewed_at_utc must not be after generated_at_utc"
        )

    return ValidationSummary(
        candidate_sha=candidate_sha,
        artifact_sha256=artifact_sha,
        session_count=len(sessions),
        covered_device_classes=tuple(sorted(class_counts)),
        covered_scenarios=tuple(sorted(covered_scenarios)),
        evidence_file_count=len(evidence_paths),
        bundle_sha256=hashlib.sha256(source_bytes).hexdigest(),
    )


def load_and_validate(path: Path) -> ValidationSummary:
    raw = path.read_bytes()
    try:
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"invalid JSON: {exc}") from exc
    return validate_bundle(data, source_bytes=raw, evidence_base=path.parent)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args(argv)

    try:
        summary = load_and_validate(args.manifest)
    except (OSError, EvidenceError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1

    payload = summary.to_json()
    if args.summary_output is not None:
        _write_json(args.summary_output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
