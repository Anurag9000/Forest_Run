from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/SpriteManager.kt"
PLAYER = ROOT / "app/src/main/java/com/anurag9000/forestrun/entities/Player.kt"


class PlayerAnimationOwnershipContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.sprites = SPRITES.read_text(encoding="utf-8")
        cls.player = PLAYER.read_text(encoding="utf-8")

    def test_jump_strip_exposes_only_live_runtime_partitions(self) -> None:
        self.assertNotIn("playerStandUp", self.sprites)
        expected = {
            "playerJumpStart": ("frameCount = 2", None),
            "playerJumping": ("frameCount = 12", "startFrame = 2"),
            "playerApex": ("frameCount = 4", "startFrame = 14"),
            "playerFalling": ("frameCount = 6", "startFrame = 18"),
            "playerLanding": ("frameCount = 4", "startFrame = 24"),
        }
        for owner, tokens in expected.items():
            with self.subTest(owner=owner):
                start = self.sprites.index(f"{owner} = SpriteSheet(")
                block = self.sprites[start:self.sprites.index("        )", start) + 9]
                for token in tokens:
                    if token is not None:
                        self.assertIn(token, block)

    def test_player_states_consume_dedicated_stumble_and_rest_sequences(self) -> None:
        for token in (
            "private val animHit = spriteManager.playerHit.copy()",
            "private val animDeath = spriteManager.playerDeath.copy()",
            "PlayerState.STUMBLE -> animHit",
            "PlayerState.REST -> animDeath",
        ):
            self.assertIn(token, self.player)


if __name__ == "__main__":
    unittest.main()
