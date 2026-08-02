# Forest Run — Recoverable Ghost Promotion

## Purpose

A best run has two durable artifacts that must describe the same accepted result:

- the validated ghost frame file;
- the best-distance threshold used to decide whether a later run may replace it.

Previously, `GhostPersistenceManager.saveBestRunAsync(...)` reported success when work was scheduled. `RunOutcomePersistenceCoordinator` then advanced best distance on the render thread before the asynchronous `AtomicFile` write was known to have completed.

That left two process-death windows:

```text
accepted ghost → best distance advanced → ghost write never completes
```

and:

```text
ghost write completes → process dies before best distance advances
```

The first can preserve an old ghost behind a newer threshold. The second can preserve a newer ghost while allowing a shorter later run to replace it.

Ghost promotion is now one recoverable single-worker protocol.

## Ownership

`RunOutcomePersistenceCoordinator` owns only candidate eligibility:

1. normalize completed distance;
2. compare it with `GhostPersistenceManager.bestDistanceFloor(...)`;
3. require a strictly better distance and a non-empty detached ghost;
4. submit one distance-aware promotion request.

It does not write best distance directly.

`GhostPersistenceManager` owns:

- immediate in-memory publication;
- pending-distance admission;
- single-worker ordering;
- startup and pre-write recovery;
- durable promotion telemetry.

`GhostPromotionRecoveryCoordinator` owns the durable protocol.

## Promotion receipt

Before the ghost file changes, `AtomicFileGhostPromotionReceiptStore` writes:

```text
target distance
frame count
64-bit frame fingerprint
```

The receipt is:

- versioned with magic `FRGP`;
- fixed at 24 bytes;
- written through `AtomicFile`;
- scoped to the active ghost filename as `<ghost>.promotion`;
- validated before replacing existing evidence.

The fingerprint covers the raw persisted identity of every frame:

```text
frame count
timestamp bits
x bits
y bits
state ordinal
scaleX bits
scaleY bits
```

It is a compact local recovery identity, not a cryptographic authenticity claim.

## Durable worker sequence

For an accepted candidate:

```text
synchronously write promotion receipt
→ atomically write validated ghost file
→ synchronously write max(current best, candidate distance)
→ clear promotion receipt
```

The best-distance write is synchronous because receipt clearing must never precede its durability.

The worker retains `max(current best, candidate distance)` as a defensive monotonic rule. A delayed or repeated operation cannot lower the threshold.

## Immediate playback

Accepted frames remain immediately available in memory before disk work starts. `PublishedGhost` carries:

- an immutable frame snapshot;
- accepted distance;
- frame fingerprint.

This preserves the existing restart experience while allowing durable work to stay off the render thread.

`RunOutcomeCommitResult.ghostPromoted` means the candidate was accepted into this worker pipeline. It does not mean disk completion occurred before `commit(...)` returned.

## Pending-distance floor

`GhostPersistenceManager.bestDistanceFloor(...)` returns:

```text
max(durable best distance, accepted in-memory promotion distance)
```

This prevents a shorter run from entering the worker queue while a longer accepted promotion is still pending.

The gate also exists inside `GhostPersistenceManager`, not only in the terminal coordinator. Legacy or direct callers therefore cannot regress a pending promotion.

The compatibility overload submits at the current floor, preserving its historical ability to replace a ghost without changing best distance.

## Recovery decisions

A pending receipt is recovered by loading the durable ghost and comparing:

```text
frame count
fingerprint
```

### Matching durable ghost

When the ghost matches:

- if best distance is lower, recovery writes the receipt distance;
- if best distance is already equal or higher, it does not rewrite it;
- the receipt is cleared only after the threshold is durable.

Dispositions:

- `REPAIRED_DISTANCE`;
- `ALREADY_APPLIED`.

### Nonmatching durable ghost

A mismatch means the candidate ghost never became the durable artifact represented by the receipt. Recovery:

- does not advance best distance;
- does not modify the existing ghost;
- clears the stale uncommitted receipt.

Disposition:

- `ABANDONED_UNWRITTEN_GHOST`.

### Corrupt or inaccessible evidence

Unknown versions, malformed receipts, and unrecoverable I/O return:

- `CORRUPT_RECEIPT`;
- `IO_FAILURE`.

Both block new ghost promotions. Non-ghost terminal progression remains independently recoverable and may still complete.

## Queue ordering

A single daemon executor serializes promotions.

When candidate B is queued behind candidate A:

1. A completes or leaves recovery evidence;
2. B first recovers A's receipt;
3. only then may B create its own receipt and write its ghost.

If A fails before ghost durability, only A's matching in-memory publication may be removed. A newer B publication is protected by distance and fingerprint identity.

## Recovery triggers

Recovery is attempted:

- when `AndroidRunOutcomePersistenceSink` is created;
- when a new manager request arrives and no worker is active;
- at the beginning of every worker task;
- before disk fallback in `loadLatest(...)`;
- through the internal test/recovery entry point.

## Relationship to the terminal outcome journal

There are now two purpose-specific recovery records:

### Non-ghost outcome journal

Protects:

- forest mood;
- return state;
- last-run summary;
- pacifist-route count.

### Ghost promotion receipt

Protects:

- ghost artifact identity;
- best-distance promotion.

These records are independently recoverable. They do not form one atomic transaction spanning every terminal side effect. A process may complete one recovery protocol before the other, but each protocol prevents its own duplicate or mismatched durable state.

## Validation surface

`GhostPromotionRecoveryCoordinatorTest` covers:

- receipt → ghost → distance → clear ordering;
- distance repair;
- already-applied recognition;
- abandoned unwritten ghost;
- failed ghost write;
- failed distance write and later repair;
- corrupt receipt blocking;
- fingerprint sensitivity across every persisted field.

`GhostPromotionReceiptStoreTest` covers:

- complete round trip;
- empty and cleared states;
- truncation, trailing bytes, and unknown version;
- invalid distance and frame count;
- preservation of existing evidence after rejected replacement.

`GhostPersistenceManagerTest` covers production integration for:

- immediate in-memory playback;
- durable ghost and distance completion;
- startup distance repair;
- mismatch abandonment.

`GhostPersistenceManagerAdmissionTest` covers:

- direct stale-candidate rejection;
- pending-distance floor behavior;
- compatibility overload behavior.

`RunOutcomePersistenceIntegrationTest` covers terminal submission and ensures a shorter next run cannot replace a longer pending promotion.

`test_ghost_promotion_recovery_contract.py` locks ownership, ordering, receipt structure, fingerprint coverage, key parity, stale admission, and matching-publication cleanup.

## Remaining limitations

- A mismatch created by an older build before promotion receipts existed cannot be reconstructed because the ghost file itself does not encode its run distance.
- A 64-bit noncryptographic fingerprint has a theoretical collision risk.
- Corrupt receipt evidence has no automated repair or user-facing remediation flow.
- Save compatibility namespaces should not be switched concurrently with an active worker; production does not perform that operation during a run, but the boundary remains an explicit maintenance constraint.
- Exact-head Android Gradle, emulator, and physical-device execution remain separate evidence gates.
