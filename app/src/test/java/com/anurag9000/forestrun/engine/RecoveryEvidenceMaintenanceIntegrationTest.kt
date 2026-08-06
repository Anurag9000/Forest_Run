package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.AtomicFileGhostArtifactManifestStore
import com.anurag9000.forestrun.systems.AtomicFileGhostPromotionReceiptStore
import com.anurag9000.forestrun.systems.GhostArtifactManifest
import com.anurag9000.forestrun.systems.GhostArtifactManifestLoadResult
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPromotionReceipt
import com.anurag9000.forestrun.systems.GhostPromotionReceiptLoadResult
import com.anurag9000.forestrun.systems.GhostRunIdentity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecoveryEvidenceMaintenanceIntegrationTest {

    private lateinit var context: Context
    private lateinit var runStore: SharedPreferencesRunOutcomeRecoveryStore
    private lateinit var ghostStore: AtomicFileGhostPromotionReceiptStore
    private lateinit var manifestStore: AtomicFileGhostArtifactManifestStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        primaryPrefs().edit().clear().commit()
        recoveryPrefs().edit().clear().commit()
        deleteGhostFiles()
        runStore = SharedPreferencesRunOutcomeRecoveryStore(
            context,
            SaveManager.activePrefsNameForTests
        )
        ghostStore = AtomicFileGhostPromotionReceiptStore(
            context,
            SaveManager.activeGhostFilenameForTests
        )
        manifestStore = AtomicFileGhostArtifactManifestStore(
            context,
            SaveManager.activeGhostFilenameForTests
        )
        runStore.clear()
        ghostStore.clear()
        manifestStore.clear()
    }

    @After
    fun tearDown() {
        runStore.clear()
        ghostStore.clear()
        manifestStore.clear()
        primaryPrefs().edit().clear().commit()
        recoveryPrefs().edit().clear().commit()
        deleteGhostFiles()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `clean installation reports no recovery evidence`() {
        val report = AndroidRecoveryEvidenceMaintenance(context).inspect()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals(RecoveryEvidenceState.CLEAN, report.ghostPromotion.state)
        assertEquals(
            "run_outcome=CLEAN(no_journal); ghost_promotion=CLEAN(no_evidence)",
            report.supportSummary()
        )
    }

    @Test
    fun `valid run outcome journal recovers all non ghost state`() {
        val summary = summary()
        val record = recoveryRecord(summary)
        assertTrue(runStore.save(record))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)
        assertEquals(RecoveryEvidenceState.PENDING, maintenance.inspect().runOutcome.state)

        val report = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals("recovered", report.runOutcome.detail)
        assertEquals(record.nextMood, SaveManager.loadForestMoodState(context))
        assertEquals(record.nextReturn, SaveManager.loadReturnMomentState(context))
        assertEquals(
            RunOutcomeRecoveryTransitions.persistedSummary(summary),
            SaveManager.loadLastRunSummary(context)
        )
        assertEquals(
            record.nextRouteTierCount,
            SaveManager.loadRouteTierCount(context, summary.pacifistRouteTier)
        )
        assertEquals(RunOutcomeRecoveryLoadResult.Empty, runStore.load())
    }

    @Test
    fun `corrupt run journal survives safe retry until explicitly discarded`() {
        recoveryPrefs().edit()
            .putBoolean("present", true)
            .putInt("schema", 999)
            .commit()
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        assertEquals(RecoveryEvidenceState.CORRUPT, maintenance.recoverSafely().runOutcome.state)
        assertTrue(recoveryPrefs().getBoolean("present", false))

        val discarded = maintenance.discardCorrupt(RecoveryEvidenceDomain.RUN_OUTCOME)

        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertFalse(recoveryPrefs().contains("present"))
    }

    @Test
    fun `conflicting valid journal is retried before deliberate discard`() {
        val summary = summary()
        val record = recoveryRecord(summary)
        assertTrue(runStore.save(record))
        val conflictingMood = record.previousMood.copy(totalRuns = 7, steadyRuns = 7)
        SaveManager.saveForestMoodState(context, conflictingMood)
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val discarded = maintenance.discardUnresolvedPending(
            RecoveryEvidenceDomain.RUN_OUTCOME
        )

        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.PENDING, discarded.before.state)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertEquals(conflictingMood, SaveManager.loadForestMoodState(context))
        assertEquals(ReturnMomentState(), SaveManager.loadReturnMomentState(context))
        assertNull(SaveManager.loadLastRunSummary(context))
        assertEquals(0, SaveManager.loadRouteTierCount(context, summary.pacifistRouteTier))
        assertEquals(RunOutcomeRecoveryLoadResult.Empty, runStore.load())
    }

    @Test
    fun `matching strong ghost receipt repairs manifest and distance independently`() {
        val frames = ghostFrames()
        val expectedManifest = manifest(frames, 900f)
        assertTrue(SaveManager.saveGhostRun(context, frames))
        SaveManager.saveBestDistance(context, 120f)
        assertTrue(ghostStore.save(receipt(frames, 900f)))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val report = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.runOutcome.state)
        assertEquals(RecoveryEvidenceState.CLEAN, report.ghostPromotion.state)
        assertEquals("distance_repaired", report.ghostPromotion.detail)
        assertEquals(900f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertEquals(GhostPromotionReceiptLoadResult.Empty, ghostStore.load())
        assertEquals(
            GhostArtifactManifestLoadResult.Present(expectedManifest),
            manifestStore.load()
        )
    }

    @Test
    fun `manifest without receipt repairs distance and remains strongly inspectable`() {
        val frames = ghostFrames()
        val expectedManifest = manifest(frames, 1_050f)
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore.save(expectedManifest))
        SaveManager.saveBestDistance(context, 100f)
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        assertEquals("valid_manifest", maintenance.inspect().ghostPromotion.detail)
        val report = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CLEAN, report.ghostPromotion.state)
        assertEquals("distance_repaired", report.ghostPromotion.detail)
        assertEquals(1_050f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(
            GhostArtifactManifestLoadResult.Present(expectedManifest),
            manifestStore.load()
        )
    }

    @Test
    fun `corrupt ghost receipt is discarded without deleting valid strong manifest`() {
        val frames = ghostFrames()
        val validManifest = manifest(frames, 700f)
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore.save(validManifest))
        SaveManager.saveBestDistance(context, validManifest.distanceM)
        promotionFile().writeBytes(byteArrayOf(1, 2, 3, 4))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val safe = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CORRUPT, safe.ghostPromotion.state)
        assertTrue(promotionFile().exists())
        assertEquals(RecoveryEvidenceState.CLEAN, safe.runOutcome.state)

        val discarded = maintenance.discardCorrupt(
            RecoveryEvidenceDomain.GHOST_PROMOTION
        )

        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertEquals("valid_manifest", discarded.after.detail)
        assertFalse(promotionFile().exists())
        assertEquals(
            GhostArtifactManifestLoadResult.Present(validManifest),
            manifestStore.load()
        )
        assertEquals(RecoveryEvidenceState.CLEAN, maintenance.inspect().runOutcome.state)
    }

    @Test
    fun `corrupt manifest is retained by safe retry and can be discarded alone`() {
        val frames = ghostFrames()
        assertTrue(SaveManager.saveGhostRun(context, frames))
        manifestFile().writeBytes(byteArrayOf(1, 2, 3, 4))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val safe = maintenance.recoverSafely()

        assertEquals(RecoveryEvidenceState.CORRUPT, safe.ghostPromotion.state)
        assertEquals("invalid_manifest", maintenance.inspect().ghostPromotion.detail)
        assertTrue(manifestFile().exists())
        assertEquals(frames, SaveManager.loadGhostRun(context))

        val discarded = maintenance.discardCorrupt(
            RecoveryEvidenceDomain.GHOST_PROMOTION
        )

        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertFalse(manifestFile().exists())
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `tampered digest is diagnosed and targeted discard preserves ghost`() {
        val frames = ghostFrames()
        val valid = manifest(frames, 800f)
        val tampered = valid.copy(
            sha256Hex = flipFirstHex(requireNotNull(valid.sha256Hex))
        )
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore.save(tampered))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val before = maintenance.inspect().ghostPromotion

        assertEquals(RecoveryEvidenceState.CORRUPT, before.state)
        assertEquals("manifest_artifact_mismatch", before.detail)
        val discarded = maintenance.discardCorrupt(RecoveryEvidenceDomain.GHOST_PROMOTION)
        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertEquals(GhostArtifactManifestLoadResult.Empty, manifestStore.load())
    }

    @Test
    fun `tampered distance is diagnosed and targeted discard preserves ghost`() {
        val frames = ghostFrames()
        val valid = manifest(frames, 800f)
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore.save(valid.copy(distanceM = 801f)))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val before = maintenance.inspect().ghostPromotion

        assertEquals(RecoveryEvidenceState.CORRUPT, before.state)
        assertEquals("manifest_artifact_mismatch", before.detail)
        val discarded = maintenance.discardCorrupt(RecoveryEvidenceDomain.GHOST_PROMOTION)
        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `manifest artifact mismatch is diagnosed and targeted discard preserves ghost`() {
        val frames = ghostFrames()
        val unrelated = frames.mapIndexed { index, frame ->
            if (index == 0) frame.copy(x = frame.x + 4f) else frame
        }
        assertTrue(SaveManager.saveGhostRun(context, frames))
        assertTrue(manifestStore.save(manifest(unrelated, 800f)))
        val maintenance = AndroidRecoveryEvidenceMaintenance(context)

        val before = maintenance.inspect().ghostPromotion

        assertEquals(RecoveryEvidenceState.CORRUPT, before.state)
        assertEquals("manifest_artifact_mismatch", before.detail)
        val discarded = maintenance.discardCorrupt(RecoveryEvidenceDomain.GHOST_PROMOTION)
        assertEquals(RecoveryDiscardDisposition.DISCARDED, discarded.disposition)
        assertEquals(RecoveryEvidenceState.CLEAN, discarded.after.state)
        assertEquals(frames, SaveManager.loadGhostRun(context))
        assertEquals(GhostArtifactManifestLoadResult.Empty, manifestStore.load())
    }

    private fun recoveryRecord(summary: RunSummary): RunOutcomeRecoveryRecord {
        val previousMood = ForestMoodState()
        val previousReturn = ReturnMomentState()
        return RunOutcomeRecoveryRecord(
            phase = RunOutcomeRecoveryPhase.PREPARED,
            summary = summary,
            previousMood = previousMood,
            nextMood = RunOutcomeRecoveryTransitions.nextForestMood(previousMood, summary),
            previousReturn = previousReturn,
            nextReturn = RunOutcomeRecoveryTransitions.nextReturnMoment(
                previous = previousReturn,
                summary = summary,
                nowMs = FIXED_NOW_MS
            ),
            previousRouteTierCount = 0,
            nextRouteTierCount = RunOutcomeRecoveryTransitions.nextRouteTierCount(
                previous = 0,
                tier = summary.pacifistRouteTier
            )
        )
    }

    private fun summary(): RunSummary = RunSummary(
        score = 2_400,
        distanceM = 780f,
        isNewHighScore = true,
        highScore = 2_400,
        mercyHearts = 5,
        mercyMisses = 1,
        kindnessChain = 6,
        cleanPasses = 10,
        sparedCount = 3,
        hitsTaken = 1,
        seedsCollected = 14,
        bloomConversions = 2,
        lastKiller = EntityType.CAT,
        restQuote = "The forest kept the recovery evidence.",
        forestMood = ForestMood.STEADY,
        pacifistRouteTier = PacifistRouteTier.MERCIFUL
    )

    private fun receipt(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostPromotionReceipt {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostPromotionReceipt(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun manifest(
        frames: List<GhostFrame>,
        distanceM: Float
    ): GhostArtifactManifest {
        val identity = GhostRunIdentity.calculate(frames, distanceM)
        return GhostArtifactManifest(
            distanceM = distanceM,
            frameCount = frames.size,
            fingerprint = identity.fingerprint,
            sha256Hex = identity.sha256Hex
        )
    }

    private fun flipFirstHex(value: String): String =
        (if (value.first() == '0') '1' else '0') + value.drop(1)

    private fun ghostFrames(): List<GhostFrame> = listOf(
        GhostFrame(
            t = 0f,
            x = 120f,
            y = 600f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        ),
        GhostFrame(
            t = 0.04f,
            x = 124f,
            y = 590f,
            stateOrdinal = PlayerState.JUMPING.ordinal,
            scaleX = 0.98f,
            scaleY = 1.02f
        )
    )

    private fun primaryPrefs() = context.getSharedPreferences(
        SaveManager.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun recoveryPrefs() = context.getSharedPreferences(
        "forest_run_outcome_recovery_${SaveManager.activePrefsNameForTests}",
        Context.MODE_PRIVATE
    )

    private fun promotionFile(): File = File(
        context.filesDir,
        "${SaveManager.activeGhostFilenameForTests}.promotion"
    )

    private fun manifestFile(): File = File(
        context.filesDir,
        "${SaveManager.activeGhostFilenameForTests}.manifest"
    )

    private fun deleteGhostFiles() {
        val ghost = File(context.filesDir, SaveManager.activeGhostFilenameForTests)
        listOf(
            ghost,
            File(ghost.path + ".bak"),
            File(ghost.path + ".new"),
            File(ghost.path + ".promotion"),
            File(ghost.path + ".promotion.bak"),
            File(ghost.path + ".promotion.new"),
            File(ghost.path + ".manifest"),
            File(ghost.path + ".manifest.bak"),
            File(ghost.path + ".manifest.new")
        ).forEach { file -> file.delete() }
    }

    private companion object {
        const val FIXED_NOW_MS = 1_725_000_000_000L
    }
}