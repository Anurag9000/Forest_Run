from __future__ import annotations

import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import stable_file_reader
import validate_device_acceptance as device
import validate_release_governance as governance
import validate_release_readiness as readiness
import validate_human_acceptance as human
import validate_installed_candidate_identity as installed
import validate_installed_identity_matrix as matrix
import validate_play_delivery_evidence as play


class AtomicSummaryPublisherRaceTest(unittest.TestCase):
    def test_acceptance_summary_writers_reject_parent_swap_before_publication(self) -> None:
        writers = (
            ("play delivery", play._write_json, play.PlayDeliveryError),
            ("installed identity", installed._write_json, installed.InstalledIdentityError),
            ("identity matrix", matrix._write_json, matrix.InstalledIdentityMatrixError),
            ("human acceptance", human._write_json, human.HumanAcceptanceError),
        )
        for label, writer, error_type in writers:
            with self.subTest(writer=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                output_dir = root / "nested"
                external = root / "external"
                external.mkdir()
                saved_output_dir = root / "nested.saved"
                output = output_dir / "summary.json"
                original_open = os.open
                swapped = False

                def swap_before_temp_open(path, flags, *args, **kwargs):
                    nonlocal swapped
                    if (
                        isinstance(path, str)
                        and path.startswith(".summary.json.")
                        and kwargs.get("dir_fd") is not None
                        and not swapped
                    ):
                        output_dir.rename(saved_output_dir)
                        output_dir.symlink_to(external, target_is_directory=True)
                        swapped = True
                    return original_open(path, flags, *args, **kwargs)

                with mock.patch.object(
                    stable_file_reader.os, "open", side_effect=swap_before_temp_open
                ):
                    with self.assertRaisesRegex(error_type, "symbolic link"):
                        writer(output, {"status": "valid"})

                self.assertTrue(swapped)
                self.assertFalse((external / "summary.json").exists())
                self.assertFalse((saved_output_dir / "summary.json").exists())

    def test_validator_clis_report_summary_publication_errors(self) -> None:
        cases = (
            (device, "load_and_validate", "_write_json", device.EvidenceError, ["manifest.json"]),
            (human, "load_and_validate", "_write_json", human.HumanAcceptanceError, ["manifest.json"]),
            (play, "load_and_validate", "_write_json", play.PlayDeliveryError, ["manifest.json"]),
            (installed, "load_and_validate", "_write_json", installed.InstalledIdentityError, ["manifest.json"]),
            (matrix, "load_and_validate", "_write_json", matrix.InstalledIdentityMatrixError, ["manifest.json"]),
            (governance, "load_and_validate", "_write_json", governance.GovernanceError, ["manifest.json"]),
            (
                readiness,
                "validate_readiness",
                "_write_summary",
                readiness.ReleaseReadinessError,
                [
                    "--root", ".", "--expected-candidate-sha", "a" * 40,
                    "--device-acceptance", "device.json", "--human-acceptance", "human.json",
                    "--installed-identity-matrix", "matrix.json", "--play-delivery", "play.json",
                    "--release-governance", "governance.json", "--store-metadata-manifest", "metadata.json",
                    "--release-evidence-index", "index.json",
                ],
            ),
        )
        for module, loader_name, writer_name, error_type, arguments in cases:
            with self.subTest(module=module.__name__):
                summary = SimpleNamespace(to_json=lambda: {"status": "valid"})
                with (
                    mock.patch.object(module, loader_name, return_value=summary),
                    mock.patch.object(module, writer_name, side_effect=error_type("publish failed")),
                    contextlib.redirect_stdout(io.StringIO()) as stdout,
                ):
                    result = module.main([*arguments, "--summary-output", "summary.json"])
                self.assertEqual(1, result)
                self.assertEqual(
                    {"status": "invalid", "error": "publish failed"},
                    json.loads(stdout.getvalue()),
                )


if __name__ == "__main__":
    unittest.main()
