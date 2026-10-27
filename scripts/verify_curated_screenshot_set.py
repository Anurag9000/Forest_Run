#!/usr/bin/env python3
"""Verify that curated store screenshots belong to one exact Forest Run candidate."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import math
import os
import re
import stat
import struct
import subprocess
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

from stable_file_reader import StableFileError, consume_regular_file
from strict_json import StrictJsonError, loads

from screenshot_capture_evidence import (
    EXPECTED_ACTIVITY_NAME,
    EXPECTED_PACKAGE_NAME,
    CaptureEvidence,
    CaptureEvidenceError,
    load_capture_evidence,
    parse_capture_evidence,
    require_same_capture_identity,
)

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCREENSHOT_ROOT = ROOT / "release" / "google-play" / "screenshots"
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
SCENARIO_TOKEN = re.compile(r"[A-Z][A-Z0-9_]*")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_SCREENSHOT_BYTES = 64 * 1024 * 1024
MAX_SCREENSHOT_PIXELS = 12_000_000
MAX_DECODED_SCREENSHOT_BYTES = 128 * 1024 * 1024
MAX_SESSION_BYTES = 64 * 1024
MAX_MANIFEST_BYTES = 256 * 1024
VALID_BIT_DEPTHS = {
    0: {1, 2, 4, 8, 16},
    2: {8, 16},
    3: {1, 2, 4, 8},
    4: {8, 16},
    6: {8, 16},
}
CHANNELS_BY_COLOR_TYPE = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


class CuratedScreenshotError(ValueError):
    """Raised when final screenshot evidence is incomplete or inconsistent."""


@dataclass(frozen=True)
class VerifiedCuratedSet:
    count: int
    candidate_sha: str
    apk_sha256: str
    device_serial: str
    package_name: str
    activity_name: str
    image_sha256: tuple[str, ...]


@dataclass(frozen=True)
class CaptureSession:
    candidate_sha: str
    origin_main_sha: str
    apk_sha256: str
    device_serial: str
    package_name: str
    activity_name: str
    captured_at_utc: dt.datetime
    screenshot_count: int


def _admit_directory(path: Path, label: str) -> Path:
    lexical = Path(os.path.abspath(os.fspath(path.expanduser())))
    current = Path(lexical.anchor)
    for part in lexical.parts[1:]:
        current = current / part
        try:
            metadata = current.lstat()
        except OSError as exc:
            raise CuratedScreenshotError(
                f"Could not inspect {label} path component {current}: {exc}"
            ) from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise CuratedScreenshotError(
                f"{label} path must not traverse a symbolic link: {current}"
            )
        if not stat.S_ISDIR(metadata.st_mode):
            raise CuratedScreenshotError(
                f"{label} path component is not a directory: {current}"
            )
    return lexical


def _read_regular_bytes(path: Path, *, maximum_bytes: int, label: str) -> bytes:
    try:
        content, _, _ = consume_regular_file(
            path,
            label,
            maximum_bytes,
            retain_bytes=True,
        )
    except StableFileError as exc:
        if "is missing or changed:" in str(exc):
            raise CuratedScreenshotError(
                f"Missing {label} or it changed while being read: {path}"
            ) from exc
        raise CuratedScreenshotError(str(exc)) from exc
    assert content is not None
    return content


def _parse_utc(value: Any, label: str) -> dt.datetime:
    if not isinstance(value, str) or not value.strip():
        raise CuratedScreenshotError(f"{label} must be a non-blank UTC timestamp")
    try:
        parsed = dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise CuratedScreenshotError(f"{label} is not ISO-8601") from exc
    if parsed.tzinfo is None or parsed.utcoffset() != dt.timedelta(0):
        raise CuratedScreenshotError(f"{label} must use UTC offset +00:00 or Z")
    return parsed


def _load_capture_session(
    path: Path,
    expected_candidate_sha: str,
    expected_count: int,
) -> CaptureSession:
    raw_bytes = _read_regular_bytes(
        path,
        maximum_bytes=MAX_SESSION_BYTES,
        label="capture session evidence",
    )
    try:
        raw = loads(
            raw_bytes,
            label=f"capture session {path}",
            maximum_bytes=MAX_SESSION_BYTES,
            require_object=True,
        )
    except StrictJsonError as exc:
        raise CuratedScreenshotError(f"Invalid capture session JSON {path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise CuratedScreenshotError(f"Capture session must be a JSON object: {path}")
    if type(raw.get("schemaVersion")) is not int or raw["schemaVersion"] != 1:
        raise CuratedScreenshotError(f"{path}: schemaVersion must equal 1")

    def required_string(key: str) -> str:
        value = raw.get(key)
        if not isinstance(value, str) or not value.strip():
            raise CuratedScreenshotError(f"{path}: {key} must be a non-blank string")
        return value.strip()

    candidate_sha = required_string("candidateSha")
    origin_main_sha = required_string("originMainSha")
    apk_sha256 = required_string("apkSha256")
    device_serial = required_string("deviceSerial")
    package_name = required_string("packageName")
    activity_name = required_string("activityName")
    screenshot_count = raw.get("screenshotCount")
    if (
        HEX_40.fullmatch(candidate_sha) is None
        or HEX_40.fullmatch(origin_main_sha) is None
    ):
        raise CuratedScreenshotError(f"{path}: candidate/origin SHA is invalid")
    if HEX_64.fullmatch(apk_sha256) is None:
        raise CuratedScreenshotError(f"{path}: apkSha256 is invalid")
    if isinstance(screenshot_count, bool) or not isinstance(screenshot_count, int) or screenshot_count <= 0:
        raise CuratedScreenshotError(f"{path}: screenshotCount must be a positive integer")

    session = CaptureSession(
        candidate_sha=candidate_sha,
        origin_main_sha=origin_main_sha,
        apk_sha256=apk_sha256,
        device_serial=device_serial,
        package_name=package_name,
        activity_name=activity_name,
        captured_at_utc=_parse_utc(raw.get("capturedAtUtc"), f"{path}: capturedAtUtc"),
        screenshot_count=screenshot_count,
    )
    expected_values = {
        "candidateSha": (session.candidate_sha, expected_candidate_sha),
        "originMainSha": (session.origin_main_sha, expected_candidate_sha),
        "screenshotCount": (session.screenshot_count, expected_count),
        "packageName": (session.package_name, EXPECTED_PACKAGE_NAME),
        "activityName": (session.activity_name, EXPECTED_ACTIVITY_NAME),
    }
    for field, (actual, expected) in expected_values.items():
        if actual != expected:
            raise CuratedScreenshotError(
                f"{path}: {field} mismatch; expected {expected!r}, found {actual!r}"
            )
    return session


def _load_manifest(path: Path) -> list[dict[str, Any]]:
    try:
        parsed = loads(
            _read_regular_bytes(
                path,
                maximum_bytes=MAX_MANIFEST_BYTES,
                label="curation manifest",
            ),
            label=f"curation manifest {path}",
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except StrictJsonError as exc:
        raise CuratedScreenshotError(
            f"Invalid curation manifest JSON {path}: {exc}"
        ) from exc
    if not isinstance(parsed, dict) or not isinstance(parsed.get("screenshots"), list):
        raise CuratedScreenshotError(f"{path}: screenshots must be an array")

    items = parsed["screenshots"]
    if not items:
        raise CuratedScreenshotError(f"{path}: screenshots must not be empty")
    required_strings = {
        "raw_file",
        "final_file",
        "scenario",
        "title",
        "purpose",
    }
    required = required_strings | {"order"}
    seen_final: set[str] = set()
    seen_raw: set[str] = set()
    seen_scenarios: set[str] = set()
    seen_titles: set[str] = set()
    normalized: list[dict[str, Any]] = []
    for index, item in enumerate(items):
        label = f"{path}: screenshots[{index}]"
        if not isinstance(item, dict):
            raise CuratedScreenshotError(f"{label} must be an object")
        missing = required.difference(item)
        if missing:
            raise CuratedScreenshotError(
                f"{label} missing {', '.join(sorted(missing))}"
            )
        for key in required_strings:
            if not isinstance(item[key], str) or not item[key].strip():
                raise CuratedScreenshotError(f"{label}.{key} must be non-blank")
        if isinstance(item["order"], bool) or not isinstance(item["order"], int):
            raise CuratedScreenshotError(f"{label}.order must be an integer")
        expected_order = index + 1
        if item["order"] != expected_order:
            raise CuratedScreenshotError(
                f"{label}.order must equal {expected_order}; found {item['order']}"
            )

        final_file = item["final_file"].strip()
        raw_file = item["raw_file"].strip()
        scenario = item["scenario"].strip()
        title = item["title"].strip()
        if Path(final_file).name != final_file or not final_file.lower().endswith(".png"):
            raise CuratedScreenshotError(
                f"{path}: invalid final_file {final_file!r}"
            )
        if Path(raw_file).name != raw_file or not raw_file.lower().endswith(".png"):
            raise CuratedScreenshotError(
                f"{path}: invalid raw_file {raw_file!r}"
            )
        if SCENARIO_TOKEN.fullmatch(scenario) is None:
            raise CuratedScreenshotError(
                f"{path}: invalid scenario token {scenario!r}"
            )

        final_key = final_file.casefold()
        raw_key = raw_file.casefold()
        scenario_key = scenario.casefold()
        title_key = title.casefold()
        if final_key in seen_final:
            raise CuratedScreenshotError(
                f"{path}: duplicate final_file {final_file}"
            )
        if raw_key in seen_raw:
            raise CuratedScreenshotError(
                f"{path}: duplicate raw_file {raw_file}"
            )
        if scenario_key in seen_scenarios:
            raise CuratedScreenshotError(
                f"{path}: duplicate scenario {scenario}"
            )
        if title_key in seen_titles:
            raise CuratedScreenshotError(f"{path}: duplicate title {title}")
        seen_final.add(final_key)
        seen_raw.add(raw_key)
        seen_scenarios.add(scenario_key)
        seen_titles.add(title_key)
        normalized.append(
            {
                **item,
                "raw_file": raw_file,
                "final_file": final_file,
                "scenario": scenario,
                "title": title,
                "purpose": item["purpose"].strip(),
            }
        )
    return normalized


def _read_png_content(path: Path) -> bytes:
    return _read_regular_bytes(
        path,
        maximum_bytes=MAX_SCREENSHOT_BYTES,
        label="curated screenshot",
    )


def _inspect_png(path: Path) -> tuple[int, int, str]:
    content = _read_png_content(path)
    if not content.startswith(PNG_SIGNATURE):
        raise CuratedScreenshotError(
            f"Curated screenshot is not a PNG: {path}"
        )

    offset = len(PNG_SIGNATURE)
    width = 0
    height = 0
    bit_depth = 0
    color_type = -1
    interlace = -1
    saw_ihdr = False
    saw_plte = False
    palette_entries = 0
    saw_trns = False
    saw_idat = False
    idat_closed = False
    saw_iend = False
    idat_parts: list[bytes] = []

    while offset < len(content):
        if len(content) - offset < 12:
            raise CuratedScreenshotError(
                f"Curated screenshot has a truncated PNG chunk header: {path}"
            )
        length = struct.unpack(">I", content[offset : offset + 4])[0]
        chunk_type = content[offset + 4 : offset + 8]
        if len(chunk_type) != 4 or any(
            not (65 <= value <= 90 or 97 <= value <= 122)
            for value in chunk_type
        ):
            raise CuratedScreenshotError(
                f"Curated screenshot has an invalid PNG chunk type: {path}"
            )
        if 97 <= chunk_type[2] <= 122:
            raise CuratedScreenshotError(
                f"Curated screenshot has a lowercase reserved PNG chunk type bit: {path}"
            )
        chunk_end = offset + 12 + length
        if chunk_end > len(content):
            raise CuratedScreenshotError(
                f"Curated screenshot has a truncated PNG chunk: {path}"
            )
        data_start = offset + 8
        data_end = data_start + length
        chunk_data = content[data_start:data_end]
        expected_crc = struct.unpack(">I", content[data_end : data_end + 4])[0]
        actual_crc = zlib.crc32(chunk_type)
        actual_crc = zlib.crc32(chunk_data, actual_crc) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise CuratedScreenshotError(
                f"Curated screenshot has a PNG CRC mismatch: {path}"
            )

        if not saw_ihdr and chunk_type != b"IHDR":
            raise CuratedScreenshotError(
                f"Curated screenshot PNG does not start with IHDR: {path}"
            )
        if chunk_type == b"IHDR":
            if saw_ihdr or length != 13:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an invalid PNG IHDR: {path}"
                )
            (
                width,
                height,
                bit_depth,
                color_type,
                compression,
                filtering,
                interlace,
            ) = struct.unpack(">IIBBBBB", chunk_data)
            if (
                width <= 0
                or height <= 0
                or width * height > MAX_SCREENSHOT_PIXELS
                or color_type not in VALID_BIT_DEPTHS
                or bit_depth not in VALID_BIT_DEPTHS[color_type]
                or compression != 0
                or filtering != 0
                or interlace not in {0, 1}
            ):
                raise CuratedScreenshotError(
                    f"Curated screenshot has invalid PNG IHDR values: {path}"
                )
            saw_ihdr = True
        elif chunk_type == b"PLTE":
            if (
                saw_idat
                or saw_plte
                or saw_trns
                or color_type in {0, 4}
                or length == 0
                or length > 768
                or length % 3 != 0
                or (color_type == 3 and length // 3 > (1 << bit_depth))
            ):
                raise CuratedScreenshotError(
                    f"Curated screenshot has an invalid PNG palette: {path}"
                )
            saw_plte = True
            palette_entries = length // 3
        elif chunk_type == b"tRNS":
            if saw_trns or saw_idat:
                raise CuratedScreenshotError(
                    f"Curated screenshot has a duplicate or out-of-order PNG tRNS chunk: {path}"
                )
            if color_type == 0:
                valid_transparency = length == 2
            elif color_type == 2:
                valid_transparency = length == 6
            elif color_type == 3:
                valid_transparency = saw_plte and 0 < length <= palette_entries
            else:
                valid_transparency = False
            if not valid_transparency:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an invalid PNG tRNS chunk: {path}"
                )
            saw_trns = True
        elif chunk_type == b"IDAT":
            if not saw_ihdr or saw_iend or idat_closed:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an out-of-order PNG IDAT: {path}"
                )
            if color_type == 3 and not saw_plte:
                raise CuratedScreenshotError(
                    f"Curated indexed PNG is missing a palette before IDAT: {path}"
                )
            saw_idat = True
            idat_parts.append(chunk_data)
        elif chunk_type == b"IEND":
            if length != 0 or not saw_ihdr or not saw_idat or saw_iend:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an invalid PNG IEND: {path}"
                )
            saw_iend = True
            offset = chunk_end
            if offset != len(content):
                raise CuratedScreenshotError(
                    f"Curated screenshot has trailing bytes after PNG IEND: {path}"
                )
            break
        else:
            if saw_idat:
                idat_closed = True
            if chunk_type[0] & 0x20 == 0:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an unknown critical PNG chunk: {path}"
                )

        offset = chunk_end

    if not saw_ihdr or not saw_idat or not saw_iend:
        raise CuratedScreenshotError(
            f"Curated screenshot is missing required PNG chunks: {path}"
        )
    decompressor = zlib.decompressobj()
    try:
        decoded = decompressor.decompress(
            b"".join(idat_parts),
            MAX_DECODED_SCREENSHOT_BYTES + 1,
        )
        if decompressor.unconsumed_tail or len(decoded) > MAX_DECODED_SCREENSHOT_BYTES:
            raise CuratedScreenshotError(
                f"Curated screenshot expands beyond the PNG safety limit: {path}"
            )
        decoded += decompressor.flush()
    except zlib.error as exc:
        raise CuratedScreenshotError(
            f"Curated screenshot has invalid compressed PNG image data: {path}: {exc}"
        ) from exc
    if (
        not decompressor.eof
        or decompressor.unused_data
        or len(decoded) > MAX_DECODED_SCREENSHOT_BYTES
    ):
        raise CuratedScreenshotError(
            f"Curated screenshot has an incomplete or trailing PNG data stream: {path}"
        )
    if not decoded:
        raise CuratedScreenshotError(
            f"Curated screenshot has empty PNG image data: {path}"
        )
    channels = CHANNELS_BY_COLOR_TYPE[color_type]
    if interlace == 0:
        passes = ((0, 0, 1, 1, width, height),)
    else:
        # Adam7 pass start x/y and step x/y, in specification order.
        passes = (
            (0, 0, 8, 8),
            (4, 0, 8, 8),
            (0, 4, 4, 8),
            (2, 0, 4, 4),
            (0, 2, 2, 4),
            (1, 0, 2, 2),
            (0, 1, 1, 2),
        )

    scanline_offset = 0
    expected_decoded_bytes = 0
    scanline_layout: list[tuple[int, int, int, int]] = []
    for pass_spec in passes:
        if interlace == 0:
            start_x, start_y, step_x, step_y, pass_width, pass_height = pass_spec
        else:
            start_x, start_y, step_x, step_y = pass_spec
            pass_width = max(0, (width - start_x + step_x - 1) // step_x)
            pass_height = max(0, (height - start_y + step_y - 1) // step_y)
        if pass_width == 0 or pass_height == 0:
            continue
        row_bytes = math.ceil(pass_width * channels * bit_depth / 8)
        row_size = row_bytes + 1
        pass_decoded_bytes = pass_height * row_size
        expected_decoded_bytes += pass_decoded_bytes
        if expected_decoded_bytes > MAX_DECODED_SCREENSHOT_BYTES:
            raise CuratedScreenshotError(
                f"Curated screenshot scanlines exceed the PNG safety limit: {path}"
            )
        scanline_layout.append((pass_height, row_size, row_bytes, pass_width))
    if len(decoded) != expected_decoded_bytes:
        raise CuratedScreenshotError(
            f"Curated screenshot PNG scanline size mismatch: {path}"
        )
    for pass_height, row_size, row_bytes, pass_width in scanline_layout:
        previous_row = bytearray(row_bytes)
        for _ in range(pass_height):
            filter_type = decoded[scanline_offset]
            if filter_type > 4:
                raise CuratedScreenshotError(
                    f"Curated screenshot has an invalid PNG scanline filter: {path}"
                )
            if color_type == 3:
                filtered_row = decoded[scanline_offset + 1 : scanline_offset + row_size]
                reconstructed_row = bytearray(row_bytes)
                filter_bpp = max(1, (channels * bit_depth + 7) // 8)
                for index, filtered_byte in enumerate(filtered_row):
                    left = (
                        reconstructed_row[index - filter_bpp]
                        if index >= filter_bpp
                        else 0
                    )
                    above = previous_row[index]
                    upper_left = (
                        previous_row[index - filter_bpp]
                        if index >= filter_bpp
                        else 0
                    )
                    if filter_type == 0:
                        predictor = 0
                    elif filter_type == 1:
                        predictor = left
                    elif filter_type == 2:
                        predictor = above
                    elif filter_type == 3:
                        predictor = (left + above) // 2
                    else:
                        paeth_base = left + above - upper_left
                        distances = (
                            abs(paeth_base - left),
                            abs(paeth_base - above),
                            abs(paeth_base - upper_left),
                        )
                        predictor = (left, above, upper_left)[distances.index(min(distances))]
                    reconstructed_row[index] = (filtered_byte + predictor) & 0xFF

                palette_index_mask = (1 << bit_depth) - 1
                for pixel_index in range(pass_width):
                    bit_offset = pixel_index * bit_depth
                    byte_value = reconstructed_row[bit_offset // 8]
                    shift = 8 - bit_depth - (bit_offset % 8)
                    palette_index = (byte_value >> shift) & palette_index_mask
                    if palette_index >= palette_entries:
                        raise CuratedScreenshotError(
                            f"Curated indexed PNG refers to a missing palette entry: {path}"
                        )
                previous_row = reconstructed_row
            scanline_offset += row_size

    if width <= height or width < 800 or height < 480:
        raise CuratedScreenshotError(
            f"Curated screenshot has invalid landscape dimensions: "
            f"{path} is {width}x{height}"
        )
    return width, height, hashlib.sha256(content).hexdigest()


def current_candidate_sha(root: Path = ROOT) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise CuratedScreenshotError(
            "Could not resolve current candidate SHA: "
            + (result.stderr or result.stdout).strip()
        )
    value = result.stdout.strip().lower()
    if HEX_40.fullmatch(value) is None:
        raise CuratedScreenshotError(
            f"git returned an invalid candidate SHA: {value!r}"
        )
    return value


def verify_curated_set(
    screenshot_root: Path,
    expected_candidate_sha: str,
) -> VerifiedCuratedSet:
    if HEX_40.fullmatch(expected_candidate_sha) is None:
        raise CuratedScreenshotError(
            "expected candidate SHA must be 40 hexadecimal characters"
        )

    screenshot_root = _admit_directory(screenshot_root, "Screenshot root")
    final_dir = _admit_directory(screenshot_root / "final", "Final screenshot directory")
    raw_dir = _admit_directory(screenshot_root / "raw", "Raw screenshot evidence directory")
    manifest_path = screenshot_root / "curation_manifest.json"
    items = _load_manifest(manifest_path)
    session = _load_capture_session(
        raw_dir / "capture-session.json",
        expected_candidate_sha,
        len(items),
    )
    expected_png_names = {item["final_file"] for item in items}
    expected_sidecar_names = {
        Path(item["final_file"]).with_suffix(".capture.json").name
        for item in items
    }
    actual_png_names = {path.name for path in final_dir.glob("*.png")}
    actual_sidecar_names = {path.name for path in final_dir.glob("*.capture.json")}
    missing = sorted(expected_png_names - actual_png_names)
    extras = sorted(actual_png_names - expected_png_names)
    missing_sidecars = sorted(expected_sidecar_names - actual_sidecar_names)
    extra_sidecars = sorted(actual_sidecar_names - expected_sidecar_names)
    if missing or extras or missing_sidecars or extra_sidecars:
        details: list[str] = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if extras:
            details.append("extra=" + ",".join(extras))
        if missing_sidecars:
            details.append(
                "missing_sidecars=" + ",".join(missing_sidecars)
            )
        if extra_sidecars:
            details.append("extra_sidecars=" + ",".join(extra_sidecars))
        raise CuratedScreenshotError(
            "Curated evidence set does not match manifest: "
            + " ".join(details)
        )

    baseline: CaptureEvidence | None = None
    image_hashes: list[str] = []
    capture_times: list[dt.datetime] = []
    for item in items:
        png_path = final_dir / item["final_file"]
        width, height, image_sha256 = _inspect_png(png_path)
        sidecar = png_path.with_suffix(".capture.json")
        try:
            sidecar_bytes = _read_regular_bytes(
                sidecar,
                maximum_bytes=64 * 1024,
                label="capture evidence sidecar",
            )
            sidecar_payload = loads(
                sidecar_bytes,
                label=f"capture evidence sidecar {sidecar}",
                maximum_bytes=64 * 1024,
                require_object=True,
            )
            evidence = parse_capture_evidence(
                sidecar_payload,
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
        except (CaptureEvidenceError, StrictJsonError) as exc:
            raise CuratedScreenshotError(str(exc)) from exc
        if evidence.candidate_sha != expected_candidate_sha:
            raise CuratedScreenshotError(
                f"{sidecar}: screenshot candidate {evidence.candidate_sha} "
                f"does not match release candidate {expected_candidate_sha}"
            )
        capture_times.append(_parse_utc(evidence.captured_at_utc, f"{sidecar}: capturedAtUtc"))
        image_hashes.append(image_sha256)

    if baseline is None:
        raise CuratedScreenshotError("No curated screenshots were verified")
    session_identity = (
        session.candidate_sha,
        session.apk_sha256,
        session.device_serial,
        session.package_name,
        session.activity_name,
    )
    if session_identity != baseline.session_identity:
        raise CuratedScreenshotError(
            "Capture session identity does not match curated screenshot sidecars"
        )
    if capture_times and session.captured_at_utc < max(capture_times):
        raise CuratedScreenshotError(
            "Capture session timestamp predates one or more screenshot captures"
        )
    if len(image_hashes) != len(set(image_hashes)):
        raise CuratedScreenshotError(
            "Curated screenshot set contains exact duplicate image hashes"
        )
    return VerifiedCuratedSet(
        count=len(image_hashes),
        candidate_sha=baseline.candidate_sha,
        apk_sha256=baseline.apk_sha256,
        device_serial=baseline.device_serial,
        package_name=baseline.package_name,
        activity_name=baseline.activity_name,
        image_sha256=tuple(image_hashes),
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Verify final Forest Run screenshots against capture sidecars "
            "and candidate SHA."
        )
    )
    parser.add_argument(
        "--screenshot-root",
        type=Path,
        default=DEFAULT_SCREENSHOT_ROOT,
    )
    parser.add_argument(
        "--candidate-sha",
        help="Expected 40-character Git SHA; defaults to the current checkout HEAD.",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        candidate_sha = args.candidate_sha or current_candidate_sha()
        result = verify_curated_set(args.screenshot_root, candidate_sha)
    except CuratedScreenshotError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(
        "Verified "
        f"{result.count} screenshot(s) for candidate {result.candidate_sha}, "
        f"APK {result.apk_sha256}, device {result.device_serial}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
