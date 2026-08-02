# Ghost Persistence and Atomic Recovery

Forest Run stores the previous run's ghost frames through Android `AtomicFile`. Availability checks, loading, and compatibility-schema storage must therefore agree on what constitutes a recoverable ghost.

## File set

For the primary schema, the active base path is:

```text
filesDir/ghost_run.bin
```

Compatibility mode selects a versioned base path:

```text
filesDir/ghost_run_compat_v<schema>.bin
```

`AtomicFile` may also maintain recovery or transient siblings:

```text
<base>.bak
<base>.new
```

The `.bak` file is a recoverable prior committed value. The `.new` file is an incomplete write and is not treated as an available ghost.

## Availability contract

`SaveManager.hasGhostRun()` answers whether an `AtomicFile` read candidate exists. It returns true when either:

- the active base file exists; or
- the active base file's `.bak` recovery file exists.

It returns false when neither exists.

Availability is deliberately an existence check, not a structural-validity promise. `loadGhostRun()` remains responsible for validating:

- bounded file size;
- supported header and version;
- frame count;
- exact expected byte length;
- finite, chronological timestamps;
- bounded positions and scales;
- valid player-state ordinals;
- maximum recording duration.

A corrupt base or backup can therefore make availability true while loading still returns an empty list. This matches the pre-existing base-file semantics and keeps the inexpensive UI availability query separate from full binary validation.

## Shared recovery predicate

Both `loadGhostRun()` and `hasGhostRun()` use the same private `hasRecoverableGhostFile(AtomicFile)` predicate. This prevents future drift in which loading recognizes `.bak` recovery but menus or ghost-playback admission do not.

The predicate is constructed from `context.applicationContext` and the currently active ghost filename, so primary and compatibility modes share identical behavior.

## Write behavior

`saveGhostRun()` continues to:

1. reject empty, oversized, nonfinite, nonchronological, or invalid-state frame sets;
2. start an `AtomicFile` write;
3. encode the versioned header and bounded frames;
4. flush the data stream;
5. call `finishWrite()` on success;
6. call `failWrite()` after an exception so the previous committed value remains recoverable.

The recovery-availability change does not alter the binary format.

## Test coverage

`SaveManagerGhostRecoveryTest` covers:

- no base or backup: unavailable and empty load;
- primary base renamed to `.bak`: available and loadable;
- compatibility-schema base renamed to `.bak`: available and loadable;
- cleanup of base, `.bak`, and `.new` paths between tests.

The existing `SaveManagerTest` continues to cover ordinary base-file round trips.

## Release interpretation

These tests are Robolectric contracts. Exact-head Android and connected-device execution are still required before release. In particular, final validation should confirm platform `AtomicFile` behavior on the supported Android API range and after forced process interruption during ghost writes.
