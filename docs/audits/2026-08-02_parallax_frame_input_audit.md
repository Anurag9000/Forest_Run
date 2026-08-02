# Forest Run — Parallax Frame Input Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Completed scope

This tranche closed the coordinator-level malformed-frame path in `ParallaxBackground.update()`.

Implemented directly on `main`:

1. `FrameInputAdmission.kt` — canonical allocation-free frame admission and bounding.
2. `FrameInputAdmissionTest.kt` — finite, nonfinite, reversing, boundary, clamp, and misuse contracts.
3. `ParallaxBackground.kt` — admission before mutation, bounded fan-out, and accumulator repair.
4. `ParallaxFrameInputIntegrationTest.kt` — coordinator/layer behavior and inherited-state repair.
5. `test_parallax_frame_input_contract.py` — balanced source extraction and ordering/raw-input contract.
6. `FRAME_INPUT_ADMISSION.md` — canonical developer and operator contract.

## Production behavior

A parallax frame now becomes a complete no-op unless delta is finite and positive and scroll speed is finite and non-negative. Admitted values are bounded to:

- maximum delta: `0.05f` seconds;
- maximum scroll speed: `GameConstants.MAX_SCROLL_SPEED`.

Only bounded values reach parallax layers, ambience time, Bloom interpolation, and Bloom pulse. Existing nonfinite ambience or Bloom accumulators are repaired before use.

## Preservation evidence

The production integration changed only the `update()` method. An initially observed KDoc escape normalization was restored in a dedicated follow-up commit. The net production diff from the frozen helper/test head contains only the intended runtime substitutions: 14 additions and 5 deletions in `ParallaxBackground.kt`. No biome artwork, authored colors, procedural geometry, narrative text, rendering order, shader logic, or public drawing API changed.

## Validation performed

- `FrameInputAdmission.kt` compiled with the available Kotlin compiler.
- A direct Kotlin executable exercised accepted, rejected, clamped, and misuse cases successfully.
- The balanced Python source contract passed against the exact checked-in update method.
- Integration-test arithmetic was independently evaluated for oversized and repaired frames.
- Commit diffs were inspected immediately after the large-owner replacement.
- The final KDoc restoration commit changed exactly one line by one addition and one deletion.

The checked-in Robolectric integration test was not executed through an exact-head Android Gradle environment. No complete exact-head unit, Robolectric, lint, release-build, packaging, connected-emulator, or physical-device result is claimed.

## Debt removed

The prior item "harden coordinator-level finite delta and speed admission in `ParallaxBackground.update()`" is complete.

## Remaining bounded runtime debt

1. integrate `FamiliarityWarmthScoring` into `RelationshipArcSystem`;
2. harden the public frame boundary in `GameView.update()`;
3. decompose `GameView` and consolidate distributed persistence ownership through behavior-preserving seams.

## Classification

This removes a runtime state-corruption and fast-forward path but does not complete release validation. Forest Run remains a feature-rich alpha until exact-head execution, physical acceptance, signing, delivery, visual, accessibility, and policy gates are complete.
