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
    "CACTUS": ("flora", "Cactus", "cactusSprite"),
    "LILY_OF_VALLEY": ("flora", "LilyOfValley", "lilySprite"),
    "HYACINTH": ("flora", "Hyacinth", "hyacinthSprite"),
    "EUCALYPTUS": ("flora", "Eucalyptus", "eucalyptusSprite"),
    "VANILLA_ORCHID": ("flora", "VanillaOrchid", "orchidSprite"),
    "WEEPING_WILLOW": ("trees", "WeepingWillow", "willowSprite"),
    "JACARANDA": ("trees", "Jacaranda", "jacarandaSprite"),
    "BAMBOO": ("trees", "Bamboo", "bambooSprite"),
    "CHERRY_BLOSSOM": ("trees", "CherryBlossom", "cherryBlossomSprite"),
    "DUCK": ("birds", "Duck", "duckFlying"),
    "TIT": ("birds", "TitGroup", "titFlying"),
    "CHICKADEE": ("birds", "ChickadeeGroup", "chickadeeFlying"),
    "OWL": ("birds", "Owl", "owlSprite"),
    "EAGLE": ("birds", "Eagle", "eagleFlying"),
    "CAT": ("animals", "Cat", "catSprite"),
    "WOLF": ("animals", "Wolf", "wolfSprite"),
    "FOX": ("animals", "Fox", "foxSprite"),
    "HEDGEHOG": ("animals", "Hedgehog", "hedgehogSprite"),
    "DOG": ("animals", "Dog", "dogSprite"),
}


class EncounterFamilyCatalogueContractTest(unittest.TestCase):
    def test_all_nineteen_entries_match_factory_real_source_and_sprite_authority(self) -> None:
        self.assertEqual(19, len(MAPPINGS))
        ordered_types = list(MAPPINGS)
        for index, (entity_type, (package_name, class_name, sprite_token)) in enumerate(MAPPINGS.items()):
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
                start = FACTORY.index(f"EntityType.{entity_type} ->")
                if index + 1 < len(ordered_types):
                    end = FACTORY.index(f"EntityType.{ordered_types[index + 1]} ->", start)
                else:
                    end = FACTORY.index("\n        }\n    }", start)
                self.assertIn(sprite_token, FACTORY[start:end])
                source = (
                    ROOT
                    / "app/src/main/java/com/anurag9000/forestrun/entities"
                    / package_name
                    / f"{class_name}.kt"
                )
                self.assertTrue(source.is_file(), source)
                source_text = source.read_text(encoding="utf-8")
                self.assertIn(f"class {class_name}", source_text)
        owl_start = FACTORY.index("EntityType.OWL ->")
        owl_end = FACTORY.index("EntityType.EAGLE ->", owl_start)
        self.assertIn("owlFlying", FACTORY[owl_start:owl_end])

    def test_catalogue_does_not_duplicate_mutable_gameplay_constants(self) -> None:
        for forbidden in (
            "mercyRadius =",
            "mercyCooldownSeconds =",
            "speed =",
            "hitbox.set(",
            "laneY =",
            "spriteManager.",
            "spawnProbability",
            "relationshipThreshold",
        ):
            self.assertNotIn(forbidden, CATALOGUE)
        self.assertIn("movementAndLane", CATALOGUE)
        self.assertIn("collisionAndMercy", CATALOGUE)
        self.assertIn("fairnessCues", CATALOGUE)
        self.assertIn("routeContribution", CATALOGUE)

    def test_derived_profiles_follow_existing_runtime_authorities(self) -> None:
        for token in (
            "Biome.entries",
            "EncounterScenario.entries",
            "RelationshipArcSystem.isTracked(type)",
            "scenario.steps.any { it.type == type }",
            "scenario.steps.all { it.type == type }",
            "type in biome.preferredPool",
            "EncounterVariant.DEFAULT",
        ):
            self.assertIn(token, CATALOGUE)

    def test_factory_contains_one_branch_for_every_catalogued_type(self) -> None:
        for entity_type in MAPPINGS:
            self.assertEqual(1, FACTORY.count(f"EntityType.{entity_type} ->"))
        self.assertEqual(19, FACTORY.count("EntityType."))
        self.assertEqual(1, FACTORY.count("return when (type)"))


if __name__ == "__main__":
    unittest.main()
