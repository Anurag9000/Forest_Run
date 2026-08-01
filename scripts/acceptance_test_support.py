from __future__ import annotations

import hashlib
import json
from pathlib import Path

import scenario_source_contract as source_contract
from test_validate_device_acceptance import materialize_files


ROOT = Path(__file__).resolve().parents[1]


def materialize_traced_bundle(
    root: Path,
    bundle: dict,
    *,
    scenario_name: str = "CACTUS_READ",
    session_index: int = 0,
    acceptance_scenario: str = "ordinary_play_15m",
    dispatch_lateness_micros: int = 20_000,
) -> Path:
    """Materialize a valid acceptance bundle with one exact schema-v2 trace."""
    if dispatch_lateness_micros < 0:
        raise ValueError("dispatch_lateness_micros must be non-negative")
    authored = source_contract.load_trace_contract(ROOT, scenario_name)
    if not authored.input_steps:
        raise ValueError(f"scenario has no deterministic input script: {scenario_name}")

    candidate = bundle["candidate"]
    events = [
        {
            "sequence": index,
            "scheduled_at_micros": step.at_micros,
            "dispatched_at_micros": step.at_micros + dispatch_lateness_micros,
            "lateness_micros": dispatch_lateness_micros,
            "action": step.action,
        }
        for index, step in enumerate(authored.input_steps)
    ]
    payload = {
        "schema_version": 2,
        "candidate_commit_sha": candidate["commit_sha"],
        "artifact_sha256": candidate["artifact_sha256"],
        "captured_at_utc_ms": 1_775_000_000_000,
        "scenario": scenario_name,
        "scenario_definition_sha256": authored.scenario_definition_sha256,
        "trace_contract_sha256": authored.trace_contract_sha256,
        "event_count": len(events),
        "events": events,
    }
    raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode("utf-8")

    entry = bundle["sessions"][session_index]["scenarios"][acceptance_scenario][
        "evidence_files"
    ][0]
    entry["path"] = (
        f"evidence/{bundle['sessions'][session_index]['device']['class']}/"
        f"scenario-trace-{scenario_name.lower()}.json"
    )
    entry["sha256"] = hashlib.sha256(raw).hexdigest()

    materialize_files(root, bundle)
    trace_path = root / entry["path"]
    trace_path.write_bytes(raw)
    return trace_path
