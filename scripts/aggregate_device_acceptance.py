#!/usr/bin/env python3
"""Aggregate validated Forest Run physical acceptance evidence.

This tool never relaxes or replaces the fail-closed acceptance validator. It
first validates every supplied manifest, referenced file, and deterministic
trace contract, then produces per-device-class distributions, worst-case
threshold headroom, and optional baseline deltas. It deliberately does not
invent regression tolerances.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import tempfile
from pathlib import Path
from statistics import fmean
from typing import Any, Mapping, Sequence

import strict_json
import validate_device_acceptance as acceptance
import validate_manifest_scenario_traces as manifest_traces

SCHEMA_VERSION = 1
REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
METRICS = (
    "p95_frame_ms",
    "p99_frame_ms",
    "slow_frame_ratio",
    "peak_pss_mb",
    "crashes",
    "anrs",
)
THRESHOLD_FOR_METRIC = {
    "p95_frame_ms": "max_p95_frame_ms",
    "p99_frame_ms": "max_p99_frame_ms",
    "slow_frame_ratio": "max_slow_frame_ratio",
    "peak_pss_mb": "max_peak_pss_mb",
    "crashes": "max_crashes",
    "anrs": "max_anrs",
}


class AggregationError(ValueError):
    """Raised when validated evidence cannot be aggregated unambiguously."""


def _stable_manifest_read(path: Path) -> tuple[bytes, tuple[int, int, int]]:
    resolved = path.expanduser().resolve()
    try:
        before = resolved.stat()
    except FileNotFoundError as exc:
        raise AggregationError(f"acceptance manifest is missing: {resolved}") from exc
    except OSError as exc:
        raise AggregationError(f"could not inspect acceptance manifest {resolved}: {exc}") from exc
    if not resolved.is_file():
        raise AggregationError(f"acceptance manifest is not a regular file: {resolved}")
    if before.st_size <= 0 or before.st_size > acceptance.MAX_MANIFEST_BYTES:
        raise AggregationError(
            f"acceptance manifest must be between 1 and {acceptance.MAX_MANIFEST_BYTES} bytes"
        )
    try:
        raw = resolved.read_bytes()
        after = resolved.stat()
    except OSError as exc:
        raise AggregationError(f"could not read acceptance manifest {resolved}: {exc}") from exc
    before_identity = (before.st_size, before.st_mtime_ns, before.st_ino)
    after_identity = (after.st_size, after.st_mtime_ns, after.st_ino)
    if len(raw) != before.st_size or after_identity != before_identity:
        raise AggregationError(f"acceptance manifest changed while being read: {resolved}")
    return raw, before_identity


def _load_validated_manifest(
    path: Path,
) -> tuple[dict[str, Any], acceptance.ValidationSummary, dict[str, Any]]:
    resolved = path.expanduser().resolve()
    raw, identity = _stable_manifest_read(resolved)
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
        trace_summary = manifest_traces.validate_manifest_traces(
            resolved,
            repository_root=REPOSITORY_ROOT,
            require_at_least_one=True,
        )
    except (
        strict_json.StrictJsonError,
        acceptance.EvidenceError,
        manifest_traces.ManifestTraceError,
    ) as exc:
        raise AggregationError(f"invalid acceptance manifest {resolved}: {exc}") from exc

    confirmed_raw, confirmed_identity = _stable_manifest_read(resolved)
    if confirmed_identity != identity or confirmed_raw != raw:
        raise AggregationError(
            f"acceptance manifest changed while trace contracts were validated: {resolved}"
        )
    if (
        trace_summary["candidate_commit_sha"] != summary.candidate_sha
        or trace_summary["artifact_sha256"] != summary.artifact_sha256
    ):
        raise AggregationError(
            "acceptance and trace validation resolved different candidate identities"
        )
    return data, summary, trace_summary


def _finite_metric(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise AggregationError(f"{label} must be numeric")
    result = float(value)
    if not math.isfinite(result) or result < 0:
        raise AggregationError(f"{label} must be finite and non-negative")
    return result


def _metric_distribution(values: Sequence[float]) -> dict[str, float | int]:
    if not values:
        raise AggregationError("cannot summarize an empty metric distribution")
    return {
        "count": len(values),
        "minimum": min(values),
        "mean": fmean(values),
        "maximum": max(values),
    }


def _device_identity(session: Mapping[str, Any]) -> str:
    device = session["device"]
    return "|".join(
        str(device[key]).strip().casefold()
        for key in ("manufacturer", "model", "build_fingerprint")
    )


def _trace_contracts(trace_validation: Mapping[str, Any]) -> list[dict[str, str]]:
    unique = {
        (
            trace["trace_scenario"],
            trace["scenario_definition_sha256"],
            trace["trace_contract_sha256"],
        )
        for trace in trace_validation["traces"]
    }
    return [
        {
            "scenario": scenario,
            "scenario_definition_sha256": scenario_sha,
            "trace_contract_sha256": trace_sha,
        }
        for scenario, scenario_sha, trace_sha in sorted(unique)
    ]


def _summarize_manifest(
    data: Mapping[str, Any],
    validation: acceptance.ValidationSummary,
    trace_validation: Mapping[str, Any],
) -> dict[str, Any]:
    sessions = data["sessions"]
    thresholds = data["policy"]["thresholds"]
    required_classes = tuple(data["policy"]["required_device_classes"])
    by_class: dict[str, Any] = {}
    global_values: dict[str, list[float]] = {metric: [] for metric in METRICS}

    for device_class in required_classes:
        class_sessions = [
            session for session in sessions if session["device"]["class"] == device_class
        ]
        if not class_sessions:
            raise AggregationError(f"validated manifest has no session for {device_class}")
        metric_values: dict[str, list[float]] = {metric: [] for metric in METRICS}
        for index, session in enumerate(class_sessions):
            performance = session["performance"]
            for metric in METRICS:
                value = _finite_metric(
                    performance[metric],
                    f"{device_class}.sessions[{index}].performance.{metric}",
                )
                metric_values[metric].append(value)
                global_values[metric].append(value)

        by_class[device_class] = {
            "session_count": len(class_sessions),
            "physical_device_count": len({_device_identity(session) for session in class_sessions}),
            "session_ids": sorted(session["session_id"] for session in class_sessions),
            "metrics": {
                metric: _metric_distribution(metric_values[metric])
                for metric in METRICS
            },
        }

    global_metrics = {
        metric: _metric_distribution(global_values[metric])
        for metric in METRICS
    }
    threshold_headroom = {
        metric: float(thresholds[THRESHOLD_FOR_METRIC[metric]])
        - float(global_metrics[metric]["maximum"])
        for metric in METRICS
    }
    durations = [
        _finite_metric(session["duration_seconds"], "sessions[].duration_seconds")
        for session in sessions
    ]

    candidate = data["candidate"]
    return {
        "candidate": {
            "commit_sha": validation.candidate_sha,
            "artifact_sha256": validation.artifact_sha256,
            "application_id": candidate["application_id"],
            "version_code": candidate["version_code"],
            "certificate_sha256": candidate["certificate_sha256"],
        },
        "session_count": validation.session_count,
        "evidence_file_count": validation.evidence_file_count,
        "device_class_count": len(required_classes),
        "trace_count": trace_validation["trace_count"],
        "trace_contracts": _trace_contracts(trace_validation),
        "duration_seconds": _metric_distribution(durations),
        "global_metrics": global_metrics,
        "threshold_headroom": threshold_headroom,
        "by_device_class": by_class,
    }


def _compare(
    candidate: Mapping[str, Any],
    baseline: Mapping[str, Any],
) -> dict[str, Any]:
    candidate_classes = set(candidate["by_device_class"])
    baseline_classes = set(baseline["by_device_class"])
    if candidate_classes != baseline_classes:
        missing = sorted(baseline_classes - candidate_classes)
        added = sorted(candidate_classes - baseline_classes)
        raise AggregationError(
            "candidate and baseline device-class matrices differ; "
            f"missing={missing}, added={added}"
        )

    candidate_contracts = {
        (
            item["scenario"],
            item["scenario_definition_sha256"],
            item["trace_contract_sha256"],
        )
        for item in candidate["trace_contracts"]
    }
    baseline_contracts = {
        (
            item["scenario"],
            item["scenario_definition_sha256"],
            item["trace_contract_sha256"],
        )
        for item in baseline["trace_contracts"]
    }
    if candidate_contracts != baseline_contracts:
        raise AggregationError(
            "candidate and baseline deterministic trace-contract sets differ; "
            f"candidate={sorted(candidate_contracts)}, baseline={sorted(baseline_contracts)}"
        )

    class_deltas: dict[str, Any] = {}
    for device_class in sorted(candidate_classes):
        candidate_metrics = candidate["by_device_class"][device_class]["metrics"]
        baseline_metrics = baseline["by_device_class"][device_class]["metrics"]
        class_deltas[device_class] = {
            metric: {
                "mean_delta": candidate_metrics[metric]["mean"]
                - baseline_metrics[metric]["mean"],
                "maximum_delta": candidate_metrics[metric]["maximum"]
                - baseline_metrics[metric]["maximum"],
            }
            for metric in METRICS
        }

    return {
        "baseline_commit_sha": baseline["candidate"]["commit_sha"],
        "baseline_artifact_sha256": baseline["candidate"]["artifact_sha256"],
        "trace_contracts": candidate["trace_contracts"],
        "global_metric_deltas": {
            metric: {
                "mean_delta": candidate["global_metrics"][metric]["mean"]
                - baseline["global_metrics"][metric]["mean"],
                "maximum_delta": candidate["global_metrics"][metric]["maximum"]
                - baseline["global_metrics"][metric]["maximum"],
            }
            for metric in METRICS
        },
        "by_device_class": class_deltas,
        "interpretation": (
            "Positive frame-time, slow-frame, memory, crash, or ANR deltas are regressions; "
            "this report does not invent an allowed tolerance."
        ),
    }


def _same_file_or_resolved_path(first: Path, second: Path) -> bool:
    first_resolved = first.expanduser().resolve()
    second_resolved = second.expanduser().resolve()
    if first_resolved == second_resolved:
        return True
    try:
        return os.path.samefile(first_resolved, second_resolved)
    except (FileNotFoundError, OSError):
        return False


def _manifest_protected_paths(
    path: Path,
    data: Mapping[str, Any] | None = None,
) -> tuple[Path, ...]:
    resolved = path.expanduser().resolve()
    if data is None:
        data, _, _ = _load_validated_manifest(resolved)
    protected: list[Path] = [resolved]
    protected.append((resolved.parent / data["candidate"]["artifact_path"]).resolve())
    for session in data["sessions"]:
        for scenario in session["scenarios"].values():
            for evidence in scenario["evidence_files"]:
                protected.append((resolved.parent / evidence["path"]).resolve())
    return tuple(protected)


def _protected_source_paths(
    candidate_path: Path,
    baseline_path: Path | None,
) -> tuple[Path, ...]:
    protected = list(_manifest_protected_paths(candidate_path))
    if baseline_path is not None:
        protected.extend(_manifest_protected_paths(baseline_path))
    return tuple(protected)


def _assert_output_is_separate(output_path: Path, protected_paths: Sequence[Path]) -> None:
    output = output_path.expanduser().resolve()
    for protected in protected_paths:
        if _same_file_or_resolved_path(output, protected):
            raise AggregationError(
                f"aggregate output must not overwrite protected source: {protected}"
            )


def _aggregate_with_sources(
    candidate_path: Path,
    *,
    baseline_path: Path | None = None,
) -> tuple[dict[str, Any], tuple[Path, ...]]:
    if baseline_path is not None and _same_file_or_resolved_path(
        candidate_path,
        baseline_path,
    ):
        raise AggregationError("candidate and baseline manifests must be distinct files")

    candidate_data, candidate_validation, candidate_traces = _load_validated_manifest(
        candidate_path
    )
    candidate_summary = _summarize_manifest(
        candidate_data,
        candidate_validation,
        candidate_traces,
    )
    protected = list(_manifest_protected_paths(candidate_path, candidate_data))
    result: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "status": "valid",
        "candidate_summary": candidate_summary,
    }
    if baseline_path is not None:
        baseline_data, baseline_validation, baseline_traces = _load_validated_manifest(
            baseline_path
        )
        baseline_summary = _summarize_manifest(
            baseline_data,
            baseline_validation,
            baseline_traces,
        )
        result["baseline_comparison"] = _compare(candidate_summary, baseline_summary)
        protected.extend(_manifest_protected_paths(baseline_path, baseline_data))
    return result, tuple(protected)


def aggregate(
    candidate_path: Path,
    *,
    baseline_path: Path | None = None,
) -> dict[str, Any]:
    payload, _ = _aggregate_with_sources(
        candidate_path,
        baseline_path=baseline_path,
    )
    return payload


def _write_json_atomic(
    path: Path,
    payload: Mapping[str, Any],
    *,
    protected_paths: Sequence[Path] = (),
) -> None:
    destination = path.expanduser().resolve()
    _assert_output_is_separate(destination, protected_paths)
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{destination.name}.",
        suffix=".tmp",
        dir=destination.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(payload, handle, indent=2, sort_keys=True, allow_nan=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        _assert_output_is_separate(destination, protected_paths)
        os.replace(temporary, destination)
    except (OSError, AggregationError) as exc:
        temporary.unlink(missing_ok=True)
        if isinstance(exc, AggregationError):
            raise
        raise AggregationError(f"could not publish aggregate {destination}: {exc}") from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    try:
        payload, protected_paths = _aggregate_with_sources(
            args.candidate,
            baseline_path=args.baseline,
        )
        if args.output is not None:
            _write_json_atomic(
                args.output,
                payload,
                protected_paths=protected_paths,
            )
    except (OSError, AggregationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(payload, sort_keys=True, allow_nan=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
