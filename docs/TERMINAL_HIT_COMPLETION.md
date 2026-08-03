# Forest Run — Terminal HIT Impact and Completion Seams

## Purpose

The terminal `HIT` branch combines immediate game feel, post-impact state capture, relationship history, authored copy, summary composition, persistence, and death transition.

Two focused coordinators now separate those responsibilities without changing behavior:

- `TerminalHitImpactCoordinator` owns the immediate live-impact order;
- `TerminalHitOutcomeCoordinator` owns deterministic completion after impact capture.

`GameView` remains responsible for collision selection, invoking both coordinators, storing the returned summary, and entering the death state.

## Immediate impact owner

`TerminalHitImpactCoordinator` depends only on `TerminalHitImpactEffectSink` and preserves this exact order:

```text
record run hit
→ suppress ghost for 1.35 seconds
→ trigger Player rest
→ hit camera shake
→ hit SFX
→ rest leitmotif
→ long haptic pulse
→ invoke post-impact capture callback
```

The production adapter is the private `GameViewTerminalHitImpactEffects` inner class. It maps one-to-one to the original owners:

```text
gameState.recordHit()
ghostPlayer.suppress(seconds)
player.triggerRest()
CameraSystem.shakeHit()
SfxManager.playHit()
LeitmotifManager.playRest()
HapticManager.longPulse()
```

No Android context, persistence, authored-copy, entity, biome, summary, or death-state dependency exists in the coordinator.

## Post-impact capture boundary

Capture is callback-based deliberately. `GameView` does not precompute terminal inputs before the immediate effects.

Only after the seven effects complete does the callback capture:

1. the detached completed ghost snapshot;
2. killer entity type;
3. current biome;
4. current pacifist-route tier;
5. Player presentation coordinates.

The result is `TerminalHitImpactCapture`, which carries:

- killer type;
- biome;
- `TerminalHitPresentation`;
- detached ghost frames.

Its constructor requires the presentation killer identity to equal the completion killer identity, preventing drift between authored feedback and persistence inputs.

If an immediate effect throws, the capture callback is not invoked. This matches the original inline fail-fast sequence, where later work did not execute after an earlier failure.

## Completion owner

`TerminalHitOutcomeCoordinator` receives the immutable post-impact capture and owns:

```text
persistent known-killer relationship hit
→ canonical authored HIT bubble and flavor line
→ exactly one live summary snapshot
→ authored rest-quote resolution
→ completed summary copy
→ exactly one RunOutcomeCommitter call
→ completion result
```

Its interfaces are:

```kotlin
TerminalHitRelationshipRecorder
TerminalHitFeedbackPresenter
TerminalHitRestQuoteResolver
RunOutcomeCommitter
```

Production uses Android adapters plus `RunOutcomePersistenceCoordinator`. Pure tests replace all seams with recording fakes.

## GameView boundary

The HIT branch now retains only orchestration that genuinely belongs to the live collision owner:

1. invoke `terminalHitImpact.apply { ... }` once;
2. capture ghost/killer/biome/presentation after impact;
3. invoke `terminalHitOutcome.complete(...)` once;
4. build the summary preview from the captured killer;
5. store returned rest quote and summary;
6. trigger death timing;
7. set `RunState.DYING`.

The branch no longer calls Player, ghost, camera, SFX, music, or haptic owners directly.

## End-to-end terminal order

```text
collision arbitration selects HIT
→ compute persistEncounter gate
→ immediate impact coordinator
    record run hit
    suppress ghost
    Player rest
    camera
    SFX
    music
    haptic
→ post-impact capture
    detach ghost
    resolve killer
    capture biome/route/Player coordinates
→ completion coordinator
    relationship memory
    authored HIT feedback
    summary preview
    rest quote
    completed summary
    exactly-once persistence
→ GameView stores summary
→ trigger death timer
→ enter DYING
```

STUMBLE, MERCY_MISS, collision severity, entity deactivation, ghost recording, rendering, input, Bloom, and restart timing are outside this seam and were not changed.

## Presentation contract

`TerminalHitPresentation` carries:

- killer type;
- pacifist route tier;
- Player X;
- Player Y.

`AndroidTerminalHitFeedbackPresenter` delegates copy selection to `RunFlavorPresentation.collisionCue(...)` using `CollisionResult.HIT` and preserves the existing anchors, lifetime, color, and size.

Dialogue and flavor managers reject nonfinite coordinates.

## Persistence interaction

The impact coordinator does not touch persistence.

The completion coordinator passes the completed summary, detached ghost, and permanent-progression gate to `RunOutcomeCommitter`.

`RunOutcomePersistenceCoordinator` implements this seam and retains its exactly-once per-run token.

### Non-ghost progression

```text
forest mood
→ return state
→ atomic completed summary plus pacifist-route count
```

Synchronous before/after evidence lets restart recognize already-applied writes. Corrupt or conflicting evidence blocks new permanent progression.

### Ghost artifact and best distance

The terminal persistence owner evaluates the detached ghost against `GhostPersistenceManager.bestDistanceFloor(...)` and submits one distance-aware candidate only when strictly better. It never writes best distance directly.

The frame file remains `SaveManager` ghost format version 2. Promotion uses:

```text
version-2 AtomicFile receipt
→ AtomicFile ghost
→ version-2 AtomicFile manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

Existing version-1 24-byte sidecars remain readable. New sidecars are version-2 56-byte records carrying distance, frame count, historical FNV, and distance-bound SHA-256.

Healthy already-applied automatic recovery avoids loading and hashing the ghost. Explicit maintenance performs full validation on demand.

## Tests

### Immediate-impact pure tests

`TerminalHitImpactCoordinatorTest` verifies:

- exact seven-effect order;
- the 1.35-second ghost suppression argument;
- post-impact capture occurs last;
- capture is skipped after an earlier effect failure;
- capture/presentation killer identity cannot diverge.

### Completion pure tests

`TerminalHitOutcomeCoordinatorTest` verifies relationship, feedback, summary, quote, persistence, identity, and nonpersistent/null-killer behavior.

### Android presentation

`TerminalHitFeedbackPresenterIntegrationTest` verifies authored-copy parity, one bubble/line, and rejection of nonfinite anchors.

### Source ownership

`test_terminal_hit_impact_contract.py` locks:

- exact impact-effect order before capture;
- one impact invocation in the HIT branch;
- absence of direct Player/ghost/camera/audio/music/haptic calls from that branch;
- detach/killer/biome/route/coordinate capture order;
- completion, summary assignment, and death-transition order;
- one-to-one private adapter ownership.

`test_terminal_hit_outcome_contract.py` continues to lock one completion call, authored/persistence ownership, one summary callback, and completion-before-death behavior.

The remaining persistence and maintenance contracts lock non-ghost journal order, strong ghost identity, versioned sidecars, recovery, and evidence isolation.

## Validation evidence

The exact `GameView` replacement diff contains only three hunks:

1. construct `TerminalHitImpactCoordinator` once;
2. replace the seven inline impact calls with one coordinator invocation and post-impact capture;
3. add `GameViewTerminalHitImpactEffects` beside the existing nonterminal adapter.

Focused Kotlin compilation and an executable fake-effect harness passed:

```text
terminal hit impact checks passed
```

The harness covered ordinary ordering, fail-fast capture suppression, and killer-identity validation.

The source-contract parser passed against the extracted HIT and adapter structure. CI discovers all `scripts/test_*.py` automatically.

The complete exact-head Gradle/JUnit/Robolectric/lint/build/device suites were not executed in this session.

## Remaining architecture work

- extract the complete collision-result dispatcher without changing severity or branch order;
- decide whether STUMBLE and MERCY_MISS live-state effects should move out of the private GameView adapter;
- decide whether an end-user recovery UI is warranted beyond debug/support tooling;
- continue reducing `GameView` only through diff-bounded, behavior-preserving seams;
- retain pre-manifest and version-1 sidecar compatibility until deliberate removal decisions are made.
