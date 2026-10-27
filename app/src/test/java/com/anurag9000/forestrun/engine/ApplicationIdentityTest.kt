package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.MainActivity
import com.anurag9000.forestrun.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class ApplicationIdentityTest {
    @Test
    fun `each build variant uses its intended Forest Run application id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val isDebugVariant = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val expectedPackage = if (isDebugVariant) {
            "com.anurag9000.forestrun.debug"
        } else {
            "com.anurag9000.forestrun"
        }
        assertEquals(expectedPackage, context.packageName)
        assertFalse(context.packageName.contains("yourname"))
    }

    @Test
    fun `manifest identifies the game and keeps its launcher landscape`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(ApplicationInfo.CATEGORY_GAME, context.applicationInfo.category)
        assertFalse(
            context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )

        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.screenOrientation)
    }

    @Test
    fun `backup policies exclude app data from cloud transfer and legacy backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allDataDomains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref"
        )

        assertEquals(
            mapOf("cloud-backup" to allDataDomains, "device-transfer" to allDataDomains),
            extractionExclusions(context, R.xml.data_extraction_rules)
        )
        assertEquals(
            allDataDomains,
            legacyBackupExclusions(context, R.xml.backup_rules)
        )
    }

    private fun extractionExclusions(context: Context, resourceId: Int): Map<String, Set<String>> {
        val parser = context.resources.getXml(resourceId)
        var section: String? = null
        val result = linkedMapOf<String, MutableSet<String>>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "cloud-backup", "device-transfer" -> {
                            section = parser.name
                            result[section!!] = linkedSetOf()
                        }
                        "exclude" -> section?.let {
                            result.getValue(it).add(parser.getAttributeValue(null, "domain"))
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == section) section = null
                }
            }
        } finally {
            parser.close()
        }
        return result.mapValues { it.value.toSet() }
    }

    private fun legacyBackupExclusions(context: Context, resourceId: Int): Set<String> {
        val parser = context.resources.getXml(resourceId)
        val result = linkedSetOf<String>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    result.add(parser.getAttributeValue(null, "domain"))
                }
            }
        } finally {
            parser.close()
        }
        return result
    }
}
