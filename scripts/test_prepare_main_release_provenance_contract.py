from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = (ROOT / "scripts/prepare_main_release.sh").read_text(encoding="utf-8")


class PrepareMainReleaseProvenanceContractTest(unittest.TestCase):
    def test_strict_asset_provenance_precedes_store_and_release_generation(self) -> None:
        source_assets = SCRIPT.index("verify_release_source_assets.py")
        provenance = SCRIPT.index("validate_asset_provenance.py")
        strict = SCRIPT.index("--require-approved", provenance)
        graphics = SCRIPT.index("verify_store_graphics.py")
        metadata = SCRIPT.index("verify_store_metadata.py")
        prepare = SCRIPT.index("prepare_play_release.py")

        self.assertLess(source_assets, provenance)
        self.assertLess(provenance, strict)
        self.assertLess(strict, graphics)
        self.assertLess(graphics, metadata)
        self.assertLess(metadata, prepare)

    def test_release_gate_remains_candidate_and_origin_bound(self) -> None:
        self.assertIn("verify_origin_main.sh", SCRIPT)
        self.assertIn("verify_main_candidate.py", SCRIPT)
        self.assertIn('if [[ "${candidate_sha}" != "${origin_sha}" ]]', SCRIPT)
        self.assertIn('if [[ "${final_origin_sha}" != "${candidate_sha}" ]]', SCRIPT)

    def test_provenance_validation_cannot_be_soft_failed(self) -> None:
        provenance_start = SCRIPT.index("validate_asset_provenance.py")
        provenance_end = SCRIPT.index("verify_store_graphics.py", provenance_start)
        command = SCRIPT[provenance_start:provenance_end]

        self.assertIn('--root "${ROOT}"', command)
        self.assertIn("--require-approved", command)
        self.assertNotIn("|| true", command)
        self.assertNotIn("set +e", SCRIPT)


if __name__ == "__main__":
    unittest.main()
