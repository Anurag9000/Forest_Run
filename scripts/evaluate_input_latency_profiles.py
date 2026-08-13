#!/usr/bin/env python3
"""Evaluate Forest Run app/render input-latency reports against measured limits."""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json

SCHEMA_VERSION = 1
MEASUREMENT_KIND = "app_touch_to_posted_frame"
MAX_REPORT_BYTES = 2 * 1024 * 1024
MAX_THRESHOLDS_BYTES = 2 * 1024 * 1024


class InputLatencyConfigurationError(ValueError):
    pass


@dataclass(frozen=True)
class ThresholdProfile:
    name: str
    manufacturer: str
    model: str
    min_refresh_rate_hz: float | None
    max_refresh_rate_hz: float | None
    min_sampled_actions: int
    max_dropped_action_ratio: float
    max_p95_touch_to_decision_ns: int
    max_p95_decision_to_response_ns: int
    max_p95_response_to_render_ns: int
    max_p95_touch_to_render_ns: int

    @property
    def specificity(self) -> int:
        return sum(value != "*" for value in (self.manufacturer, self.model)) + int(
            self.min_refresh_rate_hz is not None
        ) + int(self.max_refresh_rate_hz is not None)


@dataclass(frozen=True)
class EvaluationResult:
    report_path: Path
    profile_name: str
    violations: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return not self.violations


def _object(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise InputLatencyConfigurationError(f"{label} must be an object")
    return value


def _read(path: Path, maximum_bytes: int) -> Mapping[str, Any]:
    try:
        value = strict_json.load_file(path, maximum_bytes=maximum_bytes, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise InputLatencyConfigurationError(f"invalid JSON in {path}: {exc}") from exc
    assert isinstance(value, Mapping)
    return value


def _string(source: Mapping[str, Any], key: str, label: str) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        raise InputLatencyConfigurationError(f"{label}.{key} must be a non-blank string")
    return value.strip()


def _integer(source: Mapping[str, Any], key: str, label: str) -> int:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise InputLatencyConfigurationError(f"{label}.{key} must be a non-negative integer")
    return value


def _number(source: Mapping[str, Any], key: str, label: str) -> float:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise InputLatencyConfigurationError(f"{label}.{key} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise InputLatencyConfigurationError(f"{label}.{key} must be finite")
    return result


def _optional_non_negative_number(
    source: Mapping[str, Any], key: str, label: str
) -> float | None:
    if key not in source or source[key] is None:
        return None
    value = _number(source, key, label)
    if value < 0.0:
        raise InputLatencyConfigurationError(f"{label}.{key} must be non-negative")
    return value


def _matches(pattern: str, actual: str) -> bool:
    return pattern == "*" or pattern.casefold() == actual.casefold()


def load_thresholds(path: Path) -> tuple[ThresholdProfile, ...]:
    root = _read(path, MAX_THRESHOLDS_BYTES)
    if root.get("schemaVersion") != SCHEMA_VERSION:
        raise InputLatencyConfigurationError(
            f"{path}.schemaVersion must equal {SCHEMA_VERSION}"
        )
    raw_profiles = root.get("profiles")
    if not isinstance(raw_profiles, list) or not raw_profiles:
        raise InputLatencyConfigurationError(f"{path}.profiles must be a non-empty array")
    profiles: list[ThresholdProfile] = []
    for index, raw in enumerate(raw_profiles):
        label = f"profiles[{index}]"
        item = _object(raw, label)
        minimum = _optional_non_negative_number(item, "minRefreshRateHz", label)
        maximum = _optional_non_negative_number(item, "maxRefreshRateHz", label)
        if minimum is not None and maximum is not None and minimum > maximum:
            raise InputLatencyConfigurationError(f"{label} refresh-rate bounds are reversed")
        dropped_ratio = _number(item, "maxDroppedActionRatio", label)
        if not 0.0 <= dropped_ratio <= 1.0:
            raise InputLatencyConfigurationError(
                f"{label}.maxDroppedActionRatio must be between 0 and 1"
            )
        profile = ThresholdProfile(
            name=_string(item, "name", label),
            manufacturer=_string(item, "manufacturer", label),
            model=_string(item, "model", label),
            min_refresh_rate_hz=minimum,
            max_refresh_rate_hz=maximum,
            min_sampled_actions=_integer(item, "minSampledActions", label),
            max_dropped_action_ratio=dropped_ratio,
            max_p95_touch_to_decision_ns=_integer(item, "maxP95TouchToDecisionNs", label),
            max_p95_decision_to_response_ns=_integer(item, "maxP95DecisionToResponseNs", label),
            max_p95_response_to_render_ns=_integer(item, "maxP95ResponseToRenderNs", label),
            max_p95_touch_to_render_ns=_integer(item, "maxP95TouchToRenderNs", label),
        )
        if profile.max_p95_touch_to_render_ns < (
            profile.max_p95_touch_to_decision_ns
            + profile.max_p95_decision_to_response_ns
        ):
            raise InputLatencyConfigurationError(
                f"{label}.maxP95TouchToRenderNs cannot be below the decision+response limits"
            )
        profiles.append(profile)
    names = [profile.name for profile in profiles]
    if len(names) != len(set(names)):
        raise InputLatencyConfigurationError("threshold profile names must be unique")
    return tuple(profiles)


def validate_report(report: Mapping[str, Any]) -> None:
    if report.get("schemaVersion") != SCHEMA_VERSION:
        raise InputLatencyConfigurationError(
            f"report.schemaVersion must equal {SCHEMA_VERSION}"
        )
    if _string(report, "measurementKind", "report") != MEASUREMENT_KIND:
        raise InputLatencyConfigurationError(
            f"report.measurementKind must equal {MEASUREMENT_KIND}"
        )
    _string(report, "scenario", "report")
    _string(report, "manufacturer", "report")
    _string(report, "model", "report")
    _integer(report, "durationMs", "report")
    _integer(report, "apiLevel", "report")
    refresh = _number(report, "refreshRateHz", "report")
    if refresh <= 0.0 or refresh > 240.0:
        raise InputLatencyConfigurationError(
            "report.refreshRateHz must be positive and no greater than 240"
        )
    injected = _integer(report, "injectedActions", "report")
    sampled = _integer(report, "sampledActions", "report")
    dropped = _integer(report, "droppedActions", "report")
    if sampled > injected:
        raise InputLatencyConfigurationError(
            "report.sampledActions cannot exceed injectedActions"
        )
    if sampled + dropped < injected:
        raise InputLatencyConfigurationError(
            "report sampled+dropped actions cannot be below injectedActions"
        )

    for prefix in (
        "TouchToDecision",
        "DecisionToResponse",
        "ResponseToRender",
        "TouchToRender",
    ):
        p50 = _integer(report, f"p50{prefix}Ns", "report")
        p95 = _integer(report, f"p95{prefix}Ns", "report")
        p99 = _integer(report, f"p99{prefix}Ns", "report")
        if p50 > p95 or p95 > p99:
            raise InputLatencyConfigurationError(
                f"report {prefix} percentiles must satisfy p50 <= p95 <= p99"
            )
    if _integer(report, "p95TouchToRenderNs", "report") < _integer(
        report, "p95TouchToDecisionNs", "report"
    ):
        raise InputLatencyConfigurationError(
            "report.p95TouchToRenderNs cannot be below p95TouchToDecisionNs"
        )
    if _integer(report, "p95TouchToRenderNs", "report") < _integer(
        report, "p95ResponseToRenderNs", "report"
    ):
        raise InputLatencyConfigurationError(
            "report.p95TouchToRenderNs cannot be below p95ResponseToRenderNs"
        )


def select_profile(
    report: Mapping[str, Any], profiles: Sequence[ThresholdProfile]
) -> ThresholdProfile:
    manufacturer = _string(report, "manufacturer", "report")
    model = _string(report, "model", "report")
    refresh = _number(report, "refreshRateHz", "report")
    matches = [
        profile
        for profile in profiles
        if _matches(profile.manufacturer, manufacturer)
        and _matches(profile.model, model)
        and (
            profile.min_refresh_rate_hz is None
            or refresh >= profile.min_refresh_rate_hz
        )
        and (
            profile.max_refresh_rate_hz is None
            or refresh <= profile.max_refresh_rate_hz
        )
    ]
    if not matches:
        raise InputLatencyConfigurationError(
            "no input-latency threshold profile matches "
            f"manufacturer={manufacturer!r}, model={model!r}, refreshRateHz={refresh}"
        )
    specificity = max(profile.specificity for profile in matches)
    best = [profile for profile in matches if profile.specificity == specificity]
    if len(best) != 1:
        raise InputLatencyConfigurationError(
            "ambiguous input-latency threshold profiles: "
            + ", ".join(sorted(profile.name for profile in best))
        )
    return best[0]


def evaluate_report(
    path: Path,
    report: Mapping[str, Any],
    profiles: Sequence[ThresholdProfile],
) -> EvaluationResult:
    validate_report(report)
    profile = select_profile(report, profiles)
    sampled = _integer(report, "sampledActions", "report")
    dropped = _integer(report, "droppedActions", "report")
    injected = _integer(report, "injectedActions", "report")
    dropped_ratio = 0.0 if injected == 0 else dropped / injected
    checks = (
        ("sampledActions", sampled, profile.min_sampled_actions, "minimum"),
        ("p95TouchToDecisionNs", _integer(report, "p95TouchToDecisionNs", "report"), profile.max_p95_touch_to_decision_ns, "maximum"),
        ("p95DecisionToResponseNs", _integer(report, "p95DecisionToResponseNs", "report"), profile.max_p95_decision_to_response_ns, "maximum"),
        ("p95ResponseToRenderNs", _integer(report, "p95ResponseToRenderNs", "report"), profile.max_p95_response_to_render_ns, "maximum"),
        ("p95TouchToRenderNs", _integer(report, "p95TouchToRenderNs", "report"), profile.max_p95_touch_to_render_ns, "maximum"),
    )
    violations: list[str] = []
    for name, actual, limit, direction in checks:
        failed = actual < limit if direction == "minimum" else actual > limit
        if failed:
            symbol = "<" if direction == "minimum" else ">"
            violations.append(f"{name} {actual} {symbol} {direction} {limit}")
    if dropped_ratio > profile.max_dropped_action_ratio:
        violations.append(
            f"droppedActionRatio {dropped_ratio:.6f} > maximum "
            f"{profile.max_dropped_action_ratio:.6f}"
        )
    return EvaluationResult(path, profile.name, tuple(violations))


def run(thresholds_path: Path, reports: Sequence[Path]) -> int:
    profiles = load_thresholds(thresholds_path)
    if not reports:
        raise InputLatencyConfigurationError("at least one report is required")
    failed = False
    for path in reports:
        result = evaluate_report(path, _read(path, MAX_REPORT_BYTES), profiles)
        if result.passed:
            print(f"PASS {path} [{result.profile_name}]")
        else:
            failed = True
            print(f"FAIL {path} [{result.profile_name}]")
            for violation in result.violations:
                print(f"  - {violation}")
    return 1 if failed else 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--thresholds", required=True, type=Path)
    parser.add_argument("reports", nargs="+", type=Path)
    args = parser.parse_args(argv)
    try:
        return run(args.thresholds, args.reports)
    except InputLatencyConfigurationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
