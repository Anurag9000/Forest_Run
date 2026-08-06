#!/usr/bin/env python3
"""Build a deterministic candidate-bound inventory of declared build dependencies."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Sequence

SHA40 = re.compile(r"^[0-9a-f]{40}$")
PLUGIN = re.compile(
    r'id\("(?P<name>[^"\s]+)"\)\s+version\s+"(?P<version>[^"\s]+)"'
)
MODULE = re.compile(
    r'(?P<configuration>[A-Za-z][A-Za-z0-9]*)\("(?P<group>[^:"\s]+):'
    r'(?P<name>[^:"\s]+):(?P<version>[^"\s]+)"\)'
)
GRADLE = re.compile(r"gradle-(?P<version>[0-9][0-9A-Za-z.\-]*)-bin\.zip")
PYTHON = re.compile(r"^(?P<name>[A-Za-z0-9_.-]+)==(?P<version>[^\s#]+)$")
SOURCE_PATHS = (
    Path("build.gradle.kts"),
    Path("app/build.gradle.kts"),
    Path("gradle/wrapper/gradle-wrapper.properties"),
    Path("scripts/requirements-ci.txt"),
)


class DeclaredDependencyInventoryError(ValueError):
    pass


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _read(root: Path, relative: Path) -> tuple[str, str]:
    path = root / relative
    try:
        metadata = path.lstat()
    except FileNotFoundError as exc:
        raise DeclaredDependencyInventoryError(
            f"required dependency source is missing: {relative.as_posix()}"
        ) from exc
    if path.is_symlink() or not path.is_file():
        raise DeclaredDependencyInventoryError(
            f"dependency source must be a regular non-symlink file: {relative.as_posix()}"
        )
    raw = path.read_bytes()
    if not raw or len(raw) != metadata.st_size:
        raise DeclaredDependencyInventoryError(
            f"dependency source is empty or unstable: {relative.as_posix()}"
        )
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise DeclaredDependencyInventoryError(
            f"dependency source is not UTF-8: {relative.as_posix()}"
        ) from exc
    return text, hashlib.sha256(raw).hexdigest()


def build_inventory(root: Path, candidate_sha: str) -> dict[str, object]:
    root = root.expanduser().resolve()
    if not SHA40.fullmatch(candidate_sha):
        raise DeclaredDependencyInventoryError(
            "candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )

    sources: dict[str, tuple[str, str]] = {
        relative.as_posix(): _read(root, relative) for relative in SOURCE_PATHS
    }
    root_build = sources["build.gradle.kts"][0]
    app_build = sources["app/build.gradle.kts"][0]
    wrapper = sources["gradle/wrapper/gradle-wrapper.properties"][0]
    requirements = sources["scripts/requirements-ci.txt"][0]

    plugins = sorted(
        {
            (match.group("name"), match.group("version"))
            for match in PLUGIN.finditer(root_build)
        }
    )
    modules = sorted(
        {
            (
                match.group("configuration"),
                match.group("group"),
                match.group("name"),
                match.group("version"),
            )
            for match in MODULE.finditer(app_build)
        }
    )
    gradle_match = GRADLE.search(wrapper)
    python_packages = sorted(
        match.groups()
        for line in requirements.splitlines()
        if (line.strip() and not line.lstrip().startswith("#"))
        for match in [PYTHON.fullmatch(line.strip())]
        if match is not None
    )

    if len(plugins) != 2:
        raise DeclaredDependencyInventoryError(
            "expected exactly the Android and Kotlin build plugins"
        )
    if not modules:
        raise DeclaredDependencyInventoryError("no direct Gradle modules were found")
    if gradle_match is None:
        raise DeclaredDependencyInventoryError("Gradle wrapper version was not found")
    non_comment_requirements = [
        line.strip()
        for line in requirements.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if len(python_packages) != len(non_comment_requirements):
        raise DeclaredDependencyInventoryError(
            "every Python CI requirement must use an exact == pin"
        )

    entries: list[dict[str, str]] = [
        {"ecosystem": "gradle-plugin", "name": name, "version": version}
        for name, version in plugins
    ]
    entries.append(
        {
            "ecosystem": "gradle-wrapper",
            "name": "gradle",
            "version": gradle_match.group("version"),
        }
    )
    entries.extend(
        {
            "ecosystem": "maven",
            "configuration": configuration,
            "name": f"{group}:{name}",
            "version": version,
        }
        for configuration, group, name, version in modules
    )
    entries.extend(
        {"ecosystem": "python", "name": name, "version": version}
        for name, version in python_packages
    )
    entries.sort(
        key=lambda entry: (
            entry["ecosystem"],
            entry["name"],
            entry.get("configuration", ""),
            entry["version"],
        )
    )

    source_digests = [
        {"path": path, "sha256": digest}
        for path, (_, digest) in sorted(sources.items())
    ]
    inventory_sha256 = hashlib.sha256(_canonical_bytes(entries)).hexdigest()
    return {
        "schemaVersion": 1,
        "candidateSha": candidate_sha,
        "scope": "declared-direct-dependencies-only",
        "entryCount": len(entries),
        "inventorySha256": inventory_sha256,
        "sourceFiles": source_digests,
        "entries": entries,
        "limitations": [
            "This is not a resolved transitive dependency graph.",
            "This is not an SBOM, vulnerability report, or licence determination.",
        ],
    }


def publish(output: Path, payload: dict[str, object]) -> None:
    output = Path(os.path.abspath(os.fspath(output.expanduser())))
    if output.is_symlink():
        raise DeclaredDependencyInventoryError("output must not be a symbolic link")
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
            raise DeclaredDependencyInventoryError("output became a symbolic link")
        os.replace(temporary, output)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        payload = build_inventory(args.root, args.candidate_sha)
        output = args.output if args.output.is_absolute() else args.root / args.output
        publish(output, payload)
    except (DeclaredDependencyInventoryError, OSError) as exc:
        print(f"declared dependency inventory error: {exc}", file=os.sys.stderr)
        return 1
    print(
        f"inventoried {payload['entryCount']} declared dependencies for "
        f"{payload['candidateSha']} ({payload['inventorySha256']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
