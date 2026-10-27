#!/usr/bin/env python3
"""Strict, bounded JSON parsing for release and physical evidence files."""

from __future__ import annotations

import json
import math
import os
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from stable_file_reader import StableFileError, consume_regular_file

DEFAULT_MAX_BYTES = 16 * 1024 * 1024
DEFAULT_MAX_DEPTH = 64
DEFAULT_MAX_INTEGER_DIGITS = 256


class StrictJsonError(ValueError):
    """Raised when JSON is ambiguous, non-standard, oversized, or malformed."""


def _reject_constant(value: str) -> None:
    raise StrictJsonError(f"non-finite JSON number is forbidden: {value}")


def _bounded_integer(value: str, *, maximum_digits: int = DEFAULT_MAX_INTEGER_DIGITS) -> int:
    if maximum_digits <= 0:
        raise ValueError("maximum_digits must be positive")
    digits = value[1:] if value.startswith("-") else value
    if len(digits) > maximum_digits:
        raise StrictJsonError(
            f"JSON integer literal exceeds the {maximum_digits}-digit safety limit"
        )
    return int(value)


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise StrictJsonError(f"duplicate JSON object key: {key!r}")
        result[key] = value
    return result


def _preflight_nesting(text: str, *, maximum_depth: int, label: str) -> None:
    """Reject excessive structural nesting before the recursive JSON parser runs."""
    depth = 0
    in_string = False
    escaped = False
    for character in text:
        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
            continue

        if character == '"':
            in_string = True
        elif character in "[{":
            depth += 1
            if depth > maximum_depth:
                raise StrictJsonError(
                    f"{label} nesting exceeds the {maximum_depth}-level safety limit"
                )
        elif character in "]}":
            depth = max(0, depth - 1)


def _validate_tree(value: Any, *, depth: int, maximum_depth: int) -> None:
    if depth > maximum_depth:
        raise StrictJsonError(
            f"JSON nesting exceeds the {maximum_depth}-level safety limit"
        )
    if isinstance(value, dict):
        for key, child in value.items():
            if not isinstance(key, str):
                raise StrictJsonError("JSON object key is not a string")
            _validate_tree(child, depth=depth + 1, maximum_depth=maximum_depth)
    elif isinstance(value, list):
        for child in value:
            _validate_tree(child, depth=depth + 1, maximum_depth=maximum_depth)
    elif isinstance(value, float):
        if not math.isfinite(value):
            raise StrictJsonError("finite-looking JSON number overflowed to a non-finite value")
    elif value is None or isinstance(value, (str, int, bool)):
        return
    else:
        raise StrictJsonError(f"unsupported JSON value type: {type(value).__name__}")


def _json_values_match(left: Any, right: Any) -> bool:
    """Compare JSON values without Python's bool/int equality ambiguity."""
    if isinstance(left, Mapping) and isinstance(right, Mapping):
        return left.keys() == right.keys() and all(
            _json_values_match(left[key], right[key]) for key in left
        )
    if isinstance(left, list) and isinstance(right, list):
        return len(left) == len(right) and all(
            _json_values_match(first, second) for first, second in zip(left, right)
        )
    return type(left) is type(right) and left == right


def loads(
    raw: bytes | str,
    *,
    label: str = "JSON",
    maximum_bytes: int = DEFAULT_MAX_BYTES,
    maximum_depth: int = DEFAULT_MAX_DEPTH,
    maximum_integer_digits: int = DEFAULT_MAX_INTEGER_DIGITS,
    require_object: bool = False,
) -> Any:
    if maximum_bytes <= 0:
        raise ValueError("maximum_bytes must be positive")
    if maximum_depth <= 0:
        raise ValueError("maximum_depth must be positive")
    if maximum_integer_digits <= 0:
        raise ValueError("maximum_integer_digits must be positive")
    if isinstance(raw, bytes):
        if not raw or len(raw) > maximum_bytes:
            raise StrictJsonError(
                f"{label} must be between 1 and {maximum_bytes} bytes"
            )
        if raw.startswith(b"\xef\xbb\xbf"):
            raise StrictJsonError(f"{label} must not contain a UTF-8 BOM")
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise StrictJsonError(f"{label} is not valid UTF-8: {exc}") from exc
    elif isinstance(raw, str):
        try:
            encoded = raw.encode("utf-8")
        except UnicodeEncodeError as exc:
            raise StrictJsonError(f"{label} is not valid UTF-8 text: {exc}") from exc
        if not encoded or len(encoded) > maximum_bytes:
            raise StrictJsonError(
                f"{label} must be between 1 and {maximum_bytes} bytes"
            )
        text = raw
    else:
        raise TypeError("raw must be bytes or str")

    _preflight_nesting(text, maximum_depth=maximum_depth, label=label)
    try:
        value = json.loads(
            text,
            object_pairs_hook=_unique_object,
            parse_constant=_reject_constant,
            parse_int=lambda literal: _bounded_integer(
                literal,
                maximum_digits=maximum_integer_digits,
            ),
        )
    except StrictJsonError:
        raise
    except (ValueError, RecursionError) as exc:
        raise StrictJsonError(f"invalid {label}: {exc}") from exc
    _validate_tree(value, depth=1, maximum_depth=maximum_depth)
    if require_object and not isinstance(value, dict):
        raise StrictJsonError(f"{label} must contain a JSON object")
    return value


def load_file(
    path: Path,
    *,
    maximum_bytes: int = DEFAULT_MAX_BYTES,
    maximum_depth: int = DEFAULT_MAX_DEPTH,
    maximum_integer_digits: int = DEFAULT_MAX_INTEGER_DIGITS,
    require_object: bool = False,
) -> Any:
    path = path.expanduser()
    try:
        raw, _, _ = consume_regular_file(
            path, "JSON file", maximum_bytes, retain_bytes=True
        )
    except StableFileError as exc:
        raise StrictJsonError(str(exc)) from exc
    assert raw is not None
    return loads(
        raw,
        label=str(path),
        maximum_bytes=maximum_bytes,
        maximum_depth=maximum_depth,
        maximum_integer_digits=maximum_integer_digits,
        require_object=require_object,
    )


def loads_matching_object(
    raw: bytes,
    expected: object,
    *,
    label: str,
    maximum_bytes: int = DEFAULT_MAX_BYTES,
    maximum_depth: int = DEFAULT_MAX_DEPTH,
    maximum_integer_digits: int = DEFAULT_MAX_INTEGER_DIGITS,
) -> dict[str, Any]:
    """Parse bounded object bytes and require them to match the supplied value."""
    if not isinstance(raw, bytes):
        raise StrictJsonError(f"{label} source bytes must be bytes")
    value = loads(
        raw,
        label=label,
        maximum_bytes=maximum_bytes,
        maximum_depth=maximum_depth,
        maximum_integer_digits=maximum_integer_digits,
        require_object=True,
    )
    if not _json_values_match(value, expected):
        raise StrictJsonError(f"{label} source bytes do not encode the supplied object")
    assert isinstance(value, dict)
    return value
