#!/usr/bin/env python3
"""Compile human-entered Play internal-delivery evidence into a hashed manifest.

The draft names the installed-identity matrix and five external evidence files by
relative path. Candidate identity and file digests are derived; track/release/timing/
review assertions remain exactly human-entered and are never manufactured.
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

import strict_json
import validate_installed_identity_matrix as installed_matrix
import validate_play_delivery_evidence as delivery


class PlayDeliveryCompilationError(ValueError):
    """Raised when a Play delivery draft cannot be safely compiled."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        value = strict_json.load_file(path, maximum_bytes=delivery.MAX_MANIFEST_BYTES, require_object=True)
    except strict_json.StrictJsonError as exc:
        raise PlayDeliveryCompilationError(str(exc)) from exc
    return dict(value)


def _resolve(base: Path, raw: Any, label: str) -> tuple[str, Path]:
    if not isinstance(raw, str):
        raise PlayDeliveryCompilationError(f"{label} must be a relative path string in a draft")
    try:
        relative = delivery._safe_relative_path(raw, label)
        path = delivery._resolve_inside(base, relative, label)
    except delivery.PlayDeliveryError as exc:
        raise PlayDeliveryCompilationError(str(exc)) from exc
    return relative, path


def _hash(path: Path, label: str, maximum: int) -> str:
    try:
        digest, _ = delivery._hash_regular_file(path, label, maximum)
    except delivery.PlayDeliveryError as exc:
        raise PlayDeliveryCompilationError(str(exc)) from exc
    return digest


def _generated_at(raw: Any) -> str:
    if raw is None:
        return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    try:
        delivery._parse_utc(raw, "generated_at_utc")
    except delivery.PlayDeliveryError as exc:
        raise PlayDeliveryCompilationError(str(exc)) from exc
    return str(raw)


def compile_bundle(
    draft: Mapping[str, Any],
    *,
    base_dir: Path,
    generated_at_utc: str | None = None,
) -> tuple[dict[str, Any], delivery.PlayDeliverySummary]:
    compiled = copy.deepcopy(dict(draft))
    compiled["schema_version"] = delivery.SCHEMA_VERSION
    compiled["generated_at_utc"] = _generated_at(
        generated_at_utc if generated_at_utc is not None else compiled.get("generated_at_utc")
    )

    matrix_relative, matrix_path = _resolve(
        base_dir,
        compiled.get("installed_identity_matrix"),
        "installed_identity_matrix",
    )
    try:
        matrix_summary = installed_matrix.load_and_validate(matrix_path)
    except installed_matrix.InstalledIdentityMatrixError as exc:
        raise PlayDeliveryCompilationError(
            f"referenced installed identity matrix is invalid: {exc}"
        ) from exc
    matrix_digest = _hash(
        matrix_path,
        "installed identity matrix",
        installed_matrix.MAX_MANIFEST_BYTES,
    )
    compiled["candidate"] = {
        "repository": "Anurag9000/Forest_Run",
        "branch": "main",
        "application_id": "com.anurag9000.forestrun",
        "commit_sha": matrix_summary.candidate_sha,
        "version_code": matrix_summary.version_code,
        "artifact_sha256": matrix_summary.artifact_sha256,
        "upload_certificate_sha256": matrix_summary.upload_certificate_sha256,
        "app_signing_certificate_sha256": matrix_summary.app_signing_certificate_sha256,
    }
    compiled["installed_identity_matrix"] = {
        "path": matrix_relative,
        "sha256": matrix_digest,
    }

    evidence = compiled.get("evidence")
    if not isinstance(evidence, dict):
        raise PlayDeliveryCompilationError("evidence must be an object")
    missing = sorted(delivery.REQUIRED_EVIDENCE_KINDS - set(evidence))
    extras = sorted(set(evidence) - delivery.REQUIRED_EVIDENCE_KINDS)
    if missing:
        raise PlayDeliveryCompilationError("evidence is missing: " + ", ".join(missing))
    if extras:
        raise PlayDeliveryCompilationError("evidence contains unrecognized kinds: " + ", ".join(extras))
    for kind in sorted(delivery.REQUIRED_EVIDENCE_KINDS):
        relative, path = _resolve(base_dir, evidence[kind], f"evidence.{kind}")
        evidence[kind] = {
            "path": relative,
            "sha256": _hash(path, f"Play delivery evidence {kind}", delivery.MAX_EVIDENCE_FILE_BYTES),
        }

    raw = (json.dumps(compiled, indent=2, sort_keys=True, allow_nan=False) + "\n").encode("utf-8")
    try:
        summary = delivery.validate_bundle(compiled, source_bytes=raw, evidence_base=base_dir)
    except delivery.PlayDeliveryError as exc:
        raise PlayDeliveryCompilationError(str(exc)) from exc
    return compiled, summary


def _json_bytes(payload: Mapping[str, Any]) -> bytes:
    return (json.dumps(payload, indent=2, sort_keys=True, allow_nan=False) + "\n").encode("utf-8")


def _publish(outputs: Sequence[tuple[Path, Mapping[str, Any]]]) -> None:
    if not outputs:
        raise PlayDeliveryCompilationError("at least one output is required")
    destinations = [path.resolve() for path, _ in outputs]
    if len(destinations) != len(set(destinations)):
        raise PlayDeliveryCompilationError("output paths must be distinct")
    parent = destinations[0].parent
    if any(path.parent != parent for path in destinations):
        raise PlayDeliveryCompilationError("outputs must share one directory")
    parent.mkdir(parents=True, exist_ok=True)
    transaction = Path(tempfile.mkdtemp(prefix=".play-delivery-", dir=parent))
    backups: list[tuple[Path, Path]] = []
    published: list[Path] = []
    try:
        staged: list[Path] = []
        for index, (_, payload) in enumerate(outputs):
            stage = transaction / f"stage-{index}.json"
            with stage.open("xb") as handle:
                handle.write(_json_bytes(payload))
                handle.flush()
                os.fsync(handle.fileno())
            staged.append(stage)
        for index, destination in enumerate(destinations):
            if destination.exists():
                backup = transaction / f"backup-{index}.json"
                os.replace(destination, backup)
                backups.append((destination, backup))
        for stage, destination in zip(staged, destinations):
            os.replace(stage, destination)
            published.append(destination)
    except OSError as exc:
        for destination in reversed(published):
            destination.unlink(missing_ok=True)
        for destination, backup in reversed(backups):
            if backup.exists():
                os.replace(backup, destination)
        raise PlayDeliveryCompilationError(f"could not publish Play delivery outputs: {exc}") from exc
    finally:
        shutil.rmtree(transaction, ignore_errors=True)


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> delivery.PlayDeliverySummary:
    draft = draft_path.resolve()
    output = output_path.resolve()
    if output.parent != draft.parent or output == draft:
        raise PlayDeliveryCompilationError(
            "output must share the draft directory and must not overwrite the draft"
        )
    summary: Path | None = None
    if summary_path is not None:
        summary = summary_path.resolve()
        if summary.parent != draft.parent or summary in {draft, output}:
            raise PlayDeliveryCompilationError(
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
    except (OSError, PlayDeliveryCompilationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
