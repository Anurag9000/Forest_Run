package com.anurag9000.forestrun

import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCaptureMediaPublisherTest {
    @Test
    fun successfulPublicationWritesAndReleasesBitmap() {
        val bitmap = bitmap()
        val store = FakeStore()

        val uri = PendingCaptureMediaPublisher.publish(
            bitmap,
            "capture.png",
            "Pictures/captures",
            store
        )

        assertEquals(FakeStore.URI, uri)
        assertTrue(requireNotNull(store.output).size() > 0)
        assertEquals(1, store.publishCount)
        assertEquals(0, store.deleteCount)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun missingOutputStreamDeletesPendingRowAndReleasesBitmap() {
        val bitmap = bitmap()
        val store = FakeStore(output = null)

        assertThrows(IllegalStateException::class.java) {
            PendingCaptureMediaPublisher.publish(
                bitmap,
                "capture.png",
                "Pictures/captures",
                store
            )
        }

        assertEquals(1, store.deleteCount)
        assertEquals(0, store.publishCount)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun failedPendingToVisibleUpdateDeletesRowAndReleasesBitmap() {
        val bitmap = bitmap()
        val store = FakeStore(publishResult = 0)

        assertThrows(IllegalStateException::class.java) {
            PendingCaptureMediaPublisher.publish(
                bitmap,
                "capture.png",
                "Pictures/captures",
                store
            )
        }

        assertEquals(1, store.deleteCount)
        assertEquals(1, store.publishCount)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun failedInsertReleasesBitmapWithoutDeletingUnknownRow() {
        val bitmap = bitmap()
        val store = FakeStore(insertResult = null)

        assertThrows(IllegalArgumentException::class.java) {
            PendingCaptureMediaPublisher.publish(
                bitmap,
                "capture.png",
                "Pictures/captures",
                store
            )
        }

        assertEquals(0, store.deleteCount)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun cleanupFailureIsSuppressedAndBitmapIsStillReleased() {
        val bitmap = bitmap()
        val store = FakeStore(output = null, deleteFailure = true)

        val failure = assertThrows(IllegalStateException::class.java) {
            PendingCaptureMediaPublisher.publish(
                bitmap,
                "capture.png",
                "Pictures/captures",
                store
            )
        }

        assertEquals("delete failed", failure.suppressed.single().message)
        assertEquals(1, store.deleteCount)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun cleanupThatDeletesNoRowsIsReportedAsSuppressed() {
        val bitmap = bitmap()
        val store = FakeStore(output = null, deleteResult = 0)

        val failure = assertThrows(IllegalStateException::class.java) {
            PendingCaptureMediaPublisher.publish(
                bitmap,
                "capture.png",
                "Pictures/captures",
                store
            )
        }

        assertTrue(failure.suppressed.single().message.orEmpty().contains("Failed to remove pending"))
        assertEquals(1, store.deleteCount)
        assertTrue(bitmap.isRecycled)
    }

    private fun bitmap() = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private class FakeStore(
        private val insertResult: Uri? = URI,
        val output: ByteArrayOutputStream? = ByteArrayOutputStream(),
        private val publishResult: Int = 1,
        private val deleteFailure: Boolean = false,
        private val deleteResult: Int = 1
    ) : PendingCaptureMediaStore {
        var publishCount = 0
        var deleteCount = 0

        override fun insert(displayName: String, relativePath: String): Uri? = insertResult
        override fun openOutputStream(uri: Uri): OutputStream? = output
        override fun publish(uri: Uri): Int {
            publishCount++
            return publishResult
        }
        override fun delete(uri: Uri): Int {
            deleteCount++
            if (deleteFailure) error("delete failed")
            return deleteResult
        }

        companion object {
            val URI: Uri = Uri.parse("content://fake/capture/1")
        }
    }
}
