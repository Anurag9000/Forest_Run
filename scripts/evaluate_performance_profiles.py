#!/usr/bin/env python3
"""Evaluate Forest Run physical-device performance reports against measured gates."""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

SCHEMA_VERSION = 1
REQUIRED_LIMITS = (
    "minSampledFrames",
    "maxP95ProcessingNs",
    "maxP99ProcessingNs",
    "maxSlowFrameRatio",
    "maxUsedHeapBytes",
)
WORKLOAD_PAIRS = (
    ("currentEntities", "peakEntities"),
    ("currentSeedOrbs", "peakSeedOrbs"),
    ("currentParticles", "peakParticles"),
    ("currentDialogueBubbles", "peakDialogueBubbles"),
    ("currentFlavorTexts", "peakFlavorTexts"),
)


class ConfigurationError(ValueError):
    """Raised when reports or acceptance thresholds are malformed or ambiguous."""


@dataclass(frozen=True)
class ThresholdProfile:
    name: str
    manufacturer: str
    model: str
    scenario: str
    min_refresh_rate_hz: float | None
    max_refresh_rate_hz: float | None
    min_sampled_frames: int
    max_p95_processing_ns: int
    max_p99_processing_ns: int
    max_slow_frame_ratio: float
    max_used_heap_bytes: int
    max_maximum_processing_ns: int | None
    min_ghost_writes_completed: int | None = None
    max_ghost_write_failures: int | None = None
    min_maximum_ghost_frame_count: int | None = None
    max_ghost_write_duration_ns: int | None = None

    @property
    def specificity(self) -> int:
        return sum(
            value != "*"
            for value in (self.manufacturer, self.model, self.scenario)
        ) + int(self.min_refresh_rate_hz is not None) + int(self.max_refresh_rate_hz is not None)


@dataclass(frozen=True)
class EvaluationResult:
    report_path: Path
    profile_name: str
    violations: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return not self.violations


@dataclass(frozen=True)
class ReportEvidence:
    sampled_frames: int
    p95_processing_ns: int
    p99_processing_ns: int
    maximum_processing_ns: int
    slow_frame_ratio: float
    used_heap_bytes: int
    ghost_writes_completed: int
    ghost_writes_failed: int
    maximum_ghost_frame_count: int
    maximum_ghost_write_duration_ns: int


def _read_json(path: Path) -> dict[str, Any]:
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ConfigurationError(f"could not read {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ConfigurationError(f"invalid JSON in {path}: {exc}") from exc
    if not isinstance(parsed, dict):
        raise ConfigurationError(f"{path} must contain a JSON object")
    return parsed


def _required_string(source: dict[str, Any], key: str, label: str) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ConfigurationError(f"{label}.{key} must be a non-blank string")
    return value.strip()


def _finite_number(source: dict[str, Any], key: str, label: str) -> float:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ConfigurationError(f"{label}.{key} must be numeric")
    number = float(value)
    if not math.isfinite(number):
        raise ConfigurationError(f"{label}.{key} must be finite")
    return number


def _non_negative_int(source: dict[str, Any], key: str, label: str) -> int:
    value = source.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ConfigurationError(f"{label}.{key} must be a non-negative integer")
    return value


def _positive_int(source: dict[str, Any], key: str, label: str) -> int:
    value = _non_negative_int(source, key, label)
    if value <= 0:
        raise ConfigurationError(f"{label}.{key} must be a positive integer")
    return value


def _optional_non_negative_int(
    source: dict[str, Any],
    key: str,
    label: str,
) -> int | None:
    if key not in source or source[key] is None:
        return None
    return _non_negative_int(source, key, label)


def _optional_non_negative_number(
    source: dict[str, Any],
    key: str,
    label: str,
) -> float | None:
    if key not in source or source[key] is None:
        return None
    number = _finite_number(source, key, label)
    if number < 0:
        raise ConfigurationError(f"{label}.{key} must be non-negative")
    return number


def _parse_profile(raw: Any, index: int) -> ThresholdProfile:
    label = f"profiles[{index}]"
    if not isinstance(raw, dict):
        raise ConfigurationError(f"{label} must be an object")
    for required_limit in REQUIRED_LIMITS:
        if required_limit not in raw:
            raise ConfigurationError(f"{label}.{required_limit} is required")

    max_slow_frame_ratio = _finite_number(raw, "maxSlowFrameRatio", label)
    if not 0.0 <= max_slow_frame_ratio <= 1.0:
        raise ConfigurationError(f"{label}.maxSlowFrameRatio must be between 0 and 1")

    min_refresh = _optional_non_negative_number(raw, "minRefreshRateHz", label)
    max_refresh = _optional_non_negative_number(raw, "maxRefreshRateHz", label)
    if min_refresh is not None and max_refresh is not None and min_refresh > max_refresh:
        raise ConfigurationError(f"{label} refresh-rate bounds are reversed")

    profile = ThresholdProfile(
        name=_required_string(raw, "name", label),
        manufacturer=_required_string(raw, "manufacturer", label),
        model=_required_string(raw, "model", label),
        scenario=_required_string(raw, "scenario", label),
        min_refresh_rate_hz=min_refresh,
        max_refresh_rate_hz=max_refresh,
        min_sampled_frames=_non_negative_int(raw, "minSampledFrames", label),
        max_p95_processing_ns=_non_negative_int(raw, "maxP95ProcessingNs", label),
        max_p99_processing_ns=_non_negative_int(raw, "maxP99ProcessingNs", label),
        max_slow_frame_ratio=max_slow_frame_ratio,
        max_used_heap_bytes=_non_negative_int(raw, "maxUsedHeapBytes", label),
        max_maximum_processing_ns=_optional_non_negative_int(
            raw, "maxMaximumProcessingNs", label
        ),
        min_ghost_writes_completed=_optional_non_negative_int(
            raw, "minGhostWritesCompleted", label
        ),
        max_ghost_write_failures=_optional_non_negative_int(
            raw, "maxGhostWriteFailures", label
        ),
        min_maximum_ghost_frame_count=_optional_non_negative_int(
            raw, "minMaximumGhostFrameCount", label
        ),
        max_ghost_write_duration_ns=_optional_non_negative_int(
            raw, "maxGhostWriteDurationNs", label
        ),
    )
    if profile.max_p95_processing_ns > profile.max_p99_processing_ns:
        raise ConfigurationError(f"{label} p95 limit cannot exceed p99 limit")
    return profile


def load_thresholds(path: Path) -> tuple[ThresholdProfile, ...]:
    raw = _read_json(path)
    if raw.get("schemaVersion") != SCHEMA_VERSION:
        raise ConfigurationError(
            f"{path}.schemaVersion must equal {SCHEMA_VERSION}"
        )
    profiles = raw.get("profiles")
    if not isinstance(profiles, list) or not profiles:
        raise ConfigurationError(f"{path}.profiles must be a non-empty array")
    parsed = tuple(_parse_profile(profile, index) for index, profile in enumerate(profiles))
    names = [profile.name for profile in parsed]
    if len(names) != len(set(names)):
        raise ConfigurationError("threshold profile names must be unique")
    return parsed


def _matches(pattern: str, actual: str) -> bool:
    return pattern == "*" or pattern.casefold() == actual.casefold()


def select_profile(
    report: dict[str, Any],
    profiles: Sequence[ThresholdProfile],
) -> ThresholdProfile:
    manufacturer = _required_string(report, "manufacturer", "report")
    model = _required_string(report, "model", "report")
    scenario = _required_string(report, "scenario", "report")
    refresh_rate = _finite_number(report, "refreshRateHz", "report")
    if refresh_rate <= 0:
        raise ConfigurationError("report.refreshRateHz must be positive")

    matches = [
        profile
        for profile in profiles
        if _matches(profile.manufacturer, manufacturer)
        and _matches(profile.model, model)
        and _matches(profile.scenario, scenario)
        and (
            profile.min_refresh_rate_hz is None
            or refresh_rate >= profile.min_refresh_rate_hz
        )
        and (
            profile.max_refresh_rate_hz is None
            or refresh_rate <= profile.max_refresh_rate_hz
        )
    ]
    if not matches:
        raise ConfigurationError(
            "no threshold profile matches "
            f"manufacturer={manufacturer!r}, model={model!r}, "
            f"scenario={scenario!r}, refreshRateHz={refresh_rate}"
        )

    best_specificity = max(profile.specificity for profile in matches)
    best = [profile for profile in matches if profile.specificity == best_specificity]
    if len(best) != 1:
        names = ", ".join(sorted(profile.name for profile in best))
        raise ConfigurationError(f"ambiguous threshold profiles: {names}")
    return best[0]


def _validate_report_evidence(report: dict[str, Any]) -> ReportEvidence:
    _required_string(report, "scenario", "report")
    _required_string(report, "manufacturer", "report")
    _required_string(report, "model", "report")
    _non_negative_int(report, "durationMs", "report")
    _non_negative_int(report, "apiLevel", "report")
    refresh_rate = _finite_number(report, "refreshRateHz", "report")
    if refresh_rate <= 0:
        raise ConfigurationError("report.refreshRateHz must be positive")

    sampled_frames = _non_negative_int(report, "sampledFrames", "report")
    total_frames = _non_negative_int(report, "totalFrames", "report")
    slow_frames = _non_negative_int(report, "slowFrames", "report")
    if sampled_frames > total_frames:
        raise ConfigurationError("report.sampledFrames cannot exceed totalFrames")
    if slow_frames > total_frames:
        raise ConfigurationError("report.slowFrames cannot exceed totalFrames")

    slow_ratio = _finite_number(report, "slowFrameRatio", "report")
    if not 0.0 <= slow_ratio <= 1.0:
        raise ConfigurationError("report.slowFrameRatio must be between 0 and 1")
    expected_slow_ratio = 0.0 if total_frames == 0 else slow_frames / total_frames
    if not math.isclose(
        slow_ratio,
        expected_slow_ratio,
        rel_tol=1e-9,
        abs_tol=1e-12,
    ):
        raise ConfigurationError(
            "report.slowFrameRatio must equal slowFrames / totalFrames"
        )

    _positive_int(report, "frameBudgetNs", "report")
    mean_update = _non_negative_int(report, "meanUpdateNs", "report")
    mean_render = _non_negative_int(report, "meanRenderNs", "report")
    mean_processing = _non_negative_int(report, "meanProcessingNs", "report")
    p50 = _non_negative_int(report, "p50ProcessingNs", "report")
    p95 = _non_negative_int(report, "p95ProcessingNs", "report")
    p99 = _non_negative_int(report, "p99ProcessingNs", "report")
    maximum = _non_negative_int(report, "maximumProcessingNs", "report")
    if p50 > p95 or p95 > p99 or p99 > maximum:
        raise ConfigurationError("report processing percentiles are not ordered")
    if mean_processing > maximum:
        raise ConfigurationError(
            "report.meanProcessingNs cannot exceed maximumProcessingNs"
        )
    if mean_update > maximum or mean_render > maximum:
        raise ConfigurationError(
            "report update/render means cannot exceed maximumProcessingNs"
        )

    used_heap = _non_negative_int(report, "usedHeapBytes", "report")
    max_heap = _non_negative_int(report, "maxHeapBytes", "report")
    if used_heap > max_heap:
        raise ConfigurationError("report.usedHeapBytes cannot exceed maxHeapBytes")

    for current_key, peak_key in WORKLOAD_PAIRS:
        current = _non_negative_int(report, current_key, "report")
        peak = _non_negative_int(report, peak_key, "report")
        if current > peak:
            raise ConfigurationError(
                f"report.{current_key} cannot exceed {peak_key}"
            )

    ghost_started = _non_negative_int(report, "ghostWritesStarted", "report")
    ghost_completed = _non_negative_int(report, "ghostWritesCompleted", "report")
    ghost_failed = _non_negative_int(report, "ghostWritesFailed", "report")
    if ghost_completed > ghost_started:
        raise ConfigurationError(
            "report.ghostWritesCompleted cannot exceed ghostWritesStarted"
        )
    if ghost_failed > ghost_completed:
        raise ConfigurationError(
            "report.ghostWritesFailed cannot exceed ghostWritesCompleted"
        )

    latest_ghost_frames = _non_negative_int(
        report, "latestGhostFrameCount", "report"
    )
    maximum_ghost_frames = _non_negative_int(
        report, "maximumGhostFrameCount", "report"
    )
    if latest_ghost_frames > maximum_ghost_frames:
        raise ConfigurationError(
            "report.latestGhostFrameCount cannot exceed maximumGhostFrameCount"
        )

    latest_ghost_duration = _non_negative_int(
        report, "latestGhostWriteDurationNs", "report"
    )
    maximum_ghost_duration = _non_negative_int(
        report, "maximumGhostWriteDurationNs", "report"
    )
    if latest_ghost_duration > maximum_ghost_duration:
        raise ConfigurationError(
            "report.latestGhostWriteDurationNs cannot exceed "
            "maximumGhostWriteDurationNs"
        )

    return ReportEvidence(
        sampled_frames=sampled_frames,
        p95_processing_ns=p95,
        p99_processing_ns=p99,
        maximum_processing_ns=maximum,
        slow_frame_ratio=slow_ratio,
        used_heap_bytes=used_heap,
        ghost_writes_completed=ghost_completed,
        ghost_writes_failed=ghost_failed,
        maximum_ghost_frame_count=maximum_ghost_frames,
        maximum_ghost_write_duration_ns=maximum_ghost_duration,
    )


def evaluate_report(
    report_path: Path,
    report: dict[str, Any],
    profiles: Sequence[ThresholdProfile],
) -> EvaluationResult:
    evidence = _validate_report_evidence(report)
    profile = select_profile(report, profiles)

    violations: list[str] = []
    if evidence.sampled_frames < profile.min_sampled_frames:
        violations.append(
            f"sampledFrames {evidence.sampled_frames} < minimum {profile.min_sampled_frames}"
        )
    if evidence.p95_processing_ns > profile.max_p95_processing_ns:
        violations.append(
            "p95ProcessingNs "
            f"{evidence.p95_processing_ns} > limit {profile.max_p95_processing_ns}"
        )
    if evidence.p99_processing_ns > profile.max_p99_processing_ns:
        violations.append(
            "p99ProcessingNs "
            f"{evidence.p99_processing_ns} > limit {profile.max_p99_processing_ns}"
        )
    if evidence.slow_frame_ratio > profile.max_slow_frame_ratio:
        violations.append(
            "slowFrameRatio "
            f"{evidence.slow_frame_ratio:.6f} > limit "
            f"{profile.max_slow_frame_ratio:.6f}"
        )
    if evidence.used_heap_bytes > profile.max_used_heap_bytes:
        violations.append(
            "usedHeapBytes "
            f"{evidence.used_heap_bytes} > limit {profile.max_used_heap_bytes}"
        )
    if (
        profile.max_maximum_processing_ns is not None
        and evidence.maximum_processing_ns > profile.max_maximum_processing_ns
    ):
        violations.append(
            "maximumProcessingNs "
            f"{evidence.maximum_processing_ns} > limit "
            f"{profile.max_maximum_processing_ns}"
        )
    if (
        profile.min_ghost_writes_completed is not None
        and evidence.ghost_writes_completed < profile.min_ghost_writes_completed
    ):
        violations.append(
            "ghostWritesCompleted "
            f"{evidence.ghost_writes_completed} < minimum "
            f"{profile.min_ghost_writes_completed}"
        )
    if (
        profile.max_ghost_write_failures is not None
        and evidence.ghost_writes_failed > profile.max_ghost_write_failures
    ):
        violations.append(
            "ghostWritesFailed "
            f"{evidence.ghost_writes_failed} > limit "
            f"{profile.max_ghost_write_failures}"
        )
    if (
        profile.min_maximum_ghost_frame_count is not None
        and evidence.maximum_ghost_frame_count
        < profile.min_maximum_ghost_frame_count
    ):
        violations.append(
            "maximumGhostFrameCount "
            f"{evidence.maximum_ghost_frame_count} < minimum "
            f"{profile.min_maximum_ghost_frame_count}"
        )
    if (
        profile.max_ghost_write_duration_ns is not None
        and evidence.maximum_ghost_write_duration_ns
        > profile.max_ghost_write_duration_ns
    ):
        violations.append(
            "maximumGhostWriteDurationNs "
            f"{evidence.maximum_ghost_write_duration_ns} > limit "
            f"{profile.max_ghost_write_duration_ns}"
        )

    return EvaluationResult(report_path, profile.name, tuple(violations))


def _expand_report_paths(arguments: Iterable[Path]) -> list[Path]:
    expanded: list[Path] = []
    for argument in arguments:
        if argument.is_dir():
            expanded.extend(sorted(argument.glob("*.json")))
        elif argument.is_file():
            expanded.append(argument)
        else:
            raise ConfigurationError(f"report path does not exist: {argument}")
    unique = sorted(set(path.resolve() for path in expanded))
    if not unique:
        raise ConfigurationError("no JSON performance reports were supplied")
    return [Path(path) for path in unique]


def run(thresholds_path: Path, report_arguments: Sequence[Path]) -> int:
    profiles = load_thresholds(thresholds_path)
    report_paths = _expand_report_paths(report_arguments)
    results = [
        evaluate_report(path, _read_json(path), profiles)
        for path in report_paths
    ]

    failed = False
    for result in results:
        if result.passed:
            print(f"PASS {result.report_path} [{result.profile_name}]")
            continue
        failed = True
        print(f"FAIL {result.report_path} [{result.profile_name}]")
        for violation in result.violations:
            print(f"  - {violation}")
    print(
        f"Evaluated {len(results)} report(s): "
        f"{sum(result.passed for result in results)} passed, "
        f"{sum(not result.passed for result in results)} failed"
    )
    return 1 if failed else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Evaluate Forest Run performance JSON reports against a "
            "candidate-specific threshold manifest."
        )
    )
    parser.add_argument("--thresholds", required=True, type=Path)
    parser.add_argument("reports", nargs="+", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return run(args.thresholds, args.reports)
    except ConfigurationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
