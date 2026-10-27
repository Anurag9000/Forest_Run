#!/usr/bin/env python3
"""Compile and validate a Forest Run physical-device acceptance bundle.

The draft contains tester-entered candidate, store-delivery, session-build,
device, scenario, performance, manual-check, and approval facts. Evidence
references are plain relative paths. This compiler hashes the signed candidate
and every evidence file, preserves captured store/session identity, invokes the
fail-closed validator to reject any mismatch, and transactionally publishes the
final manifest and optional validation summary.
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json
from stable_file_reader import StableFileError, absolute_lexical_path, atomic_publish_files
import validate_device_acceptance as acceptance


class CompilationError(ValueError):
    """Raised when a draft cannot be safely compiled into acceptance evidence."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        raw, _ = acceptance._read_bounded_file(
            path, "acceptance draft", acceptance.MAX_MANIFEST_BYTES
        )
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    try:
        value = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=acceptance.MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise CompilationError(f"invalid JSON in {path}: {exc}") from exc
    return dict(value)


def _hash_file(path: Path, label: str, maximum_bytes: int) -> str:
    try:
        digest, _ = acceptance._hash_bounded_file(
            path,
            label,
            maximum_bytes,
        )
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    return digest


def _resolve_relative(base: Path, value: Any, label: str) -> tuple[str, Path]:
    try:
        relative = acceptance._safe_evidence_path(value, label)
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    try:
        resolved = acceptance._resolve_evidence_file(base, relative, label)
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    return relative, resolved


def _required_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CompilationError(f"{label} must be an object")
    return value


def _required_list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise CompilationError(f"{label} must be an array")
    return value


def _generated_timestamp(value: str | None) -> str:
    if value is None:
        return datetime.now(timezone.utc).isoformat(timespec="seconds").replace(
            "+00:00", "Z"
        )
    try:
        acceptance._parse_utc(value, "generated_at_utc")
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    return value


def compile_bundle(
    draft: Mapping[str, Any],
    *,
    base_dir: Path,
    generated_at_utc: str | None = None,
) -> tuple[dict[str, Any], acceptance.ValidationSummary]:
    """Return a fully hashed, identity-preserving, already validated manifest."""

    if not isinstance(draft, Mapping):
        raise CompilationError("draft must be an object")
    try:
        compiled = copy.deepcopy(dict(draft))
    except (TypeError, ValueError, RecursionError) as exc:
        raise CompilationError(f"draft cannot be copied safely: {exc}") from exc
    compiled["schema_version"] = acceptance.SCHEMA_VERSION
    compiled["generated_at_utc"] = _generated_timestamp(
        generated_at_utc
        if generated_at_utc is not None
        else compiled.get("generated_at_utc")
    )

    candidate = _required_mapping(compiled.get("candidate"), "candidate")
    artifact_relative, artifact_path = _resolve_relative(
        base_dir,
        candidate.get("artifact_path"),
        "candidate.artifact_path",
    )
    candidate["artifact_path"] = artifact_relative
    candidate["artifact_sha256"] = _hash_file(
        artifact_path,
        "candidate artifact",
        acceptance.MAX_ARTIFACT_BYTES,
    )

    # Store-delivery identity is a captured fact. Never derive or overwrite it
    # from the candidate: the validator must reject a wrong package, version,
    # artifact, certificate, track, or installation result.
    _required_mapping(
        candidate.get("store_delivery"),
        "candidate.store_delivery",
    )

    sessions = _required_list(compiled.get("sessions"), "sessions")
    if not sessions:
        raise CompilationError("sessions must not be empty")
    for session_index, raw_session in enumerate(sessions):
        session = _required_mapping(raw_session, f"sessions[{session_index}]")

        # Per-session build identity is also captured evidence. Preserving it
        # allows the validator to detect a stale/local APK or mixed candidate.
        _required_mapping(
            session.get("build"),
            f"sessions[{session_index}].build",
        )

        scenarios = _required_mapping(
            session.get("scenarios"),
            f"sessions[{session_index}].scenarios",
        )
        for scenario_name, raw_result in scenarios.items():
            result = _required_mapping(
                raw_result,
                f"sessions[{session_index}].scenarios.{scenario_name}",
            )
            raw_files = _required_list(
                result.get("evidence_files"),
                f"sessions[{session_index}].scenarios.{scenario_name}.evidence_files",
            )
            hashed_files: list[dict[str, str]] = []
            for file_index, raw_path in enumerate(raw_files):
                label = (
                    f"sessions[{session_index}].scenarios.{scenario_name}"
                    f".evidence_files[{file_index}]"
                )
                if not isinstance(raw_path, str):
                    raise CompilationError(
                        f"{label} must be a relative path string in a draft"
                    )
                relative, resolved = _resolve_relative(base_dir, raw_path, label)
                hashed_files.append(
                    {
                        "path": relative,
                        "sha256": _hash_file(
                            resolved,
                            "evidence file",
                            acceptance.MAX_EVIDENCE_FILE_BYTES,
                        ),
                    }
                )
            result["evidence_files"] = hashed_files

    try:
        canonical_bytes = (
            json.dumps(compiled, indent=2, sort_keys=True, allow_nan=False) + "\n"
        ).encode("utf-8")
    except (TypeError, ValueError, RecursionError) as exc:
        raise CompilationError(
            f"draft contains values that are not valid JSON: {exc}"
        ) from exc
    try:
        summary = acceptance.validate_bundle(
            compiled,
            source_bytes=canonical_bytes,
            evidence_base=base_dir,
        )
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    return compiled, summary


def _json_bytes(value: Mapping[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def _publish_json_transaction(
    outputs: Sequence[tuple[Path, Mapping[str, Any]]],
) -> None:
    try:
        atomic_publish_files([(path, _json_bytes(value)) for path, value in outputs], "device acceptance outputs")
    except StableFileError as exc:
        raise CompilationError(f"could not publish acceptance outputs: {exc}") from exc


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> acceptance.ValidationSummary:
    if output_path.is_symlink() or (summary_path is not None and summary_path.is_symlink()):
        raise CompilationError("output paths must not be symlinks")
    draft_resolved = absolute_lexical_path(draft_path)
    output_resolved = absolute_lexical_path(output_path)
    draft_parent = draft_resolved.parent
    if output_resolved.parent != draft_parent:
        raise CompilationError(
            "output manifest must share the draft directory so relative evidence paths remain stable"
        )
    if output_resolved == draft_resolved:
        raise CompilationError("output manifest must not overwrite the draft")

    summary_resolved: Path | None = None
    if summary_path is not None:
        summary_resolved = absolute_lexical_path(summary_path)
        if summary_resolved.parent != draft_parent:
            raise CompilationError("summary output must share the draft directory")
        if summary_resolved in {draft_resolved, output_resolved}:
            raise CompilationError(
                "summary output must not overwrite the draft or final manifest"
            )

    draft = _read_object(draft_resolved)
    compiled, summary = compile_bundle(
        draft,
        base_dir=draft_parent,
        generated_at_utc=generated_at_utc,
    )
    outputs: list[tuple[Path, Mapping[str, Any]]] = [
        (output_resolved, compiled)
    ]
    if summary_resolved is not None:
        outputs.append((summary_resolved, summary.to_json()))
    _publish_json_transaction(outputs)
    return summary


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
    except (OSError, CompilationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
