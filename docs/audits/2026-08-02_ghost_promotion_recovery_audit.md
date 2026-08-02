# Forest Run — Ghost Promotion Recovery Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Scope

This audit covers the best-ghost and best-distance persistence path after non-ghost terminal progression received durable recovery.

The audited pre-change sequence was:

```text
RunOutcomePersistenceCoordinator accepts new best
→ GhostPersistenceManager publishes frames in memory and schedules AtomicFile work
→ RunOutcomePersistenceCoordinator immediately writes best distance
→ worker eventually attempts ghost file write
```

The scheduling result was treated as ghost promotion acceptance, but it was not proof that the durable ghost write succeeded.

## Findings

### 1. Threshold could advance before ghost durability

A process failure or worker failure after scheduling could leave:

```text
old durable ghost
new best-distance threshold
```

A later valid run below that threshold could no longer replace the stale ghost.

### 2. Durable ghost could precede threshold durability

A process failure after ghost write but before a later threshold write could leave:

```text
new durable ghost
old best-distance threshold
```

A shorter later run could be admitted and replace the newer ghost.

### 3. A pending promotion was absent from eligibility comparisons

Best-distance checks read only durable SharedPreferences. A second run could therefore be compared against a stale threshold while a longer accepted promotion was still queued.

### 4. Direct/legacy manager callers were not protected by terminal-coordinator admission

Even after fixing the terminal owner, any direct call into `GhostPersistenceManager` could bypass pending-distance comparison unless the manager enforced it itself.

### 5. Durable recovery needed artifact identity

A receipt containing only distance cannot prove which ghost file landed. Recovery requires a compact identity over the exact persisted frame sequence.

## Implemented protocol

### Receipt

Added `GhostPromotionReceipt` with:

- target distance;
- frame count;
- 64-bit raw-frame fingerprint.

Added `AtomicFileGhostPromotionReceiptStore`:

- magic `FRGP`;
- version 1;
- fixed 24-byte record;
- `<active ghost filename>.promotion` sidecar;
- `AtomicFile` start/finish/fail semantics;
- validation before replacement;
- explicit base/backup/new cleanup.

### Fingerprint

`GhostRunFingerprint` includes:

- frame count;
- `t`, `x`, and `y` raw float bits;
- state ordinal;
- `scaleX` and `scaleY` raw float bits.

The fingerprint is for local recovery identity, not cryptographic authenticity.

### Durable ordering

`GhostPromotionRecoveryCoordinator.persist(...)` now performs:

```text
receipt save
→ ghost save
→ synchronous max(current best, candidate distance) save
→ receipt clear
```

Best distance cannot be durably advanced by this protocol before the candidate ghost write reports success.

### Recovery

For a pending receipt:

```text
load durable ghost
→ validate frame count and fingerprint
```

If the artifact matches:

- repair a lower threshold;
- accept an equal/higher threshold;
- clear receipt after threshold durability.

If the artifact does not match:

- leave existing ghost unchanged;
- leave best distance unchanged;
- clear only the stale uncommitted receipt.

Corrupt receipt and I/O failure block new ghost promotions.

### Manager ownership

`GhostPersistenceManager` now owns:

- immutable immediate publication carrying frames, distance, and fingerprint;
- single-worker promotion order;
- recovery before new work;
- recovery before disk fallback;
- pending-distance floor;
- matching-publication cleanup after pre-durable failure.

### Terminal owner migration

`RunOutcomePersistenceSink.publishBestGhost(...)` now receives distance.

`RunOutcomePersistenceCoordinator`:

- compares against `bestDistanceFloor(...)`;
- submits one distance-aware candidate;
- contains no `saveBestDistanceM` seam;
- performs no direct `SaveManager.saveBestDistance` call.

`ghostPromoted` now means accepted into the recoverable worker pipeline.

## Pending-distance admission

The manager computes:

```text
max(durable best distance, accepted in-memory publication distance)
```

A candidate below that floor is rejected before publication.

This rule is enforced twice:

- the terminal coordinator requires a strictly better run;
- the manager rejects stale direct/legacy calls below the floor.

The compatibility overload submits at the current floor, preserving equal-distance ghost replacement without changing the threshold.

## Crash-window matrix

### Receipt not durable

- no ghost write;
- no threshold write;
- candidate publication removed if still current.

### Receipt durable, ghost not durable

- threshold unchanged;
- receipt retained;
- later recovery sees nonmatching ghost and abandons receipt.

### Ghost durable, threshold not durable

- receipt retained;
- later recovery matches fingerprint and repairs threshold.

### Threshold durable, receipt not cleared

- later recovery recognizes equal/higher threshold;
- receipt clears without rewriting or incrementing anything.

### Older worker fails after newer publication

`clearPublicationIfCurrent(...)` compares distance and fingerprint. Failure of candidate A cannot remove newer candidate B's in-memory publication.

### Shorter candidate arrives behind longer pending candidate

The shorter candidate is rejected by the pending-distance floor and never enters the worker queue.

## Tests added or migrated

### Pure recovery

`GhostPromotionRecoveryCoordinatorTest` covers:

- canonical persist order;
- matching distance repair;
- already-applied threshold;
- mismatched ghost abandonment;
- failed ghost write;
- failed threshold write and later repair;
- corrupt receipt blocking;
- fingerprint sensitivity for every field.

### Receipt codec

`GhostPromotionReceiptStoreTest` covers:

- exact round trip;
- empty and clear;
- truncation;
- trailing bytes;
- unknown version;
- invalid distance;
- invalid frame count;
- preservation of earlier evidence after rejected replacement.

### Production manager

`GhostPersistenceManagerTest` covers:

- immediate in-memory availability;
- worker-completed ghost and distance durability;
- empty receipt after success;
- startup threshold repair;
- startup mismatch abandonment.

`GhostPersistenceManagerAdmissionTest` covers:

- direct stale-candidate rejection;
- pending-distance floor;
- compatibility overload equal-distance behavior.

### Terminal integration

`RunOutcomePersistenceCoordinatorTest` and `RunOutcomeRecoveryCoordinatorTest` were migrated to the distance-aware sink and no longer expect a direct threshold write.

`RunOutcomePersistenceIntegrationTest` covers:

- accepted terminal promotion;
- durable ghost and distance completion;
- empty-ghost isolation;
- a shorter next run rejected behind a longer pending promotion;
- deterministic-run isolation.

### Source contracts

`test_run_outcome_persistence_contract.py` now forbids direct best-distance ownership in the terminal coordinator.

`test_ghost_promotion_recovery_contract.py` locks:

- single-worker ownership;
- overload-safe source parsing;
- immediate publication order;
- stale direct admission;
- receipt-before-ghost-before-distance-before-clear;
- matching and mismatch recovery branches;
- corrupt/I/O blocking;
- fixed receipt codec;
- full frame fingerprint coverage;
- `best_distance` key parity;
- synchronous threshold commit;
- matching-publication cleanup.

## Validation performed

Performed in this tranche:

- focused Kotlin/JVM compilation of `GhostPromotionRecovery.kt` against Android and engine stubs;
- focused compilation of `GhostPersistenceManager.kt` with the new overloads and worker state;
- executable ghost-promotion state-machine harness;
- executable overload-parser check;
- executable matching-versus-mismatch source-contract check;
- exact production diff inspection for terminal sink migration;
- exact production diff inspection for manager ownership;
- exact architecture diff inspection;
- manual exact key comparison with `SaveManager.KEY_BEST_DIST`.

The executable state-machine harness reported:

```text
ghost-promotion recovery checks passed
```

The source parser checks reported:

```text
overload parser checks passed
matching/mismatch contract checks passed
```

## Corrections discovered during implementation

1. The first source contract used a formatting-sensitive multiline overload signature. It was replaced with an overload parser that skips expression-bodied methods and selects the first block-bodied overload.
2. The first matching-recovery contract searched for the first `receiptStore.clear()` in the whole method, which belonged to the mismatch branch. The contract now validates mismatch and matching tails separately.
3. Test cleanup used a Boolean-returning method reference where an explicit Unit lambda is clearer and more portable. It was replaced with `{ file -> file.delete() }`.
4. Manager admission was strengthened after the initial integration so direct callers, not only the terminal coordinator, obey the pending-distance floor.

## Exact production changes

`3b16ae914e0400d4a2931ac7ffd22dea15eb146c`:

- adds distance to the ghost sink request;
- routes best-distance comparison through the manager floor;
- removes the direct best-distance write;
- triggers pending ghost recovery during production sink construction.

`b4bac4ff7fb144b5ca8d6c0d4eb879b9f1f339ec`:

- replaces frame-only in-memory publication with frames/distance/fingerprint publication;
- adds worker recovery and durable promotion;
- adds pending-distance floor and recovery entry points;
- preserves immediate playback.

`a51ba046852456f6c0ce2e2cf2a1bd00f8359ee0`:

- makes the compatibility overload use the current floor;
- rejects direct candidates below the floor before publication.

No `GameView`, collision, Player, rendering, input, Bloom, encounter, Garden, audio, haptic, or authored presentation code changed.

## Remaining limitations

- Ghost files created before receipt support do not encode distance, so an existing legacy mismatch cannot be reconstructed.
- The fingerprint is noncryptographic and theoretically collidable.
- Ghost and non-ghost recovery records are independent rather than one global terminal transaction.
- Concurrent switching of compatibility save namespaces during an active worker is unsupported.
- Corrupt receipt evidence has no automated repair or user-facing remediation path.
- Exact-head Gradle, Robolectric, lint, Android build, emulator, and physical-device evidence was not available in this session.
