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
- the canonical last-run summary.

Those responsibilities previously lived directly in one large `GameView` branch. They are now split into two explicit owners:

- `TerminalHitOutcomeCoordinator` owns behavior-preserving terminal-hit completion;
- `RunOutcomePersistenceCoordinator` owns the exactly-once persistent bundle.

## GameView boundary

`GameView` now remains responsible only for the immediate impact and run-state sequence:

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
record PersistentMemoryManager hit history
compose RunFlavorPresentation collision copy
spawn the terminal bubble or flavor line
resolve RestQuoteManager copy
call RunOutcomePersistenceCoordinator.commit
```

Those operations belong to the extracted terminal-hit seam.

## Terminal-hit completion ordering

`TerminalHitOutcomeCoordinator.complete(...)` preserves the authored ordering that existed before extraction:

1. when persistence is allowed and the killer is known, record relationship hit history;
2. present the canonical HIT dialogue bubble and floating flavor line;
3. invoke the supplied summary builder exactly once;
4. resolve the authored rest quote using the summary preview, biome, and killer;
5. copy that quote into one completed `RunSummary`;
6. invoke the exactly-once persistence committer;
7. return the completed summary and persistence result.

The summary builder remains a callback so `GameView` can use the authoritative live `GameStateManager` without making the extracted coordinator depend on the entire mutable game-state owner.

Production side effects are isolated behind:

- `AndroidTerminalHitRelationshipRecorder`;
- `AndroidTerminalHitFeedbackPresenter`;
- `AndroidTerminalHitRestQuoteResolver`;
- `RunOutcomeCommitter`.

Each has a fakeable interface used by pure ordering tests.

## Persistence ownership boundary

`GameView` must not directly call any of the following terminal write APIs:

```text
GhostPersistenceManager.saveBestRunAsync
SaveManager.saveBestDistance
ForestMoodSystem.recordRun
ReturnMomentsSystem.recordRunOutcome
SaveManager.saveLastRunSummary
RunOutcomePersistenceCoordinator.commit
```

`TerminalHitOutcomeCoordinator` invokes the `RunOutcomeCommitter` seam. `RunOutcomePersistenceCoordinator` implements that seam, and `AndroidRunOutcomePersistenceSink` is the sole production adapter for the underlying storage calls.

## Exactly-once token

Each persistence coordinator instance owns one terminal token.

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

For a persistent terminal outcome, the persistence coordinator:

1. reads the current best distance;
2. normalizes non-finite or negative comparison values to zero;
3. attempts ghost publication only when the completed distance is strictly greater and the detached ghost is non-empty;
4. advances best distance only when ghost publication is accepted;
5. records forest mood;
6. records return-moment state;
7. stores the completed last-run summary.

This fixes the prior threshold mismatch where best distance could advance even if no valid ghost was accepted. A failed or empty ghost no longer blocks a later valid run from replacing stale playback data.

## Failure model

The persistence coordinator is fail-closed against duplicate delivery. It claims the token before the first sink call, including re-entrant calls on the same monitor.

The storage bundle is not yet a cross-store transaction:

- summary, mood, return state, and best distance use SharedPreferences-backed paths;
- the ghost uses asynchronous `AtomicFile` persistence;
- a process failure between sink operations can leave a partially applied bundle.

The current design prioritizes preventing duplicate counters and duplicate summaries over retrying a partially completed in-process commit. Durable journaling or idempotent recovery keys remain future architectural work.

## Tests

`TerminalHitOutcomeCoordinatorTest` covers:

- relationship → feedback → summary → quote → persistence ordering;
- exactly one summary callback;
- deterministic/non-persistent relationship isolation;
- unknown-killer behavior;
- presentation identity pass-through;
- completed-summary and persistence-result propagation.

`TerminalHitFeedbackPresenterIntegrationTest` exercises the real Android presenter under Robolectric for:

- canonical authored HIT cue parity;
- one dialogue bubble and one flavor line;
- fail-closed nonfinite presentation anchors.

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

Source contracts enforce both layers:

- `test_terminal_hit_outcome_contract.py` locks the extracted completion order and keeps direct completion work out of the `GameView` HIT block;
- `test_run_outcome_persistence_contract.py` prevents direct terminal storage writes from returning to `GameView`, requires one terminal-hit completion call, and preserves the two run-start reset sites.

## Evidence boundary

Focused Kotlin compilation and executable ordering checks passed in this session. The first persistence extraction was inspected as an exact four-hunk `GameView` diff; the terminal-hit extraction was inspected as an exact three-hunk diff containing only the obsolete import removal, one coordinator construction, and one HIT-block delegation.

The new Robolectric tests have not been executed through an exact-head Android Gradle environment in this session. These contracts do not substitute for exact-head unit, lint, release-build, connected-emulator, or physical-device evidence.
