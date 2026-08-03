# 2026-08-03 — SHA-256 Ghost Artifact Identity Audit

## Scope

This tranche replaces the theoretical collision risk of the ghost promotion protocol’s sole 64-bit FNV identity with a versioned SHA-256 identity while preserving:

- `SaveManager` ghost frame format version 2;
- version-1 receipt and manifest readability;
- single-worker promotion ordering;
- immediate in-memory ghost playback;
- pending-distance admission;
- lazy healthy-start behavior;
- fail-closed maintenance and selective evidence removal;
- direct commits to `main` without branch or PR workflow.

No `GameView`, Player, collision, rendering, input, Bloom, Garden, authored-copy, audio, haptic, or death-timing production code changed.

## Prior state

The transient promotion receipt and persistent manifest were fixed-size 24-byte AtomicFile records:

```text
distance
frame count
64-bit FNV-1a frame fingerprint
```

The FNV fingerprint covered frame count and every persisted frame component. It was sufficient for practical local crash recovery but had a theoretical collision risk and did not cryptographically bind accepted distance.

## Implemented identity

Added:

```text
app/src/main/java/com/anurag9000/forestrun/systems/GhostRunIdentity.kt
```

`GhostRunIdentity` computes:

- the historical FNV fingerprint for compatibility continuity;
- a SHA-256 digest for collision-resistant version-2 identity.

### Canonical byte stream

SHA-256 receives big-endian 32-bit values in this exact order:

```text
accepted distance raw float bits
frame count
for each frame:
    t raw float bits
    x raw float bits
    y raw float bits
    state ordinal
    scaleX raw float bits
    scaleY raw float bits
```

The use of raw float bits preserves the exact values stored by the ghost codec rather than introducing decimal conversion or normalization ambiguity.

### Distance binding correction

The first draft hashed only frame payload. Review identified that a sidecar could then alter distance without changing its digest.

The final design prepends `distanceM.toRawBits()` to the canonical SHA-256 stream. Tests now prove:

- changing only distance changes SHA-256;
- changing only distance leaves the legacy frame-only FNV value unchanged;
- a version-2 receipt or manifest with modified distance fails recovery validation.

## Cryptographic boundary

SHA-256 is used for collision-resistant local identity.

It is not:

- a message authentication code;
- a signature;
- proof of trusted authorship;
- protection against a malicious process that can replace both artifact and digest.

No secret key or certificate exists. Documentation and support output use “identity,” not “authentication.”

## Sidecar schemas

### Version 1 — read compatibility

```text
magic        4 bytes
version      4 bytes
distance     4 bytes
frame count  4 bytes
FNV-1a       8 bytes
---------------------
record      24 bytes
```

Version-1 receipt/manifest objects decode with `sha256Hex = null`.

### Version 2 — current writes

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

Receipt magic remains `FRGP`; manifest magic remains `FRGM`.

Current store `save(...)` methods require a canonical 64-character lowercase hexadecimal digest and reject digest-less, uppercase, malformed, or wrong-length identity values.

Version-1 evidence can only enter by decoding existing bytes. Current code cannot emit new weak sidecars accidentally.

## Promotion transaction

The durable order remains:

```text
version-2 receipt
→ validated ghost frame file
→ version-2 manifest
→ synchronous monotonic best-distance commit
→ receipt clear
```

The promotion result requires receipt, ghost, manifest, distance, and clear durability.

The frame file itself remains version 2 and unchanged.

## Recovery behavior

### Version-2 receipt

Recovery validates:

- frame structure;
- frame count;
- FNV fingerprint;
- SHA-256 using receipt distance and durable frames.

Distance-only or digest-only tampering cannot advance best distance.

### Version-1 receipt

Recovery validates the historical FNV frame identity, computes SHA-256 from stored distance plus durable frames, and writes a version-2 manifest before repairing best distance.

### Version-2 manifest repair

When best distance is below manifest distance, recovery loads the ghost and validates FNV plus SHA-256 over manifest distance and frames before repair.

### Version-1 manifest repair

Recovery validates FNV, computes the strong digest, writes a version-2 manifest, and only then repairs distance.

### Already-applied lazy path

When current best distance already meets manifest distance and no receipt has forced a ghost load, automatic recovery returns without decoding or hashing the ghost.

This preserves the prior startup performance decision for long ghost files. A healthy version-1 manifest may remain legacy until validation becomes necessary.

Explicit maintenance inspection still validates the full artifact association on demand.

### Receipt abandonment

If a receipt does not identify the durable ghost:

1. clear only the stale receipt;
2. validate any older manifest using the already-loaded ghost;
3. upgrade a matching legacy manifest to version 2;
4. preserve existing ghost and threshold;
5. block on older-manifest mismatch.

## In-memory publication

`PublishedGhost` now carries:

```text
frames
distance
FNV fingerprint
SHA-256 digest
```

Failure cleanup compares all identity fields. A failed older worker cannot erase a newer publication that differs only in strong digest or accepted distance.

Disk fallback loads best distance before constructing the publication digest, preserving the same distance-bound identity rule.

## Maintenance changes

`AndroidGhostPromotionEvidenceHandler` now calls:

```kotlin
GhostRunIdentity.matches(
    frames = frames,
    distanceM = manifest.distanceM,
    frameCount = manifest.frameCount,
    fingerprint = manifest.fingerprint,
    sha256Hex = manifest.sha256Hex
)
```

Version-2 inspection detects distance, digest, frame, state, scale, count, and timestamp mismatches. Version-1 inspection retains FNV compatibility.

Selective clear behavior remains:

- corrupt receipt removal preserves a valid manifest;
- corrupt/mismatched manifest removal preserves the ghost frame file;
- best distance is not rewritten by discard;
- the run-outcome journal is not opened;
- digest values are never logged.

## Tests added or migrated

Added:

- `GhostRunIdentityTest`

Expanded/migrated:

- `GhostPromotionRecoveryCoordinatorTest`
- `GhostPromotionReceiptAbandonmentTest`
- `GhostPromotionReceiptStoreTest`
- `GhostArtifactManifestStoreTest`
- `GhostPersistenceManagerTest`
- `GhostPersistenceManagerAdmissionTest`
- `RunOutcomePersistenceIntegrationTest`
- `RecoveryEvidenceMaintenanceIntegrationTest`

Coverage includes:

- independently computed golden SHA-256 vector;
- big-endian raw-bit canonical order;
- distance-only sensitivity;
- every persisted frame-field sensitivity;
- lowercase hex codec round trip;
- v1 24-byte reads;
- v2 56-byte writes;
- digest-less new-write rejection;
- malformed digest rejection without replacing valid evidence;
- version-1 receipt and manifest upgrade;
- receipt-distance tampering;
- manifest-distance tampering;
- digest tampering;
- frame/artifact mismatch;
- manifest-only repair;
- already-applied no-ghost-load path;
- receipt abandonment and older-manifest validation;
- maintenance diagnosis and selective cleanup;
- equal-distance replacement identity.

## Source contracts

Updated:

- `scripts/test_ghost_promotion_recovery_contract.py`
- `scripts/test_recovery_evidence_maintenance_contract.py`

The contracts require:

- SHA-256 implementation and canonical distance/frame fields;
- v1 read and v2 write constants;
- 24-byte and 56-byte record sizes;
- digest-required new writes;
- strong identity before manifest/distance progression;
- legacy upgrade before repair;
- lazy already-applied ordering;
- known-ghost validation before the fast path;
- distance-bound disk fallback publication;
- distance-aware maintenance inspection;
- no direct fingerprint-only maintenance validator.

A parser defect was caught during review: the manifest-repair contract initially selected the pre-fast-path `matchingIdentity(...)` call. It now slices the actual repair branch after durable ghost acquisition.

## Validation performed

### Focused Kotlin compilation

Passed for:

- `GhostRunIdentity`;
- versioned manifest codec;
- versioned receipt codec;
- complete promotion recovery state machine;
- production manager surface.

### Independent golden vector

For accepted distance `480f` and the two canonical test frames, Python and Kotlin independently produced:

```text
fbea238ceb98e6c3dd2cb4ff921d988ca14f1322b3cc520e77626c613a1b7cbe
```

The retained historical FNV value is:

```text
-4791329882507978193
```

### Executable codec harness

Passed:

```text
v1-v2 ghost sidecar codec checks passed
```

It verified v2 56-byte receipt/manifest round trips, v1 24-byte reads, and rejection of digest-less or uppercase-digest new writes.

### Executable recovery harness

Passed:

```text
distance-bound ghost recovery state machine passed
```

It covered normal strong promotion, distance tampering, frame mismatch, version-1 receipt upgrade, version-1 manifest upgrade, and no-ghost-load already-applied recovery.

### Manager compile

The manager compiled with distance-bound publication identity, worker recovery, disk fallback, and strong publication cleanup against focused Android/engine stubs.

## Evidence not obtained

This session did not execute through an exact-head Android checkout:

- complete Gradle/JUnit suite;
- Robolectric suite;
- Android lint;
- debug or release build;
- connected emulator;
- physical-device crash recovery;
- physical-device ADB maintenance;
- signed artifact and store path.

The runtime still could not resolve `raw.githubusercontent.com`, so exact-head local checkout remained unavailable.

Empty GitHub status/workflow results must not be interpreted as success.

## Remaining limitations

- Pre-manifest ghost/distance mismatches cannot be reconstructed retroactively.
- Healthy already-applied version-1 manifests remain legacy until validation is needed.
- The lazy automatic path does not continuously rehash a healthy artifact; maintenance provides explicit full validation.
- SHA-256 does not authenticate a trusted writer.
- Non-ghost and ghost recovery remain independent rather than globally atomic.
- Compatibility namespace switching during active worker/maintenance remains unsupported.
- End-user recovery UI and physical-device acceptance remain outstanding.
