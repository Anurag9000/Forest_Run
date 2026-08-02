# Forest Run — Return Moment Arithmetic Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Branch: `main`

This note records closure of the two arithmetic call sites previously isolated in `ReturnMomentsSystem`.

## Changes

Commit `717e89b31f7e0a78a67d334d050cce4782b977b7` replaced the raw rough-run increment with:

```kotlin
SafeProgressionArithmetic.saturatingIncrement(previous.roughRunStreak)
```

It also replaced direct elapsed-time subtraction with:

```kotlin
SafeProgressionArithmetic.elapsedAtLeast(
    nowMs = nowMs,
    earlierMs = previous.lastActiveAtMs,
    thresholdMs = LONG_ABSENCE_MS
)
```

The first change prevents integer wraparound. The second rejects negative timestamps and clock rollback before subtraction.

The complete 731-line owner was compiled locally against focused engine stubs. Its committed Git blob SHA, `dcbe42074fe928e979d595c4912df46d8dba422a`, exactly matched the compiled 47,164-byte local source.

## Tests

`ReturnMomentsArithmeticIntegrationTest` was added in commit `fae588aacf4a550337cf0117fe1a1bd478d9528b` and refined in commit `52e59008d3f8adf6fc8d5a40c2e4a1b6eda0fbb0`.

It verifies:

- saturation at `SafeProgressionArithmetic.DEFAULT_COUNTER_MAX`;
- ordinary increment from four to five;
- reset to zero after a gentle run;
- a bounded pre-epoch clock value with `lastActiveAtMs = Long.MAX_VALUE` cannot wrap into a false long-absence result;
- the normal greeting is selected instead of `Welcome Back` in that rollback case.

The production owner and test type surface compiled with `kotlinc` against focused stubs. A focused executable harness ran all three test methods successfully. This does not replace exact-head Gradle or Robolectric execution.

## Debt ledger

The `ReturnMomentsSystem` arithmetic item is closed.

Remaining runtime debt:

1. connect `FamiliarityWarmthScoring` to `RelationshipArcSystem.familiarityWarmth()`;
2. add finite-coordinate admission to `MainMenuScreen.onTap()`;
3. harden finite-delta boundaries in `ParallaxBackground` and `GameView.update()`;
4. decompose `GameView` and consolidate persistence ownership through behavior-preserving seams.

All work was committed directly to `main`; no branch, pull request, force-push, or history rewrite was used. Exact-head Android, lint, packaging, emulator, physical-device, signing, delivery, accessibility, and store-policy gates remain outstanding.
