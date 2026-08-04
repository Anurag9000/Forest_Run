package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.AtomicFileGhostArtifactManifestStore
import com.anurag9000.forestrun.systems.AtomicFileGhostPromotionReceiptStore
import com.anurag9000.forestrun.systems.GhostArtifactManifest
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceNamespace
import com.anurag9000.forestrun.systems.GhostPromotionReceipt
import com.anurag9000.forestrun.systems.GhostPromotionReceiptLoadResult
import com.anurag9000.forestrun.systems.GhostRunIdentity
import com.anurag9000.forestrun.systems.NamespaceBoundGhostPromotionArtifactStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecoveryEvidenceMaintenanceNamespaceIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearNamespace(PRIMARY_PREFS, PRIMARY_GHOST)
        clearNamespace(COMPAT_PREFS, COMPAT_GHOST)
        SaveManager.usePrimaryPreferences()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `manifest inspection keeps captured ghost namespace after compatibility switch`() {
        val primaryNamespace = GhostPersistenceNamespace.capture()
        val primaryFrames = ghostFrames(offset = 0f)
        val primaryDistance = 480f
        val primaryIdentity = GhostRunIdentity.calculate(primaryFrames, primaryDistance)
        val primaryArtifactStore = NamespaceBoundGhostPromotionArtifactStore(
            context,
            primaryNamespace
        )
        assertTrue(primaryArtifactStore.saveGhost(primaryFrames))
        assertTrue(
            AtomicFileGhostArtifactManifestStore(
                context,
                primaryNamespace.ghostFilename
            ).save(
                GhostArtifactManifest(
                    distanceM = primaryDistance,
                    frameCount = primaryFrames.size,
                    fingerprint = primaryIdentity.fingerprint,
                    sha256Hex = primaryIdentity.sha256Hex
                )
            )
        )

        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        val compatibilityNamespace = GhostPersistenceNamespace.capture()
        assertTrue(
            NamespaceBoundGhostPromotionArtifactStore(
                context,
                compatibilityNamespace
            ).saveGhost(ghostFrames(offset = 700f))
        )

        val report = maintenance.inspect()

        assertEquals(RecoveryEvidenceState.CLEAN, report.ghostPromotion.state)
        assertEquals("valid_manifest", report.ghostPromotion.detail)
        assertEquals(COMPAT_PREFS, SaveManager.activePrefsNameForTests)
    }

    @Test
    fun `safe run recovery mutates only captured primary namespace`() {
        val primaryRecord = recoveryRecord(summary(distanceM = 620f), nowMs = 10_000L)
        val primaryJournal = SharedPreferencesRunOutcomeRecoveryStore(context, PRIMARY_PREFS)
        assertTrue(primaryJournal.save(primaryRecord))

        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        val compatibilityRecord = recoveryRecord(
            summary(distanceM = 910f, routeTier = PacifistRouteTier.MERCIFUL),
            nowMs = 20_000L
        )
        val compatibilityJournal = SharedPreferencesRunOutcomeRecoveryStore(
            context,
            COMPAT_PREFS
        )
        assertTrue(compatibilityJournal.save(compatibilityRecord))

        val report = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals("recovered", report.runOutcome.detail)
        assertEquals(RunOutcomeRecoveryLoadResult.Empty, primaryJournal.load())
        assertTrue(compatibilityJournal.load() is RunOutcomeRecoveryLoadResult.Pending)

        val primaryState = NamespaceBoundRunOutcomeMaintenanceStateStore(
            context,
            PRIMARY_PREFS
        )
        assertEquals(
            RunOutcomeRecoveryTransitions.persistedSummary(primaryRecord.summary),
            primaryState.loadLastRunSummary()
        )
        assertEquals(primaryRecord.nextMood, primaryState.loadForestMoodState())
        assertEquals(primaryRecord.nextReturn, primaryState.loadReturnMomentState())
        assertEquals(
            primaryRecord.nextRouteTierCount,
            primaryState.loadRouteTierCount(primaryRecord.summary.pacifistRouteTier)
        )

        val compatibilityState = NamespaceBoundRunOutcomeMaintenanceStateStore(
            context,
            COMPAT_PREFS
        )
        assertEquals(null, compatibilityState.loadLastRunSummary())
        assertEquals(ForestMoodState(), compatibilityState.loadForestMoodState())
        assertEquals(ReturnMomentState(), compatibilityState.loadReturnMomentState())
        assertEquals(
            0,
            compatibilityState.loadRouteTierCount(
                compatibilityRecord.summary.pacifistRouteTier
            )
        )
        assertEquals(COMPAT_PREFS, SaveManager.activePrefsNameForTests)
    }

    @Test
    fun `unwritten receipt recovery clears only captured primary sidecar`() {
        val primaryReceiptStore = AtomicFileGhostPromotionReceiptStore(
            context,
            PRIMARY_GHOST
        )
        assertTrue(primaryReceiptStore.save(unwrittenReceipt(distanceM = 300f)))

        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        val compatibilityReceiptStore = AtomicFileGhostPromotionReceiptStore(
            context,
            COMPAT_GHOST
        )
        assertTrue(compatibilityReceiptStore.save(unwrittenReceipt(distanceM = 900f)))

        val report = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.ghostPromotion.state)
        assertEquals("unwritten_candidate_abandoned", report.ghostPromotion.detail)
        assertEquals(GhostPromotionReceiptLoadResult.Empty, primaryReceiptStore.load())
        assertTrue(
            compatibilityReceiptStore.load() is GhostPromotionReceiptLoadResult.Pending
        )
        assertEquals(COMPAT_PREFS, SaveManager.activePrefsNameForTests)
    }

    private fun recoveryRecord(
        summary: RunSummary,
        nowMs: Long
    ): RunOutcomeRecoveryRecord {
        val previousMood = ForestMoodState()
        val previousReturn = ReturnMomentState()
        val previousRouteCount = 0
        return RunOutcomeRecoveryRecord(
            phase = RunOutcomeRecoveryPhase.PREPARED,
            summary = summary,
            previousMood = previousMood,
            nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary),
            previousReturn = previousReturn,
            nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
                previous = previousReturn,
                summary = summary,
                nowMs = nowMs
            ),
            previousRouteTierCount = previousRouteCount,
            nextRouteTierCount = RunOutcomeRecoveryTransitions.nextRouteTierCount(
                previous = previousRouteCount,
                tier = summary.pacifistRouteTier
            )
        )
    }

    private fun summary(
        distanceM: Float,
        routeTier: PacifistRouteTier = PacifistRouteTier.KIND
    ): RunSummary = RunSummary(
        score = distanceM.toInt() * 2,
        distanceM = distanceM,
        isNewHighScore = false,
        highScore = 2_000,
        mercyHearts = 3,
        mercyMisses = 1,
        kindnessChain = 4,
        cleanPasses = 8,
        sparedCount = 2,
        hitsTaken = 0,
        seedsCollected = 10,
        bloomConversions = 2,
        lastKiller = EntityType.CAT,
        restQuote = "The namespace stayed still.",
        forestMood = ForestMood.GENTLE,
        pacifistRouteTier = routeTier
    )

    private fun unwrittenReceipt(distanceM: Float): GhostPromotionReceipt =
        GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = 1,
            fingerprint = 1L,
            sha256Hex = "0".repeat(64)
        )

    private fun ghostFrames(offset: Float): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f + offset, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f + offset, 196f, 1, 0.98f, 1.02f)
    )

    private fun clearNamespace(prefsName: String, ghostFilename: String) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SharedPreferencesRunOutcomeRecoveryStore(context, prefsName).clear()
        AtomicFileGhostPromotionReceiptStore(context, ghostFilename).clear()
        AtomicFileGhostArtifactManifestStore(context, ghostFilename).clear()
        listOf(
            ghostFilename,
            "$ghostFilename.bak",
            "$ghostFilename.new"
        ).forEach { name -> context.filesDir.resolve(name).delete() }
    }

    private companion object {
        const val COMPAT_VERSION = 91
        const val PRIMARY_PREFS = "forest_run_prefs"
        const val PRIMARY_GHOST = "ghost_run.bin"
        const val COMPAT_PREFS = "forest_run_prefs_compat_v91"
        const val COMPAT_GHOST = "ghost_run_compat_v91.bin"
    }
}
