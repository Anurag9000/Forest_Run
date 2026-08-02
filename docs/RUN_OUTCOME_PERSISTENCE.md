# Forest Run — Terminal Run Persistence Contract

## Purpose

A terminal collision produces several permanent side effects:

- best-ghost publication;
- best-distance advancement;
- forest-mood history;
- return-moment history;
- the canonical last-run summary.

Those writes previously originated directly from `GameView`. That made terminal persistence difficult to test as one unit and left duplicate-delivery protection implicit in run-state timing.

`RunOutcomePersistenceCoordinator` is now the only coordinator-level owner for this terminal bundle.

## Ownership boundary

`GameView` remains responsible for presentation and gameplay sequencing:

1. resolve the terminal collision;
2. detach the completed ghost buffer in O(1);
3. identify the killer and record entity-specific relationship history when allowed;
4. build the completed `RunSummary` and authored rest quote;
5. invoke `runOutcomePersistence.commit(...)` once;
6. transition the run into `DYING`.

`GameView` must not directly call any of the following terminal write APIs:

```text
GhostPersistenceManager.saveBestRunAsync
SaveManager.saveBestDistance
ForestMoodSystem.recordRun
ReturnMomentsSystem.recordRunOutcome
SaveManager.saveLastRunSummary
```

`AndroidRunOutcomePersistenceSink` is the sole production adapter for those calls.

## Exactly-once token

Each coordinator instance owns one terminal token.

`commit(...)` performs these gates in order:

1. reject an already-consumed token with `ALREADY_COMMITTED`;
2. consume the token before any sink access;
3. return `NON_PERSISTENT_RUN` without writes when permanent progression is disabled;
4. otherwise perform the ordered persistence sequence.

Consuming the token before the run-mode gate is intentional. A deterministic or screenshot/profile run cannot later become persistable through a mode change after its terminal outcome was already observed.

The token is reopened only by:

- `GameView.prepareFreshRun()`;
- `GameView.prepareEncounterScenario()`.

A death/restart animation, Garden transition, Activity pause, or duplicate collision delivery does not reopen it.

## Ordered persistence sequence

For a persistent terminal outcome, the coordinator:

1. reads the current best distance;
2. normalizes non-finite or negative comparison values to zero;
3. attempts ghost publication only when the completed distance is strictly greater and the detached ghost is non-empty;
4. advances best distance only when ghost publication is accepted;
5. records forest mood;
6. records return-moment state;
7. stores the completed last-run summary.

This fixes the prior threshold mismatch where best distance could advance even if no valid ghost was accepted. A failed or empty ghost no longer blocks a later valid run from replacing stale playback data.

## Failure model

The coordinator is fail-closed against duplicate delivery. It claims the token before the first sink call, including re-entrant calls on the same monitor.

The storage bundle is not yet a cross-store transaction:

- summary, mood, return state, and best distance use SharedPreferences-backed paths;
- the ghost uses asynchronous `AtomicFile` persistence;
- a process failure between sink operations can leave a partially applied bundle.

The current design prioritizes preventing duplicate counters and duplicate summaries over retrying a partially completed in-process commit. Durable journaling or idempotent recovery keys remain future architectural work.

## Tests

`RunOutcomePersistenceCoordinatorTest` uses a recording sink to cover:

- canonical write order;
- exactly-once duplicate suppression;
- deterministic-run token consumption;
- run-reset reopening;
- non-best runs;
- rejected ghost publication;
- malformed completed distance.

`RunOutcomePersistenceIntegrationTest` exercises the production Android sink under Robolectric for:

- one complete real persistence bundle;
- asynchronous ghost completion;
- best-distance and ghost consistency;
- forest-mood and return-moment updates;
- last-summary round-trip;
- empty-ghost behavior;
- deterministic-run isolation.

`scripts/test_run_outcome_persistence_contract.py` enforces source ownership and ordering. It prevents direct terminal writes from returning to `GameView` and requires exactly one commit call and two run-start reset sites.

## Evidence boundary

Focused Kotlin compilation and executable coordinator checks were run locally, and the Python source-contract parser passed all six checks against a representative source fixture. The large `GameView` replacement was inspected as an exact four-hunk diff with no unrelated changes.

The new Robolectric tests have not been executed through an exact-head Android Gradle environment in this session. This contract does not substitute for exact-head unit, lint, release-build, connected-emulator, or physical-device evidence.
