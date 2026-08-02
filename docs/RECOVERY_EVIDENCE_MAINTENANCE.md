# Forest Run — Recovery Evidence Maintenance

## Purpose

Forest Run fails closed when recovery evidence is corrupt, unreadable, or conflicts with live persistence state. This prevents silent double-counting and speculative overwrites, but unresolved evidence needs an explicit support path.

`AndroidRecoveryEvidenceMaintenance` covers two independent domains:

- `RUN_OUTCOME` — forest mood, return state, completed summary, and pacifist-route count;
- `GHOST_PROMOTION` — the transient promotion receipt, persistent artifact manifest, durable ghost, and best-distance threshold.

Inspection, safe retry, and destructive evidence removal are deliberately separate. No destructive operation runs automatically.

## Evidence states

Each domain reports:

```text
CLEAN      no unresolved evidence
PENDING    syntactically valid transient evidence awaits recovery
CORRUPT    evidence cannot be decoded or validated
BLOCKED    valid run-outcome evidence conflicts with live state or cannot be applied
IO_FAILURE evidence could not be read, recovered, or cleared reliably
```

The support summary includes only domain, state, and fixed detail codes. It excludes:

- scores and distances;
- summary text;
- entity identity;
- ghost frames;
- fingerprints;
- timestamps and progression counters.

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
6. verifies the domain reports `CLEAN` after deletion.

Read failure is never interpreted as permission to erase unknown data.

## Domain isolation

### Run-outcome handler

`MaintenanceRunOutcomePersistenceSink`:

- reads and writes complete mood, return, summary, and route snapshots;
- never publishes a ghost;
- never advances best distance;
- never constructs `AndroidRunOutcomePersistenceSink`, whose production initialization also touches ghost recovery.

### Ghost handler

The ghost handler owns only:

- `AtomicFileGhostPromotionReceiptStore`;
- `AtomicFileGhostArtifactManifestStore`;
- `GhostPromotionRecoveryCoordinator`;
- `AndroidGhostPromotionArtifactStore`.

It never opens the run-outcome SharedPreferences journal.

A corrupt receipt or manifest therefore cannot prevent non-ghost recovery, and clearing one domain cannot erase the other domain’s evidence.

## Run-outcome evidence removal

Run-outcome discard clears only:

```text
forest_run_outcome_recovery_<active save namespace>
```

It does not rewrite current:

- forest mood;
- return state;
- last-run summary;
- route counters;
- ghost data;
- best distance.

For a valid journal that conflicts with live state, `discardUnresolvedPending(RUN_OUTCOME)` retries first, then removes only the journal while preserving live state exactly as found.

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

A valid manifest is checked against the durable ghost by frame count, structural validity, and fingerprint during explicit inspection.

Canonical safe recovery may:

- reconstruct or replace a manifest from a matching receipt;
- repair a lower best distance from a matching receipt;
- repair a lower best distance from a matching manifest after the receipt is gone;
- recognize an already-applied threshold;
- abandon a receipt whose candidate ghost never became durable.

## Ghost evidence removal

Ghost-domain evidence consists of:

```text
<ghost>.promotion[.bak|.new]
<ghost>.manifest[.bak|.new]
```

Targeted cleanup is identity-aware:

- corrupt receipt removal preserves a valid manifest that matches the durable ghost;
- corrupt manifest removal clears the manifest sidecar but preserves the ghost frame file;
- manifest/artifact mismatch removal clears only the invalid association;
- best distance is never rewritten by the discard operation itself;
- run-outcome evidence is never touched.

The following remain untouched by direct evidence removal:

- the ghost frame artifact and backup;
- current best distance;
- forest mood, return state, summary, and route counters.

Unknown I/O failure remains fail-closed and is not force-cleared.

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

Because the manifest uses `singleTask`, ADB can deliver a command through `onNewIntent` while gameplay or the ghost worker is active.

A reused Activity permits only:

```text
inspect
```

It rejects:

```text
recover
discard_corrupt
discard_pending
```

with `reason=active_session` before constructing maintenance handlers.

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

The same command handles a corrupt receipt, corrupt manifest, or manifest/artifact mismatch. Healthy matching manifest evidence is preserved when only the receipt is corrupt.

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action discard_corrupt \
  --es recovery_domain GHOST_PROMOTION
```

## Log format

Filter:

```bash
adb logcat -s ForestRunLaunch
```

Prefix:

```text
FOREST_RUN_RECOVERY_MAINTENANCE
```

Inspection and recovery log only the support summary. Discard logs include:

```text
action
domain
disposition
before state
after state
```

Rejected commands use fixed reasons:

```text
not_debuggable
active_session
invalid_domain
unknown_action
```

## Tests and contracts

`RecoveryEvidenceMaintenanceCoordinatorTest` covers domain cardinality, independent inspection, status-only output, safe retry, corrupt-only deletion, recover-before-discard ordering, no deletion after read failure, and clear failure reporting.

`RecoveryEvidenceMaintenanceIntegrationTest` covers:

- clean inspection;
- complete non-ghost journal recovery;
- corrupt and conflicting run-journal behavior;
- matching receipt repair and manifest creation;
- receipt-free manifest repair;
- corrupt receipt removal that preserves a valid manifest;
- corrupt manifest retention and explicit removal;
- manifest/artifact mismatch diagnosis and evidence-only removal.

Source contracts lock:

- safe/destructive separation;
- domain-isolated adapters;
- no deletion after I/O failure;
- distinct receipt and manifest diagnosis;
- preservation of a valid manifest during receipt cleanup;
- cold-start mutation;
- debug-only access;
- inspection-only reused Activities;
- one-shot extras;
- payload-free logs.

## Evidence boundary

Focused Kotlin/JVM compilation passed for the policy core, production-shaped handlers, manifest-aware ghost adapter, and launch dispatcher.

Executable harnesses passed for maintenance policy, cold/live launch behavior, manifest-only distance repair, corrupt manifest blocking, and selective evidence cleanup.

The checked-in JUnit and Robolectric tests were not executed through an exact-head Android Gradle environment in this session. Physical-device ADB acceptance remains separate.
