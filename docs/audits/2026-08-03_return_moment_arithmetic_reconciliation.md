# 2026-08-03 — Return-Moment Arithmetic Reconciliation Audit

## Scope

This tranche reconciles the canonical audit record with the actual `main` implementation for:

- rough-run streak increment;
- long-absence elapsed-time classification;
- rollback and overflow behavior;
- exact threshold coverage;
- source ownership enforcement.

It also records a rejected collision-dispatch extraction attempt so repository history and validation claims remain transparent.

## Verified starting state

The tranche began from:

```text
680962516f4c006c594487495d5420d46e0e3e97
```

At that head, `GameView` already delegated immediate terminal HIT effects through `TerminalHitImpactCoordinator`.

## Rejected collision-dispatch attempt

A proposed `CollisionOutcomeDispatcher` was implemented and unit-tested in isolation. The connector lacked a line-level patch operation, and the first whole-file `GameView` transformation condensed and reformatted unrelated sections.

That integration was rejected immediately.

Corrective actions:

1. restored the exact prior `GameView` blob:
   ```text
   49bcd7e7331025816e5cb025f7ce8f555f53ff7b
   ```
2. created a normal fast-forward restoration commit;
3. did not force-push or rewrite history;
4. verified the restored path resolves to the exact prior blob SHA;
5. removed the unintegrated dispatcher production file;
6. removed its unintegrated test file.

Net production behavior after correction contains no collision-dispatch change and no unrelated `GameView` formatting change.

The remaining collision-result dispatcher debt is intentionally still open until an exact checkout or safe patch-capable path is available.

## Reconciliation finding

The remediation ledger described two return-history items as outstanding:

- raw `roughRunStreak + 1` arithmetic;
- raw timestamp subtraction for long absence.

The exact current production source already contains both repairs.

### Rough streak

`ReturnMomentsSystem.recordRunOutcome(...)` calls:

```kotlin
SafeProgressionArithmetic.saturatingIncrement(previous.roughRunStreak)
```

No raw rough-streak increment remains.

### Long absence

`ReturnMomentsSystem.buildGardenMoment(...)` calls:

```kotlin
SafeProgressionArithmetic.elapsedAtLeast(
    nowMs = nowMs,
    earlierMs = previous.lastActiveAtMs,
    thresholdMs = LONG_ABSENCE_MS
)
```

No raw `nowMs - previous.lastActiveAtMs` comparison remains.

## Shared arithmetic owner

`SafeProgressionArithmetic` provides:

```text
saturatingIncrement
elapsedAtLeast
elapsedOrZero
```

### Saturating increment

The helper:

1. rejects a negative maximum;
2. clamps restored input into the valid range;
3. returns the ceiling when already saturated;
4. adds one only below the ceiling.

The default ceiling is:

```text
Int.MAX_VALUE / 16
```

### Elapsed predicate

The helper returns false for:

- negative `nowMs`;
- negative `earlierMs`;
- negative threshold;
- clock rollback where `nowMs < earlierMs`.

Only after these checks does it subtract timestamps.

This prevents signed overflow from transforming rollback into an apparent long absence.

## Added ownership contract

Added:

```text
scripts/test_return_moment_arithmetic_contract.py
```

It requires:

- one canonical saturating increment call in `recordRunOutcome(...)`;
- no raw rough-streak `+ 1` expression;
- one rollback-safe elapsed predicate in `buildGardenMoment(...)`;
- no direct timestamp subtraction;
- no absolute-value workaround;
- normalization before increment;
- invalid-order guards before subtraction;
- the explicit 36-hour threshold constant.

The permanent host workflow discovers all `scripts/test_*.py` files automatically.

## Expanded integration coverage

The repository already contained:

```text
SafeProgressionArithmeticTest.kt
ReturnMomentsArithmeticIntegrationTest.kt
```

Existing coverage included:

- negative counter normalization;
- saturation;
- zero and invalid ceilings;
- rollback and invalid timestamps;
- maximum elapsed values;
- saturated rough-run persistence;
- rough increment and gentle reset;
- pathological subtraction-overflow input.

Added an exact boundary test proving:

```text
35:59:59.999 elapsed → ordinary daily greeting
36:00:00.000 elapsed → long-absence greeting
```

The test uses `previewGardenMoment(...)`, so the two observations do not mutate greeting state.

## Documentation

Added:

```text
docs/RETURN_MOMENT_ARITHMETIC.md
```

It records the owner, safety invariants, persistence boundary, test surface, and evidence limitations.

## Focused validation

A focused Kotlin/JVM compile and executable harness passed:

```text
return moment arithmetic checks passed
```

The harness covered:

- increment from ceiling minus one;
- saturation at the ceiling;
- normalization of `Int.MAX_VALUE`;
- negative restored value normalization;
- one millisecond below the absence threshold;
- exactly the threshold;
- ordinary rollback;
- pathological negative-now/maximum-earlier input;
- rollback-safe elapsed fallback.

The source-contract parser was exercised against the exact production call shapes:

```text
return moment arithmetic contract checks passed
```

## Evidence not obtained

This environment did not execute:

- the full Gradle build;
- the complete JUnit/Robolectric suite;
- Android lint;
- debug or release packaging;
- connected emulator tests;
- physical-device return-history acceptance.

No successful exact-head CI conclusion is claimed.

## Remaining debt after this tranche

- the complete collision-result dispatcher remains in `GameView`;
- STUMBLE and MERCY_MISS live effect adapters remain private to `GameView`;
- `GameView` remains large;
- relationship authored-warmth precedence still requires a safe exact patch path;
- exact-head Android, emulator, physical-device, signing, and store evidence remain outstanding.
