# Recovery Maintenance Namespace Binding Audit — 2026-08-04

## Scope

This tranche closes the remaining namespace-lifetime defect in `AndroidRecoveryEvidenceMaintenance`.

Starting repository head:

```text
a910afe5ecfc12b4965c4cb8b1955ffb49d4257e
```

The prior ghost namespace tranche made `GhostPersistenceManager` workers, manager recovery, disk fallback, durable sidecars, best-distance writes, and in-memory publication namespace-stable. Recovery maintenance still mixed construction-time stores with later dynamic `SaveManager` reads.

## Previous failure surface

An already-created maintenance instance captured:

- the run-outcome journal namespace;
- ghost receipt filename;
- ghost manifest filename.

But later operations still used dynamic `SaveManager` methods for:

- best distance;
- forest mood state;
- return-moment state;
- last-run summary;
- route-tier counts;
- durable ghost frames used for manifest validation;
- ghost artifact and distance writes during safe promotion recovery.

A namespace switch after construction could therefore inspect a primary manifest against compatibility frames, or replay a primary journal into compatibility progression state.

## Implemented boundary

### One immutable capture

`AndroidRecoveryEvidenceMaintenance` now performs exactly one:

```kotlin
GhostPersistenceNamespace.capture()
```

The same value is passed to both domain handlers.

### Namespace-bound run-outcome state

Added:

```text
NamespaceBoundRunOutcomeMaintenanceStateStore.kt
```

The adapter binds one `SharedPreferences(persistenceNamespace)` instance and owns:

- best-distance reads;
- forest mood snapshot reads and synchronous writes;
- return-moment snapshot reads and synchronous writes;
- last-run summary reads;
- bounded route-tier count reads.

It mirrors the canonical `SaveManager` keys and normalization rules while using `commit()` for explicit maintenance writes.

`MaintenanceRunOutcomePersistenceSink` delegates every namespace-sensitive state operation to this adapter. The existing journal and atomic summary snapshot stores already receive the same explicit namespace.

### Namespace-bound ghost maintenance

`AndroidGhostPromotionEvidenceHandler` now owns one:

```kotlin
NamespaceBoundGhostPromotionArtifactStore(context, namespace)
```

Receipt and manifest stores use `namespace.ghostFilename`, and `GhostPromotionRecoveryCoordinator` receives the same bound artifact store.

Manifest inspection now loads frames through:

```kotlin
artifactStore.loadGhost()
```

rather than dynamic `SaveManager.loadGhostRun(context)`.

## Preserved behavior

The following maintenance semantics are unchanged:

- exactly one handler per evidence domain;
- corrupt evidence is preserved during safe recovery;
- read failure never authorizes deletion;
- unresolved pending evidence is retried before explicit discard;
- support summaries exclude payload data;
- run-outcome recovery never publishes a ghost;
- ghost recovery never opens the run-outcome journal;
- valid manifests survive corrupt-receipt cleanup;
- ghost artifacts survive invalid-manifest cleanup;
- SHA-256 remains local identity rather than authentication.

## Tests

Added:

```text
RecoveryEvidenceMaintenanceNamespaceIntegrationTest.kt
```

It covers three former cross-namespace failure modes:

1. A valid primary manifest remains `CLEAN(valid_manifest)` after maintenance construction followed by a switch to a different compatibility ghost.
2. Primary run-journal recovery applies mood, return, summary, and route state only to primary preferences while a compatibility journal remains pending and untouched.
3. Primary unwritten-receipt recovery clears only the primary receipt while a compatibility receipt remains pending.

Every scenario also verifies that maintenance does not change the caller’s currently active compatibility namespace.

## Source contract

Updated:

```text
scripts/test_recovery_evidence_maintenance_contract.py
```

The contract now requires:

- one namespace capture before handler construction;
- the same capture passed to both handlers;
- no handler-level namespace recapture;
- namespace-bound run-outcome state delegation;
- namespace-bound ghost artifact ownership;
- no `AndroidGhostPromotionArtifactStore` in maintenance;
- no `SaveManager.loadGhostRun(...)` in manifest validation;
- integration coverage for inspection, replay, and abandonment after switching namespaces.

## Diff review

The `RecoveryEvidenceMaintenance.kt` production commit contains only:

- import replacement for namespace-bound types;
- one captured namespace;
- explicit handler namespace arguments;
- run-outcome state-store delegation;
- namespace-bound ghost artifact construction;
- manifest frame loading through the bound artifact store.

Coordinator policy and evidence-state behavior were not rewritten.

## Focused validation

A focused Kotlin/JVM compile succeeded for `NamespaceBoundRunOutcomeMaintenanceStateStore` using minimal Android interfaces.

An executable in-memory harness passed:

```text
namespace-bound maintenance state checks passed
```

The harness verified independent namespace state, synchronous mood/return writes, and absence of cross-namespace leakage.

The production diff and updated source-contract assumptions were inspected directly.

## Evidence not claimed

This session did not execute:

- the complete exact-head Gradle build;
- the full JUnit/Robolectric suite;
- Android lint;
- connected emulator tests;
- physical-device ADB maintenance commands;
- signed release or store delivery validation.

Empty attached status or workflow-run lists must not be interpreted as green CI.

## Remaining limitations

- One global ghost executor still serializes all namespace work.
- Explicit manager recovery remains conservatively blocked while any manager worker is active.
- Mutating recovery commands remain debug-only cold-start operations by product policy.
- SHA-256 identifies local content and distance but does not authenticate a trusted writer.
- Exact-head Android and device evidence remains outstanding.
