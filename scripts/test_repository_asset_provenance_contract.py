from __future__ import annotations

import unittest
from pathlib import Path

from validate_asset_provenance import AssetProvenanceError, validate


ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "asset-provenance.json"


class RepositoryAssetProvenanceContractTest(unittest.TestCase):
    def test_every_current_source_asset_has_exactly_one_registry_rule(self) -> None:
        report = validate(ROOT, REGISTRY)

        self.assertGreater(report["assetCount"], 0)
        self.assertEqual(5, report["ruleCount"])
        self.assertEqual(report["assetCount"], len(report["coverage"]))
        self.assertEqual(
            report["assetCount"],
            report["approvedAssetCount"] + report["reviewRequiredAssetCount"],
        )
        self.assertGreater(report["reviewRequiredAssetCount"], 0)

    def test_release_mode_remains_blocked_until_real_review_is_recorded(self) -> None:
        with self.assertRaisesRegex(
            AssetProvenanceError,
            "asset provenance review remains incomplete",
        ):
            validate(ROOT, REGISTRY, require_approved=True)


if __name__ == "__main__":
    unittest.main()
