#!/usr/bin/env python3
"""Source contract for namespace-stable ghost persistence transactions."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceManager.kt"
NAMESPACE = ROOT / "app/src/main/java/com/anurag9000/forestrun/systems/GhostPersistenceNamespace.kt"
INTEGRATION = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems/GhostPersistenceNamespaceIntegrationTest.kt"
CODEC_TEST = ROOT / "app/src/test/java/com/anurag9000/forestrun/systems/NamespaceBoundGhostPromotionArtifactStoreTest.kt"


class GhostPersistenceNamespaceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manager = MANAGER.read_text(encoding="utf-8")
        cls.namespace = NAMESPACE.read_text(encoding="utf-8")
        cls.integration = INTEGRATION.read_text(encoding="utf-8")
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

        worker = self.manager.split("pendingWrite = executor.submit", 1)[1]
        self.assertIn("recoveryCoordinator(context, namespace)", worker)
        self.assertIn("clearPublicationIfCurrent(publication)", worker)
        self.assertNotIn("GhostPersistenceNamespace.capture()", worker)

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
