package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostPersistenceNamespaceIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        GhostPersistenceManager.clearMemoryForTests()
        clearNamespace(PRIMARY_PREFS, PRIMARY_GHOST)
        clearNamespace(compatPrefs(COMPAT_VERSION), compatGhost(COMPAT_VERSION))
        SaveManager.usePrimaryPreferences()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        clearNamespace(PRIMARY_PREFS, PRIMARY_GHOST)
        clearNamespace(compatPrefs(COMPAT_VERSION), compatGhost(COMPAT_VERSION))
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `capture returns coherent primary and compatibility pairs`() {
        SaveManager.usePrimaryPreferences()
        assertEquals(
            GhostPersistenceNamespace(PRIMARY_PREFS, PRIMARY_GHOST),
            GhostPersistenceNamespace.capture()
        )

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        assertEquals(
            GhostPersistenceNamespace(
                compatPrefs(COMPAT_VERSION),
                compatGhost(COMPAT_VERSION)
            ),
            GhostPersistenceNamespace.capture()
        )
    }

    @Test
    fun `bound artifact store remains on captured namespace after active switch`() {
        val primaryNamespace = GhostPersistenceNamespace(PRIMARY_PREFS, PRIMARY_GHOST)
        val primaryStore = NamespaceBoundGhostPromotionArtifactStore(context, primaryNamespace)
        val primaryFrames = sampleFrames(offset = 0f)

        assertTrue(primaryStore.saveGhost(primaryFrames))
        assertTrue(primaryStore.saveBestDistanceM(640f))
        assertEquals(primaryFrames, SaveManager.loadGhostRun(context))
        assertEquals(640f, SaveManager.loadBestDistance(context), 0f)

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
        assertEquals(0f, SaveManager.loadBestDistance(context), 0f)
        assertEquals(primaryFrames, primaryStore.loadGhost())
        assertEquals(640f, primaryStore.loadBestDistanceM(), 0f)

        val compatStore = NamespaceBoundGhostPromotionArtifactStore(
            context,
            GhostPersistenceNamespace(
                compatPrefs(COMPAT_VERSION),
                compatGhost(COMPAT_VERSION)
            )
        )
        val compatFrames = sampleFrames(offset = 100f)
        assertTrue(compatStore.saveGhost(compatFrames))
        assertTrue(compatStore.saveBestDistanceM(780f))
        assertEquals(compatFrames, SaveManager.loadGhostRun(context))
        assertEquals(780f, SaveManager.loadBestDistance(context), 0f)

        SaveManager.usePrimaryPreferences()
        assertEquals(primaryFrames, SaveManager.loadGhostRun(context))
        assertEquals(640f, SaveManager.loadBestDistance(context), 0f)
    }

    @Test
    fun `queued manager writes and publications remain isolated by namespace`() {
        val primaryFrames = sampleFrames(offset = 0f)
        val compatFrames = sampleFrames(offset = 200f)

        SaveManager.usePrimaryPreferences()
        assertTrue(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = primaryFrames,
                distanceM = 650f
            )
        )

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        assertTrue(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = compatFrames,
                distanceM = 820f
            )
        )
        assertEquals(compatFrames, GhostPersistenceManager.loadLatest(context))
        assertEquals(820f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        SaveManager.usePrimaryPreferences()
        assertEquals(primaryFrames, GhostPersistenceManager.loadLatest(context))
        assertEquals(650f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertEquals(primaryFrames, SaveManager.loadGhostRun(context))
        assertEquals(650f, SaveManager.loadBestDistance(context), 0f)

        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        assertEquals(compatFrames, GhostPersistenceManager.loadLatest(context))
        assertEquals(820f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertEquals(compatFrames, SaveManager.loadGhostRun(context))
        assertEquals(820f, SaveManager.loadBestDistance(context), 0f)
    }

    @Test
    fun `namespace rejects path traversal filenames`() {
        var rejected = false
        try {
            GhostPersistenceNamespace("prefs", "../ghost_run.bin")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)

        rejected = false
        try {
            GhostPersistenceNamespace("prefs", "folder/ghost_run.bin")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)

        assertFalse(
            runCatching { GhostPersistenceNamespace("prefs", "ghost_run.bin") }
                .isFailure
        )
    }

    private fun sampleFrames(offset: Float): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f + offset, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f + offset, 196f, 1, 0.98f, 1.02f)
    )

    private fun clearNamespace(prefsName: String, ghostFilename: String) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        listOf(
            ghostFilename,
            "$ghostFilename.bak",
            "$ghostFilename.new",
            "$ghostFilename.promotion",
            "$ghostFilename.promotion.bak",
            "$ghostFilename.promotion.new",
            "$ghostFilename.manifest",
            "$ghostFilename.manifest.bak",
            "$ghostFilename.manifest.new"
        ).forEach { name -> File(context.filesDir, name).delete() }
    }

    private fun compatPrefs(version: Int): String = "forest_run_prefs_compat_v$version"

    private fun compatGhost(version: Int): String = "ghost_run_compat_v$version.bin"

    private companion object {
        const val COMPAT_VERSION = 91
        const val PRIMARY_PREFS = "forest_run_prefs"
        const val PRIMARY_GHOST = "ghost_run.bin"
    }
}
