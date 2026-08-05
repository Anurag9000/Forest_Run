from __future__ import annotations

import copy
import hashlib
import os
import tempfile
import unittest
from pathlib import Path

import validate_device_acceptance as acceptance
from test_validate_device_acceptance import (
    ARTIFACT_BYTES,
    EVIDENCE_BYTES,
    materialize_files,
    valid_bundle,
)


class DeviceAcceptanceHardeningTest(unittest.TestCase):
    def validate(self, bundle, root: Path | None = None):
        return acceptance.validate_bundle(
            bundle,
            source_bytes=b"{}",
            evidence_base=root,
        )

    def test_policy_cannot_shrink_below_mandatory_device_matrix(self) -> None:
        bundle = valid_bundle()
        bundle["policy"]["required_device_classes"].remove("tablet")
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "missing mandatory classes: tablet",
        ):
            self.validate(bundle)

    def test_policy_cannot_shrink_below_mandatory_scenarios(self) -> None:
        bundle = valid_bundle()
        bundle["policy"]["required_scenarios"].remove("ghost_persistence")
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "missing mandatory scenarios: ghost_persistence",
        ):
            self.validate(bundle)

    def test_one_physical_device_cannot_satisfy_two_classes(self) -> None:
        bundle = valid_bundle()
        first = bundle["sessions"][0]["device"]
        second = bundle["sessions"][1]["device"]
        for field in ("manufacturer", "model", "build_fingerprint"):
            second[field] = first[field]
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "one physical device identity cannot satisfy multiple device classes",
        ):
            self.validate(bundle)

    def test_reviewer_identity_is_case_and_unicode_normalized(self) -> None:
        bundle = valid_bundle()
        bundle["approvals"]["reviewers"] = ["Release Owner", "release owner"]
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "distinct reviewers case-insensitively",
        ):
            self.validate(bundle)

    def test_only_haptics_may_be_not_applicable(self) -> None:
        bundle = valid_bundle()
        bundle["sessions"][0]["manual_checks"]["audio"] = "not_applicable"
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "manual_checks.audio cannot be not_applicable",
        ):
            self.validate(bundle)

        bundle = valid_bundle()
        bundle["sessions"][0]["manual_checks"]["haptics"] = "not_applicable"
        self.validate(bundle)

    def test_manual_and_approval_maps_reject_unrecognized_keys(self) -> None:
        bundle = valid_bundle()
        bundle["sessions"][0]["manual_checks"]["invented_check"] = "pass"
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "manual_checks contains unrecognized keys: invented_check",
        ):
            self.validate(bundle)

        bundle = valid_bundle()
        bundle["approvals"]["invented_approval"] = "approved"
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "approvals contains unrecognized keys: invented_approval",
        ):
            self.validate(bundle)

    def test_candidate_artifact_cannot_double_as_scenario_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            artifact_digest = hashlib.sha256(ARTIFACT_BYTES).hexdigest()
            first_entry = next(
                iter(bundle["sessions"][0]["scenarios"].values())
            )["evidence_files"][0]
            first_entry["path"] = bundle["candidate"]["artifact_path"]
            first_entry["sha256"] = artifact_digest
            with self.assertRaisesRegex(
                acceptance.EvidenceError,
                "physical evidence file is reused by path aliases",
            ):
                self.validate(bundle, root)

    def test_hard_linked_evidence_cannot_count_twice(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = valid_bundle()
            materialize_files(root, bundle)
            entries = [
                result["evidence_files"][0]
                for session in bundle["sessions"][:2]
                for result in list(session["scenarios"].values())[:1]
            ]
            first_path = root / entries[0]["path"]
            second_path = root / entries[1]["path"]
            second_path.unlink()
            try:
                os.link(first_path, second_path)
            except OSError as exc:
                self.skipTest(f"hard links unavailable: {exc}")
            self.assertEqual(
                hashlib.sha256(EVIDENCE_BYTES).hexdigest(),
                entries[1]["sha256"],
            )
            with self.assertRaisesRegex(
                acceptance.EvidenceError,
                "reused through a hard link",
            ):
                self.validate(bundle, root)

    def test_manifest_byte_limit_is_enforced_before_schema_walk(self) -> None:
        with self.assertRaisesRegex(
            acceptance.EvidenceError,
            "acceptance manifest must be between",
        ):
            acceptance.validate_bundle(
                {},
                source_bytes=b"x" * (acceptance.MAX_MANIFEST_BYTES + 1),
            )

    def test_validation_does_not_mutate_the_input_bundle(self) -> None:
        bundle = valid_bundle()
        original = copy.deepcopy(bundle)
        self.validate(bundle)
        self.assertEqual(original, bundle)


if __name__ == "__main__":
    unittest.main()
