# Forest Run — Namespace-Serial Ghost Scheduling

## Purpose

Ghost persistence owns independent primary and compatibility namespaces. Each namespace has its own receipt, ghost file, manifest, best-distance preference, in-memory publication, and pending-write activity marker.

A global single-thread executor protected correctness but unnecessarily serialized disjoint namespaces. The current scheduler preserves ordering where files overlap and permits bounded parallelism where they do not.

## Scheduling model

`GhostPersistenceManager` owns a fixed daemon backend:

```text
maximum concurrent namespace workers = 2
```

It submits work through `GhostNamespaceSerialScheduler` rather than directly to the backend.

The scheduler maintains one lightweight serial queue per immutable `GhostPersistenceNamespace`:

```text
primary namespace       → FIFO queue
compatibility namespace → FIFO queue
```

Queues do not own threads. Their head tasks are dispatched onto the shared two-thread backend.

## Required ordering

For one namespace, accepted writes remain strictly FIFO:

```text
primary candidate A
→ primary candidate B
→ primary candidate C
```

A later same-namespace task cannot begin until the preceding task completes, including exceptional completion.

Every worker still performs:

```text
recover prior evidence
→ receipt
→ ghost
→ manifest
→ best distance
→ receipt clear
```

The scheduler changes admission concurrency, not the durable transaction.

## Cross-namespace concurrency

Different namespaces may overlap:

```text
primary candidate A       ┐
                          ├─ may execute concurrently
compatibility candidate B ┘
```

This is safe because each worker receives one immutable namespace and all persistence adapters are bound to it. Primary and compatibility work cannot share:

- receipt files;
- ghost files;
- manifest files;
- best-distance preferences;
- in-memory publication keys;
- pending-write registry entries.

The backend is capped at two threads, preventing unbounded worker creation even if many compatibility namespace identities are encountered.

## Failure behavior

Each submitted operation is represented by a `FutureTask`.

A worker exception:

- completes that task exceptionally;
- executes the serial queue's `finally` continuation;
- allows the next same-namespace task to run;
- does not strand the namespace queue;
- does not stop work in another namespace.

Production worker bodies retain their existing fail-closed exception handling and telemetry publication.

## Pending-write ownership

`GhostNamespacePendingWriteRegistry` tracks the latest queued task independently for each namespace.

Because same-namespace scheduling is FIFO, waiting for a namespace's latest task also waits for every earlier task in that namespace.

`awaitPendingWrites(timeoutMs)` now waits for all active namespace-latest futures under one shared monotonic timeout budget. It no longer waits for one globally latest task, which would be insufficient when a short task in one namespace completes before a longer task in another.

Behavior:

```text
negative timeout → false
zero timeout + active work → false
zero timeout + no active work → true
completed/cancelled entries → lazily removed
failure in any awaited task → false
all active namespaces complete → true
```

## Recovery admission

Recovery admission remains namespace-scoped:

```text
same namespace has queued/active work → IO_FAILURE
only another namespace is active      → recovery may proceed
no same-namespace activity            → recovery may proceed
```

Concurrent worker execution does not weaken this rule.

## Thread properties

The backend threads are:

- daemon threads;
- below normal priority;
- named `forest-run-ghost-io-1` and `forest-run-ghost-io-2` as created;
- limited by `MAX_CONCURRENT_NAMESPACE_WRITES = 2`.

## Validation

`GhostNamespaceSerialSchedulerTest` covers:

- strict FIFO ordering within one namespace;
- maximum same-namespace concurrency of one;
- overlap across two different namespaces;
- exceptional task completion;
- continued execution of later same-namespace work after failure.

`GhostNamespacePendingWriteRegistryTest` covers:

- namespace-local activity;
- completed and cancelled cleanup;
- latest same-namespace task authority;
- waiting for every active namespace;
- timeout and negative-budget behavior;
- completed-entry removal;
- complete registry clearing.

The namespace and recovery Python source contracts require:

- the bounded fixed backend;
- namespace-keyed scheduler submission;
- no global single-thread executor;
- no `latestSubmittedWrite` shortcut;
- FIFO queue mechanics;
- `finally`-based continuation;
- all-namespace waiting;
- unchanged recovery and durability ordering.

Focused Kotlin/JVM compilation and an executable concurrency harness passed for FIFO behavior, two-namespace overlap, failure continuation, and all-namespace waiting.

## Evidence boundary

The focused tests establish scheduler and registry semantics. They do not replace:

- exact-head Gradle/JUnit/Robolectric execution;
- Android filesystem stress under simultaneous namespace writes;
- process-death recovery while both backend threads are active;
- thermal, latency, or storage-pressure measurements;
- emulator and physical-device acceptance.
