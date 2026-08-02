# Forest Run — Ghost Recovery Focused Compile Evidence

Date: 2026-08-02

This evidence note records a focused local Kotlin compile performed after the `SaveManager` ghost-recovery replacement and its Robolectric test were committed to `main`.

## Compiled sources

- the complete reconstructed `SaveManager.kt` owner, 607 lines;
- `SaveManagerGhostRecoveryTest.kt`;
- minimal compile-only stubs for:
  - Android `Context`, `SharedPreferences`, and `AtomicFile`;
  - Forest Run entity, ghost-frame, progression, garden, mood, route, biome, and relationship types;
  - AndroidX `ApplicationProvider`;
  - JUnit annotations/assertions;
  - Robolectric runner type.

## Result

`kotlinc` completed successfully and produced output jars for both the production owner and the test type surface.

No Kotlin syntax errors, missing member references within the reconstructed Forest Run source, invalid chaining, or test type-shape errors were reported.

Warnings were limited to:

- deliberately unused parameters in compile-only stubs;
- the pre-existing Java deprecation warning for `Thread.currentThread().id` in `SaveManager`.

## Scope

This focused compile proves that the reconstructed full-file replacement is syntactically and type-shape coherent against the APIs it uses. It does not prove:

- Android framework runtime behavior;
- Robolectric `AtomicFile` behavior;
- Gradle source-set or dependency configuration;
- repository-wide Kotlin compatibility;
- exact-head unit, lint, release, packaging, emulator, or physical-device success.

The canonical exact-head Gradle and Robolectric suites remain required.
