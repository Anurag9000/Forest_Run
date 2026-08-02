# Forest Run — Public Game Frame Input Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Completed scope

This tranche closed the malformed and oversized-delta path at the public `GameView.update()` boundary.

Implemented directly on `main`:

1. `FrameInputAdmission.kt` — added delta-only admission for frame owners that do not yet have a trusted scroll speed.
2. `FrameInputAdmissionTest.kt` — added direct delta-only acceptance and rejection coverage.
3. `GameView.kt` — public fail-closed admission and bounded dispatch.
4. `GameViewFrameInputTest.kt` — constructor-level Robolectric frame-counter contracts.
5. `test_game_view_frame_input_contract.py` — balanced source extraction and public-boundary ownership enforcement.
6. `FRAME_INPUT_ADMISSION.md` — expanded canonical contract covering both GameView and parallax.

## Production behavior

The public method is now:

```kotlin
fun update(deltaTime: Float) {
    if (!FrameInputAdmission.acceptsDelta(deltaTime)) return
    updateBounded(FrameInputAdmission.boundedDeltaSeconds(deltaTime))
}
```

The prior update body moved intact behind `private fun updateBounded(deltaTime: Float)`.

Rejected deltas return before the debug frame counter, camera, input, Bloom timers, screen owners, physics, entities, collisions, ghost recording, audio, haptics, particles, dialogue, or persistence decisions can change.

Accepted finite deltas dispatch once. Oversized finite deltas are capped to `0.05f` before any legacy update code receives them.

## Exact preservation evidence

The complete `GameView.kt` owner was replaced from blob `db5d0473541ad8d6ca5a9ca30618e8eded55d372`. Immediate commit inspection showed exactly one hunk, five added lines, and zero deletions:

- two public admission/dispatch statements;
- the public method close;
- one blank separator;
- the new private bounded-body signature.

No pre-existing statement was deleted, reordered, or rewritten. The original first statement, `debugFrameCounter++`, remains the first statement of the bounded legacy body.

## Validation performed

- Commit comparison and exact patch inspection confirmed the five-line wrapper-only production diff.
- The extended `FrameInputAdmission` compiled with the available Kotlin compiler.
- A direct executable exercised finite, nonfinite, nonpositive, oversized, and reversing cases successfully.
- The balanced source contract syntax-compiled and all three checks passed against the exact checked-in wrapper structure.
- A Robolectric test was added for rejected, ordinary, and oversized finite deltas.

The Robolectric test was not executed through an exact-head Android Gradle environment. No complete exact-head unit, Robolectric, lint, release-build, packaging, connected-emulator, or physical-device result is claimed.

## Debt removed

The prior item "harden the public frame boundary in `GameView.update()`" is complete.

## Remaining structural debt

The principal bounded runtime debt is now architectural rather than an isolated unsafe expression:

1. decompose `GameView` into behavior-preserving update, presentation, collision-outcome, and persistence seams;
2. consolidate distributed persistence ownership so one run outcome cannot be committed through competing paths;
3. retain exact gameplay ordering and deterministic/debug scenario behavior while extracting those seams.

## Classification

This removes a top-level state-corruption and fast-forward path but does not complete release validation. Forest Run remains a feature-rich alpha until exact-head execution, physical acceptance, signing, delivery, visual, accessibility, and policy gates are complete.
