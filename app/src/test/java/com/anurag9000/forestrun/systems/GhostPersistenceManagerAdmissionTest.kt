package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.entities.PlayerState
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
class GhostPersistenceManagerAdmissionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        prefs().edit().clear().commit()
        GhostPersistenceManager.clearMemoryForTests()
        deletePersistenceFiles()
    }

    @After
    fun tearDown() {
        GhostPersistenceManager.awaitPendingWrites()
        GhostPersistenceManager.clearMemoryForTests()
        deletePersistenceFiles()
        prefs().edit().clear().commit()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `direct shorter candidate is rejected behind accepted longer promotion`() {
        val longer = frames(xOffset = 0f)
        val shorter = frames(xOffset = 50f)

        assertTrue(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = longer,
                distanceM = 900f
            )
        )
        assertFalse(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = shorter,
                distanceM = 700f
            )
        )
        assertEquals(longer, GhostPersistenceManager.loadLatest(context))
        assertEquals(900f, GhostPersistenceManager.bestDistanceFloor(context), 0f)

        assertTrue(GhostPersistenceManager.awaitPendingWrites())
        assertEquals(longer, SaveManager.loadGhostRun(context))
        assertEquals(900f, SaveManager.loadBestDistance(context), 0f)
    }

    @Test
    fun `compatibility overload preserves the current distance floor`() {
        val first = frames(xOffset = 0f)
        val replacement = frames(xOffset = 25f)
        assertTrue(
            GhostPersistenceManager.saveBestRunAsync(
                context = context,
                frames = first,
                distanceM = 600f
            )
        )
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        assertTrue(GhostPersistenceManager.saveBestRunAsync(context, replacement))
        assertEquals(600f, GhostPersistenceManager.bestDistanceFloor(context), 0f)
        assertTrue(GhostPersistenceManager.awaitPendingWrites())

        assertEquals(replacement, SaveManager.loadGhostRun(context))
        assertEquals(600f, SaveManager.loadBestDistance(context), 0f)
    }

    private fun frames(xOffset: Float): List<GhostFrame> = listOf(
        GhostFrame(
            t = 0f,
            x = 120f + xOffset,
            y = 600f,
            stateOrdinal = PlayerState.RUNNING.ordinal,
            scaleX = 1f,
            scaleY = 1f
        ),
        GhostFrame(
            t = 0.04f,
            x = 124f + xOffset,
            y = 590f,
            stateOrdinal = PlayerState.JUMPING.ordinal,
            scaleX = 0.98f,
            scaleY = 1.02f
        )
    )

    private fun prefs() = context.getSharedPreferences(
        SaveManager.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun deletePersistenceFiles() {
        val ghost = File(context.filesDir, SaveManager.activeGhostFilenameForTests)
        listOf(
            ghost,
            File(ghost.path + ".bak"),
            File(ghost.path + ".new"),
            File(ghost.path + ".promotion"),
            File(ghost.path + ".promotion.bak"),
            File(ghost.path + ".promotion.new")
        ).forEach { file -> file.delete() }
    }
}
