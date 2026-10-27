#!/usr/bin/env python3
"""Validation of scenario-bound Forest Run screenshot capture evidence."""

from __future__ import annotations

import datetime as dt
import os
import re
import stat
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from strict_json import StrictJsonError, loads

SCHEMA_VERSION = 1
EXPECTED_RUN_MODE = "SCREENSHOT_CAPTURE"
EXPECTED_PACKAGE_NAME = "com.anurag9000.forestrun.debug"
EXPECTED_ACTIVITY_NAME = "com.anurag9000.forestrun.MainActivity"
READY_PREFIX = "FOREST_RUN_SCENARIO_READY"
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
MAX_CAPTURE_EVIDENCE_BYTES = 64 * 1024


class CaptureEvidenceError(ValueError):
    """Raised when a screenshot cannot be tied to its requested capture state."""


@dataclass(frozen=True)
class CaptureEvidence:
    raw_file: str
    scenario: str
    run_mode: str
    readiness_marker: str
    candidate_sha: str
    apk_sha256: str
    device_serial: str
    package_name: str
    activity_name: str
    settle_seconds: float
    captured_at_utc: str
    image_sha256: str
    width: int
    height: int

    @property
    def session_identity(self) -> tuple[str, str, str, str, str]:
        return (
            self.candidate_sha,
            self.apk_sha256,
            self.device_serial,
            self.package_name,
            self.activity_name,
        )


def _load_object(path: Path) -> dict[str, Any]:
    path = path.expanduser()
    try:
        before = path.lstat()
    except OSError as exc:
        raise CaptureEvidenceError(f"Could not inspect capture evidence {path}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode):
        raise CaptureEvidenceError(f"Capture evidence must be a regular non-symlink file: {path}")
    if before.st_size <= 0 or before.st_size > MAX_CAPTURE_EVIDENCE_BYTES:
        raise CaptureEvidenceError(
            f"Capture evidence must be between 1 and {MAX_CAPTURE_EVIDENCE_BYTES} bytes: {path}"
        )
    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise CaptureEvidenceError(f"Could not safely open capture evidence {path}: {exc}") from exc
    identity_fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field) for field in identity_fields
        ):
            raise CaptureEvidenceError(f"Capture evidence changed while being opened: {path}")
        remaining = before.st_size
        chunks: list[bytes] = []
        while remaining:
            chunk = os.read(descriptor, min(16 * 1024, remaining))
            if not chunk:
                raise CaptureEvidenceError(f"Capture evidence changed while being read: {path}")
            chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise CaptureEvidenceError(f"Capture evidence changed while being read: {path}")
        after = os.fstat(descriptor)
    except OSError as exc:
        raise CaptureEvidenceError(f"Could not safely read capture evidence {path}: {exc}") from exc
    finally:
        os.close(descriptor)
    try:
        after_path = path.lstat()
    except OSError as exc:
        raise CaptureEvidenceError(f"Capture evidence changed while being read: {path}: {exc}") from exc
    if any(
        getattr(before, field) != getattr(current, field)
        for current in (after, after_path)
        for field in identity_fields
    ):
        raise CaptureEvidenceError(f"Capture evidence changed while being read: {path}")
    raw_bytes = b"".join(chunks)
    if len(raw_bytes) != before.st_size:
        raise CaptureEvidenceError(f"Capture evidence changed while being read: {path}")
    try:
        parsed = loads(
            raw_bytes,
            label=str(path),
            maximum_bytes=MAX_CAPTURE_EVIDENCE_BYTES,
            require_object=True,
        )
    except StrictJsonError as exc:
        raise CaptureEvidenceError(
            f"Invalid capture evidence JSON {path}: {exc}"
        ) from exc
    if not isinstance(parsed, dict):
        raise CaptureEvidenceError(
            f"Capture evidence must be a JSON object: {path}"
        )
    return parsed


def _string(source: dict[str, Any], key: str, path: Path) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        raise CaptureEvidenceError(
            f"{path}: {key} must be a non-blank string"
        )
    if value != value.strip():
        raise CaptureEvidenceError(
            f"{path}: {key} must not have surrounding whitespace"
        )
    return value


def _canonical_hex(source: dict[str, Any], key: str, path: Path, pattern: re.Pattern[str], detail: str) -> str:
    value = source.get(key)
    if not isinstance(value, str) or pattern.fullmatch(value) is None:
        raise CaptureEvidenceError(f"{path}: {key} must be {detail}")
    return value


def _integer(source: dict[str, Any], key: str, path: Path) -> int:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise CaptureEvidenceError(
            f"{path}: {key} must be a positive integer"
        )
    return value


def _number(source: dict[str, Any], key: str, path: Path) -> float:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise CaptureEvidenceError(f"{path}: {key} must be numeric")
    try:
        number = float(value)
    except OverflowError as exc:
        raise CaptureEvidenceError(f"{path}: {key} must be finite") from exc
    if not 0.0 <= number <= 60.0:
        raise CaptureEvidenceError(
            f"{path}: {key} must be between 0 and 60 seconds"
        )
    return number


def _parse_utc(value: str, path: Path) -> None:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise CaptureEvidenceError(
            f"{path}: capturedAtUtc is not ISO-8601"
        ) from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise CaptureEvidenceError(
            f"{path}: capturedAtUtc must include a timezone"
        )
    if parsed.utcoffset() != dt.timedelta(0):
        raise CaptureEvidenceError(
            f"{path}: capturedAtUtc must use UTC offset +00:00 or Z"
        )


def load_capture_evidence(
    path: Path,
    *,
    expected_raw_file: str,
    expected_scenario: str,
    expected_image_sha256: str,
    expected_width: int,
    expected_height: int,
) -> CaptureEvidence:
    raw = _load_object(path)
    return parse_capture_evidence(
        raw,
        path,
        expected_raw_file=expected_raw_file,
        expected_scenario=expected_scenario,
        expected_image_sha256=expected_image_sha256,
        expected_width=expected_width,
        expected_height=expected_height,
    )


def parse_capture_evidence(
    raw: dict[str, Any],
    path: Path,
    *,
    expected_raw_file: str,
    expected_scenario: str,
    expected_image_sha256: str,
    expected_width: int,
    expected_height: int,
) -> CaptureEvidence:
    """Validate an already parsed sidecar object against one captured image."""
    if type(raw.get("schemaVersion")) is not int or raw["schemaVersion"] != SCHEMA_VERSION:
        raise CaptureEvidenceError(
            f"{path}: schemaVersion must equal {SCHEMA_VERSION}"
        )

    evidence = CaptureEvidence(
        raw_file=_string(raw, "rawFile", path),
        scenario=_string(raw, "scenario", path),
        run_mode=_string(raw, "runMode", path),
        readiness_marker=_string(raw, "readinessMarker", path),
        candidate_sha=_canonical_hex(raw, "candidateSha", path, HEX_40, "a 40-character Git SHA"),
        apk_sha256=_canonical_hex(raw, "apkSha256", path, HEX_64, "a SHA-256 hex digest"),
        device_serial=_string(raw, "deviceSerial", path),
        package_name=_string(raw, "packageName", path),
        activity_name=_string(raw, "activityName", path),
        settle_seconds=_number(raw, "settleSeconds", path),
        captured_at_utc=_string(raw, "capturedAtUtc", path),
        image_sha256=_canonical_hex(raw, "imageSha256", path, HEX_64, "a SHA-256 hex digest"),
        width=_integer(raw, "width", path),
        height=_integer(raw, "height", path),
    )
    _parse_utc(evidence.captured_at_utc, path)

    expected_image_digest = expected_image_sha256
    if HEX_64.fullmatch(expected_image_digest) is None:
        raise CaptureEvidenceError(
            "expected_image_sha256 must be a SHA-256 hex digest"
        )
    expected_marker = (
        f"{READY_PREFIX} scenario={expected_scenario} mode={EXPECTED_RUN_MODE}"
    )
    comparisons = {
        "rawFile": (evidence.raw_file, expected_raw_file),
        "scenario": (evidence.scenario, expected_scenario),
        "runMode": (evidence.run_mode, EXPECTED_RUN_MODE),
        "readinessMarker": (evidence.readiness_marker, expected_marker),
        "imageSha256": (evidence.image_sha256, expected_image_digest),
        "width": (evidence.width, expected_width),
        "height": (evidence.height, expected_height),
        "packageName": (evidence.package_name, EXPECTED_PACKAGE_NAME),
        "activityName": (evidence.activity_name, EXPECTED_ACTIVITY_NAME),
    }
    for field, (actual, expected) in comparisons.items():
        if actual != expected:
            raise CaptureEvidenceError(
                f"{path}: {field} mismatch; expected {expected!r}, "
                f"found {actual!r}"
            )
    return evidence


def require_same_capture_identity(
    baseline: CaptureEvidence,
    candidate: CaptureEvidence,
    candidate_path: Path,
) -> None:
    if candidate.session_identity != baseline.session_identity:
        labels = (
            "candidateSha",
            "apkSha256",
            "deviceSerial",
            "packageName",
            "activityName",
        )
        differing = [
            label
            for label, left, right in zip(
                labels,
                baseline.session_identity,
                candidate.session_identity,
            )
            if left != right
        ]
        raise CaptureEvidenceError(
            f"{candidate_path}: mixed capture identity for "
            f"{', '.join(differing)}"
        )
