# Forest Run — Parallax Bloom Presentation Admission

## Purpose

`ParallaxBackground` receives three visual-only Bloom strength channels:

```text
smoothed Bloom level
activation burst level
afterglow level
```

These values influence atmosphere density, lighting intensity, Bloom overlays, rays, motes, and pulse presentation. They must never carry `NaN` or infinity into rendering arithmetic.

This boundary does not change Bloom gameplay state, meter ownership, duration, rewards, conversion rules, or activation timing. It protects presentation inputs only.

## Canonical admission policy

`BloomPresentationAdmission.level(value)` applies one rule:

```text
non-finite value → 0f
finite value < 0 → 0f
finite value in [0, 1] → unchanged
finite value > 1 → 1f
```

All non-finite values fail closed rather than being interpreted as maximum Bloom strength.

This means:

```text
NaN                → 0f
positive infinity  → 0f
negative infinity  → 0f
-0.25f             → 0f
0.35f              → 0.35f
1.75f              → 1f
```

## Why direct `Float.coerceIn` was insufficient

Kotlin's ordinary floating-point comparisons do not make `NaN` less than the minimum or greater than the maximum. A direct:

```kotlin
value.coerceIn(0f, 1f)
```

can therefore preserve `NaN`.

Once stored in `bloomActivationLevel` or `bloomAfterglowLevel`, that value can contaminate later multiplication, `maxOf`, pulse strength, alpha calculations, and drawing coordinates.

The shared admission helper checks finiteness before clamping.

## Production ownership

The boundary is used at three levels.

### Public state admission

`ParallaxBackground.setBloomState(...)` preserves the boolean target:

```text
active   → bloomTarget = 1f
inactive → bloomTarget = 0f
```

It independently normalizes activation and afterglow:

```kotlin
bloomActivationLevel = BloomPresentationAdmission.level(activationLevel)
bloomAfterglowLevel = BloomPresentationAdmission.level(afterglowLevel)
```

Malformed presentation channels do not disable or enable gameplay Bloom. They only lose their unsafe visual boost.

### Atmosphere profile admission

`resolveParallaxAtmosphereProfile(...)` normalizes its `bloomStrength` argument before it affects:

- world scale;
- drift;
- gust strength;
- sway amplitude;
- leaf, petal, firefly, glow-mote, ribbon, and mist density;
- overlay alpha values.

This keeps the pure profile builder safe for direct tests and future callers, not only the current render path.

### Draw-time defense

`drawBloomTransformation(...)` re-normalizes:

```text
bloomLevel
bloomActivationLevel
bloomAfterglowLevel
```

before computing overlay strength. This is a final defensive boundary against corrupted internal state, reflective debug mutation, future deserialization, or a later regression in admission code.

## Preserved behavior

Finite authored behavior is unchanged:

- values below zero still clamp to zero;
- values above one still clamp to one;
- valid fractional levels remain exact;
- `isActive` still controls only the target;
- Bloom blend speeds remain 4.5 rising and 2.8 falling;
- Bloom pulse advancement remains unchanged;
- afterglow weighting remains unchanged;
- atmosphere and lighting formulas remain unchanged after admission.

No entity, collision, reward, persistence, audio, haptic, or input code changed.

## Tests

`BloomPresentationAdmissionTest` covers:

- `NaN`;
- positive infinity;
- negative infinity;
- below-range finite values;
- zero;
- in-range fractional values;
- one;
- above-range finite values.

`ParallaxBloomAdmissionIntegrationTest` constructs the real `ParallaxBackground`, invokes `setBloomState(...)`, and verifies private stored state through reflection. It proves:

- non-finite activation and afterglow store as zero;
- finite clamping is retained;
- valid fractional values remain unchanged;
- the boolean Bloom target remains independent from malformed visual levels.

## Source contract

`scripts/test_parallax_bloom_admission_contract.py` requires:

- one finite admission owner;
- explicit `isFinite()` handling;
- zero fallback;
- shared use by public state admission;
- shared use by atmosphere profile construction;
- shared use by final Bloom drawing;
- non-finite and finite integration coverage;
- absence of the former direct activation/afterglow `coerceIn` calls.

The repository's `scripts/test_*.py` discovery pattern includes this contract automatically.

## Validation performed in this tranche

Focused Kotlin/JVM compilation and an executable harness passed for the admission helper. The harness checked all three non-finite values and representative finite clamp cases.

The source-contract parser was syntax-checked and executed against production-shaped source fixtures; all four contract tests passed.

The exact `ParallaxBackground.kt` GitHub diff was inspected. It contained only:

1. atmosphere-profile Bloom admission;
2. activation and afterglow state admission;
3. three draw-time defensive reads.

## Evidence boundary

The following remain separate:

- exact-head Gradle compilation;
- the checked-in JUnit/Robolectric suite;
- Android lint;
- emulator rendering;
- physical-device Bloom transitions;
- visual acceptance of Bloom intensity and afterglow;
- performance measurements under dense atmospheric effects.

Focused helper execution and exact-diff review establish the arithmetic boundary. They do not replace Android rendering or visual product approval.
