#!/usr/bin/env python3
"""Reconstruct deterministic scenario definitions and input scripts from Kotlin source."""

from __future__ import annotations

import hashlib
import json
import math
import re
import struct
from dataclasses import dataclass
from pathlib import Path

SCENARIO_FORMAT_VERSION = 1
TRACE_CONTRACT_FORMAT_VERSION = 1
MICROS_PER_SECOND = 1_000_000.0
MICRO_PIXELS_PER_PIXEL = 1_000_000.0
MAX_SOURCE_BYTES = 2 * 1024 * 1024
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1
NUMBER = (
    r"[+-]?(?:(?:\d[\d_]*(?:\.\d[\d_]*)?)|(?:\.\d[\d_]*))"
    r"(?:[eE][+-]?\d[\d_]*)?[fF]?"
)


class ScenarioSourceContractError(ValueError):
    """Raised when checked-in scenario source cannot be reconstructed exactly."""


@dataclass(frozen=True)
class EncounterStepDefinition:
    at_micros: int
    entity_type: str
    x_offset_micro_pixels: int
    variant: str


@dataclass(frozen=True)
class InputStepDefinition:
    at_micros: int
    action: str


@dataclass(frozen=True)
class ScenarioDefinition:
    name: str
    title: str
    summary: str
    forced_biome: str
    starts_with_bloom: bool
    allow_ghost_playback: bool
    steps: tuple[EncounterStepDefinition, ...]


@dataclass(frozen=True)
class ScenarioTraceContract:
    scenario: ScenarioDefinition
    input_steps: tuple[InputStepDefinition, ...]
    scenario_definition_sha256: str
    trace_contract_sha256: str


def _stable_text(path: Path) -> str:
    resolved = path.expanduser().resolve()
    try:
        before = resolved.stat()
    except FileNotFoundError as exc:
        raise ScenarioSourceContractError(f"source file is missing: {resolved}") from exc
    except OSError as exc:
        raise ScenarioSourceContractError(f"could not inspect source file {resolved}: {exc}") from exc
    if not resolved.is_file():
        raise ScenarioSourceContractError(f"source path is not a regular file: {resolved}")
    if before.st_size <= 0 or before.st_size > MAX_SOURCE_BYTES:
        raise ScenarioSourceContractError(
            f"source file must be between 1 and {MAX_SOURCE_BYTES} bytes: {resolved}"
        )
    try:
        raw = resolved.read_bytes()
        after = resolved.stat()
    except OSError as exc:
        raise ScenarioSourceContractError(f"could not read source file {resolved}: {exc}") from exc
    if (
        len(raw) != before.st_size
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise ScenarioSourceContractError(f"source file changed while being read: {resolved}")
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ScenarioSourceContractError(f"source file is not valid UTF-8: {resolved}") from exc


def _balanced_parenthesized(text: str, open_index: int) -> str:
    if open_index < 0 or open_index >= len(text) or text[open_index] != "(":
        raise ScenarioSourceContractError("balanced source extraction did not start at '('")
    depth = 0
    in_string = False
    escaped = False
    line_comment = False
    block_comment_depth = 0
    index = open_index
    while index < len(text):
        character = text[index]
        next_character = text[index + 1] if index + 1 < len(text) else ""

        if line_comment:
            if character == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment_depth > 0:
            if character == "/" and next_character == "*":
                block_comment_depth += 1
                index += 2
                continue
            if character == "*" and next_character == "/":
                block_comment_depth -= 1
                index += 2
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
            index += 1
            continue

        if character == "/" and next_character == "/":
            line_comment = True
            index += 2
            continue
        if character == "/" and next_character == "*":
            block_comment_depth = 1
            index += 2
            continue
        if character == '"':
            in_string = True
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
            if depth == 0:
                return text[open_index : index + 1]
            if depth < 0:
                break
        index += 1
    raise ScenarioSourceContractError("unterminated parenthesized Kotlin source block")


def _float32(value: str, label: str) -> float:
    try:
        parsed = float(value.rstrip("fF").replace("_", ""))
        converted = struct.unpack(">f", struct.pack(">f", parsed))[0]
    except (OverflowError, ValueError, struct.error) as exc:
        raise ScenarioSourceContractError(
            f"{label} is not a representable Kotlin Float literal"
        ) from exc
    if not math.isfinite(converted):
        raise ScenarioSourceContractError(f"{label} must be finite")
    return converted


def _kotlin_round_to_long(value: float, label: str) -> int:
    """Emulate kotlin.math.roundToLong: nearest, ties toward positive infinity."""
    if math.isnan(value):
        raise ScenarioSourceContractError(f"{label} cannot be NaN")
    if value >= LONG_MAX:
        return LONG_MAX
    if value <= LONG_MIN:
        return LONG_MIN
    return math.floor(value + 0.5)


def _seconds_to_micros(value: str) -> int:
    seconds = _float32(value, "scenario time")
    if seconds < 0:
        raise ScenarioSourceContractError("scenario time must be non-negative")
    return _kotlin_round_to_long(seconds * MICROS_PER_SECOND, "scenario time")


def _pixels_to_micro_pixels(value: str) -> int:
    pixels = _float32(value, "scenario offset")
    return _kotlin_round_to_long(
        pixels * MICRO_PIXELS_PER_PIXEL,
        "scenario offset",
    )


def _decode_kotlin_string(literal: str, label: str) -> str:
    try:
        value = json.loads(literal)
    except json.JSONDecodeError as exc:
        raise ScenarioSourceContractError(f"{label} is not a supported Kotlin string") from exc
    if not isinstance(value, str) or not value:
        raise ScenarioSourceContractError(f"{label} must be a non-empty string")
    return value


def _required_string_field(block: str, field: str) -> str:
    match = re.search(
        rf"\b{re.escape(field)}\s*=\s*(\"(?:\\.|[^\"\\])*\")",
        block,
    )
    if match is None:
        raise ScenarioSourceContractError(f"scenario is missing {field}")
    return _decode_kotlin_string(match.group(1), field)


def _extract_scenario_block(source: str, scenario_name: str) -> str:
    matches = list(
        re.finditer(
            rf"^    {re.escape(scenario_name)}\($",
            source,
            flags=re.MULTILINE,
        )
    )
    if len(matches) != 1:
        raise ScenarioSourceContractError(
            f"scenario declaration must occur exactly once: {scenario_name}"
        )
    open_index = source.find("(", matches[0].start())
    return scenario_name + _balanced_parenthesized(source, open_index)


def _parse_encounter_steps(block: str) -> tuple[EncounterStepDefinition, ...]:
    list_match = re.search(r"\bsteps\s*=\s*listOf\s*\(", block)
    if list_match is None:
        raise ScenarioSourceContractError("scenario is missing steps=listOf(...)")
    open_index = block.find("(", list_match.start())
    step_source = _balanced_parenthesized(block, open_index)
    pattern = re.compile(
        rf"EncounterStep\(\s*({NUMBER})\s*,\s*"
        rf"EntityType\.([A-Z][A-Z0-9_]*)\s*,\s*({NUMBER})"
        rf"(?:\s*,\s*EncounterVariant\.([A-Z][A-Z0-9_]*))?\s*\)"
    )
    matches = list(pattern.finditer(step_source))
    if step_source.count("EncounterStep(") != len(matches):
        raise ScenarioSourceContractError("an EncounterStep could not be parsed exactly")
    if not matches:
        raise ScenarioSourceContractError("scenario encounter step list is empty")
    return tuple(
        EncounterStepDefinition(
            at_micros=_seconds_to_micros(match.group(1)),
            entity_type=match.group(2),
            x_offset_micro_pixels=_pixels_to_micro_pixels(match.group(3)),
            variant=match.group(4) or "DEFAULT",
        )
        for match in matches
    )


def parse_scenario_definition(source: str, scenario_name: str) -> ScenarioDefinition:
    if not re.fullmatch(r"[A-Z][A-Z0-9_]*", scenario_name):
        raise ScenarioSourceContractError("scenario name is malformed")
    block = _extract_scenario_block(source, scenario_name)
    biome_match = re.search(r"\bforcedBiome\s*=\s*Biome\.([A-Z][A-Z0-9_]*)", block)
    return ScenarioDefinition(
        name=scenario_name,
        title=_required_string_field(block, "title"),
        summary=_required_string_field(block, "summary"),
        forced_biome=biome_match.group(1) if biome_match else "",
        starts_with_bloom=bool(
            re.search(r"\bstartsWithBloom\s*=\s*true\b", block)
        ),
        allow_ghost_playback=bool(
            re.search(r"\ballowGhostPlayback\s*=\s*true\b", block)
        ),
        steps=_parse_encounter_steps(block),
    )


def parse_input_steps(source: str, scenario_name: str) -> tuple[InputStepDefinition, ...]:
    match = re.search(
        rf"EncounterScenario\.{re.escape(scenario_name)}\s*->\s*listOf\s*\(",
        source,
    )
    if match is None:
        return ()
    open_index = source.find("(", match.start())
    step_source = _balanced_parenthesized(source, open_index)
    pattern = re.compile(
        rf"DebugScenarioStep\(\s*({NUMBER})\s*,\s*"
        rf"DebugScenarioAction\.([A-Z][A-Z0-9_]*)\s*\)"
    )
    matches = list(pattern.finditer(step_source))
    if step_source.count("DebugScenarioStep(") != len(matches):
        raise ScenarioSourceContractError("a DebugScenarioStep could not be parsed exactly")
    return tuple(
        InputStepDefinition(
            at_micros=_seconds_to_micros(item.group(1)),
            action=item.group(2),
        )
        for item in matches
    )


def _length_prefixed(value: str) -> str:
    return f"{len(value.encode('utf-8'))}:{value}\n"


def scenario_canonical_bytes(definition: ScenarioDefinition) -> bytes:
    parts = [
        f"forest-run-encounter-scenario-v{SCENARIO_FORMAT_VERSION}\n",
        _length_prefixed(definition.name),
        _length_prefixed(definition.title),
        _length_prefixed(definition.summary),
        _length_prefixed(definition.forced_biome),
        ("1" if definition.starts_with_bloom else "0") + "\n",
        ("1" if definition.allow_ghost_playback else "0") + "\n",
        f"{len(definition.steps)}\n",
    ]
    for index, step in enumerate(definition.steps):
        parts.extend(
            (
                f"{index}\n",
                f"{step.at_micros}\n",
                _length_prefixed(step.entity_type),
                f"{step.x_offset_micro_pixels}\n",
                _length_prefixed(step.variant),
            )
        )
    return "".join(parts).encode("utf-8")


def trace_contract_canonical_bytes(
    definition: ScenarioDefinition,
    input_steps: tuple[InputStepDefinition, ...],
) -> bytes:
    scenario_sha = hashlib.sha256(scenario_canonical_bytes(definition)).hexdigest()
    parts = [
        f"forest-run-scenario-trace-contract-v{TRACE_CONTRACT_FORMAT_VERSION}\n",
        _length_prefixed(definition.name),
        _length_prefixed(scenario_sha),
        f"{len(input_steps)}\n",
    ]
    for index, step in enumerate(input_steps):
        parts.extend(
            (
                f"{index}\n",
                f"{step.at_micros}\n",
                _length_prefixed(step.action),
            )
        )
    return "".join(parts).encode("utf-8")


def load_trace_contract(repository_root: Path, scenario_name: str) -> ScenarioTraceContract:
    root = repository_root.expanduser().resolve()
    encounter_source = _stable_text(
        root
        / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterDirector.kt"
    )
    input_source = _stable_text(
        root
        / "app/src/main/java/com/anurag9000/forestrun/engine/DebugScenarioScript.kt"
    )
    scenario = parse_scenario_definition(encounter_source, scenario_name)
    input_steps = parse_input_steps(input_source, scenario_name)
    scenario_sha = hashlib.sha256(scenario_canonical_bytes(scenario)).hexdigest()
    trace_sha = hashlib.sha256(
        trace_contract_canonical_bytes(scenario, input_steps)
    ).hexdigest()
    return ScenarioTraceContract(
        scenario=scenario,
        input_steps=input_steps,
        scenario_definition_sha256=scenario_sha,
        trace_contract_sha256=trace_sha,
    )
