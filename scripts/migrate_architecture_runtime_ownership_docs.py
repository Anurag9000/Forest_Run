#!/usr/bin/env python3
"""One-shot exact reconciliation of canonical architecture ownership prose."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARCH = ROOT / "docs/ARCHITECTURE.md"
WORKFLOW = ROOT / ".github/workflows/architecture-doc-reconciliation.yml"
SELF = Path(__file__)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    text = ARCH.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "`RunState` owns `PLAYING`, `DYING`, `GAME_OVER`, and `RESTARTING`. `RunResetManager` advances death timing, restart fade, reset, and return to the Garden.\n",
        "`RunState` owns `PLAYING`, `DYING`, `GAME_OVER`, and `RESTARTING`. `RunResetManager` advances death timing and restart mechanics. `RunSessionTransitionPlanner` is the pure transition table, `RunSessionTransitionCoordinator` executes its ordered effects, and `LiveRunSessionEffects` adapts those effects to the current Android/game owners. `GameView` adopts a transition only after all required effects succeed, so screen/run state cannot advance after a failed live effect.\n",
        "run-session ownership",
    )

    text = replace_once(
        text,
        "Only the selected entity receives effects. Collision arbitration precedes pass processing. Resolved encounters and clean passes persist centrally and once.\n",
        "Only the selected entity receives effects. Collision arbitration precedes pass processing. `CollisionOutcomeDispatcher` is the single result dispatcher for terminal HIT, STUMBLE, MERCY_MISS, and NONE; it delegates ordered behavior to the terminal-impact, terminal-completion, and nonterminal coordinators. Resolved encounters and clean passes persist centrally and once.\n",
        "collision dispatcher ownership",
    )

    old_persistence = '''Persistence remains split among:

- `SaveManager` — scores, Seeds, summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager` — encounters, hits, passes, spares, relationships, return/history signals;
- `SaveIntegrityManager` — migration, type repair, bounds, saturation, incomplete-summary rejection, compatibility storage.
'''
    new_persistence = '''Low-level persistence remains split among specialized durability owners, while live application mutations share `ApplicationPersistenceFacade`:

- `SaveManager` — scores, Seeds, summaries, Garden/costume values, ghost compatibility paths;
- `PersistentMemoryManager` — encounters, hits, passes, spares, relationships, return/history signals;
- `RunOutcomePersistenceCoordinator` and ghost promotion stores — independently recoverable terminal/ghost protocols;
- `SaveIntegrityManager` — migration, type repair, bounds, saturation, incomplete-summary rejection, compatibility storage;
- `ApplicationPersistenceFacade` — the live application boundary for terminal outcome commits, encounter/pass/hit memory, Garden purchases, wardrobe writes, feedback settings, and recovery maintenance.

The facade deliberately does **not** claim a global ACID transaction across SharedPreferences, AtomicFile ghost artifacts, and recovery journals. Each durability domain retains its own atomic/recovery protocol.
'''
    text = replace_once(text, old_persistence, new_persistence, "persistence facade ownership")

    text = replace_once(
        text,
        "The private `GameViewTerminalHitImpactEffects` adapter maps those operations one-to-one to `GameStateManager`, `GhostPlayer`, `Player`, `CameraSystem`, `SfxManager`, `LeitmotifManager`, and `HapticManager`.\n",
        "The shared `LiveCollisionEffects` adapter maps terminal and nonterminal live effects one-to-one to `GameStateManager`, `GhostPlayer`, `Player`, camera, audio, haptic, particle, and flash owners. The former private terminal/nonterminal `GameView` effect adapters no longer exist.\n",
        "shared collision effect adapter",
    )

    text = replace_once(
        text,
        "`GameView` retains one impact invocation, one completion invocation, returned-summary assignment, death-timer trigger, and transition to `DYING`. It no longer calls terminal Player/ghost/camera/audio/music/haptic effects directly.\n",
        "`GameView` captures the live collision inputs once and calls `CollisionOutcomeDispatcher`. A terminal dispatch result supplies the completed summary; `GameView` stores that presentation state and emits `RunSessionEvent.TERMINAL_COLLISION_COMPLETED`. The session transition coordinator owns the death effect and `DYING` transition. `GameView` no longer invokes terminal impact/completion coordinators or death state mutation directly.\n",
        "terminal dispatcher/session handoff",
    )

    text = replace_once(
        text,
        "`GameViewNonTerminalCollisionEffects` remains a private live-state adapter for Player, ghost, flash, camera, audio, haptic, and particles. Deterministic/persistence-disabled scenarios retain local feedback without permanent relationship writes.\n",
        "STUMBLE and MERCY_MISS share the same `LiveCollisionEffects` adapter as terminal HIT. Deterministic/persistence-disabled scenarios retain local feedback without permanent relationship writes, while persistent relationship writes flow through the shared application facade.\n",
        "nonterminal shared adapter",
    )

    maintenance_anchor = "`AndroidRecoveryEvidenceMaintenance` captures one immutable namespace during construction. Both evidence handlers use namespace-bound stores, so switching the active `SaveManager` namespace afterward does not redirect inspection, recovery, or cleanup performed by that maintenance instance.\n"
    maintenance_new = maintenance_anchor + "\nOrdinary players also have a fail-closed recovery surface: `RecoveryEvidencePresentation` produces privacy-safe rows, `RecoveryEvidenceUserController` revalidates every requested action through `ApplicationPersistenceFacade`, and `RecoveryEvidenceDialogCoordinator` is attached by `MainActivity`. Safe retry is non-destructive; corrupt/pending discard requires a second explicit confirmation. Debug/ADB maintenance remains a separate acceptance/support surface.\n"
    text = replace_once(text, maintenance_anchor, maintenance_new, "user recovery ownership")

    old_debt = '''## 20. Known debt and unresolved evidence

- `GameView` remains large and requires incremental behavior-preserving decomposition.
- The complete collision-result `when` dispatcher remains in `GameView`.
- STUMBLE and MERCY_MISS live effects remain in `GameViewNonTerminalCollisionEffects`.
- Persistence ownership remains distributed across several managers and should be consolidated only after behavior remains stable.
- Ghost/distance mismatches predating persistent manifests cannot be reconstructed.
- Version-1 sidecars retain noncryptographic identity until replay requires strong upgrade.
- The healthy already-applied path avoids repeated hashing; maintenance performs full validation.
- SHA-256 identifies content/distance but does not authenticate a trusted writer.
'''
    new_debt = '''## 20. Known debt and unresolved evidence

- `GameView` remains a large SurfaceView orchestration host, but the previously identified collision-result dispatcher, live collision-effect adapters, and top-level run-session transition table/effect execution have been extracted. Further decomposition should be driven by measured maintainability or device findings rather than broad rewrites.
- Low-level persistence remains intentionally separated by durability domain behind `ApplicationPersistenceFacade`; there is no global transaction across SharedPreferences and AtomicFile protocols.
- Ghost/distance mismatches predating persistent manifests cannot be reconstructed.
- Version-1 sidecars retain noncryptographic identity until replay requires strong upgrade.
- The healthy already-applied path avoids repeated hashing; maintenance performs full validation.
- SHA-256 identifies content/distance but does not authenticate a trusted writer.
- Source integration does not replace physical-device fairness, TalkBack, performance/thermal/battery, signed-install, store-delivery, or final asset/policy acceptance evidence.
'''
    text = replace_once(text, old_debt, new_debt, "known debt reconciliation")

    if "GameViewTerminalHitImpactEffects" in text or "GameViewNonTerminalCollisionEffects" in text:
        raise SystemExit("stale private GameView collision adapter claim survived")
    if "complete collision-result `when` dispatcher remains in `GameView`" in text:
        raise SystemExit("stale GameView dispatcher debt survived")
    if "CollisionOutcomeDispatcher" not in text or "ApplicationPersistenceFacade" not in text:
        raise SystemExit("current runtime ownership missing after architecture reconciliation")

    ARCH.write_text(text, encoding="utf-8")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
