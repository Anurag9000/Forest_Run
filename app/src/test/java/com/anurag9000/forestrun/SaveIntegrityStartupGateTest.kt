package com.anurag9000.forestrun

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import com.anurag9000.forestrun.engine.SaveIntegrityReport
import com.anurag9000.forestrun.engine.SaveIntegrityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveIntegrityStartupGateTest {
    @Test
    fun writeFailureBlocksStartupAndRendersAnAccessibleExplanation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val mayContinue = activity.continueAfterSaveRepair(
            SaveIntegrityReport(
                status = SaveIntegrityStatus.WRITE_FAILED,
                fromVersion = 0,
                toVersion = 0,
                repairedEntries = 3
            )
        )

        assertFalse(mayContinue)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val message = content.getChildAt(0) as TextView
        assertEquals(activity.getString(R.string.save_integrity_repair_failed), message.text)
        assertEquals(message.text, message.contentDescription)
    }

    @Test
    fun currentMigratedAndFutureCompatibleSavesMayContinue() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val allowedStatuses = listOf(
            SaveIntegrityStatus.CURRENT,
            SaveIntegrityStatus.MIGRATED,
            SaveIntegrityStatus.FUTURE_VERSION
        )

        allowedStatuses.forEach { status ->
            assertTrue(
                status.name,
                activity.continueAfterSaveRepair(
                    SaveIntegrityReport(status, fromVersion = 0, toVersion = 1, repairedEntries = 0)
                )
            )
        }
    }
}
