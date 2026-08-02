# Forest Run — Terminal Run Persistence Contract

## Purpose

A terminal collision produces permanent side effects across relationship history, authored presentation, completed summary, progression state, ghost publication, and best-distance eligibility.

These responsibilities are split into explicit owners:

- `TerminalHitOutcomeCoordinator` — deterministic terminal completion;
- `RunOutcomePersistenceCoordinator` — exactly-once token and non-ghost recovery;
- `SharedPreferencesRunOutcomeRecoveryStore` — non-ghost before/after journal;
- `SharedPreferencesRunOutcomeSummarySnapshotStore` — atomic summary/route snapshot;
- `GhostPersistenceManager` — immediate publication, distance floor, and worker ordering;
- `GhostPromotionRecoveryCoordinator` — recoverable ghost artifact bundle;
- `AtomicFileGhostPromotionReceiptStore` — transient in-progress evidence;
- `AtomicFileGhostArtifactManifestStore` — persistent artifact-to-distance identity;
- `RecoveryEvidenceMaintenanceCoordinator` — explicit inspection and repair policy.

## GameView boundary

`GameView` retains the live impact and run-state sequence:

1. record the run hit;
2. suppress ghost visibility;
3. trigger Player rest and immediate feedback;
4. detach the ghost buffer in O(1);
5. resolve the killer;
6. call `terminalHitOutcome.complete(...)` once;
7. accept the completed summary;
8. enter `DYING`.

The HIT branch does not directly compose authored copy, resolve the rest quote, record persistent relationship history, or write terminal persistence stores.

## Completion order

`TerminalHitOutcomeCoordinator.complete(...)` preserves:

```text
known persistent relationship hit
→ authored HIT presentation
→ exactly one summary snapshot
→ rest quote resolution
→ completed summary copy
→ exactly one RunOutcomeCommitter call
→ result return
```

## Exactly-once token

Each `RunOutcomePersistenceCoordinator` owns one terminal token.

`commit(...)`:

1. rejects duplicates with `ALREADY_COMMITTED`;
2. consumes the token before mode or storage gates;
3. returns `NON_PERSISTENT_RUN` without writes when progression is disabled;
4. returns `RECOVERY_BLOCKED` for unresolved corrupt/conflicting non-ghost evidence;
5. otherwise starts persistence.

Only fresh-run and encounter-scenario preparation reopen the token, and both retry non-ghost recovery first.

## Non-ghost journal

Before ghost eligibility, production synchronously records:

- raw completed summary;
- mood before/expected after-state;
- return before/expected after-state;
- route count before/expected after-state;
- checkpoint phase.

Recovery compares actual state with both snapshots:

```text
actual == after  → accept without replay
actual == before → write after and verify
otherwise        → retain evidence and block
```

This prevents duplicate counters when a write succeeds before process death or before checkpoint persistence.

## Atomic summary and route snapshot

`SaveManager.saveLastRunSummary(...)` also increments a route count and is not safe to replay.

`SharedPreferencesRunOutcomeSummarySnapshotStore` writes in one synchronous transaction:

- sanitized `last_run_*` values;
- the exact expected KIND, MERCIFUL, or PEACEFUL count.

`PacifistRouteTier.NONE` leaves its compatibility counter unchanged.

The route ceiling remains:

```kotlin
Int.MAX_VALUE / 16
```

## Ghost eligibility

After the PREPARED journal is durable, the terminal coordinator:

1. normalizes completed distance;
2. reads `GhostPersistenceManager.bestDistanceFloor(...)`;
3. requires a strictly better distance;
4. requires a non-empty detached ghost;
5. submits one distance-aware request.

The floor is:

```text
max(durable best distance, accepted pending distance)
```

The terminal coordinator never writes best distance directly.

## Ghost artifact bundle

The existing ghost frame file remains `SaveManager` format version 2.

Every newly accepted promotion adds:

```text
<ghost>.promotion  transient AtomicFile receipt
<ghost>.manifest   persistent AtomicFile manifest
```

Each sidecar stores:

- distance;
- frame count;
- 64-bit raw-frame fingerprint.

The receipt identifies an in-progress candidate. The manifest permanently binds the completed ghost to its distance after receipt clearing.

## Ghost worker order

The daemon worker performs:

```text
promotion receipt
→ validated ghost file
→ artifact manifest
→ synchronous max(current best, candidate distance)
→ receipt clear
```

The threshold cannot advance unless the receipt, ghost, and manifest are durable.

Accepted frames remain immediately visible in memory. `ghostPromoted == true` means accepted into this pipeline, not necessarily durable before `commit(...)` returns.

## Ghost recovery

### Pending receipt

A receipt requires full ghost validation.

Matching artifact:

```text
ensure/replace manifest
→ repair distance when lower
→ clear receipt
```

Nonmatching artifact:

```text
clear stale receipt
→ preserve ghost and threshold
→ reconcile any older manifest
```

### Manifest with no receipt

When best distance already meets or exceeds manifest distance, recovery returns `ALREADY_APPLIED` without loading the ghost.

When best distance is lower, recovery loads and fingerprints the ghost. Only a matching artifact may raise the threshold.

### Failure dispositions

The following block new promotion:

- `CORRUPT_RECEIPT`;
- `CORRUPT_MANIFEST`;
- `IO_FAILURE`.

Automatic recovery never discards corrupt evidence merely to unblock progress.

## Ordered terminal persistence

The synchronous terminal path is:

```text
claim token
→ reject unresolved non-ghost recovery
→ commit PREPARED journal
→ evaluate durable-or-pending ghost floor
→ optionally submit ghost promotion
→ mood + checkpoint
→ return + checkpoint
→ atomic summary/route + checkpoint
→ clear non-ghost journal
```

The ghost worker continues independently with its receipt/manifest sequence.

The two protocols are independently recoverable, not one global transaction across relationship history, presentation, progression, ghost storage, and best distance.

## Recovery triggers

Non-ghost recovery runs during coordinator construction and `resetForNewRun()`.

Ghost receipt/manifest recovery runs:

- during production sink construction;
- before a new manager request when idle;
- at the start of each worker task;
- before `loadLatest(...)` disk fallback;
- through explicit cold-start maintenance.

## Maintenance

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

Policy guarantees:

- safe retry preserves corrupt evidence;
- pending discard retries canonical recovery first;
- I/O failure never authorizes deletion;
- run maintenance cannot publish ghosts or advance distance;
- ghost maintenance never opens the run journal;
- corrupt receipt cleanup preserves a valid matching manifest;
- corrupt/mismatched manifest cleanup preserves the ghost frame file;
- logs expose only states and fixed detail codes.

Mutating commands require a debuggable cold start after save repair and before `GameView`. A reused Activity permits inspection only.

See `docs/RECOVERY_EVIDENCE_MAINTENANCE.md`.

## Failure model

The persistence surface fails closed against:

- duplicate terminal delivery;
- malformed/incomplete non-ghost journals;
- noncanonical after-states and live conflicts;
- failed journal/snapshot verification;
- malformed receipt or manifest;
- ghost/receipt or ghost/manifest identity mismatch before a required repair;
- failed manifest, threshold, or evidence clear;
- stale direct candidates below the distance floor;
- maintenance read/clear failure;
- live-session mutation commands.

## Test surface

Coverage includes:

- terminal completion ordering and authored presentation;
- exactly-once token behavior;
- non-ghost before/after recovery;
- atomic summary/route persistence;
- route-ceiling parity;
- receipt and manifest codecs;
- receipt → ghost → manifest → distance → clear ordering;
- manifest-write and threshold-write crash windows;
- receipt reconstruction of missing/stale manifests;
- receipt-free manifest repair;
- no-ghost-load already-applied fast path;
- pending-distance admission and equal-distance compatibility replacement;
- maintenance diagnosis and selective evidence removal;
- source ownership, synchronous critical writes, key parity, and lazy validation.

Focused Kotlin compilation and executable harnesses passed for the non-ghost recovery owners, manifest-aware ghost core, manager surface, maintenance adapter, and command router.

Exact-head Gradle/JUnit, Robolectric, lint, build, emulator, physical-device, and ADB execution were not completed in this session.

## Remaining limitations

- Ghosts created before manifests remain compatible but cannot reconstruct a mismatch that already existed.
- The fingerprint is noncryptographic and theoretically collidable.
- The already-applied automatic path avoids repeated full ghost hashing; explicit maintenance inspection performs full identity validation.
- Non-ghost and ghost records are not one global transaction.
- Compatibility namespace switching during an active worker or maintenance instance is unsupported.
- Remediation is debug/support tooling, not an end-user UI.
- Release builds reject maintenance intents.
- Physical-device ADB acceptance remains outstanding.
