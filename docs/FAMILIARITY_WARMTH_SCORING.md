# Forest Run — Familiarity Warmth Scoring

## Purpose

Relationship stage, relationship tone, and familiarity warmth are separate concepts.

- **Stage** controls the durable relationship progression level: First Impression, Recognition, Trust, or Bond.
- **Tone** classifies current history as warm, neutral, or cautious.
- **Familiarity warmth** deepens authored dialogue inside an already warm relationship without advancing the durable stage.

This document records the canonical additive warmth model and the ownership boundary that prevents Kotlin conditional-expression precedence from suppressing later modifiers.

## Production ownership

`RelationshipArcSystem.familiarityWarmth(...)` owns storage reads and eligibility:

```text
tracked entity
AND warm tone
AND stage beyond First Impression
```

It reads:

```text
clean pass count
spared count
kindness streak
encounter count
```

The function does not calculate an inline chained conditional expression. It delegates all values to:

```kotlin
FamiliarityWarmthScoring.score(...)
```

The resulting integer is mapped through:

```kotlin
FamiliarityWarmthScoring.tierOrdinal(...)
```

into `GENTLE`, `PERSONAL`, or `BONDED` authored warmth.

## Additive score model

The score is:

```text
stage base
+ pass count >= 3
+ pass count >= 5
+ spared count >= 2
+ kindness streak >= 3
+ encounter count >= 5
```

Every satisfied modifier contributes exactly one point independently.

### Stage base

```text
First Impression = 0
Recognition      = 1
Trust            = 2
Milestone/Bond   = 3
```

### Thresholds

```text
score <= 4  → GENTLE
score 5–6   → PERSONAL
score >= 7  → BONDED
```

The implementation exposes the stable thresholds as:

```kotlin
PERSONAL_THRESHOLD = 5
BONDED_THRESHOLD = 7
```

## Why a pure scorer is required

A visually chained Kotlin expression such as:

```kotlin
base + if (a) 1 else 0 + if (b) 1 else 0
```

can associate the later addition with an `else` branch rather than making every modifier independent. When an earlier condition is true, later modifiers can disappear from the result.

The canonical implementation avoids that ambiguity by using explicit additions of:

```kotlin
bonus(condition)
```

Each bonus therefore contributes independently regardless of the truth value of preceding conditions.

## Restored-state safety

Every persisted counter is normalized with `coerceAtLeast(0)` before threshold evaluation.

Negative or malformed restored values cannot:

- subtract warmth;
- produce a false tier;
- interfere with a valid positive modifier;
- overflow the score.

The maximum possible score is eight, so extreme positive counters cannot overflow arithmetic either; they only satisfy fixed Boolean thresholds.

## Public authored-copy consequence

For a warm Cat relationship at Milestone stage, combined history containing:

```text
five encounters
five clean passes
two spares
```

reaches the BONDED threshold and selects:

```text
You came back to our quiet.
```

This public line is regression-tested so the correctness guarantee is not limited to a private helper.

## Tests and contracts

`FamiliarityWarmthScoringTest` covers:

- all modifiers accumulated together;
- each modifier contributing exactly one point;
- negative restored counters;
- PERSONAL and BONDED threshold boundaries.

`FamiliarityWarmthIntegrationTest` covers:

- real persisted relationship history;
- Milestone-stage resolution;
- the BONDED Cat pass line;
- multiple independent combinations completing the BONDED threshold.

`scripts/test_familiarity_warmth_contract.py` locks:

- `RelationshipArcSystem` delegation to the pure scorer;
- all five runtime inputs;
- independent `bonus(...)` additions;
- counter normalization;
- stable authored thresholds;
- public BONDED-copy integration coverage;
- absence of the former inline conditional-chain shape.

The host workflow discovers this contract through the repository-wide `scripts/test_*.py` pattern.

## Evidence boundary

Focused Kotlin compilation and an executable scorer harness passed for:

- the complete eight-point score;
- both seven-point independent combinations;
- Trust-stage accumulation to BONDED;
- negative restored counters;
- tier boundaries.

The source-contract parser also passed against the canonical scorer and relationship-owner structure.

The complete exact-head Android Gradle, JUnit/Robolectric, emulator, and physical-device suites were not executed in this environment.
