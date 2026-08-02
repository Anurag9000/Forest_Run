# Forest Run — Ghost Atomic Recovery Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

This supplement records closure of the previously isolated `SaveManager.hasGhostRun()` inconsistency.

## Repository policy

- Changes were committed directly to `main`.
- No branch or pull request was created.
- No history was rewritten.

## Previous behavior

`loadGhostRun()` recognized either the active ghost base file or its `AtomicFile` `.bak` recovery file before calling `openRead()`.

`hasGhostRun()` checked only the base path:

```kotlin
fun hasGhostRun(context: Context): Boolean = ghostFile(context).exists()
```

After an interrupted atomic write, a valid previous ghost could therefore remain recoverable through `.bak` while menus or playback admission incorrectly reported that no ghost existed.

## Implemented correction

Commit `7748577a72aac6820078285465fabb4ec81c3936` introduced one shared predicate:

```kotlin
private fun hasRecoverableGhostFile(atomicFile: AtomicFile): Boolean =
    atomicFile.baseFile.exists() || File(atomicFile.baseFile.path + ".bak").exists()
```

Both `loadGhostRun()` and `hasGhostRun()` now call this predicate.

The correction also consistently uses `context.applicationContext` and the active ghost filename, preserving primary and compatibility-schema isolation.

The binary format, frame validator, bounded allocation behavior, and `AtomicFile.finishWrite`/`failWrite` transaction remain unchanged.

## Tests

Commit `e3de96b81c8862d445193d5380d16000ee3c0b78` added `SaveManagerGhostRecoveryTest` with three Robolectric contracts:

1. neither base nor backup exists;
2. a primary ghost exists only as `.bak`;
3. a compatibility-schema ghost exists only as `.bak`.

The recovery tests save a structurally valid ghost, rename the base file to its backup path, assert availability, and then assert exact frame recovery through `loadGhostRun()`.

Test setup deletes base, `.bak`, and `.new` paths for the active filename.

## Documentation

Commit `91eae1829bf6ac054834ac1753e13b7f42b7252a` added `docs/GHOST_PERSISTENCE_RECOVERY.md`, documenting:

- primary and compatibility file naming;
- `.bak` recovery and `.new` exclusion;
- the distinction between cheap availability and full structural validity;
- write transaction semantics;
- Robolectric and physical-device validation scope.

## Validation truth

The modified source region and complete new test file were fetched again from `main` and statically reviewed.

The available container still cannot resolve `github.com`, so the exact-head Gradle/Robolectric suite was not executed locally. No green Android or CI claim is made.

## Debt ledger update

The former item:

> Correct `SaveManager.hasGhostRun()` so recoverable `AtomicFile` backup state is recognized.

is now **closed**.

Remaining isolated runtime debt:

1. wire `FamiliarityWarmthScoring` into `RelationshipArcSystem.familiarityWarmth()`;
2. wire `SafeProgressionArithmetic` into both `ReturnMomentsSystem` call sites;
3. add finite-coordinate admission to `MainMenuScreen.onTap()`;
4. harden public finite-delta boundaries in `ParallaxBackground` and `GameView.update()`;
5. decompose `GameView` and consolidate distributed persistence ownership through behavior-preserving seams.

## External gates

- exact-head Robolectric execution;
- interruption/recovery testing on supported Android API levels;
- long-session ghost recording and playback;
- lifecycle/process-death recovery;
- signed release artifact and physical-device acceptance.

## Classification

Ghost persistence now reports recoverable atomic backups consistently. Forest Run remains a feature-rich alpha until the remaining runtime and external release gates are completed.
