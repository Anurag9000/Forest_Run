from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

import validate_human_acceptance as human
import validate_device_acceptance as device
import validate_installed_candidate_identity as installed
import validate_installed_identity_matrix as matrix
import validate_play_delivery_evidence as play
import validate_release_governance as governance


class EvidenceRootSymlinkBoundaryTest(unittest.TestCase):
    @unittest.skipUnless(hasattr(os, "O_NOFOLLOW") and hasattr(os, "symlink"), "no-follow symlinks required")
    def test_all_candidate_bound_validators_reject_a_symlinked_root(self) -> None:
        validators = (
            (device._resolve_evidence_file, device.EvidenceError),
            (governance._resolve_inside, governance.GovernanceError),
            (play._resolve_inside, play.PlayDeliveryError),
            (matrix._resolve_inside, matrix.InstalledIdentityMatrixError),
            (installed._resolve_inside, installed.InstalledIdentityError),
            (human._resolve_inside, human.HumanAcceptanceError),
        )
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            target = parent / "evidence"
            target.mkdir()
            alias = parent / "alias"
            alias.symlink_to(target, target_is_directory=True)
            for resolver, error_type in validators:
                with self.subTest(resolver=resolver.__module__):
                    with self.assertRaisesRegex(error_type, "evidence root.*symbolic link"):
                        resolver(alias, "candidate.json", "candidate")


if __name__ == "__main__":
    unittest.main()
