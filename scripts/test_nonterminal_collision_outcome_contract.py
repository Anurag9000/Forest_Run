#!/usr/bin/env python3
"""Source contracts for STUMBLE and MERCY_MISS outcome ownership."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
DISPATCHER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/CollisionOutcomeDispatcher.kt"
)
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
        cls.dispatcher = DISPATCHER.read_text(encoding="utf-8")
        cls.coordinator = COORDINATOR.read_text(encoding="utf-8")

        dispatch_start = cls.game_view.index(
            "val dispatchResult = collisionOutcomeDispatcher.dispatch("
        )
        transition_start = cls.game_view.index(
            "if (dispatchResult is CollisionOutcomeDispatchResult.Terminal)",
            dispatch_start,
        )
        cls.live_inputs = cls.game_view[dispatch_start:transition_start]
        stumble_start = cls.live_inputs.index("buildStumbleInput = {")
        deactivate_start = cls.live_inputs.index(
            "deactivateStumbleEntity = {", stumble_start
        )
        mercy_start = cls.live_inputs.index(
            "buildMercyMissInput = {", deactivate_start
        )
        cls.stumble_input = cls.live_inputs[stumble_start:deactivate_start]
        cls.stumble_deactivation = cls.live_inputs[deactivate_start:mercy_start]
        cls.mercy_input = cls.live_inputs[mercy_start:]
        effects_start = cls.game_view.index(
            "private val liveCollisionEffects = LiveCollisionEffects("
        )
        effects_end = cls.game_view.index(
            "private val terminalHitImpact = TerminalHitImpactCoordinator(",
            effects_start,
        )
        cls.effect_adapter = cls.game_view[effects_start:effects_end]
        cls.dispatch = extract_braced_block(
            cls.dispatcher,
            "fun dispatch(\n        result: CollisionResult,",
        )
        cls.stumble_complete = extract_braced_block(
            cls.coordinator,
            "fun completeStumble(\n        input: StumbleCollisionOutcome,",
        )
        cls.mercy_complete = extract_braced_block(
            cls.coordinator,
            "fun completeMercyMiss(input: MercyMissCollisionOutcome)",
        )

    def test_dispatcher_delegates_each_nonterminal_result_once(self) -> None:
        self.assertEqual(
            1,
            self.dispatch.count("nonTerminalOutcome.completeStumble("),
        )
        self.assertEqual(
            1,
            self.dispatch.count("nonTerminalOutcome.completeMercyMiss("),
        )
        self.assertIn("CollisionResult.STUMBLE ->", self.dispatch)
        self.assertIn("CollisionResult.MERCY_MISS ->", self.dispatch)

    def test_game_view_only_supplies_lazy_nonterminal_inputs(self) -> None:
        self.assertEqual(1, self.live_inputs.count("buildStumbleInput = {"))
        self.assertEqual(1, self.live_inputs.count("deactivateStumbleEntity = {"))
        self.assertEqual(1, self.live_inputs.count("buildMercyMissInput = {"))
        self.assertNotIn("nonTerminalCollisionOutcome.completeStumble(", self.game_view)
        self.assertNotIn("nonTerminalCollisionOutcome.completeMercyMiss(", self.game_view)
        self.assertNotIn("when (collision.result)", self.game_view)

    def test_live_inputs_have_no_direct_effect_or_presentation_ownership(self) -> None:
        forbidden = (
            "gameState.recordHit()",
            "PersistentMemoryManager.recordHit(",
            "ghostPlayer.suppress(",
            "player.triggerStumble()",
            "mercyFlashTimer =",
            "SfxManager.playHit()",
            "CameraSystem.shakeHit()",
            "HapticManager.mediumPulse()",
            "SfxManager.playMercyMiss()",
            "HapticManager.doubleTap()",
            "RunFlavorPresentation.collisionCue(",
            "RunFlavorPresentation.mercyCue(",
            "DialogueBubbleManager.spawn(",
            "FlavorTextManager.spawn(",
            "ParticleManager.emit(",
            "CameraSystem.shakeMercyMiss()",
        )
        for call in forbidden:
            self.assertNotIn(call, self.live_inputs)
        self.assertEqual(
            1,
            self.live_inputs.count("collision.entity.isActive = false"),
        )

    def test_game_view_passes_complete_stumble_identity(self) -> None:
        required = (
            "killerType = killerType",
            "routeTier = gameState.pacifistRouteTier",
            "playerX = player.x",
            "playerY = player.y",
            "dominantColor = dominantColor",
            "persistEncounter = persistEncounter",
        )
        for item in required:
            self.assertEqual(1, self.stumble_input.count(item), item)
        self.assertEqual(
            1,
            self.stumble_deactivation.count("collision.entity.isActive = false"),
        )

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
            self.assertEqual(1, self.mercy_input.count(item), item)

    def test_dispatcher_preserves_lazy_stumble_deactivation(self) -> None:
        stumble = self.dispatch.index("CollisionResult.STUMBLE ->")
        mercy = self.dispatch.index("CollisionResult.MERCY_MISS ->", stumble)
        block = self.dispatch[stumble:mercy]
        self.assertLess(
            block.index("input = buildStumbleInput()"),
            block.index("deactivateEntity = deactivateStumbleEntity"),
        )

    def test_stumble_coordinator_preserves_canonical_order(self) -> None:
        order = (
            "effects.recordRunHit()",
            "relationshipRecorder.recordHit(input.killerType)",
            "effects.suppressGhost(STUMBLE_GHOST_SUPPRESSION_SECONDS)",
            "effects.triggerStumble()",
            "effects.showStumbleFlash(input.dominantColor)",
            "effects.playNonLethalHit()",
            "effects.shakeHit()",
            "effects.stumbleImpactHaptic()",
            "feedbackPresenter.presentStumble(input)",
            "deactivateEntity()",
        )
        positions = [self.stumble_complete.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("fun stumbleImpactHaptic() = mediumPulse()", self.coordinator)

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
            "effects.mercyAcknowledgementHaptic()",
            "feedbackPresenter.presentMercyMiss(input)",
            "effects.emitMercyStars(",
            "effects.shakeMercyMiss()",
        )
        positions = [self.mercy_complete.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            "fun mercyAcknowledgementHaptic() = doubleTap()",
            self.coordinator,
        )

    def test_presenter_owns_authored_copy_and_geometry(self) -> None:
        expected_once = (
            "RunFlavorPresentation.collisionCue(",
            "RunFlavorPresentation.mercyCue(",
        )
        for call in expected_once:
            self.assertEqual(1, self.coordinator.count(call), call)
        self.assertEqual(2, self.coordinator.count("DialogueBubbleManager.spawn("))
        self.assertEqual(2, self.coordinator.count("FlavorTextManager.spawn("))

    def test_shared_live_effect_adapter_owns_runtime_calls(self) -> None:
        expected_once = (
            "recordRunHitAction = { gameState.recordHit() }",
            "suppressGhostAction = { seconds -> ghostPlayer.suppress(seconds) }",
            "triggerStumbleAction = { player.triggerStumble() }",
            "playHitAction = { SfxManager.playHit() }",
            "shakeHitAction = { CameraSystem.shakeHit() }",
            "mediumPulseAction = { HapticManager.mediumPulse() }",
            "playMercyMissAction = { SfxManager.playMercyMiss() }",
            "doubleTapAction = { HapticManager.doubleTap() }",
            "ParticleManager.emit(FxPreset.MERCY_STARS, centerX, centerY)",
            "shakeMercyMissAction = { CameraSystem.shakeMercyMiss() }",
        )
        for call in expected_once:
            self.assertEqual(1, self.effect_adapter.count(call), call)
        self.assertEqual(
            2,
            self.effect_adapter.count("mercyFlashTimer = mercyFlashDuration"),
        )
        self.assertEqual(2, self.game_view.count("effects = liveCollisionEffects"))


if __name__ == "__main__":
    unittest.main()
