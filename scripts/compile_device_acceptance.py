#!/usr/bin/env python3
"""Compile and validate a Forest Run physical-device acceptance bundle.

The draft contains tester-entered device, scenario, performance, manual-check,
and approval facts. Evidence references are plain relative paths. This compiler
hashes the signed candidate and every evidence file, binds every session to the
same candidate identity, invokes the fail-closed validator, and atomically
publishes the final manifest and optional validation summary.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
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
    """Return a fully hashed, candidate-bound, already validated manifest."""

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
    artifact_sha = _hash_file(artifact_path, "candidate artifact")
    candidate["artifact_path"] = artifact_relative
    candidate["artifact_sha256"] = artifact_sha

    repository = candidate.get("repository")
    branch = candidate.get("branch")
    commit_sha = candidate.get("commit_sha")
    application_id = candidate.get("application_id")
    version_code = candidate.get("version_code")
    certificate_sha = candidate.get("certificate_sha256")
    signed = candidate.get("signed")

    store = _required_mapping(candidate.get("store_delivery"), "candidate.store_delivery")
    store["package_name"] = application_id
    store["version_code"] = version_code
    store["artifact_sha256"] = artifact_sha
    store["certificate_sha256"] = certificate_sha

    sessions = _required_list(compiled.get("sessions"), "sessions")
    if not sessions:
        raise CompilationError("sessions must not be empty")
    for session_index, raw_session in enumerate(sessions):
        session = _required_mapping(raw_session, f"sessions[{session_index}]")
        session["build"] = {
            "repository": repository,
            "branch": branch,
            "commit_sha": commit_sha,
            "application_id": application_id,
            "version_code": version_code,
            "artifact_sha256": artifact_sha,
            "certificate_sha256": certificate_sha,
            "signed": signed,
            "installed_via": "internal_store",
        }
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


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    try:
        temporary.write_text(
            json.dumps(value, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)
    except OSError as exc:
        try:
            temporary.unlink(missing_ok=True)
        except OSError:
            pass
        raise CompilationError(f"could not publish {path}: {exc}") from exc


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> acceptance.ValidationSummary:
    draft_parent = draft_path.resolve().parent
    if output_path.resolve().parent != draft_parent:
        raise CompilationError(
            "output manifest must share the draft directory so relative evidence paths remain stable"
        )
    if summary_path is not None and summary_path.resolve().parent != draft_parent:
        raise CompilationError(
            "summary output must share the draft directory"
        )
    draft = _read_object(draft_path)
    compiled, summary = compile_bundle(
        draft,
        base_dir=draft_parent,
        generated_at_utc=generated_at_utc,
    )
    _write_json_atomic(output_path, compiled)
    if summary_path is not None:
        _write_json_atomic(summary_path, summary.to_json())
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
