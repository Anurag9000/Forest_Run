package com.yourname.forest_run

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityRobolectricTest {

    @Test
    fun onCreateDoesNotCrashAndSetsContentView() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(android.R.id.content))
    }
}
