#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import struct
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, NoReturn, Sequence

from screenshot_capture_evidence import (
    CaptureEvidence,
    CaptureEvidenceError,
    load_capture_evidence,
    require_same_capture_identity,
)
from verify_curated_screenshot_set import _load_manifest

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCREENSHOT_ROOT = ROOT / "release/google-play/screenshots"
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")


class RawScreenshotSetError(ValueError):
    """Raised when a raw capture session is incomplete or internally inconsistent."""


@dataclass(frozen=True)
class VerifiedRawScreenshotSet:
    count: int
    candidate_sha: str
    origin_main_sha: str
    apk_sha256: str
    device_serial: str
    package_name: str
    activity_name: str
    image_sha256: tuple[str, ...]


def _fail(message: str) -> NoReturn:
    raise RawScreenshotSetError(message)


def _read_object(path: Path) -> dict[str, Any]:
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        _fail(f"Missing raw screenshot session evidence: {path}")
    except OSError as error:
        _fail(f"Could not read raw screenshot session evidence {path}: {error}")
    except json.JSONDecodeError as error:
        _fail(f"Invalid JSON in raw screenshot session evidence {path}: {error}")
    if not isinstance(parsed, dict):
        _fail(f"Raw screenshot session evidence must be a JSON object: {path}")
    return parsed


def _required_string(source: dict[str, Any], key: str, path: Path) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        _fail(f"{path}: {key} must be a non-blank string")
    return value.strip()


def _parse_utc(value: str, path: Path, field: str) -> dt.datetime:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        _fail(f"{path}: {field} is not ISO-8601")
    if parsed.tzinfo is None or parsed.utcoffset() != dt.timedelta(0):
        _fail(f"{path}: {field} must use UTC offset +00:00 or Z")
    return parsed


def _inspect_png(path: Path) -> tuple[int, int, str]:
    try:
        content = path.read_bytes()
    except FileNotFoundError as error:
        _fail(f"Missing raw screenshot: {path}")
    except OSError as error:
        _fail(f"Could not read raw screenshot {path}: {error}")
    if len(content) < 24 or content[:8] != b"\x89PNG\r\n\x1a\n":
        _fail(f"Raw screenshot is not a PNG: {path}")
    width, height = struct.unpack(">II", content[16:24])
    if width <= height or width < 800 or height < 480:
        _fail(
            f"Raw screenshot has invalid landscape dimensions: "
            f"{path} is {width}x{height}"
        )
    return width, height, hashlib.sha256(content).hexdigest()


def verify_raw_screenshot_set(
    raw_dir: Path,
    manifest_path: Path,
    expected_candidate_sha: str,
) -> VerifiedRawScreenshotSet:
    expected_candidate_sha = expected_candidate_sha.strip().lower()
    if HEX_40.fullmatch(expected_candidate_sha) is None:
        _fail("expected candidate SHA must be 40 hexadecimal characters")
    if not raw_dir.is_dir():
        _fail(f"Raw screenshot directory does not exist: {raw_dir}")

    try:
        items = _load_manifest(manifest_path)
    except ValueError as error:
        _fail(str(error))

    expected_pngs = {item["raw_file"] for item in items}
    expected_sidecars = {
        Path(item["raw_file"]).with_suffix(".capture.json").name
        for item in items
    }
    expected_entries = expected_pngs | expected_sidecars | {"capture-session.json"}
    actual_entries = {path.name for path in raw_dir.iterdir()}
    missing = sorted(expected_entries - actual_entries)
    extras = sorted(actual_entries - expected_entries)
    if missing or extras:
        details: list[str] = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if extras:
            details.append("extra=" + ",".join(extras))
        _fail("Raw screenshot set does not match manifest: " + " ".join(details))

    baseline: CaptureEvidence | None = None
    captures: list[CaptureEvidence] = []
    image_hashes: list[str] = []
    capture_times: list[dt.datetime] = []
    for item in items:
        png_path = raw_dir / item["raw_file"]
        width, height, image_sha256 = _inspect_png(png_path)
        sidecar = png_path.with_suffix(".capture.json")
        try:
            evidence = load_capture_evidence(
                sidecar,
                expected_raw_file=item["raw_file"],
                expected_scenario=item["scenario"],
                expected_image_sha256=image_sha256,
                expected_width=width,
                expected_height=height,
            )
            if baseline is None:
                baseline = evidence
            else:
                require_same_capture_identity(baseline, evidence, sidecar)
        except CaptureEvidenceError as error:
            _fail(str(error))
        if evidence.candidate_sha != expected_candidate_sha:
            _fail(
                f"{sidecar}: screenshot candidate {evidence.candidate_sha} "
                f"does not match expected candidate {expected_candidate_sha}"
            )
        captures.append(evidence)
        image_hashes.append(image_sha256)
        capture_times.append(
            _parse_utc(evidence.captured_at_utc, sidecar, "capturedAtUtc")
        )

    if baseline is None:
        _fail("No raw screenshots were verified")
    if len(image_hashes) != len(set(image_hashes)):
        _fail("Raw screenshot set contains exact duplicate image hashes")

    session_path = raw_dir / "capture-session.json"
    session = _read_object(session_path)
    if session.get("schemaVersion") != 1:
        _fail(f"{session_path}: schemaVersion must equal 1")
    session_candidate = _required_string(
        session, "candidateSha", session_path
    ).lower()
    origin_main_sha = _required_string(
        session, "originMainSha", session_path
    ).lower()
    apk_sha256 = _required_string(session, "apkSha256", session_path).lower()
    if HEX_40.fullmatch(session_candidate) is None:
        _fail(f"{session_path}: candidateSha must be a 40-character Git SHA")
    if HEX_40.fullmatch(origin_main_sha) is None:
        _fail(f"{session_path}: originMainSha must be a 40-character Git SHA")
    if HEX_64.fullmatch(apk_sha256) is None:
        _fail(f"{session_path}: apkSha256 must be a SHA-256 hex digest")
    if session_candidate != expected_candidate_sha or origin_main_sha != expected_candidate_sha:
        _fail(
            f"{session_path}: candidate/origin mismatch; expected "
            f"{expected_candidate_sha}, candidate={session_candidate}, "
            f"origin/main={origin_main_sha}"
        )

    session_count = session.get("screenshotCount")
    if isinstance(session_count, bool) or not isinstance(session_count, int):
        _fail(f"{session_path}: screenshotCount must be an integer")
    if session_count != len(items):
        _fail(
            f"{session_path}: screenshotCount must equal {len(items)}; "
            f"found {session_count}"
        )

    session_time = _parse_utc(
        _required_string(session, "capturedAtUtc", session_path),
        session_path,
        "capturedAtUtc",
    )
    if capture_times and session_time < max(capture_times):
        _fail(
            f"{session_path}: session timestamp predates one or more screenshot captures"
        )

    session_comparisons = {
        "apkSha256": (apk_sha256, baseline.apk_sha256),
        "deviceSerial": (
            _required_string(session, "deviceSerial", session_path),
            baseline.device_serial,
        ),
        "packageName": (
            _required_string(session, "packageName", session_path),
            baseline.package_name,
        ),
        "activityName": (
            _required_string(session, "activityName", session_path),
            baseline.activity_name,
        ),
    }
    for field, (actual, expected) in session_comparisons.items():
        if actual != expected:
            _fail(
                f"{session_path}: {field} mismatch; expected {expected!r}, "
                f"found {actual!r}"
            )

    return VerifiedRawScreenshotSet(
        count=len(captures),
        candidate_sha=session_candidate,
        origin_main_sha=origin_main_sha,
        apk_sha256=apk_sha256,
        device_serial=baseline.device_serial,
        package_name=baseline.package_name,
        activity_name=baseline.activity_name,
        image_sha256=tuple(image_hashes),
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify one complete Forest Run raw screenshot capture session."
    )
    parser.add_argument(
        "--raw-dir",
        type=Path,
        default=DEFAULT_SCREENSHOT_ROOT / "raw",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_SCREENSHOT_ROOT / "curation_manifest.json",
    )
    parser.add_argument("--candidate-sha", required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = verify_raw_screenshot_set(
            args.raw_dir,
            args.manifest,
            args.candidate_sha,
        )
    except RawScreenshotSetError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print(
        "Verified "
        f"{result.count} raw screenshot(s) for candidate "
        f"{result.candidate_sha}, APK {result.apk_sha256}, "
        f"device {result.device_serial}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
