#!/usr/bin/env python3
"""Source contract for namespace-stable ghost persistence transactions."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SYSTEMS = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems"
TESTS = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems"
MANAGER = SYSTEMS / "GhostPersistenceManager.kt"
NAMESPACE = SYSTEMS / "GhostPersistenceNamespace.kt"
SCHEDULER = SYSTEMS / "GhostNamespaceSerialScheduler.kt"
PENDING_REGISTRY = SYSTEMS / "GhostNamespacePendingWriteRegistry.kt"
INTEGRATION = TESTS / "GhostPersistenceNamespaceIntegrationTest.kt"
SCHEDULER_TEST = TESTS / "GhostNamespaceSerialSchedulerTest.kt"
REGISTRY_TEST = TESTS / "GhostNamespacePendingWriteRegistryTest.kt"
CODEC_TEST = TESTS / "NamespaceBoundGhostPromotionArtifactStoreTest.kt"


class GhostPersistenceNamespaceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manager = MANAGER.read_text(encoding="utf-8")
        cls.namespace = NAMESPACE.read_text(encoding="utf-8")
        cls.scheduler = SCHEDULER.read_text(encoding="utf-8")
        cls.pending_registry = PENDING_REGISTRY.read_text(encoding="utf-8")
        cls.integration = INTEGRATION.read_text(encoding="utf-8")
        cls.scheduler_test = SCHEDULER_TEST.read_text(encoding="utf-8")
        cls.registry_test = REGISTRY_TEST.read_text(encoding="utf-8")
        cls.codec_test = CODEC_TEST.read_text(encoding="utf-8")

    def test_namespace_capture_derives_one_coherent_pair_from_one_volatile_read(self) -> None:
        self.assertIn("data class GhostPersistenceNamespace", self.namespace)
        capture = self.namespace.split("fun capture(): GhostPersistenceNamespace", 1)[1]
        capture = capture.split("private fun expectedGhostFilename", 1)[0]
        self.assertEqual(1, capture.count("SaveManager.activePrefsNameForTests"))
        self.assertNotIn("SaveManager.activeGhostFilenameForTests", capture)
        self.assertIn("expectedGhostFilename(prefsName)", capture)
        self.assertIn("Unsupported save namespace", capture)
        self.assertIn("PRIMARY_GHOST_FILENAME", self.namespace)
        self.assertIn("COMPAT_PREFS_PREFIX", self.namespace)
        self.assertIn("COMPAT_GHOST_PREFIX", self.namespace)
        self.assertIn("version.any { !it.isDigit() }", self.namespace)
        self.assertIn("'/' !in value", self.namespace)
        self.assertIn("'\\\\' !in value", self.namespace)

    def test_bound_store_never_consults_mutable_active_namespace_after_construction(self) -> None:
        store = self.namespace.split("internal class NamespaceBoundGhostPromotionArtifactStore", 1)[1]
        self.assertIn("namespace.prefsName", store)
        self.assertIn("namespace.ghostFilename", store)
        self.assertNotIn("activePrefsNameForTests", store)
        self.assertNotIn("activeGhostFilenameForTests", store)
        self.assertNotIn("SaveManager.loadGhostRun", store)
        self.assertNotIn("SaveManager.saveGhostRun", store)
        self.assertIn("AtomicFile(File(appContext.filesDir, namespace.ghostFilename))", store)
        self.assertIn("SaveManager.GHOST_FILE_MAGIC", store)
        self.assertIn("SaveManager.GHOST_FILE_VERSION", store)
        self.assertIn("GhostRunValidator.isValid(frames)", store)
        self.assertIn("GhostStateCodec.decodeToOrdinal", store)
        self.assertIn("GhostStateCodec.encodeOrdinal", store)

    def test_manager_publication_and_workers_are_namespace_keyed(self) -> None:
        self.assertIn(
            "ConcurrentHashMap<GhostPersistenceNamespace, PublishedGhost>()",
            self.manager,
        )
        self.assertIn("val namespace: GhostPersistenceNamespace", self.manager)
        self.assertIn("latestPublications[namespace] = publication", self.manager)
        self.assertIn("latestPublications[namespace]?.distanceM", self.manager)
        self.assertIn("latestPublications[namespace]?.let", self.manager)
        self.assertIn("latestPublications.remove(publication.namespace, current)", self.manager)
        self.assertNotIn("private var latestPublication:", self.manager)

        worker_start = self.manager.index("val task = scheduler.submit(namespace)")
        worker_end = self.manager.index("pendingWrites.track(namespace, task)", worker_start)
        worker = self.manager[worker_start:worker_end]
        self.assertIn("recoveryCoordinator(context, namespace)", worker)
        self.assertIn("clearPublicationIfCurrent(publication)", worker)
        self.assertNotIn("GhostPersistenceNamespace.capture()", worker)
        self.assertIn("pendingWrites.track(namespace, task)", self.manager)
        self.assertNotIn("latestSubmittedWrite", self.manager)

    def test_scheduler_is_bounded_and_serial_only_within_each_namespace(self) -> None:
        self.assertIn("GhostNamespaceSerialScheduler(", self.manager)
        self.assertIn("Executors.newFixedThreadPool(MAX_CONCURRENT_NAMESPACE_WRITES)", self.manager)
        self.assertIn("private const val MAX_CONCURRENT_NAMESPACE_WRITES = 2", self.manager)
        self.assertNotIn("Executors.newSingleThreadExecutor", self.manager)
        self.assertIn("ConcurrentHashMap<GhostPersistenceNamespace, SerialExecutor>()", self.scheduler)
        self.assertIn("computeIfAbsent(namespace)", self.scheduler)
        self.assertIn("private val queue = ArrayDeque<Runnable>()", self.scheduler)
        self.assertIn("if (active == null)", self.scheduler)
        self.assertIn("scheduleNext()", self.scheduler)
        self.assertIn("finally", self.scheduler)

        self.assertIn("same namespace stays fifo and never overlaps", self.scheduler_test)
        self.assertIn("different namespaces may overlap", self.scheduler_test)
        self.assertIn("failure completes its future and does not strand later namespace work", self.scheduler_test)
        self.assertIn("assertEquals(1, peak.get())", self.scheduler_test)
        self.assertIn("assertEquals(2, peak.get())", self.scheduler_test)

    def test_pending_activity_recovery_and_waiting_are_namespace_scoped(self) -> None:
        self.assertIn(
            "private val pendingWrites = GhostNamespacePendingWriteRegistry()",
            self.manager,
        )
        self.assertIn("if (!pendingWrites.isActive(namespace))", self.manager)
        self.assertIn("if (pendingWrites.isActive(namespace))", self.manager)
        self.assertNotIn("private var pendingWrite: Future", self.manager)
        self.assertNotIn("val activeTask = pendingWrite", self.manager)

        recovery = self.manager.split("private fun recoverPendingPromotion(", 1)[1]
        recovery = recovery.split("/** Returns the latest", 1)[0]
        self.assertIn("pendingWrites.isActive(namespace)", recovery)
        self.assertNotIn("latestSubmittedWrite", recovery)

        await_block = self.manager.split("internal fun awaitPendingWrites", 1)[1]
        await_block = await_block.split("internal fun clearPromotionEvidenceForTests", 1)[0]
        self.assertIn("pendingWrites.awaitAll(timeoutMs)", await_block)
        self.assertNotIn("latestSubmittedWrite", await_block)

    def test_pending_registry_tracks_latest_per_namespace_and_awaits_all(self) -> None:
        self.assertIn("internal class GhostNamespacePendingWriteRegistry", self.pending_registry)
        self.assertIn(
            "ConcurrentHashMap<GhostPersistenceNamespace, Future<*>>()",
            self.pending_registry,
        )
        self.assertIn("latestByNamespace[namespace] = task", self.pending_registry)
        self.assertIn("val task = latestByNamespace[namespace] ?: return false", self.pending_registry)
        self.assertIn("if (!task.isDone) return true", self.pending_registry)
        self.assertIn("latestByNamespace.remove(namespace, task)", self.pending_registry)
        self.assertIn("fun awaitAll(timeoutMs: Long): Boolean", self.pending_registry)
        self.assertIn("val active = activeTasks()", self.pending_registry)
        self.assertIn("task.get(remainingNs, TimeUnit.NANOSECONDS)", self.pending_registry)
        self.assertIn("latestByNamespace.clear()", self.pending_registry)

        self.assertIn("pending work blocks only its own namespace", self.registry_test)
        self.assertIn("completed and cancelled tasks stop blocking admission", self.registry_test)
        self.assertIn("latest same namespace task remains authoritative", self.registry_test)
        self.assertIn("await all waits for every active namespace", self.registry_test)
        self.assertIn("await all rejects negative budgets and times out on active work", self.registry_test)
        self.assertIn("await all ignores completed namespaces", self.registry_test)
        self.assertIn("clear removes every namespace activity marker", self.registry_test)

    def test_receipt_manifest_ghost_and_distance_share_one_snapshot(self) -> None:
        coordinator = self.manager.split("private fun recoveryCoordinator", 1)[1]
        self.assertGreaterEqual(coordinator.count("namespace.ghostFilename"), 2)
        self.assertIn("artifactStore(context, namespace)", coordinator)
        self.assertIn(
            "NamespaceBoundGhostPromotionArtifactStore(context, namespace)",
            self.manager,
        )
        self.assertNotIn("AndroidGhostPromotionArtifactStore(context)", self.manager)
        self.assertNotIn("SaveManager.loadGhostRun", self.manager)
        self.assertNotIn("SaveManager.saveGhostRun", self.manager)
        self.assertNotIn("SaveManager.loadBestDistance", self.manager)

    def test_integration_covers_immediate_switch_and_both_durable_namespaces(self) -> None:
        self.assertIn(
            "queued manager writes and publications remain isolated by namespace",
            self.integration,
        )
        self.assertIn("SaveManager.usePrimaryPreferences()", self.integration)
        self.assertIn(
            "SaveManager.useCompatibilityPreferences(COMPAT_VERSION)",
            self.integration,
        )
        self.assertIn("GhostPersistenceManager.awaitPendingWrites()", self.integration)
        self.assertGreaterEqual(
            self.integration.count("GhostPersistenceManager.loadLatest(context)"),
            3,
        )
        self.assertIn(
            "bound artifact store remains on captured namespace after active switch",
            self.integration,
        )
        self.assertRegex(
            self.integration,
            re.compile(r"assertEquals\(primaryFrames, SaveManager\.loadGhostRun\(context\)\)"),
        )
        self.assertRegex(
            self.integration,
            re.compile(r"assertEquals\(compatFrames, SaveManager\.loadGhostRun\(context\)\)"),
        )

    def test_codec_tests_preserve_versioned_legacy_and_rejection_paths(self) -> None:
        self.assertIn(
            "versioned writer matches SaveManager codec header and round trips",
            self.codec_test,
        )
        self.assertIn("SaveManager.GHOST_FILE_MAGIC", self.codec_test)
        self.assertIn("SaveManager.GHOST_FILE_VERSION", self.codec_test)
        self.assertIn("legacy raw ordinal ghost remains readable", self.codec_test)
        self.assertIn("output.writeInt(frame.stateOrdinal)", self.codec_test)
        self.assertIn("unknown version and trailing bytes are rejected", self.codec_test)
        self.assertIn("invalid candidate never replaces durable ghost", self.codec_test)


if __name__ == "__main__":
    unittest.main()
