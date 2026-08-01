from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("compile_device_acceptance_bundle.sh")


class DeviceAcceptanceBundleEntrypointContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_wrapper_has_valid_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(SCRIPT)],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_draft_is_strictly_parsed_before_compilation(self) -> None:
        first_strict = self.source.index("verify_strict_json_evidence.py")
        compiler = self.source.index("compile_device_acceptance.py")
        self.assertLess(first_strict, compiler)
        self.assertIn('"${DRAFT_PATH}"', self.source[first_strict:compiler])

    def test_both_outputs_are_strictly_parsed_after_transactional_compilation(self) -> None:
        compiler = self.source.index("compile_device_acceptance.py")
        second_strict = self.source.rindex("verify_strict_json_evidence.py")
        validator = self.source.index("validate_device_acceptance.py")
        self.assertLess(compiler, second_strict)
        self.assertLess(second_strict, validator)
        output_slice = self.source[second_strict:validator]
        self.assertIn('"${OUTPUT_PATH}"', output_slice)
        self.assertIn('"${SUMMARY_PATH}"', output_slice)

    def test_final_manifest_and_referenced_traces_are_independently_revalidated(self) -> None:
        validator = self.source.index("validate_device_acceptance.py")
        traces = self.source.index("validate_manifest_scenario_traces.py")
        self.assertLess(validator, traces)
        self.assertIn('"${OUTPUT_PATH}"', self.source[validator:traces])
        self.assertIn('"${OUTPUT_PATH}"', self.source[traces:])
        self.assertIn('--root "${ROOT}"', self.source[traces:])
        self.assertIn("set -euo pipefail", self.source)

    def test_optional_generated_timestamp_is_forwarded_only_when_present(self) -> None:
        self.assertIn('if [[ -n "${GENERATED_AT_UTC}" ]]', self.source)
        self.assertIn('--generated-at-utc "${GENERATED_AT_UTC}"', self.source)


if __name__ == "__main__":
    unittest.main()
