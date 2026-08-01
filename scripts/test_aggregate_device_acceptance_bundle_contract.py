from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/aggregate_device_acceptance_bundle.sh"


class AggregateDeviceAcceptanceBundleContractTest(unittest.TestCase):
    def test_operator_orders_preflight_aggregation_and_output_verification(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")
        preflight = source.index('"${ROOT}/scripts/verify_strict_json_evidence.py"')
        aggregate = source.index('"${ROOT}/scripts/aggregate_device_acceptance.py"')
        output_verify = source.rindex('"${ROOT}/scripts/verify_strict_json_evidence.py"')

        self.assertLess(preflight, aggregate)
        self.assertLess(aggregate, output_verify)
        self.assertIn("--baseline", source)
        self.assertIn("--output", source)
        self.assertIn("must not overwrite the candidate", source)
        self.assertIn("must not overwrite the baseline", source)
        self.assertIn("must be distinct files", source)

    def test_usage_is_fail_closed(self) -> None:
        result = subprocess.run(
            ["bash", str(SCRIPT)],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("Usage:", result.stderr)

    def test_output_cannot_alias_candidate_or_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = root / "candidate.json"
            baseline = root / "baseline.json"
            candidate.write_text("{}", encoding="utf-8")
            baseline.write_text("{}", encoding="utf-8")

            candidate_alias = subprocess.run(
                ["bash", str(SCRIPT), str(candidate), str(candidate)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(0, candidate_alias.returncode)
            self.assertIn("must not overwrite the candidate", candidate_alias.stderr)

            baseline_alias = subprocess.run(
                [
                    "bash",
                    str(SCRIPT),
                    str(candidate),
                    str(baseline),
                    str(baseline),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(0, baseline_alias.returncode)
            self.assertIn("must not overwrite the baseline", baseline_alias.stderr)

            same_inputs = subprocess.run(
                [
                    "bash",
                    str(SCRIPT),
                    str(candidate),
                    str(root / "output.json"),
                    str(candidate),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertNotEqual(0, same_inputs.returncode)
            self.assertIn("must be distinct files", same_inputs.stderr)


if __name__ == "__main__":
    unittest.main()
