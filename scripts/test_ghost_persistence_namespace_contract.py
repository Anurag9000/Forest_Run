#!/usr/bin/env python3
"""Source contract for namespace-stable ghost persistence transactions."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceManager.kt"
NAMESPACE = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceNamespace.kt"
PENDING_REGISTRY = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostNamespacePendingWriteRegistry.kt"
INTEGRATION = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems/GhostPersistenceNamespaceIntegrationTest.kt"
REGISTRY_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems/GhostNamespacePendingWriteRegistryTest.kt"
CODEC_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems/NamespaceBoundGhostPromotionArtifactStoreTest.kt"


class GhostPersistenceNamespaceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manager = MANAGER.read_text(encoding="utf-8")
        cls.namespace = NAMESPACE.read_text(encoding="utf-8")
        cls.pending_registry = PENDING_REGISTRY.read_text(encoding="utf-8")
        cls.integration = INTEGRATION.read_text(encoding="utf-8")
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

        worker_start = self.manager.index("val task = executor.submit")
        worker_end = self.manager.index("pendingWrites.track(namespace, task)", worker_start)
        worker = self.manager[worker_start:worker_end]
        self.assertIn("recoveryCoordinator(context, namespace)", worker)
        self.assertIn("clearPublicationIfCurrent(publication)", worker)
        self.assertNotIn("GhostPersistenceNamespace.capture()", worker)
        self.assertIn("pendingWrites.track(namespace, task)", self.manager)
        self.assertIn("latestSubmittedWrite = task", self.manager)

    def test_pending_activity_and_recovery_admission_are_namespace_scoped(self) -> None:
        self.assertIn(
            "private val pendingWrites = GhostNamespacePendingWriteRegistry()",
            self.manager,
        )
        self.assertIn("if (!pendingWrites.isActive(namespace))", self.manager)
        self.assertIn("if (pendingWrites.isActive(namespace))", self.manager)
        self.assertNotIn("private var pendingWrite: Future", self.manager)
        self.assertNotIn("val activeTask = pendingWrite", self.manager)
        self.assertIn("private var latestSubmittedWrite: Future<*>? = null", self.manager)

        recovery = self.manager.split("private fun recoverPendingPromotion(", 1)[1]
        recovery = recovery.split("/** Returns the latest", 1)[0]
        self.assertIn("pendingWrites.isActive(namespace)", recovery)
        self.assertNotIn("latestSubmittedWrite", recovery)

        await_block = self.manager.split("internal fun awaitPendingWrites", 1)[1]
        await_block = await_block.split("internal fun clearPromotionEvidenceForTests", 1)[0]
        self.assertIn("latestSubmittedWrite", await_block)
        self.assertNotIn("pendingWrites.isActive", await_block)

    def test_pending_registry_removes_only_completed_target_namespace_entries(self) -> None:
        self.assertIn("internal class GhostNamespacePendingWriteRegistry", self.pending_registry)
        self.assertIn(
            "ConcurrentHashMap<GhostPersistenceNamespace, Future<*>>()",
            self.pending_registry,
        )
        self.assertIn("latestByNamespace[namespace] = task", self.pending_registry)
        self.assertIn("val task = latestByNamespace[namespace] ?: return false", self.pending_registry)
        self.assertIn("if (!task.isDone) return true", self.pending_registry)
        self.assertIn("latestByNamespace.remove(namespace, task)", self.pending_registry)
        self.assertIn("latestByNamespace.clear()", self.pending_registry)

        self.assertIn("pending work blocks only its own namespace", self.registry_test)
        self.assertIn("completed and cancelled tasks stop blocking admission", self.registry_test)
        self.assertIn("latest same namespace task remains authoritative", self.registry_test)
        self.assertIn("clear removes every namespace activity marker", self.registry_test)
        self.assertGreaterEqual(self.registry_test.count("assertFalse(registry.isActive(compatibility))"), 2)

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
