# Forest Run — Terminal Outcome Recovery Protocol

## Goal

A terminal run updates several independent persistence surfaces. Process death between those writes must not:

- increment forest-mood counters twice;
- increment rough-run streak twice;
- increment a pacifist-route count twice;
- lose the canonical completed summary;
- silently overwrite unrelated live state;
- erase corrupt evidence and continue as though recovery succeeded.

`RunOutcomePersistenceCoordinator` now protects the non-ghost terminal bundle with a synchronous before/after journal and state-comparison recovery.

## Protected state surfaces

The recoverable bundle contains:

1. `ForestMoodState`;
2. `ReturnMomentState`;
3. the completed persisted `RunSummary`;
4. the summary's KIND, MERCIFUL, or PEACEFUL route counter.

Ghost frames and best-distance publication remain outside the replayable state bundle.

## Journal schema

`RunOutcomeRecoveryRecord` stores:

```text
phase
raw completed summary
previous forest mood
expected forest mood
previous return state
expected return state
previous route-tier count
expected route-tier count
```

The journal is written by `SharedPreferencesRunOutcomeRecoveryStore` into:

```text
forest_run_outcome_recovery_<sanitized save namespace>
```

This namespace binding matters because `SaveManager` may switch to compatibility preferences for a newer save schema. Recovery evidence must follow the same save namespace.

Schema version 2 includes the route-counter snapshots. Older or unknown journal schemas fail closed as corrupt evidence.

## Journal validity

A record is valid only when:

- every serialized summary key is present;
- enum values decode;
- preference value types match;
- the quote is no longer than 8,192 characters;
- mood counters are non-negative;
- return timestamps and counters satisfy save bounds;
- previous route count is non-negative;
- expected mood equals the canonical mood transition;
- expected return state equals the canonical return transition using the recorded completion time;
- expected route count equals the canonical route transition.

Raw summary numeric fields are intentionally not required to be non-negative or finite. The previous production path sanitized those values at final summary persistence, so the journal preserves them and `persistedSummary(...)` applies the same normalization later.

## Initial commit protocol

For a persistent terminal outcome:

```text
claim per-run token
→ reject unresolved older recovery
→ read mood/return/route before-states
→ compute expected after-states
→ synchronously journal PREPARED
→ evaluate/publish best ghost
→ advance best distance only after accepted ghost
→ ensure mood after-state
→ journal MOOD_APPLIED
→ ensure return after-state
→ journal RETURN_APPLIED
→ ensure atomic summary/route snapshot
→ journal SUMMARY_APPLIED
→ synchronously clear journal
```

The PREPARED record precedes ghost evaluation so every later non-ghost crash window has durable evidence.

## State comparison

Each recovery step implements the same three-way decision:

```text
actual == expected after-state
    already applied; do not replay

actual == recorded before-state
    apply expected after-state and verify

otherwise
    conflict; retain journal and block new permanent writes
```

This approach handles a write that succeeded immediately before process death or before its checkpoint write.

## Forest mood

The journal precomputes the same transition as `ForestMoodSystem.recordRun`:

- update current mood;
- continue or reset mood streak;
- saturating increment total runs;
- saturating increment exactly one mood-family counter.

Recovery writes the complete expected `ForestMoodState` rather than invoking the incrementing production method again.

## Return state

The journal precomputes the same rough-run formula as `ReturnMomentsSystem.recordRunOutcome`:

```text
FEARFUL mood
or at least two hits before 650 m
or a hit with zero kindness and fewer than four Seeds
```

The expected state carries:

- the fixed terminal completion time;
- the unchanged last Garden greeting day;
- saturated rough-run increment or zero reset.

Recovery writes the complete expected state and verifies it.

## Summary and route count

`SaveManager.saveLastRunSummary` combines two behaviors:

1. overwrite the canonical last-run summary;
2. increment the summary's route-tier counter.

Calling it during recovery is unsafe because overwriting the summary is idempotent but incrementing the route count is not.

`SharedPreferencesRunOutcomeSummarySnapshotStore` replaces that recovery step. In one synchronous SharedPreferences transaction it writes:

- all canonical `last_run_*` keys;
- the exact expected route counter for KIND, MERCIFUL, or PEACEFUL.

It does not increment from live state. It writes the journal's expected value.

For `PacifistRouteTier.NONE`, it writes the summary and leaves the compatibility `route_none_runs` key unchanged.

## Summary sanitization

`RunOutcomeRecoveryTransitions.persistedSummary(...)` matches `SaveManager.saveLastRunSummary` normalization:

- negative integer counters become zero;
- nonfinite or negative distance becomes zero;
- identity, quote, mood, route tier, and new-high-score flag remain unchanged.

The recovery comparison uses this persisted image rather than the raw journal summary.

## Recovery triggers

Recovery runs:

- when `RunOutcomePersistenceCoordinator` is constructed;
- when `resetForNewRun()` reopens the terminal token.

A successful recovery clears the journal. A failed clear returns `RECOVERY_PENDING`; the complete after-states remain recognizable on the next attempt.

## Dispositions

`RunOutcomeCommitDisposition` includes:

- `COMMITTED` — terminal bundle completed and journal cleared;
- `NON_PERSISTENT_RUN` — token consumed without permanent writes;
- `ALREADY_COMMITTED` — duplicate terminal delivery rejected;
- `RECOVERY_PENDING` — states completed or partially completed but durable evidence remains;
- `RECOVERY_BLOCKED` — corrupt or conflicting older evidence prevents new writes.

## Corruption and conflicts

The journal is never silently cleared when:

- its schema is unknown;
- a key is missing;
- a preference has the wrong type;
- an enum is invalid;
- a stored after-state does not match the canonical transition;
- live state matches neither the before-state nor after-state;
- a required write or verification fails.

This is fail-closed by design. Automated evidence repair or user-facing recovery tooling is not yet implemented.

## Ghost boundary

The journal does not store detached ghost frames or a durable ghost-publication identifier.

Therefore:

- the non-ghost bundle can be recovered after process death;
- an already-published ghost is not published twice by recovery;
- a crash between ghost publication and best-distance write can leave ghost data ahead of its threshold;
- a crash after best-distance write cannot recreate missing ghost frames.

Future work may journal a stable ghost artifact reference or move best-distance promotion into a recoverable ghost transaction.

## Verification surface

Pure coordinator tests cover normal commit, each already-applied state, route conflicts, corrupt evidence, failed clear, retry, and legacy nonrecoverable sinks.

Robolectric tests cover:

- journal codec round-trip and corruption;
- raw malformed summary preservation;
- transition consistency rejection;
- summary/route snapshot sanitization and idempotency;
- production startup recovery;
- production conflict retention;
- parity with canonical mood/return/route behavior.

Python source contracts lock:

- journal-before-ghost ordering;
- phase ordering;
- state comparison before replay;
- summary/route atomic ownership;
- synchronous journal/snapshot writes;
- required key coverage;
- transition formulas and record validation.

These tests are checked in, but exact-head Android Gradle and device execution remain separate evidence gates.
