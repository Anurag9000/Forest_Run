#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, NoReturn

from audit_runtime_asset_references import verify_repository as verify_asset_ownership
from verify_release_source_assets import (
    ASSETS_ROOT,
    RAW_ROOT,
    verify_release_source_assets,
)

ROOT = Path(__file__).resolve().parent.parent
INVENTORY_KIND = "forest-run-creative-asset-inventory-v1"
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")


class CreativeAssetInventoryError(RuntimeError):
    """Raised when a reproducible creative-asset inventory cannot be built."""


@dataclass(frozen=True)
class CreativeAssetEntry:
    path: str
    category: str
    bytes: int
    sha256: str
    provenanceStatus: str = "unverified"
    licenseReviewStatus: str = "required"


def _fail(message: str) -> NoReturn:
    raise CreativeAssetInventoryError(message)


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        )
        + "\n"
    ).encode("utf-8")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as error:
        _fail(f"Could not hash creative asset {path}: {error}")
    return digest.hexdigest()


def _category(relative_path: str) -> str:
    if relative_path.startswith("app/src/main/assets/fonts/"):
        return "font"
    if relative_path.startswith("app/src/main/assets/sprites/"):
        return "sprite"
    if relative_path.startswith("app/src/main/res/raw/music_"):
        return "music"
    if relative_path.startswith("app/src/main/res/raw/sfx_"):
        return "sfx"
    return "other_creative_runtime_asset"


def collect_creative_asset_entries(root: Path) -> tuple[CreativeAssetEntry, ...]:
    resolved = root.expanduser().resolve()
    roots = (resolved / ASSETS_ROOT, resolved / RAW_ROOT)
    entries: list[CreativeAssetEntry] = []

    for creative_root in roots:
        if not creative_root.is_dir():
            _fail(f"Missing creative runtime directory: {creative_root}")
        try:
            candidates = sorted(creative_root.rglob("*"))
        except OSError as error:
            _fail(f"Could not enumerate {creative_root}: {error}")
        for path in candidates:
            if path.is_symlink():
                _fail(f"Creative runtime inventory rejects symlinks: {path}")
            if path.is_dir():
                continue
            if not path.is_file():
                _fail(f"Creative runtime input is not a regular file: {path}")
            try:
                size = path.stat().st_size
            except OSError as error:
                _fail(f"Could not stat creative asset {path}: {error}")
            if size <= 0:
                _fail(f"Creative runtime asset is empty: {path}")
            relative = path.relative_to(resolved).as_posix()
            entries.append(
                CreativeAssetEntry(
                    path=relative,
                    category=_category(relative),
                    bytes=size,
                    sha256=_sha256(path),
                )
            )

    entries.sort(key=lambda entry: entry.path)
    paths = [entry.path for entry in entries]
    if len(paths) != len(set(paths)):
        _fail("Creative runtime inventory contains duplicate paths")
    return tuple(entries)


def build_inventory(
    root: Path,
    candidate_sha: str,
    *,
    validate_runtime_inputs: bool = True,
) -> dict[str, object]:
    normalized_sha = candidate_sha.strip().lower()
    if not SHA_PATTERN.fullmatch(normalized_sha):
        _fail("candidate SHA must be exactly 40 lowercase hexadecimal characters")

    resolved = root.expanduser().resolve()
    if validate_runtime_inputs:
        try:
            verify_release_source_assets(resolved)
            verify_asset_ownership(resolved)
        except Exception as error:
            _fail(f"Creative runtime inputs failed release validation: {error}")

    entries = collect_creative_asset_entries(resolved)
    serialized_entries = [asdict(entry) for entry in entries]
    identity_payload = {
        "kind": INVENTORY_KIND,
        "candidateCommit": normalized_sha,
        "assets": serialized_entries,
    }
    inventory_sha256 = hashlib.sha256(_canonical_bytes(identity_payload)).hexdigest()
    return {
        **identity_payload,
        "assetCount": len(serialized_entries),
        "inventorySha256": inventory_sha256,
        "reviewSummary": {
            "provenanceVerified": 0,
            "provenanceUnverified": len(serialized_entries),
            "licenseReviewComplete": 0,
            "licenseReviewRequired": len(serialized_entries),
        },
        "governanceStatement": (
            "This file inventories bytes and unresolved review status only. "
            "It is not evidence of authorship, ownership, permission, or license approval."
        ),
    }


def write_inventory(path: Path, inventory: dict[str, object]) -> None:
    resolved = path.expanduser().resolve()
    resolved.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(inventory, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    temporary = resolved.with_name(resolved.name + ".tmp")
    try:
        temporary.write_text(payload, encoding="utf-8")
        temporary.replace(resolved)
    except OSError as error:
        try:
            temporary.unlink(missing_ok=True)
        except OSError:
            pass
        _fail(f"Could not write creative asset inventory {resolved}: {error}")


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build a candidate-bound inventory of every shipped sprite, font, "
            "music track, and SFX file without fabricating provenance approval."
        )
    )
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        inventory = build_inventory(args.root, args.candidate_sha)
        write_inventory(args.output, inventory)
    except CreativeAssetInventoryError as error:
        raise SystemExit(str(error)) from error
    print(
        f"Inventoried {inventory['assetCount']} creative runtime asset(s); "
        "all provenance/license review remains explicitly unresolved."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
