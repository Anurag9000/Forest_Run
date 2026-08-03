# Forest Run — Recoverable Ghost Promotion

## Purpose

A promoted best run spans three durable facts:

- the validated ghost frame file;
- the accepted distance associated with that exact frame artifact;
- the best-distance threshold used for later promotion eligibility.

The frame payload remains `SaveManager` ghost format version 2. This protocol does not alter or rewrite that binary codec. It uses two AtomicFile sidecars so newly promoted artifacts remain recoverable and self-describing:

```text
<ghost>.promotion  transient in-progress receipt
<ghost>.manifest   persistent artifact-to-distance identity
```

## Ownership

`RunOutcomePersistenceCoordinator` owns candidate eligibility only:

1. normalize completed distance;
2. compare it with `GhostPersistenceManager.bestDistanceFloor(...)`;
3. require a strictly better distance and a non-empty detached ghost;
4. submit one distance-aware promotion request.

It never writes best distance directly.

`GhostPersistenceManager` owns immediate in-memory publication, pending-distance admission, single-worker ordering, startup/pre-write recovery, and I/O telemetry.

`GhostPromotionRecoveryCoordinator` owns the durable transaction and recovery decisions.

## Versioned sidecar schemas

### Legacy version 1

Version-1 receipt and manifest records remain readable:

```text
magic       4 bytes
version     4 bytes
 distance    4 bytes
frame count 4 bytes
FNV-1a      8 bytes
--------------------
record     24 bytes
```

The historical FNV-1a value identifies frame count and every persisted frame field. It does not cryptographically bind distance and is retained only for compatibility.

### Current version 2

All new writes use version 2:

```text
magic        4 bytes
version      4 bytes
distance     4 bytes
frame count  4 bytes
FNV-1a       8 bytes
SHA-256     32 bytes
---------------------
record      56 bytes
```

Receipt magic is `FRGP`; manifest magic is `FRGM`.

New store writes reject digest-less records. Version-1 evidence can only enter through decoding an existing 24-byte sidecar, not through a current `save(...)` call.

## Canonical SHA-256 identity

`GhostRunIdentity` hashes one canonical big-endian byte stream:

```text
accepted distance raw float bits
frame count
for every frame:
    timestamp raw float bits
    x raw float bits
    y raw float bits
    state ordinal
    scaleX raw float bits
    scaleY raw float bits
```

The digest therefore binds both the frame artifact and its accepted distance. Altering only the distance invalidates a version-2 sidecar even when the frame payload is unchanged.

The sidecar also retains the historical FNV value for diagnostics and compatibility continuity. Version-2 matching requires both the FNV value and SHA-256 digest to match.

SHA-256 provides collision-resistant local identity. It is **not** an authenticity guarantee, MAC, or digital signature because no secret or signing key is involved.

The digest is serialized as 32 raw bytes and represented in memory as exactly 64 lowercase hexadecimal characters.

## Durable worker sequence

For an accepted candidate, the single worker performs:

```text
write AtomicFile version-2 promotion receipt
→ write AtomicFile validated ghost
→ write AtomicFile version-2 artifact manifest
→ synchronously commit max(current best, candidate distance)
→ clear promotion receipt
```

Best distance cannot advance unless the receipt, ghost, and strong manifest are durable.

The manifest precedes the threshold write so process death after receipt clearing still leaves a durable artifact-to-distance association.

The threshold write is monotonic. Repeated or delayed recovery cannot lower progress.

## Immediate playback and pending floor

Accepted frames are published in memory before worker execution. `PublishedGhost` carries:

- immutable frames;
- accepted distance;
- legacy fingerprint;
- distance-bound SHA-256 digest.

Failure cleanup removes an in-memory publication only when distance, fingerprint, and digest all identify the same publication.

`GhostPersistenceManager.bestDistanceFloor(...)` is:

```text
max(durable best distance, accepted in-memory promotion distance)
```

A shorter candidate cannot queue behind and overwrite a longer accepted promotion.

The compatibility overload submits at the current floor. It may replace an equal-distance ghost; the resulting manifest receives the replacement frames’ distance-bound digest without lowering best distance.

## Receipt recovery

A pending receipt always requires full ghost validation.

### Version-2 receipt

Recovery verifies:

- structural frame validity;
- frame count;
- FNV fingerprint;
- SHA-256 over receipt distance and frame payload.

A modified receipt distance therefore cannot authorize best-distance advancement.

### Version-1 receipt

Recovery validates the historical FNV frame identity. It then computes the distance-bound SHA-256 digest from the durable frames and stored receipt distance.

Before distance can advance, recovery writes a version-2 manifest containing that strong identity. The legacy receipt is cleared only after manifest and threshold durability.

### Matching ghost

When the durable ghost matches:

1. create, replace, or upgrade the manifest to version 2;
2. repair best distance when lower;
3. clear the receipt after durable completion.

Results:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching ghost

When the receipt does not identify the durable ghost:

1. clear only the stale in-progress receipt;
2. reconcile any older persistent manifest using the already-loaded ghost;
3. preserve the existing ghost and threshold.

A healthy older manifest yields `ABANDONED_UNWRITTEN_GHOST`. A mismatched or corrupt older manifest remains fail-closed.

## Manifest-only recovery

When no receipt remains, the manifest is the durable association between ghost and distance.

### Threshold already applied

When:

```text
current best distance >= manifest distance
```

automatic recovery returns `ALREADY_APPLIED` without loading or hashing the ghost. This avoids repeatedly decoding up to the maximum ghost-frame count during healthy startup.

This lazy path applies to both v1 and v2 manifests. A healthy legacy manifest may therefore remain version 1 until full validation is actually needed.

### Threshold requires repair

When:

```text
current best distance < manifest distance
```

recovery loads the ghost and validates the manifest identity.

For version 2, validation includes the manifest distance in SHA-256. For version 1, recovery validates FNV and upgrades the manifest to version 2 before changing best distance.

Results:

- matching artifact and successful upgrade/write: `REPAIRED_DISTANCE`;
- mismatched artifact or distance-bound digest: `CORRUPT_MANIFEST`;
- failed I/O: `IO_FAILURE`.

## Corrupt evidence

These dispositions block new promotions:

```text
CORRUPT_RECEIPT
CORRUPT_MANIFEST
IO_FAILURE
```

Automatic recovery never deletes corrupt or unreadable evidence merely to unblock the queue.

## Maintenance behavior

`AndroidRecoveryEvidenceMaintenance` exposes the combined ghost domain as `GHOST_PROMOTION`.

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

Maintenance performs full artifact validation on demand:

- version-2 manifests use distance-bound SHA-256 plus FNV;
- version-1 manifests use legacy FNV compatibility;
- a digest-only, distance-only, frame-only, or count mismatch is diagnosed as an artifact mismatch;
- targeted corrupt-receipt removal preserves a valid manifest;
- targeted corrupt/mismatched-manifest removal preserves the ghost frame file;
- the run-outcome journal is never opened by the ghost handler;
- I/O failure never authorizes deletion.

Maintenance inspection does not silently upgrade a healthy legacy manifest. Canonical recovery upgrades legacy evidence only when replay is necessary.

Mutating maintenance commands require a debuggable cold start after save repair and before `GameView`. A reused live Activity is inspection-only.

## Recovery triggers

Recovery is attempted:

- when `AndroidRunOutcomePersistenceSink` is created;
- before a new manager request when no worker is active;
- at the start of every worker task;
- before disk fallback in `loadLatest(...)`;
- through explicit cold-start maintenance.

Disk fallback builds its in-memory publication identity from the loaded best distance and loaded frames, preserving the same distance-bound identity contract.

## Relationship to non-ghost recovery

The non-ghost journal protects forest mood, return state, last-run summary, and pacifist-route count.

Ghost receipt/manifest evidence protects ghost identity and best distance.

The protocols remain independently recoverable rather than one global transaction spanning relationship history, presentation, progression, ghost storage, and best distance.

## Validation surface

Coverage includes:

- version-2 56-byte receipt and manifest round trips;
- version-1 24-byte readability;
- rejection of digest-less or malformed new writes;
- independently computed golden SHA-256 vector;
- distance-only, digest-only, frame-only, state, scale, count, and timestamp sensitivity;
- receipt → ghost → strong manifest → distance → clear ordering;
- version-1 receipt and manifest upgrade before repair;
- receipt-distance tampering;
- manifest-distance tampering;
- stale receipt abandonment with older-manifest validation;
- manifest-only repair;
- already-applied no-ghost-load fast path;
- maintenance diagnosis and selective evidence removal;
- pending-distance admission and equal-distance replacement.

Focused Kotlin compilation passed for the identity, codecs, recovery coordinator, and manager surface. Executable golden-vector, codec, and recovery-state-machine harnesses passed. Exact-head Android Gradle, Robolectric, emulator, and physical-device execution remain separate evidence gates.

## Remaining limitations

- Ghosts and mismatches that predate persistent manifests remain load-compatible but cannot be reconstructed retroactively.
- Version-1 sidecars retain their historical noncryptographic identity until recovery needs to validate and upgrade them.
- The healthy already-applied fast path deliberately avoids repeated full ghost hashing; explicit maintenance performs full validation.
- SHA-256 establishes collision-resistant identity, not authenticity against a malicious writer with filesystem access.
- Remediation is debug/support tooling rather than an end-user UI.
- Compatibility namespaces must not switch during an active worker or maintenance instance.
- Exact-head Android, emulator, physical-device, and ADB acceptance remain outstanding.
