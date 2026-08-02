# Forest Run — Recoverable Ghost Promotion

## Purpose

A promoted best run spans three durable facts:

- the validated ghost frame file;
- the distance associated with that exact frame artifact;
- the best-distance threshold used for later promotion eligibility.

The existing frame file remains `SaveManager` ghost format version 2. This tranche does **not** alter that binary payload. Instead, every newly promoted ghost receives a small fingerprint-bound manifest sidecar so the durable artifact bundle is self-describing after the transient promotion receipt has been cleared.

## Ownership

`RunOutcomePersistenceCoordinator` owns candidate eligibility only:

1. normalize completed distance;
2. compare it with `GhostPersistenceManager.bestDistanceFloor(...)`;
3. require a strictly better distance and a non-empty detached ghost;
4. submit one distance-aware promotion request.

It never writes best distance directly.

`GhostPersistenceManager` owns:

- immediate in-memory publication;
- pending-distance admission;
- single-worker ordering;
- startup and pre-write recovery;
- durable promotion telemetry.

`GhostPromotionRecoveryCoordinator` owns the durable protocol.

## Transient promotion receipt

Before the ghost changes, `AtomicFileGhostPromotionReceiptStore` writes:

```text
target distance
frame count
64-bit frame fingerprint
```

The receipt:

- uses magic `FRGP`;
- is schema version 1;
- is fixed at 24 bytes;
- is written through `AtomicFile`;
- is scoped as `<ghost filename>.promotion`.

It represents an in-progress promotion and is cleared only after all durable promotion steps complete.

## Persistent artifact manifest

After the ghost is durable, `AtomicFileGhostArtifactManifestStore` writes the same identity as:

```text
target distance
frame count
64-bit frame fingerprint
```

The manifest:

- uses magic `FRGM`;
- is schema version 1;
- is fixed at 24 bytes;
- is written through `AtomicFile`;
- is scoped as `<ghost filename>.manifest`;
- remains after promotion completion.

The manifest binds one validated ghost artifact to the distance that produced it. It is not another frame payload and does not change the version-2 ghost binary format.

## Artifact fingerprint

`GhostRunFingerprint` covers the raw persisted identity of every frame:

```text
frame count
timestamp bits
x bits
y bits
state ordinal
scaleX bits
scaleY bits
```

It is a compact local recovery identity, not a cryptographic authenticity mechanism.

## Durable worker sequence

For an accepted candidate, the single worker performs:

```text
write AtomicFile promotion receipt
→ write AtomicFile validated ghost
→ write AtomicFile artifact manifest
→ synchronously commit max(current best, candidate distance)
→ clear promotion receipt
```

Best distance cannot advance unless the receipt, ghost, and manifest are durable.

The manifest precedes the threshold write so process death after receipt clearing can still associate the surviving ghost with its target distance.

The threshold write uses `max(current best, candidate distance)`. Repeated or delayed recovery cannot lower progress.

## Immediate playback

Accepted frames are published in memory before worker execution. `PublishedGhost` carries:

- an immutable frame snapshot;
- accepted distance;
- frame fingerprint.

This preserves immediate restart/playback behavior while keeping disk work off the render thread.

`RunOutcomeCommitResult.ghostPromoted` means accepted into the recoverable worker pipeline. It does not claim worker completion before terminal completion returns.

## Pending-distance floor

`GhostPersistenceManager.bestDistanceFloor(...)` is:

```text
max(durable best distance, accepted in-memory promotion distance)
```

A shorter candidate cannot queue behind and overwrite a longer accepted promotion.

The same gate exists inside `GhostPersistenceManager`, protecting legacy or direct callers in addition to the terminal coordinator.

The compatibility overload submits at the current floor. It may replace an equal-distance ghost, and the new artifact manifest is updated to the replacement frame fingerprint without lowering best distance.

## Receipt recovery

A pending receipt requires full artifact validation.

### Matching ghost

When durable frame count and fingerprint match the receipt:

1. create or replace the manifest with the receipt identity;
2. repair best distance when it is lower;
3. clear the receipt only after manifest and threshold durability.

Results:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching ghost

When the receipt does not identify the durable ghost:

1. clear only the stale in-progress receipt;
2. reconcile any older persistent manifest;
3. preserve the existing ghost and threshold.

When the older manifest is healthy, the result remains:

- `ABANDONED_UNWRITTEN_GHOST`.

A corrupt or mismatched older manifest remains fail-closed instead of being silently ignored.

## Manifest-only recovery

When no receipt remains, the manifest is the durable association between ghost and distance.

### Threshold already applied

When:

```text
current best distance >= manifest distance
```

recovery returns `ALREADY_APPLIED` without loading or hashing the ghost file. This keeps the ordinary startup path bounded and avoids repeatedly decoding up to the maximum frame count.

Full artifact validation remains available through explicit maintenance inspection.

### Threshold requires repair

When:

```text
current best distance < manifest distance
```

recovery must first load the ghost and verify:

- structural validity;
- frame count;
- fingerprint.

Only a matching artifact may raise best distance to the manifest distance.

Results:

- matching artifact and successful write: `REPAIRED_DISTANCE`;
- mismatched artifact: `CORRUPT_MANIFEST`;
- failed I/O: `IO_FAILURE`.

This closes the post-receipt crash window without adding repeated full-file work to healthy startup.

## Corrupt evidence

The following block new promotions:

- `CORRUPT_RECEIPT`;
- `CORRUPT_MANIFEST`;
- `IO_FAILURE`.

Automatic recovery never deletes corrupt or unreadable evidence merely to unblock the queue.

## Maintenance behavior

`AndroidRecoveryEvidenceMaintenance` exposes the combined ghost domain as:

```text
GHOST_PROMOTION
```

Inspection distinguishes:

```text
CLEAN(no_evidence)
CLEAN(valid_manifest)
PENDING(valid_receipt)
CORRUPT(invalid_receipt)
CORRUPT(invalid_manifest)
CORRUPT(manifest_artifact_mismatch)
IO_FAILURE(...)
```

Maintenance rules:

- safe retry delegates to canonical receipt/manifest recovery;
- safe retry never discards corrupt evidence;
- targeted corrupt-receipt removal preserves a valid matching manifest;
- corrupt or mismatched manifest removal preserves the ghost frame file;
- the run-outcome journal is never opened by the ghost handler;
- I/O failure never authorizes deletion.

Mutating maintenance commands require a debuggable cold start after save repair and before `GameView`. A reused live Activity is inspection-only.

See `docs/RECOVERY_EVIDENCE_MAINTENANCE.md` for operational commands.

## Queue ordering

A single daemon executor serializes promotions.

For candidate B queued behind candidate A:

1. A completes or leaves receipt/manifest evidence;
2. B recovers A’s evidence;
3. only then may B write its own receipt and artifact bundle.

If A fails before ghost durability, only A’s matching in-memory publication may be removed. A newer publication is protected by distance and fingerprint identity.

## Recovery triggers

Recovery is attempted:

- when `AndroidRunOutcomePersistenceSink` is created;
- when a new manager request arrives and no worker is active;
- at the start of every worker task;
- before disk fallback in `loadLatest(...)`;
- through explicit cold-start maintenance.

## Relationship to non-ghost recovery

Terminal persistence has independent records:

### Non-ghost journal

Protects:

- forest mood;
- return state;
- last-run summary;
- pacifist-route count.

### Ghost evidence

The transient receipt protects an in-progress promotion. The persistent manifest protects the completed artifact-to-distance association.

These protocols are independently recoverable. They are not one global transaction spanning relationship history, presentation, progression, ghost storage, and best distance.

## Validation surface

`GhostPromotionRecoveryCoordinatorTest` covers:

- receipt → ghost → manifest → distance → clear ordering;
- manifest-write failure;
- distance-write failure and later repair;
- receipt-based manifest reconstruction;
- receipt mismatch with preservation of an older valid manifest;
- receipt-free manifest repair;
- already-applied no-ghost-load fast path;
- manifest/artifact mismatch blocking;
- corrupt receipt and manifest blocking;
- stale-manifest replacement;
- fingerprint sensitivity across every frame field.

`GhostArtifactManifestStoreTest` covers:

- complete round trip;
- empty and cleared stores;
- truncation, trailing bytes, and unknown version;
- invalid replacement preserving existing evidence;
- namespace isolation by active ghost filename.

`GhostPersistenceManagerTest`, `GhostPersistenceManagerAdmissionTest`, and `RunOutcomePersistenceIntegrationTest` cover production publication, manifest durability, startup repair, equal-distance replacement, pending-distance admission, and terminal integration.

`RecoveryEvidenceMaintenanceIntegrationTest` covers valid-manifest inspection, manifest-only repair, corrupt receipt removal that preserves a valid manifest, corrupt-manifest removal, and manifest/artifact mismatch handling.

Source contracts lock transaction order, both fixed-size codecs, manager construction, cleanup, lazy validation, fingerprint coverage, maintenance isolation, and fail-closed corruption behavior.

Focused Kotlin compilation and executable state-machine harnesses passed for the core coordinator, manager surface, maintenance adapter, and lazy manifest recovery. Exact-head Android Gradle, Robolectric, emulator, and physical-device execution remain separate evidence gates.

## Remaining limitations

- Ghosts created before artifact manifests remain load-compatible but cannot reconstruct a mismatch that already existed before this feature.
- The 64-bit fingerprint is noncryptographic and has theoretical collision risk.
- The already-applied automatic fast path trusts the durable manifest/threshold pair and does not repeatedly hash the ghost; explicit maintenance inspection performs full validation.
- Remediation is debug/support tooling rather than an end-user UI.
- Release builds reject maintenance intents.
- Compatibility namespaces must not switch during an active worker or maintenance instance.
- Exact-head Android Gradle, emulator, physical-device, and ADB acceptance remain outstanding.
