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

Those responsibilities previously lived directly in one large `GameView` branch. They are now split into explicit owners:

- `TerminalHitOutcomeCoordinator` owns behavior-preserving terminal-hit completion;
- `RunOutcomePersistenceCoordinator` owns the exactly-once terminal persistence token and recovery sequence;
- `SharedPreferencesRunOutcomeRecoveryStore` owns durable before/after recovery evidence;
- `SharedPreferencesRunOutcomeSummarySnapshotStore` owns the atomic summary-plus-route-counter snapshot.

## GameView boundary

`GameView` remains responsible only for the immediate impact and run-state sequence:

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
write terminal persistence stores
```

Those operations belong to the extracted terminal-hit and persistence seams.

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

`TerminalHitOutcomeCoordinator` invokes the `RunOutcomeCommitter` seam. `RunOutcomePersistenceCoordinator` implements that seam. `AndroidRunOutcomePersistenceSink` is the production adapter for legacy storage APIs and the recoverable state surfaces.

## Exactly-once token

Each persistence coordinator instance owns one terminal token.

`commit(...)` performs these gates in order:

1. reject an already-consumed token with `ALREADY_COMMITTED`;
2. consume the token before any sink access;
3. return `NON_PERSISTENT_RUN` without writes when permanent progression is disabled;
4. fail closed with `RECOVERY_BLOCKED` when older recovery evidence is corrupt or conflicts with live state;
5. otherwise perform the ordered persistence sequence.

Consuming the token before the run-mode gate is intentional. A deterministic or screenshot/profile run cannot later become persistable through a mode change after its terminal outcome was already observed.

The token is reopened only by:

- `GameView.prepareFreshRun()`;
- `GameView.prepareEncounterScenario()`.

Both paths also retry a pending recovery before accepting a new terminal outcome. A death/restart animation, Garden transition, Activity pause, or duplicate collision delivery does not reopen the token.

## Durable recovery record

Before any ghost or progression write, a recoverable production sink synchronously records one schema-versioned journal entry containing:

- the completed raw `RunSummary`;
- previous and expected forest-mood state;
- previous and expected return-moment state;
- previous and expected pacifist-route count;
- the latest recovery checkpoint.

The journal uses a save-namespace-specific SharedPreferences file and synchronous `commit()` operations. Compatibility preference namespaces therefore receive isolated recovery evidence rather than sharing the primary save's journal.

The stored raw summary preserves malformed numeric values such as negative counters or nonfinite distance. The final summary snapshot applies the same sanitization as `SaveManager.saveLastRunSummary`. Missing keys, oversized quotes, invalid enums, wrong preference types, impossible return bounds, and after-states that do not match the canonical transition formulas are treated as corrupt evidence.

Recovery phases are:

```text
PREPARED
MOOD_APPLIED
RETURN_APPLIED
SUMMARY_APPLIED
```

A checkpoint may lag behind a state write. Recovery therefore does not trust the phase alone.

## State-comparison recovery

For each recoverable state surface, the coordinator compares live state against both journal snapshots:

```text
actual == expected after-state  → already applied; continue
actual == recorded before-state → apply expected after-state; verify
otherwise                       → conflict; retain evidence and block
```

This closes the crash window where a write succeeds but the following checkpoint does not. A restarted coordinator recognizes the already-applied state rather than incrementing it a second time.

The same model covers:

- forest mood and all mood counters;
- return timestamp and rough-run streak;
- the completed summary and pacifist-route counter.

## Atomic summary and route snapshot

`SaveManager.saveLastRunSummary` is not a pure overwrite: it also increments the pacifist-route run counter. Replaying that API during recovery could therefore double-count a route even when the summary text itself was harmless to overwrite.

Production recovery instead uses `SharedPreferencesRunOutcomeSummarySnapshotStore`, which:

1. sanitizes the summary exactly once;
2. writes every `last_run_*` field;
3. writes the expected KIND, MERCIFUL, or PEACEFUL route count;
4. commits the summary and route count in one synchronous SharedPreferences transaction.

`PacifistRouteTier.NONE` writes the summary but deliberately does not mutate its compatibility counter. Replaying an already-applied atomic snapshot leaves the route count unchanged.

## Ordered persistence sequence

For a persistent terminal outcome, the production coordinator performs:

1. read mood, return, and route-count before-states;
2. compute all expected after-states;
3. synchronously write the `PREPARED` journal before any other persistence side effect;
4. normalize best-distance comparison values;
5. attempt ghost publication only when the run is strictly better and the detached ghost is non-empty;
6. advance best distance only when ghost publication is accepted;
7. compare/apply/verify forest mood, then checkpoint `MOOD_APPLIED`;
8. compare/apply/verify return state, then checkpoint `RETURN_APPLIED`;
9. compare/apply/verify the atomic summary-plus-route snapshot, then checkpoint `SUMMARY_APPLIED`;
10. synchronously clear the journal.

If the final clear fails, the result is `RECOVERY_PENDING`. A later coordinator construction or run reset replays the evidence and recognizes the already-applied states without counting them twice.

Nonrecoverable test or alternate sinks retain the original direct sequence:

```text
forest mood → return moment → SaveManager last summary
```

## Ghost and best-distance boundary

The non-ghost progression bundle is now crash recoverable. Ghost publication is intentionally outside that replayable bundle because the detached frame list is not stored in the journal.

Consequences:

- a crash after the journal but before mood/return/summary is recoverable;
- a crash after any non-ghost state write is recognized through before/after comparison;
- a crash after summary-plus-route commit but before journal clear is idempotently recoverable;
- a crash after ghost acceptance but before best-distance advancement may leave a newer ghost with an older threshold;
- a crash after best-distance advancement cannot reconstruct or republish missing ghost frames from the journal.

A durable ghost-publication identifier or recoverable frame reference remains future work. The coordinator does not claim a cross-store transaction spanning SharedPreferences and asynchronous `AtomicFile` ghost persistence.

## Failure model

The persistence coordinator is fail-closed against:

- duplicate and re-entrant terminal delivery;
- corrupt journal schemas or preference types;
- missing journal fields;
- tampered or impossible expected after-states;
- live state that matches neither the recorded before-state nor expected after-state;
- failed initial journal creation;
- failed state verification;
- failed final journal clear.

Corrupt or conflicting evidence is retained for diagnosis rather than silently erased. New permanent terminal writes remain blocked until recovery succeeds or the evidence is deliberately repaired by a future migration/tooling path.

## Tests

`TerminalHitOutcomeCoordinatorTest` covers terminal completion ordering and identity.

`RunOutcomePersistenceCoordinatorTest` retains compatibility coverage for the original nonrecoverable sink:

- canonical write order;
- duplicate suppression;
- deterministic-run token consumption;
- run-reset reopening;
- ghost promotion and best-distance rules.

`RunOutcomeRecoveryCoordinatorTest` covers:

- journal creation before ghost evaluation;
- mood/return/summary-route ordering;
- startup recognition of a write that preceded its checkpoint;
- recognition of an already-applied atomic summary snapshot;
- conflicting mood and route-count blocking;
- corrupt evidence blocking;
- failed-clear retry without duplicate counters;
- exact rough-run recovery.

`RunOutcomeRecoveryStoreTest` covers:

- complete schema-v2 round-trip;
- empty and cleared stores;
- raw malformed numeric preservation;
- missing fields, invalid schema/enums/types, and negative route counts;
- deterministic transition consistency;
- evidence preservation after rejected replacement.

`RunOutcomeSummarySnapshotStoreTest` covers:

- sanitization parity;
- summary/route atomic writes;
- idempotent replay;
- all persistent route tiers;
- NONE route behavior;
- saturated route counts.

`RunOutcomeRecoveryTransitionIntegrationTest` checks the journal's pure mood, return, and route transitions against canonical production systems.

`RunOutcomeRecoveryIntegrationTest` exercises production SharedPreferences recovery for:

- a mood write that completed before its checkpoint;
- an already-applied summary and route snapshot;
- conflict retention and blocked new writes.

Source contracts enforce ownership, ordering, codec completeness, synchronous writes, transition formulas, summary sanitization, and loaded-record consistency.

## Evidence boundary

Performed in this implementation tranche:

- focused Kotlin compilation of the initial journal/coordinator surface against Android and engine stubs;
- compiler-driven correction of the journal load control flow;
- executable recovery harness covering ordinary commit, already-applied state recognition, conflict blocking, failed-clear retry, and legacy sink compatibility;
- route-aware executable harness covering summary sanitization, route saturation/NONE behavior, atomic replay recognition, and route conflict blocking;
- exact key comparison against `SaveManager` summary and route preference constants;
- source-contract parser corrections for expression-bodied Kotlin functions.

The checked-in JUnit and Robolectric tests were not executed through an exact-head Android Gradle environment in this session. No exact-head unit, lint, release-build, packaging, connected-emulator, or physical-device result is claimed.
