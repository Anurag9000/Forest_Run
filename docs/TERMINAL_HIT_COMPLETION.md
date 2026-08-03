# Forest Run — Terminal Hit Completion Seam

## Purpose

The terminal `HIT` branch combines immediate game feel, relationship history, authored copy, summary composition, persistence, and death-state transition. `TerminalHitOutcomeCoordinator` extracts the deterministic completion half without changing the immediate impact sequence.

## Boundary

### GameView retains

`GameView` still performs operations that directly mutate live gameplay state:

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
11. store the returned summary;
12. trigger the death timer and set `RunState.DYING`.

### Extracted coordinator owns

`TerminalHitOutcomeCoordinator` owns:

1. persistent known-killer relationship history;
2. canonical HIT dialogue and flavor presentation;
3. exactly one live summary callback;
4. authored rest-quote resolution;
5. completed summary construction;
6. exactly one terminal persistence call;
7. returning the summary and persistence result.

This remains an intermediate architecture: immediate mechanics stay near Player/run-state owners, while deterministic completion is isolated behind testable interfaces.

## Interfaces

The coordinator depends on:

```kotlin
TerminalHitRelationshipRecorder
TerminalHitFeedbackPresenter
TerminalHitRestQuoteResolver
RunOutcomeCommitter
```

Production uses Android adapters plus `RunOutcomePersistenceCoordinator`. Pure tests replace all four with recording fakes.

## Ordering contract

```text
known persistent relationship hit
→ authored collision feedback
→ summary snapshot
→ rest quote resolution
→ completed summary copy
→ exactly-once persistence
→ result return
```

The order preserves relationship memory before later copy selection, immediate collision feedback, one authoritative summary snapshot, quote/summary identity, and persistence of the completed rather than preview summary.

## Presentation contract

`TerminalHitPresentation` carries killer type, pacifist route tier, and Player coordinates.

`AndroidTerminalHitFeedbackPresenter` delegates copy selection to `RunFlavorPresentation.collisionCue(...)` with `CollisionResult.HIT` and preserves the existing anchors, lifetime, color, and size.

Dialogue and flavor managers reject nonfinite coordinates.

## Persistence interaction

The coordinator knows no Android storage. It passes the completed summary, detached ghost frames, and permanent-progression gate to `RunOutcomeCommitter`.

`RunOutcomePersistenceCoordinator` implements this seam and retains the exactly-once per-run token.

### Non-ghost progression

```text
forest mood
→ return state
→ atomic completed summary plus pacifist-route count
```

Synchronous before/after evidence lets restart recognize already-applied writes. Corrupt or conflicting evidence blocks new permanent progression.

### Ghost artifact and best distance

The terminal coordinator evaluates the detached ghost against `GhostPersistenceManager.bestDistanceFloor(...)` and submits one distance-aware candidate only when strictly better. It never writes best distance.

The frame file remains `SaveManager` ghost format version 2. Promotion uses:

```text
version-2 AtomicFile receipt
→ AtomicFile ghost
→ version-2 AtomicFile manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

Existing version-1 24-byte sidecars remain readable. All new receipt/manifest writes are version-2 56-byte records carrying distance, frame count, historical FNV fingerprint, and 32-byte SHA-256.

The SHA-256 digest binds accepted distance raw bits and every persisted frame field. A distance-only alteration therefore fails strong identity validation. Version-2 matching requires both FNV and SHA-256.

Version-1 evidence uses FNV compatibility and is upgraded to a version-2 manifest before recovery can advance best distance.

Healthy already-applied automatic recovery avoids loading and hashing the ghost. Explicit maintenance performs full validation on demand.

SHA-256 is collision-resistant identity, not authenticated provenance; no secret or signature is involved.

`ghostPromoted` means accepted into the worker pipeline, not necessarily durable before completion returns.

### Explicit maintenance

`RecoveryEvidenceMaintenanceCoordinator` provides independent inspection, safe retry, and deliberate evidence removal for `RUN_OUTCOME` and `GHOST_PROMOTION`.

Automatic recovery remains fail-closed:

- safe retry never clears corrupt evidence;
- pending evidence is retried before deliberate removal;
- I/O failure never authorizes deletion;
- version-2 inspection validates manifest distance and frames with SHA-256;
- version-1 inspection retains FNV compatibility;
- corrupt receipt cleanup preserves a valid manifest;
- corrupt/mismatched manifest cleanup preserves the ghost frame file.

`MainActivity` exposes maintenance only in debuggable builds. Mutating commands require a cold start after save repair and before `GameView`; reused live Activities are inspection-only.

## Tests

### Pure ordering

`TerminalHitOutcomeCoordinatorTest` verifies relationship, feedback, summary, quote, persistence, identity, and nonpersistent/null-killer behavior.

### Android presentation

`TerminalHitFeedbackPresenterIntegrationTest` verifies authored-copy parity, one bubble/line, and rejection of nonfinite anchors.

### Persistence and identity

The persistence suite covers exactly-once ownership, non-ghost journal replay, atomic summary/route state, pending-distance admission, version-1 sidecar reads, version-2 sidecar writes, independently verified distance-bound SHA-256, legacy upgrades, distance/digest/frame tampering, manifest-only repair, and lazy already-applied validation.

### Source ownership

`test_terminal_hit_outcome_contract.py` locks one completion call, immediate impact before completion, completion before death transition, one summary callback, one persistence call, and absence of direct authored/persistence work in the HIT branch.

`test_run_outcome_persistence_contract.py` forbids direct terminal writes from `GameView` and locks non-ghost journal order.

`test_ghost_promotion_recovery_contract.py` locks distance-aware worker ownership, receipt → ghost → manifest → distance → clear, v1/v2 codecs, distance-bound SHA-256, legacy upgrade, lazy validation, pending-distance admission, and no direct terminal best-distance write.

`test_recovery_evidence_maintenance_contract.py` and `test_recovery_maintenance_launch_contract.py` lock domain isolation, strong manifest inspection, selective cleanup, recover-before-discard, no-delete-on-I/O, debug-only access, cold-start mutation, one-shot extras, and payload-free logging.

## Evidence boundary

The extracted completion files compiled against focused Android/game stubs, and an executable fake-seam harness passed relationship → feedback → summary → quote → persistence.

The original `GameView` replacement contained exactly three intended hunks: remove an obsolete quote import, construct the completion coordinator once, and replace inline completion with one coordinator call and returned-summary assignment. Immediate impact, nonterminal collisions, rendering, input, Bloom, ghost playback, resets, debug scenarios, and death timing did not change.

Later tranches compiled the non-ghost recovery, strong ghost identity/codecs, recovery coordinator, manager, maintenance adapter, and debug-command surfaces against focused stubs. Executable golden-vector, versioned-codec, crash-window, legacy-upgrade, maintenance-policy, and cold/live launch harnesses passed.

Checked-in JUnit/Robolectric tests were not executed through an exact-head Android Gradle environment in this session.

## Remaining architecture work

- extract the complete collision-result dispatcher without changing severity or effect order;
- consider a typed terminal-impact command rather than direct static manager calls;
- decide whether an end-user recovery UI is warranted beyond debug/support tooling;
- continue reducing `GameView` only through diff-bounded, behavior-preserving seams;
- retain pre-manifest compatibility while acknowledging that already-existing legacy mismatches cannot be reconstructed;
- retain version-1 sidecar reads until a deliberate compatibility-removal decision is made.
