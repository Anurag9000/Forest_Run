# Forest Run — Relationship Familiarity Warmth Scoring

## Purpose

Relationship dialogue uses a secondary warmth tier after the primary relationship stage and tone are established. This tier distinguishes gentle, personal, and bonded authored lines without allowing familiarity alone to create a warm relationship.

The score and thresholds are owned exclusively by `FamiliarityWarmthScoring`.

## Eligibility gate

`RelationshipArcSystem.familiarityWarmth()` returns `NONE` unless all of the following are true:

- the entity is one of the six tracked relationship types;
- the relationship tone is `WARM`;
- the relationship stage is beyond `FIRST_IMPRESSION`.

The score therefore refines an already warm relationship. It does not override neutral or cautious tone and does not advance the primary relationship stage.

## Score

The canonical score is:

- stage base: First `0`, Recognition `1`, Trust `2`, Milestone `3`;
- `+1` for at least three clean passes;
- another `+1` for at least five clean passes;
- `+1` for at least two spares;
- `+1` for a kindness streak of at least three;
- `+1` for at least five encounters.

All restored counters are normalized to non-negative values before threshold checks.

## Tiers

- score below `5`: `GENTLE`;
- score `5` or `6`: `PERSONAL`;
- score `7` or above: `BONDED`.

The owner maps the helper's tier ordinal explicitly to the private authored-dialogue enum.

## Ownership rule

`RelationshipArcSystem` must not duplicate modifier arithmetic or tier thresholds. Its responsibility is limited to:

1. applying the eligibility gate;
2. loading canonical persisted counters;
3. passing all inputs to `FamiliarityWarmthScoring.score()`;
4. mapping `FamiliarityWarmthScoring.tierOrdinal()` to authored dialogue tiers.

This separation avoids Kotlin expression-precedence ambiguity and prevents tests from validating a helper that production does not actually use.

## Tests

The checked-in contracts cover:

- independent accumulation of every modifier;
- each modifier contributing exactly one point;
- normalization of negative restored counters;
- personal and bonded threshold boundaries;
- a public bonded cat thought requiring all independent modifiers;
- a public personal cat thought remaining distinct from bonded;
- neutral tone bypassing familiarity warmth even when the numeric score is high;
- source enforcement that the owner delegates score and tier decisions to the helper;
- source enforcement that local threshold and modifier arithmetic cannot return.

The public integration contracts require Robolectric and remain subject to an exact-head Android Gradle run.
