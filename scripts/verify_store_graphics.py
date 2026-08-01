#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Sequence

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


class StoreGraphicsError(ValueError):
    pass


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_store_graphics(root: Path, graphics_dir: Path, candidate_sha: str) -> dict[str, Any]:
    root = root.resolve()
    graphics_dir = graphics_dir.resolve()
    candidate_sha = candidate_sha.lower()
    if HEX_40.fullmatch(candidate_sha) is None:
        raise StoreGraphicsError("candidate SHA must be 40 hexadecimal characters")
    manifest_path = graphics_dir / "graphics_manifest.json"
    try:
        raw = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise StoreGraphicsError(f"Could not read graphics manifest: {exc}") from exc
    if not isinstance(raw, dict) or raw.get("schemaVersion") != 1:
        raise StoreGraphicsError("graphics manifest schemaVersion must equal 1")
    if raw.get("candidateSha") != candidate_sha:
        raise StoreGraphicsError("graphics manifest candidate does not match release candidate")
    if raw.get("generatedBy") != "scripts/generate_store_assets.py":
        raise StoreGraphicsError("graphics manifest generator identity is invalid")

    required_sources = [root / "scripts/generate_store_assets.py", root / FONT.relative_to(ROOT)]
    required_sources.extend(root / path.relative_to(ROOT) for path in SPRITES)
    expected_sources = {
        str(path.relative_to(root)): {"bytes": path.stat().st_size, "sha256": sha256(path)}
        for path in required_sources
    }
    source_items = raw.get("sourceAssets")
    if not isinstance(source_items, list):
        raise StoreGraphicsError("sourceAssets must be an array")
    actual_sources: dict[str, dict[str, Any]] = {}
    for item in source_items:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
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

    output_items = raw.get("outputs")
    if not isinstance(output_items, list):
        raise StoreGraphicsError("outputs must be an array")
    outputs: dict[str, dict[str, Any]] = {}
    for item in output_items:
        if not isinstance(item, dict) or not isinstance(item.get("file"), str):
            raise StoreGraphicsError("outputs contains an invalid entry")
        filename = item["file"]
        if Path(filename).name != filename or filename in outputs:
            raise StoreGraphicsError(f"invalid or duplicate output entry: {filename}")
        outputs[filename] = item
    if set(outputs) != set(EXPECTED_OUTPUTS):
        raise StoreGraphicsError("graphics output manifest set is incomplete or contains extras")
    actual_files = {path.name for path in graphics_dir.iterdir() if path.is_file()}
    if actual_files != set(EXPECTED_OUTPUTS) | {"graphics_manifest.json"}:
        raise StoreGraphicsError("graphics directory contains missing or unmanifested files")

    for filename, expected_size in EXPECTED_OUTPUTS.items():
        path = graphics_dir / filename
        try:
            with Image.open(path) as image:
                image.verify()
            with Image.open(path) as image:
                size, mode = image.size, image.mode
        except (OSError, UnidentifiedImageError) as exc:
            raise StoreGraphicsError(f"unreadable generated graphic {filename}: {exc}") from exc
        item = outputs[filename]
        if size != expected_size or (item.get("width"), item.get("height")) != expected_size:
            raise StoreGraphicsError(f"graphic dimensions are wrong: {filename}")
        if mode not in {"RGB", "RGBA"} or item.get("mode") != mode:
            raise StoreGraphicsError(f"graphic mode evidence is wrong: {filename}")
        if item.get("bytes") != path.stat().st_size or item.get("sha256") != sha256(path):
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
