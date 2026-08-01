#!/usr/bin/env python3
"""Validate a completed raw screenshot set and atomically publish session evidence."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Sequence

from screenshot_capture_evidence import (
    EXPECTED_ACTIVITY_NAME,
    EXPECTED_PACKAGE_NAME,
    CaptureEvidence,
    CaptureEvidenceError,
    load_capture_evidence,
    require_same_capture_identity,
)
from verify_curated_screenshot_set import CuratedScreenshotError, _inspect_png

HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
SCENARIO_TOKEN = re.compile(r"[A-Z][A-Z0-9_]*")
MAX_SIDECAR_BYTES = 64 * 1024


class CaptureSessionFinalizeError(ValueError):
    """Raised when a raw capture directory is incomplete or internally inconsistent."""


def _read_sidecar_identity(path: Path) -> tuple[str, str]:
    try:
        size = path.stat().st_size
    except FileNotFoundError as exc:
        raise CaptureSessionFinalizeError(f"Missing capture sidecar: {path}") from exc
    except OSError as exc:
        raise CaptureSessionFinalizeError(f"Could not inspect capture sidecar {path}: {exc}") from exc
    if size <= 0 or size > MAX_SIDECAR_BYTES:
        raise CaptureSessionFinalizeError(
            f"Capture sidecar has invalid size: {path} is {size} bytes"
        )
    try:
        raw: Any = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise CaptureSessionFinalizeError(f"Could not read capture sidecar {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise CaptureSessionFinalizeError(f"Invalid capture sidecar JSON {path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise CaptureSessionFinalizeError(f"Capture sidecar must be an object: {path}")
    raw_file = raw.get("rawFile")
    scenario = raw.get("scenario")
    if not isinstance(raw_file, str) or not raw_file.strip():
        raise CaptureSessionFinalizeError(f"{path}: rawFile must be non-blank")
    if not isinstance(scenario, str) or SCENARIO_TOKEN.fullmatch(scenario.strip()) is None:
        raise CaptureSessionFinalizeError(f"{path}: scenario must be an uppercase token")
    return raw_file.strip(), scenario.strip()


def _parse_capture_time(value: str, path: Path) -> dt.datetime:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise CaptureSessionFinalizeError(f"{path}: capturedAtUtc is invalid") from exc
    if parsed.tzinfo is None or parsed.utcoffset() != dt.timedelta(0):
        raise CaptureSessionFinalizeError(f"{path}: capturedAtUtc must be UTC")
    return parsed


def _atomic_write(path: Path, payload: dict[str, object]) -> None:
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
            json.dump(payload, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def finalize_capture_session(
    output_dir: Path,
    *,
    candidate_sha: str,
    origin_main_sha: str,
    apk_sha256: str,
    device_serial: str,
    package_name: str,
    activity_name: str,
    expected_count: int,
    captured_at_utc: dt.datetime | None = None,
) -> Path:
    output_dir = output_dir.expanduser().resolve()
    if not output_dir.is_dir():
        raise CaptureSessionFinalizeError(f"Capture output directory is missing: {output_dir}")
    candidate_sha = candidate_sha.strip().lower()
    origin_main_sha = origin_main_sha.strip().lower()
    apk_sha256 = apk_sha256.strip().lower()
    device_serial = device_serial.strip()
    package_name = package_name.strip()
    activity_name = activity_name.strip()
    if HEX_40.fullmatch(candidate_sha) is None:
        raise CaptureSessionFinalizeError("candidate_sha must be a 40-character Git SHA")
    if HEX_40.fullmatch(origin_main_sha) is None or origin_main_sha != candidate_sha:
        raise CaptureSessionFinalizeError("origin_main_sha must equal candidate_sha")
    if HEX_64.fullmatch(apk_sha256) is None:
        raise CaptureSessionFinalizeError("apk_sha256 must be a SHA-256 digest")
    if not device_serial:
        raise CaptureSessionFinalizeError("device_serial must be non-blank")
    if package_name != EXPECTED_PACKAGE_NAME:
        raise CaptureSessionFinalizeError(
            f"package_name must equal {EXPECTED_PACKAGE_NAME}"
        )
    if activity_name != EXPECTED_ACTIVITY_NAME:
        raise CaptureSessionFinalizeError(
            f"activity_name must equal {EXPECTED_ACTIVITY_NAME}"
        )
    if isinstance(expected_count, bool) or expected_count <= 0:
        raise CaptureSessionFinalizeError("expected_count must be positive")

    png_paths = sorted(output_dir.glob("*.png"))
    sidecar_paths = sorted(output_dir.glob("*.capture.json"))
    png_names = {path.name for path in png_paths}
    sidecar_png_names = {
        path.name.removesuffix(".capture.json") + ".png"
        for path in sidecar_paths
    }
    if png_names != sidecar_png_names:
        missing_sidecars = sorted(png_names - sidecar_png_names)
        orphan_sidecars = sorted(sidecar_png_names - png_names)
        raise CaptureSessionFinalizeError(
            "Raw screenshot and sidecar sets differ: "
            f"missing_sidecars={missing_sidecars}, orphan_sidecars={orphan_sidecars}"
        )
    if len(png_paths) != expected_count:
        raise CaptureSessionFinalizeError(
            f"Expected {expected_count} screenshots, found {len(png_paths)}"
        )

    baseline: CaptureEvidence | None = None
    scenarios: set[str] = set()
    latest_capture: dt.datetime | None = None
    for png_path in png_paths:
        sidecar_path = png_path.with_suffix(".capture.json")
        raw_file, scenario = _read_sidecar_identity(sidecar_path)
        if raw_file != png_path.name:
            raise CaptureSessionFinalizeError(
                f"{sidecar_path}: rawFile {raw_file!r} does not match {png_path.name!r}"
            )
        if scenario in scenarios:
            raise CaptureSessionFinalizeError(f"Duplicate capture scenario: {scenario}")
        scenarios.add(scenario)
        try:
            width, height, image_sha256 = _inspect_png(png_path)
            evidence = load_capture_evidence(
                sidecar_path,
                expected_raw_file=png_path.name,
                expected_scenario=scenario,
                expected_image_sha256=image_sha256,
                expected_width=width,
                expected_height=height,
            )
            if baseline is None:
                baseline = evidence
            else:
                require_same_capture_identity(baseline, evidence, sidecar_path)
        except (CuratedScreenshotError, CaptureEvidenceError) as exc:
            raise CaptureSessionFinalizeError(str(exc)) from exc
        expected_identity = (
            candidate_sha,
            apk_sha256,
            device_serial,
            package_name,
            activity_name,
        )
        if evidence.session_identity != expected_identity:
            raise CaptureSessionFinalizeError(
                f"{sidecar_path}: capture identity differs from requested session"
            )
        capture_time = _parse_capture_time(evidence.captured_at_utc, sidecar_path)
        latest_capture = max(latest_capture, capture_time) if latest_capture else capture_time

    if baseline is None or latest_capture is None:
        raise CaptureSessionFinalizeError("No screenshots were validated")
    session_time = captured_at_utc or dt.datetime.now(dt.timezone.utc)
    if session_time.tzinfo is None or session_time.utcoffset() != dt.timedelta(0):
        raise CaptureSessionFinalizeError("captured_at_utc must be timezone-aware UTC")
    if session_time < latest_capture:
        raise CaptureSessionFinalizeError(
            "Session timestamp cannot predate the latest screenshot capture"
        )

    destination = output_dir / "capture-session.json"
    _atomic_write(
        destination,
        {
            "schemaVersion": 1,
            "candidateSha": candidate_sha,
            "originMainSha": origin_main_sha,
            "apkSha256": apk_sha256,
            "deviceSerial": device_serial,
            "packageName": package_name,
            "activityName": activity_name,
            "capturedAtUtc": session_time.isoformat(),
            "screenshotCount": len(png_paths),
        },
    )
    return destination


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate raw screenshot evidence and publish capture-session.json."
    )
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--origin-main-sha", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--device-serial", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--activity-name", required=True)
    parser.add_argument("--expected-count", required=True, type=int)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        destination = finalize_capture_session(
            args.output_dir,
            candidate_sha=args.candidate_sha,
            origin_main_sha=args.origin_main_sha,
            apk_sha256=args.apk_sha256,
            device_serial=args.device_serial,
            package_name=args.package_name,
            activity_name=args.activity_name,
            expected_count=args.expected_count,
        )
    except CaptureSessionFinalizeError as exc:
        raise SystemExit(str(exc)) from exc
    print(destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
