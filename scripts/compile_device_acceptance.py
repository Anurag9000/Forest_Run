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
import hashlib
import json
import os
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

import validate_device_acceptance as acceptance


class CompilationError(ValueError):
    """Raised when a draft cannot be safely compiled into acceptance evidence."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise CompilationError(f"could not read {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise CompilationError(f"invalid JSON in {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise CompilationError(f"{path} must contain a JSON object")
    return value


def _hash_file(path: Path, label: str) -> str:
    if not path.is_file():
        raise CompilationError(f"{label} is missing: {path}")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise CompilationError(f"could not hash {label} {path}: {exc}") from exc
    return digest.hexdigest()


def _resolve_relative(base: Path, value: Any, label: str) -> tuple[str, Path]:
    try:
        relative = acceptance._safe_evidence_path(value, label)
    except acceptance.EvidenceError as exc:
        raise CompilationError(str(exc)) from exc
    canonical_base = base.resolve()
    resolved = (canonical_base / relative).resolve()
    try:
        resolved.relative_to(canonical_base)
    except ValueError as exc:
        raise CompilationError(f"{label} escapes the draft directory") from exc
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

    compiled = copy.deepcopy(dict(draft))
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
                        "sha256": _hash_file(resolved, "evidence file"),
                    }
                )
            result["evidence_files"] = hashed_files

    canonical_bytes = (
        json.dumps(compiled, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")
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


def _write_staged_json(path: Path, value: Mapping[str, Any]) -> None:
    try:
        with path.open("xb") as handle:
            handle.write(_json_bytes(value))
            handle.flush()
            os.fsync(handle.fileno())
    except OSError as exc:
        raise CompilationError(f"could not stage {path.name}: {exc}") from exc


def _replace_path(source: Path, destination: Path) -> None:
    os.replace(source, destination)


def _publish_json_transaction(
    outputs: Sequence[tuple[Path, Mapping[str, Any]]],
) -> None:
    if not outputs:
        raise CompilationError("at least one output is required")
    destinations = [path.resolve() for path, _ in outputs]
    if len(destinations) != len(set(destinations)):
        raise CompilationError("transaction output paths must be distinct")
    parent = destinations[0].parent
    if any(path.parent != parent for path in destinations):
        raise CompilationError("transaction outputs must share one directory")
    parent.mkdir(parents=True, exist_ok=True)

    transaction_dir = Path(
        tempfile.mkdtemp(prefix=".device-acceptance-", dir=parent)
    )
    staged: list[Path] = []
    backups: list[tuple[Path, Path]] = []
    published: list[Path] = []
    try:
        for index, (_, value) in enumerate(outputs):
            staged_path = transaction_dir / f"staged-{index}.json"
            _write_staged_json(staged_path, value)
            staged.append(staged_path)

        for index, destination in enumerate(destinations):
            if destination.exists():
                backup = transaction_dir / f"backup-{index}.json"
                _replace_path(destination, backup)
                backups.append((destination, backup))

        for staged_path, destination in zip(staged, destinations):
            _replace_path(staged_path, destination)
            published.append(destination)
    except (OSError, CompilationError) as exc:
        rollback_errors: list[str] = []
        for destination in reversed(published):
            try:
                destination.unlink(missing_ok=True)
            except OSError as rollback_exc:
                rollback_errors.append(
                    f"could not remove partial {destination.name}: {rollback_exc}"
                )
        for destination, backup in reversed(backups):
            try:
                if backup.exists():
                    _replace_path(backup, destination)
            except OSError as rollback_exc:
                rollback_errors.append(
                    f"could not restore {destination.name}: {rollback_exc}"
                )
        detail = f"could not publish acceptance outputs: {exc}"
        if rollback_errors:
            detail += "; rollback errors: " + "; ".join(rollback_errors)
        raise CompilationError(detail) from exc
    finally:
        shutil.rmtree(transaction_dir, ignore_errors=True)


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> acceptance.ValidationSummary:
    draft_resolved = draft_path.resolve()
    output_resolved = output_path.resolve()
    draft_parent = draft_resolved.parent
    if output_resolved.parent != draft_parent:
        raise CompilationError(
            "output manifest must share the draft directory so relative evidence paths remain stable"
        )
    if output_resolved == draft_resolved:
        raise CompilationError("output manifest must not overwrite the draft")

    summary_resolved: Path | None = None
    if summary_path is not None:
        summary_resolved = summary_path.resolve()
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
