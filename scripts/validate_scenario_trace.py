#!/usr/bin/env python3
"""Validate candidate-bound Forest Run deterministic scenario trace evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json

SCHEMA_VERSION = 1
MAX_TRACE_BYTES = 256 * 1024
MAX_TRACE_EVENTS = 4_096
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
ALLOWED_ACTIONS = {
    "TAP_JUMP",
    "HOLD_JUMP_START",
    "HOLD_JUMP_END",
    "DUCK_START",
    "DUCK_END",
}
ROOT_KEYS = {
    "schema_version",
    "candidate_commit_sha",
    "artifact_sha256",
    "captured_at_utc_ms",
    "scenario",
    "event_count",
    "events",
}
EVENT_KEYS = {
    "sequence",
    "scheduled_at_micros",
    "dispatched_at_micros",
    "lateness_micros",
    "action",
}


class ScenarioTraceError(ValueError):
    """Raised when deterministic trace evidence is malformed or unbound."""


def _stable_read(path: Path) -> bytes:
    resolved = path.expanduser().resolve()
    try:
        before = resolved.stat()
    except FileNotFoundError as exc:
        raise ScenarioTraceError(f"scenario trace is missing: {resolved}") from exc
    except OSError as exc:
        raise ScenarioTraceError(f"could not inspect scenario trace {resolved}: {exc}") from exc
    if not resolved.is_file():
        raise ScenarioTraceError(f"scenario trace is not a regular file: {resolved}")
    if before.st_size <= 0 or before.st_size > MAX_TRACE_BYTES:
        raise ScenarioTraceError(
            f"scenario trace must be between 1 and {MAX_TRACE_BYTES} bytes"
        )
    try:
        raw = resolved.read_bytes()
        after = resolved.stat()
    except OSError as exc:
        raise ScenarioTraceError(f"could not read scenario trace {resolved}: {exc}") from exc
    if (
        len(raw) != before.st_size
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise ScenarioTraceError(f"scenario trace changed while being read: {resolved}")
    return raw


def _exact_keys(value: Mapping[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing:
        raise ScenarioTraceError(f"{label} is missing keys: {', '.join(missing)}")
    if extra:
        raise ScenarioTraceError(f"{label} contains unrecognized keys: {', '.join(extra)}")


def _string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ScenarioTraceError(f"{label} must be a non-empty string")
    return value


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ScenarioTraceError(f"{label} must be an integer >= {minimum}")
    return value


def _scenario_names(root: Path) -> set[str]:
    source = (
        root
        / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterDirector.kt"
    )
    try:
        text = source.read_text(encoding="utf-8")
    except OSError as exc:
        raise ScenarioTraceError(f"could not read canonical scenario catalogue: {exc}") from exc
    enum_start = text.find("enum class EncounterScenario(")
    init_start = text.find("\n    init {", enum_start)
    if enum_start < 0 or init_start < 0:
        raise ScenarioTraceError("canonical scenario catalogue structure is missing")
    names = set(
        re.findall(
            r"^    ([A-Z][A-Z0-9_]*)\($",
            text[enum_start:init_start],
            flags=re.MULTILINE,
        )
    )
    if not names:
        raise ScenarioTraceError("canonical scenario catalogue is empty")
    return names


def validate_trace(
    data: Any,
    *,
    raw: bytes,
    repository_root: Path,
    expected_commit_sha: str | None = None,
    expected_artifact_sha256: str | None = None,
    expected_scenario: str | None = None,
) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise ScenarioTraceError("scenario trace root must be an object")
    _exact_keys(data, ROOT_KEYS, "scenario trace")

    if _integer(data["schema_version"], "schema_version", minimum=1) != SCHEMA_VERSION:
        raise ScenarioTraceError(f"schema_version must equal {SCHEMA_VERSION}")

    commit = _string(data["candidate_commit_sha"], "candidate_commit_sha")
    artifact = _string(data["artifact_sha256"], "artifact_sha256")
    if not COMMIT_RE.fullmatch(commit):
        raise ScenarioTraceError("candidate_commit_sha must be lowercase 40-hex")
    if not SHA256_RE.fullmatch(artifact):
        raise ScenarioTraceError("artifact_sha256 must be lowercase 64-hex")
    if expected_commit_sha is not None and commit != expected_commit_sha.lower():
        raise ScenarioTraceError("candidate_commit_sha does not match the expected candidate")
    if expected_artifact_sha256 is not None and artifact != expected_artifact_sha256.lower():
        raise ScenarioTraceError("artifact_sha256 does not match the expected artifact")

    captured_at = _integer(data["captured_at_utc_ms"], "captured_at_utc_ms")
    scenario = _string(data["scenario"], "scenario")
    if scenario not in _scenario_names(repository_root):
        raise ScenarioTraceError(f"scenario is not in the canonical catalogue: {scenario}")
    if expected_scenario is not None and scenario != expected_scenario:
        raise ScenarioTraceError("scenario does not match the expected scenario")

    declared_count = _integer(data["event_count"], "event_count")
    events = data["events"]
    if not isinstance(events, list):
        raise ScenarioTraceError("events must be an array")
    if len(events) > MAX_TRACE_EVENTS:
        raise ScenarioTraceError(f"events exceeds the {MAX_TRACE_EVENTS}-event limit")
    if declared_count != len(events):
        raise ScenarioTraceError("event_count does not match events length")

    previous_scheduled = -1
    previous_dispatched = -1
    action_counts = {action: 0 for action in sorted(ALLOWED_ACTIONS)}
    maximum_lateness = 0
    for index, raw_event in enumerate(events):
        label = f"events[{index}]"
        if not isinstance(raw_event, dict):
            raise ScenarioTraceError(f"{label} must be an object")
        _exact_keys(raw_event, EVENT_KEYS, label)
        sequence = _integer(raw_event["sequence"], f"{label}.sequence")
        scheduled = _integer(
            raw_event["scheduled_at_micros"],
            f"{label}.scheduled_at_micros",
        )
        dispatched = _integer(
            raw_event["dispatched_at_micros"],
            f"{label}.dispatched_at_micros",
        )
        lateness = _integer(raw_event["lateness_micros"], f"{label}.lateness_micros")
        action = _string(raw_event["action"], f"{label}.action")

        if sequence != index:
            raise ScenarioTraceError(f"{label}.sequence must equal {index}")
        if scheduled < previous_scheduled:
            raise ScenarioTraceError(f"{label} schedule is not chronological")
        if dispatched < previous_dispatched:
            raise ScenarioTraceError(f"{label} dispatch is not chronological")
        if dispatched < scheduled:
            raise ScenarioTraceError(f"{label} dispatch precedes its schedule")
        if lateness != dispatched - scheduled:
            raise ScenarioTraceError(f"{label}.lateness_micros is inconsistent")
        if action not in ALLOWED_ACTIONS:
            raise ScenarioTraceError(f"{label}.action is unknown: {action}")

        previous_scheduled = scheduled
        previous_dispatched = dispatched
        maximum_lateness = max(maximum_lateness, lateness)
        action_counts[action] += 1

    return {
        "status": "valid",
        "schema_version": SCHEMA_VERSION,
        "candidate_commit_sha": commit,
        "artifact_sha256": artifact,
        "captured_at_utc_ms": captured_at,
        "scenario": scenario,
        "event_count": declared_count,
        "maximum_lateness_micros": maximum_lateness,
        "action_counts": action_counts,
        "payload_sha256": hashlib.sha256(raw).hexdigest(),
    }


def load_and_validate(
    path: Path,
    *,
    repository_root: Path,
    expected_commit_sha: str | None = None,
    expected_artifact_sha256: str | None = None,
    expected_scenario: str | None = None,
) -> dict[str, Any]:
    raw = _stable_read(path)
    try:
        data = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=MAX_TRACE_BYTES,
            maximum_depth=16,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise ScenarioTraceError(str(exc)) from exc
    return validate_trace(
        data,
        raw=raw,
        repository_root=repository_root.expanduser().resolve(),
        expected_commit_sha=expected_commit_sha,
        expected_artifact_sha256=expected_artifact_sha256,
        expected_scenario=expected_scenario,
    )


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--expected-sha")
    parser.add_argument("--expected-artifact-sha256")
    parser.add_argument("--expected-scenario")
    args = parser.parse_args(argv)

    try:
        summary = load_and_validate(
            args.trace,
            repository_root=args.root,
            expected_commit_sha=args.expected_sha,
            expected_artifact_sha256=args.expected_artifact_sha256,
            expected_scenario=args.expected_scenario,
        )
    except (OSError, ScenarioTraceError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary, sort_keys=True, allow_nan=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
