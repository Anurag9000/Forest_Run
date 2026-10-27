#!/usr/bin/env python3
"""Build a deterministic candidate-bound inventory of declared build dependencies."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from typing import Sequence

from stable_file_reader import (
    StableFileError,
    atomic_write_bytes,
    checked_directory_path,
    consume_regular_file,
    ensure_directory_exists,
)

SHA40 = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PLUGINS = {"com.android.application", "org.jetbrains.kotlin.android"}
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
MAX_SOURCE_BYTES = 8 * 1024 * 1024


class DeclaredDependencyInventoryError(ValueError):
    pass


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _strip_kotlin_comments(text: str) -> str:
    """Blank Kotlin comments while retaining offsets, newlines, and string contents."""
    characters = list(text)
    index = 0
    length = len(text)
    while index < length:
        if text.startswith("//", index):
            end = text.find("\n", index)
            if end < 0:
                end = length
            for position in range(index, end):
                characters[position] = " "
            index = end
            continue

        if text.startswith("/*", index):
            depth = 1
            characters[index] = characters[index + 1] = " "
            index += 2
            while index < length and depth:
                if text.startswith("/*", index):
                    depth += 1
                    characters[index] = characters[index + 1] = " "
                    index += 2
                elif text.startswith("*/", index):
                    depth -= 1
                    characters[index] = characters[index + 1] = " "
                    index += 2
                else:
                    if text[index] != "\n":
                        characters[index] = " "
                    index += 1
            continue

        if text.startswith('"""', index):
            index += 3
            while index < length and not text.startswith('"""', index):
                index += 1
            index = min(length, index + 3)
            continue

        if text[index] in {'"', "'"}:
            quote = text[index]
            index += 1
            while index < length:
                if text[index] == "\\":
                    index += 2
                elif text[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            continue

        index += 1
    return "".join(characters)


def _kotlin_code_positions(text: str) -> list[bool]:
    """Mark source positions outside Kotlin string and character literals."""
    code = [True] * len(text)
    index = 0
    length = len(text)
    while index < length:
        if text.startswith('"""', index):
            start = index
            index += 3
            while index < length and not text.startswith('"""', index):
                index += 1
            index = min(length, index + 3)
            code[start:index] = [False] * (index - start)
            continue
        if text[index] in {'"', "'"}:
            start = index
            quote = text[index]
            index += 1
            while index < length:
                if text[index] == "\\":
                    index += 2
                elif text[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            index = min(index, length)
            code[start:index] = [False] * (index - start)
            continue
        index += 1
    return code


def _read(root: Path, relative: Path) -> tuple[str, str]:
    path = root / relative
    try:
        raw, digest, _ = consume_regular_file(
            path,
            f"dependency source {relative.as_posix()}",
            MAX_SOURCE_BYTES,
            retain_bytes=True,
        )
    except StableFileError as exc:
        raise DeclaredDependencyInventoryError(str(exc)) from exc
    assert raw is not None
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise DeclaredDependencyInventoryError(
            f"dependency source is not UTF-8: {relative.as_posix()}"
        ) from exc
    return text, digest


def build_inventory(root: Path, candidate_sha: str) -> dict[str, object]:
    try:
        root = checked_directory_path(root, "dependency source root")
    except StableFileError as exc:
        raise DeclaredDependencyInventoryError(str(exc)) from exc
    if not SHA40.fullmatch(candidate_sha):
        raise DeclaredDependencyInventoryError(
            "candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )

    sources: dict[str, tuple[str, str]] = {
        relative.as_posix(): _read(root, relative) for relative in SOURCE_PATHS
    }
    root_build = _strip_kotlin_comments(sources["build.gradle.kts"][0])
    app_build = _strip_kotlin_comments(sources["app/build.gradle.kts"][0])
    wrapper = sources["gradle/wrapper/gradle-wrapper.properties"][0]
    requirements = sources["scripts/requirements-ci.txt"][0]

    root_code = _kotlin_code_positions(root_build)
    app_code = _kotlin_code_positions(app_build)
    plugins = sorted(
        {
            (match.group("name"), match.group("version"))
            for match in PLUGIN.finditer(root_build)
            if root_code[match.start()]
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
            if app_code[match.start()]
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

    if {name for name, _ in plugins} != EXPECTED_PLUGINS:
        raise DeclaredDependencyInventoryError(
            "expected the Android application and Kotlin Android build plugins"
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
    try:
        ensure_directory_exists(output.parent, "declared dependency inventory parent directory")
        atomic_write_bytes(
            output,
            _canonical_bytes(payload),
            "declared dependency inventory",
            reject_symlink_leaf=True,
        )
    except StableFileError as exc:
        if "symbolic link" in str(exc):
            raise DeclaredDependencyInventoryError("output became a symbolic link") from exc
        raise DeclaredDependencyInventoryError(f"could not safely publish output: {exc}") from exc


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
