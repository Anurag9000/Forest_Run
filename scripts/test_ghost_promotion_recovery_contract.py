#!/usr/bin/env python3
"""Source contracts for recoverable best-ghost and best-distance promotion."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANAGER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceManager.kt"
)
RECOVERY = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPromotionRecovery.kt"
)
SAVE_MANAGER = (
    ROOT
    / "app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt"
)


def extract_braced_block_at(source: str, start: int) -> str:
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

    raise AssertionError(f"Unbalanced Kotlin block starting at {start}")


def extract_braced_block(source: str, signature: str) -> str:
    return extract_braced_block_at(source, source.index(signature))


def extract_braced_overload(source: str, signature: str) -> str:
    """Return the first overload with a block body, skipping expression bodies."""
    start = 0
    while True:
        candidate = source.find(signature, start)
        if candidate < 0:
            raise AssertionError(f"No block-bodied overload for {signature!r}")
        next_candidate = source.find(signature, candidate + len(signature))
        search_end = next_candidate if next_candidate >= 0 else len(source)
        brace = source.find("{", candidate, search_end)
        if brace >= 0:
            return extract_braced_block_at(source, candidate)
        start = candidate + len(signature)


class GhostPromotionRecoveryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manager = MANAGER.read_text(encoding="utf-8")
        cls.recovery = RECOVERY.read_text(encoding="utf-8")
        cls.save_manager = SAVE_MANAGER.read_text(encoding="utf-8")

    def test_single_worker_preserves_promotion_order(self) -> None:
        self.assertEqual(1, self.manager.count("Executors.newSingleThreadExecutor"))
        self.assertIn('Thread(runnable, "forest-run-ghost-io")', self.manager)
        self.assertIn("pendingWrite = executor.submit", self.manager)

    def test_immediate_publication_carries_distance_and_fingerprint(self) -> None:
        save = extract_braced_overload(self.manager, "fun saveBestRunAsync(")
        order = (
            "val snapshot = frames.toList()",
            "GhostRunFingerprint.calculate(snapshot)",
            "latestPublication = publication",
            "GhostIoTelemetry.recordWriteStarted(snapshot.size)",
            "pendingWrite = executor.submit",
        )
        positions = [save.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("distanceM = distanceM", save)

    def test_legacy_and_direct_callers_cannot_regress_pending_distance(self) -> None:
        compatibility_start = self.manager.index(
            "fun saveBestRunAsync(context: Context, frames: List<GhostFrame>)"
        )
        compatibility_end = self.manager.index("/**", compatibility_start)
        compatibility = self.manager[compatibility_start:compatibility_end]
        self.assertIn(
            "distanceM = bestDistanceFloor(context.applicationContext)",
            compatibility,
        )

        save = extract_braced_overload(self.manager, "fun saveBestRunAsync(")
        recovery_gate = save.index("if (!recovery.allowsNewPromotion) return false")
        stale_gate = save.index("if (distanceM < bestDistanceFloor(appContext)) return false")
        publication = save.index("val snapshot = frames.toList()")
        self.assertLess(recovery_gate, stale_gate)
        self.assertLess(stale_gate, publication)

    def test_pending_publication_is_part_of_best_distance_floor(self) -> None:
        floor = extract_braced_block(self.manager, "fun bestDistanceFloor(")
        self.assertIn("SaveManager.loadBestDistance", floor)
        self.assertIn("latestPublication?.distanceM", floor)
        self.assertIn("maxOf(diskDistance, publishedDistance)", floor)

    def test_worker_recovers_previous_receipt_before_new_persist(self) -> None:
        save = extract_braced_overload(self.manager, "fun saveBestRunAsync(")
        worker = save[save.index("pendingWrite = executor.submit") :]
        recover = worker.index("val recovery = coordinator.recover()")
        gate = worker.index("if (!recovery.allowsNewPromotion)")
        persist = worker.index("coordinator.persist(snapshot, distanceM)")
        self.assertLess(recover, gate)
        self.assertLess(gate, persist)

    def test_receipt_is_durable_before_ghost_and_distance(self) -> None:
        persist = extract_braced_block(self.recovery, "fun persist(")
        order = (
            "receiptStore.save(receipt)",
            "artifactStore.saveGhost(frames)",
            "artifactStore.loadBestDistanceM()",
            "artifactStore.saveBestDistanceM(targetBest)",
            "receiptStore.clear()",
        )
        positions = [persist.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

    def test_recovery_requires_matching_durable_ghost(self) -> None:
        recover = extract_braced_block(
            self.recovery,
            "private fun recover(receipt: GhostPromotionReceipt)",
        )
        order = (
            "artifactStore.loadGhost()",
            "durableGhost.size == receipt.frameCount",
            "GhostRunFingerprint.calculate(durableGhost) == receipt.fingerprint",
            "if (!ghostMatches)",
            "artifactStore.loadBestDistanceM()",
            "artifactStore.saveBestDistanceM(receipt.distanceM)",
            "receiptStore.clear()",
        )
        positions = [recover.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("ABANDONED_UNWRITTEN_GHOST", recover)

    def test_corrupt_or_io_failed_receipt_blocks_new_promotion(self) -> None:
        enum = extract_braced_block(
            self.recovery,
            "internal enum class GhostPromotionRecoveryDisposition",
        )
        self.assertIn("CORRUPT_RECEIPT", enum)
        self.assertIn("IO_FAILURE", enum)
        self.assertIn("allowsNewPromotion", enum)
        self.assertIn("CORRUPT_RECEIPT,\n            IO_FAILURE -> false", enum)

    def test_receipt_codec_is_versioned_fixed_size_and_atomic(self) -> None:
        store = extract_braced_block(
            self.recovery,
            "internal class AtomicFileGhostPromotionReceiptStore(",
        )
        self.assertIn("AtomicFile(baseFile)", store)
        self.assertIn("const val MAGIC = 0x46524750", store)
        self.assertIn("const val VERSION = 1", store)
        self.assertIn("const val RECORD_BYTES = 24L", store)
        self.assertIn("atomicFile.startWrite()", store)
        self.assertIn("atomicFile.finishWrite(stream)", store)
        self.assertIn("atomicFile.failWrite(it)", store)
        self.assertIn("receipt.frameCount in 1..GhostRecorder.MAX_FRAMES", store)

    def test_fingerprint_covers_all_persisted_frame_components(self) -> None:
        fingerprint = extract_braced_block(
            self.recovery,
            "internal object GhostRunFingerprint",
        )
        required = (
            "frames.size",
            "frame.t.toRawBits()",
            "frame.x.toRawBits()",
            "frame.y.toRawBits()",
            "frame.stateOrdinal",
            "frame.scaleX.toRawBits()",
            "frame.scaleY.toRawBits()",
        )
        for item in required:
            self.assertIn(item, fingerprint)

    def test_best_distance_key_matches_save_manager(self) -> None:
        self.assertIn('private const val KEY_BEST_DIST = "best_distance"', self.save_manager)
        self.assertIn('const val KEY_BEST_DISTANCE = "best_distance"', self.recovery)
        artifact = extract_braced_block(
            self.recovery,
            "internal class AndroidGhostPromotionArtifactStore",
        )
        self.assertIn("prefs.edit().putFloat(KEY_BEST_DISTANCE, safeDistance).commit()", artifact)
        self.assertNotIn(".apply()", artifact)

    def test_failed_pre_durable_worker_clears_only_matching_publication(self) -> None:
        clear = extract_braced_block(
            self.manager,
            "private fun clearPublicationIfCurrent(",
        )
        self.assertIn("current.distanceM == publication.distanceM", clear)
        self.assertIn("current.fingerprint == publication.fingerprint", clear)
        self.assertIn("latestPublication = null", clear)


if __name__ == "__main__":
    unittest.main()
