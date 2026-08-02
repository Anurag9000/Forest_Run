#!/usr/bin/env python3
"""Source contracts for STUMBLE and MERCY_MISS outcome ownership."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
COORDINATOR = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/NonTerminalCollisionOutcomeCoordinator.kt"
)


def extract_braced_block(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    in_string = False
    escaped = False
    line_comment = False
    block_comment = False
    index = brace

    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""

        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and nxt == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue

        if char == "/" and nxt == "/":
            line_comment = True
            index += 2
            continue
        if char == "/" and nxt == "*":
            block_comment = True
            index += 2
            continue
        if char == '"':
            in_string = True
            index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1

    raise AssertionError(f"Unbalanced Kotlin block for {signature!r}")


class NonTerminalCollisionOutcomeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.game_view = GAME_VIEW.read_text(encoding="utf-8")
        cls.coordinator = COORDINATOR.read_text(encoding="utf-8")

        stumble_start = cls.game_view.index("CollisionResult.STUMBLE ->")
        mercy_start = cls.game_view.index("CollisionResult.MERCY_MISS ->", stumble_start)
        none_start = cls.game_view.index("CollisionResult.NONE ->", mercy_start)
        cls.stumble_block = cls.game_view[stumble_start:mercy_start]
        cls.mercy_block = cls.game_view[mercy_start:none_start]
        cls.stumble_complete = extract_braced_block(
            cls.coordinator,
            "fun completeStumble(\n        input: StumbleCollisionOutcome,",
        )
        cls.mercy_complete = extract_braced_block(
            cls.coordinator,
            "fun completeMercyMiss(input: MercyMissCollisionOutcome)",
        )

    def test_game_view_delegates_each_nonterminal_result_once(self) -> None:
        self.assertEqual(1, self.stumble_block.count("nonTerminalCollisionOutcome.completeStumble("))
        self.assertEqual(1, self.mercy_block.count("nonTerminalCollisionOutcome.completeMercyMiss("))

    def test_stumble_branch_has_no_direct_effect_or_presentation_ownership(self) -> None:
        forbidden = (
            "gameState.recordHit()",
            "PersistentMemoryManager.recordHit(",
            "ghostPlayer.suppress(",
            "player.triggerStumble()",
            "mercyFlashTimer =",
            "SfxManager.playHit()",
            "CameraSystem.shakeHit()",
            "HapticManager.mediumPulse()",
            "RunFlavorPresentation.collisionCue(",
            "DialogueBubbleManager.spawn(",
            "FlavorTextManager.spawn(",
        )
        for call in forbidden:
            self.assertNotIn(call, self.stumble_block)
        self.assertEqual(1, self.stumble_block.count("collision.entity.isActive = false"))

    def test_mercy_branch_has_no_direct_effect_or_presentation_ownership(self) -> None:
        forbidden = (
            "mercyFlashTimer =",
            "SfxManager.playMercyMiss()",
            "HapticManager.doubleTap()",
            "RunFlavorPresentation.mercyCue(",
            "DialogueBubbleManager.spawn(",
            "FlavorTextManager.spawn(",
            "ParticleManager.emit(",
            "CameraSystem.shakeMercyMiss()",
        )
        for call in forbidden:
            self.assertNotIn(call, self.mercy_block)

    def test_game_view_passes_complete_stumble_identity(self) -> None:
        required = (
            "killerType = killerType",
            "routeTier = gameState.pacifistRouteTier",
            "playerX = player.x",
            "playerY = player.y",
            "dominantColor = dominantColor",
            "persistEncounter = persistEncounter",
            "collision.entity.isActive = false",
        )
        for item in required:
            self.assertEqual(1, self.stumble_block.count(item), item)

    def test_game_view_passes_complete_mercy_identity(self) -> None:
        required = (
            "entityType = entityManager.entityTypeOf(collision.entity)",
            "routeTier = gameState.pacifistRouteTier",
            "mercyHearts = gameState.mercyHearts",
            "kindnessChain = gameState.kindnessChain",
            "playerX = player.x",
            "playerY = player.y",
        )
        for item in required:
            self.assertEqual(1, self.mercy_block.count(item), item)

    def test_stumble_coordinator_preserves_canonical_order(self) -> None:
        order = (
            "effects.recordRunHit()",
            "relationshipRecorder.recordHit(input.killerType)",
            "effects.suppressGhost(STUMBLE_GHOST_SUPPRESSION_SECONDS)",
            "effects.triggerStumble()",
            "effects.showStumbleFlash(input.dominantColor)",
            "effects.playNonLethalHit()",
            "effects.shakeHit()",
            "effects.mediumPulse()",
            "feedbackPresenter.presentStumble(input)",
            "deactivateEntity()",
        )
        positions = [self.stumble_complete.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

    def test_stumble_relationship_is_gated_by_persistent_known_killer(self) -> None:
        gate = "if (input.persistEncounter && input.killerType != null)"
        self.assertEqual(1, self.stumble_complete.count(gate))
        self.assertLess(
            self.stumble_complete.index(gate),
            self.stumble_complete.index("relationshipRecorder.recordHit(input.killerType)"),
        )

    def test_mercy_coordinator_preserves_canonical_order(self) -> None:
        order = (
            "effects.showMercyFlash()",
            "effects.playMercyMiss()",
            "effects.doubleTap()",
            "feedbackPresenter.presentMercyMiss(input)",
            "effects.emitMercyStars(",
            "effects.shakeMercyMiss()",
        )
        positions = [self.mercy_complete.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

    def test_presenter_owns_authored_copy_and_geometry(self) -> None:
        expected_once = (
            "RunFlavorPresentation.collisionCue(",
            "RunFlavorPresentation.mercyCue(",
        )
        for call in expected_once:
            self.assertEqual(1, self.coordinator.count(call), call)
        self.assertEqual(2, self.coordinator.count("DialogueBubbleManager.spawn("))
        self.assertEqual(2, self.coordinator.count("FlavorTextManager.spawn("))

    def test_game_view_effect_adapter_owns_live_runtime_calls(self) -> None:
        expected_once = (
            "gameState.recordHit()",
            "player.triggerStumble()",
            "SfxManager.playHit()",
            "HapticManager.mediumPulse()",
            "SfxManager.playMercyMiss()",
            "HapticManager.doubleTap()",
            "CameraSystem.shakeMercyMiss()",
        )
        for call in expected_once:
            self.assertEqual(1, self.game_view.count(call), call)


if __name__ == "__main__":
    unittest.main()
