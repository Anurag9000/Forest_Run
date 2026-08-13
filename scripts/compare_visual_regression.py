#!/usr/bin/env python3
"""Compare deterministic Forest Run screenshots with explicit, tolerant metrics.

This tool is diagnostic. It can detect unexpected raster changes in deterministic
capture scenarios, but it does not replace physical-device, accessibility, or
human presentation acceptance.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import stat
import sys
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence

from PIL import Image, UnidentifiedImageError

from verify_curated_screenshot_set import CuratedScreenshotError, _load_manifest

MAX_IMAGE_BYTES = 64 * 1024 * 1024
MAX_IMAGE_PIXELS = 12_000_000
Image.MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS


class VisualRegressionError(ValueError):
    """Raised when a screenshot comparison cannot be trusted."""


@dataclass(frozen=True)
class ImageRegressionResult:
    scenario: str
    file_name: str
    width: int
    height: int
    baseline_sha256: str
    candidate_sha256: str
    per_channel_tolerance: int
    mean_absolute_channel_delta: float
    p95_pixel_max_channel_delta: int
    changed_pixel_ratio: float
    passed: bool


@dataclass(frozen=True)
class VisualRegressionSummary:
    status: str
    comparison_count: int
    failed_scenarios: tuple[str, ...]
    maximum_mean_absolute_channel_delta: float
    maximum_p95_pixel_max_channel_delta: int
    maximum_changed_pixel_ratio: float
    results: tuple[ImageRegressionResult, ...]

    def to_json(self) -> dict[str, object]:
        return {
            "status": self.status,
            "comparisonCount": self.comparison_count,
            "failedScenarios": list(self.failed_scenarios),
            "maximumMeanAbsoluteChannelDelta": self.maximum_mean_absolute_channel_delta,
            "maximumP95PixelMaxChannelDelta": self.maximum_p95_pixel_max_channel_delta,
            "maximumChangedPixelRatio": self.maximum_changed_pixel_ratio,
            "results": [asdict(result) for result in self.results],
        }


def _absolute_unresolved(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def _reject_symlink(path: Path, label: str) -> None:
    try:
        metadata = path.lstat()
    except FileNotFoundError as exc:
        raise VisualRegressionError(f"missing {label}: {path}") from exc
    except OSError as exc:
        raise VisualRegressionError(f"could not inspect {label} {path}: {exc}") from exc
    if stat.S_ISLNK(metadata.st_mode):
        raise VisualRegressionError(f"{label} must not be a symbolic link: {path}")
    if not stat.S_ISREG(metadata.st_mode):
        raise VisualRegressionError(f"{label} must be a regular file: {path}")
    if metadata.st_size <= 0 or metadata.st_size > MAX_IMAGE_BYTES:
        raise VisualRegressionError(
            f"{label} has invalid file size: {path} is {metadata.st_size} bytes"
        )


def _trusted_regular_file(root: Path, path: Path, label: str) -> Path:
    """Return a regular file that is lexically and physically confined to root.

    The manifest currently permits only leaf file names, but this boundary also
    rejects symlinked roots/descendants so a future nested-path change cannot
    silently turn the visual comparator into an arbitrary-file reader.
    """
    root_path = _absolute_unresolved(root)
    target_path = _absolute_unresolved(path)
    try:
        root_metadata = root_path.lstat()
    except FileNotFoundError as exc:
        raise VisualRegressionError(f"missing trusted screenshot root: {root_path}") from exc
    except OSError as exc:
        raise VisualRegressionError(
            f"could not inspect trusted screenshot root {root_path}: {exc}"
        ) from exc
    if stat.S_ISLNK(root_metadata.st_mode) or not stat.S_ISDIR(root_metadata.st_mode):
        raise VisualRegressionError(
            f"trusted screenshot root must be a regular directory, not a symbolic link: {root_path}"
        )

    try:
        relative = target_path.relative_to(root_path)
    except ValueError as exc:
        raise VisualRegressionError(
            f"{label} escapes its trusted screenshot root: {target_path}"
        ) from exc
    if not relative.parts:
        raise VisualRegressionError(f"{label} must name a file below {root_path}")

    current = root_path
    for component in relative.parts[:-1]:
        current = current / component
        try:
            metadata = current.lstat()
        except FileNotFoundError as exc:
            raise VisualRegressionError(f"missing {label} parent: {current}") from exc
        except OSError as exc:
            raise VisualRegressionError(
                f"could not inspect {label} parent {current}: {exc}"
            ) from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise VisualRegressionError(
                f"{label} parent must not be a symbolic link: {current}"
            )
        if not stat.S_ISDIR(metadata.st_mode):
            raise VisualRegressionError(f"{label} parent is not a directory: {current}")

    _reject_symlink(target_path, label)
    try:
        resolved_root = root_path.resolve(strict=True)
        resolved_target = target_path.resolve(strict=True)
        resolved_target.relative_to(resolved_root)
    except (FileNotFoundError, OSError, ValueError) as exc:
        raise VisualRegressionError(
            f"{label} does not resolve inside its trusted screenshot root: {target_path}"
        ) from exc
    return target_path


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_rgb(path: Path, label: str) -> tuple[Image.Image, str]:
    _reject_symlink(path, label)
    digest_before = _sha256(path)
    try:
        with Image.open(path) as source:
            if source.format != "PNG":
                raise VisualRegressionError(f"{label} must be a PNG: {path}")
            width, height = source.size
            if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
                raise VisualRegressionError(
                    f"{label} has invalid dimensions: {path} is {width}x{height}"
                )
            source.load()
            image = source.convert("RGB")
    except VisualRegressionError:
        raise
    except (OSError, UnidentifiedImageError, Image.DecompressionBombError) as exc:
        raise VisualRegressionError(f"could not decode {label} {path}: {exc}") from exc
    digest_after = _sha256(path)
    if digest_after != digest_before:
        raise VisualRegressionError(f"{label} changed while being decoded: {path}")
    return image, digest_before


def _percentile95(values: list[int]) -> int:
    if not values:
        return 0
    values.sort()
    index = max(0, math.ceil(len(values) * 0.95) - 1)
    return values[index]


def compare_image(
    *,
    baseline_path: Path,
    candidate_path: Path,
    scenario: str,
    per_channel_tolerance: int,
    max_mean_absolute_channel_delta: float,
    max_changed_pixel_ratio: float,
    max_p95_pixel_max_channel_delta: int,
) -> ImageRegressionResult:
    if not 0 <= per_channel_tolerance <= 255:
        raise VisualRegressionError("per-channel tolerance must be between 0 and 255")
    if not math.isfinite(max_mean_absolute_channel_delta) or not (
        0.0 <= max_mean_absolute_channel_delta <= 255.0
    ):
        raise VisualRegressionError(
            "maximum mean absolute channel delta must be finite and between 0 and 255"
        )
    if not math.isfinite(max_changed_pixel_ratio) or not (
        0.0 <= max_changed_pixel_ratio <= 1.0
    ):
        raise VisualRegressionError(
            "maximum changed pixel ratio must be finite and between 0 and 1"
        )
    if not 0 <= max_p95_pixel_max_channel_delta <= 255:
        raise VisualRegressionError("maximum p95 pixel delta must be between 0 and 255")

    baseline, baseline_sha = _load_rgb(baseline_path, "baseline screenshot")
    candidate, candidate_sha = _load_rgb(candidate_path, "candidate screenshot")
    if baseline.size != candidate.size:
        raise VisualRegressionError(
            "screenshot dimensions differ for "
            f"{scenario}: baseline={baseline.size[0]}x{baseline.size[1]}, "
            f"candidate={candidate.size[0]}x{candidate.size[1]}"
        )

    width, height = baseline.size
    total_pixels = width * height
    channel_delta_sum = 0
    changed_pixels = 0
    pixel_maxima: list[int] = []
    for baseline_pixel, candidate_pixel in zip(
        baseline.getdata(), candidate.getdata(), strict=True
    ):
        deltas = (
            abs(baseline_pixel[0] - candidate_pixel[0]),
            abs(baseline_pixel[1] - candidate_pixel[1]),
            abs(baseline_pixel[2] - candidate_pixel[2]),
        )
        channel_delta_sum += sum(deltas)
        pixel_max = max(deltas)
        pixel_maxima.append(pixel_max)
        if pixel_max > per_channel_tolerance:
            changed_pixels += 1

    mean_delta = channel_delta_sum / float(total_pixels * 3)
    changed_ratio = changed_pixels / float(total_pixels)
    p95_delta = _percentile95(pixel_maxima)
    passed = (
        mean_delta <= max_mean_absolute_channel_delta + 1e-12
        and changed_ratio <= max_changed_pixel_ratio + 1e-12
        and p95_delta <= max_p95_pixel_max_channel_delta
    )
    return ImageRegressionResult(
        scenario=scenario,
        file_name=candidate_path.name,
        width=width,
        height=height,
        baseline_sha256=baseline_sha,
        candidate_sha256=candidate_sha,
        per_channel_tolerance=per_channel_tolerance,
        mean_absolute_channel_delta=mean_delta,
        p95_pixel_max_channel_delta=p95_delta,
        changed_pixel_ratio=changed_ratio,
        passed=passed,
    )


def compare_manifest_set(
    *,
    manifest_path: Path,
    baseline_dir: Path,
    candidate_dir: Path,
    filename_field: str,
    per_channel_tolerance: int,
    max_mean_absolute_channel_delta: float,
    max_changed_pixel_ratio: float,
    max_p95_pixel_max_channel_delta: int,
) -> VisualRegressionSummary:
    if filename_field not in {"raw_file", "final_file"}:
        raise VisualRegressionError("filename field must be raw_file or final_file")
    try:
        items = _load_manifest(manifest_path)
    except CuratedScreenshotError as exc:
        raise VisualRegressionError(str(exc)) from exc

    results: list[ImageRegressionResult] = []
    for item in items:
        file_name = item[filename_field]
        baseline_path = _trusted_regular_file(
            baseline_dir,
            baseline_dir / file_name,
            "baseline screenshot",
        )
        candidate_path = _trusted_regular_file(
            candidate_dir,
            candidate_dir / file_name,
            "candidate screenshot",
        )
        results.append(
            compare_image(
                baseline_path=baseline_path,
                candidate_path=candidate_path,
                scenario=item["scenario"],
                per_channel_tolerance=per_channel_tolerance,
                max_mean_absolute_channel_delta=max_mean_absolute_channel_delta,
                max_changed_pixel_ratio=max_changed_pixel_ratio,
                max_p95_pixel_max_channel_delta=max_p95_pixel_max_channel_delta,
            )
        )

    failed = tuple(result.scenario for result in results if not result.passed)
    return VisualRegressionSummary(
        status="valid" if not failed else "regression",
        comparison_count=len(results),
        failed_scenarios=failed,
        maximum_mean_absolute_channel_delta=max(
            (result.mean_absolute_channel_delta for result in results), default=0.0
        ),
        maximum_p95_pixel_max_channel_delta=max(
            (result.p95_pixel_max_channel_delta for result in results), default=0
        ),
        maximum_changed_pixel_ratio=max(
            (result.changed_pixel_ratio for result in results), default=0.0
        ),
        results=tuple(results),
    )


def _write_json_atomic(path: Path, payload: dict[str, object]) -> None:
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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--candidate-dir", type=Path, required=True)
    parser.add_argument(
        "--filename-field",
        choices=("raw_file", "final_file"),
        default="final_file",
    )
    parser.add_argument("--per-channel-tolerance", type=int, default=4)
    parser.add_argument("--max-mean-absolute-channel-delta", type=float, default=1.5)
    parser.add_argument("--max-changed-pixel-ratio", type=float, default=0.01)
    parser.add_argument("--max-p95-pixel-max-channel-delta", type=int, default=4)
    parser.add_argument("--output", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        summary = compare_manifest_set(
            manifest_path=args.manifest,
            baseline_dir=args.baseline_dir,
            candidate_dir=args.candidate_dir,
            filename_field=args.filename_field,
            per_channel_tolerance=args.per_channel_tolerance,
            max_mean_absolute_channel_delta=args.max_mean_absolute_channel_delta,
            max_changed_pixel_ratio=args.max_changed_pixel_ratio,
            max_p95_pixel_max_channel_delta=args.max_p95_pixel_max_channel_delta,
        )
    except VisualRegressionError as exc:
        payload: dict[str, object] = {"status": "invalid", "error": str(exc)}
        if args.output is not None:
            _write_json_atomic(args.output, payload)
        print(json.dumps(payload, sort_keys=True))
        return 2

    payload = summary.to_json()
    if args.output is not None:
        _write_json_atomic(args.output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0 if summary.status == "valid" else 1


if __name__ == "__main__":
    raise SystemExit(main())
