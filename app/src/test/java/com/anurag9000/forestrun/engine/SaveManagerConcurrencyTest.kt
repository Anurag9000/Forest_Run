package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun `run earned Seeds and Garden purchase serialize on one currency lock`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        val state = GameStateManager(context)
        val started = CountDownLatch(2)
        val completed = CountDownLatch(2)
        val error = AtomicReference<Throwable?>(null)
        lateinit var award: Thread
        lateinit var purchase: Thread

        SaveManager.withGardenCurrencyLock {
            award = Thread {
                started.countDown()
                try {
                    state.addBonus(seeds = 10)
                } catch (failure: Throwable) {
                    error.compareAndSet(null, failure)
                } finally {
                    completed.countDown()
                }
            }
            purchase = Thread {
                started.countDown()
                try {
                    val result = GardenPurchaseManager.purchaseNext(context, 1)
                    if (!result.purchased) {
                        error.compareAndSet(
                            null, AssertionError("Unexpected purchase status: ${result.status}")
                        )
                    }
                } catch (failure: Throwable) {
                    error.compareAndSet(null, failure)
                } finally {
                    completed.countDown()
                }
            }
            award.start()
            purchase.start()
            assertTrue("workers did not start", started.await(5, TimeUnit.SECONDS))
            assertFalse(
                "currency mutation escaped its shared lock",
                completed.await(50, TimeUnit.MILLISECONDS)
            )
        }
        award.join(TimeUnit.SECONDS.toMillis(5))
        purchase.join(TimeUnit.SECONDS.toMillis(5))
        assertFalse("award thread remained alive", award.isAlive)
        assertFalse("purchase thread remained alive", purchase.isAlive)
        error.get()?.let { throw AssertionError("Concurrent mutation failed", it) }

        // Either serial ordering gives 50 + 10 - 20 = 40; no award is lost
        // and a stale award cannot undo the plant purchase.
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(40, SaveManager.loadLifetimeSeeds(context))
        // A run owner's display cache may predate a subsequent Garden spend;
        // its authoritative save/reload boundary must rejoin the canonical balance.
        state.save()
        assertEquals(40, state.lifetimeSeeds)
        assertEquals(10, state.seedsThisRun)
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
