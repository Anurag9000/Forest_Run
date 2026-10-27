#!/usr/bin/env python3
"""Build a deterministic CycloneDX inventory from a Gradle dependency report."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import urllib.parse
import uuid
from pathlib import Path
from typing import Iterable, Sequence

from stable_file_reader import StableFileError, atomic_write_bytes, ensure_directory_exists

SHA40 = re.compile(r"^[0-9a-f]{40}$")
COORDINATE = re.compile(
    r"(?P<group>[A-Za-z0-9_.-]+):"
    r"(?P<name>[A-Za-z0-9_.-]+):"
    r"(?P<version>[^\s()]+)"
)
ARROW_VERSION = re.compile(r"->\s*(?P<version>[^\s()]+)")
NAMESPACE = uuid.UUID("534dbf08-98f7-5b36-bce7-56e3f5553f0e")


class ResolvedDependencySbomError(ValueError):
    pass


def canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def regular_file(path: Path, label: str) -> bytes:
    path = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise ResolvedDependencySbomError(f"{label} is missing: {path}") from exc
    if not stat.S_ISREG(before.st_mode):
        raise ResolvedDependencySbomError(
            f"{label} must be a regular non-symlink file: {path}"
        )
    if before.st_size <= 0:
        raise ResolvedDependencySbomError(f"{label} is empty or unstable: {path}")

    flags = os.O_RDONLY
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    if hasattr(os, "O_NONBLOCK"):
        flags |= os.O_NONBLOCK
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ResolvedDependencySbomError(
            f"{label} could not be safely opened: {path}: {exc}"
        ) from exc

    identity_fields = (
        "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns"
    )
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode) or any(
            getattr(before, field) != getattr(opened, field)
            for field in identity_fields
        ):
            raise ResolvedDependencySbomError(
                f"{label} changed while being opened: {path}"
            )
        remaining = before.st_size
        chunks: list[bytes] = []
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise ResolvedDependencySbomError(
                    f"{label} changed while being read: {path}"
                )
            chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(descriptor, 1):
            raise ResolvedDependencySbomError(
                f"{label} changed while being read: {path}"
            )
        after = os.fstat(descriptor)
    except OSError as exc:
        raise ResolvedDependencySbomError(
            f"{label} could not be safely read: {path}: {exc}"
        ) from exc
    finally:
        os.close(descriptor)

    try:
        after_path = path.lstat()
    except OSError as exc:
        raise ResolvedDependencySbomError(
            f"{label} changed while being read: {path}: {exc}"
        ) from exc
    if any(
        getattr(before, field) != getattr(after, field)
        or getattr(before, field) != getattr(after_path, field)
        for field in identity_fields
    ):
        raise ResolvedDependencySbomError(
            f"{label} changed while being read: {path}"
        )
    return b"".join(chunks)


def resolved_coordinates(report_text: str) -> list[tuple[str, str, str]]:
    found: set[tuple[str, str, str]] = set()
    versions_by_module: dict[tuple[str, str], set[str]] = {}
    for raw_line in report_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("No dependencies"):
            continue
        if re.search(r"(?:^|\s)FAILED(?:\s|$)", line):
            raise ResolvedDependencySbomError(
                f"resolved report contains a failed dependency: {line}"
            )
        match = COORDINATE.search(line)
        if match is None:
            continue
        group = match.group("group")
        name = match.group("name")
        version = match.group("version")
        arrow = ARROW_VERSION.search(line[match.end() :])
        if arrow is not None:
            version = arrow.group("version")
        version = version.rstrip(",")
        if not group or not name or not version or version in {"FAILED", "(*)"}:
            continue
        coordinate = (group, name, version)
        found.add(coordinate)
        versions_by_module.setdefault((group, name), set()).add(version)

    conflicts = {
        module: versions
        for module, versions in versions_by_module.items()
        if len(versions) > 1
    }
    if conflicts:
        rendered = ", ".join(
            f"{group}:{name}={sorted(versions)}"
            for (group, name), versions in sorted(conflicts.items())
        )
        raise ResolvedDependencySbomError(
            f"resolved report contains conflicting final versions: {rendered}"
        )
    if not found:
        raise ResolvedDependencySbomError(
            "resolved Gradle report did not contain any Maven coordinates"
        )
    return sorted(found)


def purl(group: str, name: str, version: str) -> str:
    namespace = urllib.parse.quote(group, safe=".")
    encoded_name = urllib.parse.quote(name, safe="._-")
    encoded_version = urllib.parse.quote(version, safe="._-")
    return f"pkg:maven/{namespace}/{encoded_name}@{encoded_version}"


def build_sbom(
    reports: Iterable[tuple[str, Path]],
    candidate_sha: str,
) -> dict[str, object]:
    if not SHA40.fullmatch(candidate_sha):
        raise ResolvedDependencySbomError(
            "candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )

    all_coordinates: set[tuple[str, str, str]] = set()
    source_reports: list[dict[str, str]] = []
    configurations: list[str] = []
    for configuration, report_path in reports:
        configuration = configuration.strip()
        if not configuration or configuration in configurations:
            raise ResolvedDependencySbomError(
                "configuration names must be non-empty and unique"
            )
        raw = regular_file(report_path, f"{configuration} dependency report")
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ResolvedDependencySbomError(
                f"{configuration} dependency report is not UTF-8"
            ) from exc
        coordinates = resolved_coordinates(text)
        all_coordinates.update(coordinates)
        configurations.append(configuration)
        source_reports.append(
            {
                "configuration": configuration,
                "path": report_path.as_posix(),
                "sha256": hashlib.sha256(raw).hexdigest(),
            }
        )

    coordinates = sorted(all_coordinates)
    components = sorted(
        (
            {
                "type": "library",
                "bom-ref": purl(group, name, version),
                "group": group,
                "name": name,
                "version": version,
                "purl": purl(group, name, version),
            }
            for group, name, version in coordinates
        ),
        key=lambda component: component["purl"],
    )
    identity = hashlib.sha256(canonical_bytes(components)).hexdigest()
    serial = uuid.uuid5(
        NAMESPACE,
        f"forest-run:{candidate_sha}:{identity}:{','.join(configurations)}",
    )
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "component": {
                "type": "application",
                "bom-ref": f"pkg:generic/forest-run@{candidate_sha}",
                "name": "Forest Run",
                "version": candidate_sha,
            },
            "properties": [
                {"name": "forest-run:candidate-sha", "value": candidate_sha},
                {
                    "name": "forest-run:resolved-configurations",
                    "value": ",".join(configurations),
                },
                {"name": "forest-run:component-set-sha256", "value": identity},
                {
                    "name": "forest-run:scope",
                    "value": "resolved-gradle-components",
                },
            ],
        },
        "components": components,
        "properties": [
            {
                "name": "forest-run:source-reports",
                "value": json.dumps(
                    source_reports,
                    sort_keys=True,
                    separators=(",", ":"),
                ),
            },
            {
                "name": "forest-run:limitations",
                "value": (
                    "This inventory does not assert licence compatibility, "
                    "vulnerability absence, artifact signatures, or repository trust."
                ),
            },
        ],
    }


def publish(output: Path, payload: dict[str, object]) -> None:
    output = Path(os.path.abspath(os.fspath(output.expanduser())))
    if output.is_symlink():
        raise ResolvedDependencySbomError("output must not be a symbolic link")
    try:
        ensure_directory_exists(output.parent, "resolved dependency SBOM parent directory")
        if output.is_symlink():
            raise ResolvedDependencySbomError("output became a symbolic link")
        atomic_write_bytes(
            output,
            canonical_bytes(payload),
            "resolved dependency SBOM",
            reject_symlink_leaf=True,
        )
    except StableFileError as exc:
        if "symbolic link" in str(exc):
            raise ResolvedDependencySbomError("output became a symbolic link") from exc
        raise ResolvedDependencySbomError(f"could not safely publish output: {exc}") from exc


def report_argument(raw: str) -> tuple[str, Path]:
    configuration, separator, path = raw.partition("=")
    if not separator or not configuration.strip() or not path.strip():
        raise argparse.ArgumentTypeError(
            "--report must use CONFIGURATION=PATH"
        )
    return configuration.strip(), Path(path.strip())


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--candidate-sha", required=True)
    result.add_argument(
        "--report",
        action="append",
        type=report_argument,
        required=True,
        help="Resolved Gradle dependency report as CONFIGURATION=PATH",
    )
    result.add_argument("--output", type=Path, required=True)
    return result


def main(argv: Sequence[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        payload = build_sbom(args.report, args.candidate_sha)
        publish(args.output, payload)
    except (ResolvedDependencySbomError, OSError) as exc:
        print(f"resolved dependency SBOM error: {exc}", file=os.sys.stderr)
        return 1
    print(
        f"inventoried {len(payload['components'])} resolved components for "
        f"{args.candidate_sha} ({payload['serialNumber']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
