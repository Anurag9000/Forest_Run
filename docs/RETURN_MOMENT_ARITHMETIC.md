# Forest Run — Return-Moment Arithmetic Contract

## Purpose

Return history combines a persistent rough-run streak with wall-clock absence detection. Both values can originate from old installs, repaired preferences, test fixtures, clock rollback, or extreme restored data.

The arithmetic boundary must therefore be explicit rather than relying on raw integer addition or timestamp subtraction.

## Owner

`SafeProgressionArithmetic` owns the shared primitives:

```kotlin
saturatingIncrement(value, maximum)
elapsedAtLeast(nowMs, earlierMs, thresholdMs)
elapsedOrZero(nowMs, earlierMs)
```

`ReturnMomentsSystem` consumes those primitives. It does not perform raw rough-streak increment or direct absence subtraction.

## Rough-run streak

A run is considered rough when any existing authored condition is true:

- the resulting forest mood is `FEARFUL`;
- at least two hits occur before 650 metres;
- at least one hit occurs with no kindness chain and fewer than four Seeds.

For a rough run:

```text
load prior streak
→ normalize into 0..DEFAULT_COUNTER_MAX
→ increment by one when below the ceiling
→ remain at the ceiling when already saturated
```

The canonical ceiling is:

```kotlin
Int.MAX_VALUE / 16
```

A non-rough run resets the streak to zero.

This preserves ordinary progression while preventing signed overflow or negative restored values from becoming a valid large streak.

## Long absence

The authored threshold remains exactly:

```text
36 hours
```

A long absence is true only when:

```text
lastActiveAtMs > 0
nowMs >= 0
earlierMs >= 0
thresholdMs >= 0
nowMs >= earlierMs
nowMs - earlierMs >= thresholdMs
```

All ordering and sign checks occur before subtraction. Clock rollback therefore returns false instead of wrapping into a large positive elapsed duration.

The boundary is inclusive: an elapsed duration of exactly 36 hours is a long absence; one millisecond less is not.

## Persistence behavior

`recordRunOutcome(...)` still writes the supplied current timestamp through the existing `SaveManager` state owner. This contract changes only arithmetic safety; it does not alter rough-run classification, greeting priority, authored copy, relationship selection, or persistence namespace.

`previewGardenMoment(...)` remains non-mutating. `resolveGardenMoment(...)` retains its existing greeting-state commit.

## Executable coverage

`SafeProgressionArithmeticTest` covers:

- negative counter normalization;
- ordinary increment;
- saturation at a caller ceiling;
- default-ceiling saturation from `Int.MAX_VALUE`;
- zero ceiling;
- negative-ceiling rejection;
- timestamp rollback;
- invalid negative inputs;
- maximum representable elapsed value;
- zero elapsed fallback.

`ReturnMomentsArithmeticIntegrationTest` covers:

- saturated rough streak remaining saturated;
- ordinary rough increment;
- gentle-run reset;
- pathological rollback that would overflow raw subtraction;
- one millisecond before the 36-hour threshold;
- exactly the 36-hour threshold.

`scripts/test_return_moment_arithmetic_contract.py` locks production ownership:

- `ReturnMomentsSystem` must call `saturatingIncrement(...)`;
- raw `roughRunStreak + 1` is forbidden;
- long absence must call `elapsedAtLeast(...)`;
- raw timestamp subtraction and absolute-value workarounds are forbidden;
- helper guards must precede subtraction;
- the 36-hour constant must remain explicit.

All `scripts/test_*.py` files are discovered by the permanent host workflow.

## Evidence boundary

Focused Kotlin compilation and an executable arithmetic harness passed for saturation, rollback, pathological timestamps, and the exact threshold.

The new source-contract assertions were exercised against the exact production call shapes.

The complete Android/Robolectric suite was not executed through an exact-head Gradle checkout in this environment. Empty GitHub status or workflow lists are not evidence of successful CI.
