from __future__ import annotations

import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import aggregate_device_acceptance as aggregate
from acceptance_test_support import materialize_traced_bundle
from test_validate_device_acceptance import session, valid_bundle


class AggregateDeviceMatrixTest(unittest.TestCase):
    def test_device_identity_and_profile_are_unicode_case_normalized(self) -> None:
        original = session("older_phone", 0)
        equivalent = deepcopy(original)
        equivalent["device"]["manufacturer"] = "  ＥＸＡＭＰＬＥ  "
        equivalent["device"]["model"] = original["device"]["model"].upper()
        equivalent["device"]["build_fingerprint"] = original["device"][
            "build_fingerprint"
        ].upper()

        self.assertEqual(
            aggregate._physical_device_id(original),
            aggregate._physical_device_id(equivalent),
        )
        self.assertEqual(
            aggregate._device_profile_id(original),
            aggregate._device_profile_id(equivalent),
        )

    def test_configuration_change_preserves_physical_identity_but_changes_profile(self) -> None:
        original = session("older_phone", 0)
        for field, value in (
            ("sdk", 34),
            ("ram_mb", 3072),
            ("refresh_hz", 90),
            ("width_px", 1920),
            ("height_px", 1200),
            ("density_dpi", 360),
            ("cutout", True),
        ):
            changed = deepcopy(original)
            changed["device"][field] = value
            with self.subTest(field=field):
                self.assertEqual(
                    aggregate._physical_device_id(original),
                    aggregate._physical_device_id(changed),
                )
                self.assertNotEqual(
                    aggregate._device_profile_id(original),
                    aggregate._device_profile_id(changed),
                )

    def test_identity_field_change_changes_both_physical_and_profile_ids(self) -> None:
        original = session("older_phone", 0)
        for field, value in (
            ("manufacturer", "Different-OEM"),
            ("model", "Different-Model"),
            ("build_fingerprint", "different/device/build"),
        ):
            changed = deepcopy(original)
            changed["device"][field] = value
            with self.subTest(field=field):
                self.assertNotEqual(
                    aggregate._physical_device_id(original),
                    aggregate._physical_device_id(changed),
                )
                self.assertNotEqual(
                    aggregate._device_profile_id(original),
                    aggregate._device_profile_id(changed),
                )

    def test_real_baseline_comparison_rejects_device_profile_substitution(self) -> None:
        import json

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline_bundle = valid_bundle()
            candidate_bundle = valid_bundle()
            candidate_bundle["candidate"]["commit_sha"] = "2" * 40
            for item in candidate_bundle["sessions"]:
                item["build"]["commit_sha"] = "2" * 40
            candidate_bundle["sessions"][0]["device"]["model"] = "Substituted-Older-Phone"

            baseline_root = root / "baseline"
            candidate_root = root / "candidate"
            materialize_traced_bundle(baseline_root, baseline_bundle)
            materialize_traced_bundle(candidate_root, candidate_bundle)
            baseline = baseline_root / "manifest.json"
            candidate = candidate_root / "manifest.json"
            baseline.write_text(
                json.dumps(baseline_bundle, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            candidate.write_text(
                json.dumps(candidate_bundle, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                aggregate.AggregationError,
                "device-profile sets differ for older_phone",
            ):
                aggregate.aggregate(candidate, baseline_path=baseline)

    def test_session_count_mismatch_is_rejected_before_metric_deltas(self) -> None:
        metric_summary = {
            metric: {"mean": 1.0, "maximum": 1.0}
            for metric in aggregate.METRICS
        }
        trace_contracts = [
            {
                "scenario": "CACTUS_READ",
                "scenario_definition_sha256": "1" * 64,
                "trace_contract_sha256": "2" * 64,
            }
        ]
        candidate = {
            "candidate": {"commit_sha": "4" * 40, "artifact_sha256": "5" * 64},
            "global_metrics": metric_summary,
            "comparison_matrix_sha256": "a" * 64,
            "trace_contracts": trace_contracts,
            "by_device_class": {
                "older_phone": {
                    "session_count": 2,
                    "device_profile_ids": ["3" * 64],
                    "metrics": metric_summary,
                }
            },
        }
        baseline = deepcopy(candidate)
        baseline["candidate"] = {
            "commit_sha": "6" * 40,
            "artifact_sha256": "7" * 64,
        }
        baseline["by_device_class"]["older_phone"]["session_count"] = 1

        with self.assertRaisesRegex(
            aggregate.AggregationError,
            "session counts differ for older_phone",
        ):
            aggregate._compare(candidate, baseline)

    def test_comparison_matrix_hash_is_deterministic_and_sensitive(self) -> None:
        matrix = {
            "older_phone": {
                "session_count": 1,
                "device_profile_ids": ["1" * 64],
            },
            "tablet": {
                "session_count": 1,
                "device_profile_ids": ["2" * 64],
            },
        }
        first = aggregate._comparison_matrix_sha256(matrix)
        reordered = {
            "tablet": matrix["tablet"],
            "older_phone": matrix["older_phone"],
        }
        self.assertEqual(first, aggregate._comparison_matrix_sha256(reordered))
        self.assertEqual(64, len(first))

        changed = deepcopy(matrix)
        changed["older_phone"]["session_count"] = 2
        self.assertNotEqual(first, aggregate._comparison_matrix_sha256(changed))
        changed = deepcopy(matrix)
        changed["tablet"]["device_profile_ids"] = ["3" * 64]
        self.assertNotEqual(first, aggregate._comparison_matrix_sha256(changed))


if __name__ == "__main__":
    unittest.main()
