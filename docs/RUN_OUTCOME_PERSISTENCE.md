# Forest Run — Terminal Run Persistence Contract

## Purpose

A terminal collision produces several permanent side effects:

- entity-specific relationship hit history;
- authored collision dialogue and flavor copy;
- the completed rest quote and `RunSummary`;
- best-ghost publication;
- best-distance advancement;
- forest-mood history;
- return-moment history;
- the canonical last-run summary;
- the completed run's pacifist-route counter.

The responsibilities are split into explicit owners:

- `TerminalHitOutcomeCoordinator` owns deterministic terminal-hit completion;
- `RunOutcomePersistenceCoordinator` owns the exactly-once terminal token and non-ghost recovery sequence;
- `SharedPreferencesRunOutcomeRecoveryStore` owns non-ghost before/after evidence;
- `SharedPreferencesRunOutcomeSummarySnapshotStore` owns the atomic summary-plus-route snapshot;
- `GhostPersistenceManager` owns immediate ghost publication and single-worker ordering;
- `GhostPromotionRecoveryCoordinator` owns durable ghost-plus-distance promotion;
- `AtomicFileGhostPromotionReceiptStore` owns the promotion receipt.

## GameView boundary

`GameView` remains responsible for the live impact and run-state sequence:

1. record the run-level hit;
2. suppress ghost visibility;
3. trigger Player rest and immediate camera/audio/haptic feedback;
4. detach the completed ghost buffer in O(1);
5. identify the killer;
6. invoke `terminalHitOutcome.complete(...)` once;
7. accept the returned completed summary;
8. transition the run into `DYING`.

The `HIT` branch must not directly:

```text
record relationship hit history
compose authored HIT copy
resolve the rest quote
write terminal persistence stores
```

## Terminal-hit completion ordering

`TerminalHitOutcomeCoordinator.complete(...)` preserves:

```text
known persistent relationship hit
→ authored HIT presentation
→ exactly one summary snapshot
→ rest quote resolution
→ completed summary copy
→ exactly one RunOutcomeCommitter call
→ result return
```

The summary callback keeps authoritative live `GameStateManager` access in `GameView` without coupling the extracted owner to the complete mutable game loop.

## Exactly-once terminal token

Each `RunOutcomePersistenceCoordinator` owns one terminal token.

`commit(...)`:

1. rejects duplicate delivery with `ALREADY_COMMITTED`;
2. consumes the token before any mode or storage gate;
3. returns `NON_PERSISTENT_RUN` without writes for persistence-disabled runs;
4. returns `RECOVERY_BLOCKED` when older non-ghost evidence is corrupt or conflicting;
5. otherwise starts the ordered persistence flow.

Only `prepareFreshRun()` and `prepareEncounterScenario()` reopen the token. Both retry non-ghost recovery first.

## Non-ghost recovery journal

Before ghost eligibility is evaluated, production synchronously records:

- raw completed summary;
- forest-mood before and expected after-state;
- return-state before and expected after-state;
- pacifist-route count before and expected after-state;
- current recovery checkpoint.

The schema-versioned journal is scoped to the active save namespace and uses synchronous SharedPreferences `commit()`.

Recovery compares each live state with both journal snapshots:

```text
actual == expected after-state  → accept without replay
actual == recorded before-state → apply expected state and verify
otherwise                       → retain evidence and block
```

This prevents duplicate counters when a state write succeeds immediately before process death or before its checkpoint.

## Atomic summary and route snapshot

`SaveManager.saveLastRunSummary(...)` also increments a route-tier counter, so replaying it is not idempotent.

`SharedPreferencesRunOutcomeSummarySnapshotStore` instead writes:

- all sanitized `last_run_*` fields;
- the exact expected KIND, MERCIFUL, or PEACEFUL count;
- in one synchronous transaction.

`PacifistRouteTier.NONE` writes the summary without mutating its compatibility counter.

Route counts use the same canonical derived-counter ceiling as `SaveManager`:

```kotlin
Int.MAX_VALUE / 16
```

## Ghost eligibility

After the non-ghost PREPARED journal is durable, the terminal coordinator:

1. normalizes completed distance;
2. reads `GhostPersistenceManager.bestDistanceFloor(...)`;
3. requires the run to be strictly better;
4. requires a non-empty detached ghost;
5. submits one distance-aware promotion request.

`bestDistanceFloor(...)` is:

```text
max(durable best distance, accepted in-memory promotion distance)
```

A shorter run therefore cannot enter the queue behind a longer pending promotion.

`RunOutcomePersistenceCoordinator` does not write best distance directly.

## Ghost promotion receipt

Before changing the ghost artifact, the worker writes a fixed-size AtomicFile receipt containing:

```text
target distance
frame count
64-bit raw-frame fingerprint
```

The fingerprint covers frame count and every persisted frame component:

- time;
- x and y;
- Player state ordinal;
- x and y scale.

The sidecar is named from the active ghost artifact:

```text
<ghost filename>.promotion
```

## Ghost promotion sequence

The single daemon worker performs:

```text
AtomicFile promotion receipt
→ AtomicFile validated ghost
→ synchronous best-distance commit
→ promotion receipt clear
```

The threshold write stores `max(current best, candidate distance)`, preserving monotonicity.

Immediate restart behavior is unchanged: accepted frames are published in memory before worker execution. The in-memory publication also carries distance and fingerprint.

`ghostPromoted == true` means accepted into this recoverable pipeline. It does not claim worker completion before the terminal commit returns.

## Ghost recovery

Recovery loads the durable ghost and verifies:

- frame count;
- frame validity;
- fingerprint.

### Matching ghost

When the artifact matches:

- a lower best distance is repaired to the receipt distance;
- an equal or higher threshold is accepted;
- the receipt clears only after the threshold is durable.

### Nonmatching ghost

When the artifact does not match:

- best distance is not advanced;
- the existing ghost is not modified;
- the stale uncommitted receipt is cleared.

### Corrupt or inaccessible receipt

Corrupt receipt or I/O failure blocks new ghost promotions. It does not block the independent non-ghost terminal bundle.

## Ordered terminal persistence

For a normal persistent terminal outcome:

```text
claim terminal token
→ reject unresolved non-ghost recovery
→ compute and commit PREPARED non-ghost journal
→ compare against durable-or-pending best-distance floor
→ optionally accept ghost promotion into worker
→ ensure mood state; checkpoint MOOD_APPLIED
→ ensure return state; checkpoint RETURN_APPLIED
→ ensure atomic summary/route snapshot; checkpoint SUMMARY_APPLIED
→ clear non-ghost journal
```

In parallel after acceptance, the single ghost worker performs its receipt-based sequence.

The two protocols are independently recoverable. They are not one global transaction spanning relationship history, presentation, non-ghost progression, ghost storage, and best distance.

## Recovery triggers

Non-ghost recovery runs:

- during `RunOutcomePersistenceCoordinator` construction;
- during `resetForNewRun()`.

Ghost recovery runs:

- during `AndroidRunOutcomePersistenceSink` construction;
- before a new manager request when no worker is active;
- at the start of every worker task;
- before `loadLatest(...)` uses disk fallback.

## Failure model

The terminal persistence surface is fail-closed against:

- duplicate and re-entrant terminal delivery;
- corrupt or incomplete non-ghost journals;
- noncanonical expected after-states;
- live non-ghost conflicts;
- failed journal or snapshot verification;
- corrupt or invalid ghost promotion receipts;
- a receipt whose candidate ghost never became durable;
- best-distance writes that fail after ghost durability;
- stale direct ghost candidates below the accepted distance floor.

A failed non-ghost journal clear returns `RECOVERY_PENDING`. A corrupt/conflicting non-ghost journal returns `RECOVERY_BLOCKED`.

Ghost recovery uses separate dispositions because ghost evidence can be repaired or abandoned independently.

## Test surface

### Terminal completion

- `TerminalHitOutcomeCoordinatorTest`
- `TerminalHitFeedbackPresenterIntegrationTest`
- `test_terminal_hit_outcome_contract.py`

### Non-ghost terminal persistence

- `RunOutcomePersistenceCoordinatorTest`
- `RunOutcomeRecoveryCoordinatorTest`
- `RunOutcomeRecoveryStoreTest`
- `RunOutcomeRecoveryIntegrationTest`
- `RunOutcomeRecoveryTransitionIntegrationTest`
- `RunOutcomeSummarySnapshotStoreTest`
- `test_run_outcome_persistence_contract.py`
- `test_run_outcome_recovery_store_contract.py`
- `test_run_outcome_recovery_record_validation_contract.py`
- `test_route_counter_ceiling_parity.py`

### Ghost promotion

- `GhostPromotionRecoveryCoordinatorTest`
- `GhostPromotionReceiptStoreTest`
- `GhostPersistenceManagerTest`
- `GhostPersistenceManagerAdmissionTest`
- `RunOutcomePersistenceIntegrationTest`
- `test_ghost_promotion_recovery_contract.py`

The ghost tests cover immediate memory publication, durable completion, all major crash windows, startup repair, mismatch abandonment, pending-distance admission, compatibility overload behavior, receipt corruption, and full frame fingerprint sensitivity.

## Evidence boundary

Performed during these persistence tranches:

- focused Kotlin compilation of non-ghost recovery owners against Android and engine stubs;
- focused Kotlin compilation of ghost recovery primitives and manager surface;
- executable non-ghost recovery harnesses;
- executable route-ceiling matrix;
- executable ghost-promotion crash-window harness;
- exact source and production diff inspection;
- representative source-contract parser execution, including overloaded Kotlin method parsing.

Not performed through an exact-head Android environment:

- complete Gradle/JUnit suite;
- Robolectric suite;
- lint;
- debug or release build;
- connected emulator;
- physical device.

## Remaining limitations

- Legacy ghost/distance mismatches created before promotion receipts cannot be reconstructed because the ghost file contains no distance.
- The frame fingerprint is noncryptographic and has theoretical collision risk.
- Non-ghost and ghost recovery records are not one global atomic transaction.
- Concurrent compatibility-namespace switching during an active ghost worker is unsupported maintenance behavior.
- Corrupt evidence has no automated repair or user-facing remediation path.
