# Forest Run — Route Counter Ceiling Parity Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`

## Finding

Final recovery review found that pacifist-route counts are not unbounded `Int` counters.

`SaveManager` reads and increments derived counters using:

```kotlin
private const val MAX_DERIVED_COUNTER = Int.MAX_VALUE / 16
```

The first recovery transition used ordinary `Int.MAX_VALUE` saturation. That mismatch could create an impossible journal after-state:

1. recovery predicts `Int.MAX_VALUE`;
2. the atomic snapshot writes that value;
3. `SaveManager.loadRouteTierCount` clamps the stored value to `Int.MAX_VALUE / 16`;
4. recovery verification cannot observe its predicted state;
5. the journal remains pending or blocked even though the SharedPreferences write completed.

## Correction

Added the shared recovery-side mirror:

```kotlin
internal const val MAX_RECOVERABLE_ROUTE_TIER_COUNT = Int.MAX_VALUE / 16
```

`RunOutcomeRecoveryTransitions.nextRouteTierCount(...)` now:

1. bounds the previous value into `0..MAX_RECOVERABLE_ROUTE_TIER_COUNT`;
2. preserves the bounded value for `PacifistRouteTier.NONE`;
3. increments persistent tiers by one below the ceiling;
4. remains at the ceiling once saturated.

`SharedPreferencesRunOutcomeSummarySnapshotStore` also defensively bounds its route-count argument to the same range before writing.

Journal validation rejects a recorded previous route count above the canonical ceiling.

## Verification

Added or updated:

- `RunOutcomeSummarySnapshotStoreTest`;
- `RunOutcomeRecoveryTransitionIntegrationTest`;
- `test_run_outcome_recovery_store_contract.py`;
- `test_run_outcome_recovery_record_validation_contract.py`;
- `test_route_counter_ceiling_parity.py`.

The parity contract checks both literal owner formulas:

```text
SaveManager.MAX_DERIVED_COUNTER = Int.MAX_VALUE / 16
MAX_RECOVERABLE_ROUTE_TIER_COUNT = Int.MAX_VALUE / 16
```

It also locks transition bounding, journal validation, and defensive snapshot bounding.

A focused executable Kotlin matrix passed:

```text
canonical route ceiling checks passed: 134217727
```

The matrix covered:

- negative previous count;
- NONE below and above the ceiling;
- increment immediately below the ceiling;
- persistent tiers at and above the ceiling;
- negative and `Int.MAX_VALUE` snapshot inputs.

## Exact diff evidence

Production correction `e504f293bbdebc0d3e75f55fde604573d7e1c667` changed only:

- the recovery ceiling constant;
- previous-route journal validation;
- the route transition formula.

Production correction `8e9e665b552c71487b05eefaf7e2989b537ed73d` changed only the route-count bound applied by the atomic snapshot writer.

No mood, return, summary, ghost, best-distance, collision, rendering, or gameplay behavior changed.

## Evidence boundary

The focused ceiling matrix passed. The GitHub host remained unavailable to the local runtime, so no exact-head checkout, Gradle execution, or Robolectric execution was possible in this session. The connector exposed no exact-head workflow run or successful status context.
