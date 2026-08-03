# Forest Run — Terminal Run Persistence Contract

## Purpose

A terminal collision produces permanent side effects across immediate game feel, relationship history, authored presentation, completed summary, progression state, ghost publication, and best-distance eligibility.

Responsibilities are split into explicit owners:

- `TerminalHitImpactCoordinator` — immediate terminal HIT effect ordering and post-impact capture boundary;
- `TerminalHitOutcomeCoordinator` — deterministic terminal completion;
- `RunOutcomePersistenceCoordinator` — exactly-once token and non-ghost recovery;
- `SharedPreferencesRunOutcomeRecoveryStore` — non-ghost before/after journal;
- `SharedPreferencesRunOutcomeSummarySnapshotStore` — atomic summary/route snapshot;
- `GhostPersistenceManager` — immediate publication, distance floor, and worker ordering;
- `GhostPromotionRecoveryCoordinator` — recoverable ghost artifact bundle;
- `AtomicFileGhostPromotionReceiptStore` — transient sidecar;
- `AtomicFileGhostArtifactManifestStore` — persistent artifact-to-distance sidecar;
- `GhostRunIdentity` — legacy FNV compatibility plus distance-bound SHA-256;
- `RecoveryEvidenceMaintenanceCoordinator` — explicit inspection and repair policy.

## Live terminal boundary

`TerminalHitImpactCoordinator` owns:

```text
record run hit
→ suppress ghost for 1.35 seconds
→ Player rest
→ camera shake
→ hit SFX
→ rest music
→ long haptic
→ post-impact capture callback
```

The callback then captures detached ghost, killer, biome, route tier, and Player presentation coordinates.

`GameView` retains only collision ownership and run-state orchestration:

1. invoke the impact coordinator once;
2. capture terminal inputs after impact;
3. call `terminalHitOutcome.complete(...)` once;
4. accept the completed summary;
5. trigger death timing;
6. enter `DYING`.

The HIT branch does not directly call Player, ghost, camera, SFX, music, or haptic terminal-impact owners. It also does not compose authored copy, resolve the rest quote, record persistent relationship history, or write terminal persistence stores.

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

Before ghost eligibility, production synchronously records raw completed summary, mood before/after, return before/after, route count before/after, and checkpoint phase.

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
- exact expected KIND, MERCIFUL, or PEACEFUL count.

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

## Ghost frame and sidecar formats

The ghost frame file remains `SaveManager` format version 2.

Every accepted promotion uses:

```text
<ghost>.promotion  transient AtomicFile receipt
<ghost>.manifest   persistent AtomicFile manifest
```

### Sidecar version 1

Existing 24-byte records remain readable and contain distance, frame count, and 64-bit FNV frame fingerprint.

### Sidecar version 2

All new writes are 56 bytes and add a 32-byte SHA-256 digest.

The digest covers accepted distance raw bits, frame count, and every persisted frame field. Version-2 matching requires both FNV and SHA-256.

Current store APIs reject digest-less new objects. Version-1 evidence can only be read from existing bytes.

SHA-256 is used for collision-resistant identity, not authenticated provenance.

## Ghost worker order

The daemon worker performs:

```text
version-2 promotion receipt
→ validated ghost file
→ version-2 artifact manifest
→ synchronous max(current best, candidate distance)
→ receipt clear
```

The threshold cannot advance unless receipt, ghost, and strong manifest are durable.

Accepted frames remain immediately visible in memory. The publication identity includes distance, FNV, and SHA-256.

`ghostPromoted == true` means accepted into this pipeline, not necessarily durable before `commit(...)` returns.

## Ghost recovery

### Pending version-2 receipt

Recovery loads the durable ghost and validates structure, count, FNV, and SHA-256 over receipt distance plus frames.

Matching artifact:

```text
ensure strong manifest
→ repair distance when lower
→ clear receipt
```

A modified receipt distance or digest cannot authorize advancement.

### Pending version-1 receipt

Recovery validates the historical FNV frame identity, computes a distance-bound SHA-256 digest, writes a version-2 manifest, and only then repairs distance.

### Nonmatching receipt

Recovery clears the stale receipt, preserves ghost and threshold, and validates any older manifest with the already-loaded ghost.

### Manifest with no receipt

When best distance already meets or exceeds manifest distance, recovery returns `ALREADY_APPLIED` without loading the ghost.

When best distance is lower, recovery loads the ghost and validates the association. Version-1 manifests are upgraded to version 2 before distance repair.

Frame, count, digest, or distance mismatch produces `CORRUPT_MANIFEST`.

### Blocking dispositions

```text
CORRUPT_RECEIPT
CORRUPT_MANIFEST
IO_FAILURE
```

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

The ghost worker continues independently with its versioned receipt/manifest sequence.

The protocols remain independently recoverable, not one global transaction across relationship history, presentation, progression, ghost storage, and best distance.

## Recovery triggers

Non-ghost recovery runs during coordinator construction and `resetForNewRun()`.

Ghost recovery runs:

- during production sink construction;
- before a new manager request when idle;
- at the start of each worker task;
- before `loadLatest(...)` disk fallback;
- through explicit cold-start maintenance.

Disk fallback computes the publication SHA-256 using loaded best distance plus loaded frames.

## Maintenance

`AndroidRecoveryEvidenceMaintenance` exposes `RUN_OUTCOME` and `GHOST_PROMOTION` through inspection, safe retry, corrupt discard, and unresolved-pending discard.

Policy guarantees:

- safe retry preserves corrupt evidence;
- pending discard retries canonical recovery first;
- I/O failure never authorizes deletion;
- run maintenance cannot publish ghosts or advance distance;
- ghost maintenance never opens the run journal;
- version-2 inspection validates manifest distance and frame payload with SHA-256;
- version-1 inspection retains FNV compatibility;
- corrupt receipt cleanup preserves a valid manifest;
- corrupt/mismatched manifest cleanup preserves the ghost frame file;
- logs expose only states and fixed detail codes.

Mutating commands require a debuggable cold start after save repair and before `GameView`. A reused Activity permits inspection only.

## Failure model

Persistence fails closed against:

- duplicate terminal delivery;
- malformed/incomplete non-ghost journals;
- noncanonical after-states and live conflicts;
- failed journal/snapshot verification;
- malformed v1/v2 receipt or manifest;
- noncanonical SHA-256 text;
- frame, count, FNV, digest, or distance mismatch;
- failed legacy-to-strong manifest upgrade;
- failed manifest, threshold, or evidence clear;
- stale direct candidates below the distance floor;
- maintenance read/clear failure;
- live-session mutation commands.

## Test surface

Coverage includes:

- immediate terminal-impact ordering and fail-fast capture behavior;
- post-impact capture before terminal completion;
- terminal completion ordering and authored presentation;
- exactly-once token behavior;
- non-ghost before/after recovery;
- atomic summary/route persistence;
- route-ceiling parity;
- independent distance-bound SHA-256 golden vector;
- version-1 24-byte sidecar reads;
- version-2 56-byte sidecar writes;
- digest-less new-write rejection;
- receipt → ghost → strong manifest → distance → clear ordering;
- legacy upgrade before threshold repair;
- distance-only and digest-only tampering;
- frame/count/state/scale/timestamp sensitivity;
- no-ghost-load already-applied path;
- pending-distance admission and equal-distance replacement;
- maintenance diagnosis and selective removal;
- source ownership, synchronous writes, key parity, and lazy validation.

Focused Kotlin compilation and executable harnesses passed for the terminal-impact coordinator and strong ghost identity/promotion surfaces. The impact and completion source contracts passed together against the extracted HIT/capture/adapter structure.

Exact-head Gradle/JUnit, Robolectric, lint, build, emulator, physical-device, and ADB execution were not completed in this session.

## Remaining limitations

- The complete collision dispatcher and nonterminal live-effect adapter remain in `GameView`.
- Ghosts and mismatches predating manifests cannot be reconstructed retroactively.
- Version-1 sidecars remain weak until replay requires upgrade.
- The already-applied automatic path avoids repeated full hashing; maintenance validates fully.
- SHA-256 does not authenticate a trusted writer.
- Non-ghost and ghost records are not one global transaction.
- Compatibility namespace switching during an active worker or maintenance instance is unsupported.
- Remediation is debug/support tooling, not an end-user UI.
- Physical-device ADB acceptance remains outstanding.
