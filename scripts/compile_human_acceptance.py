#!/usr/bin/env python3
"""Compile a human acceptance draft into a hashed, validated final manifest.

Draft rules:
- device_acceptance is a relative path string;
- each review.evidence_files entry is a relative path string;
- candidate/reviewer/check facts are preserved exactly as entered.

The compiler derives only file digests and schema/timestamp metadata, validates the
result against validate_human_acceptance.py, then publishes manifest and optional
summary transactionally in the draft directory.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json
from stable_file_reader import (
    StableFileError,
    absolute_lexical_path,
    atomic_publish_files,
)
import validate_human_acceptance as human


class HumanCompilationError(ValueError):
    """Raised when a human acceptance draft cannot be safely compiled."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        value = strict_json.load_file(
            path,
            maximum_bytes=human.MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise HumanCompilationError(str(exc)) from exc
    return dict(value)


def _mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise HumanCompilationError(f"{label} must be an object")
    return value


def _sequence(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise HumanCompilationError(f"{label} must be an array")
    return value


def _resolve(base: Path, value: Any, label: str) -> tuple[str, Path]:
    try:
        relative = human._safe_relative_path(value, label)
        path = human._resolve_inside(base, relative, label)
    except human.HumanAcceptanceError as exc:
        raise HumanCompilationError(str(exc)) from exc
    return relative, path


def _hash(path: Path, label: str, maximum_bytes: int) -> str:
    try:
        digest, _ = human._hash_regular_file(path, label, maximum_bytes)
    except human.HumanAcceptanceError as exc:
        raise HumanCompilationError(str(exc)) from exc
    return digest


def _timestamp(value: Any) -> str:
    if value is None:
        return datetime.now(timezone.utc).isoformat(timespec="seconds").replace(
            "+00:00", "Z"
        )
    try:
        human._parse_utc(value, "generated_at_utc")
    except human.HumanAcceptanceError as exc:
        raise HumanCompilationError(str(exc)) from exc
    return str(value)


def compile_bundle(
    draft: Mapping[str, Any],
    *,
    base_dir: Path,
    generated_at_utc: str | None = None,
) -> tuple[dict[str, Any], human.HumanAcceptanceSummary]:
    if not isinstance(draft, Mapping):
        raise HumanCompilationError("draft must be an object")
    try:
        compiled = copy.deepcopy(dict(draft))
    except (TypeError, ValueError, RecursionError) as exc:
        raise HumanCompilationError(f"draft cannot be copied safely: {exc}") from exc
    compiled["schema_version"] = human.SCHEMA_VERSION
    compiled["generated_at_utc"] = _timestamp(
        generated_at_utc if generated_at_utc is not None else compiled.get("generated_at_utc")
    )

    raw_device = compiled.get("device_acceptance")
    if not isinstance(raw_device, str):
        raise HumanCompilationError(
            "device_acceptance must be a relative path string in a draft"
        )
    device_relative, device_path = _resolve(
        base_dir,
        raw_device,
        "device_acceptance",
    )
    compiled["device_acceptance"] = {
        "path": device_relative,
        "sha256": _hash(
            device_path,
            "device acceptance manifest",
            human.MAX_MANIFEST_BYTES,
        ),
    }

    reviews = _sequence(compiled.get("reviews"), "reviews")
    if not reviews:
        raise HumanCompilationError("reviews must not be empty")
    for review_index, raw_review in enumerate(reviews):
        review = _mapping(raw_review, f"reviews[{review_index}]")
        files = _sequence(
            review.get("evidence_files"),
            f"reviews[{review_index}].evidence_files",
        )
        if not files:
            raise HumanCompilationError(
                f"reviews[{review_index}].evidence_files must not be empty"
            )
        hashed: list[dict[str, str]] = []
        for file_index, raw_path in enumerate(files):
            label = f"reviews[{review_index}].evidence_files[{file_index}]"
            if not isinstance(raw_path, str):
                raise HumanCompilationError(
                    f"{label} must be a relative path string in a draft"
                )
            relative, path = _resolve(base_dir, raw_path, label)
            hashed.append(
                {
                    "path": relative,
                    "sha256": _hash(
                        path,
                        "human acceptance evidence",
                        human.MAX_EVIDENCE_FILE_BYTES,
                    ),
                }
            )
        review["evidence_files"] = hashed

    try:
        raw = (
            json.dumps(compiled, indent=2, sort_keys=True, allow_nan=False) + "\n"
        ).encode("utf-8")
    except (TypeError, ValueError, RecursionError) as exc:
        raise HumanCompilationError(f"draft contains values that are not valid JSON: {exc}") from exc
    try:
        summary = human.validate_bundle(
            compiled,
            source_bytes=raw,
            evidence_base=base_dir,
        )
    except human.HumanAcceptanceError as exc:
        raise HumanCompilationError(str(exc)) from exc
    return compiled, summary


def _json_bytes(value: Mapping[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n").encode(
        "utf-8"
    )


def _publish_transaction(outputs: Sequence[tuple[Path, Mapping[str, Any]]]) -> None:
    try:
        atomic_publish_files([(path, _json_bytes(value)) for path, value in outputs], "human acceptance outputs")
    except StableFileError as exc:
        raise HumanCompilationError(f"could not publish human acceptance outputs: {exc}") from exc


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> human.HumanAcceptanceSummary:
    if output_path.is_symlink() or (summary_path is not None and summary_path.is_symlink()):
        raise HumanCompilationError("output paths must not be symlinks")
    draft = absolute_lexical_path(draft_path)
    output = absolute_lexical_path(output_path)
    base = draft.parent
    if output.parent != base or output == draft:
        raise HumanCompilationError(
            "output must share the draft directory and must not overwrite the draft"
        )
    summary: Path | None = None
    if summary_path is not None:
        summary = absolute_lexical_path(summary_path)
        if summary.parent != base or summary in {draft, output}:
            raise HumanCompilationError(
                "summary must share the draft directory and be distinct from draft/output"
            )

    compiled, result = compile_bundle(
        _read_object(draft),
        base_dir=base,
        generated_at_utc=generated_at_utc,
    )
    outputs: list[tuple[Path, Mapping[str, Any]]] = [(output, compiled)]
    if summary is not None:
        outputs.append((summary, result.to_json()))
    _publish_transaction(outputs)
    return result


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("draft", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--summary-output", type=Path)
    parser.add_argument("--generated-at-utc")
    args = parser.parse_args(argv)
    try:
        summary = compile_file(
            args.draft,
            args.output,
            summary_path=args.summary_output,
            generated_at_utc=args.generated_at_utc,
        )
    except (OSError, HumanCompilationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
