#!/usr/bin/env python3
"""Final, fail-closed publisher for a staged device-acceptance aggregate."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any, Mapping, Sequence

import aggregate_device_acceptance as aggregate_producer
import strict_json
import validate_device_acceptance as acceptance
import validate_device_acceptance_aggregate as aggregate_validator
import validate_manifest_scenario_traces as manifest_traces
from stable_file_reader import StableFileError, atomic_replace_file, consume_regular_file

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


class PublicationError(ValueError):
    """Raised when a staged aggregate cannot be published safely."""


def _stable_manifest_bytes(path: Path) -> tuple[bytes, tuple[int, int, int, int, int]]:
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        raw, _, metadata = consume_regular_file(
            absolute, "acceptance manifest", acceptance.MAX_MANIFEST_BYTES,
            retain_bytes=True,
        )
    except StableFileError as exc:
        raise PublicationError(str(exc)) from exc
    assert raw is not None
    return raw, (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_size,
        metadata.st_mtime_ns,
        metadata.st_ctime_ns,
    )

def _validate_manifest(
    path: Path,
) -> tuple[
    dict[str, Any],
    acceptance.ValidationSummary,
    Mapping[str, Any],
    tuple[tuple[Path, tuple[int, int, int, int, int]], ...],
]:
    resolved = Path(os.path.abspath(os.fspath(path.expanduser())))
    raw, identity = _stable_manifest_bytes(resolved)
    try:
        data = strict_json.loads(
            raw,
            label=str(resolved),
            maximum_bytes=acceptance.MAX_MANIFEST_BYTES,
            maximum_depth=64,
            require_object=True,
        )
        summary = acceptance.validate_bundle(
            data,
            source_bytes=raw,
            evidence_base=resolved.parent,
        )
        traces = manifest_traces.validate_manifest_traces(
            resolved,
            repository_root=REPOSITORY_ROOT,
            require_at_least_one=True,
        )
        final_summary = acceptance.validate_bundle(
            data,
            source_bytes=raw,
            evidence_base=resolved.parent,
        )
    except (
        strict_json.StrictJsonError,
        acceptance.EvidenceError,
        manifest_traces.ManifestTraceError,
    ) as exc:
        raise PublicationError(f"invalid acceptance manifest {resolved}: {exc}") from exc

    confirmed_raw, confirmed_identity = _stable_manifest_bytes(resolved)
    if confirmed_identity != identity or confirmed_raw != raw:
        raise PublicationError(
            f"acceptance manifest changed during final publication validation: {resolved}"
        )
    if final_summary != summary:
        raise PublicationError(
            "acceptance validation changed across the final trace-validation pass"
        )
    if (
        traces["candidate_commit_sha"] != final_summary.candidate_sha
        or traces["artifact_sha256"] != final_summary.artifact_sha256
    ):
        raise PublicationError(
            "acceptance and trace validators resolved different candidate identities"
        )
    verified_snapshots = _verified_source_snapshots(
        resolved,
        confirmed_identity,
        data,
    )
    return data, final_summary, traces, verified_snapshots


def _same_file_or_path(first: Path, second: Path) -> bool:
    first_resolved = first.expanduser().resolve()
    second_resolved = second.expanduser().resolve()
    if first_resolved == second_resolved:
        return True
    try:
        return os.path.samefile(first_resolved, second_resolved)
    except (FileNotFoundError, OSError):
        return False


def _protected_paths(manifest: Path, data: Mapping[str, Any]) -> tuple[Path, ...]:
    resolved = Path(os.path.abspath(os.fspath(manifest.expanduser())))
    protected = [resolved]
    protected.append(resolved.parent / data["candidate"]["artifact_path"])
    for session in data["sessions"]:
        for scenario in session["scenarios"].values():
            for evidence in scenario["evidence_files"]:
                protected.append(resolved.parent / evidence["path"])
    return tuple(protected)


def _assert_separate(path: Path, protected_paths: Sequence[Path], label: str) -> None:
    for protected in protected_paths:
        if _same_file_or_path(path, protected):
            raise PublicationError(f"{label} must not alias protected source: {protected}")


def _load_aggregate(
    path: Path,
) -> tuple[dict[str, Any], dict[str, Any], bytes, tuple[int, int, int, int, int]]:
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        raw, _, metadata = consume_regular_file(
            absolute, "staged aggregate", aggregate_validator.MAX_AGGREGATE_BYTES,
            retain_bytes=True,
        )
    except StableFileError as exc:
        raise PublicationError(str(exc)) from exc
    assert raw is not None
    identity = (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_size,
        metadata.st_mtime_ns,
        metadata.st_ctime_ns,
    )
    try:
        payload = strict_json.loads(
            raw,
            label=str(absolute),
            maximum_bytes=aggregate_validator.MAX_AGGREGATE_BYTES,
            maximum_depth=64,
            require_object=True,
        )
        summary = aggregate_validator.validate_report(payload)
    except (
        strict_json.StrictJsonError,
        aggregate_validator.AggregateValidationError,
    ) as exc:
        raise PublicationError(f"invalid staged aggregate {path}: {exc}") from exc
    return payload, summary, raw, identity

def _hash_expected_source(
    path: Path,
    *,
    label: str,
    expected_sha256: str,
    maximum_bytes: int,
) -> tuple[Path, tuple[int, int, int, int, int]]:
    absolute = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        _, digest, metadata = consume_regular_file(
            absolute, label, maximum_bytes, retain_bytes=False
        )
    except StableFileError as exc:
        raise PublicationError(str(exc)) from exc
    identity = (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_size,
        metadata.st_mtime_ns,
        metadata.st_ctime_ns,
    )
    if digest != expected_sha256:
        raise PublicationError(f"{label} digest changed before publication: {absolute}")
    return absolute, identity

def _verified_source_snapshots(
    manifest_path: Path,
    manifest_identity: tuple[int, int, int, int, int],
    data: Mapping[str, Any],
) -> tuple[tuple[Path, tuple[int, int, int, int, int]], ...]:
    snapshots: list[tuple[Path, tuple[int, int, int, int, int]]] = [
        (Path(os.path.abspath(os.fspath(manifest_path.expanduser()))), manifest_identity)
    ]
    base = Path(os.path.abspath(os.fspath(manifest_path.expanduser()))).parent
    candidate = data["candidate"]
    snapshots.append(
        _hash_expected_source(
            base / candidate["artifact_path"],
            label="signed artifact",
            expected_sha256=candidate["artifact_sha256"],
            maximum_bytes=acceptance.MAX_ARTIFACT_BYTES,
        )
    )
    for session_index, session in enumerate(data["sessions"]):
        for scenario_name, scenario in session["scenarios"].items():
            for evidence_index, evidence in enumerate(scenario["evidence_files"]):
                snapshots.append(
                    _hash_expected_source(
                        base / evidence["path"],
                        label=(
                            f"sessions[{session_index}].scenarios.{scenario_name}."
                            f"evidence_files[{evidence_index}]"
                        ),
                        expected_sha256=evidence["sha256"],
                        maximum_bytes=acceptance.MAX_EVIDENCE_FILE_BYTES,
                    )
                )
    return tuple(snapshots)


def _assert_source_snapshot(
    snapshots: Sequence[tuple[Path, tuple[int, int, int, int, int]]],
) -> None:
    for path, expected in snapshots:
        try:
            _, _, current = consume_regular_file(
                path,
                "protected source",
                acceptance.MAX_ARTIFACT_BYTES,
                retain_bytes=False,
            )
        except StableFileError as exc:
            raise PublicationError(
                f"protected source changed before publication: {path}: {exc}"
            ) from exc
        identity = (
            current.st_dev,
            current.st_ino,
            current.st_size,
            current.st_mtime_ns,
            current.st_ctime_ns,
        )
        if identity != expected:
            raise PublicationError(
                f"protected source changed before publication: {path}"
            )


def _assert_aggregate_identity(
    payload: Mapping[str, Any],
    candidate: acceptance.ValidationSummary,
    baseline: acceptance.ValidationSummary | None,
) -> None:
    candidate_summary = payload["candidate_summary"]["candidate"]
    if candidate_summary["commit_sha"] != candidate.candidate_sha:
        raise PublicationError(
            "staged aggregate candidate commit does not match the final candidate manifest"
        )
    if candidate_summary["artifact_sha256"] != candidate.artifact_sha256:
        raise PublicationError(
            "staged aggregate candidate artifact does not match the final candidate manifest"
        )

    comparison = payload.get("baseline_comparison")
    if baseline is None:
        if comparison is not None:
            raise PublicationError(
                "staged aggregate contains a baseline comparison but no baseline was supplied"
            )
        return
    if comparison is None:
        raise PublicationError(
            "staged aggregate is missing the supplied baseline comparison"
        )
    if comparison["baseline_commit_sha"] != baseline.candidate_sha:
        raise PublicationError(
            "staged aggregate baseline commit does not match the final baseline manifest"
        )
    if comparison["baseline_artifact_sha256"] != baseline.artifact_sha256:
        raise PublicationError(
            "staged aggregate baseline artifact does not match the final baseline manifest"
        )


def _reconstruct_expected_payload(
    candidate_path: Path,
    baseline_path: Path | None,
) -> dict[str, Any]:
    try:
        return aggregate_producer.aggregate(
            candidate_path,
            baseline_path=baseline_path,
        )
    except (OSError, aggregate_producer.AggregationError) as exc:
        raise PublicationError(
            f"could not reconstruct final aggregate from validated manifests: {exc}"
        ) from exc


def publish(
    candidate_path: Path,
    staged_path: Path,
    output_path: Path,
    *,
    baseline_path: Path | None = None,
) -> dict[str, Any]:
    candidate_resolved = Path(os.path.abspath(os.fspath(candidate_path.expanduser())))
    staged_original = Path(os.path.abspath(os.fspath(staged_path.expanduser())))
    output_original = Path(os.path.abspath(os.fspath(output_path.expanduser())))

    if staged_original.is_symlink():
        raise PublicationError("staged aggregate must not be a symbolic link")
    if output_original.is_symlink():
        raise PublicationError("aggregate output must not be a symbolic link")

    staged = staged_original
    output = output_original
    if staged == output or _same_file_or_path(staged, output):
        raise PublicationError("staged aggregate and output must be distinct files")
    if staged.parent != output.parent:
        raise PublicationError(
            "staged aggregate must be in the output directory for atomic publication"
        )
    baseline_resolved = None
    manifest_paths = [candidate_resolved]
    if baseline_path is not None:
        baseline_resolved = Path(os.path.abspath(os.fspath(baseline_path.expanduser())))
        if _same_file_or_path(candidate_resolved, baseline_resolved):
            raise PublicationError("candidate and baseline manifests must be distinct files")
        manifest_paths.append(baseline_resolved)

    _assert_separate(staged, manifest_paths, "staged aggregate")
    _assert_separate(output, manifest_paths, "aggregate output")
    payload, summary, staged_raw, staged_identity = _load_aggregate(staged)

    # Final source validation deliberately happens after staged-report validation.
    # The second acceptance pass inside _validate_manifest rehashes every artifact
    # and evidence file after exact trace validation and immediately before binding.
    candidate_data, candidate_summary, _, candidate_snapshots = _validate_manifest(
        candidate_resolved
    )
    protected = list(_protected_paths(candidate_resolved, candidate_data))
    source_snapshot = list(candidate_snapshots)
    baseline_summary = None
    if baseline_resolved is not None:
        baseline_data, baseline_summary, _, baseline_snapshots = _validate_manifest(
            baseline_resolved
        )
        protected.extend(_protected_paths(baseline_resolved, baseline_data))
        source_snapshot.extend(baseline_snapshots)

    _assert_separate(staged, protected, "staged aggregate")
    _assert_separate(output, protected, "aggregate output")
    _assert_aggregate_identity(payload, candidate_summary, baseline_summary)

    # Reconstruct every serialized metric, matrix, trace contract, count, identity,
    # headroom value, and baseline delta from the final validated manifests. This
    # prevents a self-consistent but source-unbound staged report from publishing.
    expected_payload = _reconstruct_expected_payload(
        candidate_resolved,
        baseline_resolved,
    )

    confirmed_payload, confirmed_summary, confirmed_raw, confirmed_identity = (
        _load_aggregate(staged)
    )
    if (
        confirmed_identity != staged_identity
        or confirmed_raw != staged_raw
        or confirmed_payload != payload
        or confirmed_summary != summary
    ):
        raise PublicationError(
            "staged aggregate changed during final source validation"
        )
    if confirmed_payload != expected_payload:
        raise PublicationError(
            "staged aggregate does not exactly match the final validated manifest aggregation"
        )
    _assert_source_snapshot(source_snapshot)

    # Recheck aliases after all hashing and semantic validation, then replace the
    # destination immediately with the already validated staged inode.
    _assert_separate(staged, protected, "staged aggregate")
    _assert_separate(output, protected, "aggregate output")
    try:
        atomic_replace_file(
            staged,
            output,
            "validated acceptance aggregate",
            expected_source_identity=staged_identity,
        )
    except StableFileError as exc:
        raise PublicationError(f"could not publish aggregate {output}: {exc}") from exc
    return summary


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("staged", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = publish(
            args.candidate,
            args.staged,
            args.output,
            baseline_path=args.baseline,
        )
    except (OSError, PublicationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps({"status": "published", **summary}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
