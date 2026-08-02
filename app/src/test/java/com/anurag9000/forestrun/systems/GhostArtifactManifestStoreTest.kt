package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class GhostArtifactManifestStoreTest {
    private lateinit var context: Context
    private lateinit var store: AtomicFileGhostArtifactManifestStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteManifestFiles(GHOST_FILENAME)
        deleteManifestFiles(OTHER_GHOST_FILENAME)
        store = AtomicFileGhostArtifactManifestStore(context, GHOST_FILENAME)
    }

    @After
    fun tearDown() {
        deleteManifestFiles(GHOST_FILENAME)
        deleteManifestFiles(OTHER_GHOST_FILENAME)
    }

    @Test
    fun `manifest round trip preserves every field`() {
        val manifest = GhostArtifactManifest(
            distanceM = 1_234.5f,
            frameCount = 321,
            fingerprint = -9_876_543_210L
        )

        assertTrue(store.save(manifest))

        assertEquals(
            GhostArtifactManifestLoadResult.Present(manifest),
            store.load()
        )
    }

    @Test
    fun `empty and cleared manifest store loads empty`() {
        assertEquals(GhostArtifactManifestLoadResult.Empty, store.load())
        assertTrue(store.save(GhostArtifactManifest(400f, 2, 99L)))

        assertTrue(store.clear())

        assertEquals(GhostArtifactManifestLoadResult.Empty, store.load())
        assertFalse(manifestFile(GHOST_FILENAME).exists())
        assertFalse(File(manifestFile(GHOST_FILENAME).path + ".bak").exists())
        assertFalse(File(manifestFile(GHOST_FILENAME).path + ".new").exists())
    }

    @Test
    fun `truncated trailing or unknown version manifest is corrupt`() {
        manifestFile(GHOST_FILENAME).writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(GhostArtifactManifestLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        assertTrue(store.save(GhostArtifactManifest(500f, 2, 77L)))
        manifestFile(GHOST_FILENAME).appendBytes(byteArrayOf(8))
        assertEquals(GhostArtifactManifestLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        val bytes = ByteArray(24)
        bytes[0] = 0x46
        bytes[1] = 0x52
        bytes[2] = 0x47
        bytes[3] = 0x4D
        bytes[7] = 2
        manifestFile(GHOST_FILENAME).writeBytes(bytes)
        assertEquals(GhostArtifactManifestLoadResult.Corrupt, store.load())
    }

    @Test
    fun `invalid manifest is rejected without replacing valid identity`() {
        val valid = GhostArtifactManifest(700f, 3, 123L)
        assertTrue(store.save(valid))

        assertFalse(store.save(valid.copy(distanceM = Float.NaN)))
        assertFalse(store.save(valid.copy(distanceM = -1f)))
        assertFalse(store.save(valid.copy(frameCount = 0)))
        assertFalse(store.save(valid.copy(frameCount = GhostRecorder.MAX_FRAMES + 1)))

        assertEquals(
            GhostArtifactManifestLoadResult.Present(valid),
            store.load()
        )
    }

    @Test
    fun `manifest namespaces follow ghost filenames`() {
        val primary = GhostArtifactManifest(800f, 4, 111L)
        val other = GhostArtifactManifest(900f, 5, 222L)
        val otherStore = AtomicFileGhostArtifactManifestStore(context, OTHER_GHOST_FILENAME)

        assertTrue(store.save(primary))
        assertTrue(otherStore.save(other))

        assertEquals(
            GhostArtifactManifestLoadResult.Present(primary),
            store.load()
        )
        assertEquals(
            GhostArtifactManifestLoadResult.Present(other),
            otherStore.load()
        )
        assertTrue(store.clear())
        assertEquals(GhostArtifactManifestLoadResult.Empty, store.load())
        assertEquals(
            GhostArtifactManifestLoadResult.Present(other),
            otherStore.load()
        )
    }

    private fun manifestFile(ghostFilename: String): File =
        File(context.filesDir, "$ghostFilename.manifest")

    private fun deleteManifestFiles(ghostFilename: String) {
        val base = manifestFile(ghostFilename)
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private companion object {
        const val GHOST_FILENAME = "ghost_artifact_manifest_test.bin"
        const val OTHER_GHOST_FILENAME = "ghost_artifact_manifest_other.bin"
    }
}
