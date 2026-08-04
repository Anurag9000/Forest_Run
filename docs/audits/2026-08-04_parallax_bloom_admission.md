# 2026-08-04 — Parallax Bloom Presentation Admission Audit

## Scope

This tranche closed the remaining numeric boundary identified for:

```text
ParallaxBackground.setBloomState(...)
```

The audit focused on visual Bloom activation and afterglow channels. It did not alter gameplay Bloom ownership, conversion, rewards, duration, persistence, collision behavior, or authored presentation timing.

## Starting point

Verified `main` before this tranche:

```text
9d31eb39bfeede40abdb96cbdf4dd0c9e57b3233
```

The branch was identical to that SHA when work began.

## Defect

The prior implementation used:

```kotlin
activationLevel.coerceIn(0f, 1f)
afterglowLevel.coerceIn(0f, 1f)
```

For floating-point `NaN`, ordinary comparison-based clamping can return `NaN` unchanged. That allowed malformed caller state to persist in renderer fields and contaminate later Bloom presentation arithmetic.

Positive and negative infinity did not create the same `NaN` propagation shape, but the new policy treats every non-finite value consistently and fails it closed to zero.

## Implementation

Added:

```text
app/src/main/java/com/anurag9000/forestrun/engine/BloomPresentationAdmission.kt
```

Canonical rule:

```kotlin
value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
```

Integrated at:

1. `resolveParallaxAtmosphereProfile(...)`;
2. `ParallaxBackground.setBloomState(...)` activation level;
3. `ParallaxBackground.setBloomState(...)` afterglow level;
4. `drawBloomTransformation(...)` smoothed Bloom level;
5. `drawBloomTransformation(...)` activation boost;
6. `drawBloomTransformation(...)` afterglow strength.

## Exact renderer diff

The whole-file contents API replacement was built from the verified blob:

```text
d6ccd98d77ba7ee724450b63b43a82cb776a07d0
```

GitHub's generated commit diff contained exactly three hunks:

```text
profile admission: 1 changed line
setBloomState:     2 changed lines
draw defense:      3 changed lines
```

No unrelated formatting or renderer code changed.

Renderer commit:

```text
326ae2e3fa5ba9258e8a90afdaf7f83d5bc844cb
```

## Tests added

### Pure admission test

```text
BloomPresentationAdmissionTest.kt
```

Covers:

- `Float.NaN`;
- `Float.POSITIVE_INFINITY`;
- `Float.NEGATIVE_INFINITY`;
- below-range finite input;
- zero;
- valid fractional input;
- one;
- above-range finite input.

### Renderer integration test

```text
ParallaxBloomAdmissionIntegrationTest.kt
```

Constructs the real background and verifies stored private fields after public `setBloomState(...)` calls.

The test proves malformed visual levels fail closed without changing the independent active/inactive target.

## Source contract

Added:

```text
scripts/test_parallax_bloom_admission_contract.py
```

It locks:

- explicit finiteness admission;
- zero fallback;
- finite unit-interval clamping;
- public state ownership;
- atmosphere-profile ownership;
- draw-time defensive ownership;
- non-finite and finite integration coverage;
- removal of direct activation/afterglow clamping.

## Focused execution

A Kotlin/JVM harness compiled and executed the production-shaped helper and reported:

```text
Parallax Bloom admission checks passed
```

The harness verified:

```text
NaN               → 0f
+infinity         → 0f
-infinity         → 0f
-0.25f            → 0f
0.35f             → 0.35f
1.75f             → 1f
```

The Python contract was syntax-checked and executed against production-shaped fixtures:

```text
Ran 4 tests
OK
```

## Behavior preservation

Unchanged:

- Bloom target boolean mapping;
- Bloom rise and fall blend speeds;
- pulse timing;
- afterglow weighting;
- atmosphere formulas after normalization;
- lighting formulas;
- gameplay Bloom meter and timer;
- Bloom conversion rewards;
- entity and collision systems;
- audio and haptics;
- persistence;
- `GameView`.

## Evidence not claimed

This tranche did not execute:

- a complete exact-head Gradle build;
- checked-in JUnit/Robolectric tests through Gradle;
- Android lint;
- emulator rendering;
- physical-device Bloom activation or afterglow;
- visual intensity acceptance;
- dense-atmosphere performance profiling;
- signed release or store validation.

## Result

The renderer no longer stores or consumes non-finite Bloom presentation strengths through the targeted paths. The previous `setBloomState(...)` numeric debt is closed at source, profile, and final draw boundaries, with pure tests, integration coverage, a source contract, documentation, and exact-diff evidence.
