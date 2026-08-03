# Ghost Persistence Namespace Isolation Audit — 2026-08-03

## Scope

This audit covers asynchronous best-ghost promotion while Forest Run changes between its primary save namespace and compatibility namespaces.

Starting canonical head:

```text
2ab6a5af0272a6880b05fa7cc69ffd9dd3e3cb4d
```

The tranche is intentionally limited to:

- `GhostPersistenceManager` workers;
- manager-owned recovery;
- manager disk fallback;
- manager in-memory publication;
- a namespace-bound ghost-plus-distance artifact adapter;
- tests, source contracts, and documentation.

It does not claim to migrate every recovery-maintenance state adapter.

## Original risk

`SaveManager` exposes mutable active names for:

```text
SharedPreferences
ghost frame file
```

The old manager built receipt and manifest stores from an active ghost filename, while `AndroidGhostPromotionArtifactStore` delegated ghost reads/writes back to dynamic `SaveManager` functions and captured preferences separately.

A namespace change after request admission but before worker execution could therefore make one promotion span more than one namespace. The old global `latestPublication` could also expose a primary ghost while compatibility storage was active.

The risk was architectural even though ordinary production does not switch namespaces during a run. Compatibility, migration, support, and test paths are precisely where this boundary must fail closed rather than rely on timing.

## Implemented correction

### Immutable namespace

Added `GhostPersistenceNamespace`:

```text
prefsName
ghostFilename
```

Capture reads the active preference namespace once and derives the canonical ghost filename from that one value.

This avoids a torn pair during `SaveManager`'s two-field update without requiring access to its private lock.

### Bound artifact adapter

Added `NamespaceBoundGhostPromotionArtifactStore`.

At construction it binds:

```text
SharedPreferences(namespace.prefsName)
AtomicFile(filesDir / namespace.ghostFilename)
```

It retains:

- version-2 writes;
- stable state codes;
- legacy raw-ordinal reads;
- exact-size checks;
- bounded frame count and file size;
- shared frame validation;
- synchronous best-distance commits.

It never consults mutable active namespace state after construction.

### Manager publication

Replaced one global publication with:

```text
ConcurrentHashMap<GhostPersistenceNamespace, PublishedGhost>
```

The namespace is now part of publication identity and cleanup.

### Worker ownership

Each public request captures one namespace. That exact object flows through:

```text
recovery gate
best-distance gate
publication
queued worker
receipt
artifact
manifest
distance
cleanup
```

The worker does not recapture.

### Disk fallback

`loadLatest(...)` now reads ghost and best distance from one bound store before computing the distance-bound identity and publishing it under that namespace.

## Review correction during implementation

The first capture design retried two volatile field reads until preference and ghost names matched.

Review identified a stronger and simpler invariant: the filename is a deterministic function of the preference namespace. The final design therefore performs one volatile preference-name read and derives the file name. This eliminates the mixed-pair problem rather than merely waiting for it to disappear.

## Test additions

### `GhostPersistenceNamespaceIntegrationTest`

Covers:

- primary pair;
- compatibility pair;
- store binding after an active switch;
- independent primary/compatibility artifacts and distances;
- queued primary and compatibility manager writes;
- immediate per-namespace publication;
- durable reads after switching back and forth;
- unsafe filename rejection.

### `NamespaceBoundGhostPromotionArtifactStoreTest`

Covers:

- version-2 header and round trip;
- `SaveManager` interoperability;
- legacy reads;
- unknown version;
- trailing bytes;
- invalid candidate preserving existing data.

## Contract changes

Added:

```text
scripts/test_ghost_persistence_namespace_contract.py
```

Migrated:

```text
scripts/test_ghost_promotion_recovery_contract.py
```

The migration preserves all existing durable-transaction protections while replacing obsolete assumptions about a global publication and dynamic artifact store.

## Focused execution

Passed:

```text
ghost namespace filesystem isolation passed
```

The executable harness used filesystem-backed AtomicFile behavior and independent preference maps. It verified:

- primary artifact/distance write;
- compatibility capture while the old mutable filename was intentionally stale;
- compatibility artifact/distance write;
- primary data unchanged;
- distinct files for both namespaces.

Focused Kotlin compilation also passed for the new production surfaces with only local-stub unused-parameter warnings.

## Diff boundary

Production scope is limited to:

```text
GhostPersistenceManager.kt                 modified
GhostPersistenceNamespace.kt               added
```

No gameplay, collision, rendering, authored copy, summary, or death-state production file is part of this change.

## Behavior preserved

Unchanged:

- candidate frame validation;
- accepted-distance validation;
- strictly shorter-candidate rejection;
- equal-distance replacement compatibility;
- immediate publication before worker execution;
- one daemon worker;
- receipt → ghost → manifest → distance → clear;
- distance-bound SHA-256 identity;
- FNV legacy compatibility;
- corrupt-evidence blocking;
- legacy sidecar upgrade;
- healthy already-applied no-ghost-load path;
- telemetry start/completion events.

## Remaining limitation

`AndroidRecoveryEvidenceMaintenance` remains instantiated for one selected namespace and still contains dynamic `SaveManager` accesses. A maintenance object must not survive a namespace change.

This is now narrower than the previous limitation:

```text
active manager worker switching: supported and isolated
live maintenance-instance switching: still unsupported
```

## Validation truth

Not executed in this environment:

- complete exact-head Gradle compilation;
- full JUnit/Robolectric suite;
- lint;
- emulator process death;
- physical-device process death;
- long-run disk-pressure testing;
- signed package or store delivery.

The repository must not be described as exact-head green from focused compilation and source contracts alone.
