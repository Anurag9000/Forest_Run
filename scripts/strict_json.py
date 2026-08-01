#!/usr/bin/env python3
"""Strict, bounded JSON parsing for release and physical evidence files."""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

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
        encoded = raw.encode("utf-8")
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
    path = path.expanduser().resolve()
    try:
        before = path.stat()
    except FileNotFoundError as exc:
        raise StrictJsonError(f"JSON file is missing: {path}") from exc
    except OSError as exc:
        raise StrictJsonError(f"could not inspect JSON file {path}: {exc}") from exc
    if not path.is_file():
        raise StrictJsonError(f"JSON path is not a regular file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise StrictJsonError(
            f"{path} must be between 1 and {maximum_bytes} bytes"
        )
    try:
        raw = path.read_bytes()
        after = path.stat()
    except OSError as exc:
        raise StrictJsonError(f"could not read JSON file {path}: {exc}") from exc
    if (
        len(raw) != before.st_size
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise StrictJsonError(f"JSON file changed while being read: {path}")
    return loads(
        raw,
        label=str(path),
        maximum_bytes=maximum_bytes,
        maximum_depth=maximum_depth,
        maximum_integer_digits=maximum_integer_digits,
        require_object=require_object,
    )
