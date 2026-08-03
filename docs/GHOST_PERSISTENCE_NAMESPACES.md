# Forest Run — Ghost Persistence Namespace Isolation

## Purpose

Forest Run supports a primary save namespace and compatibility namespaces used by schema-repair, migration, test, and support flows. Each namespace owns four related facts:

```text
SharedPreferences name
best-distance key inside those preferences
ghost frame filename
promotion receipt and artifact manifest derived from that filename
```

A ghost promotion is correct only when all four facts belong to the same namespace for the complete asynchronous transaction.

Before this boundary was added, `GhostPersistenceManager` could capture an active filename at one point while later ghost or distance operations consulted mutable `SaveManager` namespace state. Switching between the primary and compatibility namespaces while a queued worker existed could therefore mix:

- a receipt from one ghost filename;
- a frame artifact from another filename;
- a manifest from the first filename;
- a best-distance write in whichever preference namespace happened to be active later;
- a globally published in-memory ghost visible from the wrong namespace.

The current implementation removes that cross-namespace ambiguity for manager-owned ghost work.

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

## Bound artifact store

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

`bestDistanceFloor(...)` is now namespace-specific:

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

## Switching behavior

The following is supported:

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

The single executor still serializes writes globally. Namespace isolation changes ownership, not concurrency level. This is deliberately conservative: promotion ordering and telemetry remain simple while cross-namespace writes cannot mix.

Explicit recovery in a second namespace may still return `IO_FAILURE` while any manager worker is active. That is a fail-closed admission choice, not cross-writing.

## Maintenance boundary

This implementation closes namespace switching for `GhostPersistenceManager` workers, manager recovery, manager disk fallback, and manager in-memory publication.

A live `AndroidRecoveryEvidenceMaintenance` instance remains a separate boundary. Its handlers capture some stores at construction but still contain dynamic `SaveManager` access for artifact and non-ghost state. Therefore:

- instantiate maintenance only after the desired namespace is selected;
- do not switch namespaces while that maintenance instance remains in use;
- mutating recovery/discard remains a debuggable cold-start operation before gameplay owners exist.

This narrower limitation is retained explicitly. The worker limitation is removed; the maintenance-instance limitation is not.

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

`test_ghost_persistence_namespace_contract.py` locks:

- one-read namespace derivation;
- no mutable active namespace access inside the bound store;
- namespace-keyed publication;
- no namespace recapture inside queued workers;
- one namespace shared by receipt, ghost, manifest, and distance;
- integration and codec coverage.

`test_ghost_promotion_recovery_contract.py` remains the broader transaction contract and now recognizes namespace-bound publication and artifact ownership while retaining receipt/manifest ordering, legacy upgrades, SHA-256 identity, corruption blocking, and the healthy no-ghost-load path.

Focused Kotlin compilation and a filesystem-backed executable harness passed for the new namespace, artifact, and manager surfaces. The harness deliberately changed the active preference namespace while leaving the separate mutable ghost filename stale and verified that capture still derived the correct compatibility file.

## Remaining evidence and limitations

- Full exact-head Gradle and Robolectric execution remains required.
- Emulator and physical-device process-death/relaunch behavior remains required.
- Long-run worker latency and disk-pressure behavior remain device gates.
- The single executor remains global across namespaces.
- Live maintenance-instance switching remains unsupported until its artifact and state adapters are migrated to immutable namespace snapshots.
- SHA-256 identifies local content and distance but does not authenticate a trusted writer.
