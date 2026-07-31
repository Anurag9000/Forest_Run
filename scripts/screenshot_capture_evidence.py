#!/usr/bin/env python3
"""Validation of scenario-bound Forest Run screenshot capture evidence."""

from __future__ import annotations

import datetime as dt
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
EXPECTED_RUN_MODE = "SCREENSHOT_CAPTURE"
EXPECTED_PACKAGE_NAME = "com.anurag9000.forestrun.debug"
EXPECTED_ACTIVITY_NAME = "com.anurag9000.forestrun.MainActivity"
READY_PREFIX = "FOREST_RUN_SCENARIO_READY"
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")


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
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise CaptureEvidenceError(f"Missing capture evidence: {path}") from exc
    except OSError as exc:
        raise CaptureEvidenceError(f"Could not read capture evidence {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise CaptureEvidenceError(f"Invalid JSON in capture evidence {path}: {exc}") from exc
    if not isinstance(parsed, dict):
        raise CaptureEvidenceError(f"Capture evidence must be a JSON object: {path}")
    return parsed


def _string(source: dict[str, Any], key: str, path: Path) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        raise CaptureEvidenceError(f"{path}: {key} must be a non-blank string")
    return value.strip()


def _integer(source: dict[str, Any], key: str, path: Path) -> int:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise CaptureEvidenceError(f"{path}: {key} must be a positive integer")
    return value


def _number(source: dict[str, Any], key: str, path: Path) -> float:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise CaptureEvidenceError(f"{path}: {key} must be numeric")
    number = float(value)
    if not 0.0 <= number <= 60.0:
        raise CaptureEvidenceError(f"{path}: {key} must be between 0 and 60 seconds")
    return number


def _parse_utc(value: str, path: Path) -> None:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise CaptureEvidenceError(f"{path}: capturedAtUtc is not ISO-8601") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise CaptureEvidenceError(f"{path}: capturedAtUtc must include a timezone")


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
    if raw.get("schemaVersion") != SCHEMA_VERSION:
        raise CaptureEvidenceError(
            f"{path}: schemaVersion must equal {SCHEMA_VERSION}"
        )

    evidence = CaptureEvidence(
        raw_file=_string(raw, "rawFile", path),
        scenario=_string(raw, "scenario", path),
        run_mode=_string(raw, "runMode", path),
        readiness_marker=_string(raw, "readinessMarker", path),
        candidate_sha=_string(raw, "candidateSha", path).lower(),
        apk_sha256=_string(raw, "apkSha256", path).lower(),
        device_serial=_string(raw, "deviceSerial", path),
        package_name=_string(raw, "packageName", path),
        activity_name=_string(raw, "activityName", path),
        settle_seconds=_number(raw, "settleSeconds", path),
        captured_at_utc=_string(raw, "capturedAtUtc", path),
        image_sha256=_string(raw, "imageSha256", path).lower(),
        width=_integer(raw, "width", path),
        height=_integer(raw, "height", path),
    )
    _parse_utc(evidence.captured_at_utc, path)

    expected_marker = (
        f"{READY_PREFIX} scenario={expected_scenario} mode={EXPECTED_RUN_MODE}"
    )
    comparisons = {
        "rawFile": (evidence.raw_file, expected_raw_file),
        "scenario": (evidence.scenario, expected_scenario),
        "runMode": (evidence.run_mode, EXPECTED_RUN_MODE),
        "readinessMarker": (evidence.readiness_marker, expected_marker),
        "imageSha256": (evidence.image_sha256, expected_image_sha256.lower()),
        "width": (evidence.width, expected_width),
        "height": (evidence.height, expected_height),
        "packageName": (evidence.package_name, EXPECTED_PACKAGE_NAME),
        "activityName": (evidence.activity_name, EXPECTED_ACTIVITY_NAME),
    }
    for field, (actual, expected) in comparisons.items():
        if actual != expected:
            raise CaptureEvidenceError(
                f"{path}: {field} mismatch; expected {expected!r}, found {actual!r}"
            )

    if HEX_40.fullmatch(evidence.candidate_sha) is None:
        raise CaptureEvidenceError(f"{path}: candidateSha must be a 40-character Git SHA")
    if HEX_64.fullmatch(evidence.apk_sha256) is None:
        raise CaptureEvidenceError(f"{path}: apkSha256 must be a SHA-256 hex digest")
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
            f"{candidate_path}: mixed capture identity for {', '.join(differing)}"
        )
