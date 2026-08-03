#!/usr/bin/env python3
"""Source contracts for recoverable best-ghost and best-distance promotion."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SYSTEMS = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems"
MANAGER = SYSTEMS / "GhostPersistenceManager.kt"
RECOVERY = SYSTEMS / "GhostPromotionRecovery.kt"
MANIFEST = SYSTEMS / "GhostArtifactManifest.kt"
IDENTITY = SYSTEMS / "GhostRunIdentity.kt"
SAVE_MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt"


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
        cls.manifest = MANIFEST.read_text(encoding="utf-8")
        cls.identity = IDENTITY.read_text(encoding="utf-8")
        cls.save_manager = SAVE_MANAGER.read_text(encoding="utf-8")

    def test_single_worker_preserves_promotion_order(self) -> None:
        self.assertEqual(1, self.manager.count("Executors.newSingleThreadExecutor"))
        self.assertIn('Thread(runnable, "forest-run-ghost-io")', self.manager)
        self.assertIn("pendingWrite = executor.submit", self.manager)

    def test_immediate_publication_carries_strong_identity(self) -> None:
        save = extract_braced_overload(self.manager, "fun saveBestRunAsync(")
        order = (
            "val snapshot = frames.toList()",
            "val identity = GhostRunIdentity.calculate(snapshot)",
            "fingerprint = identity.fingerprint",
            "sha256Hex = identity.sha256Hex",
            "latestPublication = publication",
            "GhostIoTelemetry.recordWriteStarted(snapshot.size)",
            "pendingWrite = executor.submit",
        )
        positions = [save.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

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

    def test_worker_recovers_previous_evidence_before_new_persist(self) -> None:
        save = extract_braced_overload(self.manager, "fun saveBestRunAsync(")
        worker = save[save.index("pendingWrite = executor.submit") :]
        recover = worker.index("val recovery = coordinator.recover()")
        gate = worker.index("if (!recovery.allowsNewPromotion)")
        persist = worker.index("coordinator.persist(snapshot, distanceM)")
        self.assertLess(recover, gate)
        self.assertLess(gate, persist)

    def test_receipt_ghost_manifest_distance_and_clear_order(self) -> None:
        persist = extract_braced_block(self.recovery, "fun persist(")
        order = (
            "val identity = GhostRunIdentity.calculate(frames)",
            "receiptStore.save(receipt)",
            "artifactStore.saveGhost(frames)",
            "manifestStore.save(receipt.toManifest(identity))",
            "artifactStore.loadBestDistanceM()",
            "artifactStore.saveBestDistanceM(targetBest)",
            "receiptStore.clear()",
        )
        positions = [persist.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)

        result = extract_braced_block(
            self.recovery,
            "internal data class GhostPromotionPersistenceResult(",
        )
        self.assertIn("manifestDurable", result)
        self.assertIn(
            "get() = receiptDurable &&\n"
            "            ghostDurable &&\n"
            "            manifestDurable &&\n"
            "            distanceDurable &&\n"
            "            receiptCleared",
            result,
        )

    def test_receipt_recovery_requires_strong_match_and_manifest_before_distance(self) -> None:
        recover = extract_braced_block(self.recovery, "private fun recoverReceipt(")
        order = (
            "artifactStore.loadGhost()",
            "matchingIdentity(",
            "val expectedManifest = receipt.toManifest(durableIdentity)",
            "ensureManifest(expectedManifest)",
            "repairDistanceIfNeeded(receipt.distanceM)",
            "receiptStore.clear()",
        )
        positions = [recover.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("sha256Hex = receipt.sha256Hex", recover)

        mismatch = recover[recover.index("if (durableIdentity == null)") :]
        self.assertIn("receiptStore.clear()", mismatch)
        self.assertIn("recoverManifest(durableGhost)", mismatch)

    def test_legacy_replay_upgrades_manifest_before_distance(self) -> None:
        manifest_recovery = extract_braced_block(
            self.recovery,
            "private fun recoverManifest(",
        )
        self.assertIn("ensureStrongManifest", manifest_recovery)
        ensure = extract_braced_block(self.recovery, "private fun ensureStrongManifest(")
        self.assertIn("if (manifest.sha256Hex != null) return true", ensure)
        self.assertIn("sha256Hex = identity.sha256Hex", ensure)
        self.assertIn("manifestStore.save", ensure)

    def test_manifest_repairs_distance_without_receipt(self) -> None:
        recover = extract_braced_block(self.recovery, "fun recover()")
        self.assertIn(
            "GhostPromotionReceiptLoadResult.Empty -> recoverManifest()",
            recover,
        )
        manifest_recovery = extract_braced_block(
            self.recovery,
            "private fun recoverManifest(",
        )
        order = (
            "manifestStore.load()",
            "artifactStore.loadBestDistanceM()",
            "currentBest >= manifest.distanceM ->",
            "knownGhost ?: artifactStore.loadGhost()",
            "matchingIdentity(",
            "ensureStrongManifest(manifest, identity)",
            "artifactStore.saveBestDistanceM(manifest.distanceM)",
        )
        positions = [manifest_recovery.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("CORRUPT_MANIFEST", manifest_recovery)

    def test_applied_manifest_fast_path_precedes_ghost_loading(self) -> None:
        recover = extract_braced_block(
            self.recovery,
            "private fun recoverManifest(",
        )
        best = recover.index("artifactStore.loadBestDistanceM()")
        gate = recover.index("currentBest >= manifest.distanceM ->")
        ghost = recover.index("knownGhost ?: artifactStore.loadGhost()")
        self.assertLess(best, gate)
        self.assertLess(gate, ghost)
        fast_path = recover[gate:ghost]
        self.assertIn("ALREADY_APPLIED", fast_path)
        self.assertNotIn("GhostRunIdentity.calculate", fast_path)

    def test_known_ghost_validation_precedes_applied_fast_path(self) -> None:
        recover = extract_braced_block(
            self.recovery,
            "private fun recoverManifest(",
        )
        known_gate = recover.index("knownGhost != null && knownIdentity == null")
        fast_gate = recover.index("currentBest >= manifest.distanceM ->")
        self.assertLess(known_gate, fast_gate)
        prefix = recover[:fast_gate]
        self.assertIn("sha256Hex = manifest.sha256Hex", prefix)
        self.assertIn("CORRUPT_MANIFEST", prefix)

    def test_sha256_identity_is_canonical_and_covers_persisted_fields(self) -> None:
        identity = extract_braced_block(
            self.identity,
            "internal object GhostRunIdentity",
        )
        self.assertIn('MessageDigest.getInstance("SHA-256")', identity)
        self.assertIn("const val SHA256_BYTE_COUNT = 32", identity)
        self.assertIn("const val SHA256_HEX_LENGTH = SHA256_BYTE_COUNT * 2", identity)
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
            self.assertIn(item, identity)
        self.assertIn("update((value ushr 24).toByte())", identity)
        self.assertIn("character in '0'..'9' || character in 'a'..'f'", identity)

    def test_matching_prefers_sha256_and_falls_back_only_when_absent(self) -> None:
        matching = extract_braced_block(
            self.recovery,
            "private fun matchingIdentity(",
        )
        self.assertIn("if (sha256Hex == null)", matching)
        self.assertIn("identity.fingerprint == fingerprint", matching)
        self.assertIn("identity.sha256Hex == sha256Hex", matching)
        self.assertIn("GhostRunIdentity.isCanonicalSha256", matching)

    def test_corrupt_receipt_manifest_or_io_blocks_new_promotion(self) -> None:
        enum = extract_braced_block(
            self.recovery,
            "internal enum class GhostPromotionRecoveryDisposition",
        )
        for item in ("CORRUPT_RECEIPT", "CORRUPT_MANIFEST", "IO_FAILURE"):
            self.assertIn(item, enum)
        self.assertIn(
            "CORRUPT_RECEIPT,\n            CORRUPT_MANIFEST,\n            IO_FAILURE -> false",
            enum,
        )

    def test_receipt_codec_reads_v1_and_writes_v2_sha256_records(self) -> None:
        store = extract_braced_block(
            self.recovery,
            "internal class AtomicFileGhostPromotionReceiptStore(",
        )
        self.assertIn("AtomicFile(baseFile)", store)
        self.assertIn("const val MAGIC = 0x46524750", store)
        self.assertIn("const val LEGACY_VERSION = 1", store)
        self.assertIn("const val VERSION = 2", store)
        self.assertIn("const val LEGACY_RECORD_BYTES = 24L", store)
        self.assertIn("const val RECORD_BYTES = 56L", store)
        self.assertIn("sha256Hex = null", store)
        self.assertIn("ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT)", store)
        self.assertIn("output.write(digest)", store)
        self.assertIn("isValidForSave", store)
        self.assertIn("receipt.sha256Hex", store)

    def test_manifest_codec_reads_v1_and_writes_v2_sha256_records(self) -> None:
        store = extract_braced_block(
            self.manifest,
            "internal class AtomicFileGhostArtifactManifestStore(",
        )
        self.assertIn('"$ghostFilename.manifest"', store)
        self.assertIn("const val MAGIC = 0x4652474D", store)
        self.assertIn("const val LEGACY_VERSION = 1", store)
        self.assertIn("const val VERSION = 2", store)
        self.assertIn("const val LEGACY_RECORD_BYTES = 24L", store)
        self.assertIn("const val RECORD_BYTES = 56L", store)
        self.assertIn("sha256Hex = null", store)
        self.assertIn("ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT)", store)
        self.assertIn("output.write(digest)", store)
        self.assertIn("manifest.sha256Hex", store)

    def test_manager_constructs_both_stores_and_uses_digest_for_publication_cleanup(self) -> None:
        coordinator = extract_braced_block(
            self.manager,
            "private fun recoveryCoordinator(",
        )
        self.assertIn("AtomicFileGhostPromotionReceiptStore(", coordinator)
        self.assertIn("AtomicFileGhostArtifactManifestStore(", coordinator)

        clear = extract_braced_block(
            self.manager,
            "private fun clearPublicationIfCurrent(",
        )
        self.assertIn("current.distanceM == publication.distanceM", clear)
        self.assertIn("current.fingerprint == publication.fingerprint", clear)
        self.assertIn("current.sha256Hex == publication.sha256Hex", clear)
        self.assertIn("latestPublication = null", clear)

    def test_best_distance_key_matches_save_manager(self) -> None:
        self.assertIn('private const val KEY_BEST_DIST = "best_distance"', self.save_manager)
        self.assertIn('const val KEY_BEST_DISTANCE = "best_distance"', self.recovery)
        artifact = extract_braced_block(
            self.recovery,
            "internal class AndroidGhostPromotionArtifactStore",
        )
        self.assertIn("prefs.edit().putFloat(KEY_BEST_DISTANCE, safeDistance).commit()", artifact)
        self.assertNotIn(".apply()", artifact)


if __name__ == "__main__":
    unittest.main()
