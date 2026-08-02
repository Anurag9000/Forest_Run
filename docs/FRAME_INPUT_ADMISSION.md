# Forest Run — Frame Input Admission Contract

## Purpose

Public per-frame coordinators must not allow malformed timing or speed values to mutate persistent runtime state. A nonfinite or reversing input can otherwise poison animation clocks, Bloom interpolation, procedural ambience, and every downstream update that receives the corrupted value.

`FrameInputAdmission` is the canonical allocation-free boundary for a frame delta paired with a world scroll speed.

## Contract

A frame is admitted only when:

- `deltaSeconds` is finite;
- `deltaSeconds` is strictly positive;
- `scrollSpeed` is finite;
- `scrollSpeed` is non-negative.

Rejected frames are complete no-ops. No coordinator clock, interpolation value, layer position, or cached speed may change.

After admission:

- delta is capped to `0.05f` seconds;
- speed is capped to `GameConstants.MAX_SCROLL_SPEED`;
- every downstream subsystem receives the bounded values, not the raw inputs.

The `0.05f` cap represents one 20 Hz recovery step. It prevents an app resume, debugger pause, scheduler stall, or otherwise oversized finite frame from fast-forwarding simulation state.

## API

```kotlin
if (!FrameInputAdmission.accepts(deltaTime, gameScrollSpeed)) return
val dt = FrameInputAdmission.boundedDeltaSeconds(deltaTime)
val scrollSpeed = FrameInputAdmission.boundedScrollSpeed(gameScrollSpeed)
```

The bounding functions deliberately reject invalid direct use. Production callers must perform `accepts` before invoking them.

## Parallax integration

`ParallaxBackground.update()` applies this boundary before any mutation. It then:

1. repairs an inherited nonfinite or negative ambience clock to zero;
2. advances ambience by bounded `dt`;
3. stores only bounded scroll speed;
4. advances every `ParallaxLayer` with bounded inputs;
5. repairs inherited nonfinite Bloom level and clamps it to `[0, 1]`;
6. advances Bloom interpolation with bounded `dt`;
7. repairs inherited nonfinite or negative Bloom pulse before advancing it.

`ParallaxLayer.update()` retains its own lower-level fail-closed guard. The coordinator and leaf-level checks are intentionally layered: direct layer calls remain safe, while coordinator-owned state is protected before fan-out.

## Tests and invariants

The checked-in contracts cover:

- ordinary and boundary finite values;
- NaN and both infinities on either input;
- zero and negative delta;
- negative scroll speed;
- delta and speed clamping;
- direct bounding-function misuse;
- complete no-mutation behavior for rejected parallax frames;
- consistent bounded values across coordinator and layers;
- repair of inherited nonfinite ambience and Bloom accumulators;
- source-order enforcement ensuring admission precedes mutation;
- source enforcement ensuring raw frame arguments are used only for admission and bounding.

## Scope boundary

This contract currently protects `ParallaxBackground.update()`. The public `GameView` frame boundary must independently adopt the same contract so malformed input is rejected before any gameplay, persistence, audio, haptic, or presentation subsystem is updated.

The existence of this source contract does not substitute for exact-head Gradle, Robolectric, connected-emulator, or physical-device execution.
