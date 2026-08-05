# Ghost Namespace-Serial Scheduling Audit — 2026-08-05

## Scope

This tranche removes the remaining global queued-write serialization debt in `GhostPersistenceManager` while preserving all namespace, recovery, identity, telemetry, and durability contracts.

Authoritative starting head:

```text
f28db821c3bd8b25855818890ed66edbf0590681
```

## Previous behavior

The manager used one `Executors.newSingleThreadExecutor(...)` for every primary and compatibility promotion.

Namespace activity and recovery admission had already been corrected: an active primary task no longer blocked compatibility recovery and vice versa. Queued persistence itself, however, remained globally serial.

Consequences:

- unrelated namespace writes waited behind one another;
- `latestSubmittedWrite` was sufficient only because all tasks shared one FIFO executor;
- the audit ledger still correctly listed global serialization as bounded debt.

## Design

Added `GhostNamespaceSerialScheduler`.

It combines:

```text
shared fixed backend: 2 daemon threads
per-namespace SerialExecutor queue
FutureTask for every submission
```

Properties:

- same namespace: strict FIFO and no overlap;
- different namespaces: bounded overlap;
- no thread per compatibility namespace;
- failed tasks continue the queue through `finally`;
- no recapture of mutable namespace state.

## Manager integration

`GhostPersistenceManager` now:

- constructs `GhostNamespaceSerialScheduler` over a two-thread fixed backend;
- names daemon workers with a monotonic ordinal;
- submits through `scheduler.submit(namespace)`;
- continues tracking the returned future under the same namespace;
- removes `latestSubmittedWrite`;
- delegates `awaitPendingWrites(...)` to registry-wide waiting.

Unchanged:

- candidate validation;
- namespace capture;
- pre-write recovery gate;
- pending-distance floor;
- immediate publication;
- SHA-256/FNV identity;
- worker recovery;
- receipt→ghost→manifest→distance→clear order;
- failed-publication cleanup;
- telemetry calls;
- disk fallback;
- explicit recovery admission.

The exact manager commit was inspected and contained only executor/scheduler construction, namespace-keyed submission, all-namespace waiting, cleanup, constants, imports, and comments.

## Await semantics

`GhostNamespacePendingWriteRegistry.awaitAll(timeoutMs)` now:

1. rejects negative timeout budgets;
2. snapshots every active namespace-latest future;
3. waits under one shared monotonic deadline;
4. loops to catch tasks registered or replaced during waiting;
5. removes completed/cancelled entries lazily;
6. returns false on timeout or exceptional completion.

Because each namespace queue is serial, its latest future represents all earlier same-namespace work. Waiting every namespace-latest future therefore drains all accepted work without depending on global execution order.

## Test coverage

Added `GhostNamespaceSerialSchedulerTest`:

- same-namespace FIFO;
- same-namespace peak concurrency one;
- cross-namespace peak concurrency two;
- task-failure propagation;
- later same-namespace continuation after failure.

Expanded `GhostNamespacePendingWriteRegistryTest`:

- multi-namespace waiting;
- shared timeout budget;
- negative and zero timeout behavior;
- completed namespace cleanup.

Migrated:

- `scripts/test_ghost_persistence_namespace_contract.py`;
- `scripts/test_ghost_promotion_recovery_contract.py`.

The recovery contract retains its previous receipt, ghost, manifest, distance, SHA-256, legacy compatibility, corruption, cleanup, and fast-path assertions.

## Focused execution

A Kotlin/JVM executable harness compiled and passed:

```text
ghost namespace scheduler checks passed
```

It exercised:

- concurrent primary and compatibility work;
- same-namespace FIFO;
- failed-task continuation;
- waiting for two active namespace futures;
- timeout before the second namespace completed.

## Risk analysis

### Ordering regression

Mitigation: each namespace owns one serial queue and tests require exact order plus maximum concurrency one.

### Unbounded worker creation

Mitigation: all queues share a fixed backend of two threads. Queue objects do not own threads.

### Await returning early

Mitigation: waiting covers every active namespace and repeats until the registry is empty.

### Failure stranding later work

Mitigation: `scheduleNext()` runs in a wrapper `finally` block and is regression-tested.

### Cross-namespace file collision

Mitigation: namespace-bound receipt, artifact, manifest, distance, publication, and activity ownership was completed in preceding tranches.

### Same-namespace recovery race

Mitigation: pending-write admission remains namespace-scoped and blocks recovery whenever that namespace's latest queued task is unfinished.

## Evidence not obtained

This tranche does not claim:

- full exact-head Gradle/JUnit/Robolectric success;
- Android AtomicFile stress with both backend threads active;
- emulator or physical-device process-death recovery;
- thermal, memory, latency, or disk-pressure acceptance;
- release packaging or store delivery evidence.

## Result

The global single-worker queue is no longer production architecture. Ghost work remains serial where durable stores overlap and becomes concurrently executable only across immutable, disjoint persistence namespaces.
