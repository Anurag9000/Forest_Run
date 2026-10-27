#!/usr/bin/env python3
"""Cross-validate candidate-bound declared and resolved dependency evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import urllib.parse
from pathlib import Path, PurePosixPath
from typing import Sequence

import strict_json
from stable_file_reader import (
    StableFileError,
    checked_directory_path,
    consume_regular_file,
)

SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
EXPECTED_CONFIGURATIONS = {
    "releaseRuntimeClasspath",
    "debugAndroidTestRuntimeClasspath",
}
EXPECTED_DECLARED_SOURCE_PATHS = {
    "build.gradle.kts",
    "app/build.gradle.kts",
    "gradle/wrapper/gradle-wrapper.properties",
    "scripts/requirements-ci.txt",
}
MAX_REFERENCED_FILE_BYTES = 512 * 1024 * 1024


class SupplyChainEvidenceError(ValueError):
    pass


def _canonical_bytes(value: object) -> bytes:
    try:
        return (
            json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
            + "\n"
        ).encode("utf-8")
    except UnicodeEncodeError as exc:
        raise SupplyChainEvidenceError(
            "evidence contains a string that is not valid Unicode"
        ) from exc


def _regular_non_symlink(path: Path, label: str) -> Path:
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        metadata = absolute.lstat()
    except FileNotFoundError as exc:
        raise SupplyChainEvidenceError(f"{label} is missing: {absolute}") from exc
    if absolute.is_symlink() or not absolute.is_file() or metadata.st_size <= 0:
        raise SupplyChainEvidenceError(
            f"{label} must be a non-empty regular non-symlink file: {absolute}"
        )
    return absolute


def _object(path: Path, label: str) -> dict[str, object]:
    regular = _regular_non_symlink(path, label)
    try:
        value = strict_json.load_file(regular, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise SupplyChainEvidenceError(f"invalid {label}: {exc}") from exc
    assert isinstance(value, dict)
    return value


def _string(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise SupplyChainEvidenceError(f"{label} must be a non-empty string")
    try:
        value.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise SupplyChainEvidenceError(f"{label} contains invalid Unicode") from exc
    return value


def _list(value: object, label: str) -> list[object]:
    if not isinstance(value, list):
        raise SupplyChainEvidenceError(f"{label} must be a list")
    return value


def _repository_relative_path(value: object, label: str) -> str:
    raw = _string(value, label)
    path = PurePosixPath(raw)
    if (
        "\\" in raw
        or "\x00" in raw
        or ":" in raw
        or any(ord(character) < 32 or ord(character) == 127 for character in raw)
        or path.is_absolute()
        or not path.parts
        or any(part in {"", ".", ".."} for part in path.parts)
        or path.as_posix() != raw
    ):
        raise SupplyChainEvidenceError(
            f"{label} must be a canonical repository-relative POSIX path"
        )
    return raw


def _properties(value: object, label: str) -> dict[str, str]:
    properties = _list(value, label)
    result: dict[str, str] = {}
    for index, item in enumerate(properties):
        if not isinstance(item, dict):
            raise SupplyChainEvidenceError(f"{label}[{index}] must be an object")
        name = _string(item.get("name"), f"{label}[{index}].name")
        property_value = _string(item.get("value"), f"{label}[{index}].value")
        if name in result:
            raise SupplyChainEvidenceError(f"duplicate property name in {label}: {name}")
        result[name] = property_value
    return result


def _canonical_maven_purl(group: str, name: str, version: str) -> str:
    """Match build_resolved_dependency_sbom.purl without trusting SBOM identity text."""
    namespace = urllib.parse.quote(group, safe=".")
    encoded_name = urllib.parse.quote(name, safe="._-")
    encoded_version = urllib.parse.quote(version, safe="._-")
    return f"pkg:maven/{namespace}/{encoded_name}@{encoded_version}"


def _verify_referenced_file(
    root: Path,
    relative: str,
    expected_digest: str,
    label: str,
) -> None:
    try:
        _, actual_digest, _ = consume_regular_file(
            root / relative,
            label,
            MAX_REFERENCED_FILE_BYTES,
            retain_bytes=False,
        )
    except StableFileError as exc:
        raise SupplyChainEvidenceError(str(exc)) from exc
    if actual_digest != expected_digest:
        raise SupplyChainEvidenceError(
            f"{label} digest does not match referenced file: {relative}"
        )


def validate_declared(
    payload: dict[str, object],
    expected_sha: str,
    *,
    repository_root: Path | None = None,
) -> set[tuple[str, str]]:
    if type(payload.get("schemaVersion")) is not int or payload["schemaVersion"] != 1:
        raise SupplyChainEvidenceError("declared schemaVersion must equal 1")
    if payload.get("candidateSha") != expected_sha:
        raise SupplyChainEvidenceError("declared candidateSha does not match expected commit")
    if payload.get("scope") != "declared-direct-dependencies-only":
        raise SupplyChainEvidenceError("declared scope is not the expected direct-dependency scope")

    entries = _list(payload.get("entries"), "declared.entries")
    if not entries:
        raise SupplyChainEvidenceError("declared.entries must not be empty")
    entry_count = payload.get("entryCount")
    if type(entry_count) is not int or entry_count != len(entries):
        raise SupplyChainEvidenceError("declared entryCount does not match entries")
    inventory_digest = _string(payload.get("inventorySha256"), "declared.inventorySha256")
    if not SHA256.fullmatch(inventory_digest):
        raise SupplyChainEvidenceError("declared inventorySha256 must be lowercase SHA-256")
    if hashlib.sha256(_canonical_bytes(entries)).hexdigest() != inventory_digest:
        raise SupplyChainEvidenceError("declared inventorySha256 does not match entries")

    source_files = _list(payload.get("sourceFiles"), "declared.sourceFiles")
    if not source_files:
        raise SupplyChainEvidenceError("declared.sourceFiles must not be empty")
    seen_source_paths: set[str] = set()
    for index, item in enumerate(source_files):
        if not isinstance(item, dict):
            raise SupplyChainEvidenceError(f"declared.sourceFiles[{index}] must be an object")
        source_path = _repository_relative_path(
            item.get("path"), f"declared.sourceFiles[{index}].path"
        )
        digest = _string(item.get("sha256"), f"declared.sourceFiles[{index}].sha256")
        if source_path in seen_source_paths:
            raise SupplyChainEvidenceError(f"duplicate declared source path: {source_path}")
        if not SHA256.fullmatch(digest):
            raise SupplyChainEvidenceError(f"invalid declared source SHA-256: {source_path}")
        if repository_root is not None:
            _verify_referenced_file(
                repository_root,
                source_path,
                digest,
                f"declared source file {source_path}",
            )
        seen_source_paths.add(source_path)
    if seen_source_paths != EXPECTED_DECLARED_SOURCE_PATHS:
        missing = sorted(EXPECTED_DECLARED_SOURCE_PATHS - seen_source_paths)
        extra = sorted(seen_source_paths - EXPECTED_DECLARED_SOURCE_PATHS)
        details = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if extra:
            details.append("extra=" + ",".join(extra))
        raise SupplyChainEvidenceError(
            "declared sourceFiles do not match the required dependency inputs ("
            + "; ".join(details)
            + ")"
        )

    runtime_direct_modules: set[tuple[str, str]] = set()
    for index, item in enumerate(entries):
        if not isinstance(item, dict):
            raise SupplyChainEvidenceError(f"declared.entries[{index}] must be an object")
        if item.get("ecosystem") != "maven":
            continue
        configuration = _string(item.get("configuration"), f"declared.entries[{index}].configuration")
        if configuration not in {"implementation", "androidTestImplementation"}:
            continue
        coordinate = _string(item.get("name"), f"declared.entries[{index}].name")
        group, separator, name = coordinate.partition(":")
        if not separator or not group or not name or ":" in name:
            raise SupplyChainEvidenceError(f"invalid declared Maven coordinate: {coordinate}")
        runtime_direct_modules.add((group, name))
    if not runtime_direct_modules:
        raise SupplyChainEvidenceError(
            "declared inventory must contain implementation or androidTestImplementation modules"
        )
    return runtime_direct_modules


def validate_resolved(
    payload: dict[str, object],
    expected_sha: str,
    runtime_direct_modules: set[tuple[str, str]],
    *,
    repository_root: Path | None = None,
) -> None:
    if payload.get("bomFormat") != "CycloneDX" or payload.get("specVersion") != "1.6":
        raise SupplyChainEvidenceError("resolved evidence must be CycloneDX 1.6")
    if type(payload.get("version")) is not int or payload["version"] != 1:
        raise SupplyChainEvidenceError("resolved CycloneDX version must equal 1")

    metadata = payload.get("metadata")
    if not isinstance(metadata, dict):
        raise SupplyChainEvidenceError("resolved.metadata must be an object")
    component = metadata.get("component")
    if not isinstance(component, dict):
        raise SupplyChainEvidenceError("resolved.metadata.component must be an object")
    if component.get("name") != "Forest Run" or component.get("version") != expected_sha:
        raise SupplyChainEvidenceError("resolved metadata component is not bound to expected candidate")

    metadata_properties = _properties(metadata.get("properties"), "resolved.metadata.properties")
    if metadata_properties.get("forest-run:candidate-sha") != expected_sha:
        raise SupplyChainEvidenceError("resolved candidate property does not match expected commit")
    if metadata_properties.get("forest-run:scope") != "resolved-gradle-components":
        raise SupplyChainEvidenceError("resolved scope property is invalid")
    configured = set(
        filter(None, metadata_properties.get("forest-run:resolved-configurations", "").split(","))
    )
    if configured != EXPECTED_CONFIGURATIONS:
        raise SupplyChainEvidenceError(
            "resolved configurations must contain exactly releaseRuntimeClasspath and "
            "debugAndroidTestRuntimeClasspath"
        )
    component_set_digest = metadata_properties.get("forest-run:component-set-sha256", "")
    if not SHA256.fullmatch(component_set_digest):
        raise SupplyChainEvidenceError("resolved component-set digest must be lowercase SHA-256")

    components = _list(payload.get("components"), "resolved.components")
    if not components:
        raise SupplyChainEvidenceError("resolved.components must not be empty")
    seen_purls: set[str] = set()
    resolved_modules: set[tuple[str, str]] = set()
    for index, item in enumerate(components):
        if not isinstance(item, dict):
            raise SupplyChainEvidenceError(f"resolved.components[{index}] must be an object")
        if item.get("type") != "library":
            raise SupplyChainEvidenceError("every resolved component must have type 'library'")
        group = _string(item.get("group"), f"resolved.components[{index}].group")
        name = _string(item.get("name"), f"resolved.components[{index}].name")
        version = _string(item.get("version"), f"resolved.components[{index}].version")
        purl = _string(item.get("purl"), f"resolved.components[{index}].purl")
        if item.get("bom-ref") != purl:
            raise SupplyChainEvidenceError("resolved bom-ref must equal purl")
        expected_purl = _canonical_maven_purl(group, name, version)
        if purl != expected_purl:
            raise SupplyChainEvidenceError(
                "resolved Maven purl is not the canonical encoding of its "
                f"group/name/version fields: {purl} != {expected_purl}"
            )
        if purl in seen_purls:
            raise SupplyChainEvidenceError(f"duplicate resolved purl: {purl}")
        seen_purls.add(purl)
        resolved_modules.add((group, name))

    if hashlib.sha256(_canonical_bytes(sorted(components, key=lambda item: item["purl"]))).hexdigest() != component_set_digest:
        raise SupplyChainEvidenceError("resolved component-set digest does not match components")

    missing_direct = sorted(runtime_direct_modules - resolved_modules)
    if missing_direct:
        rendered = ", ".join(f"{group}:{name}" for group, name in missing_direct)
        raise SupplyChainEvidenceError(
            f"resolved evidence is missing runtime/test direct modules: {rendered}"
        )

    top_properties = _properties(payload.get("properties"), "resolved.properties")
    source_reports_raw = top_properties.get("forest-run:source-reports")
    if source_reports_raw is None:
        raise SupplyChainEvidenceError("resolved source-report provenance is missing")
    try:
        source_reports = strict_json.loads(
            source_reports_raw,
            label="resolved source-report provenance",
        )
    except strict_json.StrictJsonError as exc:
        raise SupplyChainEvidenceError(f"invalid resolved source-report provenance: {exc}") from exc
    reports = _list(source_reports, "resolved source-report provenance")
    report_configurations: set[str] = set()
    report_paths: set[str] = set()
    for index, item in enumerate(reports):
        if not isinstance(item, dict):
            raise SupplyChainEvidenceError(f"source report {index} must be an object")
        configuration = _string(item.get("configuration"), f"source report {index}.configuration")
        report_path = _repository_relative_path(
            item.get("path"), f"source report {index}.path"
        )
        report_digest = _string(item.get("sha256"), f"source report {index}.sha256")
        if configuration in report_configurations:
            raise SupplyChainEvidenceError(f"duplicate source report configuration: {configuration}")
        if report_path in report_paths:
            raise SupplyChainEvidenceError("resolved source-report paths must be unique and relative")
        if not SHA256.fullmatch(report_digest):
            raise SupplyChainEvidenceError(f"invalid source report SHA-256: {report_path}")
        if repository_root is not None:
            _verify_referenced_file(
                repository_root,
                report_path,
                report_digest,
                f"resolved source report {report_path}",
            )
        report_configurations.add(configuration)
        report_paths.add(report_path)
    if report_configurations != EXPECTED_CONFIGURATIONS:
        raise SupplyChainEvidenceError("source-report provenance does not cover required configurations")


def validate(
    declared_path: Path,
    resolved_path: Path,
    expected_sha: str,
    *,
    repository_root: Path | None = None,
) -> None:
    if not isinstance(expected_sha, str) or not SHA40.fullmatch(expected_sha):
        raise SupplyChainEvidenceError(
            "expected commit must be exactly 40 lowercase hexadecimal characters"
        )
    declared = _object(declared_path, "declared dependency inventory")
    resolved = _object(resolved_path, "resolved dependency SBOM")
    try:
        root = checked_directory_path(
            repository_root or Path.cwd(), "supply-chain repository root"
        )
    except StableFileError as exc:
        raise SupplyChainEvidenceError(str(exc)) from exc
    runtime_direct_modules = validate_declared(
        declared,
        expected_sha,
        repository_root=root,
    )
    validate_resolved(
        resolved,
        expected_sha,
        runtime_direct_modules,
        repository_root=root,
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--declared", type=Path, required=True)
    parser.add_argument("--resolved", type=Path, required=True)
    parser.add_argument("--expected-commit", required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        validate(args.declared, args.resolved, args.expected_commit)
    except (SupplyChainEvidenceError, OSError) as exc:
        print(f"supply-chain evidence error: {exc}", file=os.sys.stderr)
        return 1
    print(
        json.dumps(
            {"candidateSha": args.expected_commit, "status": "valid"},
            sort_keys=True,
            separators=(",", ":"),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
