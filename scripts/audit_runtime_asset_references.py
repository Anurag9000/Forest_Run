#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, NoReturn

from verify_release_source_assets import ASSET_PATHS_SOURCE, ASSETS_ROOT, parse_asset_paths

ROOT = Path(__file__).resolve().parent.parent

# These pairs are checked-in aliases today: their base/flying files are byte-identical.
# They are not asserted to be creatively approved. The allowlist only prevents an
# existing intentional source alias from being confused with a newly introduced
# accidental duplicate. If replacement art makes a pair distinct, that is accepted.
KNOWN_CONTENT_ALIAS_GROUPS = frozenset(
    {
        frozenset(
            {
                f"sprites/birds/{name}_4frames.png",
                f"sprites/birds/{name}_flying.png",
            }
        )
        for name in ("duck", "tit", "chickadee", "owl", "eagle")
    }
)


class RuntimeAssetReferenceError(RuntimeError):
    """Raised when packaged runtime assets drift from their declared ownership."""


@dataclass(frozen=True)
class RuntimeAssetReferenceEvidence:
    declared_asset_count: int
    packaged_asset_count: int
    duplicate_content_groups: int
    allowed_duplicate_content_groups: int


def _fail(message: str) -> NoReturn:
    raise RuntimeAssetReferenceError(message)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as error:
        _fail(f"Could not hash runtime asset {path}: {error}")
    return digest.hexdigest()


def _discover_assets(assets_root: Path) -> tuple[str, ...]:
    if not assets_root.is_dir():
        _fail(f"Missing runtime assets directory: {assets_root}")

    discovered: list[str] = []
    try:
        candidates = sorted(assets_root.rglob("*"))
    except OSError as error:
        _fail(f"Could not enumerate runtime assets: {error}")

    for path in candidates:
        if path.is_symlink():
            _fail(f"Runtime assets must not contain symlinks: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            _fail(f"Runtime asset is not a regular file: {path}")
        relative = path.relative_to(assets_root).as_posix()
        if not relative or relative.startswith("/") or ".." in Path(relative).parts:
            _fail(f"Unsafe discovered runtime asset path: {relative!r}")
        discovered.append(relative)
    return tuple(discovered)


def _duplicate_groups(
    assets_root: Path,
    paths: Iterable[str],
) -> tuple[frozenset[str], ...]:
    by_digest: dict[str, set[str]] = {}
    for relative in paths:
        digest = _sha256(assets_root / relative)
        by_digest.setdefault(digest, set()).add(relative)
    return tuple(
        sorted(
            (frozenset(group) for group in by_digest.values() if len(group) > 1),
            key=lambda group: tuple(sorted(group)),
        )
    )


def audit_runtime_asset_references(
    asset_paths_source: Path,
    assets_root: Path,
    *,
    allowed_duplicate_groups: frozenset[frozenset[str]] = KNOWN_CONTENT_ALIAS_GROUPS,
) -> RuntimeAssetReferenceEvidence:
    try:
        source = asset_paths_source.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        _fail(f"Could not read AssetPaths source {asset_paths_source}: {error}")

    declared = tuple(sorted(parse_asset_paths(source)))
    discovered = _discover_assets(assets_root)
    declared_set = set(declared)
    discovered_set = set(discovered)

    missing = sorted(declared_set - discovered_set)
    if missing:
        _fail("Declared runtime assets are missing: " + ", ".join(missing))

    orphaned = sorted(discovered_set - declared_set)
    if orphaned:
        _fail(
            "Packaged runtime assets have no AssetPaths owner: "
            + ", ".join(orphaned)
        )

    duplicates = _duplicate_groups(assets_root, discovered)
    unexpected = [group for group in duplicates if group not in allowed_duplicate_groups]
    if unexpected:
        rendered = "; ".join(
            "[" + ", ".join(sorted(group)) + "]" for group in unexpected
        )
        _fail("Unexpected byte-identical runtime asset group(s): " + rendered)

    return RuntimeAssetReferenceEvidence(
        declared_asset_count=len(declared),
        packaged_asset_count=len(discovered),
        duplicate_content_groups=len(duplicates),
        allowed_duplicate_content_groups=sum(
            1 for group in duplicates if group in allowed_duplicate_groups
        ),
    )


def verify_repository(root: Path = ROOT) -> RuntimeAssetReferenceEvidence:
    resolved = root.expanduser().resolve()
    return audit_runtime_asset_references(
        resolved / ASSET_PATHS_SOURCE,
        resolved / ASSETS_ROOT,
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Fail closed when packaged app/src/main/assets files are orphaned, "
            "missing, symlinked, or unexpectedly byte-identical."
        )
    )
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        evidence = verify_repository(args.root)
    except RuntimeAssetReferenceError as error:
        raise SystemExit(str(error)) from error

    if args.json:
        print(json.dumps(asdict(evidence), sort_keys=True))
    else:
        print(
            f"Verified {evidence.packaged_asset_count} declared runtime assets; "
            f"{evidence.allowed_duplicate_content_groups} known content alias group(s)."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
