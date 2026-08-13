#!/usr/bin/env python3
"""Build and verify deterministic visual-baseline identity provenance.

This descriptor proves which candidate, curation manifest, and raster files a
visual baseline claims to contain. It does not claim that a human approved the
appearance and must never be treated as an approval signature.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import tempfile
from pathlib import Path
from typing import Sequence

from PIL import Image, UnidentifiedImageError

import strict_json
from verify_curated_screenshot_set import CuratedScreenshotError, _load_manifest

SCHEMA_VERSION = 1
KIND = "visual_baseline_identity"
SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
MAX_IMAGE_BYTES = 64 * 1024 * 1024
MAX_IMAGE_PIXELS = 12_000_000
EXPECTED_KEYS = {
    "schemaVersion",
    "kind",
    "baselineCandidateSha",
    "manifestSha256",
    "filenameField",
    "screenshotCount",
    "screenshotSetSha256",
    "screenshots",
    "limitations",
}


class VisualBaselineProvenanceError(ValueError):
    pass


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def _regular_file(path: Path, label: str, *, maximum_bytes: int) -> Path:
    absolute = _absolute(path)
    try:
        metadata = absolute.lstat()
    except FileNotFoundError as exc:
        raise VisualBaselineProvenanceError(f"missing {label}: {absolute}") from exc
    except OSError as exc:
        raise VisualBaselineProvenanceError(
            f"could not inspect {label} {absolute}: {exc}"
        ) from exc
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
        raise VisualBaselineProvenanceError(
            f"{label} must be a regular non-symlink file: {absolute}"
        )
    if metadata.st_size <= 0 or metadata.st_size > maximum_bytes:
        raise VisualBaselineProvenanceError(
            f"{label} has invalid size: {absolute} is {metadata.st_size} bytes"
        )
    return absolute


def _root(root: Path) -> Path:
    absolute = _absolute(root)
    try:
        metadata = absolute.lstat()
    except FileNotFoundError as exc:
        raise VisualBaselineProvenanceError(
            f"missing visual baseline root: {absolute}"
        ) from exc
    except OSError as exc:
        raise VisualBaselineProvenanceError(
            f"could not inspect visual baseline root {absolute}: {exc}"
        ) from exc
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
        raise VisualBaselineProvenanceError(
            f"visual baseline root must be a regular non-symlink directory: {absolute}"
        )
    return absolute


def _baseline_file(root: Path, file_name: str) -> Path:
    trusted_root = _root(root)
    target = _absolute(trusted_root / file_name)
    try:
        target.relative_to(trusted_root)
    except ValueError as exc:
        raise VisualBaselineProvenanceError(
            f"baseline file escapes visual baseline root: {file_name}"
        ) from exc
    target = _regular_file(target, "visual baseline screenshot", maximum_bytes=MAX_IMAGE_BYTES)
    try:
        target.resolve(strict=True).relative_to(trusted_root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise VisualBaselineProvenanceError(
            f"baseline file does not resolve inside visual baseline root: {file_name}"
        ) from exc
    return target


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _image_identity(path: Path, *, scenario: str, file_name: str) -> dict[str, object]:
    before = _sha256(path)
    try:
        with Image.open(path) as image:
            if image.format != "PNG":
                raise VisualBaselineProvenanceError(
                    f"baseline screenshot must be PNG: {file_name}"
                )
            width, height = image.size
            if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
                raise VisualBaselineProvenanceError(
                    f"baseline screenshot dimensions are invalid: {file_name}={width}x{height}"
                )
            image.load()
    except VisualBaselineProvenanceError:
        raise
    except (OSError, UnidentifiedImageError, Image.DecompressionBombError) as exc:
        raise VisualBaselineProvenanceError(
            f"could not decode baseline screenshot {file_name}: {exc}"
        ) from exc
    after = _sha256(path)
    if before != after:
        raise VisualBaselineProvenanceError(
            f"baseline screenshot changed while being inspected: {file_name}"
        )
    return {
        "scenario": scenario,
        "fileName": file_name,
        "sha256": before,
        "width": width,
        "height": height,
    }


def build_provenance(
    *,
    manifest_path: Path,
    baseline_dir: Path,
    filename_field: str,
    baseline_candidate_sha: str,
) -> dict[str, object]:
    if filename_field not in {"raw_file", "final_file"}:
        raise VisualBaselineProvenanceError(
            "filename field must be raw_file or final_file"
        )
    if SHA40.fullmatch(baseline_candidate_sha) is None:
        raise VisualBaselineProvenanceError(
            "baseline candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )
    manifest = _regular_file(
        manifest_path,
        "curation manifest",
        maximum_bytes=4 * 1024 * 1024,
    )
    try:
        items = _load_manifest(manifest)
    except CuratedScreenshotError as exc:
        raise VisualBaselineProvenanceError(str(exc)) from exc

    screenshots = [
        _image_identity(
            _baseline_file(baseline_dir, item[filename_field]),
            scenario=item["scenario"],
            file_name=item[filename_field],
        )
        for item in items
    ]
    screenshot_set_sha = hashlib.sha256(_canonical_bytes(screenshots)).hexdigest()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "baselineCandidateSha": baseline_candidate_sha,
        "manifestSha256": _sha256(manifest),
        "filenameField": filename_field,
        "screenshotCount": len(screenshots),
        "screenshotSetSha256": screenshot_set_sha,
        "screenshots": screenshots,
        "limitations": [
            "This descriptor proves baseline identity and provenance only.",
            "It is not evidence that a human approved the visual appearance.",
            "It does not replace accessibility, physical-device, or store review.",
        ],
    }


def verify_provenance(
    provenance_path: Path,
    *,
    manifest_path: Path,
    baseline_dir: Path,
    filename_field: str,
) -> dict[str, object]:
    provenance = _regular_file(
        provenance_path,
        "visual baseline provenance",
        maximum_bytes=4 * 1024 * 1024,
    )
    try:
        payload = strict_json.load_file(provenance, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise VisualBaselineProvenanceError(
            f"invalid visual baseline provenance: {exc}"
        ) from exc
    assert isinstance(payload, dict)
    if set(payload) != EXPECTED_KEYS:
        missing = sorted(EXPECTED_KEYS - set(payload))
        extra = sorted(set(payload) - EXPECTED_KEYS)
        raise VisualBaselineProvenanceError(
            f"visual baseline provenance keys mismatch; missing={missing}, extra={extra}"
        )
    if payload.get("schemaVersion") != SCHEMA_VERSION or payload.get("kind") != KIND:
        raise VisualBaselineProvenanceError("visual baseline provenance schema/kind is invalid")
    candidate_sha = payload.get("baselineCandidateSha")
    if not isinstance(candidate_sha, str) or SHA40.fullmatch(candidate_sha) is None:
        raise VisualBaselineProvenanceError(
            "baselineCandidateSha must be exactly 40 lowercase hexadecimal characters"
        )
    rebuilt = build_provenance(
        manifest_path=manifest_path,
        baseline_dir=baseline_dir,
        filename_field=filename_field,
        baseline_candidate_sha=candidate_sha,
    )
    if payload != rebuilt:
        raise VisualBaselineProvenanceError(
            "visual baseline provenance does not match the current manifest/baseline files"
        )
    set_digest = payload.get("screenshotSetSha256")
    manifest_digest = payload.get("manifestSha256")
    if not isinstance(set_digest, str) or SHA256.fullmatch(set_digest) is None:
        raise VisualBaselineProvenanceError("screenshotSetSha256 is invalid")
    if not isinstance(manifest_digest, str) or SHA256.fullmatch(manifest_digest) is None:
        raise VisualBaselineProvenanceError("manifestSha256 is invalid")
    return payload


def publish(path: Path, payload: dict[str, object]) -> None:
    output = _absolute(path)
    if output.is_symlink():
        raise VisualBaselineProvenanceError("output must not be a symbolic link")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output.name}.", suffix=".tmp", dir=output.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(_canonical_bytes(payload))
            handle.flush()
            os.fsync(handle.fileno())
        if output.is_symlink():
            raise VisualBaselineProvenanceError("output became a symbolic link")
        os.replace(temporary, output)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    subparsers = result.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build")
    build.add_argument("--manifest", type=Path, required=True)
    build.add_argument("--baseline-dir", type=Path, required=True)
    build.add_argument("--filename-field", choices=("raw_file", "final_file"), default="final_file")
    build.add_argument("--baseline-candidate-sha", required=True)
    build.add_argument("--output", type=Path, required=True)

    verify = subparsers.add_parser("verify")
    verify.add_argument("--provenance", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    verify.add_argument("--baseline-dir", type=Path, required=True)
    verify.add_argument("--filename-field", choices=("raw_file", "final_file"), default="final_file")
    return result


def main(argv: Sequence[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "build":
            payload = build_provenance(
                manifest_path=args.manifest,
                baseline_dir=args.baseline_dir,
                filename_field=args.filename_field,
                baseline_candidate_sha=args.baseline_candidate_sha,
            )
            publish(args.output, payload)
        else:
            payload = verify_provenance(
                args.provenance,
                manifest_path=args.manifest,
                baseline_dir=args.baseline_dir,
                filename_field=args.filename_field,
            )
    except (VisualBaselineProvenanceError, OSError) as exc:
        print(f"visual baseline provenance error: {exc}", file=os.sys.stderr)
        return 1
    print(
        json.dumps(
            {
                "baselineCandidateSha": payload["baselineCandidateSha"],
                "screenshotSetSha256": payload["screenshotSetSha256"],
                "status": "valid",
            },
            sort_keys=True,
            separators=(",", ":"),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
