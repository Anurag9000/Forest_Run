#!/usr/bin/env python3
"""Build a deterministic CycloneDX inventory from a Gradle dependency report."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import tempfile
import urllib.parse
import uuid
from pathlib import Path
from typing import Iterable, Sequence

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
        metadata = path.lstat()
    except FileNotFoundError as exc:
        raise ResolvedDependencySbomError(f"{label} is missing: {path}") from exc
    if path.is_symlink() or not path.is_file():
        raise ResolvedDependencySbomError(
            f"{label} must be a regular non-symlink file: {path}"
        )
    raw = path.read_bytes()
    if not raw or len(raw) != metadata.st_size:
        raise ResolvedDependencySbomError(f"{label} is empty or unstable: {path}")
    return raw


def resolved_coordinates(report_text: str) -> list[tuple[str, str, str]]:
    found: set[tuple[str, str, str]] = set()
    versions_by_module: dict[tuple[str, str], set[str]] = {}
    for raw_line in report_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("No dependencies"):
            continue
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
    components = [
        {
            "type": "library",
            "bom-ref": purl(group, name, version),
            "group": group,
            "name": name,
            "version": version,
            "purl": purl(group, name, version),
        }
        for group, name, version in coordinates
    ]
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
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output.name}.", suffix=".tmp", dir=output.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(canonical_bytes(payload))
            handle.flush()
            os.fsync(handle.fileno())
        if output.is_symlink():
            raise ResolvedDependencySbomError("output became a symbolic link")
        os.replace(temporary, output)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


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
