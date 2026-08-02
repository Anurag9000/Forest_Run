# Finite Pointer Input Admission

Forest Run treats mapped pointer coordinates as untrusted runtime input. A touch event may pass through platform transforms, safe-area mapping, scaling, test harnesses, or debug tooling before reaching a screen owner. `NaN` and infinite coordinates must not reach hit-testing or phase transitions.

## Canonical predicate

`FiniteCoordinateAdmission.accepts(x, y)` is the shared UI predicate. It returns `true` only when both coordinates are finite.

Finite coordinates are not clamped. Negative and off-screen finite values remain valid inputs to ordinary hit-testing, which decides whether they target a control. This preserves existing layout semantics while rejecting values that make comparisons ambiguous.

## Main menu boundary

`MainMenuScreen.onTap()` invokes the predicate as its first executable statement. Rejected input cannot:

- toggle comfort settings;
- invoke the Garden callback;
- advance the willow sit-rise ritual;
- latch a run-start request.

The guard intentionally precedes every nested owner and every phase branch.

## Comfort controls

`FeedbackSettingsPanelLayout.hitTest()` uses the same predicate before rectangle checks. Its existing rectangle validation remains independent: malformed or externally mutated `RectF` values also fail closed.

## Verification

The checked-in contracts include:

- exhaustive finite-coordinate Cartesian coverage;
- each `NaN`, positive-infinity, and negative-infinity value on either axis;
- every nonfinite/nonfinite pair;
- a Robolectric menu contract covering state, Garden callback, and run-request behavior;
- existing settings-layout tests for nonfinite taps and malformed rectangles;
- a Python source-order contract requiring the menu guard to remain the first executable statement;
- exact commit comparison showing the menu owner changed by one added line and the settings owner by one replacement line.

A direct Kotlin harness executed the pure admission matrix successfully in the available environment. The Python source-order contract also executed successfully against the exact current `onTap()` source region. Complete exact-head Gradle and Robolectric execution remains an external validation gate.
