from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import validate_scenario_trace as trace


ROOT = Path(__file__).resolve().parents[1]
COMMIT = "1" * 40
ARTIFACT = "2" * 64


def valid_trace() -> dict:
    return {
        "schema_version": 1,
        "candidate_commit_sha": COMMIT,
        "artifact_sha256": ARTIFACT,
        "captured_at_utc_ms": 1_775_000_000_000,
        "scenario": "CACTUS_READ",
        "event_count": 2,
        "events": [
            {
                "sequence": 0,
                "scheduled_at_micros": 3_180_000,
                "dispatched_at_micros": 3_200_000,
                "lateness_micros": 20_000,
                "action": "HOLD_JUMP_START",
            },
            {
                "sequence": 1,
                "scheduled_at_micros": 3_480_000,
                "dispatched_at_micros": 3_500_000,
                "lateness_micros": 20_000,
                "action": "HOLD_JUMP_END",
            },
        ],
    }


class ValidateScenarioTraceTest(unittest.TestCase):
    def validate(self, payload: dict) -> dict:
        raw = (json.dumps(payload, sort_keys=True) + "\n").encode()
        return trace.validate_trace(payload, raw=raw, repository_root=ROOT)

    def invalid(self, payload: dict, fragment: str) -> None:
        with self.assertRaisesRegex(trace.ScenarioTraceError, fragment):
            self.validate(payload)

    def test_valid_trace_is_candidate_bound_and_summarized(self) -> None:
        payload = valid_trace()
        raw = (json.dumps(payload, sort_keys=True) + "\n").encode()
        summary = trace.validate_trace(
            payload,
            raw=raw,
            repository_root=ROOT,
            expected_commit_sha=COMMIT,
            expected_artifact_sha256=ARTIFACT,
            expected_scenario="CACTUS_READ",
        )

        self.assertEqual("valid", summary["status"])
        self.assertEqual(2, summary["event_count"])
        self.assertEqual(20_000, summary["maximum_lateness_micros"])
        self.assertEqual(1, summary["action_counts"]["HOLD_JUMP_START"])
        self.assertEqual(hashlib.sha256(raw).hexdigest(), summary["payload_sha256"])

    def test_root_and_event_keys_are_exact(self) -> None:
        missing = valid_trace()
        del missing["captured_at_utc_ms"]
        self.invalid(missing, "missing keys")

        extra = valid_trace()
        extra["invented"] = True
        self.invalid(extra, "unrecognized keys")

        event_extra = valid_trace()
        event_extra["events"][0]["invented"] = 1
        self.invalid(event_extra, r"events\[0\].*unrecognized")

    def test_identity_and_scenario_must_match_canonical_expectations(self) -> None:
        payload = valid_trace()
        raw = json.dumps(payload).encode()
        with self.assertRaisesRegex(trace.ScenarioTraceError, "expected candidate"):
            trace.validate_trace(
                payload,
                raw=raw,
                repository_root=ROOT,
                expected_commit_sha="3" * 40,
            )
        with self.assertRaisesRegex(trace.ScenarioTraceError, "expected artifact"):
            trace.validate_trace(
                payload,
                raw=raw,
                repository_root=ROOT,
                expected_artifact_sha256="4" * 64,
            )
        payload["scenario"] = "INVENTED_SCENARIO"
        self.invalid(payload, "not in the canonical catalogue")

    def test_count_sequence_order_lateness_and_action_fail_closed(self) -> None:
        cases = []

        count = valid_trace()
        count["event_count"] = 1
        cases.append((count, "event_count"))

        sequence = valid_trace()
        sequence["events"][1]["sequence"] = 7
        cases.append((sequence, "sequence must equal 1"))

        schedule = valid_trace()
        schedule["events"][1]["scheduled_at_micros"] = 3_000_000
        cases.append((schedule, "schedule is not chronological"))

        dispatch = valid_trace()
        dispatch["events"][1]["dispatched_at_micros"] = 3_100_000
        cases.append((dispatch, "dispatch is not chronological"))

        early = valid_trace()
        early["events"][0]["dispatched_at_micros"] = 3_000_000
        cases.append((early, "dispatch precedes"))

        lateness = valid_trace()
        lateness["events"][0]["lateness_micros"] = 1
        cases.append((lateness, "lateness_micros is inconsistent"))

        action = valid_trace()
        action["events"][0]["action"] = "TELEPORT"
        cases.append((action, "action is unknown"))

        for payload, message in cases:
            with self.subTest(message=message):
                self.invalid(payload, message)

    def test_strict_file_loader_rejects_duplicate_keys_and_oversize(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            duplicate = root / "duplicate.json"
            duplicate.write_text(
                '{"schema_version":1,"schema_version":1}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(trace.ScenarioTraceError, "duplicate"):
                trace.load_and_validate(duplicate, repository_root=ROOT)

            oversized = root / "oversized.json"
            oversized.write_bytes(b"{" + b" " * trace.MAX_TRACE_BYTES + b"}")
            with self.assertRaisesRegex(trace.ScenarioTraceError, "between"):
                trace.load_and_validate(oversized, repository_root=ROOT)

    def test_cli_returns_nonzero_for_wrong_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "trace.json"
            path.write_text(json.dumps(valid_trace()), encoding="utf-8")
            self.assertEqual(
                1,
                trace.main(
                    [
                        str(path),
                        "--root",
                        str(ROOT),
                        "--expected-sha",
                        "9" * 40,
                    ]
                ),
            )


if __name__ == "__main__":
    unittest.main()
