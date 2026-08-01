#!/usr/bin/env python3
"""Write one scenario-bound screenshot sidecar after strict PNG validation."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import re
import tempfile
from pathlib import Path
from typing import Sequence

from screenshot_capture_evidence import (
    EXPECTED_ACTIVITY_NAME,
    EXPECTED_PACKAGE_NAME,
    EXPECTED_RUN_MODE,
    READY_PREFIX,
)
from verify_curated_screenshot_set import CuratedScreenshotError, _inspect_png

HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
SCENARIO_TOKEN = re.compile(r"[A-Z][A-Z0-9_]*")


class CaptureEvidenceWriteError(ValueError):
    """Raised when capture metadata cannot safely describe the supplied image."""


def _required_text(value: str, label: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise CaptureEvidenceWriteError(f"{label} must be non-blank")
    if any(ord(character) < 32 or ord(character) == 127 for character in normalized):
        raise CaptureEvidenceWriteError(f"{label} contains control characters")
    return normalized


def _atomic_write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as stream:
            temporary_path = Path(stream.name)
            json.dump(payload, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, path)
        temporary_path = None
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def write_capture_evidence(
    image_path: Path,
    *,
    scenario: str,
    settle_seconds: float,
    readiness_marker: str,
    candidate_sha: str,
    apk_sha256: str,
    device_serial: str,
    package_name: str,
    activity_name: str,
    run_mode: str,
    captured_at_utc: dt.datetime | None = None,
) -> Path:
    image_path = image_path.expanduser().resolve()
    scenario = _required_text(scenario, "scenario")
    readiness_marker = _required_text(readiness_marker, "readiness_marker")
    candidate_sha = _required_text(candidate_sha, "candidate_sha").lower()
    apk_sha256 = _required_text(apk_sha256, "apk_sha256").lower()
    device_serial = _required_text(device_serial, "device_serial")
    package_name = _required_text(package_name, "package_name")
    activity_name = _required_text(activity_name, "activity_name")
    run_mode = _required_text(run_mode, "run_mode")

    if SCENARIO_TOKEN.fullmatch(scenario) is None:
        raise CaptureEvidenceWriteError("scenario must be an uppercase scenario token")
    if HEX_40.fullmatch(candidate_sha) is None:
        raise CaptureEvidenceWriteError("candidate_sha must be a 40-character Git SHA")
    if HEX_64.fullmatch(apk_sha256) is None:
        raise CaptureEvidenceWriteError("apk_sha256 must be a SHA-256 hex digest")
    if not math.isfinite(settle_seconds) or not 0.0 <= settle_seconds <= 60.0:
        raise CaptureEvidenceWriteError("settle_seconds must be finite and between 0 and 60")
    if package_name != EXPECTED_PACKAGE_NAME:
        raise CaptureEvidenceWriteError(
            f"package_name must equal {EXPECTED_PACKAGE_NAME}"
        )
    if activity_name != EXPECTED_ACTIVITY_NAME:
        raise CaptureEvidenceWriteError(
            f"activity_name must equal {EXPECTED_ACTIVITY_NAME}"
        )
    if run_mode != EXPECTED_RUN_MODE:
        raise CaptureEvidenceWriteError(
            f"run_mode must equal {EXPECTED_RUN_MODE}"
        )
    expected_marker = (
        f"{READY_PREFIX} scenario={scenario} mode={EXPECTED_RUN_MODE}"
    )
    if readiness_marker != expected_marker:
        raise CaptureEvidenceWriteError(
            f"readiness_marker mismatch; expected {expected_marker!r}"
        )

    try:
        width, height, image_sha256 = _inspect_png(image_path)
    except CuratedScreenshotError as exc:
        raise CaptureEvidenceWriteError(str(exc)) from exc

    captured_at = captured_at_utc or dt.datetime.now(dt.timezone.utc)
    if captured_at.tzinfo is None or captured_at.utcoffset() != dt.timedelta(0):
        raise CaptureEvidenceWriteError("captured_at_utc must be timezone-aware UTC")

    sidecar = image_path.with_suffix(".capture.json")
    _atomic_write_json(
        sidecar,
        {
            "schemaVersion": 1,
            "rawFile": image_path.name,
            "scenario": scenario,
            "runMode": run_mode,
            "readinessMarker": readiness_marker,
            "candidateSha": candidate_sha,
            "apkSha256": apk_sha256,
            "deviceSerial": device_serial,
            "packageName": package_name,
            "activityName": activity_name,
            "settleSeconds": float(settle_seconds),
            "capturedAtUtc": captured_at.isoformat(),
            "imageSha256": image_sha256,
            "width": width,
            "height": height,
        },
    )
    return sidecar


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate one screenshot and write its capture evidence sidecar."
    )
    parser.add_argument("--image", required=True, type=Path)
    parser.add_argument("--scenario", required=True)
    parser.add_argument("--settle-seconds", required=True, type=float)
    parser.add_argument("--readiness-marker", required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--device-serial", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--activity-name", required=True)
    parser.add_argument("--run-mode", required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        sidecar = write_capture_evidence(
            args.image,
            scenario=args.scenario,
            settle_seconds=args.settle_seconds,
            readiness_marker=args.readiness_marker,
            candidate_sha=args.candidate_sha,
            apk_sha256=args.apk_sha256,
            device_serial=args.device_serial,
            package_name=args.package_name,
            activity_name=args.activity_name,
            run_mode=args.run_mode,
        )
    except CaptureEvidenceWriteError as exc:
        raise SystemExit(str(exc)) from exc
    print(sidecar)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
