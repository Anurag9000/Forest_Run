from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CATALOGUE = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/EncounterFamilyCatalogue.kt"
).read_text(encoding="utf-8")
FACTORY = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/entities/EntityFactory.kt"
).read_text(encoding="utf-8")


MAPPINGS = {
    "CACTUS": ("flora", "Cactus"),
    "LILY_OF_VALLEY": ("flora", "LilyOfValley"),
    "HYACINTH": ("flora", "Hyacinth"),
    "EUCALYPTUS": ("flora", "Eucalyptus"),
    "VANILLA_ORCHID": ("flora", "VanillaOrchid"),
    "WEEPING_WILLOW": ("trees", "WeepingWillow"),
    "JACARANDA": ("trees", "Jacaranda"),
    "BAMBOO": ("trees", "Bamboo"),
    "CHERRY_BLOSSOM": ("trees", "CherryBlossom"),
    "DUCK": ("birds", "Duck"),
    "TIT": ("birds", "TitGroup"),
    "CHICKADEE": ("birds", "ChickadeeGroup"),
    "OWL": ("birds", "Owl"),
    "EAGLE": ("birds", "Eagle"),
    "CAT": ("animals", "Cat"),
    "WOLF": ("animals", "Wolf"),
    "FOX": ("animals", "Fox"),
    "HEDGEHOG": ("animals", "Hedgehog"),
    "DOG": ("animals", "Dog"),
}


class EncounterFamilyCatalogueContractTest(unittest.TestCase):
    def test_all_nineteen_entries_match_factory_and_real_source_files(self) -> None:
        self.assertEqual(19, len(MAPPINGS))
        for entity_type, (package_name, class_name) in MAPPINGS.items():
            with self.subTest(entity_type=entity_type):
                self.assertEqual(
                    1,
                    CATALOGUE.count(
                        f'EntityType.{entity_type}, EncounterFamilyGroup.'
                    ),
                )
                self.assertIn(f'"{class_name}", "{package_name}"', CATALOGUE)
                self.assertIn(
                    f"EntityType.{entity_type} -> {class_name}(",
                    FACTORY,
                )
                source = (
                    ROOT
                    / "app/src/main/java/com/anurag9000/forestrun/entities"
                    / package_name
                    / f"{class_name}.kt"
                )
                self.assertTrue(source.is_file(), source)
                source_text = source.read_text(encoding="utf-8")
                self.assertIn(f"class {class_name}", source_text)

    def test_catalogue_does_not_duplicate_mutable_gameplay_constants(self) -> None:
        for forbidden in (
            "mercyRadius =",
            "mercyCooldownSeconds =",
            "speed =",
            "hitbox.set(",
            "laneY =",
            "spriteManager.",
        ):
            self.assertNotIn(forbidden, CATALOGUE)
        self.assertIn("movementAndLane", CATALOGUE)
        self.assertIn("collisionAndMercy", CATALOGUE)
        self.assertIn("fairnessCues", CATALOGUE)
        self.assertIn("routeContribution", CATALOGUE)

    def test_factory_contains_one_branch_for_every_catalogued_type(self) -> None:
        for entity_type in MAPPINGS:
            self.assertEqual(1, FACTORY.count(f"EntityType.{entity_type} ->"))
        self.assertEqual(19, FACTORY.count("EntityType.") - 3)
        self.assertEqual(1, FACTORY.count("return when (type)"))


if __name__ == "__main__":
    unittest.main()
