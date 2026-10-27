#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import io
import json
import os
import re
import stat
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from strict_json import StrictJsonError, load_file, loads

from screenshot_capture_evidence import (
    CaptureEvidence,
    CaptureEvidenceError,
    MAX_CAPTURE_EVIDENCE_BYTES,
    load_capture_evidence,
    parse_capture_evidence,
    require_same_capture_identity,
)
from stable_file_reader import (
    StableFileError,
    atomic_publish_directory,
    atomic_write_bytes,
    ensure_directory_exists,
    open_directory_descriptor,
    remove_directory_at,
)

ROOT_DIR = Path(__file__).resolve().parent.parent
SCREENSHOT_DIR = ROOT_DIR / "release" / "google-play" / "screenshots"
RAW_DIR = SCREENSHOT_DIR / "raw"
FINAL_DIR = SCREENSHOT_DIR / "final"
STAGING_DIR = SCREENSHOT_DIR / ".final-staging"
BACKUP_DIR = SCREENSHOT_DIR / ".final-backup"
MANIFEST_PATH = SCREENSHOT_DIR / "curation_manifest.json"
SUMMARY_PATH = SCREENSHOT_DIR / "CURATED_SET.md"

MIN_WIDTH = 800
MIN_HEIGHT = 480
MAX_WIDTH = 7_680
MAX_HEIGHT = 4_320
MAX_FILE_BYTES = 50 * 1024 * 1024
MAX_MANIFEST_BYTES = 256 * 1024
MIN_LUMA_STDDEV = 6.0
NEAR_DUPLICATE_HAMMING_DISTANCE = 3
SYSTEM_BAR_BAND_PX = 18
HEX_64 = re.compile(r"[0-9a-fA-F]{64}")
SCENARIO_TOKEN = re.compile(r"[A-Z][A-Z0-9_]*")

_IMAGE = None
_IMAGE_STAT = None
_UNIDENTIFIED_IMAGE_ERROR = None


@dataclass(frozen=True)
class ImageFacts:
    width: int
    height: int
    sha256: str
    perceptual_hash: int
    luma_stddev: float


def _load_pillow():
    global _IMAGE, _IMAGE_STAT, _UNIDENTIFIED_IMAGE_ERROR
    if _IMAGE is not None:
        return _IMAGE, _IMAGE_STAT, _UNIDENTIFIED_IMAGE_ERROR
    try:
        from PIL import Image, ImageStat, UnidentifiedImageError
    except ImportError as exc:
        raise SystemExit(
            "Pillow is required. Install scripts/requirements.txt before curating screenshots."
        ) from exc
    Image.MAX_IMAGE_PIXELS = MAX_WIDTH * MAX_HEIGHT
    _IMAGE = Image
    _IMAGE_STAT = ImageStat
    _UNIDENTIFIED_IMAGE_ERROR = UnidentifiedImageError
    return _IMAGE, _IMAGE_STAT, _UNIDENTIFIED_IMAGE_ERROR


def load_manifest() -> dict[str, Any]:
    try:
        return load_file(
            MANIFEST_PATH,
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except StrictJsonError as exc:
        raise SystemExit(f"Invalid curation manifest {MANIFEST_PATH}: {exc}") from exc

def validate_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    items = manifest.get("screenshots")
    if not isinstance(items, list) or not items:
        raise SystemExit("Manifest must define a non-empty screenshots list")

    required = {"order", "raw_file", "final_file", "scenario", "title", "purpose"}
    seen_raw: set[str] = set()
    seen_final: set[str] = set()
    seen_scenarios: set[str] = set()
    seen_titles: set[str] = set()
    normalized: list[dict[str, Any]] = []

    for index, item in enumerate(items):
        label = f"Manifest screenshots[{index}]"
        if not isinstance(item, dict):
            raise SystemExit(f"{label} must be an object")
        missing = sorted(required - item.keys())
        if missing:
            raise SystemExit(f"{label} missing keys {missing}")

        expected_order = index + 1
        order = item["order"]
        if isinstance(order, bool) or not isinstance(order, int) or order != expected_order:
            raise SystemExit(
                f"{label}.order must equal {expected_order}; found {order!r}"
            )

        values: dict[str, str] = {}
        for key in ("raw_file", "final_file", "scenario", "title", "purpose"):
            value = item[key]
            if not isinstance(value, str) or not value.strip():
                raise SystemExit(f"{label}.{key} must be a non-blank string")
            values[key] = value.strip()

        for key in ("raw_file", "final_file"):
            filename = values[key]
            if Path(filename).name != filename or Path(filename).suffix.lower() != ".png":
                raise SystemExit(f"{label}.{key} must be a plain PNG filename: {filename!r}")
        if SCENARIO_TOKEN.fullmatch(values["scenario"]) is None:
            raise SystemExit(
                f"{label}.scenario must be an uppercase scenario token: "
                f"{values['scenario']!r}"
            )

        allow_dark_bars = item.get("allow_dark_bars", False)
        if not isinstance(allow_dark_bars, bool):
            raise SystemExit(f"{label}.allow_dark_bars must be boolean when present")

        expected_hash = item.get("sha256")
        if expected_hash is not None:
            if not isinstance(expected_hash, str) or HEX_64.fullmatch(expected_hash) is None:
                raise SystemExit(f"{label}.sha256 must be a SHA-256 hex digest")
            expected_hash = expected_hash.lower()

        duplicate_fields = (
            ("raw_file", values["raw_file"].casefold(), seen_raw),
            ("final_file", values["final_file"].casefold(), seen_final),
            ("scenario", values["scenario"].casefold(), seen_scenarios),
            ("title", values["title"].casefold(), seen_titles),
        )
        for field, key, seen in duplicate_fields:
            if key in seen:
                raise SystemExit(f"Duplicate screenshot {field}: {values[field]}")
            seen.add(key)

        normalized.append(
            {
                **item,
                **values,
                "order": order,
                "allow_dark_bars": allow_dark_bars,
                **({"sha256": expected_hash} if expected_hash is not None else {}),
            }
        )
    return normalized


def difference_hash(image) -> int:
    Image, _, _ = _load_pillow()
    reduced = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = reduced.tobytes()
    value = 0
    for row in range(8):
        row_start = row * 9
        for column in range(8):
            value = (value << 1) | int(
                pixels[row_start + column] > pixels[row_start + column + 1]
            )
    return value


def hamming_distance(left: int, right: int) -> int:
    return (left ^ right).bit_count()


def band_is_uniform_dark(image, top: bool) -> bool:
    _, ImageStat, _ = _load_pillow()
    band_height = min(SYSTEM_BAR_BAND_PX, max(1, image.height // 20))
    box = (
        (0, 0, image.width, band_height)
        if top
        else (0, image.height - band_height, image.width, image.height)
    )
    band = image.convert("RGB").crop(box)
    stat = ImageStat.Stat(band)
    means = stat.mean
    extrema = band.getextrema()
    channel_ranges = [maximum - minimum for minimum, maximum in extrema]
    return max(means) < 18 and max(channel_ranges) < 8


def _read_regular_bytes(path: Path, *, maximum_bytes: int, label: str) -> bytes:
    lexical = Path(os.path.abspath(os.fspath(path.expanduser())))
    current = Path(lexical.anchor)
    for part in lexical.parts[1:-1]:
        current = current / part
        try:
            metadata = current.lstat()
        except OSError as exc:
            raise SystemExit(f"Could not inspect {label} directory {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise SystemExit(f"{label} path must not traverse a symbolic link: {current}")
        if not stat.S_ISDIR(metadata.st_mode):
            raise SystemExit(f"{label} path component is not a directory: {current}")

    try:
        before = lexical.lstat()
    except FileNotFoundError as exc:
        raise SystemExit(f"Missing {label}: {lexical}") from exc
    except OSError as exc:
        raise SystemExit(f"Could not inspect {label} {lexical}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode):
        raise SystemExit(f"{label} must not be a symbolic link: {lexical}")
    if not stat.S_ISREG(before.st_mode):
        raise SystemExit(f"{label} must be a regular file: {lexical}")
    if before.st_size <= 0:
        raise SystemExit(f"{label} is empty: {lexical}")
    if before.st_size > maximum_bytes:
        raise SystemExit(
            f"{label} exceeds {maximum_bytes} bytes: "
            f"{lexical} is {before.st_size} bytes"
        )

    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    try:
        descriptor = os.open(lexical, flags)
    except OSError as exc:
        raise SystemExit(f"Could not safely open {label} {lexical}: {exc}") from exc

    identity_fields = (
        "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns"
    )
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field)
            for field in identity_fields
        ):
            raise SystemExit(f"{label} changed while being opened: {lexical}")
        remaining = before.st_size
        chunks: list[bytes] = []
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise SystemExit(f"{label} changed while being read: {lexical}")
            chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise SystemExit(f"{label} changed while being read: {lexical}")
        after = os.fstat(descriptor)
    except OSError as exc:
        raise SystemExit(f"Could not safely read {label} {lexical}: {exc}") from exc
    finally:
        os.close(descriptor)

    try:
        after_path = lexical.lstat()
    except OSError as exc:
        raise SystemExit(f"{label} changed while being read: {lexical}: {exc}") from exc
    if any(
        getattr(before, field) != getattr(after, field)
        or getattr(before, field) != getattr(after_path, field)
        for field in identity_fields
    ):
        raise SystemExit(f"{label} changed while being read: {lexical}")
    return b"".join(chunks)


def _read_screenshot_bytes(path: Path) -> bytes:
    return _read_regular_bytes(
        path,
        maximum_bytes=MAX_FILE_BYTES,
        label="raw screenshot",
    )


def inspect_image(path: Path, allow_dark_bars: bool) -> ImageFacts:
    Image, ImageStat, UnidentifiedImageError = _load_pillow()
    content = _read_screenshot_bytes(path)
    digest = hashlib.sha256(content).hexdigest()
    try:
        with Image.open(io.BytesIO(content)) as source:
            source.verify()
        with Image.open(io.BytesIO(content)) as source:
            image = source.convert("RGB")
            width, height = image.size
            if width < MIN_WIDTH or height < MIN_HEIGHT:
                raise SystemExit(
                    f"Screenshot too small: {path} is {width}x{height}; "
                    f"minimum is {MIN_WIDTH}x{MIN_HEIGHT}"
                )
            if width > MAX_WIDTH or height > MAX_HEIGHT:
                raise SystemExit(
                    f"Screenshot too large: {path} is {width}x{height}; "
                    f"maximum is {MAX_WIDTH}x{MAX_HEIGHT}"
                )
            if width <= height:
                raise SystemExit(f"Screenshot must be landscape: {path} is {width}x{height}")

            luma_stddev = float(ImageStat.Stat(image.convert("L")).stddev[0])
            if luma_stddev < MIN_LUMA_STDDEV:
                raise SystemExit(
                    f"Screenshot appears blank or nearly uniform: {path} "
                    f"(luma stddev={luma_stddev:.2f})"
                )

            if not allow_dark_bars and (
                band_is_uniform_dark(image, top=True)
                or band_is_uniform_dark(image, top=False)
            ):
                raise SystemExit(
                    f"Screenshot appears to contain a uniform black system-bar band: {path}. "
                    "Set allow_dark_bars=true only after manual review."
                )

            return ImageFacts(
                width=width,
                height=height,
                sha256=digest,
                perceptual_hash=difference_hash(image),
                luma_stddev=luma_stddev,
            )
    except (UnidentifiedImageError, OSError) as exc:
        raise SystemExit(f"Unreadable or corrupt PNG: {path}: {exc}") from exc


def _publish_staged_directory() -> None:
    try:
        atomic_publish_directory(
            STAGING_DIR,
            FINAL_DIR,
            BACKUP_DIR,
            "curated screenshot directory",
        )
    except StableFileError as exc:
        raise SystemExit(str(exc)) from exc


def _write_staged_file(directory_fd: int, name: str, content: bytes) -> None:
    descriptor = os.open(
        name,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
        0o600,
        dir_fd=directory_fd,
    )
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())


def curate(items: list[dict[str, Any]]) -> list[tuple[dict[str, Any], ImageFacts, CaptureEvidence]]:
    validated: list[
        tuple[
            dict[str, Any], ImageFacts, CaptureEvidence, Path, Path, bytes, bytes
        ]
    ] = []
    expected_dimensions: tuple[int, int] | None = None
    baseline_evidence: CaptureEvidence | None = None

    for item in items:
        raw_path = RAW_DIR / item["raw_file"]
        facts = inspect_image(raw_path, allow_dark_bars=item["allow_dark_bars"])
        raw_bytes = _read_screenshot_bytes(raw_path)
        if hashlib.sha256(raw_bytes).hexdigest() != facts.sha256:
            raise SystemExit(f"Raw screenshot changed after inspection: {raw_path}")
        evidence_path = raw_path.with_suffix(".capture.json")
        try:
            evidence = load_capture_evidence(
                evidence_path,
                expected_raw_file=item["raw_file"],
                expected_scenario=item["scenario"],
                expected_image_sha256=facts.sha256,
                expected_width=facts.width,
                expected_height=facts.height,
            )
            evidence_bytes = _read_regular_bytes(
                evidence_path,
                maximum_bytes=MAX_CAPTURE_EVIDENCE_BYTES,
                label="Capture evidence",
            )
            evidence_object = loads(
                evidence_bytes,
                label=f"capture evidence {evidence_path}",
                maximum_bytes=MAX_CAPTURE_EVIDENCE_BYTES,
                require_object=True,
            )
            staged_evidence = parse_capture_evidence(
                evidence_object,
                evidence_path,
                expected_raw_file=item["raw_file"],
                expected_scenario=item["scenario"],
                expected_image_sha256=facts.sha256,
                expected_width=facts.width,
                expected_height=facts.height,
            )
            if staged_evidence != evidence:
                raise CaptureEvidenceError(
                    f"{evidence_path}: capture evidence changed while being validated"
                )
            if baseline_evidence is None:
                baseline_evidence = evidence
            else:
                require_same_capture_identity(baseline_evidence, evidence, evidence_path)
        except (CaptureEvidenceError, StrictJsonError) as exc:
            raise SystemExit(str(exc)) from exc

        if expected_dimensions is None:
            expected_dimensions = (facts.width, facts.height)
        elif (facts.width, facts.height) != expected_dimensions:
            raise SystemExit(
                f"Screenshot dimensions do not match: {raw_path} is "
                f"{facts.width}x{facts.height}, expected "
                f"{expected_dimensions[0]}x{expected_dimensions[1]}"
            )

        for previous_item, previous_facts, *_ in validated:
            if facts.sha256 == previous_facts.sha256:
                raise SystemExit(
                    f"Exact duplicate screenshots: {item['raw_file']} and "
                    f"{previous_item['raw_file']}"
                )
            distance = hamming_distance(
                facts.perceptual_hash,
                previous_facts.perceptual_hash,
            )
            if distance <= NEAR_DUPLICATE_HAMMING_DISTANCE:
                raise SystemExit(
                    f"Near-duplicate screenshots (dHash distance {distance}): "
                    f"{item['raw_file']} and {previous_item['raw_file']}"
                )

        expected_hash = item.get("sha256")
        if expected_hash is not None and expected_hash != facts.sha256:
            raise SystemExit(
                f"SHA-256 mismatch for {raw_path}: expected {expected_hash}, got {facts.sha256}"
            )

        validated.append(
            (item, facts, evidence, raw_path, evidence_path, raw_bytes, evidence_bytes)
        )

    if not validated:
        raise SystemExit("No screenshots were curated")

    ensure_directory_exists(STAGING_DIR.parent, "screenshot staging parent")
    parent_fd = open_directory_descriptor(STAGING_DIR.parent, "screenshot staging parent")
    staging_fd: int | None = None
    staging_identity: tuple[int, int] | None = None
    try:
        try:
            existing_staging = os.stat(
                STAGING_DIR.name, dir_fd=parent_fd, follow_symlinks=False
            )
        except FileNotFoundError:
            existing_staging = None
        if existing_staging is not None:
            if not stat.S_ISDIR(existing_staging.st_mode):
                raise SystemExit(f"Screenshot staging path is not a plain directory: {STAGING_DIR}")
            remove_directory_at(parent_fd, STAGING_DIR.name)
        os.mkdir(STAGING_DIR.name, mode=0o700, dir_fd=parent_fd)
        staging_fd = os.open(
            STAGING_DIR.name,
            os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
            dir_fd=parent_fd,
        )
        metadata = os.fstat(staging_fd)
        staging_identity = (metadata.st_dev, metadata.st_ino)
        for item, _, _, _raw_path, _evidence_path, raw_bytes, evidence_bytes in validated:
            target_path = Path(item["final_file"])
            target_evidence_path = target_path.with_suffix(".capture.json")
            _write_staged_file(staging_fd, target_path.name, raw_bytes)
            _write_staged_file(staging_fd, target_evidence_path.name, evidence_bytes)
        os.fsync(staging_fd)
        os.close(staging_fd)
        staging_fd = None
        _publish_staged_directory()
    except BaseException:
        if staging_fd is not None:
            os.close(staging_fd)
            staging_fd = None
        try:
            current = os.stat(
                STAGING_DIR.name, dir_fd=parent_fd, follow_symlinks=False
            )
        except FileNotFoundError:
            current = None
        if current is not None and stat.S_ISDIR(current.st_mode) and staging_identity == (
            current.st_dev,
            current.st_ino,
        ):
            remove_directory_at(parent_fd, STAGING_DIR.name)
        raise
    finally:
        if staging_fd is not None:
            os.close(staging_fd)
        os.close(parent_fd)

    return [(item, facts, evidence) for item, facts, evidence, *_ in validated]


def write_summary(copied: list[tuple[dict[str, Any], ImageFacts, CaptureEvidence]]) -> None:
    baseline = copied[0][2]
    lines = [
        "# Curated Screenshot Set",
        "",
        f"- Source manifest: `{MANIFEST_PATH.relative_to(ROOT_DIR)}`",
        f"- Raw source directory: `{RAW_DIR.relative_to(ROOT_DIR)}`",
        f"- Final output directory: `{FINAL_DIR.relative_to(ROOT_DIR)}`",
        f"- Curated screenshots: `{len(copied)}`",
        f"- Candidate SHA: `{baseline.candidate_sha}`",
        f"- Debug APK SHA-256: `{baseline.apk_sha256}`",
        f"- Capture device: `{baseline.device_serial}`",
        f"- Package: `{baseline.package_name}`",
        "",
        "Automated checks cover PNG integrity, dimensions, orientation, size bounds, "
        "blank images, uniform black edge bands, exact duplicates, near duplicates, "
        "verified scenario-ready markers, image hashes, candidate/APK identity, run mode, "
        "package, and device consistency. Marketing quality and truthful visual "
        "interpretation still require human review.",
        "",
        "## Final Set",
        "",
    ]

    for item, facts, evidence in copied:
        lines.extend(
            [
                f"### {item['order']}. {item['title']}",
                "",
                f"- Scenario: `{item['scenario']}`",
                f"- Run mode: `{evidence.run_mode}`",
                f"- Readiness marker: `{evidence.readiness_marker}`",
                f"- Captured at: `{evidence.captured_at_utc}`",
                f"- Settle time after readiness: `{evidence.settle_seconds:.2f}s`",
                f"- Raw file: `{item['raw_file']}`",
                f"- Final file: `{item['final_file']}`",
                f"- Evidence file: `{Path(item['final_file']).with_suffix('.capture.json').name}`",
                f"- Size: `{facts.width}x{facts.height}`",
                f"- SHA-256: `{facts.sha256}`",
                f"- Luma standard deviation: `{facts.luma_stddev:.2f}`",
                f"- Purpose: {item['purpose']}",
                "",
            ]
        )

    try:
        atomic_write_bytes(
            SUMMARY_PATH,
            "\n".join(lines).encode("utf-8"),
            "curated screenshot summary",
            reject_symlink_leaf=True,
        )
    except StableFileError as exc:
        raise SystemExit(str(exc)) from exc


def main() -> int:
    items = validate_manifest(load_manifest())
    copied = curate(items)
    write_summary(copied)
    print(f"Curated {len(copied)} screenshots into {FINAL_DIR}")
    print(f"Wrote summary to {SUMMARY_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
