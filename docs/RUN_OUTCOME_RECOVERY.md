# Forest Run — Terminal Outcome Recovery Protocol

## Goal

A terminal run updates independent persistence surfaces. Process death between writes must not:

- increment progression counters twice;
- lose the completed summary;
- advance best distance without a corresponding ghost;
- preserve a newer ghost behind an older threshold;
- silently overwrite unrelated live state;
- erase corrupt evidence and continue as though recovery succeeded.

Terminal persistence uses two purpose-specific protocols:

1. `RunOutcomePersistenceCoordinator` protects non-ghost progression with a synchronous before/after journal;
2. `GhostPromotionRecoveryCoordinator` protects ghost publication with a transient receipt and a persistent artifact manifest.

They are independently recoverable rather than one global transaction spanning every terminal side effect.

## Protected surfaces

### Non-ghost journal

Protects:

- `ForestMoodState`;
- `ReturnMomentState`;
- persisted `RunSummary`;
- KIND, MERCIFUL, or PEACEFUL route count.

### Ghost evidence

Protects:

- the validated ghost frame artifact;
- the distance associated with that exact artifact;
- the durable best-distance threshold.

Relationship history and authored presentation occur before these persistence owners and are not replayed by either protocol.

## Non-ghost journal

`RunOutcomeRecoveryRecord` stores:

```text
phase
raw completed summary
previous and expected forest mood
previous and expected return state
previous and expected route-tier count
```

`SharedPreferencesRunOutcomeRecoveryStore` writes it to:

```text
forest_run_outcome_recovery_<sanitized save namespace>
```

Schema version 2 includes route-counter snapshots. Unknown schemas, missing fields, wrong types, invalid enums, impossible bounds, or noncanonical expected states fail closed.

Raw malformed summary numerics are retained in the journal because final persistence applies the same normalization as `SaveManager.saveLastRunSummary(...)`.

## Initial terminal protocol

For a persistent terminal outcome:

```text
claim per-run token
→ reject unresolved older non-ghost recovery
→ read and derive non-ghost before/after states
→ synchronously journal PREPARED
→ evaluate durable-or-pending ghost distance floor
→ submit a distance-aware ghost candidate when eligible
→ ensure mood; checkpoint MOOD_APPLIED
→ ensure return; checkpoint RETURN_APPLIED
→ ensure atomic summary/route snapshot; checkpoint SUMMARY_APPLIED
→ clear non-ghost journal
```

The terminal coordinator does not write best distance. Ghost artifact and threshold durability belong to the asynchronous promotion worker.

## Non-ghost state comparison

Each state uses:

```text
actual == expected after-state  → already applied; continue
actual == recorded before-state → apply expected state and verify
otherwise                       → conflict; retain evidence and block
```

This recognizes a write that completed immediately before process death or before its checkpoint.

## Forest mood

Recovery precomputes the same transition as `ForestMoodSystem.recordRun(...)`:

- current mood;
- streak continue/reset;
- saturating total-run increment;
- one mood-family counter increment.

It writes the complete expected state rather than replaying the incrementing method.

## Return state

Recovery precomputes the same rough-run formula as `ReturnMomentsSystem.recordRunOutcome(...)`:

```text
FEARFUL mood
or at least two hits before 650 m
or a hit with zero kindness and fewer than four Seeds
```

The expected state carries the fixed completion time, unchanged Garden greeting day, and bounded rough-run streak.

## Summary and route count

`SaveManager.saveLastRunSummary(...)` also increments a route-tier counter, so replay is not idempotent.

`SharedPreferencesRunOutcomeSummarySnapshotStore` instead writes in one synchronous transaction:

- every canonical `last_run_*` field;
- the exact expected persistent route count.

`PacifistRouteTier.NONE` writes the summary without changing its compatibility counter.

Route counts use the same derived-counter ceiling as `SaveManager`:

```kotlin
Int.MAX_VALUE / 16
```

## Ghost binary compatibility

The frame payload remains `SaveManager` ghost file version 2. This recovery tranche does not alter that codec.

New promotions add two 24-byte AtomicFile sidecars scoped to the active ghost filename:

```text
<ghost>.promotion  transient in-progress receipt
<ghost>.manifest   persistent artifact-to-distance association
```

Both store:

```text
distance
frame count
64-bit raw-frame fingerprint
```

The fingerprint covers frame count and every persisted frame field. It is a local recovery identity, not a cryptographic authenticity mechanism.

## Ghost promotion sequence

The single worker performs:

```text
write transient receipt
→ write validated ghost
→ write persistent manifest
→ synchronously commit max(current best, candidate distance)
→ clear transient receipt
```

Best distance cannot advance unless receipt, ghost, and manifest are durable.

The manifest persists after receipt clearing, making future promoted ghost bundles self-describing without changing the frame codec.

`GhostPersistenceManager.bestDistanceFloor(...)` remains:

```text
max(durable best distance, accepted in-memory promotion distance)
```

A shorter candidate cannot queue behind a longer accepted promotion.

## Receipt recovery

A pending receipt always validates the durable ghost by frame count and fingerprint.

### Matching ghost

Recovery:

1. creates or repairs the persistent manifest;
2. repairs a lower best distance;
3. clears the receipt after durable completion.

Results:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching ghost

Recovery:

- clears the stale in-progress receipt;
- preserves the existing ghost and threshold;
- reconciles any older manifest.

A healthy older artifact yields `ABANDONED_UNWRITTEN_GHOST`. Corrupt older manifest evidence remains fail-closed.

## Manifest-only recovery

When no receipt remains:

### Already-applied fast path

If:

```text
best distance >= manifest distance
```

recovery returns `ALREADY_APPLIED` before loading or hashing the ghost. This avoids repeated full decoding of healthy long ghost files during ordinary startup.

### Repair path

If:

```text
best distance < manifest distance
```

recovery must load and validate the ghost, then compare frame count and fingerprint. Only a matching artifact may raise best distance.

Results:

- match and successful write: `REPAIRED_DISTANCE`;
- mismatch: `CORRUPT_MANIFEST`;
- failed read/write: `IO_FAILURE`.

Explicit maintenance inspection performs full identity validation even when the threshold is already applied.

## Ghost dispositions

`GhostPromotionRecoveryDisposition` includes:

- `EMPTY`;
- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`;
- `ABANDONED_UNWRITTEN_GHOST`;
- `CORRUPT_RECEIPT`;
- `CORRUPT_MANIFEST`;
- `IO_FAILURE`.

The last three block new promotions.

`RunOutcomeCommitResult.ghostPromoted` means accepted into the recoverable worker pipeline, not necessarily durable before terminal completion returns.

## Recovery triggers

### Non-ghost journal

Recovery runs:

- during `RunOutcomePersistenceCoordinator` construction;
- during `resetForNewRun()`.

### Ghost receipt and manifest

Recovery runs:

- during `AndroidRunOutcomePersistenceSink` construction;
- before a manager request when no worker is active;
- at the start of every worker task;
- before disk fallback in `loadLatest(...)`;
- through explicit cold-start maintenance.

## Corruption and conflicts

The non-ghost journal remains fail-closed for malformed schema, impossible transitions, third-state conflicts, failed verification, or failed clear.

Ghost recovery never raises best distance from:

- malformed receipt or manifest;
- invalid distance or frame count;
- a mismatched artifact;
- a failed synchronous threshold write.

Automatic recovery never deletes corrupt evidence merely to unblock progress.

## Explicit maintenance

`AndroidRecoveryEvidenceMaintenance` exposes:

```text
RUN_OUTCOME
GHOST_PROMOTION
```

with:

```text
inspect
recoverSafely
discardCorrupt(domain)
discardUnresolvedPending(domain)
```

Ghost-domain inspection distinguishes no evidence, valid manifest, pending receipt, corrupt receipt, corrupt manifest, and manifest/artifact mismatch.

Targeted ghost cleanup:

- preserves a valid manifest when only the receipt is corrupt;
- removes a corrupt or mismatched manifest without deleting the ghost frame file;
- never directly rewrites best distance;
- never opens the run-outcome journal.

Safe retry never clears corrupt evidence, and I/O failure never authorizes deletion.

Mutating maintenance commands require a debuggable cold start after save repair and before `GameView`. Reused live Activities are inspection-only.

See `docs/RECOVERY_EVIDENCE_MAINTENANCE.md`.

## Verification surface

Tests and source contracts cover:

- non-ghost journal transitions and atomic summary/route snapshots;
- receipt → ghost → manifest → distance → clear ordering;
- manifest codec corruption and namespace isolation;
- manifest-write and distance-write crash windows;
- receipt reconstruction of a missing/stale manifest;
- receipt-free manifest distance repair;
- no-ghost-load already-applied fast path;
- corrupt/mismatched manifest blocking;
- compatibility-overload manifest replacement;
- maintenance diagnosis and selective evidence removal;
- fixed-size codec structure, synchronous critical writes, and key parity.

Focused Kotlin compilation and executable harnesses passed for the non-ghost recovery owners, manifest-aware ghost coordinator, manager surface, maintenance adapter, and lazy-validation state machine.

Exact-head Gradle/JUnit, Robolectric, lint, build, emulator, physical-device, and ADB execution remain separate evidence gates.

## Remaining limitations

- Ghosts created before manifests remain load-compatible but cannot reconstruct a mismatch that already existed before this feature.
- The fingerprint is noncryptographic and theoretically collidable.
- The already-applied automatic path trusts the manifest/threshold pair and avoids repeated ghost hashing; explicit inspection validates the full identity.
- Non-ghost and ghost protocols are not one global transaction.
- Compatibility namespace switching during an active worker or maintenance instance remains unsupported.
- Remediation is debug/support tooling, not an end-user UI.
- Release builds reject maintenance intents.
- Physical-device ADB acceptance remains outstanding.
