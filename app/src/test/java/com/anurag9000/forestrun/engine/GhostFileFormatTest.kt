package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostStateCodec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostFileFormatTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        file = File(context.filesDir, "ghost_run.bin")
        deleteGhostFiles()
    }

    @After
    fun tearDown() {
        deleteGhostFiles()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `new ghost writes magic version count and stable state codes`() {
        val frame = GhostFrame(
            t = 0.5f,
            x = 120f,
            y = 240f,
            stateOrdinal = PlayerState.STUMBLE.ordinal,
            scaleX = 0.9f,
            scaleY = 1.1f
        )

        assertTrue(SaveManager.saveGhostRun(context, listOf(frame)))

        DataInputStream(file.inputStream().buffered()).use { input ->
            assertEquals(SaveManager.GHOST_FILE_MAGIC, input.readInt())
            assertEquals(SaveManager.GHOST_FILE_VERSION, input.readInt())
            assertEquals(1, input.readInt())
            assertEquals(frame.t, input.readFloat(), 0f)
            assertEquals(frame.x, input.readFloat(), 0f)
            assertEquals(frame.y, input.readFloat(), 0f)
            assertEquals(GhostStateCodec.encode(PlayerState.STUMBLE), input.readInt())
            assertEquals(frame.scaleX, input.readFloat(), 0f)
            assertEquals(frame.scaleY, input.readFloat(), 0f)
            assertEquals(-1, input.read())
        }
    }

    @Test
    fun `legacy count and ordinal file still loads without rewrite`() {
        val frame = GhostFrame(
            t = 0.25f,
            x = 90f,
            y = 180f,
            stateOrdinal = PlayerState.REST.ordinal,
            scaleX = 1f,
            scaleY = 1f
        )
        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeInt(1)
            output.writeFloat(frame.t)
            output.writeFloat(frame.x)
            output.writeFloat(frame.y)
            output.writeInt(frame.stateOrdinal)
            output.writeFloat(frame.scaleX)
            output.writeFloat(frame.scaleY)
        }
        val legacyBytes = file.readBytes()

        assertEquals(listOf(frame), SaveManager.loadGhostRun(context))
        assertArrayEquals(legacyBytes, file.readBytes())
    }

    @Test
    fun `unknown future ghost version is rejected and preserved`() {
        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeInt(SaveManager.GHOST_FILE_MAGIC)
            output.writeInt(SaveManager.GHOST_FILE_VERSION + 99)
            output.writeInt(1)
            output.writeFloat(0.1f)
            output.writeFloat(100f)
            output.writeFloat(200f)
            output.writeInt(GhostStateCodec.encode(PlayerState.RUNNING))
            output.writeFloat(1f)
            output.writeFloat(1f)
        }
        val futureBytes = file.readBytes()

        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
        assertArrayEquals(futureBytes, file.readBytes())
    }

    @Test
    fun `unknown stable state code rejects complete versioned payload`() {
        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeInt(SaveManager.GHOST_FILE_MAGIC)
            output.writeInt(SaveManager.GHOST_FILE_VERSION)
            output.writeInt(1)
            output.writeFloat(0.1f)
            output.writeFloat(100f)
            output.writeFloat(200f)
            output.writeInt(Int.MAX_VALUE)
            output.writeFloat(1f)
            output.writeFloat(1f)
        }

        assertTrue(SaveManager.loadGhostRun(context).isEmpty())
    }

    private fun deleteGhostFiles() {
        file.delete()
        File(context.filesDir, "ghost_run.bin.bak").delete()
        File(context.filesDir, "ghost_run.bin.new").delete()
    }
}
