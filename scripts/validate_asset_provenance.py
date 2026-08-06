#!/usr/bin/env python3
"""Validate source asset provenance coverage without inventing legal approval."""

from __future__ import annotations

import argparse
import datetime as dt
import fnmatch
import os
from pathlib import Path
from typing import Sequence

from strict_json import StrictJsonError, load_file

ASSET_ROOTS = (
    Path("app/src/main/assets"),
    Path("app/src/main/res/raw"),
    Path("app/src/main/res/drawable"),
)
MIPMAP_PARENT = Path("app/src/main/res")
REQUIRED_RULE_FIELDS = {
    "pattern",
    "assetKind",
    "status",
    "source",
    "license",
    "attribution",
    "reviewer",
    "reviewedAt",
}
ALLOWED_STATUSES = {
    "review-required",
    "approved-first-party",
    "approved-third-party",
    "excluded-generated",
}
APPROVED_STATUSES = {
    "approved-first-party",
    "approved-third-party",
    "excluded-generated",
}


class AssetProvenanceError(ValueError):
    pass


def regular_json(path: Path) -> dict[str, object]:
    path = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        metadata = path.lstat()
    except FileNotFoundError as exc:
        raise AssetProvenanceError(f"provenance registry is missing: {path}") from exc
    if path.is_symlink() or not path.is_file():
        raise AssetProvenanceError(
            f"provenance registry must be a regular non-symlink file: {path}"
        )
    if metadata.st_size <= 0:
        raise AssetProvenanceError("provenance registry must not be empty")
    try:
        value = load_file(path, require_object=True)
    except (StrictJsonError, OSError) as exc:
        raise AssetProvenanceError(f"invalid provenance registry: {exc}") from exc
    if not isinstance(value, dict):
        raise AssetProvenanceError("provenance registry root must be an object")
    return value


def source_assets(root: Path) -> list[str]:
    root = root.expanduser().resolve()
    candidates: list[Path] = []
    for relative in ASSET_ROOTS:
        candidate = root / relative
        if candidate.exists():
            candidates.append(candidate)
    res = root / MIPMAP_PARENT
    if res.is_dir() and not res.is_symlink():
        candidates.extend(
            path
            for path in sorted(res.iterdir())
            if path.name.startswith("mipmap-")
        )

    assets: list[str] = []
    visited_roots: set[Path] = set()
    for candidate in candidates:
        if candidate in visited_roots:
            continue
        visited_roots.add(candidate)
        if candidate.is_symlink() or not candidate.is_dir():
            raise AssetProvenanceError(
                f"asset root must be a regular directory: {candidate}"
            )
        for path in sorted(candidate.rglob("*")):
            if path.is_symlink():
                raise AssetProvenanceError(
                    f"source assets must not contain symbolic links: {path}"
                )
            if path.is_file():
                assets.append(path.relative_to(root).as_posix())
            elif not path.is_dir():
                raise AssetProvenanceError(
                    f"unsupported source asset entry: {path}"
                )
    if not assets:
        raise AssetProvenanceError("no source assets were found")
    if len(assets) != len(set(assets)):
        raise AssetProvenanceError("source asset scan produced duplicate paths")
    return assets


def validated_rules(payload: dict[str, object]) -> list[dict[str, str]]:
    if set(payload) != {"schemaVersion", "rules"}:
        raise AssetProvenanceError(
            "registry must contain only schemaVersion and rules"
        )
    if payload["schemaVersion"] != 1:
        raise AssetProvenanceError("unsupported provenance schemaVersion")
    raw_rules = payload["rules"]
    if not isinstance(raw_rules, list) or not raw_rules:
        raise AssetProvenanceError("rules must be a non-empty array")

    rules: list[dict[str, str]] = []
    patterns: set[str] = set()
    for index, raw in enumerate(raw_rules):
        if not isinstance(raw, dict) or set(raw) != REQUIRED_RULE_FIELDS:
            raise AssetProvenanceError(
                f"rule {index} must contain exactly the required fields"
            )
        if not all(isinstance(value, str) for value in raw.values()):
            raise AssetProvenanceError(f"rule {index} fields must be strings")
        rule = {key: str(raw[key]).strip() for key in REQUIRED_RULE_FIELDS}
        pattern = rule["pattern"]
        if not pattern or pattern.startswith("/") or ".." in Path(pattern).parts:
            raise AssetProvenanceError(f"rule {index} has an unsafe pattern")
        if pattern in patterns:
            raise AssetProvenanceError(f"duplicate provenance pattern: {pattern}")
        patterns.add(pattern)
        if not rule["assetKind"]:
            raise AssetProvenanceError(f"rule {index} assetKind must not be empty")
        status = rule["status"]
        if status not in ALLOWED_STATUSES:
            raise AssetProvenanceError(
                f"rule {index} has unsupported status: {status}"
            )
        if status in APPROVED_STATUSES:
            for field in ("source", "license", "reviewer", "reviewedAt"):
                if not rule[field]:
                    raise AssetProvenanceError(
                        f"approved rule {index} requires {field}"
                    )
            try:
                reviewed = dt.date.fromisoformat(rule["reviewedAt"])
            except ValueError as exc:
                raise AssetProvenanceError(
                    f"approved rule {index} reviewedAt must be YYYY-MM-DD"
                ) from exc
            if reviewed > dt.date.today():
                raise AssetProvenanceError(
                    f"approved rule {index} reviewedAt must not be in the future"
                )
        elif any(
            rule[field]
            for field in ("source", "license", "attribution", "reviewer", "reviewedAt")
        ):
            raise AssetProvenanceError(
                f"review-required rule {index} must not contain unreviewed claims"
            )
        rules.append(rule)
    return rules


def validate(
    root: Path,
    registry: Path,
    require_approved: bool = False,
) -> dict[str, object]:
    assets = source_assets(root)
    rules = validated_rules(regular_json(registry))
    matched_rules: set[int] = set()
    unresolved: list[str] = []
    coverage: list[dict[str, str]] = []

    for asset in assets:
        matches = [
            (index, rule)
            for index, rule in enumerate(rules)
            if fnmatch.fnmatchcase(asset, rule["pattern"])
        ]
        if not matches:
            raise AssetProvenanceError(
                f"source asset is not covered by provenance registry: {asset}"
            )
        if len(matches) > 1:
            patterns = [rule["pattern"] for _, rule in matches]
            raise AssetProvenanceError(
                f"source asset matches overlapping provenance rules: {asset}: {patterns}"
            )
        index, rule = matches[0]
        matched_rules.add(index)
        coverage.append(
            {
                "path": asset,
                "pattern": rule["pattern"],
                "status": rule["status"],
            }
        )
        if rule["status"] not in APPROVED_STATUSES:
            unresolved.append(asset)

    stale = [
        rule["pattern"]
        for index, rule in enumerate(rules)
        if index not in matched_rules
    ]
    if stale:
        raise AssetProvenanceError(
            f"provenance rules do not match any source asset: {stale}"
        )
    if require_approved and unresolved:
        raise AssetProvenanceError(
            f"asset provenance review remains incomplete for {len(unresolved)} files"
        )

    return {
        "assetCount": len(assets),
        "ruleCount": len(rules),
        "approvedAssetCount": len(assets) - len(unresolved),
        "reviewRequiredAssetCount": len(unresolved),
        "coverage": coverage,
    }


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--root", type=Path, default=Path.cwd())
    result.add_argument(
        "--registry",
        type=Path,
        default=Path("asset-provenance.json"),
    )
    result.add_argument("--require-approved", action="store_true")
    return result


def main(argv: Sequence[str] | None = None) -> int:
    args = parser().parse_args(argv)
    root = args.root.expanduser().resolve()
    registry = args.registry
    if not registry.is_absolute():
        registry = root / registry
    try:
        report = validate(root, registry, args.require_approved)
    except (AssetProvenanceError, OSError) as exc:
        print(f"asset provenance error: {exc}", file=os.sys.stderr)
        return 1
    print(
        f"covered {report['assetCount']} source assets with {report['ruleCount']} rules; "
        f"approved={report['approvedAssetCount']} "
        f"review-required={report['reviewRequiredAssetCount']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
