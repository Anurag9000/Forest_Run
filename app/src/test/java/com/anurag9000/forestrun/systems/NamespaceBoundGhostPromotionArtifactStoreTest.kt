package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.entities.PlayerState
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NamespaceBoundGhostPromotionArtifactStoreTest {
    private lateinit var context: Context
    private lateinit var namespace: GhostPersistenceNamespace
    private lateinit var store: NamespaceBoundGhostPromotionArtifactStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.useCompatibilityPreferences(COMPAT_VERSION)
        namespace = GhostPersistenceNamespace.capture()
        clearNamespace()
        store = NamespaceBoundGhostPromotionArtifactStore(context, namespace)
    }

    @After
    fun tearDown() {
        clearNamespace()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `versioned writer matches SaveManager codec header and round trips`() {
        val frames = sampleFrames()

        assertTrue(store.saveGhost(frames))
        DataInputStream(FileInputStream(ghostFile()).buffered()).use { input ->
            assertEquals(SaveManager.GHOST_FILE_MAGIC, input.readInt())
            assertEquals(SaveManager.GHOST_FILE_VERSION, input.readInt())
            assertEquals(frames.size, input.readInt())
        }
        assertEquals(frames, store.loadGhost())
        assertEquals(frames, SaveManager.loadGhostRun(context))
    }

    @Test
    fun `legacy raw ordinal ghost remains readable`() {
        val frames = sampleFrames()
        DataOutputStream(FileOutputStream(ghostFile()).buffered()).use { output ->
            output.writeInt(frames.size)
            frames.forEach { frame ->
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(frame.stateOrdinal)
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
        }

        assertEquals(frames, store.loadGhost())
    }

    @Test
    fun `unknown version and trailing bytes are rejected`() {
        DataOutputStream(FileOutputStream(ghostFile()).buffered()).use { output ->
            output.writeInt(SaveManager.GHOST_FILE_MAGIC)
            output.writeInt(SaveManager.GHOST_FILE_VERSION + 1)
            output.writeInt(1)
            output.write(ByteArray(24))
        }
        assertTrue(store.loadGhost().isEmpty())

        assertTrue(store.saveGhost(sampleFrames()))
        FileOutputStream(ghostFile(), true).use { it.write(7) }
        assertTrue(store.loadGhost().isEmpty())
    }

    @Test
    fun `invalid candidate never replaces durable ghost`() {
        val frames = sampleFrames()
        assertTrue(store.saveGhost(frames))

        val invalid = frames.toMutableList().also { list ->
            list[1] = list[1].copy(t = Float.NaN)
        }
        assertTrue(!store.saveGhost(invalid))
        assertEquals(frames, store.loadGhost())
    }

    private fun sampleFrames(): List<GhostFrame> = listOf(
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

    private fun ghostFile(): File = File(context.filesDir, namespace.ghostFilename)

    private fun clearNamespace() {
        context.getSharedPreferences(namespace.prefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val base = ghostFile()
        listOf(
            base,
            File(base.path + ".bak"),
            File(base.path + ".new")
        ).forEach(File::delete)
    }

    private companion object {
        const val COMPAT_VERSION = 92
    }
}
