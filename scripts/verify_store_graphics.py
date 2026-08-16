#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import io
import os
import re
import stat
import sys
from pathlib import Path
from typing import Any, Sequence

import strict_json

try:
    from PIL import Image, UnidentifiedImageError
except ImportError as exc:
    raise SystemExit("Pillow is required. Install scripts/requirements.txt.") from exc

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GRAPHICS_DIR = ROOT / "release/google-play/graphics"
GENERATOR = ROOT / "scripts/generate_store_assets.py"
FONT = ROOT / "app/src/main/assets/fonts/PressStart2P-Regular.ttf"
SPRITES = (
    ROOT / "app/src/main/assets/sprites/char/runner_girl_technical_48frame.png",
    ROOT / "app/src/main/assets/sprites/animals/fox_4frames.png",
    ROOT / "app/src/main/assets/sprites/birds/owl_4frames.png",
    ROOT / "app/src/main/assets/sprites/plants/lily_of_valley_4frames.png",
)
EXPECTED_OUTPUTS = {"feature-graphic.png": (1024, 500), "promo-square.png": (512, 512)}
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
MAX_MANIFEST_BYTES = 64 * 1024
MAX_SOURCE_BYTES = 64 * 1024 * 1024
MAX_GRAPHIC_BYTES = 16 * 1024 * 1024
MANIFEST_KEYS = {"schemaVersion", "generatedBy", "candidateSha", "sourceAssets", "outputs"}
SOURCE_EVIDENCE_KEYS = {"path", "bytes", "sha256"}
OUTPUT_EVIDENCE_KEYS = {"file", "width", "height", "mode", "bytes", "sha256"}


class StoreGraphicsError(ValueError):
    pass


def _absolute_lexical(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def _admit_graphics_directory(path: Path) -> Path:
    lexical = _absolute_lexical(path)
    try:
        metadata = lexical.lstat()
    except FileNotFoundError as exc:
        raise StoreGraphicsError(f"Store graphics directory is missing: {lexical}") from exc
    except OSError as exc:
        raise StoreGraphicsError(f"Could not inspect store graphics directory {lexical}: {exc}") from exc
    if stat.S_ISLNK(metadata.st_mode):
        raise StoreGraphicsError(f"Store graphics directory must not be a symbolic link: {lexical}")
    if not stat.S_ISDIR(metadata.st_mode):
        raise StoreGraphicsError(f"Store graphics path is not a directory: {lexical}")
    return lexical


def _read_regular_bytes(path: Path, *, maximum_bytes: int, label: str) -> bytes:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise StoreGraphicsError(f"Missing {label}: {path}") from exc
    except OSError as exc:
        raise StoreGraphicsError(f"Could not inspect {label} {path}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode):
        raise StoreGraphicsError(f"{label} must not be a symbolic link: {path}")
    if not stat.S_ISREG(before.st_mode):
        raise StoreGraphicsError(f"{label} must be a regular file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise StoreGraphicsError(f"{label} has invalid byte length: {path}")
    try:
        raw = path.read_bytes()
        after = path.stat()
    except OSError as exc:
        raise StoreGraphicsError(f"Could not read {label} {path}: {exc}") from exc
    if (
        len(raw) != before.st_size
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise StoreGraphicsError(f"{label} changed while being read: {path}")
    return raw


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _inspect_png(path: Path, expected_size: tuple[int, int]) -> dict[str, Any]:
    raw = _read_regular_bytes(path, maximum_bytes=MAX_GRAPHIC_BYTES, label="generated graphic")
    try:
        with Image.open(io.BytesIO(raw)) as image:
            image.verify()
        with Image.open(io.BytesIO(raw)) as image:
            size, mode = image.size, image.mode
    except (OSError, UnidentifiedImageError) as exc:
        raise StoreGraphicsError(f"unreadable generated graphic {path.name}: {exc}") from exc
    if size != expected_size:
        raise StoreGraphicsError(f"graphic dimensions are wrong: {path.name}")
    if mode not in {"RGB", "RGBA"}:
        raise StoreGraphicsError(f"graphic mode is unsupported: {path.name}")
    return {
        "file": path.name,
        "width": size[0],
        "height": size[1],
        "mode": mode,
        "bytes": len(raw),
        "sha256": _sha256(raw),
    }


def verify_store_graphics(root: Path, graphics_dir: Path, candidate_sha: str) -> dict[str, Any]:
    root = root.resolve()
    graphics_dir = _admit_graphics_directory(graphics_dir)
    candidate_sha = candidate_sha.lower()
    if HEX_40.fullmatch(candidate_sha) is None:
        raise StoreGraphicsError("candidate SHA must be 40 hexadecimal characters")

    manifest_path = graphics_dir / "graphics_manifest.json"
    manifest_bytes = _read_regular_bytes(
        manifest_path,
        maximum_bytes=MAX_MANIFEST_BYTES,
        label="graphics manifest",
    )
    try:
        raw = strict_json.loads(
            manifest_bytes,
            label="graphics manifest",
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise StoreGraphicsError(f"Invalid graphics manifest: {exc}") from exc
    assert isinstance(raw, dict)
    if set(raw) != MANIFEST_KEYS:
        raise StoreGraphicsError("graphics manifest fields are incomplete or contain extras")
    if raw.get("schemaVersion") != 1:
        raise StoreGraphicsError("graphics manifest schemaVersion must equal 1")
    if raw.get("candidateSha") != candidate_sha:
        raise StoreGraphicsError("graphics manifest candidate does not match release candidate")
    if raw.get("generatedBy") != "scripts/generate_store_assets.py":
        raise StoreGraphicsError("graphics manifest generator identity is invalid")

    required_sources = [root / "scripts/generate_store_assets.py", root / FONT.relative_to(ROOT)]
    required_sources.extend(root / path.relative_to(ROOT) for path in SPRITES)
    expected_sources: dict[str, dict[str, Any]] = {}
    for path in required_sources:
        source_bytes = _read_regular_bytes(
            path,
            maximum_bytes=MAX_SOURCE_BYTES,
            label="graphics source asset",
        )
        expected_sources[str(path.relative_to(root))] = {
            "bytes": len(source_bytes),
            "sha256": _sha256(source_bytes),
        }

    source_items = raw.get("sourceAssets")
    if not isinstance(source_items, list):
        raise StoreGraphicsError("sourceAssets must be an array")
    actual_sources: dict[str, dict[str, Any]] = {}
    for item in source_items:
        if (
            not isinstance(item, dict)
            or set(item) != SOURCE_EVIDENCE_KEYS
            or not isinstance(item.get("path"), str)
        ):
            raise StoreGraphicsError("sourceAssets contains an invalid entry")
        path = item["path"]
        if path in actual_sources:
            raise StoreGraphicsError(f"duplicate source asset entry: {path}")
        actual_sources[path] = item
    if set(actual_sources) != set(expected_sources):
        raise StoreGraphicsError("graphics source asset set does not match generator contract")
    for path, expected in expected_sources.items():
        item = actual_sources[path]
        if item.get("bytes") != expected["bytes"] or item.get("sha256") != expected["sha256"]:
            raise StoreGraphicsError(f"graphics source asset evidence is stale: {path}")
        if HEX_64.fullmatch(str(item.get("sha256", ""))) is None:
            raise StoreGraphicsError(f"graphics source asset digest is malformed: {path}")

    output_items = raw.get("outputs")
    if not isinstance(output_items, list):
        raise StoreGraphicsError("outputs must be an array")
    outputs: dict[str, dict[str, Any]] = {}
    for item in output_items:
        if (
            not isinstance(item, dict)
            or set(item) != OUTPUT_EVIDENCE_KEYS
            or not isinstance(item.get("file"), str)
        ):
            raise StoreGraphicsError("outputs contains an invalid entry")
        filename = item["file"]
        if Path(filename).name != filename or filename in outputs:
            raise StoreGraphicsError(f"invalid or duplicate output entry: {filename}")
        outputs[filename] = item
    if set(outputs) != set(EXPECTED_OUTPUTS):
        raise StoreGraphicsError("graphics output manifest set is incomplete or contains extras")

    expected_directory_entries = set(EXPECTED_OUTPUTS) | {"graphics_manifest.json"}
    actual_entries: set[str] = set()
    try:
        entries = list(graphics_dir.iterdir())
    except OSError as exc:
        raise StoreGraphicsError(f"Could not enumerate store graphics directory {graphics_dir}: {exc}") from exc
    for path in entries:
        try:
            metadata = path.lstat()
        except OSError as exc:
            raise StoreGraphicsError(f"Could not inspect store graphics entry {path}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise StoreGraphicsError(f"Store graphics entry must not be a symbolic link: {path}")
        if not stat.S_ISREG(metadata.st_mode):
            raise StoreGraphicsError(f"Store graphics directory contains a non-file entry: {path.name}")
        actual_entries.add(path.name)
    if actual_entries != expected_directory_entries:
        raise StoreGraphicsError("graphics directory contains missing or unmanifested files")

    for filename, expected_size in EXPECTED_OUTPUTS.items():
        facts = _inspect_png(graphics_dir / filename, expected_size)
        item = outputs[filename]
        if (item.get("width"), item.get("height")) != expected_size:
            raise StoreGraphicsError(f"graphic dimensions are wrong: {filename}")
        if item.get("mode") != facts["mode"]:
            raise StoreGraphicsError(f"graphic mode evidence is wrong: {filename}")
        if item.get("bytes") != facts["bytes"] or item.get("sha256") != facts["sha256"]:
            raise StoreGraphicsError(f"graphic hash/size evidence is stale: {filename}")
        if HEX_64.fullmatch(str(item.get("sha256", ""))) is None:
            raise StoreGraphicsError(f"graphic digest is malformed: {filename}")
    return raw


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify candidate-bound Forest Run store graphics")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--graphics-dir", type=Path, default=DEFAULT_GRAPHICS_DIR)
    parser.add_argument("--candidate-sha", required=True)
    args = parser.parse_args(argv)
    try:
        manifest = verify_store_graphics(args.root, args.graphics_dir, args.candidate_sha)
    except StoreGraphicsError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(f"Verified {len(manifest['outputs'])} store graphics for {args.candidate_sha.lower()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
