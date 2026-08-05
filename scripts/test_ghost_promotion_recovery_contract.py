#!/usr/bin/env python3
"""Source contracts for recoverable best-ghost and best-distance promotion."""

from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SYSTEMS = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems"
MANAGER = SYSTEMS / "GhostPersistenceManager.kt"
NAMESPACE = SYSTEMS / "GhostPersistenceNamespace.kt"
SCHEDULER = SYSTEMS / "GhostNamespaceSerialScheduler.kt"
PENDING_REGISTRY = SYSTEMS / "GhostNamespacePendingWriteRegistry.kt"
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


class GhostPromotionRecoveryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manager = MANAGER.read_text(encoding="utf-8")
        cls.namespace = NAMESPACE.read_text(encoding="utf-8")
        cls.scheduler = SCHEDULER.read_text(encoding="utf-8")
        cls.pending_registry = PENDING_REGISTRY.read_text(encoding="utf-8")
        cls.recovery = RECOVERY.read_text(encoding="utf-8")
        cls.manifest = MANIFEST.read_text(encoding="utf-8")
        cls.identity = IDENTITY.read_text(encoding="utf-8")
        cls.save_manager = SAVE_MANAGER.read_text(encoding="utf-8")

    def test_namespace_serial_scheduler_preserves_local_order_and_bounded_parallelism(self) -> None:
        self.assertIn("GhostNamespaceSerialScheduler(", self.manager)
        self.assertIn("Executors.newFixedThreadPool(MAX_CONCURRENT_NAMESPACE_WRITES)", self.manager)
        self.assertIn("private const val MAX_CONCURRENT_NAMESPACE_WRITES = 2", self.manager)
        self.assertIn("val task = scheduler.submit(namespace)", self.manager)
        self.assertIn("pendingWrites.track(namespace, task)", self.manager)
        self.assertIn("GhostNamespacePendingWriteRegistry()", self.manager)
        self.assertNotIn("Executors.newSingleThreadExecutor", self.manager)
        self.assertNotIn("latestSubmittedWrite", self.manager)
        self.assertIn("ConcurrentHashMap<GhostPersistenceNamespace, SerialExecutor>()", self.scheduler)
        self.assertIn("private val queue = ArrayDeque<Runnable>()", self.scheduler)
        self.assertIn("scheduleNext()", self.scheduler)
        self.assertIn(
            "ConcurrentHashMap<GhostPersistenceNamespace, PublishedGhost>()",
            self.manager,
        )
        self.assertNotIn("private var latestPublication:", self.manager)
        self.assertNotIn("private var pendingWrite: Future", self.manager)

    def test_immediate_publication_carries_distance_bound_strong_identity(self) -> None:
        save = extract_braced_block(self.manager, "private fun saveBestRunAsync(")
        order = (
            "val snapshot = frames.toList()",
            "val identity = GhostRunIdentity.calculate(snapshot, distanceM)",
            "fingerprint = identity.fingerprint",
            "sha256Hex = identity.sha256Hex",
            "latestPublications[namespace] = publication",
            "GhostIoTelemetry.recordWriteStarted(snapshot.size)",
            "val task = scheduler.submit(namespace)",
            "pendingWrites.track(namespace, task)",
        )
        positions = [save.index(item) for item in order]
        self.assertEqual(sorted(positions), positions)
        self.assertIn("namespace = namespace", save)

    def test_legacy_and_direct_callers_use_one_captured_namespace_for_floor_and_write(self) -> None:
        compatibility = extract_braced_block(
            self.manager,
            "fun saveBestRunAsync(context: Context, frames: List<GhostFrame>)",
        )
        self.assertIn("val namespace = GhostPersistenceNamespace.capture()", compatibility)
        self.assertIn("distanceM = bestDistanceFloor(appContext, namespace)", compatibility)
        self.assertIn("namespace = namespace", compatibility)

        save = extract_braced_block(self.manager, "private fun saveBestRunAsync(")
        recovery_gate = save.index("if (!recovery.allowsNewPromotion) return false")
        stale_gate = save.index("if (distanceM < bestDistanceFloor(context, namespace)) return false")
        publication = save.index("val snapshot = frames.toList()")
        self.assertLess(recovery_gate, stale_gate)
        self.assertLess(stale_gate, publication)
        self.assertIn("if (!pendingWrites.isActive(namespace))", save)

    def test_explicit_recovery_blocks_only_same_namespace_activity(self) -> None:
        recover = extract_braced_block(self.manager, "private fun recoverPendingPromotion(")
        self.assertIn("pendingWrites.isActive(namespace)", recover)
        self.assertIn("GhostPromotionRecoveryDisposition.IO_FAILURE", recover)
        self.assertNotIn("latestSubmittedWrite", recover)
        self.assertNotIn("val activeTask = pendingWrite", recover)

        self.assertIn(
            "ConcurrentHashMap<GhostPersistenceNamespace, Future<*>>()",
            self.pending_registry,
        )
        self.assertIn("latestByNamespace[namespace]", self.pending_registry)
        self.assertIn("latestByNamespace.remove(namespace, task)", self.pending_registry)
        self.assertIn("fun awaitAll(timeoutMs: Long): Boolean", self.pending_registry)
        self.assertIn("task.get(remainingNs, TimeUnit.NANOSECONDS)", self.pending_registry)
        await_block = self.manager.split("internal fun awaitPendingWrites", 1)[1]
        await_block = await_block.split("internal fun clearPromotionEvidenceForTests", 1)[0]
        self.assertIn("pendingWrites.awaitAll(timeoutMs)", await_block)

    def test_pending_publication_is_part_of_namespace_specific_best_distance_floor(self) -> None:
        floor = extract_braced_block(self.manager, "private fun bestDistanceFloor(")
        self.assertIn("artifactStore(context, namespace).loadBestDistanceM()", floor)
        self.assertIn("latestPublications[namespace]?.distanceM", floor)
        self.assertIn("maxOf(diskDistance, publishedDistance)", floor)
        self.assertNotIn("SaveManager.loadBestDistance", floor)

    def test_worker_recovers_previous_evidence_before_new_persist_without_recapturing(self) -> None:
        save = extract_braced_block(self.manager, "private fun saveBestRunAsync(")
        worker = save[save.index("val task = scheduler.submit(namespace)") :]
        recover = worker.index("val recovery = coordinator.recover()")
        gate = worker.index("if (!recovery.allowsNewPromotion)")
        persist = worker.index("coordinator.persist(snapshot, distanceM)")
        self.assertLess(recover, gate)
        self.assertLess(gate, persist)
        self.assertIn("recoveryCoordinator(context, namespace)", worker)
        self.assertNotIn("GhostPersistenceNamespace.capture()", worker)

    def test_receipt_ghost_manifest_distance_and_clear_order(self) -> None:
        persist = extract_braced_block(self.recovery, "fun persist(")
        order = (
            "val identity = GhostRunIdentity.calculate(frames, distanceM)",
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
        for field in (
            "receiptDurable",
            "ghostDurable",
            "manifestDurable",
            "distanceDurable",
            "receiptCleared",
        ):
            self.assertIn(field, result)

    def test_receipt_recovery_requires_distance_bound_match_before_manifest_and_distance(self) -> None:
        recover = extract_braced_block(self.recovery, "private fun recoverReceipt(")
        order = (
            "artifactStore.loadGhost()",
            "matchingIdentity(",
            "distanceM = receipt.distanceM",
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

    def test_manifest_recovery_upgrades_legacy_identity_and_retains_healthy_fast_path(self) -> None:
        recover = extract_braced_block(self.recovery, "private fun recoverManifest(")
        self.assertIn("ensureStrongManifest", recover)
        best = recover.index("artifactStore.loadBestDistanceM()")
        gate = recover.index("currentBest >= manifest.distanceM ->")
        ghost = recover.index("knownGhost ?: artifactStore.loadGhost()")
        self.assertLess(best, gate)
        self.assertLess(gate, ghost)
        fast_path = recover[gate:ghost]
        self.assertIn("ALREADY_APPLIED", fast_path)
        self.assertNotIn("GhostRunIdentity.calculate", fast_path)

        repair_path = recover[
            recover.index("val durableGhost = knownGhost ?: artifactStore.loadGhost()") :
        ]
        repair_order = (
            "val durableGhost = knownGhost ?: artifactStore.loadGhost()",
            "matchingIdentity(",
            "distanceM = manifest.distanceM",
            "ensureStrongManifest(manifest, identity)",
            "artifactStore.saveBestDistanceM(manifest.distanceM)",
        )
        repair_positions = [repair_path.index(item) for item in repair_order]
        self.assertEqual(sorted(repair_positions), repair_positions)
        self.assertIn("CORRUPT_MANIFEST", recover)

        ensure = extract_braced_block(self.recovery, "private fun ensureStrongManifest(")
        self.assertIn("if (manifest.sha256Hex != null) return true", ensure)
        self.assertIn("sha256Hex = identity.sha256Hex", ensure)
        self.assertIn("manifestStore.save", ensure)

    def test_known_ghost_validation_precedes_applied_fast_path(self) -> None:
        recover = extract_braced_block(self.recovery, "private fun recoverManifest(")
        known_gate = recover.index("knownGhost != null && knownIdentity == null")
        fast_gate = recover.index("currentBest >= manifest.distanceM ->")
        self.assertLess(known_gate, fast_gate)
        prefix = recover[:fast_gate]
        self.assertIn("distanceM = manifest.distanceM", prefix)
        self.assertIn("sha256Hex = manifest.sha256Hex", prefix)
        self.assertIn("CORRUPT_MANIFEST", prefix)

    def test_sha256_identity_binds_distance_and_every_persisted_frame_field(self) -> None:
        identity = extract_braced_block(self.identity, "internal object GhostRunIdentity")
        self.assertIn('MessageDigest.getInstance("SHA-256")', identity)
        self.assertIn("const val SHA256_BYTE_COUNT = 32", identity)
        required = (
            "distanceM.toRawBits()",
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

    def test_matching_prefers_sha256_and_uses_legacy_fingerprint_only_when_absent(self) -> None:
        matching = extract_braced_block(self.recovery, "private fun matchingIdentity(")
        self.assertIn("distanceM: Float", matching)
        self.assertIn("if (sha256Hex == null)", matching)
        self.assertIn("GhostRunFingerprint.calculate(frames)", matching)
        self.assertIn("GhostRunIdentity.calculate(frames, distanceM)", matching)
        self.assertIn("identity.sha256Hex == sha256Hex", matching)
        self.assertIn("GhostRunIdentity.isCanonicalSha256", matching)

    def test_corrupt_receipt_manifest_or_io_blocks_new_promotion(self) -> None:
        enum = extract_braced_block(
            self.recovery,
            "internal enum class GhostPromotionRecoveryDisposition",
        )
        for item in ("CORRUPT_RECEIPT", "CORRUPT_MANIFEST", "IO_FAILURE"):
            self.assertIn(item, enum)
        self.assertIn("CORRUPT_RECEIPT,", enum)
        self.assertIn("CORRUPT_MANIFEST,", enum)
        self.assertIn("IO_FAILURE -> false", enum)

    def test_receipt_and_manifest_codecs_read_v1_and_write_v2_sha256_records(self) -> None:
        receipt = extract_braced_block(
            self.recovery,
            "internal class AtomicFileGhostPromotionReceiptStore(",
        )
        manifest = extract_braced_block(
            self.manifest,
            "internal class AtomicFileGhostArtifactManifestStore(",
        )
        for store, magic in ((receipt, "0x46524750"), (manifest, "0x4652474D")):
            self.assertIn(magic, store)
            self.assertIn("const val LEGACY_VERSION = 1", store)
            self.assertIn("const val VERSION = 2", store)
            self.assertIn("const val LEGACY_RECORD_BYTES = 24L", store)
            self.assertIn("const val RECORD_BYTES = 56L", store)
            self.assertIn("sha256Hex = null", store)
            self.assertIn("ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT)", store)
            self.assertIn("output.write(digest)", store)

    def test_manager_binds_receipt_artifact_manifest_and_cleanup_to_namespace(self) -> None:
        coordinator = extract_braced_block(self.manager, "private fun recoveryCoordinator(")
        self.assertIn("AtomicFileGhostPromotionReceiptStore(", coordinator)
        self.assertIn("AtomicFileGhostArtifactManifestStore(", coordinator)
        self.assertGreaterEqual(coordinator.count("namespace.ghostFilename"), 2)
        self.assertIn("artifactStore(context, namespace)", coordinator)

        artifact = extract_braced_block(self.manager, "private fun artifactStore(")
        self.assertIn("NamespaceBoundGhostPromotionArtifactStore(context, namespace)", artifact)

        clear = extract_braced_block(self.manager, "private fun clearPublicationIfCurrent(")
        self.assertIn("latestPublications[publication.namespace]", clear)
        self.assertIn("current.distanceM == publication.distanceM", clear)
        self.assertIn("current.fingerprint == publication.fingerprint", clear)
        self.assertIn("current.sha256Hex == publication.sha256Hex", clear)
        self.assertIn("latestPublications.remove(publication.namespace, current)", clear)

    def test_disk_fallback_identity_uses_distance_from_same_bound_store(self) -> None:
        load = extract_braced_block(self.manager, "fun loadLatest(")
        store = load.index("val store = artifactStore(appContext, namespace)")
        ghost = load.index("val loaded = store.loadGhost()")
        distance = load.index("val loadedDistance = store.loadBestDistanceM()")
        identity = load.index("GhostRunIdentity.calculate(loaded, loadedDistance)")
        publication = load.index("val publication = PublishedGhost(")
        self.assertEqual(sorted((store, ghost, distance, identity, publication)), [
            store,
            ghost,
            distance,
            identity,
            publication,
        ])
        self.assertIn("namespace = namespace", load)

    def test_best_distance_key_and_bound_codec_match_save_manager(self) -> None:
        self.assertIn('private const val KEY_BEST_DIST = "best_distance"', self.save_manager)
        self.assertIn('const val KEY_BEST_DISTANCE = "best_distance"', self.recovery)
        self.assertIn('const val KEY_BEST_DISTANCE = "best_distance"', self.namespace)
        self.assertIn("SaveManager.GHOST_FILE_MAGIC", self.namespace)
        self.assertIn("SaveManager.GHOST_FILE_VERSION", self.namespace)
        self.assertIn("GhostStateCodec.decodeToOrdinal", self.namespace)
        self.assertIn("GhostStateCodec.encodeOrdinal", self.namespace)
        self.assertIn("GhostRunValidator.isValid(frames)", self.namespace)

        legacy_artifact = extract_braced_block(
            self.recovery,
            "internal class AndroidGhostPromotionArtifactStore",
        )
        self.assertIn(
            "prefs.edit().putFloat(KEY_BEST_DISTANCE, safeDistance).commit()",
            legacy_artifact,
        )
        self.assertNotIn(".apply()", legacy_artifact)


if __name__ == "__main__":
    unittest.main()
