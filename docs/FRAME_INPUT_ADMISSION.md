# Forest Run — Frame Input Admission Contract

## Purpose

Public per-frame coordinators must not allow malformed timing or speed values to mutate runtime state. A nonfinite, nonpositive, reversing, or unbounded frame can otherwise poison animation clocks, physics, Bloom interpolation, input timing, camera state, procedural ambience, audio transitions, persistence decisions, and every downstream update that receives it.

`FrameInputAdmission` is the canonical allocation-free boundary for frame timing and optional world scroll speed.

## Contracts

### Delta-only admission

A delta is admitted only when it is finite and strictly positive:

```kotlin
if (!FrameInputAdmission.acceptsDelta(deltaTime)) return
val dt = FrameInputAdmission.boundedDeltaSeconds(deltaTime)
```

This form is used at the top-level `GameView.update()` boundary before a trustworthy scroll speed exists for the frame.

### Delta-and-speed admission

A frame pair is admitted only when:

- `deltaSeconds` is finite;
- `deltaSeconds` is strictly positive;
- `scrollSpeed` is finite;
- `scrollSpeed` is non-negative.

```kotlin
if (!FrameInputAdmission.accepts(deltaTime, gameScrollSpeed)) return
val dt = FrameInputAdmission.boundedDeltaSeconds(deltaTime)
val scrollSpeed = FrameInputAdmission.boundedScrollSpeed(gameScrollSpeed)
```

This form is used by `ParallaxBackground.update()`.

## Bounding

After admission:

- delta is capped to `0.05f` seconds;
- speed is capped to `GameConstants.MAX_SCROLL_SPEED`;
- every downstream subsystem receives bounded values rather than raw external values.

The `0.05f` cap represents one 20 Hz recovery step. It prevents app resume, debugger pause, scheduler stall, or another oversized finite frame from fast-forwarding simulation state.

The bounding functions reject invalid direct use. Callers must perform the appropriate admission check before invoking them.

## GameView integration

`GameView.update()` is the public frame boundary called by `GameThread`. It now performs only two operations:

1. reject a delta that is nonfinite or nonpositive;
2. dispatch the bounded delta to `updateBounded()`.

The original gameplay body remains in `updateBounded()` and retains its sequencing. Rejected frames return before:

- `debugFrameCounter` changes;
- camera shake advances;
- active input timing changes or gestures are cancelled;
- Bloom presentation timers change;
- menu, Garden, death, restart, physics, player, entity, ghost, audio, haptic, particle, or dialogue owners update;
- collision-triggered persistence can execute.

Every accepted caller frame advances the legacy body at most once and with a delta no greater than `0.05f`.

## Parallax integration

`ParallaxBackground.update()` applies the delta-and-speed boundary before any mutation. It then:

1. repairs an inherited nonfinite or negative ambience clock to zero;
2. advances ambience by bounded `dt`;
3. stores only bounded scroll speed;
4. advances every `ParallaxLayer` with bounded inputs;
5. repairs inherited nonfinite Bloom level and clamps it to `[0, 1]`;
6. advances Bloom interpolation with bounded `dt`;
7. repairs inherited nonfinite or negative Bloom pulse before advancing it.

`ParallaxLayer.update()` retains its own lower-level fail-closed guard. The public coordinator, parallax coordinator, and leaf-level checks are intentionally layered.

## Tests and invariants

The checked-in contracts cover:

- ordinary and boundary finite values;
- NaN and both infinities;
- zero and negative delta;
- negative scroll speed;
- delta-only admission;
- delta and speed clamping;
- direct bounding-function misuse;
- complete no-mutation behavior for rejected parallax frames;
- consistent bounded values across parallax coordinator and layers;
- repair of inherited nonfinite parallax accumulators;
- rejected `GameView` deltas leaving the frame counter unchanged;
- ordinary and oversized finite `GameView` deltas dispatching exactly one frame;
- source-order enforcement that the `GameView` public method contains only admission and bounded dispatch;
- source-order enforcement that parallax admission precedes mutation;
- source enforcement that raw parallax arguments are used only for admission and bounding.

## Scope boundary

The public `GameView` boundary and the `ParallaxBackground` coordinator are now protected. Individual subsystem owners may retain additional defensive checks appropriate to their own invariants.

This source and focused-test evidence does not substitute for exact-head Gradle, Robolectric, connected-emulator, or physical-device execution.
