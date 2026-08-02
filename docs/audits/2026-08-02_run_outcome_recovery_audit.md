# Forest Run — Terminal Outcome Recovery Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Scope

This tranche addressed the remaining crash-consistency debt in terminal-run persistence without changing `GameView` collision behavior.

The target was the non-ghost bundle:

- forest-mood state and counters;
- return-moment state and rough-run streak;
- canonical completed summary;
- pacifist-route run count.

Ghost frames and best-distance promotion were audited separately and remain outside replayable recovery.

## Implemented production surfaces

### Durable journal

Added `RunOutcomeRecoveryStore.kt` with:

- schema-versioned `RunOutcomeRecoveryRecord`;
- PREPARED, MOOD_APPLIED, RETURN_APPLIED, and SUMMARY_APPLIED phases;
- Empty, Corrupt, and Pending load results;
- synchronous namespace-scoped SharedPreferences storage;
- complete raw-summary serialization;
- mood/return before and after snapshots;
- route-counter before and after snapshots;
- strict key, type, enum, bounds, and transition-consistency validation;
- pure canonical transition functions;
- final-summary sanitization parity.

The schema was advanced to version 2 when the hidden route-counter side effect was discovered.

### Recoverable coordinator

Extended `RunOutcomePersistenceCoordinator` with:

- automatic recovery during construction;
- recovery retry during `resetForNewRun()`;
- `RECOVERY_PENDING` and `RECOVERY_BLOCKED` dispositions;
- synchronous journal creation before ghost evaluation;
- live-state comparison against journal before and after snapshots;
- verified mood and return writes;
- conflict retention and fail-closed blocking;
- compatibility fallback for nonrecoverable test/alternate sinks.

### Atomic summary and route snapshot

Added `RunOutcomeSummarySnapshotStore.kt`.

The audit found that `SaveManager.saveLastRunSummary` also invokes `incrementRouteTierCount`. Replaying that method could double-count a pacifist route even though the summary overwrite itself is idempotent.

The new store writes:

- every canonical `last_run_*` key;
- the expected KIND, MERCIFUL, or PEACEFUL route count;
- one synchronous SharedPreferences commit.

NONE does not mutate a route counter.

## Recovery ordering

The final production order is:

```text
claim terminal token
→ reject older unresolved recovery
→ read mood/return/route before-states
→ compute expected after-states
→ commit PREPARED journal
→ evaluate ghost and best distance
→ compare/apply/verify mood
→ checkpoint MOOD_APPLIED
→ compare/apply/verify return
→ checkpoint RETURN_APPLIED
→ compare/apply/verify atomic summary+route snapshot
→ checkpoint SUMMARY_APPLIED
→ clear journal
```

The initial journal is durable before the first ghost or progression side effect.

## Crash-window behavior

### Write completed, checkpoint did not

Recovery recognizes live state equal to the expected after-state and does not replay the increment.

Covered for:

- mood counters;
- return rough-run streak;
- summary plus route count.

### State unchanged

When live state equals the recorded before-state, recovery applies the expected state and verifies the write.

### Foreign/conflicting state

When live state equals neither snapshot, recovery retains evidence and blocks new permanent terminal writes.

### Final clear failed

The commit returns `RECOVERY_PENDING`. On retry, all completed states are recognized and the journal is cleared without duplicate counters.

## Important findings during implementation

### Kotlin control-flow error

The initial journal `load()` implementation used early `return` statements in an expression-bodied function. Focused compilation rejected it. The function was changed to a block body before further work.

### Source-contract fail-closed count

The first migrated contract expected too few explicit `recoveryBlocked = true` paths. Exact source inspection showed deliberate failure points for preparation, mood, return, final summary, journal clearing, and exception handling. The contract was corrected.

### Test event ordering

The first coordinator test used separate sink and journal event lists, which could not prove interleaving. Both fakes were changed to share one event trace.

### Shared flash contract unrelated correction

The immediately preceding nonterminal tranche had one test-only correction because STUMBLE and MERCY_MISS legitimately share the same flash timer. No recovery production code depended on that correction.

### Route counter hidden side effect

`SaveManager.saveLastRunSummary` was audited line-by-line after summary replay was initially considered idempotent. Its route-tier increment made that assumption false and required schema v2 plus the atomic snapshot store.

### Raw malformed summaries

Initial journal validation rejected negative counters. That would have changed canonical behavior because `SaveManager` sanitizes malformed values at write time. Validation was split into:

- explicit required-key checks;
- raw numeric preservation;
- payload-size and typed-identity checks;
- deterministic after-state validation;
- final persisted-summary sanitization.

## Tests added or extended

Added:

- `RunOutcomeRecoveryCoordinatorTest.kt`;
- `RunOutcomeRecoveryStoreTest.kt`;
- `RunOutcomeRecoveryTransitionIntegrationTest.kt`;
- `RunOutcomeRecoveryIntegrationTest.kt`;
- `RunOutcomeSummarySnapshotStoreTest.kt`;
- `test_run_outcome_recovery_store_contract.py`;
- `test_run_outcome_recovery_record_validation_contract.py`.

Extended:

- `test_run_outcome_persistence_contract.py`.

Coverage includes:

- durable ordering before ghost work;
- all phase transitions;
- already-applied mood, return, and summary-route states;
- raw summary round-trip and sanitization;
- route NONE and saturation;
- corrupt schema/types/enums/missing keys;
- inconsistent expected state rejection;
- mood and route conflicts;
- failed-clear retry;
- production SharedPreferences recovery;
- legacy sink compatibility.

## Validation performed

Performed in this session:

- focused Kotlin compilation of the initial production journal/coordinator against Android and engine stubs;
- compiler-driven correction of invalid Kotlin control flow;
- executable recovery harness with successful output;
- route-aware executable harness with successful output: `route-aware recovery checks passed`;
- exact `SaveManager` preference-key and hidden-side-effect inspection;
- source-contract ordering and parser review;
- contract parser corrections for expression-bodied Kotlin functions;
- exact current-file and commit inspection through the GitHub connector.

Not performed or claimed:

- exact-head Gradle unit execution;
- exact-head Robolectric execution;
- Android lint;
- debug or release build;
- package/install validation;
- connected emulator;
- physical-device acceptance;
- store signing or delivery.

The GitHub connector exposed no workflow run for the exact head and no attached status contexts. Absence of status data is not treated as successful CI.

## Debt removed

The previous statement that mood, return, and summary could remain partially applied without a recovery journal is no longer accurate.

The non-ghost terminal bundle is now recoverable through durable before/after evidence and idempotent state comparison. The summary's hidden route-counter side effect is included in the same atomic snapshot.

## Remaining debt

1. Detached ghost frames are not journaled.
2. Ghost publication and best-distance advancement are not one recoverable transaction.
3. A crash between accepted ghost publication and best-distance write can leave ghost data ahead of its threshold.
4. Automated repair or user-facing handling for corrupt/conflicting recovery evidence does not exist.
5. The immediate terminal HIT impact sequence remains inline in `GameView`.
6. The full collision-result dispatcher remains in `GameView`.
7. Exact-head Android and physical release evidence remains outstanding.

## Classification

This tranche materially improves process-death correctness for terminal progression. It does not establish a cross-store transaction and does not complete release acceptance. Forest Run remains a feature-rich alpha.
