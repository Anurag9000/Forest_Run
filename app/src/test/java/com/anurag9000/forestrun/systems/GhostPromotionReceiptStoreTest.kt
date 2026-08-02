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
    fun `receipt round trip preserves every field`() {
        val receipt = GhostPromotionReceipt(
            distanceM = 1_234.5f,
            frameCount = 321,
            fingerprint = -9_876_543_210L
        )

        assertTrue(store.save(receipt))

        assertEquals(
            GhostPromotionReceiptLoadResult.Pending(receipt),
            store.load()
        )
    }

    @Test
    fun `empty and cleared receipt store loads empty`() {
        assertEquals(GhostPromotionReceiptLoadResult.Empty, store.load())
        assertTrue(
            store.save(
                GhostPromotionReceipt(400f, 2, 99L)
            )
        )

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
        assertTrue(store.save(GhostPromotionReceipt(500f, 2, 77L)))
        receiptFile().appendBytes(byteArrayOf(8))
        assertEquals(GhostPromotionReceiptLoadResult.Corrupt, store.load())

        assertTrue(store.clear())
        val bytes = ByteArray(24)
        bytes[0] = 0x46
        bytes[1] = 0x52
        bytes[2] = 0x47
        bytes[3] = 0x50
        bytes[7] = 2
        receiptFile().writeBytes(bytes)
        assertEquals(GhostPromotionReceiptLoadResult.Corrupt, store.load())
    }

    @Test
    fun `invalid receipt is rejected without replacing valid evidence`() {
        val valid = GhostPromotionReceipt(700f, 3, 123L)
        assertTrue(store.save(valid))

        assertFalse(store.save(valid.copy(distanceM = Float.NaN)))
        assertFalse(store.save(valid.copy(distanceM = -1f)))
        assertFalse(store.save(valid.copy(frameCount = 0)))
        assertFalse(store.save(valid.copy(frameCount = GhostRecorder.MAX_FRAMES + 1)))

        assertEquals(
            GhostPromotionReceiptLoadResult.Pending(valid),
            store.load()
        )
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
