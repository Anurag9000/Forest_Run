# Forest Run — Terminal Hit Completion Seam

## Purpose

The terminal `HIT` branch is behaviorally sensitive because it combines immediate game feel, relationship history, authored copy, summary composition, persistence, and death-state transition. Keeping all of that work inline made `GameView` difficult to verify and made ordering changes easy to introduce accidentally.

`TerminalHitOutcomeCoordinator` extracts the completion half of that branch without changing the immediate impact sequence.

## Boundary

### GameView retains

`GameView` still performs the operations that directly mutate live gameplay state:

1. `gameState.recordHit()`;
2. suppress ghost playback visibility;
3. trigger Player rest;
4. trigger camera shake;
5. play hit SFX;
6. transition the leitmotif into rest;
7. emit the long haptic pulse;
8. detach the completed ghost buffer;
9. resolve the killer entity type;
10. call `terminalHitOutcome.complete(...)` once;
11. store the returned summary for the game-over presentation;
12. trigger the death timer and set `RunState.DYING`.

### Extracted coordinator owns

`TerminalHitOutcomeCoordinator` owns:

1. persistent relationship hit history for a known killer;
2. the canonical authored HIT dialogue bubble;
3. the canonical authored HIT flavor line;
4. exactly one live summary snapshot callback;
5. the authored rest quote;
6. the completed summary copy;
7. exactly one call to the terminal persistence seam;
8. returning the completed summary and persistence result.

This is a deliberate intermediate architecture. The live impact mechanics remain close to the Player and run-state owners, while the deterministic completion work is isolated behind testable interfaces.

## Interfaces

The coordinator depends on four seams:

```kotlin
TerminalHitRelationshipRecorder
TerminalHitFeedbackPresenter
TerminalHitRestQuoteResolver
RunOutcomeCommitter
```

Production uses:

```kotlin
AndroidTerminalHitRelationshipRecorder
AndroidTerminalHitFeedbackPresenter
AndroidTerminalHitRestQuoteResolver
RunOutcomePersistenceCoordinator
```

Pure tests replace all four with recording fakes.

## Ordering contract

The completion method must retain this exact order:

```text
known persistent relationship hit
→ authored collision feedback
→ summary snapshot
→ rest quote resolution
→ completed summary copy
→ exactly-once persistence
→ result return
```

Why the order matters:

- relationship memory must reflect the terminal encounter before later return copy is selected;
- the collision cue must remain immediate and precede game-over summary work;
- the summary must be captured exactly once from the authoritative live state;
- the quote must be based on that same preview;
- persistence must receive the completed summary, never the unquoted preview.

## Presentation contract

`TerminalHitPresentation` carries only the data needed by the authored collision presenter:

- killer type;
- pacifist route tier;
- player X;
- player Y.

`AndroidTerminalHitFeedbackPresenter` delegates text selection to `RunFlavorPresentation.collisionCue(...)` using `CollisionResult.HIT`. It preserves the previous anchors and styling:

- bubble X: player X plus half player width;
- bubble Y: player Y minus 28 pixels;
- flavor X: player X plus 18 percent player width;
- flavor Y: player Y minus 8 pixels;
- flavor lifetime: 1.25 seconds;
- authored color and size from the cue.

The underlying dialogue and flavor managers reject nonfinite coordinates, so malformed presentation anchors cannot poison their queues.

## Persistence interaction

The coordinator does not know Android storage. It calls `RunOutcomeCommitter` with:

- the one completed summary;
- the detached ghost list;
- the permanent-progression gate.

`RunOutcomePersistenceCoordinator` implements this interface and retains its exactly-once per-run token.

## Tests

### Pure ordering tests

`TerminalHitOutcomeCoordinatorTest` verifies:

- persistent known-killer ordering;
- exactly one summary callback;
- deterministic/non-persistent relationship isolation;
- null-killer behavior;
- presentation pass-through;
- quote insertion;
- persistence input and result propagation.

### Android presenter tests

`TerminalHitFeedbackPresenterIntegrationTest` verifies under Robolectric:

- parity with `RunFlavorPresentation.collisionCue(...)`;
- one dialogue bubble and one flavor line;
- no queue mutation from NaN or infinite player anchors.

### Source ownership tests

`test_terminal_hit_outcome_contract.py` verifies:

- one completion call in the HIT branch;
- no direct relationship, authored HIT copy, quote, or persistence work in that branch;
- immediate game-feel ordering before completion;
- completion before death transition;
- identity and presentation inputs;
- internal completion ordering;
- one summary callback and one persistence call;
- production adapter ownership.

`test_run_outcome_persistence_contract.py` was migrated to the new boundary and now forbids direct persistence calls from `GameView` while requiring one terminal-hit completion call.

## Evidence boundary

The extracted production files compiled against focused Android/game stubs. An executable fake-seam harness passed the relationship → feedback → summary → quote → persistence order.

The complete `GameView` replacement was compared immediately. Its diff contained exactly three intended hunks:

1. remove the obsolete `RestQuoteManager` import;
2. construct `TerminalHitOutcomeCoordinator` once;
3. replace inline HIT completion with one coordinator call and returned-summary assignment.

No immediate impact, STUMBLE, MERCY_MISS, rendering, input, Bloom, ghost playback, run reset, debug scenario, or death-transition code changed.

The checked-in JUnit and Robolectric tests were not executed through an exact-head Android Gradle environment in this session.

## Remaining architecture work

- extract the complete collision-result dispatcher without changing severity or effect ordering;
- consider a typed terminal-impact command rather than direct static manager calls;
- add durable persistence journaling for process death between storage operations;
- continue reducing `GameView` only through diff-bounded, behavior-preserving seams.
