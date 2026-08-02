# Forest Run — Terminal Outcome Recovery Protocol

## Goal

A terminal run updates several independent persistence surfaces. Process death between those writes must not:

- increment forest-mood counters twice;
- increment rough-run streak twice;
- increment a pacifist-route count twice;
- lose the canonical completed summary;
- advance best distance without the corresponding ghost;
- preserve a newer ghost behind an older threshold;
- silently overwrite unrelated live state;
- erase corrupt evidence and continue as though recovery succeeded.

Terminal persistence now uses two purpose-specific recovery protocols:

1. `RunOutcomePersistenceCoordinator` protects non-ghost progression with a synchronous before/after journal;
2. `GhostPromotionRecoveryCoordinator` protects ghost and best-distance promotion with an AtomicFile receipt and artifact fingerprint.

The protocols are independently recoverable. They are not one global transaction spanning every terminal side effect.

## Protected state surfaces

### Non-ghost outcome journal

Protects:

1. `ForestMoodState`;
2. `ReturnMomentState`;
3. the completed persisted `RunSummary`;
4. the summary's KIND, MERCIFUL, or PEACEFUL route counter.

### Ghost promotion receipt

Protects:

1. the validated durable ghost frame artifact;
2. the best-distance threshold associated with an accepted promotion.

Relationship history and authored presentation occur before these persistence owners and are not replayed by either recovery protocol.

## Non-ghost journal schema

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

Schema version 2 includes route-counter snapshots. Older or unknown journal schemas fail closed as corrupt evidence.

## Non-ghost journal validity

A record is valid only when:

- every serialized summary key is present;
- enum values decode;
- preference value types match;
- the quote is no longer than 8,192 characters;
- mood counters are non-negative;
- return timestamps and counters satisfy save bounds;
- previous route count is within the canonical derived-counter range;
- expected mood equals the canonical mood transition;
- expected return state equals the canonical return transition using the recorded completion time;
- expected route count equals the canonical bounded route transition.

Raw summary numeric fields are intentionally not required to be non-negative or finite. The previous production path sanitized those values at final summary persistence, so the journal preserves them and `persistedSummary(...)` applies the same normalization later.

## Initial terminal protocol

For a persistent terminal outcome:

```text
claim per-run token
→ reject unresolved older non-ghost recovery
→ read mood/return/route before-states
→ compute expected after-states
→ synchronously journal PREPARED
→ evaluate best distance against durable-or-pending ghost floor
→ submit distance-aware ghost promotion when eligible
→ ensure mood after-state
→ journal MOOD_APPLIED
→ ensure return after-state
→ journal RETURN_APPLIED
→ ensure atomic summary/route snapshot
→ journal SUMMARY_APPLIED
→ synchronously clear non-ghost journal
```

The PREPARED journal precedes ghost evaluation so every later non-ghost crash window has durable evidence.

The terminal coordinator does not write best distance. Ghost and threshold durability belong to the asynchronous promotion worker.

## Non-ghost state comparison

Each non-ghost recovery step implements the same three-way decision:

```text
actual == expected after-state
    already applied; do not replay

actual == recorded before-state
    apply expected after-state and verify

otherwise
    conflict; retain journal and block new permanent writes
```

This handles a write that succeeded immediately before process death or before its checkpoint write.

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

## Ghost promotion receipt

`GhostPromotionReceipt` stores:

```text
target distance
frame count
64-bit frame fingerprint
```

`AtomicFileGhostPromotionReceiptStore` persists it as a versioned, fixed-size 24-byte sidecar:

```text
<active ghost filename>.promotion
```

The fingerprint covers the raw persisted bits of every frame field and the frame count. It identifies the local durable artifact expected by the receipt; it is not a cryptographic authenticity mechanism.

## Ghost promotion sequence

An accepted promotion is immediately published in memory, then one daemon worker performs:

```text
write AtomicFile receipt
→ write AtomicFile ghost
→ synchronously commit max(current best, candidate distance)
→ clear receipt
```

The best-distance write occurs only after the ghost write succeeds. Receipt clearing occurs only after the threshold is durable.

`GhostPersistenceManager.bestDistanceFloor(...)` returns the maximum of:

- durable best distance;
- accepted in-memory promotion distance.

Both the terminal coordinator and the manager itself use this floor. A shorter direct or terminal candidate cannot queue behind a longer pending promotion.

## Ghost recovery decisions

Recovery loads the durable ghost and compares its frame count and fingerprint with the receipt.

### Matching ghost

```text
current best < receipt distance
    synchronously repair best distance

current best >= receipt distance
    threshold is already applied or superseded

then clear receipt
```

Results:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching ghost

A mismatch means the candidate represented by the receipt did not become the durable ghost. Recovery:

- does not change best distance;
- does not modify the existing ghost;
- clears only the stale uncommitted receipt.

Result:

- `ABANDONED_UNWRITTEN_GHOST`.

### Corrupt or inaccessible receipt

Results:

- `CORRUPT_RECEIPT`;
- `IO_FAILURE`.

These block new ghost promotions but do not prevent the separate non-ghost terminal bundle from completing.

## Recovery triggers

### Non-ghost journal

Recovery runs:

- when `RunOutcomePersistenceCoordinator` is constructed;
- when `resetForNewRun()` reopens the terminal token.

### Ghost receipt

Recovery runs:

- when `AndroidRunOutcomePersistenceSink` is created;
- before a new manager request when no worker is active;
- at the beginning of each worker task;
- before `loadLatest(...)` falls back to disk.

A successful recovery clears its own evidence. A failed clear leaves an idempotently recognizable record for the next attempt.

## Commit dispositions

`RunOutcomeCommitDisposition` includes:

- `COMMITTED` — non-ghost terminal bundle completed and its journal cleared;
- `NON_PERSISTENT_RUN` — token consumed without permanent writes;
- `ALREADY_COMMITTED` — duplicate terminal delivery rejected;
- `RECOVERY_PENDING` — non-ghost states completed or partially completed but evidence remains;
- `RECOVERY_BLOCKED` — corrupt or conflicting non-ghost evidence prevents new permanent terminal writes.

`ghostPromoted` means a candidate was accepted into the recoverable worker pipeline. It does not assert worker completion before the terminal commit returns.

Ghost recovery uses its own `GhostPromotionRecoveryDisposition` values because its evidence and retry lifecycle are independent.

## Corruption and conflicts

The non-ghost journal is never silently cleared when:

- its schema is unknown;
- a key is missing;
- a preference has the wrong type;
- an enum is invalid;
- a stored after-state does not match the canonical transition;
- live state matches neither the before-state nor after-state;
- a required write or verification fails.

A ghost receipt is never used to advance best distance when:

- its schema or size is invalid;
- distance or frame count is invalid;
- the durable ghost count differs;
- the durable ghost fingerprint differs;
- threshold persistence fails.

Automatic recovery remains fail-closed. Corrupt or conflicting evidence is retained unless a valid ghost receipt proves that its candidate ghost never landed.

## Explicit inspection and remediation

`AndroidRecoveryEvidenceMaintenance` exposes deliberate support/debug operations for the two independent domains:

```text
RUN_OUTCOME
GHOST_PROMOTION
```

Evidence states:

```text
CLEAN
PENDING
CORRUPT
BLOCKED
IO_FAILURE
```

Operations:

```text
inspect
recoverSafely
discardCorrupt(domain)
discardUnresolvedPending(domain)
```

Safety rules:

- safe recovery never clears `CORRUPT` evidence;
- corrupt discard requires a fresh confirmed `CORRUPT` state;
- pending discard retries canonical recovery before deletion;
- successful retry returns `RECOVERED_INSTEAD` and performs no destructive clear;
- `IO_FAILURE` never authorizes evidence deletion;
- each domain uses an isolated production handler;
- support summaries contain only state and fixed detail codes, never run or frame payloads;
- deletion verifies the selected domain reports `CLEAN` afterward.

The run-outcome maintenance sink does not publish ghosts or advance best distance. The ghost maintenance handler does not open the run-outcome journal.

### Debug-only command surface

`MainActivity` accepts:

```text
recovery_action = inspect | recover | discard_corrupt | discard_pending
recovery_domain = RUN_OUTCOME | GHOST_PROMOTION
```

The app must be debuggable. Commands are removed from the Intent in `finally` so Activity recreation cannot repeat them.

Because recovery and discard mutate durable evidence, they are accepted only during cold `onCreate`:

```text
SaveIntegrityManager.repair
→ maintenance command
→ GameView construction
```

A reused `singleTask` Activity accepts `inspect` only. `recover` and both discard commands return `reason=active_session` before constructing maintenance, preventing races with active gameplay or the ghost worker.

Detailed ADB examples and evidence-deletion semantics are in `docs/RECOVERY_EVIDENCE_MAINTENANCE.md`.

## Verification surface

Pure non-ghost tests cover normal commit, each already-applied state, route conflicts, corrupt evidence, failed clear, retry, and legacy nonrecoverable sinks.

Pure ghost tests cover:

- receipt → ghost → distance → clear ordering;
- matching repair and already-applied recognition;
- mismatched-ghost abandonment;
- failed ghost and distance writes;
- corrupt receipt blocking;
- full frame fingerprint sensitivity.

Maintenance policy tests cover:

- exact domain-handler cardinality;
- independent inspection and retry;
- corrupt-only deletion;
- recover-before-discard ordering;
- deliberate blocked-journal removal;
- no deletion after read failure;
- clear failure visibility;
- payload-free support summaries.

Robolectric tests cover:

- non-ghost journal codec and transition parity;
- summary/route snapshot sanitization and idempotency;
- production non-ghost startup recovery;
- promotion receipt codec;
- immediate ghost playback;
- durable ghost and distance completion;
- startup distance repair;
- mismatch abandonment;
- pending-distance admission;
- Android-backed maintenance inspection, recovery, conflict preservation, and domain-specific discard.

Python source contracts lock both recovery owners, ordering, fixed schemas, synchronous critical writes, fingerprint coverage, preference-key parity, stale-candidate admission, exact state comparisons, maintenance operation separation, no-delete-on-I/O, debug-only access, cold-start mutation, one-shot extras, and payload-free logging.

Focused Kotlin compilation and executable harnesses passed for the maintenance policy, production-shaped handlers, and cold/live command routing.

The checked-in Android tests were not executed through an exact-head Gradle environment in this implementation session. Focused evidence does not replace exact-head unit, lint, build, emulator, or physical-device gates.

## Remaining limitations

- Legacy ghost/distance mismatches created before receipt support cannot be reconstructed because the ghost file does not encode distance.
- The ghost fingerprint is noncryptographic and has a theoretical collision risk.
- Non-ghost and ghost evidence are not one global atomic transaction.
- Concurrent switching of compatibility save namespaces during an active worker or maintenance instance remains unsupported.
- Remediation is debug/support tooling, not an end-user recovery UI.
- Release builds intentionally reject maintenance intents.
- Physical-device ADB acceptance remains outstanding.
