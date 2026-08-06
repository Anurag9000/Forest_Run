from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
BUILDER = (ROOT / "scripts/build_release_evidence_index.py").read_text(encoding="utf-8")
VERIFIER = (ROOT / "scripts/verify_release_evidence_index.py").read_text(encoding="utf-8")
DOC = (ROOT / "docs/RELEASE_EVIDENCE_INDEX.md").read_text(encoding="utf-8")


class ReleaseEvidenceIndexVerifierContractTest(unittest.TestCase):
    def test_verifier_is_independent_and_strict(self) -> None:
        self.assertNotIn("import build_release_evidence_index", VERIFIER)
        self.assertNotIn("from build_release_evidence_index", VERIFIER)
        self.assertIn("import strict_json", VERIFIER)
        self.assertIn("strict_json.loads(", VERIFIER)
        self.assertIn("release evidence index changed during verification", VERIFIER)
        self.assertIn("release evidence index aliases an indexed evidence file", VERIFIER)

    def test_builder_output_is_root_and_inode_separated(self) -> None:
        self.assertIn("def _validate_output_path(", BUILDER)
        self.assertIn("output index must remain inside the evidence root", BUILDER)
        self.assertIn(
            "output index cannot reuse an evidence input through a hard link",
            BUILDER,
        )
        self.assertIn("publish_index(output, payload, root=root)", BUILDER)

    def test_operator_contract_requires_independent_verification(self) -> None:
        self.assertIn("verify_release_evidence_index.py", DOC)
        self.assertIn("--expected-candidate-sha", DOC)
        self.assertGreaterEqual(DOC.count("--require-bound-kind"), 2)
        self.assertIn("independent verifier", DOC.lower())


if __name__ == "__main__":
    unittest.main()
