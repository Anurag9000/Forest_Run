#!/usr/bin/env python3
"""Validate every deterministic scenario trace referenced by an acceptance manifest."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Sequence

import strict_json
import validate_device_acceptance as acceptance
import validate_scenario_trace as trace

TRACE_PREFIX = "scenario-trace-"
TRACE_SUFFIX = ".json"


class ManifestTraceError(ValueError):
    """Raised when a referenced deterministic trace is invalid or unsafe."""


def _is_trace_path(relative_path: str) -> bool:
    name = Path(relative_path).name
    return name.startswith(TRACE_PREFIX) and name.endswith(TRACE_SUFFIX)


def validate_manifest_traces(
    manifest_path: Path,
    *,
    repository_root: Path,
    require_at_least_one: bool = False,
) -> dict[str, Any]:
    resolved_manifest = manifest_path.expanduser().resolve()
    try:
        raw = resolved_manifest.read_bytes()
        data = strict_json.loads(
            raw,
            label=str(resolved_manifest),
            maximum_bytes=acceptance.MAX_MANIFEST_BYTES,
            maximum_depth=64,
            require_object=True,
        )
        manifest_summary = acceptance.validate_bundle(
            data,
            source_bytes=raw,
            evidence_base=resolved_manifest.parent,
        )
    except (OSError, strict_json.StrictJsonError, acceptance.EvidenceError) as exc:
        raise ManifestTraceError(f"invalid acceptance manifest: {exc}") from exc

    candidate = data["candidate"]
    validated: list[dict[str, Any]] = []
    for session in data["sessions"]:
        for acceptance_scenario, result in session["scenarios"].items():
            for evidence in result["evidence_files"]:
                relative_path = evidence["path"]
                if not _is_trace_path(relative_path):
                    continue
                try:
                    safe_relative = acceptance._safe_evidence_path(
                        relative_path,
                        "scenario trace evidence path",
                    )
                    trace_path = (resolved_manifest.parent / safe_relative).resolve()
                    trace_path.relative_to(resolved_manifest.parent.resolve())
                    summary = trace.load_and_validate(
                        trace_path,
                        repository_root=repository_root,
                        expected_commit_sha=candidate["commit_sha"],
                        expected_artifact_sha256=candidate["artifact_sha256"],
                    )
                except (ValueError, acceptance.EvidenceError, trace.ScenarioTraceError) as exc:
                    raise ManifestTraceError(
                        f"invalid scenario trace {relative_path}: {exc}"
                    ) from exc
                validated.append(
                    {
                        "path": safe_relative,
                        "acceptance_scenario": acceptance_scenario,
                        "trace_scenario": summary["scenario"],
                        "scenario_definition_sha256": summary[
                            "scenario_definition_sha256"
                        ],
                        "trace_contract_sha256": summary["trace_contract_sha256"],
                        "event_count": summary["event_count"],
                        "maximum_lateness_micros": summary[
                            "maximum_lateness_micros"
                        ],
                        "payload_sha256": summary["payload_sha256"],
                    }
                )

    if require_at_least_one and not validated:
        raise ManifestTraceError("acceptance manifest references no deterministic scenario trace")

    return {
        "status": "valid",
        "candidate_commit_sha": manifest_summary.candidate_sha,
        "artifact_sha256": manifest_summary.artifact_sha256,
        "trace_count": len(validated),
        "traces": sorted(validated, key=lambda item: item["path"]),
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--require-at-least-one", action="store_true")
    args = parser.parse_args(argv)

    try:
        summary = validate_manifest_traces(
            args.manifest,
            repository_root=args.root.expanduser().resolve(),
            require_at_least_one=args.require_at_least_one,
        )
    except (OSError, ManifestTraceError) as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    print(json.dumps(summary, sort_keys=True, allow_nan=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
