# Forest Run — Terminal Run Persistence Ownership Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Completed scope

This tranche replaced distributed terminal-run writes in `GameView` with one exactly-once persistence owner.

Implemented directly on `main`:

1. `RunOutcomePersistenceCoordinator.kt`
   - exactly-once per-run token;
   - deterministic/non-persistent token consumption;
   - fakeable persistence sink;
   - production Android sink;
   - best-ghost and best-distance consistency gate;
   - ordered forest-mood, return-moment, and last-summary writes.
2. `GameView.kt`
   - one coordinator field;
   - unconditional O(1) terminal ghost detachment;
   - one completed-summary commit call;
   - no direct terminal ghost, distance, mood, return, or summary writes;
   - one reset at ordinary-run start and one at deterministic-scenario start.
3. `RunOutcomePersistenceCoordinatorTest.kt`
   - six pure ownership and ordering tests.
4. `RunOutcomePersistenceIntegrationTest.kt`
   - three Robolectric tests over the real sink and storage paths.
5. `test_run_outcome_persistence_contract.py`
   - six balanced source ownership and ordering contracts.
6. `ARCHITECTURE.md` and `RUN_OUTCOME_PERSISTENCE.md`
   - synchronized implementation, failure model, evidence boundary, and remaining debt.

## Prior risk

Before this tranche, the terminal `HIT` branch directly performed:

- best-distance comparison;
- ghost detachment and asynchronous publication;
- best-distance storage;
- forest-mood counter updates;
- return-moment updates;
- last-run summary storage.

Duplicate suppression depended on surrounding run-state timing rather than an explicit owner. The best-distance threshold also advanced without checking whether ghost publication was accepted.

## Production contract now

`GameView` performs one call:

```kotlin
runOutcomePersistence.commit(
    summary = completedSummary,
    completedGhost = completedGhost,
    persistProgress = persistEncounter
)
```

The coordinator consumes its token before checking `persistProgress` or touching a sink. Therefore:

- repeated collision delivery cannot double-increment forest history;
- repeated delivery cannot rewrite the last summary;
- a deterministic run cannot be retroactively persisted after a mode change;
- re-entrant delivery observes `ALREADY_COMMITTED`;
- run reset is explicit rather than inferred from state timing.

Best distance is now written only after `GhostPersistenceManager.saveBestRunAsync(...)` returns acceptance. Empty, invalid, or unschedulable ghost data leaves the threshold unchanged.

## Exact large-owner preservation evidence

The complete `GameView.kt` file was replaced from blob:

```text
77d50f17cdc6a2760be66380aa3b953a9c4a4fc3
```

The resulting production commit was:

```text
46acf00de782d1537a1b8cfc8cfa0302947106dc
```

Immediate comparison showed exactly four intended hunks:

1. add the coordinator field;
2. replace the direct best-ghost/best-distance block with O(1) detachment;
3. replace direct mood/return/summary writes with one coordinator call;
4. add one reset to each run-start path.

The diff contained 12 additions and 18 deletions. No rendering, input, physics, collision severity, dialogue, audio, haptic, particle, debug-scenario, or death-state statement changed outside those hunks.

## Validation performed

Executed in this session:

- focused Kotlin compilation of the coordinator with Android/game stubs;
- executable checks for exactly-once commit, reset behavior, deterministic token consumption, canonical ordering, and failed ghost publication;
- Python syntax compilation for the source contract;
- all six source-contract tests against a representative fixture;
- exact Git commit and four-hunk `GameView` diff inspection;
- exact current-source search confirming zero forbidden direct terminal-write calls in `GameView`;
- exact current-source search confirming one coordinator commit call and two reset calls.

Added but not executed through Android Gradle in this session:

- six pure JUnit tests in `RunOutcomePersistenceCoordinatorTest`;
- three production-sink Robolectric tests in `RunOutcomePersistenceIntegrationTest`.

No complete exact-head unit, Robolectric, lint, release-build, packaging, connected-emulator, or physical-device result is claimed.

## Debt removed

The prior architecture item “consolidate distributed persistence ownership so one run outcome cannot be committed through competing paths” is complete at the coordinator ownership level.

## Remaining debt

1. The persistence bundle is ordered but not transactionally atomic across SharedPreferences and asynchronous ghost storage.
2. There is no durable outcome journal or idempotency key for process death between sink operations.
3. Entity-specific hit history remains correctly separate from run-summary persistence but still lives in the large collision branch.
4. Collision feedback, summary construction, relationship recording, and death transition should be extracted behind a behavior-preserving terminal-outcome seam.
5. `GameView` remains a large coordinator.
6. Exact-head Android and physical release evidence remains outstanding.

## Classification

This closes a concrete duplicate-write and stale-ghost-threshold risk and establishes a tested architectural seam. It does not complete release acceptance. Forest Run remains a feature-rich alpha until exact-head execution, physical acceptance, signing, delivery, visual, accessibility, and policy gates are complete.
