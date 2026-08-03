package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.DataOutputStream
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
    fun `version two manifest round trip preserves every field`() {
        val manifest = strongManifest(
            distanceM = 1_234.5f,
            frameCount = 321,
            fingerprint = -9_876_543_210L,
            digestByte = 0x2a
        )

        assertTrue(store.save(manifest))

        assertEquals(
            GhostArtifactManifestLoadResult.Present(manifest),
            store.load()
        )
        assertEquals(56L, manifestFile(GHOST_FILENAME).length())
    }

    @Test
    fun `version one manifest remains readable without being emitted by save`() {
        writeLegacyManifest(
            distanceM = 812f,
            frameCount = 7,
            fingerprint = 99L
        )

        val loaded = store.load()

        assertTrue(loaded is GhostArtifactManifestLoadResult.Present)
        val manifest = (loaded as GhostArtifactManifestLoadResult.Present).manifest
        assertEquals(812f, manifest.distanceM, 0f)
        assertEquals(7, manifest.frameCount)
        assertEquals(99L, manifest.fingerprint)
        assertNull(manifest.sha256Hex)
        assertEquals(24L, manifestFile(GHOST_FILENAME).length())
    }

    @Test
    fun `digestless new manifest is rejected`() {
        assertFalse(store.save(GhostArtifactManifest(400f, 2, 99L)))
        assertEquals(GhostArtifactManifestLoadResult.Empty, store.load())
    }

    @Test
    fun `empty and cleared manifest store loads empty`() {
        assertEquals(GhostArtifactManifestLoadResult.Empty, store.load())
        assertTrue(store.save(strongManifest(400f, 2, 99L, 0x11)))

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
        assertTrue(store.save(strongManifest(500f, 2, 77L, 0x22)))
        manifestFile(GHOST_FILENAME).appendBytes(byteArrayOf(8))
        assertEquals(GhostArtifactManifestLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        val bytes = ByteArray(56)
        bytes[0] = 0x46
        bytes[1] = 0x52
        bytes[2] = 0x47
        bytes[3] = 0x4D
        bytes[7] = 3
        manifestFile(GHOST_FILENAME).writeBytes(bytes)
        assertEquals(GhostArtifactManifestLoadResult.Corrupt, store.load())
    }

    @Test
    fun `invalid manifest is rejected without replacing valid identity`() {
        val valid = strongManifest(700f, 3, 123L, 0x33)
        assertTrue(store.save(valid))

        assertFalse(store.save(valid.copy(distanceM = Float.NaN)))
        assertFalse(store.save(valid.copy(distanceM = -1f)))
        assertFalse(store.save(valid.copy(frameCount = 0)))
        assertFalse(store.save(valid.copy(frameCount = GhostRecorder.MAX_FRAMES + 1)))
        assertFalse(store.save(valid.copy(sha256Hex = "ABC")))
        assertFalse(store.save(valid.copy(sha256Hex = "g".repeat(64))))

        assertEquals(
            GhostArtifactManifestLoadResult.Present(valid),
            store.load()
        )
    }

    @Test
    fun `manifest namespaces follow ghost filenames`() {
        val primary = strongManifest(800f, 4, 111L, 0x44)
        val other = strongManifest(900f, 5, 222L, 0x55)
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

    private fun strongManifest(
        distanceM: Float,
        frameCount: Int,
        fingerprint: Long,
        digestByte: Int
    ): GhostArtifactManifest = GhostArtifactManifest(
        distanceM = distanceM,
        frameCount = frameCount,
        fingerprint = fingerprint,
        sha256Hex = GhostRunIdentity.encodeHex(
            ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT) { digestByte.toByte() }
        )
    )

    private fun writeLegacyManifest(
        distanceM: Float,
        frameCount: Int,
        fingerprint: Long
    ) {
        DataOutputStream(manifestFile(GHOST_FILENAME).outputStream()).use { output ->
            output.writeInt(0x4652474D)
            output.writeInt(1)
            output.writeFloat(distanceM)
            output.writeInt(frameCount)
            output.writeLong(fingerprint)
        }
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
