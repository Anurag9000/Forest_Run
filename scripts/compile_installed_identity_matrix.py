#!/usr/bin/env python3
"""Compile one installed identity record per physical acceptance session."""

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
import validate_device_acceptance as device_acceptance
import validate_installed_identity_matrix as matrix


class InstalledIdentityMatrixCompilationError(ValueError):
    """Raised when the matrix draft cannot be safely compiled."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        value = strict_json.load_file(path, maximum_bytes=matrix.MAX_MANIFEST_BYTES, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise InstalledIdentityMatrixCompilationError(str(exc)) from exc
    return dict(value)


def _resolve(base: Path, raw: Any, label: str) -> tuple[str, Path]:
    if not isinstance(raw, str):
        raise InstalledIdentityMatrixCompilationError(f"{label} must be a relative path string in a draft")
    try:
        relative = matrix._safe_relative(raw, label)
        path = matrix._resolve_inside(base, relative, label)
    except matrix.InstalledIdentityMatrixError as exc:
        raise InstalledIdentityMatrixCompilationError(str(exc)) from exc
    return relative, path


def _hash(path: Path, label: str, maximum: int) -> str:
    try:
        digest, _ = matrix._hash_regular(path, label, maximum)
    except matrix.InstalledIdentityMatrixError as exc:
        raise InstalledIdentityMatrixCompilationError(str(exc)) from exc
    return digest


def _generated_at(raw: Any) -> str:
    if raw is None:
        return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    try:
        matrix._parse_utc(raw, "generated_at_utc")
    except matrix.InstalledIdentityMatrixError as exc:
        raise InstalledIdentityMatrixCompilationError(str(exc)) from exc
    return str(raw)


def compile_bundle(
    draft: Mapping[str, Any],
    *,
    base_dir: Path,
    generated_at_utc: str | None = None,
) -> tuple[dict[str, Any], matrix.InstalledIdentityMatrixSummary]:
    if not isinstance(draft, Mapping):
        raise InstalledIdentityMatrixCompilationError("draft must be an object")
    try:
        compiled = copy.deepcopy(dict(draft))
    except (TypeError, ValueError, RecursionError) as exc:
        raise InstalledIdentityMatrixCompilationError(
            f"draft cannot be copied safely: {exc}"
        ) from exc
    compiled["schema_version"] = matrix.SCHEMA_VERSION
    compiled["generated_at_utc"] = _generated_at(
        generated_at_utc if generated_at_utc is not None else compiled.get("generated_at_utc")
    )

    raw_device = compiled.get("device_acceptance")
    device_relative, device_path = _resolve(base_dir, raw_device, "device_acceptance")
    try:
        device_bytes, _ = device_acceptance._read_bounded_file(
            device_path,
            "acceptance manifest",
            device_acceptance.MAX_MANIFEST_BYTES,
        )
        device_data = strict_json.loads(
            device_bytes,
            label=str(device_path),
            maximum_bytes=device_acceptance.MAX_MANIFEST_BYTES,
            require_object=True,
        )
        device_summary = device_acceptance.validate_bundle(
            device_data,
            source_bytes=device_bytes,
            evidence_base=device_path.parent,
        )
    except (device_acceptance.EvidenceError, strict_json.StrictJsonError) as exc:
        raise InstalledIdentityMatrixCompilationError(
            f"referenced device acceptance is invalid: {exc}"
        ) from exc
    device_digest = hashlib.sha256(device_bytes).hexdigest()
    candidate = device_data["candidate"]
    compiled["candidate"] = {
        "repository": candidate["repository"],
        "branch": candidate["branch"],
        "commit_sha": device_summary.candidate_sha,
        "application_id": candidate["application_id"],
        "version_code": candidate["version_code"],
        "artifact_sha256": device_summary.artifact_sha256,
        "upload_certificate_sha256": device_summary.upload_certificate_sha256,
        "app_signing_certificate_sha256": device_summary.app_signing_certificate_sha256,
    }
    compiled["device_acceptance"] = {
        "path": device_relative,
        "sha256": device_digest,
    }

    raw_records = compiled.get("records")
    if not isinstance(raw_records, list) or not raw_records:
        raise InstalledIdentityMatrixCompilationError("records must be a non-empty array")
    hashed_records: list[dict[str, str]] = []
    for index, raw_record in enumerate(raw_records):
        if not isinstance(raw_record, dict):
            raise InstalledIdentityMatrixCompilationError(f"records[{index}] must be an object")
        if set(raw_record) != {"session_id", "path"}:
            raise InstalledIdentityMatrixCompilationError(
                f"records[{index}] draft must contain exactly session_id and path"
            )
        session_id = raw_record["session_id"]
        if not isinstance(session_id, str) or not session_id.strip():
            raise InstalledIdentityMatrixCompilationError(f"records[{index}].session_id must be nonblank")
        relative, path = _resolve(base_dir, raw_record["path"], f"records[{index}].path")
        hashed_records.append(
            {
                "session_id": session_id.strip(),
                "path": relative,
                "sha256": _hash(
                    path,
                    "installed identity record",
                    matrix.installed_identity.MAX_MANIFEST_BYTES,
                ),
            }
        )
    compiled["records"] = hashed_records

    try:
        raw = (
            json.dumps(compiled, indent=2, sort_keys=True, allow_nan=False) + "\n"
        ).encode("utf-8")
    except (TypeError, ValueError, RecursionError) as exc:
        raise InstalledIdentityMatrixCompilationError(
            f"draft contains values that are not valid JSON: {exc}"
        ) from exc
    try:
        summary = matrix.validate_bundle(compiled, source_bytes=raw, evidence_base=base_dir)
    except matrix.InstalledIdentityMatrixError as exc:
        raise InstalledIdentityMatrixCompilationError(str(exc)) from exc
    return compiled, summary


def _json_bytes(payload: Mapping[str, Any]) -> bytes:
    return (json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + "\n").encode("utf-8")


def _publish(outputs: Sequence[tuple[Path, Mapping[str, Any]]]) -> None:
    try:
        atomic_publish_files([(path, _json_bytes(payload)) for path, payload in outputs], "installed identity matrix outputs")
    except StableFileError as exc:
        raise InstalledIdentityMatrixCompilationError(f"could not publish matrix outputs: {exc}") from exc


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> matrix.InstalledIdentityMatrixSummary:
    if output_path.is_symlink() or (summary_path is not None and summary_path.is_symlink()):
        raise InstalledIdentityMatrixCompilationError("output paths must not be symlinks")
    draft = absolute_lexical_path(draft_path)
    output = absolute_lexical_path(output_path)
    if output.parent != draft.parent or output == draft:
        raise InstalledIdentityMatrixCompilationError(
            "output must share the draft directory and must not overwrite the draft"
        )
    summary: Path | None = None
    if summary_path is not None:
        summary = absolute_lexical_path(summary_path)
        if summary.parent != draft.parent or summary in {draft, output}:
            raise InstalledIdentityMatrixCompilationError(
                "summary must share the draft directory and be distinct from draft/output"
            )
    compiled, result = compile_bundle(
        _read_object(draft),
        base_dir=draft.parent,
        generated_at_utc=generated_at_utc,
    )
    outputs: list[tuple[Path, Mapping[str, Any]]] = [(output, compiled)]
    if summary is not None:
        outputs.append((summary, result.to_json()))
    _publish(outputs)
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
    except (OSError, InstalledIdentityMatrixCompilationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
