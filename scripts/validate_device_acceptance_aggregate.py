#!/usr/bin/env python3
"""Strict independent validator for Forest Run device-acceptance aggregates."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json

SCHEMA_VERSION = 2
MAX_AGGREGATE_BYTES = 16 * 1024 * 1024
CANONICAL_APPLICATION_ID = "com.anurag9000.forestrun"
MANDATORY_DEVICE_CLASSES = {
    "older_phone",
    "midrange_phone",
    "high_refresh_phone",
    "cutout_phone",
    "tablet",
}
METRICS = (
    "p95_frame_ms",
    "p99_frame_ms",
    "slow_frame_ratio",
    "peak_pss_mb",
    "crashes",
    "anrs",
)
SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SESSION_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,79}$")
SCENARIO_RE = re.compile(r"^[A-Z][A-Z0-9_]{0,79}$")
BASELINE_INTERPRETATION = (
    "Positive frame-time, slow-frame, memory, crash, or ANR deltas are regressions; "
    "this report does not invent an allowed tolerance."
)


class AggregateValidationError(ValueError):
    """Raised when an aggregate violates the independent output contract."""


def _object(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise AggregateValidationError(f"{label} must be an object")
    return value


def _array(value: Any, label: str) -> Sequence[Any]:
    if not isinstance(value, list):
        raise AggregateValidationError(f"{label} must be an array")
    return value


def _exact_keys(value: Mapping[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        raise AggregateValidationError(
            f"{label} keys differ; missing={missing}, extra={extra}"
        )


def _string(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise AggregateValidationError(f"{label} must be a string")
    if value != value.strip() or not value:
        raise AggregateValidationError(f"{label} must be nonblank and trimmed")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise AggregateValidationError(f"{label} must not contain control characters")
    return value


def _integer(
    value: Any,
    label: str,
    *,
    minimum: int | None = None,
    maximum: int | None = None,
) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise AggregateValidationError(f"{label} must be an integer")
    if minimum is not None and value < minimum:
        raise AggregateValidationError(f"{label} must be >= {minimum}")
    if maximum is not None and value > maximum:
        raise AggregateValidationError(f"{label} must be <= {maximum}")
    return value


def _finite_number(
    value: Any,
    label: str,
    *,
    minimum: float | None = None,
) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise AggregateValidationError(f"{label} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise AggregateValidationError(f"{label} must be finite")
    if minimum is not None and result < minimum:
        raise AggregateValidationError(f"{label} must be >= {minimum}")
    return result


def _sha(value: Any, label: str, pattern: re.Pattern[str]) -> str:
    text = _string(value, label)
    if pattern.fullmatch(text) is None:
        bits = 160 if pattern is SHA1_RE else 256
        raise AggregateValidationError(
            f"{label} must be a lowercase SHA-{bits} hexadecimal digest"
        )
    return text


def _ordered_unique_strings(
    value: Any,
    label: str,
    *,
    pattern: re.Pattern[str],
    minimum_items: int = 1,
) -> tuple[str, ...]:
    items = tuple(_string(item, f"{label}[]") for item in _array(value, label))
    if len(items) < minimum_items:
        raise AggregateValidationError(
            f"{label} must contain at least {minimum_items} item(s)"
        )
    if any(pattern.fullmatch(item) is None for item in items):
        raise AggregateValidationError(f"{label} contains an invalid value")
    if tuple(sorted(items)) != items:
        raise AggregateValidationError(f"{label} must be sorted")
    if len(set(items)) != len(items):
        raise AggregateValidationError(f"{label} must not contain duplicates")
    return items


def _distribution(
    value: Any,
    label: str,
    *,
    expected_count: int,
) -> dict[str, float | int]:
    distribution = _object(value, label)
    _exact_keys(distribution, {"count", "minimum", "mean", "maximum"}, label)
    count = _integer(distribution["count"], f"{label}.count", minimum=1)
    if count != expected_count:
        raise AggregateValidationError(
            f"{label}.count must equal {expected_count}, found {count}"
        )
    minimum = _finite_number(distribution["minimum"], f"{label}.minimum", minimum=0.0)
    mean = _finite_number(distribution["mean"], f"{label}.mean", minimum=0.0)
    maximum = _finite_number(distribution["maximum"], f"{label}.maximum", minimum=0.0)
    if minimum > mean or mean > maximum:
        raise AggregateValidationError(
            f"{label} must satisfy minimum <= mean <= maximum"
        )
    return {
        "count": count,
        "minimum": minimum,
        "mean": mean,
        "maximum": maximum,
    }


def _trace_contracts(value: Any, label: str) -> tuple[tuple[str, str, str], ...]:
    contracts: list[tuple[str, str, str]] = []
    for index, item in enumerate(_array(value, label)):
        contract_label = f"{label}[{index}]"
        contract = _object(item, contract_label)
        _exact_keys(
            contract,
            {
                "scenario",
                "scenario_definition_sha256",
                "trace_contract_sha256",
            },
            contract_label,
        )
        scenario = _string(contract["scenario"], f"{contract_label}.scenario")
        if SCENARIO_RE.fullmatch(scenario) is None:
            raise AggregateValidationError(
                f"{contract_label}.scenario must be an uppercase scenario identifier"
            )
        contracts.append(
            (
                scenario,
                _sha(
                    contract["scenario_definition_sha256"],
                    f"{contract_label}.scenario_definition_sha256",
                    SHA256_RE,
                ),
                _sha(
                    contract["trace_contract_sha256"],
                    f"{contract_label}.trace_contract_sha256",
                    SHA256_RE,
                ),
            )
        )
    if not contracts:
        raise AggregateValidationError(f"{label} must contain at least one contract")
    result = tuple(contracts)
    if tuple(sorted(result)) != result:
        raise AggregateValidationError(f"{label} must be sorted")
    if len(set(result)) != len(result):
        raise AggregateValidationError(f"{label} must not contain duplicates")
    return result


def _matrix_sha256(by_device_class: Mapping[str, Any]) -> str:
    canonical = {
        device_class: {
            "session_count": summary["session_count"],
            "device_profile_ids": summary["device_profile_ids"],
        }
        for device_class, summary in sorted(by_device_class.items())
    }
    encoded = json.dumps(
        canonical,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
        allow_nan=False,
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _metric_map(
    value: Any,
    label: str,
    *,
    expected_count: int,
) -> dict[str, dict[str, float | int]]:
    metrics = _object(value, label)
    _exact_keys(metrics, set(METRICS), label)
    return {
        metric: _distribution(
            metrics[metric],
            f"{label}.{metric}",
            expected_count=expected_count,
        )
        for metric in METRICS
    }


def _validate_class_summary(value: Any, label: str) -> dict[str, Any]:
    summary = _object(value, label)
    _exact_keys(
        summary,
        {
            "session_count",
            "physical_device_count",
            "physical_device_ids",
            "device_profile_ids",
            "session_ids",
            "metrics",
        },
        label,
    )
    session_count = _integer(
        summary["session_count"],
        f"{label}.session_count",
        minimum=1,
    )
    physical_device_count = _integer(
        summary["physical_device_count"],
        f"{label}.physical_device_count",
        minimum=1,
        maximum=session_count,
    )
    physical_ids = _ordered_unique_strings(
        summary["physical_device_ids"],
        f"{label}.physical_device_ids",
        pattern=SHA256_RE,
    )
    if len(physical_ids) != physical_device_count:
        raise AggregateValidationError(
            f"{label}.physical_device_count does not match physical_device_ids"
        )
    profile_ids = _ordered_unique_strings(
        summary["device_profile_ids"],
        f"{label}.device_profile_ids",
        pattern=SHA256_RE,
    )
    if len(profile_ids) < physical_device_count or len(profile_ids) > session_count:
        raise AggregateValidationError(
            f"{label}.device_profile_ids count must be between physical-device "
            "count and session count"
        )
    session_ids = _ordered_unique_strings(
        summary["session_ids"],
        f"{label}.session_ids",
        pattern=SESSION_ID_RE,
    )
    if len(session_ids) != session_count:
        raise AggregateValidationError(
            f"{label}.session_ids count must equal session_count"
        )
    metrics = _metric_map(
        summary["metrics"],
        f"{label}.metrics",
        expected_count=session_count,
    )
    return {
        "session_count": session_count,
        "physical_device_count": physical_device_count,
        "physical_device_ids": list(physical_ids),
        "device_profile_ids": list(profile_ids),
        "session_ids": list(session_ids),
        "metrics": metrics,
    }


def _close(first: float, second: float) -> bool:
    return math.isclose(first, second, rel_tol=1e-12, abs_tol=1e-12)


def _validate_global_consistency(
    global_metrics: Mapping[str, Mapping[str, float | int]],
    by_device_class: Mapping[str, Mapping[str, Any]],
    session_count: int,
) -> None:
    for metric in METRICS:
        class_distributions = [
            summary["metrics"][metric] for summary in by_device_class.values()
        ]
        expected_minimum = min(float(item["minimum"]) for item in class_distributions)
        expected_maximum = max(float(item["maximum"]) for item in class_distributions)
        expected_mean = math.fsum(
            float(item["mean"]) * int(item["count"])
            for item in class_distributions
        ) / session_count
        actual = global_metrics[metric]
        if not _close(float(actual["minimum"]), expected_minimum):
            raise AggregateValidationError(
                f"candidate_summary.global_metrics.{metric}.minimum "
                "does not match class summaries"
            )
        if not _close(float(actual["maximum"]), expected_maximum):
            raise AggregateValidationError(
                f"candidate_summary.global_metrics.{metric}.maximum "
                "does not match class summaries"
            )
        if not _close(float(actual["mean"]), expected_mean):
            raise AggregateValidationError(
                f"candidate_summary.global_metrics.{metric}.mean "
                "does not match weighted class summaries"
            )


def _validate_candidate_summary(value: Any) -> dict[str, Any]:
    label = "candidate_summary"
    summary = _object(value, label)
    _exact_keys(
        summary,
        {
            "candidate",
            "session_count",
            "evidence_file_count",
            "device_class_count",
            "comparison_matrix_sha256",
            "trace_count",
            "trace_contracts",
            "duration_seconds",
            "global_metrics",
            "threshold_headroom",
            "by_device_class",
        },
        label,
    )
    candidate = _object(summary["candidate"], f"{label}.candidate")
    _exact_keys(
        candidate,
        {
            "commit_sha",
            "artifact_sha256",
            "application_id",
            "version_code",
            "upload_certificate_sha256",
            "app_signing_certificate_sha256",
        },
        f"{label}.candidate",
    )
    commit_sha = _sha(candidate["commit_sha"], f"{label}.candidate.commit_sha", SHA1_RE)
    artifact_sha256 = _sha(
        candidate["artifact_sha256"],
        f"{label}.candidate.artifact_sha256",
        SHA256_RE,
    )
    application_id = _string(
        candidate["application_id"],
        f"{label}.candidate.application_id",
    )
    if application_id != CANONICAL_APPLICATION_ID:
        raise AggregateValidationError(
            f"{label}.candidate.application_id must equal {CANONICAL_APPLICATION_ID}"
        )
    version_code = _integer(
        candidate["version_code"],
        f"{label}.candidate.version_code",
        minimum=1,
    )
    upload_certificate_sha256 = _sha(
        candidate["upload_certificate_sha256"],
        f"{label}.candidate.upload_certificate_sha256",
        SHA256_RE,
    )
    app_signing_certificate_sha256 = _sha(
        candidate["app_signing_certificate_sha256"],
        f"{label}.candidate.app_signing_certificate_sha256",
        SHA256_RE,
    )

    session_count = _integer(summary["session_count"], f"{label}.session_count", minimum=1)
    evidence_file_count = _integer(
        summary["evidence_file_count"],
        f"{label}.evidence_file_count",
        minimum=1,
    )
    device_class_count = _integer(
        summary["device_class_count"],
        f"{label}.device_class_count",
        minimum=1,
    )
    trace_count = _integer(summary["trace_count"], f"{label}.trace_count", minimum=1)
    contracts = _trace_contracts(summary["trace_contracts"], f"{label}.trace_contracts")
    if len(contracts) > trace_count:
        raise AggregateValidationError(f"{label}.trace_contracts cannot exceed trace_count")
    if trace_count > evidence_file_count:
        raise AggregateValidationError(f"{label}.trace_count cannot exceed evidence_file_count")

    by_class_raw = _object(summary["by_device_class"], f"{label}.by_device_class")
    if set(by_class_raw) != MANDATORY_DEVICE_CLASSES:
        missing = sorted(MANDATORY_DEVICE_CLASSES - set(by_class_raw))
        extra = sorted(set(by_class_raw) - MANDATORY_DEVICE_CLASSES)
        raise AggregateValidationError(
            f"{label}.by_device_class must contain the mandatory matrix; "
            f"missing={missing}, extra={extra}"
        )
    if device_class_count != len(MANDATORY_DEVICE_CLASSES):
        raise AggregateValidationError(
            f"{label}.device_class_count must equal {len(MANDATORY_DEVICE_CLASSES)}"
        )
    by_device_class = {
        device_class: _validate_class_summary(
            by_class_raw[device_class],
            f"{label}.by_device_class.{device_class}",
        )
        for device_class in sorted(MANDATORY_DEVICE_CLASSES)
    }
    if sum(item["session_count"] for item in by_device_class.values()) != session_count:
        raise AggregateValidationError(
            f"{label}.session_count does not equal the per-class total"
        )

    duration = _distribution(
        summary["duration_seconds"],
        f"{label}.duration_seconds",
        expected_count=session_count,
    )
    global_metrics = _metric_map(
        summary["global_metrics"],
        f"{label}.global_metrics",
        expected_count=session_count,
    )
    _validate_global_consistency(global_metrics, by_device_class, session_count)

    headroom = _object(summary["threshold_headroom"], f"{label}.threshold_headroom")
    _exact_keys(headroom, set(METRICS), f"{label}.threshold_headroom")
    normalized_headroom = {
        metric: _finite_number(
            headroom[metric],
            f"{label}.threshold_headroom.{metric}",
            minimum=0.0,
        )
        for metric in METRICS
    }

    comparison_matrix_sha256 = _sha(
        summary["comparison_matrix_sha256"],
        f"{label}.comparison_matrix_sha256",
        SHA256_RE,
    )
    if comparison_matrix_sha256 != _matrix_sha256(by_device_class):
        raise AggregateValidationError(
            f"{label}.comparison_matrix_sha256 does not match the class matrix"
        )

    return {
        "candidate": {
            "commit_sha": commit_sha,
            "artifact_sha256": artifact_sha256,
            "application_id": application_id,
            "version_code": version_code,
            "upload_certificate_sha256": upload_certificate_sha256,
            "app_signing_certificate_sha256": app_signing_certificate_sha256,
        },
        "session_count": session_count,
        "evidence_file_count": evidence_file_count,
        "device_class_count": device_class_count,
        "comparison_matrix_sha256": comparison_matrix_sha256,
        "trace_count": trace_count,
        "trace_contracts": contracts,
        "duration_seconds": duration,
        "global_metrics": global_metrics,
        "threshold_headroom": normalized_headroom,
        "by_device_class": by_device_class,
    }


def _delta_map(value: Any, label: str) -> dict[str, dict[str, float]]:
    mapping = _object(value, label)
    _exact_keys(mapping, set(METRICS), label)
    result: dict[str, dict[str, float]] = {}
    for metric in METRICS:
        delta_label = f"{label}.{metric}"
        delta = _object(mapping[metric], delta_label)
        _exact_keys(delta, {"mean_delta", "maximum_delta"}, delta_label)
        result[metric] = {
            "mean_delta": _finite_number(delta["mean_delta"], f"{delta_label}.mean_delta"),
            "maximum_delta": _finite_number(
                delta["maximum_delta"],
                f"{delta_label}.maximum_delta",
            ),
        }
    return result


def _validate_baseline_comparison(value: Any, candidate: Mapping[str, Any]) -> dict[str, Any]:
    label = "baseline_comparison"
    comparison = _object(value, label)
    _exact_keys(
        comparison,
        {
            "baseline_commit_sha",
            "baseline_artifact_sha256",
            "comparison_matrix_sha256",
            "trace_contracts",
            "global_metric_deltas",
            "by_device_class",
            "interpretation",
        },
        label,
    )
    baseline_commit_sha = _sha(
        comparison["baseline_commit_sha"],
        f"{label}.baseline_commit_sha",
        SHA1_RE,
    )
    baseline_artifact_sha256 = _sha(
        comparison["baseline_artifact_sha256"],
        f"{label}.baseline_artifact_sha256",
        SHA256_RE,
    )
    matrix_sha = _sha(
        comparison["comparison_matrix_sha256"],
        f"{label}.comparison_matrix_sha256",
        SHA256_RE,
    )
    if matrix_sha != candidate["comparison_matrix_sha256"]:
        raise AggregateValidationError(
            f"{label}.comparison_matrix_sha256 must match candidate_summary"
        )
    contracts = _trace_contracts(comparison["trace_contracts"], f"{label}.trace_contracts")
    if contracts != candidate["trace_contracts"]:
        raise AggregateValidationError(
            f"{label}.trace_contracts must exactly match candidate_summary"
        )
    global_deltas = _delta_map(
        comparison["global_metric_deltas"],
        f"{label}.global_metric_deltas",
    )
    by_class_raw = _object(comparison["by_device_class"], f"{label}.by_device_class")
    if set(by_class_raw) != MANDATORY_DEVICE_CLASSES:
        raise AggregateValidationError(
            f"{label}.by_device_class must match the mandatory device matrix"
        )
    by_device_class = {
        device_class: _delta_map(
            by_class_raw[device_class],
            f"{label}.by_device_class.{device_class}",
        )
        for device_class in sorted(MANDATORY_DEVICE_CLASSES)
    }
    interpretation = _string(comparison["interpretation"], f"{label}.interpretation")
    if interpretation != BASELINE_INTERPRETATION:
        raise AggregateValidationError(
            f"{label}.interpretation does not match the frozen semantics"
        )
    return {
        "baseline_commit_sha": baseline_commit_sha,
        "baseline_artifact_sha256": baseline_artifact_sha256,
        "comparison_matrix_sha256": matrix_sha,
        "trace_contracts": contracts,
        "global_metric_deltas": global_deltas,
        "by_device_class": by_device_class,
        "interpretation": interpretation,
    }


def validate_report(value: Any) -> dict[str, Any]:
    root = _object(value, "aggregate")
    allowed = {"schema_version", "status", "candidate_summary", "baseline_comparison"}
    required = {"schema_version", "status", "candidate_summary"}
    actual = set(root)
    missing = sorted(required - actual)
    extra = sorted(actual - allowed)
    if missing or extra:
        raise AggregateValidationError(
            f"aggregate keys differ; missing={missing}, extra={extra}"
        )
    if _integer(root["schema_version"], "aggregate.schema_version") != SCHEMA_VERSION:
        raise AggregateValidationError(
            f"aggregate.schema_version must equal {SCHEMA_VERSION}"
        )
    if _string(root["status"], "aggregate.status") != "valid":
        raise AggregateValidationError("aggregate.status must equal 'valid'")
    candidate = _validate_candidate_summary(root["candidate_summary"])
    baseline = None
    if "baseline_comparison" in root:
        baseline = _validate_baseline_comparison(root["baseline_comparison"], candidate)
    return {
        "candidate_commit_sha": candidate["candidate"]["commit_sha"],
        "candidate_artifact_sha256": candidate["candidate"]["artifact_sha256"],
        "session_count": candidate["session_count"],
        "comparison_matrix_sha256": candidate["comparison_matrix_sha256"],
        "trace_count": candidate["trace_count"],
        "baseline_comparison": baseline is not None,
    }


def validate_file(path: Path) -> dict[str, Any]:
    try:
        payload = strict_json.load_file(
            path,
            maximum_bytes=MAX_AGGREGATE_BYTES,
            maximum_depth=64,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise AggregateValidationError(str(exc)) from exc
    return validate_report(payload)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aggregate", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = validate_file(args.aggregate)
    except (OSError, AggregateValidationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps({"status": "valid", **summary}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
