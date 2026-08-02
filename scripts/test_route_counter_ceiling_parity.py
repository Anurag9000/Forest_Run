#!/usr/bin/env python3
"""Keep recovery route counters aligned with SaveManager derived counters."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SAVE_MANAGER = (
    ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt"
)
RECOVERY = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomeRecoveryStore.kt"
)
SNAPSHOT = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RunOutcomeSummarySnapshotStore.kt"
)


class RouteCounterCeilingParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.save_manager = SAVE_MANAGER.read_text(encoding="utf-8")
        cls.recovery = RECOVERY.read_text(encoding="utf-8")
        cls.snapshot = SNAPSHOT.read_text(encoding="utf-8")

    def test_both_owners_use_the_same_derived_counter_formula(self) -> None:
        self.assertIn(
            "private const val MAX_DERIVED_COUNTER = Int.MAX_VALUE / 16",
            self.save_manager,
        )
        self.assertIn(
            "internal const val MAX_RECOVERABLE_ROUTE_TIER_COUNT = Int.MAX_VALUE / 16",
            self.recovery,
        )

    def test_recovery_transition_bounds_before_incrementing(self) -> None:
        start = self.recovery.index("fun nextRouteTierCount(")
        end = self.recovery.index("fun persistedSummary(", start)
        transition = self.recovery[start:end]
        bound = transition.index(
            "previous.coerceIn(0, MAX_RECOVERABLE_ROUTE_TIER_COUNT)"
        )
        increment = transition.index("else -> boundedPrevious + 1")
        self.assertLess(bound, increment)

    def test_journal_rejects_before_counts_above_canonical_ceiling(self) -> None:
        self.assertIn(
            "record.previousRouteTierCount in 0..MAX_RECOVERABLE_ROUTE_TIER_COUNT",
            self.recovery,
        )

    def test_atomic_snapshot_defensively_bounds_route_count(self) -> None:
        self.assertIn(
            "routeTierCount.coerceIn(0, MAX_RECOVERABLE_ROUTE_TIER_COUNT)",
            self.snapshot,
        )


if __name__ == "__main__":
    unittest.main()
