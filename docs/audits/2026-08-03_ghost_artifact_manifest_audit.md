# Ghost Artifact Manifest Audit — 2026-08-03

## Scope

This tranche closes the remaining post-receipt ghost/distance recovery gap for newly promoted ghosts without changing the mature version-2 ghost frame binary format.

Before this change, the transient promotion receipt was cleared after ghost and best-distance durability. Once cleared, no durable file associated the surviving ghost artifact with the distance that produced it. A later threshold loss or repair could not be resolved from the ghost alone.

The implementation adds a persistent, fingerprint-bound artifact manifest.

## Non-goals

This tranche does not:

- change `SaveManager` ghost format version 2;
- rewrite or migrate existing ghost frame payloads;
- alter `GameView`, collision behavior, Player mechanics, rendering, input, Bloom, Garden, audio, haptics, or authored copy;
- create a global transaction spanning relationship history, non-ghost progression, ghost storage, and best distance;
- claim exact-head Android Gradle or physical-device evidence.

## New durable artifact

Added:

```text
app/src/main/java/com/anurag9000/forestrun/systems/GhostArtifactManifest.kt
```

`GhostArtifactManifest` stores:

```text
distanceM
frameCount
fingerprint
```

`AtomicFileGhostArtifactManifestStore` writes:

```text
<active ghost filename>.manifest
```

Codec contract:

```text
magic       FRGM / 0x4652474D
version     1
record size 24 bytes
write       AtomicFile
bounds      finite nonnegative distance; 1..MAX_FRAMES
cleanup     base + .bak + .new
```

The manifest duplicates no frame payload. It is a durable identity binding the existing validated ghost file to its run distance.

## Compatibility decision

The frame codec remains unchanged:

```text
SaveManager ghost file version = 2
```

Reasons:

- the existing frame parser already has mature malformed/truncated/future-schema handling;
- changing the frame header would require a broader compatibility migration;
- a sidecar can be introduced only for new promotions while keeping older ghosts readable;
- manifest namespace naturally follows the active ghost filename.

Newly promoted ghost bundles are self-describing. Pre-manifest ghosts remain load-compatible but cannot reconstruct a mismatch that already existed before this feature.

## Promotion transaction

Previous durable order:

```text
receipt
→ ghost
→ best distance
→ receipt clear
```

Current durable order:

```text
receipt
→ ghost
→ persistent manifest
→ best distance
→ receipt clear
```

The manifest is durable before best distance, and the receipt is retained until both manifest and threshold complete.

`GhostPromotionPersistenceResult.complete` now requires:

```text
receiptDurable
ghostDurable
manifestDurable
distanceDurable
receiptCleared
```

## Crash-window matrix

### Before receipt durability

No artifact mutation occurs. The candidate is not recoverable and is not reported complete.

### After receipt, before ghost

Receipt remains. Recovery finds no matching candidate ghost and abandons only the stale in-progress receipt. Existing ghost and threshold remain unchanged.

### After ghost, before manifest

Receipt remains and identifies the newly durable ghost. Recovery verifies the ghost and writes the missing manifest before touching best distance.

### After manifest, before best distance

Receipt and manifest both identify the ghost. Recovery repairs best distance, then clears the receipt.

### After best distance, before receipt clear

Recovery recognizes the applied threshold, ensures the manifest identity, and clears the receipt without lowering or duplicating state.

### After receipt clear

The persistent manifest retains artifact distance, count, and fingerprint. If best distance later becomes lower, manifest-only recovery validates the ghost before repairing it.

## Receipt recovery

A pending receipt always performs full ghost validation:

```text
load ghost
→ validate structure
→ compare frame count
→ compare fingerprint
```

### Matching artifact

```text
ensure exact manifest
→ repair lower threshold
→ clear receipt
```

A stale or corrupt manifest is replaced with the receipt identity only after the receipt has proven the durable ghost match.

### Nonmatching artifact

```text
clear stale receipt
→ reconcile older manifest
→ preserve ghost and threshold
```

A healthy older manifest is preserved. Corrupt or mismatched older manifest evidence remains fail-closed.

## Manifest-only recovery

### Repair required

When:

```text
current best < manifest distance
```

recovery performs full ghost validation and raises the threshold only when frame count and fingerprint match.

### Already applied

When:

```text
current best >= manifest distance
```

recovery returns `ALREADY_APPLIED` before loading the ghost.

This lazy-validation decision avoids synchronously decoding and hashing as many as 36,000 frames on every healthy startup or manager admission check.

The optimization does not weaken threshold repair: every path that would raise distance still requires full artifact identity verification.

Explicit maintenance inspection also performs full identity validation on demand.

## New disposition

Added:

```text
CORRUPT_MANIFEST
```

It blocks new promotion together with:

```text
CORRUPT_RECEIPT
IO_FAILURE
```

Manifest corruption includes:

- malformed/future fixed-size record;
- invalid distance or frame count;
- manifest/artifact mismatch when repair requires identity verification;
- explicit maintenance mismatch inspection.

## Manager integration

`GhostPersistenceManager` now constructs:

- `AtomicFileGhostPromotionReceiptStore`;
- `AtomicFileGhostArtifactManifestStore`;
- `AndroidGhostPromotionArtifactStore`.

Recovery triggers now reconcile both receipt and manifest evidence.

Test cleanup clears both sidecars.

The compatibility overload retains equal-distance replacement and writes a new manifest fingerprint for replacement frames without lowering best distance.

## Maintenance integration

`AndroidGhostPromotionEvidenceHandler` now distinguishes:

```text
CLEAN(no_evidence)
CLEAN(valid_manifest)
PENDING(valid_receipt)
CORRUPT(invalid_receipt)
CORRUPT(invalid_manifest)
CORRUPT(manifest_artifact_mismatch)
```

Selective cleanup rules:

- corrupt receipt removal preserves a valid matching manifest;
- corrupt manifest removal preserves the ghost frame file;
- manifest/artifact mismatch removal clears only the invalid association;
- best distance is not directly rewritten by discard;
- run-outcome evidence remains isolated.

## Tests added or expanded

### `GhostArtifactManifestStoreTest`

Covers:

- complete round trip;
- empty and clear;
- truncation;
- trailing data;
- unknown version;
- invalid replacement preserving valid evidence;
- namespace isolation.

### `GhostPromotionRecoveryCoordinatorTest`

Expanded for:

- receipt → ghost → manifest → distance → clear order;
- manifest durability in completion;
- missing/stale manifest reconstruction;
- failed manifest write;
- failed distance write and later repair;
- receipt-free manifest repair;
- already-applied no-ghost-load fast path;
- corrupt manifest blocking;
- manifest/artifact mismatch blocking;
- old valid manifest preservation after receipt mismatch;
- compatibility fingerprint replacement.

### Android-backed tests

Expanded:

- `GhostPersistenceManagerTest`;
- `GhostPersistenceManagerAdmissionTest`;
- `RunOutcomePersistenceIntegrationTest`;
- `RecoveryEvidenceMaintenanceIntegrationTest`.

They cover manifest durability, startup repair without receipt, terminal integration, equal-distance replacement, corrupt manifest retention, selective cleanup, and mismatch diagnosis.

## Source contracts

`test_ghost_promotion_recovery_contract.py` now locks:

- both sidecar codecs;
- transaction order;
- manifest durability in `complete`;
- receipt recovery order;
- receipt-free manifest repair;
- already-applied fast path before ghost loading;
- corrupt manifest blocking;
- manager construction and cleanup;
- full frame fingerprint coverage.

`test_recovery_evidence_maintenance_contract.py` now locks:

- receipt/manifest/artifact ownership;
- separate corruption detail codes;
- full explicit manifest identity inspection;
- preservation of a valid manifest during receipt cleanup;
- invalid-manifest cleanup;
- domain isolation.

A contract defect was corrected during review: the first manifest contract attempted to parse an expression-bodied Kotlin getter as a braced block. The final contract checks the exact expression body directly.

## Validation performed

Passed focused Kotlin compilation for:

- manifest codec and ghost recovery core;
- optimized manifest-only recovery control flow;
- production manager construction;
- maintenance ghost handler surface.

Passed executable state-machine coverage for:

- normal promotion;
- manifest-write failure;
- receipt-based manifest reconstruction;
- manifest-only distance repair;
- already-applied no-hash fast path;
- fingerprint mismatch blocking;
- monotonic threshold behavior.

Representative source-contract parser assertions were checked after correcting the expression-getter defect.

## Evidence not claimed

Not executed through an exact-head Android checkout:

- complete Gradle/JUnit suite;
- Robolectric suite;
- Android lint;
- debug or release build;
- connected emulator;
- physical-device ghost recovery;
- physical-device maintenance ADB commands;
- signed packaging/store path.

GitHub commit statuses and workflow evidence must be inspected separately. Empty status lists are not proof of success.

## Remaining debt

- Immediate terminal HIT impact remains inline in `GameView`.
- The complete collision-result dispatcher remains in `GameView`.
- STUMBLE and MERCY_MISS live effects remain in the private GameView adapter.
- Pre-manifest ghost/distance mismatches cannot be reconstructed.
- The 64-bit fingerprint is noncryptographic.
- Healthy automatic startup avoids repeated full ghost hashing; full explicit inspection is on demand.
- Ghost and non-ghost recovery remain independent protocols.
- Compatibility namespace switching during active worker/maintenance execution is unsupported.
- Remediation remains debug/support-only rather than an end-user UI.
- Exact-head Android and physical-device evidence remains outstanding.
