package com.anurag9000.forestrun

import android.content.ContentValues
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.OutputStream

internal interface PendingCaptureMediaStore {
    fun insert(displayName: String, relativePath: String): Uri?
    fun openOutputStream(uri: Uri): OutputStream?
    fun publish(uri: Uri): Int
    fun delete(uri: Uri): Int
}

/** Publishes a captured image as one pending-to-visible MediaStore transaction. */
internal object PendingCaptureMediaPublisher {
    fun publish(
        bitmap: Bitmap,
        displayName: String,
        relativePath: String,
        mediaStore: PendingCaptureMediaStore
    ): Uri {
        var inserted: Uri? = null
        var published = false
        var operationFailure: Throwable? = null
        try {
            inserted = requireNotNull(mediaStore.insert(displayName, relativePath)) {
                "Failed to create MediaStore item for $displayName"
            }
            val output = checkNotNull(mediaStore.openOutputStream(inserted)) {
                "Failed to open output stream for $displayName"
            }
            output.use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    "Failed to write bitmap for $displayName"
                }
            }
            check(mediaStore.publish(inserted) > 0) {
                "Failed to publish MediaStore item for $displayName"
            }
            published = true
            return inserted
        } catch (failure: Throwable) {
            operationFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            try {
                if (!bitmap.isRecycled) bitmap.recycle()
            } catch (failure: Throwable) {
                cleanupFailure = failure
            }
            if (!published && inserted != null) {
                try {
                    check(mediaStore.delete(inserted) > 0) {
                        "Failed to remove pending MediaStore item for $displayName"
                    }
                } catch (failure: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = failure
                    else cleanupFailure.addSuppressed(failure)
                }
            }
            cleanupFailure?.let { cleanup ->
                if (operationFailure != null) operationFailure.addSuppressed(cleanup)
                else throw cleanup
            }
        }
    }
}

internal class ContentResolverCaptureMediaStore(
    private val resolver: ContentResolver
) : PendingCaptureMediaStore {
    override fun insert(displayName: String, relativePath: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    override fun openOutputStream(uri: Uri): OutputStream? = resolver.openOutputStream(uri)

    override fun publish(uri: Uri): Int {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        return resolver.update(uri, values, null, null)
    }

    override fun delete(uri: Uri): Int = resolver.delete(uri, null, null)
}
