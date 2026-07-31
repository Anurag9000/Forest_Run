package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class SaveManagerConcurrencyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `other thread Seed write survives stale Garden follow up`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveGardenProgress(context, 2)
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))

        val failure = AtomicReference<Throwable?>(null)
        val writer = Thread {
            try {
                SaveManager.saveLifetimeSeeds(context, 31)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        writer.start()
        writer.join()
        failure.get()?.let { throw AssertionError("Concurrent Seed write failed", it) }

        // Original Garden screen now sends a stale cached value on its thread.
        SaveManager.saveLifetimeSeeds(context, 80)

        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(31, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `preference namespace switch cannot consume another namespace marker`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        SaveManager.saveGardenProgress(context, 2)

        SaveManager.useCompatibilityPreferences(SaveIntegrityManager.CURRENT_SCHEMA_VERSION)
        SaveManager.saveLifetimeSeeds(context, 17)

        assertEquals(17, SaveManager.loadLifetimeSeeds(context))
        SaveManager.usePrimaryPreferences()
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }
}
