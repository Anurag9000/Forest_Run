from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GAME_VIEW = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
)

OLD_FIELDS = """    private val terminalHitImpact = TerminalHitImpactCoordinator(
        effects = GameViewTerminalHitImpactEffects()
    )
    private val terminalHitOutcome = TerminalHitOutcomeCoordinator(
        relationshipRecorder = AndroidTerminalHitRelationshipRecorder(context),
        feedbackPresenter = AndroidTerminalHitFeedbackPresenter(context),
        restQuoteResolver = AndroidTerminalHitRestQuoteResolver(context),
        outcomeCommitter = runOutcomePersistence
    )
    private val nonTerminalCollisionOutcome = NonTerminalCollisionOutcomeCoordinator(
        effects = GameViewNonTerminalCollisionEffects(),
        relationshipRecorder = AndroidNonTerminalCollisionRelationshipRecorder(context),
        feedbackPresenter = AndroidNonTerminalCollisionFeedbackPresenter(context)
    )
    private val collisionOutcomeDispatcher = CollisionOutcomeDispatcher(
        terminalHitImpact = terminalHitImpact,
        terminalHitOutcome = terminalHitOutcome,
        nonTerminalOutcome = nonTerminalCollisionOutcome
    )
    private val ghostPlayer   = GhostPlayer()
"""

NEW_FIELDS = """    private val ghostPlayer = GhostPlayer()
    private val liveCollisionEffects = LiveCollisionEffects(
        recordRunHitAction = { gameState.recordHit() },
        suppressGhostAction = { seconds -> ghostPlayer.suppress(seconds) },
        triggerPlayerRestAction = { player.triggerRest() },
        triggerStumbleAction = { player.triggerStumble() },
        showStumbleFlashAction = { dominantColor ->
            mercyFlashTimer = mercyFlashDuration
            mercyFlashPaint.color = Color.argb(
                200,
                Color.red(dominantColor),
                Color.green(dominantColor),
                Color.blue(dominantColor)
            )
        },
        playHitAction = { SfxManager.playHit() },
        shakeHitAction = { CameraSystem.shakeHit() },
        playRestAction = { LeitmotifManager.playRest() },
        longPulseAction = { HapticManager.longPulse() },
        mediumPulseAction = { HapticManager.mediumPulse() },
        showMercyFlashAction = {
            mercyFlashTimer = mercyFlashDuration
            mercyFlashPaint.color = Color.argb(200, 60, 240, 80)
        },
        playMercyMissAction = { SfxManager.playMercyMiss() },
        doubleTapAction = { HapticManager.doubleTap() },
        emitMercyStarsAction = { centerX, centerY ->
            ParticleManager.emit(FxPreset.MERCY_STARS, centerX, centerY)
        },
        shakeMercyMissAction = { CameraSystem.shakeMercyMiss() }
    )
    private val terminalHitImpact = TerminalHitImpactCoordinator(
        effects = liveCollisionEffects
    )
    private val terminalHitOutcome = TerminalHitOutcomeCoordinator(
        relationshipRecorder = AndroidTerminalHitRelationshipRecorder(context),
        feedbackPresenter = AndroidTerminalHitFeedbackPresenter(context),
        restQuoteResolver = AndroidTerminalHitRestQuoteResolver(context),
        outcomeCommitter = runOutcomePersistence
    )
    private val nonTerminalCollisionOutcome = NonTerminalCollisionOutcomeCoordinator(
        effects = liveCollisionEffects,
        relationshipRecorder = AndroidNonTerminalCollisionRelationshipRecorder(context),
        feedbackPresenter = AndroidNonTerminalCollisionFeedbackPresenter(context)
    )
    private val collisionOutcomeDispatcher = CollisionOutcomeDispatcher(
        terminalHitImpact = terminalHitImpact,
        terminalHitOutcome = terminalHitOutcome,
        nonTerminalOutcome = nonTerminalCollisionOutcome
    )
"""

TAIL_MARKER = "\n    private inner class GameViewTerminalHitImpactEffects"


def verify_adopted(text: str) -> None:
    assert text.count("private val liveCollisionEffects = LiveCollisionEffects(") == 1
    assert text.count("effects = liveCollisionEffects") == 2
    assert "GameViewTerminalHitImpactEffects" not in text
    assert "GameViewNonTerminalCollisionEffects" not in text
    assert text.count("private val ghostPlayer = GhostPlayer()") == 1
    assert text.rstrip().endswith("}")


def apply_migration(text: str) -> str:
    assert text.count(OLD_FIELDS) == 1, "expected exact old collision field block once"
    assert text.count(TAIL_MARKER) == 1, "expected exact private effect tail once"
    assert "private val liveCollisionEffects" not in text

    migrated = text.replace(OLD_FIELDS, NEW_FIELDS, 1)
    tail_start = migrated.index(TAIL_MARKER)
    tail = migrated[tail_start:]
    assert tail.count("private inner class GameViewTerminalHitImpactEffects") == 1
    assert tail.count("private inner class GameViewNonTerminalCollisionEffects") == 1
    assert tail.count("private inner class") == 2
    assert tail.rstrip().endswith("    }\n}")
    migrated = migrated[:tail_start] + "\n}"
    verify_adopted(migrated)
    return migrated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    current = GAME_VIEW.read_text(encoding="utf-8")
    if args.check:
        verify_adopted(current)
        return

    migrated = apply_migration(current)
    GAME_VIEW.write_text(migrated, encoding="utf-8")


if __name__ == "__main__":
    main()
