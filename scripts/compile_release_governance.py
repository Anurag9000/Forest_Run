#!/usr/bin/env python3
"""Compile human-entered release governance into a hashed final manifest.

Draft rules:
- device_acceptance and human_acceptance are relative path strings;
- every evidence[kind] value is a relative path string;
- legal/store/security/presentation decisions are preserved exactly as entered.

Only hashes, schema version, and an optional generated timestamp are derived.
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
import validate_release_governance as governance


class GovernanceCompilationError(ValueError):
    """Raised when a governance draft cannot be safely compiled."""


def _read_object(path: Path) -> dict[str, Any]:
    try:
        value = strict_json.load_file(
            path,
            maximum_bytes=governance.MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise GovernanceCompilationError(str(exc)) from exc
    return dict(value)


def _mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise GovernanceCompilationError(f"{label} must be an object")
    return value


def _resolve(base: Path, raw: Any, label: str) -> tuple[str, Path]:
    if not isinstance(raw, str):
        raise GovernanceCompilationError(f"{label} must be a relative path string in a draft")
    try:
        relative = governance._safe_relative_path(raw, label)
        path = governance._resolve_inside(base, relative, label)
    except governance.GovernanceError as exc:
        raise GovernanceCompilationError(str(exc)) from exc
    return relative, path


def _hash(path: Path, label: str, maximum: int) -> str:
    try:
        digest, _ = governance._hash_regular_file(path, label, maximum)
    except governance.GovernanceError as exc:
        raise GovernanceCompilationError(str(exc)) from exc
    return digest


def _generated_at(value: Any) -> str:
    if value is None:
        return datetime.now(timezone.utc).isoformat(timespec="seconds").replace(
            "+00:00", "Z"
        )
    try:
        governance._parse_utc(value, "generated_at_utc")
    except governance.GovernanceError as exc:
        raise GovernanceCompilationError(str(exc)) from exc
    return str(value)


def compile_bundle(
    draft: Mapping[str, Any],
    *,
    base_dir: Path,
    generated_at_utc: str | None = None,
) -> tuple[dict[str, Any], governance.GovernanceSummary]:
    compiled = copy.deepcopy(dict(draft))
    compiled["schema_version"] = governance.SCHEMA_VERSION
    compiled["generated_at_utc"] = _generated_at(
        generated_at_utc if generated_at_utc is not None else compiled.get("generated_at_utc")
    )

    for key, label in (
        ("device_acceptance", "device_acceptance"),
        ("human_acceptance", "human_acceptance"),
    ):
        relative, path = _resolve(base_dir, compiled.get(key), label)
        compiled[key] = {
            "path": relative,
            "sha256": _hash(
                path,
                f"{label} manifest",
                governance.MAX_MANIFEST_BYTES,
            ),
        }

    evidence = _mapping(compiled.get("evidence"), "evidence")
    missing = sorted(governance.REQUIRED_EVIDENCE_KINDS - set(evidence))
    extras = sorted(set(evidence) - governance.REQUIRED_EVIDENCE_KINDS)
    if missing:
        raise GovernanceCompilationError("evidence is missing: " + ", ".join(missing))
    if extras:
        raise GovernanceCompilationError("evidence contains unrecognized kinds: " + ", ".join(extras))
    for kind in sorted(governance.REQUIRED_EVIDENCE_KINDS):
        relative, path = _resolve(base_dir, evidence[kind], f"evidence.{kind}")
        evidence[kind] = {
            "path": relative,
            "sha256": _hash(
                path,
                f"governance evidence {kind}",
                governance.MAX_EVIDENCE_FILE_BYTES,
            ),
        }

    raw = (json.dumps(compiled, indent=2, sort_keys=True, allow_nan=False) + "\n").encode(
        "utf-8"
    )
    try:
        summary = governance.validate_bundle(
            compiled,
            source_bytes=raw,
            evidence_base=base_dir,
        )
    except governance.GovernanceError as exc:
        raise GovernanceCompilationError(str(exc)) from exc
    return compiled, summary


def _json_bytes(value: Mapping[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n").encode(
        "utf-8"
    )


def _stage(path: Path, value: Mapping[str, Any]) -> None:
    try:
        with path.open("xb") as handle:
            handle.write(_json_bytes(value))
            handle.flush()
            os.fsync(handle.fileno())
    except OSError as exc:
        raise GovernanceCompilationError(f"could not stage {path.name}: {exc}") from exc


def _publish_transaction(outputs: Sequence[tuple[Path, Mapping[str, Any]]]) -> None:
    if not outputs:
        raise GovernanceCompilationError("at least one output is required")
    destinations = [path.resolve() for path, _ in outputs]
    if len(destinations) != len(set(destinations)):
        raise GovernanceCompilationError("output paths must be distinct")
    parent = destinations[0].parent
    if any(path.parent != parent for path in destinations):
        raise GovernanceCompilationError("transaction outputs must share one directory")
    parent.mkdir(parents=True, exist_ok=True)
    transaction = Path(tempfile.mkdtemp(prefix=".release-governance-", dir=parent))
    staged: list[Path] = []
    backups: list[tuple[Path, Path]] = []
    published: list[Path] = []
    try:
        for index, (_, value) in enumerate(outputs):
            stage = transaction / f"staged-{index}.json"
            _stage(stage, value)
            staged.append(stage)
        for index, destination in enumerate(destinations):
            if destination.exists():
                backup = transaction / f"backup-{index}.json"
                os.replace(destination, backup)
                backups.append((destination, backup))
        for stage, destination in zip(staged, destinations):
            os.replace(stage, destination)
            published.append(destination)
    except (OSError, GovernanceCompilationError) as exc:
        rollback: list[str] = []
        for destination in reversed(published):
            try:
                destination.unlink(missing_ok=True)
            except OSError as rollback_exc:
                rollback.append(str(rollback_exc))
        for destination, backup in reversed(backups):
            try:
                if backup.exists():
                    os.replace(backup, destination)
            except OSError as rollback_exc:
                rollback.append(str(rollback_exc))
        detail = f"could not publish governance outputs: {exc}"
        if rollback:
            detail += "; rollback errors: " + "; ".join(rollback)
        raise GovernanceCompilationError(detail) from exc
    finally:
        shutil.rmtree(transaction, ignore_errors=True)


def compile_file(
    draft_path: Path,
    output_path: Path,
    *,
    summary_path: Path | None = None,
    generated_at_utc: str | None = None,
) -> governance.GovernanceSummary:
    draft = draft_path.resolve()
    output = output_path.resolve()
    base = draft.parent
    if output.parent != base or output == draft:
        raise GovernanceCompilationError(
            "output must share the draft directory and must not overwrite the draft"
        )
    summary: Path | None = None
    if summary_path is not None:
        summary = summary_path.resolve()
        if summary.parent != base or summary in {draft, output}:
            raise GovernanceCompilationError(
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
    except (OSError, GovernanceCompilationError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary.to_json(), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
