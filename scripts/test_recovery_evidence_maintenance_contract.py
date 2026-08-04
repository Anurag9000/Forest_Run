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
STATE_STORE = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/NamespaceBoundRunOutcomeMaintenanceStateStore.kt"
)
INTEGRATION_TEST = (
    ROOT
    / "app/src/test/java/com/anurag9000/forestrun/engine/RecoveryEvidenceMaintenanceNamespaceIntegrationTest.kt"
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
        cls.state_store = STATE_STORE.read_text(encoding="utf-8")
        cls.integration_test = INTEGRATION_TEST.read_text(encoding="utf-8")

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
            "sha256Hex",
        ):
            self.assertNotIn(forbidden, report)

    def test_entrypoint_captures_one_namespace_for_both_handlers(self) -> None:
        entrypoint = extract_braced_block(
            self.source,
            "internal class AndroidRecoveryEvidenceMaintenance(",
        )
        capture = entrypoint.index(
            "private val namespace = GhostPersistenceNamespace.capture()"
        )
        run_handler = entrypoint.index(
            "AndroidRunOutcomeEvidenceHandler(appContext, namespace.prefsName)"
        )
        ghost_handler = entrypoint.index(
            "AndroidGhostPromotionEvidenceHandler(appContext, namespace)"
        )
        self.assertLess(capture, run_handler)
        self.assertLess(capture, ghost_handler)
        self.assertEqual(1, entrypoint.count("GhostPersistenceNamespace.capture()"))

    def test_run_repair_uses_namespace_bound_non_ghost_sink(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidRunOutcomeEvidenceHandler(",
        )
        self.assertIn("private val namespace: String", handler)
        self.assertIn("MaintenanceRunOutcomePersistenceSink(", handler)
        self.assertNotIn("SaveManager.activePrefsNameForTests", handler)
        self.assertNotIn("AndroidRunOutcomePersistenceSink(", handler)

        sink = extract_braced_block(
            self.source,
            "private class MaintenanceRunOutcomePersistenceSink(",
        )
        self.assertIn(
            "NamespaceBoundRunOutcomeMaintenanceStateStore(context, namespace)",
            sink,
        )
        self.assertIn("publishBestGhost", sink)
        self.assertIn("= false", sink)
        for method in (
            "loadBestDistanceM()",
            "loadForestMoodState()",
            "saveForestMoodState(state)",
            "loadReturnMomentState()",
            "saveReturnMomentState(state)",
            "loadLastRunSummary()",
            "loadRouteTierCount(tier)",
        ):
            self.assertIn(f"stateStore.{method}", sink)
        self.assertNotIn("SaveManager.", sink)
        self.assertNotIn("GhostPersistenceManager", sink)

    def test_bound_state_store_uses_one_preferences_instance_and_sync_writes(self) -> None:
        self.assertIn(
            "context.applicationContext.getSharedPreferences(\n        persistenceNamespace,",
            self.state_store,
        )
        self.assertEqual(1, self.state_store.count("getSharedPreferences("))
        self.assertIn("fun saveForestMoodState", self.state_store)
        self.assertIn("fun saveReturnMomentState", self.state_store)
        self.assertGreaterEqual(self.state_store.count(".commit()"), 2)
        self.assertNotIn("SaveManager.activePrefsNameForTests", self.state_store)
        self.assertNotIn("SaveManager.activeGhostFilenameForTests", self.state_store)

    def test_ghost_repair_uses_bound_receipt_manifest_and_artifact_owners(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidGhostPromotionEvidenceHandler(",
        )
        self.assertIn("namespace: GhostPersistenceNamespace", handler)
        self.assertIn(
            "NamespaceBoundGhostPromotionArtifactStore(context, namespace)",
            handler,
        )
        self.assertIn("AtomicFileGhostPromotionReceiptStore(", handler)
        self.assertIn("AtomicFileGhostArtifactManifestStore(", handler)
        self.assertIn("ghostFilename = namespace.ghostFilename", handler)
        self.assertIn("GhostPromotionRecoveryCoordinator(", handler)
        self.assertIn("artifactStore = artifactStore", handler)
        self.assertIn("manifestStore = manifestStore", handler)
        self.assertNotIn("AndroidGhostPromotionArtifactStore", handler)
        self.assertNotIn("SaveManager.activeGhostFilenameForTests", handler)
        self.assertNotIn("RunOutcomePersistenceCoordinator", handler)
        self.assertNotIn("SharedPreferencesRunOutcomeRecoveryStore", handler)

    def test_ghost_inspection_distinguishes_receipt_and_manifest_corruption(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidGhostPromotionEvidenceHandler(",
        )
        inspect = extract_braced_block(handler, "override fun inspect()")
        self.assertIn('"invalid_receipt"', inspect)
        self.assertIn("inspectManifest()", inspect)

        manifest = extract_braced_block(handler, "private fun inspectManifest()")
        self.assertIn('"no_evidence"', manifest)
        self.assertIn('"invalid_manifest"', manifest)
        self.assertIn('"valid_manifest"', manifest)
        self.assertIn('"manifest_artifact_mismatch"', manifest)
        self.assertIn("manifestMatches(loaded.manifest)", manifest)

    def test_ghost_clear_preserves_valid_manifest_but_removes_invalid_identity(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidGhostPromotionEvidenceHandler(",
        )
        clear = extract_braced_block(handler, "override fun clearEvidence()")
        self.assertIn("receiptStore.clear()", clear)
        self.assertIn("manifestStore.clear()", clear)
        self.assertIn("if (manifestMatches(loaded.manifest)) true", clear)
        self.assertIn("receiptCleared && manifestCleared", clear)

        matches = extract_braced_block(handler, "private fun manifestMatches(")
        self.assertIn("artifactStore.loadGhost()", matches)
        self.assertIn("GhostRunIdentity.matches(", matches)
        self.assertIn("distanceM = manifest.distanceM", matches)
        self.assertIn("frameCount = manifest.frameCount", matches)
        self.assertIn("fingerprint = manifest.fingerprint", matches)
        self.assertIn("sha256Hex = manifest.sha256Hex", matches)
        self.assertNotIn("SaveManager.loadGhostRun", matches)
        self.assertNotIn("GhostRunFingerprint.calculate", matches)

    def test_manifest_corruption_has_distinct_recovery_status(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private class AndroidGhostPromotionEvidenceHandler(",
        )
        recover = extract_braced_block(handler, "override fun recoverSafely()")
        self.assertIn("GhostPromotionRecoveryDisposition.CORRUPT_RECEIPT", recover)
        self.assertIn("GhostPromotionRecoveryDisposition.CORRUPT_MANIFEST", recover)
        self.assertIn('"invalid_manifest_or_artifact"', recover)

    def test_namespace_switch_integration_covers_inspect_recover_and_abandon(self) -> None:
        for marker in (
            "manifest inspection keeps captured ghost namespace after compatibility switch",
            "safe run recovery mutates only captured primary namespace",
            "unwritten receipt recovery clears only captured primary sidecar",
            "assertEquals(COMPAT_PREFS, SaveManager.activePrefsNameForTests)",
        ):
            self.assertIn(marker, self.integration_test)

    def test_clear_verifies_clean_state_after_deletion(self) -> None:
        clear = extract_braced_block(self.source, "private fun clear(")
        self.assertIn("if (!handler.clearEvidence())", clear)
        self.assertIn("val after = handler.inspect()", clear)
        self.assertIn("after.state == RecoveryEvidenceState.CLEAN", clear)
        self.assertIn("RecoveryDiscardDisposition.IO_FAILURE", clear)


if __name__ == "__main__":
    unittest.main()
