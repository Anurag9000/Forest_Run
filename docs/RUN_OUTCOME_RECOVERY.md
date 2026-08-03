# Forest Run — Terminal Outcome Recovery Protocol

## Goal

A terminal run updates independent persistence surfaces. Process death between writes must not:

- increment progression counters twice;
- lose the completed summary;
- advance best distance without a corresponding ghost;
- preserve a newer ghost behind an older threshold;
- silently overwrite unrelated live state;
- accept altered ghost distance or frame identity;
- erase corrupt evidence and continue as though recovery succeeded.

Terminal persistence uses two purpose-specific protocols:

1. `RunOutcomePersistenceCoordinator` protects non-ghost progression with a synchronous before/after journal;
2. `GhostPromotionRecoveryCoordinator` protects ghost publication with a transient receipt and persistent artifact manifest.

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
- the accepted distance associated with that artifact;
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

Raw malformed summary numerics are retained because final persistence applies the same normalization as `SaveManager.saveLastRunSummary(...)`.

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

This recognizes a write completed immediately before process death or before its checkpoint.

## Forest mood and return state

Mood recovery precomputes the same bounded transition as `ForestMoodSystem.recordRun(...)` and writes the complete expected state rather than replaying increments.

Return recovery precomputes the same rough-run formula as `ReturnMomentsSystem.recordRunOutcome(...)`:

```text
FEARFUL mood
or at least two hits before 650 m
or a hit with zero kindness and fewer than four Seeds
```

The expected state carries the fixed completion time, unchanged Garden greeting day, and bounded rough-run streak.

## Summary and route count

`SaveManager.saveLastRunSummary(...)` also increments a route-tier counter, so replay is not idempotent.

`SharedPreferencesRunOutcomeSummarySnapshotStore` writes in one synchronous transaction:

- every canonical `last_run_*` field;
- the exact expected persistent route count.

`PacifistRouteTier.NONE` writes the summary without changing its compatibility counter.

Route counts use the same ceiling as `SaveManager`:

```kotlin
Int.MAX_VALUE / 16
```

## Ghost frame compatibility

The frame payload remains `SaveManager` ghost format version 2. No frame codec or `PlayerState` ordinal migration is introduced.

The active ghost filename scopes two sidecars:

```text
<ghost>.promotion  transient receipt
<ghost>.manifest   persistent artifact-to-distance association
```

## Sidecar versions

### Version 1 compatibility

Existing 24-byte records remain readable:

```text
distance
frame count
64-bit FNV frame fingerprint
```

The historical fingerprint covers frame count and all persisted frame fields but does not cryptographically bind distance.

### Version 2 current writes

All new sidecars are 56 bytes and add a 32-byte SHA-256 digest. The digest covers a canonical big-endian stream containing:

```text
accepted distance raw bits
frame count
every persisted frame field raw bits/ordinal
```

Version-2 validation requires both FNV and SHA-256 to match. Changing only the stored distance invalidates the strong identity.

Current sidecar `save(...)` methods reject digest-less values. Version 1 can only be loaded from existing bytes.

SHA-256 is collision-resistant identity, not authenticity. No MAC, secret key, certificate, or signature is used.

## Ghost promotion sequence

The single worker performs:

```text
write version-2 transient receipt
→ write validated ghost
→ write version-2 persistent manifest
→ synchronously commit max(current best, candidate distance)
→ clear transient receipt
```

Best distance cannot advance unless receipt, ghost, and strong manifest are durable.

The manifest persists after receipt clearing, making new ghost bundles self-describing without changing the frame codec.

`GhostPersistenceManager.bestDistanceFloor(...)` remains:

```text
max(durable best distance, accepted in-memory promotion distance)
```

A shorter candidate cannot queue behind a longer accepted promotion.

The in-memory publication also carries distance, FNV, and SHA-256. Failure cleanup compares all three identity dimensions.

## Receipt recovery

A pending receipt always loads and validates the durable ghost.

### Version-2 receipt

Recovery verifies frame structure, count, FNV, and SHA-256 over receipt distance plus frame payload. A modified distance or digest cannot authorize threshold advancement.

### Version-1 receipt

Recovery validates the legacy FNV frame identity, computes SHA-256 using the stored receipt distance, and writes a version-2 manifest before best distance changes.

### Matching ghost

Recovery:

1. creates, repairs, or upgrades the persistent manifest;
2. repairs lower best distance;
3. clears the receipt after durable completion.

Results:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching ghost

Recovery:

- clears the stale receipt;
- preserves existing ghost and threshold;
- validates any older manifest using the already-loaded ghost.

A healthy older artifact yields `ABANDONED_UNWRITTEN_GHOST`. Corrupt or mismatched older evidence remains fail-closed.

## Manifest-only recovery

### Already-applied fast path

If:

```text
best distance >= manifest distance
```

automatic recovery returns `ALREADY_APPLIED` without loading or hashing the ghost. This avoids repeated full decoding of healthy long ghost files.

A healthy already-applied version-1 manifest may remain legacy until validation is required.

### Repair path

If:

```text
best distance < manifest distance
```

recovery loads the ghost and validates the association.

For version 2, SHA-256 binds manifest distance and frame payload. For version 1, recovery validates FNV and writes a version-2 manifest before threshold repair.

Results:

- valid association and successful writes: `REPAIRED_DISTANCE`;
- frame, digest, count, or distance mismatch: `CORRUPT_MANIFEST`;
- failed read/write: `IO_FAILURE`.

Explicit maintenance performs full identity validation even when the threshold is already applied.

## Ghost dispositions

`GhostPromotionRecoveryDisposition` includes:

```text
EMPTY
REPAIRED_DISTANCE
ALREADY_APPLIED
ABANDONED_UNWRITTEN_GHOST
CORRUPT_RECEIPT
CORRUPT_MANIFEST
IO_FAILURE
```

The last three block new promotions.

`RunOutcomeCommitResult.ghostPromoted` means accepted into the recoverable worker pipeline, not necessarily durable before terminal completion returns.

## Recovery triggers

Non-ghost journal recovery runs during coordinator construction and `resetForNewRun()`.

Ghost recovery runs:

- during `AndroidRunOutcomePersistenceSink` construction;
- before a manager request when no worker is active;
- at the start of every worker task;
- before disk fallback in `loadLatest(...)`;
- through explicit cold-start maintenance.

Disk fallback calculates its in-memory SHA-256 identity using the loaded best distance and loaded frames.

## Corruption and conflicts

The non-ghost journal remains fail-closed for malformed schema, impossible transitions, third-state conflicts, failed verification, or failed clear.

Ghost recovery never raises best distance from:

- malformed sidecar schema or length;
- invalid distance or frame count;
- noncanonical digest text;
- FNV mismatch;
- SHA-256 mismatch;
- altered version-2 distance;
- failed strong-manifest upgrade;
- failed synchronous threshold write.

Automatic recovery never deletes corrupt evidence merely to unblock progress.

## Explicit maintenance

`AndroidRecoveryEvidenceMaintenance` exposes `RUN_OUTCOME` and `GHOST_PROMOTION` with:

```text
inspect
recoverSafely
discardCorrupt(domain)
discardUnresolvedPending(domain)
```

Ghost inspection passes manifest distance, count, FNV, and optional SHA-256 to `GhostRunIdentity.matches(...)`.

Targeted ghost cleanup:

- preserves a valid manifest when only the receipt is corrupt;
- removes a corrupt or mismatched manifest without deleting the ghost file;
- never directly rewrites best distance;
- never opens the run-outcome journal.

Safe retry never clears corrupt evidence, and I/O failure never authorizes deletion.

Mutating commands require a debuggable cold start after save repair and before `GameView`. Reused live Activities are inspection-only.

## Verification surface

Tests and contracts cover:

- non-ghost transitions and atomic summary/route snapshots;
- independently verified SHA-256 golden vector;
- v1 24-byte read compatibility and v2 56-byte writes;
- digest-less new-write rejection;
- distance and every persisted frame-field sensitivity;
- receipt → ghost → strong manifest → distance → clear ordering;
- legacy receipt/manifest upgrade before repair;
- receipt-distance and manifest-distance tampering;
- receipt reconstruction of missing/stale manifest;
- receipt-free repair;
- no-ghost-load already-applied fast path;
- maintenance full validation and selective removal;
- synchronous critical writes and preference-key parity.

Focused Kotlin compilation passed for identity, codecs, recovery, and manager surfaces. Executable golden-vector, codec, and recovery-state-machine harnesses passed.

Exact-head Gradle/JUnit, Robolectric, lint, build, emulator, physical-device, and ADB execution remain separate evidence gates.

## Remaining limitations

- Ghosts and mismatches predating persistent manifests cannot be reconstructed retroactively.
- Version-1 evidence remains weak until recovery needs to validate and upgrade it.
- The already-applied automatic path intentionally avoids repeated hashing; explicit inspection validates fully.
- SHA-256 is not proof of trusted authorship against a malicious filesystem writer.
- Non-ghost and ghost protocols are not one global transaction.
- Compatibility namespace switching during an active worker or maintenance instance remains unsupported.
- Remediation is debug/support tooling, not an end-user UI.
- Physical-device ADB acceptance remains outstanding.
