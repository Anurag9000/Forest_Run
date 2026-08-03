# 2026-08-03 — Terminal HIT Impact Extraction Audit

## Scope

This tranche extracts the immediate terminal `HIT` impact sequence from `GameView` into a pure ordering coordinator while preserving:

- collision arbitration and severity;
- the exact seven immediate effects and their arguments;
- post-impact ghost detachment and state capture;
- authored HIT presentation;
- relationship-memory gating;
- exactly-once terminal persistence;
- summary assignment;
- death-timer triggering and `RunState.DYING` transition;
- STUMBLE and MERCY_MISS behavior;
- rendering, input, Bloom, Garden, reset, and debug-scenario behavior.

All work was committed directly to `main`. No branch, pull request, force push, or history rewrite was used.

## Verified base

The actual repository head at the start of this tranche was:

```text
f896e1f5e54b6e67fc616da568f5daaca6a26099
```

The previously stated `423a14ef...` value was not the repository tip. The GitHub connector’s verified `main` state was used as the source of truth before any write.

The pre-change `GameView.kt` blob was:

```text
7de12d9d8eb90cc7d728c3182c7c968138ee81fb
```

The complete blob was fetched before replacement.

## Prior inline sequence

The `CollisionResult.HIT` branch directly performed:

```text
gameState.recordHit()
→ ghostPlayer.suppress(1.35f)
→ player.triggerRest()
→ CameraSystem.shakeHit()
→ SfxManager.playHit()
→ LeitmotifManager.playRest()
→ HapticManager.longPulse()
→ ghostRecorder.detachSnapshot()
→ resolve killer
→ capture biome/route/Player coordinates
→ TerminalHitOutcomeCoordinator.complete(...)
→ assign rest quote and summary
→ runResetManager.triggerDeath(...)
→ RunState.DYING
```

The behavior was correct, but live terminal feel depended on seven unrelated owners being called inline in one large coordinator.

## Implemented seam

Added:

```text
app/src/main/java/com/anurag9000/forestrun/engine/TerminalHitImpactCoordinator.kt
```

### Effect interface

`TerminalHitImpactEffectSink` exposes only:

```kotlin
recordRunHit()
suppressGhost(seconds)
triggerPlayerRest()
shakeHit()
playHit()
playRest()
longPulse()
```

It has no Android context, entity, biome, authored-copy, summary, persistence, or run-state dependency.

### Exact coordinator order

`TerminalHitImpactCoordinator.apply(...)` performs:

```text
record run hit
→ suppress ghost for 1.35 seconds
→ trigger Player rest
→ hit camera shake
→ hit SFX
→ rest leitmotif
→ long haptic pulse
→ invoke capture callback
```

The suppression duration is owned as one constant:

```kotlin
GHOST_SUPPRESSION_SECONDS = 1.35f
```

### Post-impact capture

The callback returns `TerminalHitImpactCapture` containing:

- killer type;
- current biome;
- `TerminalHitPresentation`;
- detached completed ghost.

The callback remains after every immediate effect. This is important because the previous branch did not detach the ghost or capture killer/biome/Player state until after `player.triggerRest()`, camera, audio, music, and haptic mutations.

Precomputing those values before invoking the coordinator would have changed the observable snapshot point.

`TerminalHitImpactCapture` requires:

```kotlin
presentation.killerType == killerType
```

so authored feedback and terminal completion cannot use different killer identities.

## GameView integration

`GameView` now constructs one coordinator:

```kotlin
private val terminalHitImpact = TerminalHitImpactCoordinator(
    effects = GameViewTerminalHitImpactEffects()
)
```

The HIT branch invokes it once and captures only after impact:

```kotlin
val impact = terminalHitImpact.apply {
    val completedGhost = ghostRecorder.detachSnapshot()
    val killerType = entityManager.entityTypeOf(collision.entity)
    TerminalHitImpactCapture(...)
}
```

It then calls `TerminalHitOutcomeCoordinator.complete(...)` with the immutable capture.

Summary assignment and death transition remain in `GameView`.

### Private live adapter

Added `GameViewTerminalHitImpactEffects`, mapping one-to-one to the original owners:

```text
recordRunHit       → gameState.recordHit()
suppressGhost      → ghostPlayer.suppress(seconds)
triggerPlayerRest  → player.triggerRest()
shakeHit           → CameraSystem.shakeHit()
playHit            → SfxManager.playHit()
playRest           → LeitmotifManager.playRest()
longPulse          → HapticManager.longPulse()
```

No effect was removed, duplicated, reordered, or redirected.

## Exact replacement review

The `GameView` replacement commit was:

```text
b2f88d1d1072c436e423afb1d08843ced2adf2a6
```

Its diff contained exactly three hunks:

1. construct `TerminalHitImpactCoordinator`;
2. replace the seven inline effects with one coordinator invocation plus post-impact capture;
3. add `GameViewTerminalHitImpactEffects` beside the existing nonterminal adapter.

There were no import, STUMBLE, MERCY_MISS, collision-priority, rendering, input, Bloom, Garden, ghost-playback, reset, debug-scenario, summary, persistence, or death-timing hunks.

The new `GameView.kt` blob produced by that update was:

```text
49bcd7e7331025816e5cb025f7ce8f555f53ff7b
```

## Tests

Added:

```text
app/src/test/java/com/anurag9000/forestrun/engine/TerminalHitImpactCoordinatorTest.kt
```

It covers:

- exact seven-effect ordering;
- exact 1.35-second suppression argument;
- capture callback last;
- returned capture identity;
- capture not invoked after an earlier effect failure;
- rejection of presentation/completion killer mismatch.

The failure case preserves the previous fail-fast behavior: if one immediate effect throws, later effects and terminal capture do not execute.

## Source contracts

Added:

```text
scripts/test_terminal_hit_impact_contract.py
```

It locks:

- coordinator order before capture;
- one impact invocation in the HIT branch;
- absence of direct Player/ghost/camera/SFX/music/haptic calls from the HIT branch;
- post-impact detach/killer/biome/route/coordinate capture order;
- completion, summary assignment, death timer, and `DYING` order;
- exact private-adapter ownership;
- capture killer-identity invariant.

Updated:

```text
scripts/test_terminal_hit_outcome_contract.py
```

The previous contract expected seven direct impact calls in `GameView` and would have falsely failed after the extraction. It now locks:

```text
impact invocation
→ post-impact capture
→ completion invocation
→ summary assignment
→ death timer
→ DYING
```

The dedicated impact contract owns the internal seven-effect order, avoiding duplicate and contradictory ownership assertions.

CI discovers both scripts through:

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
```

## Validation performed

### Focused Kotlin compile and executable harness

The coordinator was compiled with focused `EntityType`, `Biome`, `TerminalHitPresentation`, and `GhostFrame` stubs.

The executable harness passed:

```text
terminal hit impact checks passed
```

It exercised:

- ordinary effect order;
- exact suppression argument;
- fail-fast capture suppression;
- capture identity validation.

### Source-contract parser validation

The impact and completion contracts were executed together against the exact extracted HIT/capture/adapter structure.

Result:

```text
Ran 3 tests
OK
```

### Exact diff inspection

The complete `GameView` commit was fetched from GitHub and confirmed to contain only the three intended hunks.

## Evidence not obtained

This tranche did not execute:

- exact-head Gradle compilation;
- the complete JUnit/Robolectric suite;
- Android lint;
- debug or release packaging;
- connected emulator tests;
- physical-device terminal-hit acceptance;
- signed installation or store delivery.

The runtime still could not establish a local GitHub checkout. Focused compilation and source-contract execution are not substitutes for exact-head Android validation.

Empty GitHub status or workflow lists must not be interpreted as successful CI.

## Closed debt

The following architecture debt is closed:

```text
Immediate terminal HIT impact directly coordinates Player, ghost,
camera, audio, music, and haptic owners inside the HIT branch.
```

Those operations are now owned by `TerminalHitImpactCoordinator` and a private live-state adapter.

## Remaining debt

- `GameView` remains large.
- The full collision-result `when` dispatcher remains in `GameView`.
- STUMBLE and MERCY_MISS live effects remain in `GameViewNonTerminalCollisionEffects`.
- Summary assignment and death-state transition intentionally remain in `GameView`.
- Exact-head Android, emulator, physical-device, and release evidence remains outstanding.
- The broader persistence, recovery UI, compatibility, performance, artwork, signing, and store gates remain as documented in the canonical architecture and release checklist.
