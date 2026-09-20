from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/SpriteManager.kt"
PLAYER = ROOT / "app/src/main/java/com/anurag9000/forestrun/entities/Player.kt"
MENU = ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt"


class PlayerAnimationOwnershipContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.sprites = SPRITES.read_text(encoding="utf-8")
        cls.player = PLAYER.read_text(encoding="utf-8")
        cls.menu = MENU.read_text(encoding="utf-8")

    def sprite_block(self, owner: str) -> str:
        start = self.sprites.index(f"{owner} = SpriteSheet(")
        end = self.sprites.index("        )", start) + len("        )")
        return self.sprites[start:end]

    def test_jump_strip_gameplay_partitions_are_explicit(self) -> None:
        expected = {
            "playerJumpStart": ("frameCount = 2", "startFrame = 0"),
            "playerJumping": ("frameCount = 12", "startFrame = 2"),
            "playerApex": ("frameCount = 4", "startFrame = 14"),
            "playerFalling": ("frameCount = 6", "startFrame = 18"),
            "playerLanding": ("frameCount = 4", "startFrame = 24"),
        }
        for owner, tokens in expected.items():
            with self.subTest(owner=owner):
                block = self.sprite_block(owner)
                for token in tokens:
                    self.assertIn(token, block)

    def test_willow_home_rise_is_a_live_presentation_owner(self) -> None:
        block = self.sprite_block("playerStandUp")
        self.assertIn("frameCount = 18", block)
        self.assertIn("startFrame = 0", block)
        self.assertIn("isLooping = false", block)
        self.assertIn(
            "private val standPlayerSprite: SpriteSheet = spriteManager.playerStandUp.copy()",
            self.menu,
        )
        self.assertIn("Phase.STANDING_UP -> standPlayerSprite", self.menu)

    def test_gameplay_stumble_and_rest_keep_dedicated_sequences(self) -> None:
        for token in (
            "private val animHit = spriteManager.playerHit.copy()",
            "private val animDeath = spriteManager.playerDeath.copy()",
            "PlayerState.STUMBLE -> animHit",
            "PlayerState.REST -> animDeath",
        ):
            self.assertIn(token, self.player)


if __name__ == "__main__":
    unittest.main()
