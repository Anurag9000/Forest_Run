# Forest Run — Recovery Evidence Maintenance

## Purpose

Forest Run deliberately fails closed when durable recovery evidence is corrupt, unreadable, or conflicts with live persistence state. That protects progression from silent double-counting or speculative overwrites, but it also requires an explicit support path when automatic recovery cannot finish.

`AndroidRecoveryEvidenceMaintenance` provides that path for the two independent recovery domains:

- `RUN_OUTCOME` — forest mood, return state, completed summary, and pacifist-route count;
- `GHOST_PROMOTION` — best-ghost receipt, durable ghost artifact, and best-distance threshold.

The maintenance layer separates inspection, safe retry, and destructive evidence removal. No destructive action runs automatically.

## Evidence states

Each domain reports one of:

```text
CLEAN      no outstanding evidence
PENDING    syntactically valid evidence awaits recovery
CORRUPT    evidence cannot be decoded or validated
BLOCKED    valid run-outcome evidence conflicts with live state or cannot be applied
IO_FAILURE evidence could not be read, recovered, or cleared reliably
```

The stable support summary contains only domain, state, and a fixed detail code. It never includes:

- scores or distances;
- run-summary text;
- entity identity;
- ghost frames;
- frame fingerprints;
- timestamps or progression counters.

Example:

```text
run_outcome=PENDING(valid_journal); ghost_promotion=CORRUPT(invalid_receipt)
```

## Maintenance API

`RecoveryEvidenceMaintenanceCoordinator` requires exactly one handler for each domain and exposes:

```kotlin
inspect()
recoverSafely()
discardCorrupt(domain)
discardUnresolvedPending(domain)
```

### `inspect()`

Reads both evidence domains without applying or deleting anything.

### `recoverSafely()`

Retries only recoverable states:

- `CLEAN` remains unchanged;
- `CORRUPT` remains preserved for diagnosis;
- `PENDING`, `BLOCKED`, or transient `IO_FAILURE` is retried through the domain's canonical recovery owner.

Safe recovery never calls `clearEvidence()` merely because evidence is corrupt.

### `discardCorrupt(domain)`

Clears evidence only when a fresh inspection confirms `CORRUPT` for the selected domain. Clean, pending, blocked, and unreadable evidence returns `NOT_APPLICABLE` or `IO_FAILURE` without deletion.

### `discardUnresolvedPending(domain)`

This is an explicit last-resort operation:

1. inspect the selected domain;
2. refuse clean, corrupt, or unreadable evidence;
3. attempt canonical safe recovery once more;
4. return `RECOVERED_INSTEAD` if that succeeds;
5. clear only evidence still confirmed as `PENDING` or `BLOCKED`;
6. verify the domain reports `CLEAN` after deletion.

A read failure is never interpreted as permission to erase unknown data.

## Domain isolation

The run-outcome maintenance handler uses a dedicated `MaintenanceRunOutcomePersistenceSink`:

- it can read and write complete mood, return, summary, and route snapshots;
- it never publishes a ghost;
- it never advances best distance;
- it never constructs `AndroidRunOutcomePersistenceSink`, whose production initialization also touches ghost recovery.

The ghost handler uses only:

- `AtomicFileGhostPromotionReceiptStore`;
- `GhostPromotionRecoveryCoordinator`;
- `AndroidGhostPromotionArtifactStore`.

It does not access the run-outcome SharedPreferences journal.

Therefore a corrupt ghost receipt cannot prevent a valid non-ghost journal from being recovered, and deliberate removal of one domain does not clear the other domain's evidence.

## What evidence discard does

Evidence removal is intentionally narrower than save reset.

### Run-outcome evidence discard

Clears only the save-namespace recovery journal:

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

If a valid journal conflicts with live mood state, `discardUnresolvedPending(RUN_OUTCOME)` retries first, then removes only the journal while preserving the conflicting live state exactly as found.

### Ghost-promotion evidence discard

Clears only:

```text
<active ghost filename>.promotion
<active ghost filename>.promotion.bak
<active ghost filename>.promotion.new
```

It does not directly delete or rewrite:

- the ghost artifact;
- the ghost backup;
- best distance;
- non-ghost progression evidence.

Canonical ghost recovery normally resolves a valid receipt by repairing distance, recognizing an already-applied promotion, or abandoning a receipt whose ghost never landed. Unknown I/O failure remains fail-closed and is not force-cleared.

## Debug-only Activity command surface

`MainActivity` accepts maintenance commands only when Android marks the application debuggable.

Intent extras:

```text
recovery_action = inspect | recover | discard_corrupt | discard_pending
recovery_domain = RUN_OUTCOME | GHOST_PROMOTION
```

`recovery_domain` is required only for the two discard actions.

All commands are one-shot. `MainActivity` removes both extras in `finally`, including rejection paths, so configuration recreation cannot repeat a maintenance mutation.

### Live-session rule

Because the manifest uses `singleTask`, an ADB launch may arrive through `onNewIntent` while gameplay or the ghost worker is active.

In a reused Activity:

- `inspect` is allowed;
- `recover` is rejected with `reason=active_session`;
- `discard_corrupt` is rejected with `reason=active_session`;
- `discard_pending` is rejected with `reason=active_session`.

Mutating commands are accepted only during cold `onCreate`, after `SaveIntegrityManager.repair(...)` and before `GameView` construction.

## ADB usage

The debug application ID is:

```text
com.anurag9000.forestrun.debug
```

### Inspect without stopping a live debug session

```bash
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action inspect
```

### Safely retry both domains

Stop the app first so the command is processed during cold `onCreate`:

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

### Discard a confirmed corrupt ghost receipt

```bash
adb shell am force-stop com.anurag9000.forestrun.debug
adb shell am start \
  -n com.anurag9000.forestrun.debug/com.anurag9000.forestrun.MainActivity \
  --es recovery_action discard_corrupt \
  --es recovery_domain GHOST_PROMOTION
```

## Log format

Filter by:

```bash
adb logcat -s ForestRunLaunch
```

Prefix:

```text
FOREST_RUN_RECOVERY_MAINTENANCE
```

Inspection and recovery log the status-only support summary. Discard actions log:

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

`RecoveryEvidenceMaintenanceCoordinatorTest` covers:

- exact domain-handler cardinality;
- independent inspection;
- status-only support output;
- safe retry selection;
- corrupt-only deletion;
- recover-before-discard ordering;
- deliberate blocked-journal removal;
- no deletion after read failure;
- clear failure reporting.

`RecoveryEvidenceMaintenanceIntegrationTest` covers Android-backed:

- clean inspection;
- complete non-ghost journal recovery;
- corrupt run journal retention and explicit removal;
- conflicting valid journal retry before evidence-only discard;
- matching ghost receipt distance repair;
- corrupt ghost receipt isolation and explicit removal.

Source contracts lock:

- separation of safe and destructive operations;
- domain-isolated production adapters;
- no deletion after I/O failure;
- cold-start command ordering;
- debug-only access;
- inspection-only reused Activities;
- one-shot extras;
- payload-free logs.

## Evidence boundary

Focused Kotlin/JVM compilation passed for:

- the maintenance policy core;
- Android handler and sink conformance against production-shaped stubs;
- the launch command parser and dispatcher.

Executable harnesses passed for maintenance policy and cold/live launch behavior.

The checked-in JUnit and Robolectric tests have not been executed through an exact-head Android Gradle environment in this session. Physical-device ADB command execution remains a separate acceptance gate.
