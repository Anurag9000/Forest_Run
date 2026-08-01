from __future__ import annotations

import json
import unittest
from pathlib import Path

import scenario_source_contract as source_contract
import validate_scenario_trace as trace


ROOT = Path(__file__).resolve().parents[1]


class ScenarioTraceZeroActionTest(unittest.TestCase):
    def test_unscripted_scenario_is_rejected_even_with_matching_hashes(self) -> None:
        authored = source_contract.load_trace_contract(ROOT, "GHOST_READABILITY")
        payload = {
            "schema_version": 2,
            "candidate_commit_sha": "1" * 40,
            "artifact_sha256": "2" * 64,
            "captured_at_utc_ms": 1,
            "scenario": "GHOST_READABILITY",
            "scenario_definition_sha256": authored.scenario_definition_sha256,
            "trace_contract_sha256": authored.trace_contract_sha256,
            "event_count": 0,
            "events": [],
        }
        raw = json.dumps(payload, sort_keys=True).encode()

        with self.assertRaisesRegex(
            trace.ScenarioTraceError,
            "no authored deterministic input script",
        ):
            trace.validate_trace(payload, raw=raw, repository_root=ROOT)


if __name__ == "__main__":
    unittest.main()
