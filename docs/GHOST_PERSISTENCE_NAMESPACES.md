# Forest Run — Ghost Persistence Namespace Isolation

## Purpose

Forest Run supports a primary save namespace and compatibility namespaces used by schema-repair, migration, test, and support flows. Each namespace owns four related facts:

```text
SharedPreferences name
best-distance key inside those preferences
ghost frame filename
promotion receipt and artifact manifest derived from that filename
```

A ghost promotion or recovery-maintenance operation is correct only when every related read and write belongs to the same namespace for its complete lifetime.

Before these boundaries were added, asynchronous manager work or an already-created maintenance instance could combine stores captured at construction with later dynamic `SaveManager` reads. Switching between primary and compatibility namespaces could therefore mix:

- a receipt from one ghost filename;
- a frame artifact from another filename;
- a manifest from the first filename;
- a best-distance write in whichever preference namespace happened to be active later;
- a globally published in-memory ghost visible from the wrong namespace;
- a run-outcome journal from one namespace with mood, return, summary, or route state from another.

The current implementation removes those cross-namespace ambiguities for both manager-owned ghost work and recovery-evidence maintenance.

## Canonical namespace identities

Primary namespace:

```text
preferences: forest_run_prefs
ghost:       ghost_run.bin
```

Compatibility namespace for schema version `N`:

```text
preferences: forest_run_prefs_compat_vN
ghost:       ghost_run_compat_vN.bin
```

Promotion sidecars remain derived from the captured ghost filename:

```text
<ghost>.promotion
<ghost>.manifest
```

## Immutable snapshot

`GhostPersistenceNamespace` stores:

```kotlin
prefsName: String
ghostFilename: String
```

`capture()` reads the active preference namespace once and derives its canonical ghost filename from that one value. It deliberately does not read `activeGhostFilenameForTests` as a second independent volatile value.

This matters because `SaveManager.usePrimaryPreferences()` and `useCompatibilityPreferences(...)` update two fields under their own lock, while readers outside `SaveManager` cannot participate in that private lock. Deriving the filename from one preference-name read prevents a mixed pair during the brief field transition.

Capture accepts only:

- the exact primary preference name; or
- a compatibility prefix followed by decimal digits.

Unsupported names fail closed.

The namespace constructor also rejects unsafe artifact names:

- blank names;
- `.` or `..`;
- `/`;
- `\`;
- NUL.

The captured filename is therefore a plain file inside the application files directory, not a path supplied to traversal-sensitive APIs.

## Bound ghost artifact store

`NamespaceBoundGhostPromotionArtifactStore` is constructed from one immutable namespace. At construction it binds:

```text
SharedPreferences(namespace.prefsName)
AtomicFile(filesDir / namespace.ghostFilename)
```

After construction it never consults mutable active namespace state.

It does not call dynamic `SaveManager.loadGhostRun(...)`, `saveGhostRun(...)`, or `loadBestDistance(...)`. Instead it owns a namespace-bound adapter with the same durable ghost contract:

- AtomicFile writes;
- version-2 magic and stable state codes;
- legacy raw-ordinal reads;
- exact header and payload-size validation;
- maximum frame-count and file-size bounds;
- non-finite, invalid-state, invalid-scale, and non-monotonic-frame rejection through `GhostRunValidator`;
- synchronous best-distance commit.

The adapter preserves format compatibility rather than introducing a second ghost format.

## Bound run-outcome maintenance state

`NamespaceBoundRunOutcomeMaintenanceStateStore` binds one `SharedPreferences(namespace.prefsName)` instance at construction. It owns only the state required to inspect and replay the run-outcome recovery journal:

```text
best distance read
forest mood snapshot read/write
return moment snapshot read/write
last-run summary read
pacifist-route count read
```

It mirrors the corresponding `SaveManager` keys and normalization rules, but explicit maintenance writes use synchronous `commit()` so recovery can verify durability immediately.

The adapter never consults:

```text
SaveManager.activePrefsNameForTests
SaveManager.activeGhostFilenameForTests
```

`SharedPreferencesRunOutcomeRecoveryStore` and `SharedPreferencesRunOutcomeSummarySnapshotStore` already accept an explicit namespace. The new state adapter closes the remaining dynamic reads around those stores.

## Manager transaction ownership

Every public manager operation captures one namespace before doing namespace-sensitive work.

For an accepted asynchronous promotion, the captured namespace is carried into:

```text
pre-write recovery
pending-distance comparison
in-memory publication
worker recovery
promotion receipt store
ghost artifact store
artifact manifest store
best-distance preference store
failed-publication cleanup
```

The worker closure never recaptures the active namespace.

The durable sequence remains:

```text
receipt
→ ghost
→ manifest
→ best distance
→ receipt clear
```

but every step is now bound to the same immutable pair.

## Per-namespace publication

The old single global publication slot is replaced by:

```kotlin
ConcurrentHashMap<GhostPersistenceNamespace, PublishedGhost>
```

A publication contains:

- namespace;
- immutable frame snapshot;
- accepted distance;
- legacy fingerprint;
- distance-bound SHA-256 digest.

`loadLatest(...)` returns only the publication for the currently captured namespace. A primary ghost cannot become the compatibility ghost merely because it is newer in memory, and vice versa.

`bestDistanceFloor(...)` is namespace-specific:

```text
max(bound durable distance, accepted publication distance for this namespace)
```

A failed worker clears only the publication matching all of:

```text
namespace
distance
FNV fingerprint
SHA-256 digest
```

An older failed primary worker therefore cannot clear a newer compatibility publication.

## Maintenance lifetime ownership

`AndroidRecoveryEvidenceMaintenance` captures one `GhostPersistenceNamespace` exactly once during construction and passes it to both evidence handlers.

The run-outcome handler binds:

```text
journal store(namespace.prefsName)
summary snapshot store(namespace.prefsName)
NamespaceBoundRunOutcomeMaintenanceStateStore(namespace.prefsName)
```

The ghost handler binds:

```text
receipt store(namespace.ghostFilename)
manifest store(namespace.ghostFilename)
NamespaceBoundGhostPromotionArtifactStore(namespace)
GhostPromotionRecoveryCoordinator over those same stores
```

After construction, switching `SaveManager` to another namespace does not redirect:

- inspection;
- safe replay;
- manifest-to-artifact validation;
- corrupt evidence cleanup;
- unresolved receipt abandonment;
- mood, return, summary, route, ghost, or distance reads used by recovery.

Maintenance does not switch the active namespace itself. The caller may continue using another namespace while an older maintenance instance remains bound to the namespace it captured.

## Switching behavior

The following manager workflow is supported:

```text
queue primary promotion
→ switch to compatibility namespace
→ queue compatibility promotion
→ read compatibility in-memory publication
→ await worker queue
→ switch back to primary
→ read primary publication and durable artifact
→ switch to compatibility
→ read compatibility publication and durable artifact
```

The following maintenance workflow is also supported:

```text
select primary namespace
→ create maintenance instance
→ switch active namespace to compatibility
→ inspect or recover through the existing maintenance instance
→ only primary evidence and state are read or mutated
```

The single executor still serializes manager writes globally. Namespace isolation changes ownership, not concurrency level. Explicit recovery in a second namespace may still return `IO_FAILURE` while any manager worker is active. That is a fail-closed admission choice, not cross-writing.

## Validation surface

`GhostPersistenceNamespaceIntegrationTest` covers:

- canonical primary and compatibility capture;
- a bound store remaining on its captured namespace after an active switch;
- independent primary and compatibility ghosts and distances;
- immediate manager publication after a namespace switch;
- queued primary and compatibility writes on the single executor;
- switching back and forth after durability;
- traversal-name rejection.

`NamespaceBoundGhostPromotionArtifactStoreTest` covers:

- version-2 writer header parity with `SaveManager`;
- versioned round trip;
- `SaveManager` reading bound-store output;
- legacy raw-ordinal readability;
- unknown-version rejection;
- trailing-byte rejection;
- invalid-candidate preservation of the existing durable ghost.

`RecoveryEvidenceMaintenanceNamespaceIntegrationTest` covers:

- valid primary manifest inspection after switching to a different compatibility ghost;
- primary run-journal recovery while a compatibility journal remains pending and untouched;
- primary unwritten-receipt abandonment while the compatibility receipt remains pending;
- active namespace remaining compatibility after the captured-primary maintenance operation.

`test_ghost_persistence_namespace_contract.py` locks manager namespace derivation, bound artifact ownership, namespace-keyed publication, and no worker recapture.

`test_recovery_evidence_maintenance_contract.py` locks:

- one maintenance namespace capture;
- the same capture passed to both handlers;
- namespace-bound non-ghost state access;
- namespace-bound receipt, manifest, ghost, and distance access;
- no dynamic `SaveManager` access inside either maintenance handler;
- cross-namespace integration coverage.

Focused Kotlin compilation and filesystem/in-memory executable harnesses passed for namespace capture, ghost artifacts, manager publication, and run-outcome maintenance state isolation.

## Remaining evidence and limitations

- Full exact-head Gradle and Robolectric execution remains required.
- Emulator and physical-device process-death/relaunch behavior remains required.
- Long-run worker latency and disk-pressure behavior remain device gates.
- The single manager executor remains global across namespaces.
- Explicit manager recovery still blocks conservatively while any worker is active.
- SHA-256 identifies local content and distance but does not authenticate a trusted writer.
