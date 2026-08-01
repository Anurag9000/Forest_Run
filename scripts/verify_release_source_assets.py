#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import NoReturn

ROOT = Path(__file__).resolve().parent.parent
ASSET_PATHS_SOURCE = Path(
    "app/src/main/java/com/anurag9000/forestrun/engine/AssetPaths.kt"
)
RELEASE_PREPARER_SOURCE = Path("scripts/prepare_play_release.py")
ASSETS_ROOT = Path("app/src/main/assets")
RAW_ROOT = Path("app/src/main/res/raw")

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
FONT_SIGNATURES = {b"\x00\x01\x00\x00", b"OTTO", b"true", b"typ1"}
SUPPORTED_AUDIO_SUFFIXES = {".ogg", ".wav", ".mp3", ".m4a"}


class SourceAssetVerificationError(RuntimeError):
    """Raised when checked-in release inputs are missing, empty, or malformed."""


@dataclass(frozen=True)
class SourceAssetEvidence:
    asset_count: int
    png_count: int
    font_count: int
    required_audio_count: int
    checked_audio_count: int


def _fail(message: str) -> NoReturn:
    raise SourceAssetVerificationError(message)


def _read_text(path: Path, label: str) -> str:
    if not path.is_file():
        _fail(f"Missing {label}: {path}")
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        _fail(f"Could not read {label} {path}: {error}")


def parse_asset_paths(source: str) -> tuple[str, ...]:
    paths = tuple(
        match.group(1)
        for match in re.finditer(r'\bconst\s+val\s+\w+\s*=\s*"([^"]+)"', source)
    )
    if not paths:
        _fail("AssetPaths.kt does not declare any asset paths")
    if len(paths) != len(set(paths)):
        _fail("AssetPaths.kt contains duplicate asset paths")
    for path in paths:
        candidate = Path(path)
        if candidate.is_absolute() or ".." in candidate.parts or not candidate.parts:
            _fail(f"AssetPaths.kt contains an unsafe relative path: {path!r}")
    return paths


def parse_required_audio(source: str) -> tuple[str, ...]:
    try:
        module = ast.parse(source)
    except SyntaxError as error:
        _fail(f"Could not parse release preparer: {error}")

    value: object | None = None
    for node in module.body:
        if not isinstance(node, ast.Assign):
            continue
        if any(isinstance(target, ast.Name) and target.id == "REQUIRED_AUDIO" for target in node.targets):
            try:
                value = ast.literal_eval(node.value)
            except (ValueError, TypeError, SyntaxError) as error:
                _fail(f"REQUIRED_AUDIO must be a literal sequence: {error}")
            break

    if not isinstance(value, (tuple, list)) or not value:
        _fail("prepare_play_release.py does not declare a non-empty REQUIRED_AUDIO sequence")
    if any(not isinstance(item, str) or not item for item in value):
        _fail("REQUIRED_AUDIO entries must be non-empty strings")

    names = tuple(value)
    if len(names) != len(set(names)):
        _fail("REQUIRED_AUDIO contains duplicate resource names")
    resource_pattern = re.compile(r"[a-z][a-z0-9_]*")
    for name in names:
        if not resource_pattern.fullmatch(name):
            _fail(f"Invalid Android raw-resource name in REQUIRED_AUDIO: {name!r}")
    return names


def _require_nonempty_file(path: Path, label: str) -> bytes:
    if not path.is_file():
        _fail(f"Missing {label}: {path}")
    try:
        size = path.stat().st_size
    except OSError as error:
        _fail(f"Could not stat {label} {path}: {error}")
    if size <= 0:
        _fail(f"Empty {label}: {path}")
    try:
        with path.open("rb") as stream:
            return stream.read(16)
    except OSError as error:
        _fail(f"Could not read {label} {path}: {error}")


def _verify_asset_file(path: Path) -> str:
    header = _require_nonempty_file(path, "runtime asset")
    suffix = path.suffix.lower()
    if suffix == ".png":
        if not header.startswith(PNG_SIGNATURE):
            _fail(f"Runtime PNG has an invalid signature: {path}")
        return "png"
    if suffix in {".ttf", ".otf"}:
        if header[:4] not in FONT_SIGNATURES:
            _fail(f"Runtime font has an invalid signature: {path}")
        return "font"
    _fail(f"AssetPaths.kt references an unsupported release asset type: {path}")


def _verify_audio_file(path: Path) -> None:
    header = _require_nonempty_file(path, "raw audio resource")
    suffix = path.suffix.lower()
    if suffix not in SUPPORTED_AUDIO_SUFFIXES:
        _fail(f"Unsupported raw audio extension: {path}")
    if suffix == ".ogg" and not header.startswith(b"OggS"):
        _fail(f"Ogg resource has an invalid signature: {path}")
    if suffix == ".wav" and not (
        header.startswith(b"RIFF") and header[8:12] == b"WAVE"
    ):
        _fail(f"WAV resource has an invalid signature: {path}")
    if suffix == ".mp3" and not (
        header.startswith(b"ID3") or (len(header) >= 2 and header[0] == 0xFF and header[1] & 0xE0 == 0xE0)
    ):
        _fail(f"MP3 resource has an invalid signature: {path}")
    if suffix == ".m4a" and (len(header) < 12 or header[4:8] != b"ftyp"):
        _fail(f"M4A resource has an invalid signature: {path}")


def verify_release_source_assets(root: Path = ROOT) -> SourceAssetEvidence:
    resolved_root = root.expanduser().resolve()
    asset_paths = parse_asset_paths(
        _read_text(resolved_root / ASSET_PATHS_SOURCE, "AssetPaths source")
    )
    required_audio = parse_required_audio(
        _read_text(resolved_root / RELEASE_PREPARER_SOURCE, "release preparer")
    )

    png_count = 0
    font_count = 0
    for relative_path in asset_paths:
        kind = _verify_asset_file(resolved_root / ASSETS_ROOT / relative_path)
        if kind == "png":
            png_count += 1
        elif kind == "font":
            font_count += 1

    raw_root = resolved_root / RAW_ROOT
    if not raw_root.is_dir():
        _fail(f"Missing Android raw-resource directory: {raw_root}")
    audio_paths = sorted(path for path in raw_root.iterdir() if path.is_file())
    if not audio_paths:
        _fail(f"Android raw-resource directory is empty: {raw_root}")

    by_stem: dict[str, list[Path]] = {}
    for path in audio_paths:
        by_stem.setdefault(path.stem, []).append(path)
        _verify_audio_file(path)

    for name in required_audio:
        matches = by_stem.get(name, [])
        if not matches:
            _fail(f"Missing required raw audio resource: {name}")
        if len(matches) != 1:
            rendered = ", ".join(str(path) for path in matches)
            _fail(f"Duplicate raw audio resources for {name}: {rendered}")

    return SourceAssetEvidence(
        asset_count=len(asset_paths),
        png_count=png_count,
        font_count=font_count,
        required_audio_count=len(required_audio),
        checked_audio_count=len(audio_paths),
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify checked-in Forest Run release assets before Gradle packaging."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="Repository root to verify.",
    )
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        evidence = verify_release_source_assets(args.root)
    except SourceAssetVerificationError as error:
        raise SystemExit(str(error)) from error

    if args.json:
        print(json.dumps(asdict(evidence), sort_keys=True))
    else:
        print(
            "Verified "
            f"{evidence.asset_count} runtime asset(s) and "
            f"{evidence.checked_audio_count} raw audio resource(s)."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
