# Forest Run — Recovery Evidence Maintenance

## Purpose

Forest Run fails closed when recovery evidence is corrupt, unreadable, or conflicts with live persistence state. This prevents silent double-counting and speculative overwrites, but unresolved evidence needs an explicit support path.

`AndroidRecoveryEvidenceMaintenance` covers two independent domains:

- `RUN_OUTCOME` — forest mood, return state, completed summary, and pacifist-route count;
- `GHOST_PROMOTION` — transient receipt, persistent artifact manifest, durable ghost, and best-distance threshold.

Inspection, safe retry, and destructive evidence removal remain separate. No destructive operation runs automatically.

## Evidence states

Each domain reports:

```text
CLEAN      no unresolved evidence
PENDING    valid transient evidence awaits recovery
CORRUPT    evidence cannot be decoded or validated
BLOCKED    valid run-outcome evidence conflicts with live state or cannot be applied
IO_FAILURE evidence could not be read, recovered, or cleared reliably
```

Support summaries contain only domain, state, and fixed detail codes. They exclude scores, distances, summary text, entity identity, ghost frames, fingerprints, SHA-256 values, timestamps, and progression counters.

Examples:

```text
run_outcome=PENDING(valid_journal); ghost_promotion=CORRUPT(invalid_receipt)
run_outcome=CLEAN(no_journal); ghost_promotion=CLEAN(valid_manifest)
```

## Maintenance API

`RecoveryEvidenceMaintenanceCoordinator` requires exactly one handler per domain and exposes:

```kotlin
inspect()
recoverSafely()
discardCorrupt(domain)
discardUnresolvedPending(domain)
```

### `inspect()`

Reads both domains without applying or deleting anything.

### `recoverSafely()`

Retries only recoverable states:

- `CLEAN` remains unchanged;
- `CORRUPT` remains preserved;
- `PENDING`, `BLOCKED`, or transient `IO_FAILURE` is retried through the canonical domain owner.

Safe recovery never deletes corrupt evidence merely to unblock progress.

### `discardCorrupt(domain)`

Clears evidence only after a fresh inspection confirms `CORRUPT` for the selected domain. Clean, pending, blocked, or unreadable evidence is not deleted.

### `discardUnresolvedPending(domain)`

This explicit last-resort operation:

1. inspects the selected domain;
2. refuses clean, corrupt, or unreadable evidence;
3. attempts canonical recovery once more;
4. returns `RECOVERED_INSTEAD` when recovery succeeds;
5. clears only evidence still confirmed `PENDING` or `BLOCKED`;
6. verifies the domain reports `CLEAN` afterward.

Read failure is never interpreted as permission to erase unknown data.

## Domain isolation

### Run-outcome handler

`MaintenanceRunOutcomePersistenceSink` can read and write complete mood, return, summary, and route snapshots. It cannot publish a ghost, advance best distance, or construct `AndroidRunOutcomePersistenceSink`.

### Ghost handler

The ghost handler owns only:

- `AtomicFileGhostPromotionReceiptStore`;
- `AtomicFileGhostArtifactManifestStore`;
- `GhostPromotionRecoveryCoordinator`;
- `AndroidGhostPromotionArtifactStore`.

It never opens the run-outcome journal.

A corrupt ghost sidecar therefore cannot prevent non-ghost recovery, and clearing one domain cannot erase the other domain’s evidence.

## Run-outcome evidence removal

Run-outcome discard clears only:

```text
forest_run_outcome_recovery_<active save namespace>
```

It does not rewrite current mood, return state, last-run summary, route counters, ghost data, or best distance.

For a valid journal that conflicts with live state, `discardUnresolvedPending(RUN_OUTCOME)` retries first, then removes only the journal while preserving live state exactly as found.

## Ghost sidecar compatibility

Ghost maintenance understands both sidecar generations.

### Version 1

```text
24 bytes
accepted distance
frame count
64-bit FNV frame fingerprint
```

Version-1 evidence is readable for compatibility. The legacy fingerprint identifies frames only and does not cryptographically bind distance.

### Version 2

```text
56 bytes
accepted distance
frame count
64-bit FNV frame fingerprint
32-byte SHA-256 digest
```

Version-2 SHA-256 binds accepted distance, frame count, and every persisted frame field. Both FNV and SHA-256 must match during full validation.

Current store APIs write version 2 only. Digest-less objects cannot be emitted as new sidecars.

## Ghost evidence inspection

The ghost domain distinguishes:

```text
CLEAN(no_evidence)
CLEAN(valid_manifest)
PENDING(valid_receipt)
CORRUPT(invalid_receipt)
CORRUPT(invalid_manifest)
CORRUPT(manifest_artifact_mismatch)
IO_FAILURE(...)
```

Explicit inspection always loads the durable ghost and checks the manifest association.

For version 2 it verifies:

- structural frame validity;
- frame count;
- FNV fingerprint;
- SHA-256 over manifest distance and frame payload.

This detects frame-only, digest-only, count-only, and distance-only alteration.

For version 1 it applies the historical FNV compatibility check. Inspection is read-only and does not silently upgrade a healthy legacy manifest.

Canonical safe recovery may:

- reconstruct or replace a strong manifest from a matching receipt;
- upgrade a matching version-1 receipt or manifest before distance repair;
- repair a lower best distance;
- recognize an already-applied threshold;
- abandon a receipt whose candidate ghost never became durable.

## Ghost evidence removal

Ghost-domain evidence consists of:

```text
<ghost>.promotion[.bak|.new]
<ghost>.manifest[.bak|.new]
```

Targeted cleanup is identity-aware:

- corrupt receipt removal preserves a valid manifest matching the ghost;
- corrupt manifest removal clears only the manifest sidecar;
- manifest/artifact mismatch removal clears only the invalid association;
- the ghost frame file and backup are preserved;
- best distance is not rewritten by discard itself;
- run-outcome evidence is not touched.

Unknown I/O failure remains fail-closed and is not force-cleared.

## Cryptographic boundary

SHA-256 is used as collision-resistant local identity, not as authentication.

Maintenance must not describe a valid digest as proof that a trusted party created the file. A process with filesystem write access can replace both artifact and digest. No secret key, MAC, certificate, or digital signature is involved.

The maintenance surface also never logs the digest itself.

## Debug-only Activity command surface

`MainActivity` accepts maintenance commands only when Android marks the application debuggable.

Intent extras:

```text
recovery_action = inspect | recover | discard_corrupt | discard_pending
recovery_domain = RUN_OUTCOME | GHOST_PROMOTION
```

`recovery_domain` is required only for discard actions.

Commands are one-shot. Both extras are removed in `finally`, including rejection paths, preventing Activity recreation from repeating a mutation.

### Live-session rule

Because the Activity is `singleTask`, ADB can deliver a command through `onNewIntent` while gameplay or the ghost worker is active.

A reused Activity permits only `inspect`. It rejects `recover`, `discard_corrupt`, and `discard_pending` with `reason=active_session` before constructing maintenance handlers.

Mutating commands run only during cold `onCreate`, after `SaveIntegrityManager.repair(...)` and before `GameView` construction.

## ADB usage

Debug application ID:

```text
com.anurag9000.forestrun.debug
```

### Inspect a live debug session

```bash
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action inspect
```

### Safely retry both domains

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action recover
```

### Discard confirmed corrupt run-outcome evidence

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action discard_corrupt \
  --es recovery_domain RUN_OUTCOME
```

### Retry, then discard an unresolved run-outcome journal

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action discard_pending \
  --es recovery_domain RUN_OUTCOME
```

### Discard confirmed corrupt ghost evidence

The same command handles a corrupt receipt, corrupt manifest, or manifest/artifact mismatch. A healthy manifest remains when only the receipt is corrupt.

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action discard_corrupt \
  --es recovery_domain GHOST_PROMOTION
```

## Logging

Filter:

```bash
adb logcat -s ForestRunLaunch
```

Prefix:

```text
FOREST_RUN_RECOVERY_MAINTENANCE
```

Inspection and recovery log only the support summary. Discard logs include action, domain, disposition, before state, and after state.

Rejected commands use fixed reasons:

```text
not_debuggable
active_session
invalid_domain
unknown_action
```

## Tests and contracts

Coverage includes:

- exact domain-handler cardinality;
- independent inspection and retry;
- corrupt-only deletion;
- recover-before-discard ordering;
- no deletion after read failure;
- complete non-ghost recovery and conflict preservation;
- version-2 receipt/manifest inspection;
- version-1 compatibility and recovery upgrade;
- digest tampering;
- distance tampering;
- frame and count mismatch;
- valid-manifest preservation during receipt cleanup;
- ghost preservation during manifest cleanup;
- debug gating, cold-start mutation, one-shot extras, and payload-free logs.

Source contracts require the maintenance adapter to pass manifest distance, frame count, FNV, and optional SHA-256 into `GhostRunIdentity.matches(...)` and forbid a direct fingerprint-only validator.

## Evidence boundary

Focused Kotlin/JVM compilation passed for the identity and recovery core and production manager surface. A production-shaped maintenance adapter compile validates the distance-bound identity call. Executable golden-vector, versioned-codec, state-machine, maintenance-policy, and cold/live launch harnesses passed.

The checked-in JUnit and Robolectric tests were not executed through an exact-head Android Gradle environment in this session. Physical-device ADB acceptance remains separate.
