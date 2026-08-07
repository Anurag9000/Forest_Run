#!/usr/bin/env python3
"""Source drift contracts for the derived 19-type encounter catalogue."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine"
ENTITIES = ROOT / "app/src/main/java/com/anurag9000/forestrun/entities"
CATALOGUE = ENGINE / "EncounterContentCatalogue.kt"
FACTORY = ENTITIES / "EntityFactory.kt"
ENTITY_TYPE = ENTITIES / "EntityType.kt"

EXPECTED_ASSET_TOKENS = {
    "CACTUS": "cactusSprite",
    "LILY_OF_VALLEY": "lilySprite",
    "HYACINTH": "hyacinthSprite",
    "EUCALYPTUS": "eucalyptusSprite",
    "VANILLA_ORCHID": "orchidSprite",
    "WEEPING_WILLOW": "willowSprite",
    "JACARANDA": "jacarandaSprite",
    "BAMBOO": "bambooSprite",
    "CHERRY_BLOSSOM": "cherryBlossomSprite",
    "DUCK": "duckFlying",
    "TIT": "titFlying",
    "CHICKADEE": "chickadeeFlying",
    "OWL": "owlSprite",
    "EAGLE": "eagleFlying",
    "CAT": "catSprite",
    "WOLF": "wolfSprite",
    "FOX": "foxSprite",
    "HEDGEHOG": "hedgehogSprite",
    "DOG": "dogSprite",
}


class EncounterContentCatalogueContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalogue = CATALOGUE.read_text(encoding="utf-8")
        cls.factory = FACTORY.read_text(encoding="utf-8")
        cls.entity_type = ENTITY_TYPE.read_text(encoding="utf-8")

    def test_authoritative_roster_remains_exactly_nineteen_types(self) -> None:
        body = self.entity_type.split("enum class EntityType", 1)[1]
        body = body[body.index("{") + 1 : body.rindex("}")]
        names = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*,?\s*(?://.*)?$", body, re.MULTILINE)
        self.assertEqual(list(EXPECTED_ASSET_TOKENS), names)
        self.assertEqual(19, len(names))

    def test_factory_has_exactly_one_exhaustive_branch_per_type(self) -> None:
        for name in EXPECTED_ASSET_TOKENS:
            token = f"EntityType.{name} ->"
            self.assertEqual(1, self.factory.count(token), token)

    def test_factory_branches_retain_expected_sprite_authorities(self) -> None:
        names = list(EXPECTED_ASSET_TOKENS)
        for index, name in enumerate(names):
            start = self.factory.index(f"EntityType.{name} ->")
            if index + 1 < len(names):
                end = self.factory.index(f"EntityType.{names[index + 1]} ->", start)
            else:
                end = self.factory.index("\n        }\n    }", start)
            branch = self.factory[start:end]
            expected = EXPECTED_ASSET_TOKENS[name]
            self.assertIn(expected, branch, f"{name} lost {expected} asset wiring")
        owl_start = self.factory.index("EntityType.OWL ->")
        owl_end = self.factory.index("EntityType.EAGLE ->", owl_start)
        self.assertIn("owlFlying", self.factory[owl_start:owl_end])

    def test_catalogue_derives_runtime_authorities_instead_of_copying_them(self) -> None:
        required = (
            "EntityType.entries.map(::buildProfile)",
            "Biome.entries",
            "EncounterScenario.entries",
            "RelationshipArcSystem.isTracked(type)",
            "scenario.steps.any { it.type == type }",
            "scenario.steps.all { it.type == type }",
            "type in biome.preferredPool",
        )
        for token in required:
            self.assertIn(token, self.catalogue, token)
        for forbidden in (
            "spriteManager.",
            "spawnProbability",
            "collisionResult",
            "relationshipThreshold",
        ):
            self.assertNotIn(forbidden, self.catalogue, forbidden)


if __name__ == "__main__":
    unittest.main()
