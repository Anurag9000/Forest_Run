package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationIdentityTest {
    @Test
    fun `debug build uses the final Forest Run application id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("com.anurag9000.forestrun.debug", context.packageName)
        assertFalse(context.packageName.contains("yourname"))
    }
}
