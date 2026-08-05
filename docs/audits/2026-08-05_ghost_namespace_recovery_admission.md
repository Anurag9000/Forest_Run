# Ghost Namespace Recovery Admission Audit — 2026-08-05

## Scope

This audit records the replacement of Forest Run's global pending-write recovery gate with namespace-scoped activity admission.

The tranche began from verified canonical head:

```text
d7dd25d38fe46e8bef3f213c2a9eaedd243ee29f
```

That head already contained:

- immutable `GhostPersistenceNamespace` capture;
- namespace-bound ghost and distance storage;
- namespace-keyed immediate publications;
- namespace-stable queued workers;
- namespace-bound recovery maintenance;
- a single global serial ghost executor.

The remaining defect was narrower: `GhostPersistenceManager` stored only one global `pendingWrite` future. Explicit recovery returned `IO_FAILURE` whenever that future was active, even when the active worker and requested recovery targeted different immutable namespaces.

## Previous behavior

The manager used:

```kotlin
private var pendingWrite: Future<*>? = null
```

Both pre-write recovery and explicit recovery inspected that one future.

The resulting admission matrix was:

```text
active primary worker + primary recovery        blocked
active primary worker + compatibility recovery  blocked
active compatibility worker + primary recovery  blocked
active compatibility worker + same compat       blocked
```

The same-namespace cases were correctly fail-closed. The cross-namespace cases were unnecessarily blocked because their receipt, manifest, ghost, and preference stores were already disjoint.

## Correctness objective

Preserve:

- one global daemon single-thread executor;
- queued promotion order;
- immediate namespace-keyed publication;
- receipt → ghost → manifest → distance → receipt-clear durability;
- same-namespace fail-closed recovery;
- existing telemetry;
- existing `awaitPendingWrites(...)` semantics;
- all receipt/manifest/SHA-256 compatibility behavior.

Change only recovery admission:

```text
unfinished task in requested namespace → block
unfinished task in another namespace   → permit recovery
```

## New activity owner

Added:

```text
app/src/main/java/com/anurag9000/forestrun/systems/
GhostNamespacePendingWriteRegistry.kt
```

The registry owns:

```kotlin
ConcurrentHashMap<GhostPersistenceNamespace, Future<*>>
```

It stores only the latest submitted task for each namespace.

Operations:

```text
track(namespace, task)
isActive(namespace)
clear()
```

`isActive(...)`:

1. looks up only the requested namespace;
2. returns `true` while its latest task is unfinished;
3. removes a completed or cancelled task with `remove(namespace, task)`;
4. returns `false` afterward.

The exact-value removal is important. If an older task completes after a newer task has replaced it, the older task cannot clear the newer namespace activity marker.

## Manager integration

`GhostPersistenceManager` now owns:

```kotlin
private val pendingWrites = GhostNamespacePendingWriteRegistry()

@Volatile
private var latestSubmittedWrite: Future<*>? = null
```

The two fields have different responsibilities.

### `pendingWrites`

Used by:

- pre-write recovery admission;
- explicit recovery admission;
- test-state reset.

It is always queried with the already captured immutable namespace.

### `latestSubmittedWrite`

Used only by:

- `awaitPendingWrites(...)`;
- assignment after successful executor submission;
- test-state reset.

It is not a recovery gate.

Because the executor is serial, waiting for the latest submitted future still waits for every earlier queued task.

## New behavior matrix

```text
active primary worker + primary recovery        blocked with IO_FAILURE
active primary worker + compatibility recovery  permitted
active compatibility worker + primary recovery  permitted
active compatibility worker + same compat       blocked with IO_FAILURE
```

Pre-write recovery uses the same matrix.

A cross-namespace recovery may execute on the caller thread while the executor writes another namespace. This does not introduce same-artifact concurrency because both operations use immutable namespace-bound stores.

## Same-namespace queue behavior

For two writes targeting one namespace:

```text
write A submitted
→ registry[A] = future A
→ write B submitted
→ registry[A] = future B
```

The single executor still runs A before B.

If A completes while B remains queued, `isActive(A)` observes future B and continues to block recovery. B therefore remains the authoritative activity marker.

The worker itself still begins with canonical recovery before attempting a new persistence transaction, preserving recovery-before-persist ordering for queued same-namespace writes.

## Files changed

Production:

- added `GhostNamespacePendingWriteRegistry.kt`;
- modified `GhostPersistenceManager.kt`.

Tests and contracts:

- added `GhostNamespacePendingWriteRegistryTest.kt`;
- migrated `scripts/test_ghost_persistence_namespace_contract.py`;
- migrated `scripts/test_ghost_promotion_recovery_contract.py`.

Documentation:

- updated `docs/GHOST_PERSISTENCE_NAMESPACES.md`;
- updated `docs/GHOST_PROMOTION_RECOVERY.md`;
- updated `docs/ARCHITECTURE.md`;
- updated `docs/AUDIT_LEDGER.md` in the final reconciliation commit.

## Exact manager diff boundary

The production manager replacement contained only:

1. registry construction;
2. renaming the global drain pointer from `pendingWrite` to `latestSubmittedWrite`;
3. namespace-scoped pre-write recovery admission;
4. task registration after executor submission;
5. namespace-scoped explicit recovery admission;
6. drain-pointer use in `awaitPendingWrites(...)`;
7. clearing both activity owners during test reset.

Unchanged:

- executor type and thread properties;
- validation and distance admission;
- publication construction;
- SHA-256 and FNV identity;
- worker recovery-before-persist sequence;
- durable persistence coordinator;
- failure cleanup;
- telemetry;
- disk fallback;
- sidecar formats;
- ghost frame codec.

## Test coverage

`GhostNamespacePendingWriteRegistryTest` covers:

- a primary task blocking primary but not compatibility;
- completed tasks no longer blocking;
- cancelled tasks no longer blocking;
- a newer same-namespace task remaining authoritative after an older task completes;
- clearing all namespace activity markers.

The namespace source contract requires:

- registry ownership in the manager;
- namespace-keyed `isActive(...)` checks in save and recovery paths;
- no obsolete global `pendingWrite` admission shape;
- drain-only use of `latestSubmittedWrite`;
- exact completed-task eviction;
- registry test coverage.

The broader promotion contract retains checks for:

- single-worker ordering;
- immediate publication before submission;
- worker recovery before persist;
- receipt → ghost → manifest → distance → clear order;
- version-1 compatibility and version-2 SHA-256 identity;
- corruption blocking;
- namespace-bound disk fallback;
- identity-aware publication cleanup.

## Focused validation

A focused Kotlin/JVM compilation and executable harness passed for the registry.

The harness verified:

- primary activity does not mark compatibility active;
- an older completed task cannot unblock a newer same-namespace task;
- completed and cancelled tasks are evicted;
- clear removes all namespace activity.

Result:

```text
namespace-scoped pending write checks passed
```

The exact `GhostPersistenceManager` commit diff was inspected and contained only the intended admission/tracking changes.

## Evidence boundary

Not executed in this environment:

- complete exact-head Gradle compilation;
- full JUnit/Robolectric suite;
- Android lint;
- emulator execution;
- physical-device process-death recovery;
- physical-device cross-namespace recovery during active I/O;
- thermal or disk-pressure behavior;
- signed release or store delivery.

The connector exposes no successful push-triggered check runs for this head. Empty status or workflow lists must not be interpreted as green CI.

## Remaining debt

The global executor remains deliberately serial across namespaces. Primary and compatibility queued writes cannot execute in parallel.

This is now a throughput/latency limitation rather than a recovery-admission correctness defect. Changing it would require per-namespace executors or a keyed serial scheduler plus new telemetry, lifecycle, shutdown, ordering, and disk-pressure evidence.

No parallel-executor change is included in this tranche.
