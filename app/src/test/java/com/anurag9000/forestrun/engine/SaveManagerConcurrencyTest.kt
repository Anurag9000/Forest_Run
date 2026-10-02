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
    fun `equal concurrent high score candidates have exactly one durable owner`() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val results = java.util.concurrent.ConcurrentLinkedQueue<Boolean>()
        val failure = AtomicReference<Throwable?>(null)

        val workers = List(2) {
            Thread {
                try {
                    release.await(5, TimeUnit.SECONDS)
                    results.add(SaveManager.publishHighScoreIfBetter(context, 900))
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                } finally {
                    finished.countDown()
                }
            }
        }
        workers.forEach(Thread::start)
        release.countDown()
        assertTrue("high-score workers timed out", finished.await(5, TimeUnit.SECONDS))
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }
        failure.get()?.let { throw AssertionError("Concurrent high-score publish failed", it) }

        assertEquals(2, results.size)
        assertEquals(1, results.count { it })
        assertEquals(1, results.count { !it })
        assertEquals(900, SaveManager.loadHighScore(context))
    }


    @Test
    fun `derived progression counter keeps every concurrent logical increment`() {
        val workerCount = 8
        val incrementsPerWorker = 125
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workerCount)
        val error = AtomicReference<Throwable?>(null)

        val workers = List(workerCount) {
            Thread {
                try {
                    start.await(5, TimeUnit.SECONDS)
                    repeat(incrementsPerWorker) {
                        SaveManager.incrementEncounterCount(context, EntityType.CAT)
                    }
                } catch (failure: Throwable) {
                    error.compareAndSet(null, failure)
                } finally {
                    finished.countDown()
                }
            }
        }

        workers.forEach(Thread::start)
        start.countDown()
        assertTrue("counter workers timed out", finished.await(10, TimeUnit.SECONDS))
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }
        error.get()?.let { throw AssertionError("Concurrent counter increment failed", it) }

        assertEquals(
            workerCount * incrementsPerWorker,
            SaveManager.loadEncounterCount(context, EntityType.CAT)
        )
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
