"""Source regression: all authored encounter probes remain read-only.

The running EntityManager alone chooses and commits an outcome. A collision
query must not mutate presentation, the entity geometry, or persistent state.
"""

from pathlib import Path
import unittest


ENTITY_SOURCE_ROOT = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "anurag9000"
    / "forestrun"
    / "entities"
)
ENCOUNTER_FAMILIES = ("animals", "birds", "flora", "trees")
FORBIDDEN_SIDE_EFFECT_CALLS = (
    ".set(",
    ".offset(",
    ".offsetTo(",
    "ParticleManager.emit(",
    "DialogueBubbleManager.spawn(",
    "FlavorTextManager.spawn(",
    "PersistentMemoryManager.",
    "gameState.add",
    "gameState.record",
    "SfxManager.play",
)


def collision_query(source: str, path: Path) -> str:
    signature = "override fun onCollision("
    if source.count(signature) != 1:
        raise AssertionError(f"{path}: expected one overridden collision probe")
    start = source.index(signature)
    first_brace = source.find("{", start)
    if first_brace < 0:
        raise AssertionError(f"{path}: missing collision body")
    depth = 0
    for index in range(first_brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[first_brace : index + 1]
    raise AssertionError(f"{path}: unterminated collision body")


class CollisionQuerySourceContractTest(unittest.TestCase):
    def test_all_nineteen_authored_encounters_have_read_only_collision_queries(self):
        paths = [
            file
            for family in ENCOUNTER_FAMILIES
            for file in sorted((ENTITY_SOURCE_ROOT / family).glob("*.kt"))
        ]
        self.assertEqual(19, len(paths), "Authored encounter inventory changed; re-audit")
        for path in paths:
            with self.subTest(encounter=path.name):
                body = collision_query(path.read_text(encoding="utf-8"), path)
                for forbidden in FORBIDDEN_SIDE_EFFECT_CALLS:
                    self.assertNotIn(
                        forbidden, body,
                        f"{path.name}: collision probe performs {forbidden}",
                    )

    def test_hedgehog_uses_the_shared_allocation_free_mercy_probe(self):
        path = ENTITY_SOURCE_ROOT / "animals" / "Hedgehog.kt"
        source = path.read_text(encoding="utf-8")
        body = collision_query(source, path)
        self.assertIn(
            "intersectsExpanded(player.hitbox, hitbox, mercyPad)",
            body,
        )
        self.assertNotIn("mercyRect", source)

    def test_dog_projectile_near_miss_does_not_allocate_a_rectangle(self):
        path = ENTITY_SOURCE_ROOT / "animals" / "Dog.kt"
        source = path.read_text(encoding="utf-8")
        near_miss = source.split("fun nearMiss(player: Player): Boolean {", 1)[1].split(
            "\n        }", 1
        )[0]
        self.assertIn("this@Dog.intersectsExpanded(player.hitbox, rect, mercyPad)", near_miss)
        self.assertNotIn("RectF(", near_miss)


if __name__ == "__main__":
    unittest.main()
