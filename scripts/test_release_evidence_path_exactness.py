from __future__ import annotations

import unittest

import validate_human_acceptance as human
import validate_device_acceptance as device
import validate_installed_candidate_identity as installed
import validate_installed_identity_matrix as matrix
import validate_play_delivery_evidence as play
import validate_release_governance as governance


class ReleaseEvidencePathExactnessTest(unittest.TestCase):
    validators = (
        ("device", device._safe_evidence_path, device.EvidenceError),
        ("human", human._safe_relative_path, human.HumanAcceptanceError),
        ("installed", installed._safe_relative_path, installed.InstalledIdentityError),
        ("play", play._safe_relative_path, play.PlayDeliveryError),
        ("matrix", matrix._safe_relative, matrix.InstalledIdentityMatrixError),
        ("governance", governance._safe_relative_path, governance.GovernanceError),
    )

    def test_paths_with_surrounding_whitespace_are_rejected(self) -> None:
        for name, validator, error_type in self.validators:
            for path in (
                " evidence/item.json",
                "evidence/item.json ",
                "\tevidence/item.json",
                "evidence//item.json",
                r"evidence\item.json",
                "C:/evidence/item.json",
                "evidence/item:alternate.json",
            ):
                with self.subTest(validator=name, path=path):
                    with self.assertRaisesRegex(error_type, "path|whitespace|canonical|normalized|relative"):
                        validator(path, "test path")

    def test_paths_with_unpaired_surrogates_are_rejected(self) -> None:
        for name, validator, error_type in self.validators:
            with self.subTest(validator=name):
                with self.assertRaisesRegex(error_type, "Unicode|canonical|normalized"):
                    validator("evidence/\ud800.json", "test path")


if __name__ == "__main__":
    unittest.main()
