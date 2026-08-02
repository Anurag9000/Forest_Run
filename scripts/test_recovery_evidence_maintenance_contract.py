#!/usr/bin/env python3
"""Source contracts for explicit recovery inspection and repair tooling."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/RecoveryEvidenceMaintenance.kt"
)


def extract_braced_block(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    in_string = False
    escaped = False
    line_comment = False
    block_comment = False
    index = brace

    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and nxt == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and nxt == "/":
            line_comment = True
            index += 2
            continue
        if char == "/" and nxt == "*":
            block_comment = True
            index += 2
            continue
        if char == '"':
            in_string = True
            index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1

    raise AssertionError(f"Unbalanced Kotlin block for {signature!r}")


class RecoveryEvidenceMaintenanceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_each_domain_requires_exactly_one_handler(self) -> None:
        constructor = extract_braced_block(
            self.source,
            "internal class RecoveryEvidenceMaintenanceCoordinator(",
        )
        self.assertIn(
            "handlers.associateBy(RecoveryEvidenceHandler::domain)",
            constructor,
        )
        self.assertIn(
            "handlersByDomain.size == RecoveryEvidenceDomain.entries.size",
            constructor,
        )
        self.assertIn(
            "RecoveryEvidenceDomain.entries.all(handlersByDomain::containsKey)",
            constructor,
        )

    def test_safe_recovery_never_discards_corrupt_evidence(self) -> None:
        recover = extract_braced_block(self.source, "fun recoverSafely()")
        self.assertIn("recoverIfEligible", recover)
        helper = extract_braced_block(self.source, "private fun recoverIfEligible(")
        self.assertIn(
            "RecoveryEvidenceState.CLEAN,\n            RecoveryEvidenceState.CORRUPT -> before",
            helper,
        )
        self.assertNotIn("clearEvidence()", recover)
        self.assertNotIn("clearEvidence()", helper)

    def test_corrupt_discard_requires_confirmed_corrupt_state(self) -> None:
        discard = extract_braced_block(self.source, "fun discardCorrupt(")
        gate = discard.index("before.state != RecoveryEvidenceState.CORRUPT")
        clear = discard.index("return clear(")
        self.assertLess(gate, clear)
        self.assertIn("RecoveryDiscardDisposition.NOT_APPLICABLE", discard)

    def test_pending_discard_retries_before_clear(self) -> None:
        discard = extract_braced_block(
            self.source,
            "fun discardUnresolvedPending(",
        )
        retry = discard.index("val recovered = handler.recoverSafely()")
        clear = discard.index("clear(handler, before")
        self.assertLess(retry, clear)
        self.assertIn("RecoveryDiscardDisposition.RECOVERED_INSTEAD", discard)
        self.assertIn("RecoveryEvidenceState.PENDING,", discard)
        self.assertIn("RecoveryEvidenceState.BLOCKED ->", discard)

    def test_read_failure_never_authorizes_deletion(self) -> None:
        discard = extract_braced_block(
            self.source,
            "fun discardUnresolvedPending(",
        )
        io_gate = discard.index(
            "if (before.state == RecoveryEvidenceState.IO_FAILURE)"
        )
        retry = discard.index("val recovered = handler.recoverSafely()")
        clear = discard.index("clear(handler, before")
        self.assertLess(io_gate, retry)
        self.assertLess(io_gate, clear)
        io_block = discard[io_gate:retry]
        self.assertIn("RecoveryDiscardDisposition.IO_FAILURE", io_block)
        self.assertNotIn("clearEvidence", io_block)

    def test_support_summary_contains_only_status_codes(self) -> None:
        report = extract_braced_block(
            self.source,
            "internal data class RecoveryEvidenceReport(",
        )
        self.assertIn('append("run_outcome=")', report)
        self.assertIn('append("; ghost_promotion=")', report)
        for forbidden in (
            "RunSummary",
            "GhostFrame",
            "score",
            "distanceM",
            "restQuote",
            "fingerprint",
        ):
            self.assertNotIn(forbidden, report)

    def test_run_repair_uses_non_ghost_maintenance_sink(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidRunOutcomeEvidenceHandler(",
        )
        self.assertIn("MaintenanceRunOutcomePersistenceSink(", handler)
        self.assertNotIn("AndroidRunOutcomePersistenceSink(", handler)
        sink = extract_braced_block(
            self.source,
            "private class MaintenanceRunOutcomePersistenceSink(",
        )
        self.assertIn("publishBestGhost", sink)
        self.assertIn("= false", sink)
        self.assertNotIn("GhostPersistenceManager", sink)

    def test_ghost_repair_uses_only_receipt_and_artifact_owners(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidGhostPromotionEvidenceHandler(",
        )
        self.assertIn("AtomicFileGhostPromotionReceiptStore(", handler)
        self.assertIn("GhostPromotionRecoveryCoordinator(", handler)
        self.assertIn("AndroidGhostPromotionArtifactStore(context)", handler)
        self.assertNotIn("RunOutcomePersistenceCoordinator", handler)
        self.assertNotIn("SharedPreferencesRunOutcomeRecoveryStore", handler)

    def test_clear_verifies_clean_state_after_deletion(self) -> None:
        clear = extract_braced_block(self.source, "private fun clear(")
        self.assertIn("if (!handler.clearEvidence())", clear)
        self.assertIn("val after = handler.inspect()", clear)
        self.assertIn(
            "after.state == RecoveryEvidenceState.CLEAN",
            clear,
        )
        self.assertIn("RecoveryDiscardDisposition.IO_FAILURE", clear)


if __name__ == "__main__":
    unittest.main()
