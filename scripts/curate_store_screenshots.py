#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from screenshot_capture_evidence import (
    CaptureEvidence,
    CaptureEvidenceError,
    load_capture_evidence,
    require_same_capture_identity,
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
        value = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"Missing manifest: {MANIFEST_PATH}") from exc
    except OSError as exc:
        raise SystemExit(f"Could not read manifest {MANIFEST_PATH}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON in {MANIFEST_PATH}: {exc}") from exc
    if not isinstance(value, dict):
        raise SystemExit("Curation manifest must be a JSON object")
    return value


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
    pixels = list(reduced.getdata())
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


def inspect_image(path: Path, allow_dark_bars: bool) -> ImageFacts:
    Image, ImageStat, UnidentifiedImageError = _load_pillow()
    if not path.is_file():
        raise SystemExit(f"Missing raw screenshot: {path}")
    file_size = path.stat().st_size
    if file_size <= 0:
        raise SystemExit(f"Raw screenshot is empty: {path}")
    if file_size > MAX_FILE_BYTES:
        raise SystemExit(
            f"Raw screenshot exceeds {MAX_FILE_BYTES} bytes: {path} is {file_size} bytes"
        )

    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    try:
        with Image.open(path) as source:
            source.verify()
        with Image.open(path) as source:
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


def _remove_path(path: Path) -> None:
    if path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


def _publish_staged_directory() -> None:
    _remove_path(BACKUP_DIR)
    if FINAL_DIR.exists():
        FINAL_DIR.replace(BACKUP_DIR)
    try:
        STAGING_DIR.replace(FINAL_DIR)
    except BaseException:
        if not FINAL_DIR.exists() and BACKUP_DIR.exists():
            BACKUP_DIR.replace(FINAL_DIR)
        raise
    _remove_path(BACKUP_DIR)


def curate(items: list[dict[str, Any]]) -> list[tuple[dict[str, Any], ImageFacts, CaptureEvidence]]:
    validated: list[tuple[dict[str, Any], ImageFacts, CaptureEvidence, Path, Path]] = []
    expected_dimensions: tuple[int, int] | None = None
    baseline_evidence: CaptureEvidence | None = None

    for item in items:
        raw_path = RAW_DIR / item["raw_file"]
        facts = inspect_image(raw_path, allow_dark_bars=item["allow_dark_bars"])
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
            if baseline_evidence is None:
                baseline_evidence = evidence
            else:
                require_same_capture_identity(baseline_evidence, evidence, evidence_path)
        except CaptureEvidenceError as exc:
            raise SystemExit(str(exc)) from exc

        if expected_dimensions is None:
            expected_dimensions = (facts.width, facts.height)
        elif (facts.width, facts.height) != expected_dimensions:
            raise SystemExit(
                f"Screenshot dimensions do not match: {raw_path} is "
                f"{facts.width}x{facts.height}, expected "
                f"{expected_dimensions[0]}x{expected_dimensions[1]}"
            )

        for previous_item, previous_facts, _, _, _ in validated:
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

        validated.append((item, facts, evidence, raw_path, evidence_path))

    if not validated:
        raise SystemExit("No screenshots were curated")

    _remove_path(STAGING_DIR)
    STAGING_DIR.mkdir(parents=True, exist_ok=False)
    try:
        for item, _, _, raw_path, evidence_path in validated:
            target_path = STAGING_DIR / item["final_file"]
            target_evidence_path = target_path.with_suffix(".capture.json")
            shutil.copy2(raw_path, target_path)
            shutil.copy2(evidence_path, target_evidence_path)
        _publish_staged_directory()
    except BaseException:
        _remove_path(STAGING_DIR)
        raise

    return [(item, facts, evidence) for item, facts, evidence, _, _ in validated]


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

    SUMMARY_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    items = validate_manifest(load_manifest())
    copied = curate(items)
    write_summary(copied)
    print(f"Curated {len(copied)} screenshots into {FINAL_DIR}")
    print(f"Wrote summary to {SUMMARY_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
