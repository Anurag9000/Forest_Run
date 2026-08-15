from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from build_creative_asset_inventory import build_inventory
from build_release_evidence_index import collect_entries


class CreativeAssetInventoryEvidenceBindingTest(unittest.TestCase):
    def test_release_evidence_index_recognizes_inventory_candidate_binding(self) -> None:
        candidate = "b" * 40
        repository = Path(__file__).resolve().parent.parent
        inventory = build_inventory(repository, candidate)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            evidence_path = root / "creative-assets.json"
            evidence_path.write_text(
                json.dumps(inventory, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            entries = collect_entries(
                root,
                candidate,
                ["creative_assets=creative-assets.json"],
                require_bound_kinds=("creative_assets",),
            )

        self.assertEqual(1, len(entries))
        self.assertTrue(entries[0].candidate_bound)
        self.assertEqual((candidate,), entries[0].candidate_bindings)

    def test_wrong_candidate_is_rejected_by_existing_evidence_authority(self) -> None:
        candidate = "c" * 40
        repository = Path(__file__).resolve().parent.parent
        inventory = build_inventory(repository, candidate)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            evidence_path = root / "creative-assets.json"
            evidence_path.write_text(
                json.dumps(inventory, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "does not match"):
                collect_entries(
                    root,
                    "d" * 40,
                    ["creative_assets=creative-assets.json"],
                    require_bound_kinds=("creative_assets",),
                )


if __name__ == "__main__":
    unittest.main()
