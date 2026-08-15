from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ECONOMY = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GardenEconomy.kt"
GARDEN = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt"
README = ROOT / "README.md"
GAME_DESIGN = ROOT / "docs/GAME_DESIGN.md"

ENTRY_PATTERN = re.compile(
    r'GardenPlantEconomy\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'
)
CARD_PATTERN = re.compile(
    r'GardenPlant\(\s*"([^"]+)"\s*,\s*(\d+)\s*,'
)


def canonical_entries() -> list[tuple[int, str, str, int]]:
    source = ECONOMY.read_text(encoding="utf-8")
    return [
        (int(index), full_name, compact_name, int(cost))
        for index, full_name, compact_name, cost in ENTRY_PATTERN.findall(source)
    ]


class GardenCatalogueContractTest(unittest.TestCase):
    def test_runtime_catalogue_is_complete_contiguous_and_unique(self) -> None:
        entries = canonical_entries()
        self.assertEqual(9, len(entries))
        self.assertEqual(list(range(9)), [entry[0] for entry in entries])
        self.assertEqual(9, len({entry[1] for entry in entries}))
        self.assertEqual(9, len({entry[2] for entry in entries}))
        self.assertEqual(
            [15, 20, 25, 30, 40, 50, 60, 75, 100],
            [entry[3] for entry in entries],
        )

    def test_canonical_docs_publish_the_live_order_and_costs(self) -> None:
        entries = canonical_entries()
        readme = README.read_text(encoding="utf-8")
        design = GAME_DESIGN.read_text(encoding="utf-8")

        for index, full_name, _compact_name, cost in entries:
            table_row = f"| {index + 1} | {full_name} | {cost} |"
            self.assertIn(table_row, readme)
            self.assertIn(table_row, design)

        self.assertNotIn("fixed landscape, pending final product/device acceptance", design)
        self.assertIn("GardenEconomy", design)
        self.assertIn("Forest Journal", design)

    def test_canvas_card_metadata_matches_canonical_economy_until_deduplicated(self) -> None:
        entries = canonical_entries()
        source = GARDEN.read_text(encoding="utf-8")
        cards = [(name, int(cost)) for name, cost in CARD_PATTERN.findall(source)]
        expected = [(entry[2], entry[3]) for entry in entries]

        self.assertEqual(expected, cards)
        self.assertIn("persistenceFacade.purchaseNextGardenPlant(i)", source)
        self.assertNotIn("SaveManager.saveGardenProgress", source)
        self.assertNotIn("SaveManager.saveLifetimeSeeds", source)


if __name__ == "__main__":
    unittest.main()
