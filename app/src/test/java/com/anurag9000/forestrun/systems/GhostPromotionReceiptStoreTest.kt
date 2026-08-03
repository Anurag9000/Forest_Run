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
class GhostPromotionReceiptStoreTest {
    private lateinit var context: Context
    private lateinit var store: AtomicFileGhostPromotionReceiptStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteReceiptFiles()
        store = AtomicFileGhostPromotionReceiptStore(context, GHOST_FILENAME)
    }

    @After
    fun tearDown() {
        deleteReceiptFiles()
    }

    @Test
    fun `version two receipt round trip preserves every field`() {
        val receipt = strongReceipt(
            distanceM = 1_234.5f,
            frameCount = 321,
            fingerprint = -9_876_543_210L,
            digestByte = 0x2a
        )

        assertTrue(store.save(receipt))

        assertEquals(
            GhostPromotionReceiptLoadResult.Pending(receipt),
            store.load()
        )
        assertEquals(56L, receiptFile().length())
    }

    @Test
    fun `version one receipt remains readable without being emitted by save`() {
        writeLegacyReceipt(
            distanceM = 812f,
            frameCount = 7,
            fingerprint = 99L
        )

        val loaded = store.load()

        assertTrue(loaded is GhostPromotionReceiptLoadResult.Pending)
        val receipt = (loaded as GhostPromotionReceiptLoadResult.Pending).receipt
        assertEquals(812f, receipt.distanceM, 0f)
        assertEquals(7, receipt.frameCount)
        assertEquals(99L, receipt.fingerprint)
        assertNull(receipt.sha256Hex)
        assertEquals(24L, receiptFile().length())
    }

    @Test
    fun `digestless new receipt is rejected`() {
        assertFalse(store.save(GhostPromotionReceipt(400f, 2, 99L)))
        assertEquals(GhostPromotionReceiptLoadResult.Empty, store.load())
    }

    @Test
    fun `empty and cleared receipt store loads empty`() {
        assertEquals(GhostPromotionReceiptLoadResult.Empty, store.load())
        assertTrue(store.save(strongReceipt(400f, 2, 99L, 0x11)))

        assertTrue(store.clear())

        assertEquals(GhostPromotionReceiptLoadResult.Empty, store.load())
        assertFalse(receiptFile().exists())
        assertFalse(File(receiptFile().path + ".bak").exists())
        assertFalse(File(receiptFile().path + ".new").exists())
    }

    @Test
    fun `truncated trailing or unknown version receipt is corrupt`() {
        receiptFile().writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(GhostPromotionReceiptLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        assertTrue(store.save(strongReceipt(500f, 2, 77L, 0x22)))
        receiptFile().appendBytes(byteArrayOf(8))
        assertEquals(GhostPromotionReceiptLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        val bytes = ByteArray(56)
        bytes[0] = 0x46
        bytes[1] = 0x52
        bytes[2] = 0x47
        bytes[3] = 0x50
        bytes[7] = 3
        receiptFile().writeBytes(bytes)
        assertEquals(GhostPromotionReceiptLoadResult.Corrupt, store.load())
    }

    @Test
    fun `invalid receipt is rejected without replacing valid evidence`() {
        val valid = strongReceipt(700f, 3, 123L, 0x33)
        assertTrue(store.save(valid))

        assertFalse(store.save(valid.copy(distanceM = Float.NaN)))
        assertFalse(store.save(valid.copy(distanceM = -1f)))
        assertFalse(store.save(valid.copy(frameCount = 0)))
        assertFalse(store.save(valid.copy(frameCount = GhostRecorder.MAX_FRAMES + 1)))
        assertFalse(store.save(valid.copy(sha256Hex = "ABC")))
        assertFalse(store.save(valid.copy(sha256Hex = "g".repeat(64))))

        assertEquals(
            GhostPromotionReceiptLoadResult.Pending(valid),
            store.load()
        )
    }

    private fun strongReceipt(
        distanceM: Float,
        frameCount: Int,
        fingerprint: Long,
        digestByte: Int
    ): GhostPromotionReceipt = GhostPromotionReceipt(
        distanceM = distanceM,
        frameCount = frameCount,
        fingerprint = fingerprint,
        sha256Hex = GhostRunIdentity.encodeHex(
            ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT) { digestByte.toByte() }
        )
    )

    private fun writeLegacyReceipt(
        distanceM: Float,
        frameCount: Int,
        fingerprint: Long
    ) {
        DataOutputStream(receiptFile().outputStream()).use { output ->
            output.writeInt(0x46524750)
            output.writeInt(1)
            output.writeFloat(distanceM)
            output.writeInt(frameCount)
            output.writeLong(fingerprint)
        }
    }

    private fun receiptFile(): File =
        File(context.filesDir, "$GHOST_FILENAME.promotion")

    private fun deleteReceiptFiles() {
        val base = receiptFile()
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    private companion object {
        const val GHOST_FILENAME = "ghost_promotion_receipt_test.bin"
    }
}
