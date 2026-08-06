#!/usr/bin/env python3
"""Initialization-order contract for the ghost persistence singleton."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANAGER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceManager.kt"
)


class GhostPersistenceInitializationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = MANAGER.read_text(encoding="utf-8")

    def test_executor_dependencies_are_initialized_before_scheduler(self) -> None:
        limit = self.source.index(
            "private const val MAX_CONCURRENT_NAMESPACE_WRITES = 2"
        )
        ordinal = self.source.index("private val workerOrdinal = AtomicInteger(0)")
        executor = self.source.index(
            "Executors.newFixedThreadPool(MAX_CONCURRENT_NAMESPACE_WRITES)"
        )
        self.assertLess(limit, executor)
        self.assertLess(ordinal, executor)

    def test_scheduler_remains_bounded_and_namespace_serial(self) -> None:
        self.assertEqual(
            1,
            self.source.count(
                "Executors.newFixedThreadPool(MAX_CONCURRENT_NAMESPACE_WRITES)"
            ),
        )
        self.assertIn("GhostNamespaceSerialScheduler(", self.source)
        self.assertNotIn("Executors.newCachedThreadPool", self.source)
        self.assertNotIn("Executors.newSingleThreadExecutor", self.source)


if __name__ == "__main__":
    unittest.main()
